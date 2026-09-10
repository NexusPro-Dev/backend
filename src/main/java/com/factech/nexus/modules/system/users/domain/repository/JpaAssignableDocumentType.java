package com.factech.nexus.modules.system.users.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta directa sobre `document_types`, igual que {@link JpaAssignableCountry} sobre
 * `countries`.
 *
 * <p><b>Se lee con bloqueo compartido</b> —{@code FOR SHARE}—, por el mismo motivo que el país, el
 * rol y el superior: aunque `RN-SP-036` deja este catálogo fuera de la API, una migración puede
 * estar retirando un tipo en la misma ventana. Compartido y no exclusivo: aquí solo se lee, y un
 * {@code FOR UPDATE} serializaría entre sí todas las altas que declaran el mismo tipo — que en la
 * práctica son casi todas.
 */
@Repository
public class JpaAssignableDocumentType implements AssignableDocumentType {

  private static final String PROYECCION =
      "SELECT d.id AS id, d.abbreviation AS abbreviation, d.name AS name,"
          + " d.is_active AS is_active FROM document_types d";

  private final EntityManager em;

  public JpaAssignableDocumentType(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DocumentTypeRef> find(UUID documentTypeId) {
    if (documentTypeId == null) {
      return Optional.empty();
    }
    return leer(PROYECCION + " WHERE d.id = :clave FOR SHARE", documentTypeId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DocumentTypeRef> findByAbbreviation(String abbreviation) {
    if (abbreviation == null || abbreviation.isBlank()) {
      return Optional.empty();
    }
    // La normalización vive aquí y no en cada DTO que llame: el formato lo
    // impone `ck_document_types_abbreviation_format` sobre esta tabla, y quien
    // conoce la restricción es quien la consulta.
    return leer(
        PROYECCION + " WHERE d.abbreviation = :clave FOR SHARE", abbreviation.trim().toUpperCase());
  }

  private Optional<DocumentTypeRef> leer(String sql, Object clave) {
    List<Tuple> filas =
        em.createNativeQuery(sql, Tuple.class).setParameter("clave", clave).getResultList();

    return filas.stream()
        .map(
            fila ->
                new DocumentTypeRef(
                    (UUID) fila.get("id"),
                    (String) fila.get("abbreviation"),
                    (String) fila.get("name"),
                    (Boolean) fila.get("is_active")))
        .findFirst();
  }
}
