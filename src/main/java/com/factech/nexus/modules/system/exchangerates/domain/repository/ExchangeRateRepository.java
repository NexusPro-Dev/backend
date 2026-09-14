package com.factech.nexus.modules.system.exchangerates.domain.repository;

import com.factech.nexus.modules.system.exchangerates.domain.models.ExchangeRate;
import java.time.LocalDate;
import java.util.UUID;

/** Escritura de tasas de cambio (`RF-SP-047`). */
public interface ExchangeRateRepository {

  ExchangeRate save(ExchangeRate tasa);

  /**
   * ¿Hay ya una tasa <b>activa y viva</b> de ese par cuya vigencia toque a la que se declara?
   *
   * <p><b>Existe para el MENSAJE, no para la garantía.</b> Dos altas simultáneas la pasan las dos
   * —ninguna ve a la otra— y quien decide es {@code uq_exchange_rates_vigente}. Es el mismo reparto
   * que `RF-PM-001` tiene con {@code uq_products_code}: la verificación previa redacta, la
   * restricción decide.
   */
  boolean seSolapa(UUID origen, UUID destino, LocalDate desde, LocalDate hasta);

  /**
   * Vuelca lo pendiente traduciendo la violación del {@code EXCLUDE}.
   *
   * <p><b>Sin este volcado la violación llega en el {@code commit}</b>, cuando ya no hay nadie
   * escuchando que pueda traducirla, y el actor recibe un {@code 500} sobre una regla de negocio
   * perfectamente expresable.
   */
  void flush();
}
