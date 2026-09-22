package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.RegisterUserCommissionRateRequest;
import com.factech.nexus.modules.commissions.application.UserCommissionRateResponse;
import com.factech.nexus.modules.commissions.domain.models.UserCommissionRate;
import com.factech.nexus.modules.commissions.domain.repository.UserCommissionRateRepository;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.application.ProductCatalog.ProductView;
import com.factech.nexus.modules.products.application.ProductCatalog.SaleView;
import com.factech.nexus.modules.system.users.application.UserCatalog;
import com.factech.nexus.modules.system.users.application.UserCatalog.UserView;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
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
 * Alta de la tasa personalizada de una persona <b>sobre un producto</b> (`RF-CM-006`).
 *
 * <p><b>Desde el 16-09-2026 la tasa nace con su producto</b> (`RN-CM-021`), y todo lo que del
 * 11-09-2026 al 16-09-2026 comprobaba la asociación se comprueba aquí, en este orden: la persona
 * existe (`EX-002`), el producto existe y no está retirado (`EX-003`, `EX-004`), el importe fijo
 * cabe en los decimales de la moneda del producto (`RN-CM-017`, `VAL-014`), ningún día del periodo
 * está ya cubierto por otra tasa viva de esa persona sobre ese producto (`RN-CM-006`, `EX-006`), y
 * el valor cabe en el producto: tope individual y gratuito (`RN-CM-019`, `RN-CM-020`, `EX-007`,
 * `EX-008`).
 *
 * <p><b>De la persona comprueba que existe y NADA MÁS, y esa ausencia es deliberada.</b> El modelo
 * anterior exigía además que portara el rol de la tarifa, y con ello impedía que una excepción
 * sobreviviera a que su titular dejara de vender. Al quitarle el rol a estas tasas (01-09-2026)
 * <b>esa protección desapareció</b> (`cm.md` §5.3): esta operación admite declarar una tasa a
 * alguien que no vende, y esa tasa <b>rige</b> — no se queda inerte, cobra.
 *
 * <p><b>El solapamiento se comprueba dos veces, y las dos hacen falta.</b> La consulta previa da el
 * mensaje en el camino normal; la garantía es {@code uq_user_commission_rates_vigente}, en el
 * motor, que es lo único que dos altas simultáneas no pueden burlar — y el adaptador traduce esa
 * violación al mismo {@code 409}.
 */
@Service
public class RegisterUserCommissionRateService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "user_commission_rates";

  private final UserCommissionRateRepository tasas;
  private final UserCatalog usuarios;
  private final ProductCatalog productos;
  private final ProductCommissionCapGuard tope;
  private final ProductCurrencyScale escala;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  @Autowired
  public RegisterUserCommissionRateService(
      UserCommissionRateRepository tasas,
      UserCatalog usuarios,
      ProductCatalog productos,
      ProductCommissionCapGuard tope,
      ProductCurrencyScale escala,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this(tasas, usuarios, productos, tope, escala, auditoria, ids, Clock.systemUTC());
  }

  RegisterUserCommissionRateService(
      UserCommissionRateRepository tasas,
      UserCatalog usuarios,
      ProductCatalog productos,
      ProductCommissionCapGuard tope,
      ProductCurrencyScale escala,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Clock reloj) {
    this.tasas = tasas;
    this.usuarios = usuarios;
    this.productos = productos;
    this.tope = tope;
    this.escala = escala;
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

    // Un rango invertido no se puede ni preguntar: el agregado lo rechazaría
    // igual, pero después de una consulta que con esas fechas falla.
    UserCommissionRate.verificarVigencia(peticion.validFrom(), peticion.validTo());

    // El importe fijo tiene moneda desde hoy: la de su producto (`RN-CM-017`).
    escala.verificar(producto.id(), peticion.valor(), "VAL-014");

    // `RN-CM-006` con mensaje. La garantía es el `EXCLUDE`; esto es el camino normal.
    if (tasas
        .findOverlapping(
            peticion.userId(), producto.id(), peticion.validFrom(), peticion.validTo(), null)
        .isPresent()) {
      throw UserCommissionRateRepository.solapamiento();
    }

    // Tope individual y gratuito contra el precio de HOY (`RN-CM-019`, `RN-CM-020`).
    tope.verificarIndividual(producto.id(), producto.code(), peticion.valor(), "EX-007", "EX-008");

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

    return UserCommissionRateResponse.from(nueva, persona, vistaDeVenta(producto.id()));
  }

  /**
   * `RN-CM-002` y `RN-CM-010`: existe, y no está retirado. Los dos responden {@code 422} con
   * códigos distintos, como en el alta de rol: quien lo envía puede estar mirando un catálogo
   * desactualizado y conviene que sepa cuál de las dos cosas pasó.
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
      String mensaje = "No se configuran comisiones sobre un producto retirado.";
      throw new UnprocessableEntityException(
          "EX-004", mensaje, List.of(new FieldError("productId", "EX-004", mensaje)));
    }
    return producto;
  }

  /** La vista de venta trae el precio y la moneda; el producto ya se comprobó vivo. */
  private SaleView vistaDeVenta(UUID productId) {
    List<SaleView> vistas = productos.saleViewOf(List.of(productId));
    if (vistas.isEmpty()) {
      throw new IllegalStateException(
          "El producto " + productId + " no tiene vista de venta: no debería llegar aquí.");
    }
    return vistas.get(0);
  }
}
