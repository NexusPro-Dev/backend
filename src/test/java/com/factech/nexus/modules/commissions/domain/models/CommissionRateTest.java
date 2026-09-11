package com.factech.nexus.modules.commissions.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
 * Los dos agregados de tasa y el valor que comparten, sin base de datos.
 *
 * <p>Lo que se prueba aquí es lo que el agregado <b>puede</b> decidir solo. El no solapamiento no
 * está: mira a otras filas y vive en el motor.
 */
class CommissionRateTest {

  private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-09-01T10:00:00Z");
  private static final UUID ID = UUID.randomUUID();
  private static final UUID ROL = UUID.randomUUID();
  private static final UUID PERSONA = UUID.randomUUID();

  private static CommissionValue diezPorCiento() {
    return CommissionValue.porcentaje(new BigDecimal("10.00"));
  }

  @Nested
  @DisplayName("El valor de una comisión (`RN-CM-016`)")
  class ElValor {

    @Test
    @DisplayName("las cuatro combinaciones de forma y valor: solo dos son válidas")
    void lasCuatroCombinaciones() {
      // La que toca, en cada forma.
      assertThatCode(
              () ->
                  CommissionValue.of(CommissionRateType.PORCENTAJE, new BigDecimal("10.00"), null))
          .doesNotThrowAnyException();
      assertThatCode(
              () -> CommissionValue.of(CommissionRateType.FIJO, null, new BigDecimal("10000")))
          .doesNotThrowAnyException();

      // La equivocada: el tipo dice una cosa y llega la otra.
      assertThatThrownBy(
              () -> CommissionValue.of(CommissionRateType.FIJO, new BigDecimal("10.00"), null))
          .isInstanceOf(ValidationException.class)
          .hasMessageContaining("porcentaje");

      // Las dos a la vez. NO SE SUMAN: no existe «5 % más 10.000».
      assertThatThrownBy(
              () ->
                  CommissionValue.of(
                      CommissionRateType.PORCENTAJE,
                      new BigDecimal("5.00"),
                      new BigDecimal("10000")))
          .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("ninguna de las dos, y sin forma: se rechazan por separado")
    void ningunaYSinForma() {
      assertThatThrownBy(() -> CommissionValue.of(CommissionRateType.PORCENTAJE, null, null))
          .isInstanceOf(ValidationException.class);

      // Sin forma no se puede saber CUÁL de las dos se quiso declarar, y por eso
      // la forma se pide en lugar de deducirse.
      assertThatThrownBy(() -> CommissionValue.of(null, new BigDecimal("10.00"), null))
          .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("el cero vale en las dos formas: significa «no comisiona»")
    void elCeroValeEnLasDos() {
      assertThat(CommissionValue.porcentaje(BigDecimal.ZERO).cifra()).isEqualByComparingTo("0");
      assertThat(CommissionValue.fijo(BigDecimal.ZERO).cifra()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("el porcentaje se acota a cien y EL IMPORTE FIJO NO SE ACOTA POR ARRIBA")
    void soloUnaDeLasDosFormasTieneTope() {
      assertThatThrownBy(() -> CommissionValue.porcentaje(new BigDecimal("100.01")))
          .isInstanceOf(ValidationException.class);
      assertThatThrownBy(() -> CommissionValue.porcentaje(new BigDecimal("-0.01")))
          .isInstanceOf(ValidationException.class);

      assertThatThrownBy(() -> CommissionValue.fijo(new BigDecimal("-0.01")))
          .isInstanceOf(ValidationException.class);

      // `RN-CM-018`. UN IMPORTE DESMESURADO ENTRA SIN RESISTENCIA, y esta
      // afirmación es la que hay que leer dos veces: la tasa no conoce el precio
      // de ningún producto, de modo que nada puede impedirlo aquí. El día que
      // alguien añada un tope, esta prueba falla y la discusión pasa por
      // `cm.md` en lugar de resolverse con un número inventado.
      assertThatCode(() -> CommissionValue.fijo(new BigDecimal("99999999.9999")))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MISMA forma y distinta escala: NO hay cambio")
    void laEscalaNoEsInformacion() {
      assertThat(
              diezPorCiento().mismoValorQue(CommissionValue.porcentaje(new BigDecimal("10.0000"))))
          .isTrue();
    }

    @Test
    @DisplayName("DISTINTA forma y la MISMA cifra: SÍ hay cambio")
    void laFormaSiEsInformacion() {
      // `CA-CM-091` en unitaria. Es el contrario exacto de la prueba anterior
      // CON LOS MISMOS NÚMEROS, y ahí está su valor: `compareTo` sobre la cifra
      // pasa aquella y falla esta; `equals` pasa esta y falla aquella.
      // Cualquiera de las dos se satisface rompiendo la otra.
      assertThat(diezPorCiento().mismoValorQue(CommissionValue.fijo(new BigDecimal("10.00"))))
          .isFalse();
    }

    @Test
    @DisplayName("lo que va a la auditoría lleva la forma, no solo el número")
    void laAuditoriaLlevaLaForma() {
      assertThat(diezPorCiento().paraAuditoria()).isEqualTo("PORCENTAJE 10.00");
      assertThat(CommissionValue.fijo(new BigDecimal("10000")).paraAuditoria())
          .isEqualTo("FIJO 10000");
    }
  }

  @Nested
  @DisplayName("La tasa de rol")
  class DeRol {

    @Test
    @DisplayName("el cero es un valor válido: significa «no comisiona»")
    void elCeroEsValido() {
      CommissionRate tasa =
          CommissionRate.create(ID, ROL, CommissionValue.porcentaje(BigDecimal.ZERO), AHORA);
      assertThat(tasa.getPercentage()).isEqualByComparingTo("0");
      assertThat(tasa.getFixedAmount()).isNull();
    }

    @Test
    @DisplayName("una tasa en importe fijo deja el porcentaje NULO, y eso no es que falte")
    void enImporteFijo() {
      CommissionRate tasa =
          CommissionRate.create(ID, ROL, CommissionValue.fijo(new BigDecimal("10000")), AHORA);

      assertThat(tasa.getValue().getRateType()).isEqualTo(CommissionRateType.FIJO);
      assertThat(tasa.getFixedAmount()).isEqualByComparingTo("10000");
      assertThat(tasa.getPercentage()).isNull();
    }

    @Test
    @DisplayName("la instantánea lleva la FORMA junto al valor")
    void laInstantaneaLlevaLaForma() {
      Map<String, Object> foto =
          CommissionRate.create(ID, ROL, diezPorCiento(), AHORA).instantanea();

      assertThat(foto).containsOnlyKeys("role_id", "rate_type", "value");
      assertThat(foto.get("rate_type")).isEqualTo("PORCENTAJE");
      assertThat(foto.get("value")).isEqualTo("10.00");
    }

    @Test
    @DisplayName("corregir devuelve el antes y el después, que es la única copia del valor viejo")
    void corregirDevuelveElCambio() {
      CommissionRate tasa = CommissionRate.create(ID, ROL, diezPorCiento(), AHORA);

      Map<String, Object> cambios =
          tasa.update(
              Patchable.de(CommissionValue.porcentaje(new BigDecimal("12.00"))), AHORA.plusDays(1));

      assertThat(cambios).containsKey("value");
      assertThat(cambios.get("value"))
          .isEqualTo(Map.of("before", "PORCENTAJE 10.00", "after", "PORCENTAJE 12.00"));
      assertThat(tasa.getUpdatedAt()).isEqualTo(AHORA.plusDays(1));
    }

    @Test
    @DisplayName("10.00 y 10.0000 son el mismo valor: no se registra un cambio que no cambia")
    void laEscalaNoEsUnCambio() {
      CommissionRate tasa = CommissionRate.create(ID, ROL, diezPorCiento(), AHORA);

      Map<String, Object> cambios =
          tasa.update(
              Patchable.de(CommissionValue.porcentaje(new BigDecimal("10.0000"))),
              AHORA.plusDays(1));

      assertThat(cambios).isEmpty();
      // Y `updatedAt` no se mueve: hacerlo haría creer que alguien tocó la tasa.
      assertThat(tasa.getUpdatedAt()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("`10 %` corregido a `10` FIJO SÍ es un cambio, aunque las cifras comparen iguales")
    void cambiarDeFormaConLaMismaCifra() {
      // `CA-CM-091`. Es el defecto silencioso de esta operación: si la
      // comparación mirara solo la cifra, esto devolvería un mapa vacío, no
      // escribiría auditoría, no movería la marca de modificación Y DEVOLVERÍA
      // ÉXITO — con la tasa todavía pagando el 10 %.
      CommissionRate tasa = CommissionRate.create(ID, ROL, diezPorCiento(), AHORA);

      Map<String, Object> cambios =
          tasa.update(
              Patchable.de(CommissionValue.fijo(new BigDecimal("10.00"))), AHORA.plusDays(1));

      assertThat(cambios).containsKey("value");
      assertThat(cambios.get("value"))
          .isEqualTo(Map.of("before", "PORCENTAJE 10.00", "after", "FIJO 10.00"));
      assertThat(tasa.getValue().getRateType()).isEqualTo(CommissionRateType.FIJO);
      assertThat(tasa.getUpdatedAt()).isEqualTo(AHORA.plusDays(1));
    }

    @Test
    @DisplayName("vaciar la forma se rechaza")
    void noSePuedeVaciar() {
      CommissionRate tasa = CommissionRate.create(ID, ROL, diezPorCiento(), AHORA);

      assertThatThrownBy(() -> tasa.update(Patchable.de(null), AHORA))
          .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("retirar no es idempotente")
    void retirarNoEsIdempotente() {
      CommissionRate tasa = CommissionRate.create(ID, ROL, diezPorCiento(), AHORA);

      assertThat(tasa.delete(AHORA)).isTrue();
      assertThat(tasa.delete(AHORA)).isFalse();
    }
  }

  @Nested
  @DisplayName("La tasa personalizada")
  class Personalizada {

    private static final CommissionValue DOCE = CommissionValue.porcentaje(new BigDecimal("12.00"));

    @Test
    @DisplayName("una que rigió un solo día es válida")
    void unSoloDia() {
      LocalDate dia = LocalDate.of(2026, 3, 1);
      UserCommissionRate tasa = UserCommissionRate.create(ID, PERSONA, DOCE, dia, dia, AHORA);

      assertThat(tasa.getValidTo()).isEqualTo(dia);
    }

    @Test
    @DisplayName("también se declara en importe fijo, con el MISMO objeto que la de rol")
    void enImporteFijo() {
      UserCommissionRate tasa =
          UserCommissionRate.create(
              ID,
              PERSONA,
              CommissionValue.fijo(new BigDecimal("10000")),
              LocalDate.of(2026, 1, 1),
              null,
              AHORA);

      assertThat(tasa.getValue().getRateType()).isEqualTo(CommissionRateType.FIJO);
      assertThat(tasa.getPercentage()).isNull();
    }

    @Test
    @DisplayName("el fin anterior al inicio se rechaza")
    void vigenciaInvertida() {
      assertThatThrownBy(
              () ->
                  UserCommissionRate.create(
                      ID, PERSONA, DOCE, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1), AHORA))
          .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("quitar el fin de vigencia SE CUMPLE, y quitar la forma se RECHAZA")
    void losDosNulosSeTratanAlReves() {
      UserCommissionRate tasa =
          UserCommissionRate.create(
              ID, PERSONA, DOCE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), AHORA);

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
          UserCommissionRate.create(ID, PERSONA, DOCE, LocalDate.of(2026, 1, 1), null, AHORA);

      tasa.delete(AHORA.plusDays(1));

      assertThat(tasa.getValidTo()).isNull();
      assertThat(tasa.estaRetirada()).isTrue();
    }

    @Test
    @DisplayName("la instantánea lleva la forma y la vigencia, y el nulo viaja como nulo")
    void laInstantanea() {
      Map<String, Object> foto =
          UserCommissionRate.create(ID, PERSONA, DOCE, LocalDate.of(2026, 1, 1), null, AHORA)
              .instantanea();

      assertThat(foto).containsOnlyKeys("user_id", "rate_type", "value", "valid_from", "valid_to");
      assertThat(foto.get("rate_type")).isEqualTo("PORCENTAJE");
      assertThat(foto.get("valid_to")).isNull();
    }
  }

  @Nested
  @DisplayName("La asociación")
  class Asociacion {

    @Test
    @DisplayName("copia el rol DE LA TASA, no de quien la pide")
    void copiaElRolDeLaTasa() {
      CommissionRate tasa = CommissionRate.create(ID, ROL, diezPorCiento(), AHORA);
      UUID producto = UUID.randomUUID();

      ProductCommissionRate asociacion = ProductCommissionRate.create(producto, tasa, AHORA);

      assertThat(asociacion.getRoleId()).isEqualTo(ROL);
      assertThat(asociacion.getCommissionRateId()).isEqualTo(ID);
      assertThat(asociacion.getProductId()).isEqualTo(producto);
    }
  }
}
