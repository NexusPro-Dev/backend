package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.ChangeProductStatusRequest;
import com.factech.nexus.modules.products.application.ProductDetailResponse;
import com.factech.nexus.modules.products.domain.models.Product;
import com.factech.nexus.modules.products.domain.models.ProductStatus;
import com.factech.nexus.modules.products.domain.models.ProductType;
import com.factech.nexus.modules.products.domain.repository.ProductQueryRepository;
import com.factech.nexus.modules.products.domain.repository.ProductRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publicar o retirar de la oferta un producto (`RF-PM-005`).
 *
 * <p><b>Es la operación más corta del módulo y la que concentra su invariante más caro.</b> Cambiar
 * una columna es trivial; lo que no lo es es que <b>`RN-PM-004` vive entera aquí</b>: desde que el
 * producto nace inactivo (`RN-PM-012`), esta es la <b>única</b> puerta por la que un upgrade puede
 * quedar activo, y por tanto el único sitio donde dos precios simultáneos para la misma pareja
 * origen→destino podrían entrar.
 *
 * <h2>La verificación previa NO es la garantía</h2>
 *
 * <p>Dos upgrades inactivos con la misma pareja origen→destino activados a la vez: las dos
 * transacciones leen que la pareja está libre, las dos concluyen que pueden proceder, y sin nada
 * más quedarían las dos activas — que es exactamente el desenlace que la regla existe para impedir.
 * Es la misma escritura sesgada que `RN-SP-018` costó en `SP`.
 *
 * <p>Lo que lo impide es <b>{@code uq_products_upgrade_target}</b>. La verificación previa existe
 * para <b>redactar</b> —decir qué producto ocupa el destino, que es lo único accionable—; la
 * <b>garantía</b> la da el índice. La restricción decide, la comprobación redacta.
 *
 * <p><b>El bloqueo de la fila no sustituye al índice</b>, y confundirlos es el error fácil: el
 * bloqueo serializa dos peticiones sobre <b>el mismo</b> producto; lo que serializa dos productos
 * <b>distintos</b> compitiendo por el mismo destino solo puede ser una restricción sobre esa
 * competencia.
 */
@Service
public class ChangeProductStatusService {

  private static final String MODULO = "PM";
  private static final String ENTIDAD = "products";

  private final ProductRepository productos;
  private final ProductQueryRepository consultas;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public ChangeProductStatusService(
      ProductRepository productos, ProductQueryRepository consultas, AuditWriter auditoria) {
    this(productos, consultas, auditoria, Clock.systemUTC());
  }

  ChangeProductStatusService(
      ProductRepository productos,
      ProductQueryRepository consultas,
      AuditWriter auditoria,
      Clock reloj) {
    this.productos = productos;
    this.consultas = consultas;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public ProductDetailResponse change(UUID id, ChangeProductStatusRequest peticion) {
    ProductStatus destino = resolver(peticion.status());

    Product producto =
        productos
            .findAliveByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un producto con ese identificador."));

    ProductStatus anterior = producto.getStatus();

    // FA-001, ANTES de validar nada más: pedir el estado que ya tiene no es un
    // error ni exige descripción. Rechazarlo obligaría a la interfaz a
    // consultar el estado antes de cada pulsación.
    if (anterior == destino) {
      return detalleDe(producto.getId());
    }

    if (destino == ProductStatus.ACTIVO) {
      verificarDescripcion(producto);
      verificarDestinoLibre(producto);
    }
    // Desactivar NO comprueba nada del destino (`FA-002`): liberarlo nunca
    // produce conflicto. En un servicio, la comprobación de arriba tampoco se
    // ejecuta, y una prueba de número de sentencias comprueba esa ausencia.

    aplicar(producto, destino);

    // El volcado explícito es lo que convierte la violación del índice en un
    // `409` legible. Sin él saltaría al confirmar, fuera de este método.
    productos.flush();

    auditoria.recordChange(
        new ChangeEvent(
            MODULO,
            ENTIDAD,
            producto.getId(),
            ChangeAction.UPDATE,
            Map.of("status", Map.of("before", anterior.name(), "after", destino.name()))));

    return detalleDe(producto.getId());
  }

  /**
   * `RN-PM-014` — no se publica lo que no se puede explicar.
   *
   * <p><b>Solo al activar.</b> La regla acota <b>lo que se ofrece</b>, no lo que se retira: exigir
   * descripción para desactivar dejaría atrapado en la oferta justo al producto peor documentado
   * (`CA-PM-073`).
   */
  private static void verificarDescripcion(Product producto) {
    if (!producto.tieneDescripcion()) {
      String mensaje = "Un producto sin descripción no puede publicarse.";
      throw new ValidationException(
          "VAL-003", mensaje, List.of(new FieldError("description", "VAL-003", mensaje)));
    }
  }

  /**
   * `RN-PM-004` — un solo upgrade activo por pareja origen→destino.
   *
   * <p><b>El mensaje nombra al producto que ocupa la pareja.</b> Un {@code 409} que diga solo «ya
   * hay uno» obliga a quien lo recibe a buscarlo, y es lo único que hace la respuesta accionable:
   * lo que el actor necesita saber es <b>cuál desactivar</b>.
   *
   * <p>En un servicio esto <b>no se ejecuta</b>: no tiene destino que ocupar.
   */
  private void verificarDestinoLibre(Product producto) {
    if (producto.getType() != ProductType.UPGRADE_MEMBRESIA) {
      return;
    }
    productos
        .findActiveUpgradeFor(
            producto.getSourceMembershipId(), producto.getTargetMembershipId(), producto.getId())
        .ifPresent(
            ocupante -> {
              String mensaje =
                  "Ya hay un upgrade activo con ese mismo origen y destino: '"
                      + ocupante.getName()
                      + "' ("
                      + ocupante.getCode()
                      + "). Desactívelo antes de publicar este.";
              throw new BusinessRuleException(
                  "EX-002", mensaje, List.of(new FieldError("status", "EX-002", mensaje)));
            });
  }

  private void aplicar(Product producto, ProductStatus destino) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    if (destino == ProductStatus.ACTIVO) {
      producto.activate(ahora);
    } else {
      producto.deactivate(ahora);
    }
  }

  /**
   * La respuesta sale de <b>una</b> consulta y no de dos llamadas a los puertos de `SP`.
   *
   * <p>Es la misma proyección que devuelve `RF-PM-003`: el destino y la moneda vienen resueltos en
   * la misma sentencia, y el mismo recurso se lee igual por donde se pida.
   */
  private ProductDetailResponse detalleDe(UUID id) {
    return consultas
        .findDetail(id)
        .map(fila -> ProductDetailResponse.from(fila, null))
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "EX-001", "No existe un producto con ese identificador."));
  }

  /**
   * `VAL-002`. Se compara sin distinguir caja y el mensaje enumera los admitidos: un estado
   * rechazado sin decir cuáles existen obliga a buscarlos en la documentación.
   */
  private static ProductStatus resolver(String valor) {
    return Arrays.stream(ProductStatus.values())
        .filter(estado -> estado.name().equalsIgnoreCase(valor.trim()))
        .findFirst()
        .orElseThrow(
            () -> {
              String mensaje =
                  "El estado '"
                      + valor
                      + "' no es válido. Valores admitidos: "
                      + Arrays.stream(ProductStatus.values()).map(Enum::name).toList()
                      + ".";
              return new ValidationException(
                  "VAL-002", mensaje, List.of(new FieldError("status", "VAL-002", mensaje)));
            });
  }
}
