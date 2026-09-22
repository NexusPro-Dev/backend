package com.factech.nexus.modules.system.brokers.domain.repository;

import com.factech.nexus.modules.system.brokers.application.BrokerItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de lectura del catálogo (`RF-SP-052` · `T-04`).
 *
 * <p><b>Sin entidad JPA</b>, igual que el catálogo de tipos de documento: lo que se devuelve es una
 * proyección de tres campos, y mapear una entidad para descartarla acto seguido sugiere que alguien
 * podría cargarla y escribirla — que es justo lo que `RN-SP-039` prohíbe.
 */
@Repository
public class JpaBrokerQueryRepository implements BrokerQueryRepository {

  private final EntityManager em;

  public JpaBrokerQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<BrokerItem> findAll(boolean incluirInactivos) {
    String sql =
        "SELECT b.id AS id, b.name AS name, b.is_active AS is_active"
            + " FROM brokers b"
            // El predicado se arma aquí y no con `(:flag OR b.is_active)`: ese
            // patrón impide que el motor use un índice, y es el que `RF-SP-002`
            // descartó para todo el módulo.
            + (incluirInactivos ? "" : " WHERE b.is_active")
            // El orden lo fija el servidor y el cliente no lo cambia
            // (`CA-SP-605`). Lo hace bien porque la columna declara su
            // intercalación: sin `COLLATE "es-x-icu"` un nombre acentuado
            // caería al final y el desplegable parecería roto.
            + " ORDER BY b.name";

    List<Tuple> filas = em.createNativeQuery(sql, Tuple.class).getResultList();
    List<BrokerItem> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(
          new BrokerItem(
              (UUID) fila.get("id"), (String) fila.get("name"), (Boolean) fila.get("is_active")));
    }
    return resultado;
  }
}
