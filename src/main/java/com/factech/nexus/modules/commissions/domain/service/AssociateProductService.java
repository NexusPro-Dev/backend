package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.AssociateProductRequest;
import com.factech.nexus.modules.commissions.application.ProductAssociationResponse;
import com.factech.nexus.modules.commissions.domain.models.CommissionRate;
import com.factech.nexus.modules.commissions.domain.models.ProductCommissionRate;
import com.factech.nexus.modules.commissions.domain.repository.CommissionRateRepository;
import com.factech.nexus.modules.commissions.domain.repository.ProductCommissionRateQueryRepository;
import com.factech.nexus.modules.commissions.domain.repository.ProductCommissionRateRepository;
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
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asociar una tasa de rol a un producto (`RF-CM-007`).
 *
 * <p><b>Es la operación que pone una tasa en vigor</b>, y sin ella el catálogo entero no paga nada
 * a nadie (`RN-CM-012`). Esa es la diferencia de fondo con el modelo anterior, donde una tarifa sin
 * producto regía sobre todo el catálogo: <b>la ausencia pasó de significar «todos» a significar
 * «ninguno»</b>.
 *
 * <p><b>El rol no se recibe: se toma de la tasa.</b> Y la clave foránea compuesta de {@code
 * product_commission_rates} hace imposible que el copiado diverja del que la tasa declara.
 *
 * <p><b>`RN-CM-013` no se comprueba aquí</b> —«un porcentaje por rol y producto»—: mira a otras
 * filas, y un {@code SELECT} previo sería una carrera. Lo cierra la clave primaria, y el adaptador
 * traduce su violación.
 *
 * <p><b>Y `RN-CM-014` tampoco se comprueba</b>: que solo las tasas de rol se asocien a productos no
 * es una validación de este caso de uso, es que las personalizadas viven en otra tabla y esta ruta
 * no puede alcanzarlas.
 */
@Service
public class AssociateProductService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "product_commission_rates";

  private final CommissionRateRepository tasas;
  private final ProductCommissionRateRepository asociaciones;
  private final ProductCommissionRateQueryRepository consultas;
  private final ProductCatalog productos;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public AssociateProductService(
      CommissionRateRepository tasas,
      ProductCommissionRateRepository asociaciones,
      ProductCommissionRateQueryRepository consultas,
      ProductCatalog productos,
      AuditWriter auditoria) {
    this(tasas, asociaciones, consultas, productos, auditoria, Clock.systemUTC());
  }

  AssociateProductService(
      CommissionRateRepository tasas,
      ProductCommissionRateRepository asociaciones,
      ProductCommissionRateQueryRepository consultas,
      ProductCatalog productos,
      AuditWriter auditoria,
      Clock reloj) {
    this.tasas = tasas;
    this.asociaciones = asociaciones;
    this.consultas = consultas;
    this.productos = productos;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public ProductAssociationResponse associate(UUID rateId, AssociateProductRequest peticion) {
    // Una tasa retirada no se asocia: poner en vigor lo que alguien declaró que
    // no debió existir es justo lo contrario de lo que el retiro significa.
    CommissionRate tasa =
        tasas
            .findAlive(rateId)
            .orElseThrow(
                () -> new ResourceNotFoundException("EX-404", "La tasa indicada no existe."));

    ProductView producto = verificarProducto(peticion.productId());

    asociaciones.save(ProductCommissionRate.create(producto.id(), tasa, OffsetDateTime.now(reloj)));

    auditoria.recordChange(
        new ChangeEvent(
            MODULO,
            ENTIDAD,
            tasa.getId(),
            ChangeAction.CREATE,
            java.util.Map.of(
                "product_id", producto.id().toString(),
                "role_id", tasa.getRoleId().toString(),
                "commission_rate_id", tasa.getId().toString(),
                "percentage", tasa.getPercentage().toPlainString())));

    return ProductAssociationResponse.de(consultas.findByRate(tasa.getId()));
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

    // `RN-CM-010`. Configurar lo que nadie puede vender no falla nunca y no
    // sirve nunca: se rechaza al declararlo, que es el único momento en que
    // alguien está mirando.
    if (producto.retired()) {
      String mensaje = "No se pueden asociar tasas a un producto retirado.";
      throw new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("productId", "EX-002", mensaje)));
    }
    return producto;
  }
}
