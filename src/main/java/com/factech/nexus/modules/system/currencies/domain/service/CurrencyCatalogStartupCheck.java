package com.factech.nexus.modules.system.currencies.domain.service;

import com.factech.nexus.modules.system.currencies.domain.repository.CurrencyQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Comprueba al arrancar que existe la moneda con la que opera el sistema (`RF-SP-019` §2).
 *
 * <p><b>Falla el arranque, y no advierte.</b> Un backend financiero que atiende peticiones sin
 * moneda de referencia produce datos que habrá que corregir después uno por uno, mientras que un
 * arranque fallido es visible de inmediato y no corrompe nada. El coste —que un error de siembra
 * deje el servicio caído— es precisamente el aviso que se quiere.
 *
 * <p><b>Es de arranque y no de cada petición</b>: el catálogo solo cambia por migración o por
 * `RF-SP-023`, y este último tiene sus propias restricciones — {@code ck_currencies_default_active}
 * impide dejar inactiva la moneda por defecto.
 *
 * <p>Comprueba <b>exactamente una</b>: {@code uq_currencies_single_default} ya garantiza que no
 * haya dos, de modo que el caso que esto añade es el cero. Se afirma igualmente el «exactamente»
 * para que la comprobación siga diciendo la verdad si algún día ese índice desapareciera.
 *
 * <p><b>Vive en el módulo y no en {@code shared/config}, como pedía `plan.md` §3.</b> La regla de
 * `architecture.md` §5.3 —y la prueba de ArchUnit que la verifica desde `RF-SP-001`— prohíbe que la
 * infraestructura transversal dependa de un módulo de negocio, y esta comprobación consulta el
 * catálogo de `SP`. Situarla aquí conserva la regla sin perder nada: se ejecuta igual al arrancar,
 * porque lo que la dispara es implementar {@link ApplicationRunner}, no el paquete donde viva.
 */
@Component
public class CurrencyCatalogStartupCheck implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(CurrencyCatalogStartupCheck.class);

  private final CurrencyQueryRepository monedas;

  public CurrencyCatalogStartupCheck(CurrencyQueryRepository monedas) {
    this.monedas = monedas;
  }

  @Override
  public void run(ApplicationArguments args) {
    long porDefecto = monedas.countDefaultActive();
    if (porDefecto != 1) {
      throw new IllegalStateException(
          "El catálogo de monedas no tiene exactamente una moneda por defecto activa (encontradas: "
              + porDefecto
              + "). Revise la migración de siembra: el sistema no puede operar sin moneda de"
              + " referencia.");
    }
    LOG.info("Catálogo de monedas verificado: hay una moneda por defecto activa.");
  }
}
