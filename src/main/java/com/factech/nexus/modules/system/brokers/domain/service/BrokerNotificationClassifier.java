package com.factech.nexus.modules.system.brokers.domain.service;

import com.factech.nexus.modules.system.brokers.domain.repository.BrokerNotificationRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, asocia a su broker los avisos guardados sin él y los interpreta (`RF-SP-078` ·
 * `T-21`, `RN-SP-076`, `plan.md` §16).
 *
 * <h2>Por qué al arrancar</h2>
 *
 * <p>Un aviso queda sin broker cuando su {@code advertiser} no es el de ningún broker activo, y eso
 * solo cambia cuando <b>una migración</b> carga ese {@code advertiser} o enciende el broker
 * (`RN-SP-039`). Las migraciones corren al arrancar; después de ellas, aquí. Una tarea periódica
 * miraría todo el día algo que no cambia.
 *
 * <p><b>Uno a uno y cada uno en su transacción</b>, en el orden en que llegaron: un registro crea
 * la cuenta antes de que el depósito que llegó después la mueva. <b>Un fallo no tumba el
 * arranque</b>: el aviso se queda sin broker, se avisa con su identificador, y el siguiente
 * arranque lo reintenta.
 */
@Component
public class BrokerNotificationClassifier implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(BrokerNotificationClassifier.class);

  private final BrokerNotificationRepository avisos;
  private final ReceiveBrokerNotificationService recepcion;

  public BrokerNotificationClassifier(
      BrokerNotificationRepository avisos, ReceiveBrokerNotificationService recepcion) {
    this.avisos = avisos;
    this.recepcion = recepcion;
  }

  @Override
  public void run(ApplicationArguments args) {
    List<UUID> pendientes = avisos.unclassifiedWithKnownAdvertiser();
    if (pendientes.isEmpty()) {
      return;
    }
    int fallidos = 0;
    for (UUID id : pendientes) {
      try {
        recepcion.classify(id);
      } catch (RuntimeException fallo) {
        fallidos++;
        LOG.warn(
            "El aviso de broker {} no se pudo asociar e interpretar; queda sin broker y se"
                + " reintentará en el siguiente arranque.",
            id,
            fallo);
      }
    }
    LOG.info(
        "Avisos de broker sin broker asociados al arrancar: {} de {}.",
        pendientes.size() - fallidos,
        pendientes.size());
  }
}
