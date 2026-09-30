package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.PointsRateResponse;
import com.factech.nexus.modules.movements.application.PointsRequests;
import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.repository.PointsRateRepository;
import com.factech.nexus.modules.movements.domain.repository.PointsRateRepository.RateRow;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog.CurrencyView;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las tasas de puntos: fijarlas (`RF-MV-025`) y consultarlas (`RF-MV-026`). Y la lectura de la
 * vigente que compartan la compra (`RF-MV-027`) y el pago con puntos (`RF-MV-030`).
 */
@Service
public class PointsRateService {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "points_rates";

  /** {@code numeric(12,4)}: ocho cifras enteras y cuatro decimales (`VAL-002`). */
  private static final int DECIMALES = 4;

  private static final int ENTEROS = 8;

  private final PointsRateRepository tasas;
  private final CurrencyCatalog monedas;
  private final AuthenticatedActor actor;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public PointsRateService(
      PointsRateRepository tasas,
      CurrencyCatalog monedas,
      AuthenticatedActor actor,
      AuditWriter auditoria) {
    this(tasas, monedas, actor, auditoria, Clock.systemUTC());
  }

  PointsRateService(
      PointsRateRepository tasas,
      CurrencyCatalog monedas,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      Clock reloj) {
    this.tasas = tasas;
    this.monedas = monedas;
    this.actor = actor;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  /** Lo que salió de fijar: la tasa que rige, y si es nueva o ya lo era (`FA-001`). */
  public record SetResult(PointsRateResponse rate, boolean created) {}

  // ---------------------------------------------------------------------------
  // `RF-MV-025` — fijar
  // ---------------------------------------------------------------------------

  @Transactional
  public SetResult set(PointsRequests.SetRate peticion) {
    // 1. Los dos datos, con los errores juntos, antes de consultar nada.
    UUID monedaId = peticion == null ? null : peticion.currencyId();
    BigDecimal valor = peticion == null ? null : peticion.pointsPerUnit();
    List<FieldError> errores = new ArrayList<>();
    if (monedaId == null) {
      errores.add(new FieldError("currencyId", "VAL-001", "La moneda es obligatoria."));
    }
    String problema = problemaDelValor(valor);
    if (problema != null) {
      errores.add(new FieldError("pointsPerUnit", "VAL-002", problema));
    }
    if (!errores.isEmpty()) {
      throw new ValidationException(errores.get(0).code(), errores.get(0).message(), errores);
    }

    // 2. La moneda: existe (`EX-002`) y está activa (`EX-003`).
    CurrencyView moneda =
        monedas
            .find(monedaId)
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-002",
                        "La moneda indicada no existe.",
                        List.of(
                            new FieldError(
                                "currencyId", "EX-002", "La moneda indicada no existe."))));
    if (!moneda.active()) {
      String mensaje = "La moneda " + moneda.code() + " está inactiva: no vende puntos.";
      throw new BusinessRuleException(
          "EX-003", mensaje, List.of(new FieldError("currencyId", "EX-003", mensaje)));
    }

    // 3. `FA-001`: la que rige ya es esa. Nada que escribir ni que auditar.
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    Optional<RateRow> vigente = tasas.current(moneda.id(), ahora);
    if (vigente.isPresent() && vigente.get().pointsPerUnit().compareTo(valor) == 0) {
      return new SetResult(respuesta(vigente.get()), false);
    }

    // 4. La tasa nueva, que rige desde ahora. La anterior no se toca (`RN-MV-050`).
    UUID id = UUID.randomUUID();
    BigDecimal escalado = valor.setScale(DECIMALES);
    tasas.insert(id, moneda.id(), escalado, ahora, actor.id());

    Map<String, Object> antes = new LinkedHashMap<>();
    antes.put("points_per_unit", vigente.map(r -> r.pointsPerUnit().toPlainString()).orElse(null));
    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("currency_id", moneda.id().toString());
    despues.put("points_per_unit", escalado.toPlainString());
    despues.put("valid_from", ahora.toString());
    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", antes);
    cambios.put("after", despues);
    auditoria.recordChange(new ChangeEvent(MODULO, ENTIDAD, id, ChangeAction.CREATE, cambios));

    return new SetResult(
        new PointsRateResponse(
            id, new SaleResponse.Money(moneda.id(), moneda.code()), escalado, ahora),
        true);
  }

  private static String problemaDelValor(BigDecimal valor) {
    if (valor == null) {
      return "Los puntos por unidad son obligatorios.";
    }
    if (valor.signum() <= 0) {
      return "Los puntos por unidad tienen que ser mayores que cero.";
    }
    BigDecimal limpio = valor.stripTrailingZeros();
    if (limpio.scale() > DECIMALES) {
      return "Los puntos por unidad admiten hasta " + DECIMALES + " decimales.";
    }
    if (limpio.precision() - limpio.scale() > ENTEROS) {
      return "Los puntos por unidad admiten hasta " + ENTEROS + " cifras enteras.";
    }
    return null;
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-026` — consultar las vigentes
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<PointsRateResponse> current() {
    return tasas.currentOfActiveCurrencies(OffsetDateTime.now(reloj)).stream()
        .map(PointsRateService::respuesta)
        .toList();
  }

  // ---------------------------------------------------------------------------
  // La vigente, para comprar y pagar
  // ---------------------------------------------------------------------------

  /**
   * La tasa que rige ahora en esa moneda, o {@code 409} si no tiene: sin tasa, la moneda no vende
   * puntos ni acepta pagos con ellos (`RN-MV-050`).
   *
   * @param codigo el código de error que corresponde a quien pregunta
   */
  RateRow vigenteOConflicto(CurrencyView moneda, String codigo) {
    return tasas
        .current(moneda.id(), OffsetDateTime.now(reloj))
        .orElseThrow(
            () -> {
              String mensaje =
                  "La moneda " + moneda.code() + " no tiene tasa de puntos: no opera con puntos.";
              return new BusinessRuleException(
                  codigo, mensaje, List.of(new FieldError("currencyId", codigo, mensaje)));
            });
  }

  static PointsRateResponse respuesta(RateRow fila) {
    return new PointsRateResponse(
        fila.id(),
        new SaleResponse.Money(fila.currencyId(), fila.currencyCode()),
        fila.pointsPerUnit(),
        fila.validFrom());
  }
}
