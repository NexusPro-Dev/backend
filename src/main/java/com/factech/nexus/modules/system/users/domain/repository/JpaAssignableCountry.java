package com.factech.nexus.modules.system.users.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta directa sobre `countries`, igual que {@link JpaMembershipCatalog} la hace sobre
 * `memberships` y {@link JpaRoleCatalog} sobre `roles`.
 *
 * <p>No pasa por el agregado {@code Country} a propósito: es de otro submódulo, y lo que el alta de
 * una persona necesita saber de un país cabe en cuatro campos.
 *
 * <p><b>Se lee con bloqueo compartido</b> —{@code FOR SHARE}—, por el mismo motivo por el que el
 * alta lee así los roles y el superior: `RF-SP-022` puede estar desactivando ese país en la misma
 * ventana. Sin el bloqueo, la comprobación gana la carrera unas veces y otras no, y el resultado es
 * una persona registrada en un país que acababa de retirarse de la circulación.
 *
 * <p><b>Compartido y no exclusivo</b>: aquí solo se lee. Un {@code FOR UPDATE} serializaría entre
 * sí todas las altas que declaran el mismo país, que en la práctica son casi todas.
 */
@Repository
public class JpaAssignableCountry implements AssignableCountry {

  private static final String PROYECCION =
      "SELECT c.id AS id, c.code AS code, c.name AS name, c.is_active AS is_active"
          + " FROM countries c";

  private final EntityManager em;

  public JpaAssignableCountry(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CountryRef> find(UUID countryId) {
    if (countryId == null) {
      return Optional.empty();
    }
    return leer(PROYECCION + " WHERE c.id = :clave FOR SHARE", countryId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CountryRef> findByCode(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    // La normalización vive aquí y no en cada DTO que llame: el formato lo
    // impone `ck_countries_code_format` sobre esta tabla, y quien conoce la
    // restricción es quien la consulta.
    return leer(PROYECCION + " WHERE c.code = :clave FOR SHARE", code.trim().toUpperCase());
  }

  private Optional<CountryRef> leer(String sql, Object clave) {
    List<Tuple> filas =
        em.createNativeQuery(sql, Tuple.class).setParameter("clave", clave).getResultList();

    return filas.stream()
        .map(
            fila ->
                new CountryRef(
                    (UUID) fila.get("id"),
                    // `char(3)` en el motor: PostgreSQL no rellena con espacios
                    // mientras el CHECK exija tres mayúsculas exactas, pero el
                    // recorte cuesta nada y protege de una migración futura que
                    // ensanche la columna, que es justo lo que `V42` hizo una vez.
                    ((String) fila.get("code")).trim(),
                    (String) fila.get("name"),
                    (Boolean) fila.get("is_active")))
        .findFirst();
  }
}
