package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.CommissionRateResponse;
import com.factech.nexus.modules.commissions.application.RegisterCommissionRateRequest;
import com.factech.nexus.modules.commissions.domain.models.CommissionRate;
import com.factech.nexus.modules.commissions.domain.repository.CommissionRateRepository;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.application.ProductCatalog.ProductView;
import com.factech.nexus.modules.system.roles.application.RoleCatalog;
import com.factech.nexus.modules.system.roles.application.RoleCatalog.RoleView;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-CM-001` — registrar la tasa de comisión de un rol <b>sobre un producto</b>.
 *
 * <p><b>Desde el 15-09-2026 la tasa nace con su producto y rige desde que existe</b> (`RN-CM-021`).
 * Todo lo que hasta esa fecha comprobaba la asociación (`RF-CM-007`, retirada) se comprueba aquí, y
 * en este orden: el rol es vendedor (`RN-CM-001`), el producto existe y no está retirado
 * (`RN-CM-002`, `RN-CM-010`), el importe fijo cabe en los decimales de la moneda del producto
 * (`RN-CM-017`), y el tope y el gratuito contra el precio del producto (`RN-CM-019`, `RN-CM-020`)
 * con el mismo {@link ProductCommissionCapGuard} de siempre, que además bloquea el producto para
 * que dos altas simultáneas no sumen cada una por su lado.
 *
 * <p><b>Un solo rol por producto</b> (`RN-CM-013`) lo dice el esquema —{@code
 * uq_commission_rates_product_role}, parcial sobre las vivas— y aquí se comprueba antes para dar el
 * mensaje en el camino normal; la carrera la traduce el adaptador al mismo {@code 409}.
 */
@Service
public class RegisterCommissionRateService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "commission_rates";

  private final CommissionRateRepository tasas;
  private final RoleCatalog roles;
  private final ProductCatalog productos;
  private final ProductCommissionCapGuard tope;
  private final ProductCurrencyScale escala;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  @Autowired
  public RegisterCommissionRateService(
      CommissionRateRepository tasas,
      RoleCatalog roles,
      ProductCatalog productos,
      ProductCommissionCapGuard tope,
      ProductCurrencyScale escala,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this(tasas, roles, productos, tope, escala, auditoria, ids, Clock.systemUTC());
  }

  RegisterCommissionRateService(
      CommissionRateRepository tasas,
      RoleCatalog roles,
      ProductCatalog productos,
      ProductCommissionCapGuard tope,
      ProductCurrencyScale escala,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Clock reloj) {
    this.tasas = tasas;
    this.roles = roles;
    this.productos = productos;
    this.tope = tope;
    this.escala = escala;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  @Transactional
  public CommissionRateResponse register(RegisterCommissionRateRequest peticion) {
    RoleView rol = verificarRol(peticion);
    ProductView producto = verificarProducto(peticion.productId());

    // El importe fijo tiene moneda desde hoy: la de su producto (`RN-CM-017`).
    escala.verificar(producto.id(), peticion.valor(), "VAL-014");

    // Un rol vivo por producto, con mensaje: la carrera la traduce el adaptador.
    if (tasas.existsAlive(producto.id(), rol.id())) {
      String mensaje = "Ya hay una tasa viva de ese rol sobre ese producto.";
      throw new com.factech.nexus.shared.error.BusinessRuleException(
          "EX-007", mensaje, List.of(new FieldError("roleId", "EX-007", mensaje)));
    }

    // Tope y gratuito contra el precio de HOY, contando la nueva (`RN-CM-019`,
    // `RN-CM-020`). Bloquea el producto: dos altas a la vez suman en serie.
    tope.verificar(producto.id(), producto.code(), null, peticion.valor(), "EX-005", "EX-006");

    CommissionRate nueva =
        tasas.save(
            CommissionRate.create(
                ids.next(),
                producto.id(),
                peticion.roleId(),
                peticion.valor(),
                OffsetDateTime.now(reloj)));

    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, nueva.getId(), ChangeAction.CREATE, nueva.instantanea()));
    return CommissionRateResponse.from(nueva, rol, producto);
  }

  private RoleView verificarRol(RegisterCommissionRateRequest peticion) {
    RoleView rol =
        roles
            .find(peticion.roleId())
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-002",
                        "El rol indicado no existe.",
                        List.of(new FieldError("roleId", "EX-002", "El rol indicado no existe."))));
    if (!rol.esVendedor()) {
      String mensaje = "Solo los roles de tipo vendedor pueden llevar comisión.";
      throw new ValidationException(
          "EX-001", mensaje, List.of(new FieldError("roleId", "EX-001", mensaje)));
    }
    return rol;
  }

  /**
   * `RN-CM-002` y `RN-CM-010`: existe, y no está retirado.
   *
   * <p>Los dos responden {@code 422} con códigos distintos: el inexistente es un dato que no
   * referencia nada; el retirado existe y se distingue, porque quien lo envía puede estar mirando
   * un catálogo desactualizado y conviene que sepa cuál de las dos cosas pasó.
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
}
