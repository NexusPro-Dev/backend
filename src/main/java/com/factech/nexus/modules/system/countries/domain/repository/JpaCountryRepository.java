package com.factech.nexus.modules.system.countries.domain.repository;

import com.factech.nexus.modules.system.countries.domain.models.Country;
import com.factech.nexus.modules.system.countries.domain.models.CountryCode;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Adaptador de escritura del catálogo de países. */
@Repository
public class JpaCountryRepository implements CountryRepository {

  private static final String UQ_CODIGO = "uq_countries_code";
  private static final String UQ_NOMBRE = "uq_countries_name";

  private final EntityManager em;

  public JpaCountryRepository(EntityManager em) {
    this.em = em;
  }

  /**
   * Persiste y fuerza el volcado, para poder traducir el duplicado a un {@code 409} legible en
   * lugar de dejar que salte al confirmar, fuera de este método.
   */
  @Override
  public Country save(Country pais) {
    try {
      em.persist(pais);
      em.flush();
      return pais;
    } catch (PersistenceException fallo) {
      throw traducir(fallo, pais);
    }
  }

  @Override
  public boolean existsCode(CountryCode code) {
    return !em.createQuery("SELECT 1 FROM Country c WHERE c.code = :code", Integer.class)
        .setParameter("code", code.value())
        .setMaxResults(1)
        .getResultList()
        .isEmpty();
  }

  @Override
  public boolean existsName(String name) {
    return !em.createNativeQuery(
            """
            SELECT 1 FROM countries
             WHERE f_unaccent(lower(name)) = f_unaccent(lower(CAST(:name AS text)))
             LIMIT 1
            """)
        .setParameter("name", name == null ? "" : name.trim())
        .getResultList()
        .isEmpty();
  }

  @Override
  public Optional<Country> findByIdForUpdate(UUID id) {
    return Optional.ofNullable(em.find(Country.class, id, LockModeType.PESSIMISTIC_WRITE));
  }

  /**
   * Traduce por <b>nombre de restricción</b>, nunca por el texto del mensaje del driver.
   *
   * <p><b>El mensaje del duplicado por nombre incluye el nombre enviado</b>, y es necesario: {@code
   * uq_countries_name} compara sobre la forma normalizada, de modo que el rechazo puede dispararse
   * contra una fila cuyo nombre <b>no es idéntico</b> al enviado. Sin esa precisión, el actor vería
   * rechazado un «Panama» que no encuentra en ninguna parte del catálogo.
   */
  private static RuntimeException traducir(PersistenceException fallo, Country pais) {
    String restriccion = nombreDeRestriccion(fallo);
    if (UQ_CODIGO.equals(restriccion)) {
      String mensaje = "Ya existe un país con el código " + pais.getCode() + ".";
      return new BusinessRuleException(
          "EX-001", mensaje, List.of(new FieldError("code", "EX-001", mensaje)));
    }
    if (UQ_NOMBRE.equals(restriccion)) {
      String mensaje =
          "Ya existe un país cuyo nombre coincide con '"
              + pais.getName()
              + "' sin distinguir mayúsculas ni acentos.";
      return new BusinessRuleException(
          "EX-001", mensaje, List.of(new FieldError("name", "EX-001", mensaje)));
    }
    return fallo;
  }

  private static String nombreDeRestriccion(Throwable fallo) {
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
      if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion) {
        return violacion.getConstraintName();
      }
    }
    return null;
  }
}
