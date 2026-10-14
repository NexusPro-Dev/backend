package com.factech.nexus.modules.academy.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.modules.academy.domain.models.Signal;
import com.factech.nexus.modules.academy.domain.models.SubscriptionType;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** `CA-AC-343`: la forma del CSV de señales. */
class SignalCsvTest {

  private static String codigo(Runnable lectura) {
    try {
      lectura.run();
    } catch (ValidationException rechazo) {
      return rechazo.errorCode();
    }
    throw new AssertionError("Debía rechazarse");
  }

  private static byte[] utf8(String texto) {
    return texto.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName(
      "Punto y coma, BOM, columnas en otro orden y en camelCase, comillas, líneas en blanco y"
          + " códigos separados por |")
  void leeLoQueGuardaExcel() {
    String csv =
        "﻿subscriptionType;Signal_Time;currency_pair;signal_type;memberships;nota\r\n"
            + "free;10:30;EUR/USD;1;;\r\n"
            + "\r\n"
            + "personalized;9:00;\"XAU;USD\";2;oro| vip ;x\r\n";
    List<SignalCsv.Row> filas = SignalCsv.read(utf8(csv));
    assertThat(filas).hasSize(2);
    assertThat(filas.get(0))
        .isEqualTo(new SignalCsv.Row(2, "EUR/USD", "10:30", "1", "free", List.of()));
    assertThat(filas.get(1))
        .isEqualTo(
            new SignalCsv.Row(4, "XAU;USD", "9:00", "2", "personalized", List.of("ORO", "VIP")));
  }

  @Test
  @DisplayName("Comas y Windows-1252")
  void comasYWindows1252() {
    String csv =
        "currency_pair,signal_time,signal_type,subscription_type\nGBP/JPY,23:59,0,PREMIUM\n";
    List<SignalCsv.Row> filas = SignalCsv.read(csv.getBytes(Charset.forName("windows-1252")));
    assertThat(filas)
        .containsExactly(new SignalCsv.Row(2, "GBP/JPY", "23:59", "0", "PREMIUM", List.of()));
  }

  @Test
  @DisplayName(
      "Vacío o de más de 1 MB, VAL-009; sin columnas, VAL-010; sin filas, VAL-007; 501, VAL-008")
  void rechazos() {
    assertThat(codigo(() -> SignalCsv.read(new byte[0]))).isEqualTo("VAL-009");
    assertThat(codigo(() -> SignalCsv.read(null))).isEqualTo("VAL-009");
    assertThat(codigo(() -> SignalCsv.read(new byte[SignalCsv.BYTES_MAXIMOS + 1])))
        .isEqualTo("VAL-009");
    assertThat(codigo(() -> SignalCsv.read(utf8("currency_pair;signal_time\nEUR/USD;10:00\n"))))
        .isEqualTo("VAL-010");
    assertThat(
            codigo(
                () ->
                    SignalCsv.read(
                        utf8("currency_pair;signal_time;signal_type;subscription_type\n\n"))))
        .isEqualTo("VAL-007");
    assertThat(codigo(() -> SignalCsv.read(utf8("\n \n")))).isEqualTo("VAL-007");
    StringBuilder largo =
        new StringBuilder("currency_pair;signal_time;signal_type;subscription_type\n");
    for (int i = 0; i < 501; i++) {
      largo.append("EUR/USD;10:00;1;FREE\n");
    }
    assertThat(codigo(() -> SignalCsv.read(utf8(largo.toString())))).isEqualTo("VAL-008");
  }

  @Test
  @DisplayName("El CSV de ejemplo de la tripleta se lee entero y cada campo es válido")
  void ejemploDeLaTripleta() throws java.io.IOException {
    byte[] ejemplo =
        Files.readAllBytes(Path.of("docs/specs/ac/064-senales-de-trading/ejemplo-senales.csv"));
    List<SignalCsv.Row> filas = SignalCsv.read(ejemplo);
    assertThat(filas).hasSize(8);
    List<FieldError> errores = new ArrayList<>();
    for (SignalCsv.Row fila : filas) {
      Signal.par(fila.currencyPair(), "currency_pair", errores);
      Signal.hora(fila.signalTime(), "signal_time", errores);
      Signal.tipo(fila.signalType(), "signal_type", errores);
      SubscriptionType tipo =
          Signal.suscripcion(fila.subscriptionType(), "subscription_type", errores);
      Signal.membresias(tipo, fila.memberships().size(), "memberships", errores);
      assertThat(fila.memberships()).isSubsetOf("BECA", "VIP", "PLATINO", "ORO");
    }
    assertThat(errores).isEmpty();
  }
}
