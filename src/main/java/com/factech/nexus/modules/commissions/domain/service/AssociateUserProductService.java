package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.AssociateUserProductRequest;
import com.factech.nexus.modules.commissions.application.UserRateProductsResponse;
import com.factech.nexus.modules.commissions.domain.models.UserCommissionRate;
import com.factech.nexus.modules.commissions.domain.models.UserRateProduct;
import com.factech.nexus.modules.commissions.domain.repository.UserCommissionRateRepository;
import com.factech.nexus.modules.commissions.domain.repository.UserRateProductRepository;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.application.ProductCatalog.ProductView;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asociar una tasa personalizada a un producto (`RF-CM-006`, 11-09-2026).
 *
 * <p><b>Es la operación que pone la tasa en vigor</b>, y hasta que ocurre esa excepción no paga
 * nada a nadie (`RN-CM-012`). Hasta el 11-09-2026 la personalizada era la <b>única excepción</b> a
 * esa regla: pagaba desde el primer día, sobre todo el catálogo.
 *
 * <h2>Aquí vive `RN-CM-006`, y eso es lo que hay que saber de esta clase</h2>
 *
 * <p>«Una sola tasa viva por persona y producto en cada fecha» <b>estaba en el motor</b> —un {@code
 * EXCLUDE} sobre {@code (user_id, daterange)}— y {@code V85} la retiró: con el producto en una
 * tabla de asociación la regla <b>cruza dos tablas</b>, y ningún índice hace eso.
 *
 * <p>De modo que se comprueba aquí, y con un <b>bloqueo consultivo por persona</b> tomado
 * <b>antes</b> de mirar. Sin el bloqueo, dos asociaciones simultáneas leerían las dos «no hay
 * solape» y escribirían las dos: es exactamente el defecto que `RN-SP-018` tuvo. <b>Ya no hay nadie
 * detrás que atrape el caso</b>, y esa es la diferencia con el alta, donde el motor rechazaba y el
 * adaptador solo traducía.
 *
 * <p><b>Se comprueba al asociar y no al registrar</b>: sin producto no hay solapamiento posible, y
 * dos tasas de la misma persona con fechas que se pisan son legítimas mientras rijan en productos
 * distintos.
 */
@Service
public class AssociateUserProductService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "user_commission_rate_products";

  private final UserCommissionRateRepository tasas;
  private final UserRateProductRepository asociaciones;
  private final ProductCatalog productos;
  private final ProductCommissionCapGuard tope;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public AssociateUserProductService(
      UserCommissionRateRepository tasas,
      UserRateProductRepository asociaciones,
      ProductCatalog productos,
      ProductCommissionCapGuard tope,
      AuditWriter auditoria) {
    this(tasas, asociaciones, productos, tope, auditoria, Clock.systemUTC());
  }

  AssociateUserProductService(
      UserCommissionRateRepository tasas,
      UserRateProductRepository asociaciones,
      ProductCatalog productos,
      ProductCommissionCapGuard tope,
      AuditWriter auditoria,
      Clock reloj) {
    this.tasas = tasas;
    this.asociaciones = asociaciones;
    this.productos = productos;
    this.tope = tope;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public UserRateProductsResponse associate(UUID rateId, AssociateUserProductRequest peticion) {
    // Una tasa retirada no se asocia: poner en vigor lo que alguien declaró que
    // no debió existir es justo lo contrario de lo que el retiro significa.
    UserCommissionRate tasa =
        tasas
            .findAlive(rateId)
            .orElseThrow(
                () -> new ResourceNotFoundException("EX-404", "La tasa indicada no existe."));

    ProductView producto = verificarProducto(peticion.productId());

    // EL BLOQUEO, ANTES DE MIRAR NADA. Ver el javadoc de la clase.
    tasas.lockUser(tasa.getUserId());

    if (asociaciones.existe(rateId, producto.id())) {
      String mensaje = "Esa tasa ya está asociada a ese producto.";
      throw new BusinessRuleException(
          "EX-005", mensaje, List.of(new FieldError("productId", "EX-005", mensaje)));
    }

    // `RN-CM-006`, fuera del motor desde `V85`.
    if (asociaciones.haySolape(
        tasa.getUserId(), producto.id(), rateId, tasa.getValidFrom(), tasa.getValidTo())) {
      String mensaje =
          "Esa persona ya tiene una tasa viva sobre ese producto en parte de ese periodo.";
      throw new BusinessRuleException(
          "EX-006", mensaje, List.of(new FieldError("productId", "EX-006", mensaje)));
    }

    // `RN-CM-019`, individual y no una suma: ver `verificarIndividual`.
    tope.verificarIndividual(producto.id(), producto.code(), tasa.getValue(), "EX-007");

    asociaciones.save(UserRateProduct.create(rateId, producto.id(), OffsetDateTime.now(reloj)));

    auditoria.recordChange(
        new ChangeEvent(
            MODULO,
            ENTIDAD,
            rateId,
            ChangeAction.CREATE,
            Map.of(
                "user_commission_rate_id", rateId.toString(),
                "product_id", producto.id().toString(),
                "user_id", tasa.getUserId().toString())));

    return respuesta(rateId);
  }

  private UserRateProductsResponse respuesta(UUID rateId) {
    return new UserRateProductsResponse(
        rateId,
        asociaciones.asociadosDe(rateId).stream()
            .map(
                fila ->
                    new UserRateProductsResponse.ProductRef(fila.id(), fila.code(), fila.name()))
            .toList());
  }

  /** `RN-CM-002` y `RN-CM-010`: el retirado se distingue del inexistente. */
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
      String mensaje = "No se pueden asociar tasas a un producto retirado.";
      throw new BusinessRuleException(
          "EX-004", mensaje, List.of(new FieldError("productId", "EX-004", mensaje)));
    }
    return producto;
  }
}
