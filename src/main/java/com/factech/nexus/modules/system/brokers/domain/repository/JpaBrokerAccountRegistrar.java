package com.factech.nexus.modules.system.brokers.domain.repository;

import com.factech.nexus.modules.system.brokers.domain.models.BrokerAccountKind;
import com.factech.nexus.modules.system.users.domain.repository.BrokerAccountRegistrar;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
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

  private final EntityManager em;
  private final BrokerAccountWriter cuentas;

  public JpaBrokerAccountRegistrar(EntityManager em, BrokerAccountWriter cuentas) {
    this.em = em;
    this.cuentas = cuentas;
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
  public void declare(
      UUID accountId, UUID userId, UUID brokerId, String externalId, UUID sellerId) {
    // `RN-SP-072`: llegó antes por el broker, con el origen de este vendedor.
    if (cuentas.claim(brokerId, externalId, userId, sellerId).isPresent()) {
      return;
    }
    // `CONSUMIDOR` sin consultar los roles (`RN-SP-068`): el enlace registra clientes.
    // Su origen, la `VENDEDOR` del vendedor del enlace en ese broker (`RN-SP-070`).
    UUID origen = cuentas.vendorAccount(sellerId, brokerId).orElse(null);
    try {
      cuentas.insert(
          accountId, userId, brokerId, externalId, BrokerAccountKind.CONSUMIDOR, null, origen);
    } catch (BusinessRuleException ocupada) {
      if (!"EX-009".equals(ocupada.errorCode())) {
        throw ocupada;
      }
      // SÍ dice qué pasó, al revés que el documento repetido de `EX-007`, y
      // la asimetría es deliberada: quien declara una cuenta de broker es su
      // titular —tuvo que abrirla— y necesita saber que ya está tomada,
      // porque significa que alguien se la atribuyó. Un número de documento,
      // en cambio, es un dato que se consigue.
      String mensaje = "Esa cuenta de broker ya está declarada por otra persona.";
      throw new BusinessRuleException(
          "EX-009", mensaje, List.of(new FieldError("brokerAccountId", "EX-009", mensaje)));
    }
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasFirstDeposit(UUID userId) {
    return !em.createNativeQuery(
            """
            SELECT 1 FROM user_brokers
             WHERE user_id = CAST(:persona AS uuid) AND status = 'FIRST_DEPOSIT'
             LIMIT 1
            """)
        .setParameter("persona", userId)
        .getResultList()
        .isEmpty();
  }
}
