package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.RegisterUserCommissionRateRequest;
import com.factech.nexus.modules.commissions.application.UserCommissionRateResponse;
import com.factech.nexus.modules.commissions.domain.models.UserCommissionRate;
import com.factech.nexus.modules.commissions.domain.repository.UserCommissionRateRepository;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.application.ProductCatalog.ProductView;
import com.factech.nexus.modules.system.users.application.UserCatalog;
import com.factech.nexus.modules.system.users.application.UserCatalog.UserView;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de la tasa personalizada de una persona (`RF-CM-006`).
 *
 * <p><b>Comprueba que la persona existe y NADA MÁS, y esa ausencia es deliberada.</b> El modelo
 * anterior exigía además que portara el rol de la tarifa, y con ello impedía que una excepción
 * sobreviviera a que su titular dejara de vender. Al quitarle el rol a estas tasas (01-09-2026)
 * <b>esa protección desapareció</b> (`cm.md` §5.3): esta operación admite declarar una tasa a
 * alguien que no vende, y esa tasa <b>rige</b> — no se queda inerte, cobra.
 *
 * <p><b>Lo que registra sí paga desde el primer día</b>, al revés que una tasa de rol: no necesita
 * asociarse a nada, porque ignora el producto (`RN-CM-014`).
 *
 * <p><b>El no solapamiento no se comprueba aquí</b> (`RN-CM-006`): mira a <b>otras</b> filas, y
 * comprobarlo con un {@code SELECT} previo sería una carrera. Lo resuelve el motor y el adaptador
 * lo traduce.
 */
@Service
public class RegisterUserCommissionRateService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "user_commission_rates";

  private final UserCommissionRateRepository tasas;
  private final UserCatalog usuarios;
  private final ProductCatalog productos;
  private final ProductCommissionCapGuard tope;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  @Autowired
  public RegisterUserCommissionRateService(
      UserCommissionRateRepository tasas,
      UserCatalog usuarios,
      ProductCatalog productos,
      ProductCommissionCapGuard tope,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this(tasas, usuarios, productos, tope, auditoria, ids, Clock.systemUTC());
  }

  RegisterUserCommissionRateService(
      UserCommissionRateRepository tasas,
      UserCatalog usuarios,
      ProductCatalog productos,
      ProductCommissionCapGuard tope,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Clock reloj) {
    this.tasas = tasas;
    this.usuarios = usuarios;
    this.productos = productos;
    this.tope = tope;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  @Transactional
  public UserCommissionRateResponse register(RegisterUserCommissionRateRequest peticion) {
    UserView persona =
        usuarios
            .find(peticion.userId())
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-002",
                        "La persona indicada no existe.",
                        List.of(
                            new FieldError("userId", "EX-002", "La persona indicada no existe."))));

    ProductView producto = verificarProducto(peticion.productId());

    // `RN-CM-019` (11-09-2026): individual y no una suma. Ver el javadoc de
    // `verificarIndividual` -- las personalizadas de personas distintas sobre el
    // mismo producto son ALTERNATIVAS entre si, no cosas que se paguen a la vez.
    tope.verificarIndividual(producto.id(), producto.code(), peticion.valor(), "EX-006");

    UserCommissionRate nueva =
        tasas.save(
            UserCommissionRate.create(
                ids.next(),
                peticion.userId(),
                producto.id(),
                peticion.valor(),
                peticion.validFrom(),
                peticion.validTo(),
                OffsetDateTime.now(reloj)));

    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, nueva.getId(), ChangeAction.CREATE, nueva.instantanea()));

    return UserCommissionRateResponse.from(nueva, persona, producto);
  }

  /**
   * `RN-CM-002` y `RN-CM-010`: el retirado se distingue del inexistente.
   *
   * <p>Mismo par de comprobaciones que {@code AssociateProductService}, y por el mismo motivo: una
   * excepción sobre un producto que nadie puede vender no falla nunca y no sirve nunca, de modo que
   * se rechaza al declararla — el único momento en que alguien está mirando.
   */
  private ProductView verificarProducto(UUID productId) {
    ProductView producto =
        productos
            .find(productId)
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-003",
                        "El producto indicado no existe.",
                        List.of(
                            new FieldError(
                                "productId", "EX-003", "El producto indicado no existe."))));

    if (producto.retired()) {
      String mensaje = "No se pueden declarar tasas sobre un producto retirado.";
      throw new BusinessRuleException(
          "EX-004", mensaje, List.of(new FieldError("productId", "EX-004", mensaje)));
    }
    return producto;
  }
}
