package com.factech.nexus.modules.commissions.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El agregado de las tarifas (`RF-CM-001` · `T-07`).
 *
 * <p>Sin Spring: lo que se comprueba aquí es que una tarifa mal formada <b>no pueda existir dentro
 * del modelo</b>, venga por donde venga.
 *
 * <p><b>`RN-CM-006` NO se prueba aquí</b>, y no es un hueco: el solapamiento mira a otras filas y
 * vive en el motor. Su prueba es de integración, y la concurrente es la que de verdad lo verifica.
 */
class CommissionRateTest {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 8, 28, 12, 0, 0, 0, ZoneOffset.UTC);
  private static final UUID ROL = UUID.randomUUID();
  private static final UUID PRODUCTO = UUID.randomUUID();
  private static final UUID PERSONA = UUID.randomUUID();
  private static final LocalDate DESDE = LocalDate.of(2026, 1, 1);

  @Test
  @DisplayName("`RN-CM-007` — el CERO es un porcentaje válido: significa «no comisiona»")
  void elCeroEsValido() {
    CommissionRate tarifa = tarifa(BigDecimal.ZERO, null, null, DESDE, null);

    assertThat(tarifa.getPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  @DisplayName("`RN-CM-007` — se rechaza el negativo y el mayor que cien")
  void porcentajeFueraDeRango() {
    for (String malo : new String[] {"-0.01", "100.01", "101"}) {
      ValidationException fallo =
          catchThrowableOfType(
              () -> tarifa(new BigDecimal(malo), null, null, DESDE, null),
              ValidationException.class);

      assertThat(fallo).as("debía rechazar %s", malo).isNotNull();
      assertThat(fallo.errorCode()).isEqualTo("VAL-003");
    }

    assertThatCode(() -> tarifa(new BigDecimal("100"), null, null, DESDE, null))
        .as("cien exacto sí se admite")
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("`RN-CM-009` — el fin anterior al inicio se rechaza; igual al inicio se admite")
  void vigenciaCoherente() {
    ValidationException fallo =
        catchThrowableOfType(
            () -> tarifa(diez(), null, null, DESDE, DESDE.minusDays(1)), ValidationException.class);

    assertThat(fallo).isNotNull();
    assertThat(fallo.errorCode()).isEqualTo("VAL-005");

    // Una tarifa que rigió un solo día es válida.
    assertThatCode(() -> tarifa(diez(), null, null, DESDE, DESDE)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("el grado se CALCULA de qué se declaró, y no se guarda")
  void elGradoSeCalcula() {
    assertThat(tarifa(diez(), null, null, DESDE, null).scope()).isEqualTo(RateScope.ROL);
    assertThat(tarifa(diez(), PRODUCTO, null, DESDE, null).scope()).isEqualTo(RateScope.PRODUCTO);
    assertThat(tarifa(diez(), null, PERSONA, DESDE, null).scope()).isEqualTo(RateScope.PERSONA);
    assertThat(tarifa(diez(), PRODUCTO, PERSONA, DESDE, null).scope())
        .isEqualTo(RateScope.PERSONA_Y_PRODUCTO);
  }

  @Test
  @DisplayName("`RN-CM-004` — la persona pesa más que el producto")
  void laPersonaPesaMas() {
    assertThat(RateScope.PERSONA.esMasEspecificoQue(RateScope.PRODUCTO)).isTrue();
    assertThat(RateScope.PERSONA_Y_PRODUCTO.esMasEspecificoQue(RateScope.PERSONA)).isTrue();
    assertThat(RateScope.PRODUCTO.esMasEspecificoQue(RateScope.ROL)).isTrue();
    assertThat(RateScope.ROL.esMasEspecificoQue(RateScope.PERSONA)).isFalse();
  }

  @Test
  @DisplayName("el fin de vigencia se declara, se cambia y se VACÍA con nulo explícito")
  void elFinDeVigenciaSeVacia() {
    CommissionRate tarifa = tarifa(diez(), null, null, DESDE, null);

    Map<String, Object> cierre =
        tarifa.update(Patchable.ausente(), Patchable.de(DESDE.plusMonths(1)), AHORA);

    assertThat(tarifa.getValidTo()).isEqualTo(DESDE.plusMonths(1));
    assertThat(cierre.get("valid_to"))
        .isEqualTo(Map.of("before", "", "after", DESDE.plusMonths(1).toString()));

    // Reabrir: el nulo explícito es una orden que se cumple.
    Map<String, Object> reapertura =
        tarifa.update(Patchable.ausente(), Patchable.de(null), AHORA.plusDays(1));

    assertThat(tarifa.getValidTo()).isNull();
    assertThat(reapertura).containsKey("valid_to");
  }

  @Test
  @DisplayName("el porcentaje NO admite vaciarse, al revés que el fin de vigencia")
  void elPorcentajeNoSeVacia() {
    CommissionRate tarifa = tarifa(diez(), null, null, DESDE, null);

    ValidationException fallo =
        catchThrowableOfType(
            () -> tarifa.update(Patchable.de(null), Patchable.ausente(), AHORA),
            ValidationException.class);

    assertThat(fallo).isNotNull();
    assertThat(fallo.errorCode()).isEqualTo("VAL-002");
    assertThat(tarifa.getPercentage()).isEqualByComparingTo(diez());
  }

  @Test
  @DisplayName("una petición que no cambia nada no mueve `updatedAt` ni deja diff")
  void loQueNoCambiaNoSeAudita() {
    CommissionRate tarifa = tarifa(diez(), null, null, DESDE, null);
    OffsetDateTime antes = tarifa.getUpdatedAt();

    // El mismo valor con otra escala: `compareTo` y no `equals`, o el registro
    // se llenaría de cambios que no cambian nada.
    Map<String, Object> cambios =
        tarifa.update(
            Patchable.de(new BigDecimal("10.00")), Patchable.ausente(), AHORA.plusDays(1));

    assertThat(cambios).isEmpty();
    assertThat(tarifa.getUpdatedAt()).isEqualTo(antes);
  }

  @Test
  @DisplayName("`RF-CM-004` — retirar NO toca la vigencia, y no es idempotente")
  void retirarNoTocaLaVigencia() {
    CommissionRate tarifa = tarifa(diez(), null, null, DESDE, DESDE.plusMonths(1));

    assertThat(tarifa.delete(AHORA)).isTrue();
    assertThat(tarifa.estaRetirada()).isTrue();

    // La evidencia que el registro de eliminación necesita: qué periodo cubría.
    assertThat(tarifa.getValidFrom()).isEqualTo(DESDE);
    assertThat(tarifa.getValidTo()).isEqualTo(DESDE.plusMonths(1));

    assertThat(tarifa.delete(AHORA.plusDays(1))).as("la segunda vez no hay cambio").isFalse();
  }

  @Test
  @DisplayName("la instantánea lleva el grado calculado, para que se lea sin deducir de tres nulos")
  void laInstantaneaLlevaElGrado() {
    Map<String, Object> estado = tarifa(diez(), PRODUCTO, PERSONA, DESDE, null).instantanea();

    assertThat(estado).containsEntry("scope", "PERSONA_Y_PRODUCTO");
    assertThat(estado).containsEntry("valid_to", null);
    assertThat(estado).containsEntry("percentage", "10");
  }

  private static BigDecimal diez() {
    return new BigDecimal("10");
  }

  private static CommissionRate tarifa(
      BigDecimal porcentaje, UUID producto, UUID persona, LocalDate desde, LocalDate hasta) {
    return CommissionRate.create(
        UUID.randomUUID(), ROL, producto, persona, porcentaje, desde, hasta, AHORA);
  }
}
