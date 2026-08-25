package com.factech.nexus.modules.system.auth.application;

import com.factech.nexus.modules.system.auth.domain.service.PurgeExpiredTokensService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara la purga de sesiones caducadas a su hora (issue #25).
 *
 * <p><b>Separado del servicio a propósito.</b> Aquí solo vive <i>cuándo</i>; el <i>qué</i> está en
 * {@link PurgeExpiredTokensService}, que es lo que las pruebas invocan directamente. Una lógica de
 * borrado que solo se pueda ejercitar esperando a que salte un reloj no se prueba: se supone.
 *
 * <p><b>Se puede apagar</b>, y la suite lo apaga. Con la purga activa, una prueba que crea sesiones
 * podría encontrárselas retiradas por una tarea que corre por su cuenta — un fallo intermitente que
 * no se parece en nada a su causa.
 *
 * <p><b>La hora por defecto es de madrugada</b> y no cada hora: la purga toma un cerrojo y borra en
 * bloque sobre la tabla que sostiene la autenticación de todo el sistema. Nada de esto urge — lo
 * que se retira lleva días sin autenticar a nadie.
 *
 * <p>Con varias instancias, las tres despiertan y solo una purga: el cerrojo de aviso lo resuelve
 * en el motor, que es el único sitio donde las tres se ven.
 */
@Component
@ConditionalOnProperty(
    name = "nexus.security.token-purge.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ExpiredTokenPurgeJob {

  private static final Logger LOG = LoggerFactory.getLogger(ExpiredTokenPurgeJob.class);

  private final PurgeExpiredTokensService purga;

  public ExpiredTokenPurgeJob(PurgeExpiredTokensService purga) {
    this.purga = purga;
  }

  /**
   * Una excepción aquí no puede tumbar el planificador.
   *
   * <p>Spring cancela las ejecuciones futuras de una tarea que lanza, de modo que un fallo puntual
   * —la base reiniciándose, un bloqueo— apagaría la purga <b>para siempre</b> sin que nadie se
   * entere hasta que la tabla estorbe. Se traga y se registra: mañana lo vuelve a intentar.
   */
  @Scheduled(cron = "${nexus.security.token-purge.cron}", zone = "UTC")
  public void ejecutar() {
    try {
      purga.purgar();
    } catch (RuntimeException fallo) {
      LOG.error(
          "La purga de sesiones caducadas falló; se reintentará en la próxima ventana", fallo);
    }
  }
}
