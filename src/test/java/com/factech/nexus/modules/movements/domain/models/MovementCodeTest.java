package com.factech.nexus.modules.movements.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La composición del código de comprobante (`RF-MV-001` · `T-09`, `RN-MV-016`).
 *
 * <p>Se prueba <b>sin base de datos</b>: el código es una función pura de tres cosas —el prefijo,
 * el instante y el azar—, y probarlo contra PostgreSQL no añadiría nada salvo segundos.
 */
class MovementCodeTest {

  private static final Pattern FORMA = Pattern.compile("^VTA-\\d{8}-[0-9A-HJKMNP-TV-Z]{6}$");

  @Test
  @DisplayName("Prefijo, día de ocho cifras y seis caracteres")
  void laForma() {
    String codigo =
        MovementCode.generar("VTA", OffsetDateTime.of(2026, 9, 4, 15, 0, 0, 0, ZoneOffset.UTC));

    assertThat(codigo).matches(FORMA);
    assertThat(codigo).startsWith("VTA-20260904-");
  }

  @Test
  @DisplayName("El día se corta en America/Bogota: las 23:30 de allí NO son del día siguiente")
  void elDiaSeCortaEnBogota() {
    // 04:30 UTC del 5 son las 23:30 del 4 en Bogotá. Con el corte en UTC, el
    // papel que se le entrega al cliente llevaría un día que no es el de la
    // venta — y como el código se emite una sola vez, no habría forma de
    // arreglarlo después sin reemitir el comprobante.
    OffsetDateTime nocheDeBogota = OffsetDateTime.of(2026, 9, 5, 4, 30, 0, 0, ZoneOffset.UTC);

    assertThat(MovementCode.generar("VTA", nocheDeBogota)).startsWith("VTA-20260904-");
  }

  @Test
  @DisplayName("El alfabeto no tiene I, L, O ni U")
  void elAlfabetoEsElDeCrockford() {
    assertThat(MovementCode.ALFABETO).hasSize(32);
    // Es el error que se comete al dictar: `O` contra `0` y `I`/`L` contra `1`.
    // La `U` se descarta por otro motivo — completa palabras que nadie quiere
    // leer en un comprobante.
    assertThat(MovementCode.ALFABETO).doesNotContain("I").doesNotContain("L");
    assertThat(MovementCode.ALFABETO).doesNotContain("O").doesNotContain("U");

    // Mil códigos y ninguna letra prohibida: el alfabeto está en un sitio, pero
    // esto comprueba que el generador lo usa entero y solo a él.
    for (int i = 0; i < 1000; i++) {
      assertThat(MovementCode.generar("VTA", OffsetDateTime.now())).matches(FORMA);
    }
  }

  @Test
  @DisplayName("Dos códigos seguidos no son el mismo: el sufijo es aleatorio, no correlativo")
  void elSufijoEsAleatorio() {
    OffsetDateTime instante = OffsetDateTime.of(2026, 9, 4, 15, 0, 0, 0, ZoneOffset.UTC);

    Set<String> vistos = new HashSet<>();
    for (int i = 0; i < 200; i++) {
      vistos.add(MovementCode.generar("VTA", instante));
    }
    // No se afirma que NO haya colisión —treinta y dos elevado a seis la hace
    // improbable y no imposible, que es justo por lo que existe el índice
    // único—, sino que el generador no devuelve siempre lo mismo.
    assertThat(vistos).hasSizeGreaterThan(190);
  }

  @Test
  @DisplayName(
      "Con el azar forzado, el código es predecible: es lo que permite probar el reintento")
  void elAzarSePuedeFijar() {
    // Sin poder fijar el generador, «tres intentos y falla» solo se podría
    // comprobar esperando a que el azar repita, que es exactamente lo que no
    // ocurre.
    String primero =
        MovementCode.generar(
            "VTA",
            OffsetDateTime.of(2026, 9, 4, 15, 0, 0, 0, ZoneOffset.UTC),
            new java.util.Random(42));
    String segundo =
        MovementCode.generar(
            "VTA",
            OffsetDateTime.of(2026, 9, 4, 15, 0, 0, 0, ZoneOffset.UTC),
            new java.util.Random(42));

    assertThat(primero).isEqualTo(segundo);
  }
}
