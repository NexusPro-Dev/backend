package com.factech.nexus.modules.system.brokers.domain.repository;

import com.factech.nexus.modules.system.users.domain.repository.BrokerAccountRegistrar;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * La primera escritura sobre {@code user_brokers} (`RF-SP-045` · `T-19`).
 *
 * <p><b>La tabla existía desde el 08-09-2026 sin nadie que la escribiera</b>, porque quién declara
 * una cuenta no estaba decidido. La respuesta resultó ser <b>el titular, al registrarse</b>.
 *
 * <p><b>Traduce la violación de `uq_user_brokers_cuenta` a un `409` por NOMBRE DE RESTRICCIÓN</b>,
 * y nunca por el texto del driver — ese texto cambia entre versiones de PostgreSQL y la traducción
 * se rompería sin que ninguna prueba lo dijera. Aquí sí basta el nombre, al revés que en el {@code
 * EXCLUDE} de las tasas de cambio: un {@code UNIQUE} corriente sí lo trae.
 *
 * <p><b>El {@code flush} es explícito y no un descuido</b>: sin él, la violación saldría al
 * confirmar la transacción —fuera del caso de uso y fuera de este {@code catch}— y el actor
 * recibiría un {@code 500} sobre una regla de negocio que el sistema conoce y sabe explicar.
 */
@Repository
public class JpaBrokerAccountRegistrar implements BrokerAccountRegistrar {

  private static final String UQ_CUENTA = "uq_user_brokers_cuenta";

  private final EntityManager em;

  public JpaBrokerAccountRegistrar(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<BrokerRef> find(UUID brokerId) {
    if (brokerId == null) {
      return Optional.empty();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT id, name, is_active FROM brokers WHERE id = CAST(:id AS uuid)", Tuple.class)
            .setParameter("id", brokerId)
            .getResultList();

    return filas.stream()
        .findFirst()
        .map(
            fila ->
                new BrokerRef(
                    (UUID) fila.get("id"),
                    (String) fila.get("name"),
                    (Boolean) fila.get("is_active")));
  }

  @Override
  @Transactional
  public void declare(UUID accountId, UUID userId, UUID brokerId, String externalId) {
    try {
      em.createNativeQuery(
              """
              INSERT INTO user_brokers (id, user_id, broker_id, external_id)
              VALUES (CAST(:id AS uuid), CAST(:usuario AS uuid), CAST(:broker AS uuid), :cuenta)
              """)
          .setParameter("id", accountId)
          .setParameter("usuario", userId)
          .setParameter("broker", brokerId)
          .setParameter("cuenta", externalId)
          .executeUpdate();

      // Explícito: ver el Javadoc de la clase.
      em.flush();
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  private static RuntimeException traducir(PersistenceException fallo) {
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
      if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion
          && UQ_CUENTA.equals(violacion.getConstraintName())) {

        // SÍ dice qué pasó, al revés que el documento repetido de `EX-007`, y
        // la asimetría es deliberada: quien declara una cuenta de broker es su
        // titular —tuvo que abrirla— y necesita saber que ya está tomada,
        // porque significa que alguien se la atribuyó. Un número de documento,
        // en cambio, es un dato que se consigue.
        String mensaje = "Esa cuenta de broker ya está declarada por otra persona.";
        return new BusinessRuleException(
            "EX-009", mensaje, List.of(new FieldError("brokerAccountId", "EX-009", mensaje)));
      }
    }
    return fallo;
  }
}
