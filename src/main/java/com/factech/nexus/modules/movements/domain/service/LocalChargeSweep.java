package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.domain.repository.LocalChargeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * <b>El barrido de los cobros locales pendientes</b> (`RF-MV-050`, `RN-MV-064`): PayRetailers no
 * reenvía un aviso que no se recibió, de modo que cada pocos minutos se pregunta por los cobros que
 * llevan un rato sin noticias. <b>No decide nada</b>: elige por quién preguntar y llama a {@link
 * LocalChargeReconciler#conciliar}, como un aviso.
 *
 * <p><b>Una instancia a la vez</b>: la elección del lote toma un bloqueo consultivo de PostgreSQL,
 * como el cierre de comisiones. La que no lo consigue no barre en esa pasada.
 */
@Component
public class LocalChargeSweep {

  private static final Logger LOG = LoggerFactory.getLogger(LocalChargeSweep.class);

  /** La llave del bloqueo consultivo: «PR» y «SW» en ASCII, para no chocar con otras. */
  static final long LLAVE = 0x5052_5357L;

  private final LocalPaymentGateway pasarela;
  private final LocalChargeRepository cobros;
  private final LocalChargeReconciler conciliacion;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final Duration espera;
  private final int lote;
  private final Clock reloj;

  @Autowired
  public LocalChargeSweep(
      LocalPaymentGateway pasarela,
      LocalChargeRepository cobros,
      LocalChargeReconciler conciliacion,
      JdbcTemplate jdbc,
      PlatformTransactionManager transacciones,
      @Value("${nexus.payretailers.reconcile-after:PT10M}") Duration espera,
      @Value("${nexus.payretailers.reconcile-batch:50}") int lote) {
    this(pasarela, cobros, conciliacion, jdbc, transacciones, espera, lote, Clock.systemUTC());
  }

  LocalChargeSweep(
      LocalPaymentGateway pasarela,
      LocalChargeRepository cobros,
      LocalChargeReconciler conciliacion,
      JdbcTemplate jdbc,
      PlatformTransactionManager transacciones,
      Duration espera,
      int lote,
      Clock reloj) {
    this.pasarela = pasarela;
    this.cobros = cobros;
    this.conciliacion = conciliacion;
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(transacciones);
    this.espera = espera;
    this.lote = lote;
    this.reloj = reloj;
  }

  @Scheduled(cron = "${nexus.payretailers.reconcile-cron:0 */5 * * * *}")
  public void programado() {
    barrer(OffsetDateTime.now(reloj));
  }

  /**
   * Una pasada: los cobros pendientes abiertos antes de {@code ahora − espera}, hasta el lote.
   *
   * @return cuántos cobros se preguntaron
   */
  public int barrer(OffsetDateTime ahora) {
    if (!pasarela.enabled()) {
      return 0;
    }
    List<UUID> pendientes =
        tx.execute(
            e -> {
              Boolean mio =
                  jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, LLAVE);
              return Boolean.TRUE.equals(mio)
                  ? cobros.toReconcile(ahora.minus(espera), lote)
                  : List.<UUID>of();
            });
    int preguntados = 0;
    for (UUID pago : pendientes == null ? List.<UUID>of() : pendientes) {
      try {
        conciliacion.conciliar(pago);
        preguntados++;
      } catch (RuntimeException fallo) {
        // `FA-001`: el siguiente sigue; este se volverá a preguntar en la próxima pasada.
        LOG.warn("El barrido no pudo conciliar el pago {}: {}", pago, fallo.toString());
      }
    }
    return preguntados;
  }
}
