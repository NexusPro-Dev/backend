package com.factech.nexus.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** `RF-SP-071` · `T-03`: el cálculo TOTP contra RFC 6238 y las reglas de `RN-SP-060`. */
class TotpTest {

  /** El secreto de RFC 6238 Apéndice B para SHA-1: «12345678901234567890» en ASCII. */
  private static final String SECRETO_RFC =
      Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

  /**
   * Los vectores de RFC 6238 Apéndice B en SHA-1 son de ocho dígitos; los de seis son sus seis
   * últimos, porque el truncado es el mismo número tomado módulo 10^6.
   */
  @ParameterizedTest(name = "T = {0} → {1}")
  @CsvSource({
    "59, 287082",
    "1111111109, 081804",
    "1111111111, 050471",
    "1234567890, 005924",
    "2000000000, 279037",
    "20000000000, 353130"
  })
  @DisplayName("Los vectores de RFC 6238 Apéndice B (SHA-1), en seis dígitos")
  void vectoresDelRfc(long segundos, String esperado) {
    long periodo = Totp.periodo(Instant.ofEpochSecond(segundos));

    assertThat(Totp.codigo(SECRETO_RFC, periodo)).isEqualTo(esperado);
  }

  @Test
  @DisplayName("Base32 de ida y vuelta, y el secreto nuevo mide 32 caracteres")
  void base32() {
    String secreto = Totp.nuevoSecreto();

    assertThat(secreto).hasSize(32).matches("[A-Z2-7]+");
    assertThat(Totp.base32(Totp.desdeBase32(secreto))).isEqualTo(secreto);
    assertThat(Totp.desdeBase32(secreto)).hasSize(Totp.BYTES_DEL_SECRETO);
  }

  @Test
  @DisplayName("Se acepta el periodo actual y uno a cada lado, y no dos")
  void tolerancia() {
    Instant ahora = Instant.ofEpochSecond(1_800_000_000L);
    long actual = Totp.periodo(ahora);

    for (long delta = -1; delta <= 1; delta++) {
      String codigo = Totp.codigo(SECRETO_RFC, actual + delta);
      assertThat(Totp.periodoQueCasa(SECRETO_RFC, codigo, ahora, null)).hasValue(actual + delta);
    }
    assertThat(Totp.periodoQueCasa(SECRETO_RFC, Totp.codigo(SECRETO_RFC, actual - 2), ahora, null))
        .isEmpty();
    assertThat(Totp.periodoQueCasa(SECRETO_RFC, Totp.codigo(SECRETO_RFC, actual + 2), ahora, null))
        .isEmpty();
  }

  @Test
  @DisplayName("Un código no sirve dos veces: solo casa un periodo posterior al último usado")
  void unSoloUso() {
    Instant ahora = Instant.ofEpochSecond(1_800_000_000L);
    long actual = Totp.periodo(ahora);
    String codigo = Totp.codigo(SECRETO_RFC, actual);

    assertThat(Totp.periodoQueCasa(SECRETO_RFC, codigo, ahora, actual)).isEmpty();
    assertThat(Totp.periodoQueCasa(SECRETO_RFC, codigo, ahora, actual - 1)).hasValue(actual);
    // El del periodo anterior, aceptable por tolerancia, tampoco si ya se usó el actual.
    assertThat(
            Totp.periodoQueCasa(SECRETO_RFC, Totp.codigo(SECRETO_RFC, actual - 1), ahora, actual))
        .isEmpty();
  }

  @Test
  @DisplayName("Lo que no son seis dígitos no casa nunca")
  void formato() {
    Instant ahora = Instant.ofEpochSecond(1_800_000_000L);

    assertThat(Totp.periodoQueCasa(SECRETO_RFC, null, ahora, null)).isEmpty();
    assertThat(Totp.periodoQueCasa(SECRETO_RFC, "12345", ahora, null)).isEmpty();
    assertThat(Totp.periodoQueCasa(SECRETO_RFC, "1234567", ahora, null)).isEmpty();
    assertThat(Totp.periodoQueCasa(SECRETO_RFC, "12a456", ahora, null)).isEmpty();
  }

  @Test
  @DisplayName("La URI lleva emisor, cuenta, secreto y los parámetros que la app espera")
  void uri() {
    assertThat(Totp.uri("NEXUS", "jperez", "ABCDEF"))
        .isEqualTo(
            "otpauth://totp/NEXUS:jperez?secret=ABCDEF&issuer=NEXUS&algorithm=SHA1&digits=6&period=30");
  }
}
