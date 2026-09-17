package com.factech.nexus.modules.system.exchangerates.domain.service;

import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog.CurrencyView;
import com.factech.nexus.modules.system.exchangerates.application.ExchangeRateResponse;
import com.factech.nexus.modules.system.exchangerates.application.RegisterExchangeRateRequest;
import com.factech.nexus.modules.system.exchangerates.domain.models.ExchangeRate;
import com.factech.nexus.modules.system.exchangerates.domain.repository.ExchangeRateRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de una tasa de cambio (`RF-SP-047`).
 *
 * <p><b>El orden de verificación es el contrato</b> (`plan.md` §5):
 *
 * <ol>
 *   <li>Las dos monedas <b>no son la misma</b> — antes de buscar nada.
 *   <li>Las dos <b>existen y están activas</b>.
 *   <li>El precio y la vigencia, en el agregado.
 *   <li>El solapamiento: se comprueba <b>para el mensaje</b> y decide el {@code EXCLUDE}.
 * </ol>
 *
 * <p><b>El paso 1 va antes que el 2 a propósito</b>: si se buscaran primero las monedas, una tasa
 * de `USD` a `USD` saldría como «la moneda no existe» —un {@code 422} sobre un dato que el actor sí
 * envió— en vez del {@code 400} que le corresponde. Es la misma lección que `RF-PM-001` aprendió
 * con la condición cruzada del tipo.
 */
@Service
public class RegisterExchangeRateService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "exchange_rates";

  private final ExchangeRateRepository tasas;
  private final CurrencyCatalog monedas;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  @Autowired
  public RegisterExchangeRateService(
      ExchangeRateRepository tasas,
      CurrencyCatalog monedas,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this(tasas, monedas, auditoria, ids, Clock.systemUTC());
  }

  RegisterExchangeRateService(
      ExchangeRateRepository tasas,
      CurrencyCatalog monedas,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Clock reloj) {
    this.tasas = tasas;
    this.monedas = monedas;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  @Transactional
  public ExchangeRateResponse register(RegisterExchangeRateRequest peticion) {
    ExchangeRate.verificarMonedas(peticion.sourceCurrencyId(), peticion.targetCurrencyId());

    CurrencyView origen = verificarMoneda(peticion.sourceCurrencyId(), "sourceCurrencyId");
    CurrencyView destino = verificarMoneda(peticion.targetCurrencyId(), "targetCurrencyId");

    ExchangeRate nueva =
        ExchangeRate.create(
            ids.next(),
            peticion.sourceCurrencyId(),
            peticion.targetCurrencyId(),
            peticion.price(),
            peticion.validFrom(),
            peticion.validTo(),
            peticion.activa(),
            OffsetDateTime.now(reloj));

    verificarSolapamiento(peticion);

    tasas.save(nueva);
    // El volcado explícito es lo que separa un 409 de un 500: sin él, la
    // violación del EXCLUDE llega en el `commit`, cuando ya no hay nadie
    // escuchando que pueda traducirla.
    tasas.flush();

    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, nueva.getId(), ChangeAction.CREATE, nueva.instantanea()));

    return ExchangeRateResponse.from(nueva, origen, destino);
  }

  /**
   * `EX-001`. <b>Inexistente y desactivada se distinguen</b>: una es un dato equivocado y la otra
   * una decisión del sistema que el actor no puede saltarse. Devolver el mismo mensaje haría que
   * quien escribió bien el identificador buscara el error donde no está.
   */
  private CurrencyView verificarMoneda(UUID id, String campo) {
    CurrencyView moneda =
        monedas.find(id).orElseThrow(() -> noProcede(campo, "La moneda indicada no existe."));
    if (!moneda.active()) {
      throw noProcede(campo, "La moneda indicada está desactivada y no admite tasas nuevas.");
    }
    return moneda;
  }

  private static UnprocessableEntityException noProcede(String campo, String mensaje) {
    return new UnprocessableEntityException(
        "EX-001", mensaje, List.of(new FieldError(campo, "EX-001", mensaje)));
  }

  /**
   * `EX-002`, y <b>solo para el mensaje</b>.
   *
   * <p>La garantía es {@code uq_exchange_rates_vigente}: dos altas simultáneas pasan las dos por
   * aquí sin verse. Lo que esta consulta compra es poder decir que el periodo choca <b>antes</b> de
   * intentarlo, que es el camino normal.
   */
  private void verificarSolapamiento(RegisterExchangeRateRequest peticion) {
    if (!peticion.activa()) {
      // Una tasa que nace suspendida no participa en la restricción: el EXCLUDE
      // es parcial sobre `is_active`. Comprobarlo aquí rechazaría altas que la
      // base admite.
      return;
    }
    boolean choca =
        tasas.seSolapa(
            peticion.sourceCurrencyId(),
            peticion.targetCurrencyId(),
            peticion.validFrom(),
            peticion.validTo());
    if (choca) {
      String mensaje = "Ya hay una tasa vigente para ese par en ese periodo.";
      throw new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("validFrom", "EX-002", mensaje)));
    }
  }
}
