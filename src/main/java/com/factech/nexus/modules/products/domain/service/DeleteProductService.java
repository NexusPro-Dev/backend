package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.DeleteProductRequest;
import com.factech.nexus.modules.products.domain.models.DeletionReason;
import com.factech.nexus.modules.products.domain.models.Product;
import com.factech.nexus.modules.products.domain.repository.ProductRepository;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retirar un producto del catálogo (`RF-PM-006`).
 *
 * <p><b>Lo que define esta operación no es lo que hace, es el orden en el que lo hace</b> y lo que
 * deliberadamente no toca:
 *
 * <ol>
 *   <li><b>El motivo tiene contenido</b> (`VAL-002`). Va primero: rechazar por motivo vacío no debe
 *       costar ni una consulta.
 *   <li>El producto existe y <b>no está ya retirado</b>, bloqueado.
 *   <li><b>Se captura el estado completo ANTES de tocar nada.</b>
 *   <li>Se marca {@code deleted_at}. <b>{@code status} no se toca.</b>
 *   <li>Se registra la eliminación con el motivo y la instantánea.
 * </ol>
 *
 * <p><b>Los pasos 3 y 4 son el requerimiento, y los dos se hacen mal sin fallar.</b> Capturar la
 * instantánea después de marcar deja un registro que dice qué <b>quedó</b> del producto y no qué
 * <b>era</b>; desactivarlo «de paso» haría que todos los registros dijeran «inactivo» y ese dato
 * dejaría de significar nada. En los dos casos el sistema funciona y el registro miente.
 *
 * <p><b>No es idempotente a propósito</b>: retirar dos veces con dos motivos distintos dejaría el
 * segundo escrito sobre un hecho que ocurrió antes y por otra razón.
 *
 * <p><b>Ningún evento de seguridad</b> (`CA-PM-087`): un producto no concede privilegios sobre el
 * sistema, y el catálogo de `security.md` §8.1 es cerrado. Es la misma postura del alta.
 */
@Service
public class DeleteProductService {

  private static final String MODULO = "PM";
  private static final String ENTIDAD = "products";

  private final ProductRepository productos;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public DeleteProductService(ProductRepository productos, AuditWriter auditoria) {
    this(productos, auditoria, Clock.systemUTC());
  }

  DeleteProductService(ProductRepository productos, AuditWriter auditoria, Clock reloj) {
    this.productos = productos;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public void delete(UUID id, DeleteProductRequest peticion) {
    // 1. El motivo, antes que nada y antes de cualquier consulta.
    DeletionReason motivo = new DeletionReason(peticion == null ? null : peticion.reason());

    // 2. El producto, bloqueado y EN CUALQUIER ESTADO: hay que poder
    //    distinguir «no existe» de «ya está retirado».
    Product producto =
        productos
            .findByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un producto con ese identificador."));

    // `EX-002`, y se distingue de `EX-001` a propósito. Al eliminar una persona
    // los dos casos comparten el `404` para no revelar que existió; aquí no hay
    // nada que ocultar —el catálogo DEVUELVE los retirados a cualquiera con
    // `products:read`— y quien intenta retirar dos veces merece saber que su
    // primera petición ya funcionó, en lugar de creer que falló.
    if (producto.estaRetirado()) {
      String mensaje = "El producto ya está retirado del catálogo.";
      throw new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("id", "EX-002", mensaje)));
    }

    // 3. LA INSTANTÁNEA, ANTES DE TOCAR NADA. Después de marcar ya no hay qué
    //    capturar: el registro diría qué quedó del producto, no qué era.
    Map<String, Object> instantanea = producto.instantanea();

    // 4. La marca. `status` NO se toca: el registro tiene que poder decir si
    //    estaba a la venta cuando se retiró (`CA-PM-052`).
    producto.delete(OffsetDateTime.now(reloj));

    // 5. El registro, en la MISMA transacción (Art. V.14): si el retiro se
    //    revierte, su registro también; si el registro falla, el retiro falla.
    auditoria.recordDeletion(
        new DeletionEvent(
            MODULO, ENTIDAD, producto.getId(), DeletionType.LOGICAL, motivo.value(), instantanea));
  }
}
