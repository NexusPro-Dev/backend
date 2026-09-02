package com.factech.nexus.modules.commissions.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Los dos agregados de tasa, sin base de datos.
 *
 * <p>Lo que se prueba aquí es lo que el agregado <b>puede</b> decidir solo. El no solapamiento no
 * está: mira a otras filas y vive en el motor.
 */
class CommissionRateTest {

  private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-09-01T10:00:00Z");
  private static final UUID ID = UUID.randomUUID();
  private static final UUID ROL = UUID.randomUUID();
  private static final UUID PERSONA = UUID.randomUUID();

  @Nested
  @DisplayName("La tasa de rol")
  class DeRol {

    @Test
    @DisplayName("el cero es un porcentaje válido: significa «no comisiona»")
    void elCeroEsValido() {
      CommissionRate tasa = CommissionRate.create(ID, ROL, BigDecimal.ZERO, AHORA);
      assertThat(tasa.getPercentage()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("fuera de [0, 100] se rechaza")
    void fueraDeRango() {
      assertThatThrownBy(() -> CommissionRate.create(ID, ROL, new BigDecimal("100.01"), AHORA))
          .isInstanceOf(ValidationException.class);
      assertThatThrownBy(() -> CommissionRate.create(ID, ROL, new BigDecimal("-0.01"), AHORA))
          .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("la instantánea ya no lleva producto, persona, vigencia ni grado")
    void loQuePerdioLaInstantanea() {
      Map<String, Object> foto =
          CommissionRate.create(ID, ROL, new BigDecimal("10.00"), AHORA).instantanea();

      assertThat(foto).containsOnlyKeys("role_id", "percentage");
    }

    @Test
    @DisplayName("corregir devuelve el antes y el después, que es la única copia del valor viejo")
    void corregirDevuelveElCambio() {
      CommissionRate tasa = CommissionRate.create(ID, ROL, new BigDecimal("10.00"), AHORA);

      Map<String, Object> cambios =
          tasa.update(Patchable.de(new BigDecimal("12.00")), AHORA.plusDays(1));

      assertThat(cambios).containsKey("percentage");
      assertThat(cambios.get("percentage")).isEqualTo(Map.of("before", "10.00", "after", "12.00"));
      assertThat(tasa.getUpdatedAt()).isEqualTo(AHORA.plusDays(1));
    }

    @Test
    @DisplayName("10.00 y 10.0000 son el mismo porcentaje: no se registra un cambio que no cambia")
    void laEscalaNoEsUnCambio() {
      CommissionRate tasa = CommissionRate.create(ID, ROL, new BigDecimal("10.00"), AHORA);

      Map<String, Object> cambios =
          tasa.update(Patchable.de(new BigDecimal("10.0000")), AHORA.plusDays(1));

      assertThat(cambios).isEmpty();
      // Y `updatedAt` no se mueve: hacerlo haría creer que alguien tocó la tasa.
      assertThat(tasa.getUpdatedAt()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("vaciar el porcentaje se rechaza")
    void noSePuedeVaciar() {
      CommissionRate tasa = CommissionRate.create(ID, ROL, new BigDecimal("10.00"), AHORA);

      assertThatThrownBy(() -> tasa.update(Patchable.de(null), AHORA))
          .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("retirar no es idempotente")
    void retirarNoEsIdempotente() {
      CommissionRate tasa = CommissionRate.create(ID, ROL, new BigDecimal("10.00"), AHORA);

      assertThat(tasa.delete(AHORA)).isTrue();
      assertThat(tasa.delete(AHORA)).isFalse();
    }
  }

  @Nested
  @DisplayName("La tasa personalizada")
  class Personalizada {

    @Test
    @DisplayName("una que rigió un solo día es válida")
    void unSoloDia() {
      LocalDate dia = LocalDate.of(2026, 3, 1);
      UserCommissionRate tasa =
          UserCommissionRate.create(ID, PERSONA, new BigDecimal("12.00"), dia, dia, AHORA);

      assertThat(tasa.getValidTo()).isEqualTo(dia);
    }

    @Test
    @DisplayName("el fin anterior al inicio se rechaza")
    void vigenciaInvertida() {
      assertThatThrownBy(
              () ->
                  UserCommissionRate.create(
                      ID,
                      PERSONA,
                      new BigDecimal("12.00"),
                      LocalDate.of(2026, 6, 1),
                      LocalDate.of(2026, 1, 1),
                      AHORA))
          .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("quitar el fin de vigencia SE CUMPLE, y quitar el porcentaje se RECHAZA")
    void losDosNulosSeTratanAlReves() {
      UserCommissionRate tasa =
          UserCommissionRate.create(
              ID,
              PERSONA,
              new BigDecimal("12.00"),
              LocalDate.of(2026, 1, 1),
              LocalDate.of(2026, 6, 30),
              AHORA);

      Map<String, Object> cambios =
          tasa.update(Patchable.ausente(), Patchable.de(null), AHORA.plusDays(1));

      assertThat(cambios).containsKey("valid_to");
      assertThat(tasa.getValidTo()).isNull();

      assertThatThrownBy(
              () -> tasa.update(Patchable.de(null), Patchable.ausente(), AHORA.plusDays(2)))
          .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("retirar NO toca la vigencia: el registro debe decir qué periodo cubría")
    void retirarNoCierraLaVigencia() {
      UserCommissionRate tasa =
          UserCommissionRate.create(
              ID, PERSONA, new BigDecimal("12.00"), LocalDate.of(2026, 1, 1), null, AHORA);

      tasa.delete(AHORA.plusDays(1));

      assertThat(tasa.getValidTo()).isNull();
      assertThat(tasa.estaRetirada()).isTrue();
    }

    @Test
    @DisplayName("la instantánea lleva la vigencia, y el nulo viaja como nulo")
    void laInstantanea() {
      Map<String, Object> foto =
          UserCommissionRate.create(
                  ID, PERSONA, new BigDecimal("12.00"), LocalDate.of(2026, 1, 1), null, AHORA)
              .instantanea();

      assertThat(foto).containsOnlyKeys("user_id", "percentage", "valid_from", "valid_to");
      assertThat(foto.get("valid_to")).isNull();
    }
  }

  @Nested
  @DisplayName("La asociación")
  class Asociacion {

    @Test
    @DisplayName("copia el rol DE LA TASA, no de quien la pide")
    void copiaElRolDeLaTasa() {
      CommissionRate tasa = CommissionRate.create(ID, ROL, new BigDecimal("10.00"), AHORA);
      UUID producto = UUID.randomUUID();

      ProductCommissionRate asociacion = ProductCommissionRate.create(producto, tasa, AHORA);

      assertThat(asociacion.getRoleId()).isEqualTo(ROL);
      assertThat(asociacion.getCommissionRateId()).isEqualTo(ID);
      assertThat(asociacion.getProductId()).isEqualTo(producto);
    }
  }
}
