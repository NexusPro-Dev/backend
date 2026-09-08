package com.factech.nexus.modules.system.documenttypes.domain.repository;

import com.factech.nexus.modules.system.documenttypes.application.DocumentTypeItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador del catálogo de tipos de documento.
 *
 * <p><b>Una sola sentencia y sin agregado por medio</b>, igual que el catálogo de países y el de
 * monedas: lo que se devuelve es una proyección, y cargar entidades para leer cuatro filas es
 * pagar un mapeo que nadie usa.
 *
 * <p><b>El orden lo decide la columna y no la consulta.</b> {@code name} está declarada con la
 * intercalación {@code es-x-icu} (`V67`), de modo que un {@code ORDER BY name} corriente ordena
 * bien los acentos. Sin ella, «Ñ» y cualquier vocal acentuada caerían al final y el desplegable
 * parecería roto.
 */
@Repository
public class JpaDocumentTypeQueryRepository implements DocumentTypeQueryRepository {

  private final EntityManager em;

  public JpaDocumentTypeQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<DocumentTypeItem> findAll(boolean incluirInactivos) {
    String sql =
        "SELECT d.id AS id, d.abbreviation AS abbreviation, d.name AS name,"
            + " d.is_active AS is_active"
            + " FROM document_types d"
            // El predicado se arma aquí y no con `(:flag OR d.is_active)`: ese
            // patrón impide que el motor use un índice y es el que `RF-SP-002`
            // descartó para todo el módulo.
            + (incluirInactivos ? "" : " WHERE d.is_active")
            + " ORDER BY d.name";

    List<Tuple> filas = em.createNativeQuery(sql, Tuple.class).getResultList();
    List<DocumentTypeItem> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(
          new DocumentTypeItem(
              (UUID) fila.get("id"),
              (String) fila.get("abbreviation"),
              (String) fila.get("name"),
              (Boolean) fila.get("is_active")));
    }
    return resultado;
  }
}
