package com.factech.nexus.shared.audit;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de {@link DeletionReasonReader}.
 *
 * <p><b>Selecciona una sola columna</b>, y eso no es una optimización: es la frontera. Traer la
 * fila entera dejaría el actor y la instantánea al alcance de quien luego quisiera «aprovechar que
 * ya están», y el puerto habría dejado de ser estrecho sin que nadie decidiera ampliarlo.
 *
 * <p>Usa {@code ix_audit_deletion_log_entity}, que ya existe desde `V4` sobre {@code (entity,
 * entity_id, occurred_at DESC)}.
 */
@Repository
public class JpaDeletionReasonReader implements DeletionReasonReader {

  private final EntityManager em;

  public JpaDeletionReasonReader(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<String> reasonFor(String module, String entity, UUID entityId) {
    if (module == null || entity == null || entityId == null) {
      return Optional.empty();
    }

    // Se descartan las de tipo ASSOCIATION: el Art. V.13 no les exige motivo
    // —quitar un rol a alguien no es eliminar el rol— y `ck_deletion_reason`
    // las deja con el motivo en nulo. Sin este filtro, una asociación
    // registrada DESPUÉS de la eliminación real ganaría el `ORDER BY` y esta
    // lectura devolvería vacío teniendo el motivo delante.
    List<?> filas =
        em.createNativeQuery(
                """
                SELECT d.reason
                  FROM audit_deletion_log d
                 WHERE d.module = :modulo
                   AND d.entity = :entidad
                   AND d.entity_id = :id
                   AND d.deletion_type <> 'ASSOCIATION'
                 ORDER BY d.occurred_at DESC
                 LIMIT 1
                """,
                String.class)
            .setParameter("modulo", module)
            .setParameter("entidad", entity)
            .setParameter("id", entityId)
            .getResultList();

    return filas.stream().findFirst().map(String.class::cast);
  }
}
