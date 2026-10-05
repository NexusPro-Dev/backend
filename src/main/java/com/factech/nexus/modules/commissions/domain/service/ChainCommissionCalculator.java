package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.domain.models.AccrualOutcome;
import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import com.factech.nexus.modules.commissions.domain.repository.CommissionResolutionRepository.ResolvedRate;
import com.factech.nexus.shared.persistence.MinorUnits;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * <b>Lo que devenga cada nivel de la cadena por una línea</b>, y si la cadena cabe en ella
 * (`RF-CM-013` · `T-08`).
 *
 * <p><b>Puro</b>: no lee nada. Recibe la cadena ya resuelta y devuelve un veredicto, de modo que la
 * base bruta, el fijo por unidad y el tope —las tres reglas que deciden dinero— se prueban sin base
 * de datos.
 *
 * <ul>
 *   <li><b>La base es el bruto</b>, {@code unit_price × quantity} (`RN-CM-023`): el descuento de la
 *       línea lo absorbe la empresa.
 *   <li><b>El fijo paga por unidad</b>, {@code fixed_amount × quantity} (`RN-CM-023`).
 *   <li><b>Si la cadena pasa del importe de la línea, nadie cobra</b> (`RN-CM-026`): se rechaza y
 *       no se recorta, porque recortar decidiría en silencio a quién se le quita. <b>Salvo en una
 *       línea de importe cero</b>: un producto gratuito solo comisiona por importe fijo, y ese fijo
 *       no tiene tope (`RN-CM-020`); un porcentaje de cero es cero y no suma.
 * </ul>
 *
 * <p>Los importes se calculan con <b>cuatro decimales</b> y el tope se compara con ellos, sin
 * redondear (`RN-CM-026`). <b>Solo lo que devenga se redondea</b>, con {@code HALF_UP} a dos
 * decimales, porque {@code commission_amount} guarda centésimas desde `V65` (ADR-006, `CA-CM-336`).
 * Se redondea aquí, en el dominio, y no en el convertidor: el lote suma estas cifras ya redondeadas
 * y el abono paga exactamente esa suma.
 */
public final class ChainCommissionCalculator {

  private static final int ESCALA = 4;
  private static final BigDecimal CIEN = BigDecimal.valueOf(100);

  private ChainCommissionCalculator() {}

  /**
   * @param unitPrice el precio unitario copiado en la línea
   * @param quantity la cantidad de la línea
   * @param cadena cada nivel en orden —0 quien vendió— con su tasa, o vacía si no tiene
   */
  public static Verdict calcular(BigDecimal unitPrice, int quantity, List<Level> cadena) {
    BigDecimal base = unitPrice.multiply(BigDecimal.valueOf(quantity));
    List<LevelCommission> cobran = new ArrayList<>();
    BigDecimal suma = BigDecimal.ZERO;
    for (Level nivel : cadena) {
      if (nivel.rate().isEmpty()) {
        // `RN-CM-012` y `RN-CM-025`: no cobra y NO interrumpe la cadena.
        continue;
      }
      ResolvedRate tasa = nivel.rate().get();
      BigDecimal importe =
          tasa.rateType() == CommissionRateType.PORCENTAJE
              ? base.multiply(tasa.value()).divide(CIEN, ESCALA, RoundingMode.HALF_UP)
              : tasa.value()
                  .multiply(BigDecimal.valueOf(quantity))
                  .setScale(ESCALA, RoundingMode.HALF_UP);
      cobran.add(new LevelCommission(nivel.userId(), nivel.level(), tasa, importe));
      suma = suma.add(importe);
    }
    if (cobran.isEmpty()) {
      return Verdict.sinComision();
    }
    if (base.signum() > 0 && suma.compareTo(base) > 0) {
      return Verdict.rechazada(
          "La cadena suma "
              + suma.stripTrailingZeros().toPlainString()
              + " y la línea vale "
              + base.stripTrailingZeros().toPlainString()
              + ": pasa del 100 % de la línea.");
    }
    return Verdict.devengada(cobran.stream().map(LevelCommission::redondeada).toList());
  }

  /** Un nivel de la cadena y la tasa que resolvió, si la tiene. */
  public record Level(UUID userId, int level, Optional<ResolvedRate> rate) {}

  /** Lo que cobra un nivel. */
  public record LevelCommission(UUID userId, int level, ResolvedRate rate, BigDecimal amount) {

    /** La misma, con el importe en las centésimas que se guardan (`CA-CM-336`). */
    LevelCommission redondeada() {
      return new LevelCommission(
          userId, level, rate, amount.setScale(MinorUnits.ESCALA, RoundingMode.HALF_UP));
    }
  }

  /** El desenlace de la línea, con lo que cobra cada nivel si devengó. */
  public record Verdict(AccrualOutcome outcome, List<LevelCommission> commissions, String reason) {

    static Verdict devengada(List<LevelCommission> cobran) {
      return new Verdict(AccrualOutcome.DEVENGADA, List.copyOf(cobran), null);
    }

    static Verdict sinComision() {
      return new Verdict(AccrualOutcome.SIN_COMISION, List.of(), null);
    }

    static Verdict rechazada(String motivo) {
      return new Verdict(AccrualOutcome.RECHAZADA, List.of(), motivo);
    }
  }
}
