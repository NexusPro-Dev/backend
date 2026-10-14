package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * El archivo CSV de señales (`RF-AC-070`), leído a filas de texto; los valores los comprueba el
 * caso de uso, como los de una petición JSON.
 *
 * <p>La forma: UTF-8 —o Windows-1252, lo que guarda Excel— con o sin BOM; separado por comas o por
 * punto y coma, según la cabecera; <b>cabecera obligatoria</b> con {@code currency_pair}, {@code
 * signal_time}, {@code signal_type} y {@code subscription_type}, en cualquier orden y sin
 * distinguir mayúsculas ni guiones bajos, y {@code memberships} opcional —los códigos separados por
 * {@code |}—. Las columnas que no conoce se ignoran y las líneas en blanco se saltan. Un campo
 * entre comillas dobles puede llevar el separador.
 */
final class SignalCsv {

  static final int BYTES_MAXIMOS = 1024 * 1024;
  static final int FILAS_MAXIMAS = 500;

  private static final List<String> OBLIGATORIAS =
      List.of("currencypair", "signaltime", "signaltype", "subscriptiontype");

  private SignalCsv() {}

  /** Una fila de datos con su número de línea en el archivo (la cabecera es la 1 si va primero). */
  record Row(
      int line,
      String currencyPair,
      String signalTime,
      String signalType,
      String subscriptionType,
      List<String> memberships) {}

  static List<Row> read(byte[] archivo) {
    if (archivo == null || archivo.length == 0) {
      throw archivo("VAL-009", "El archivo es obligatorio y no puede estar vacío.");
    }
    if (archivo.length > BYTES_MAXIMOS) {
      throw archivo("VAL-009", "El archivo no puede superar 1 MB.");
    }
    String texto = decodificar(archivo);
    if (texto.startsWith("﻿")) {
      texto = texto.substring(1);
    }
    String[] lineas = texto.split("\\r?\\n|\\r", -1);

    int cabecera = 0;
    while (cabecera < lineas.length && lineas[cabecera].isBlank()) {
      cabecera++;
    }
    if (cabecera == lineas.length) {
      throw archivo("VAL-007", "El archivo no tiene filas de señales.");
    }
    char separador = lineas[cabecera].indexOf(';') >= 0 ? ';' : ',';
    Map<String, Integer> columnas = new HashMap<>();
    List<String> nombres = campos(lineas[cabecera], separador);
    for (int i = 0; i < nombres.size(); i++) {
      columnas.putIfAbsent(normalizar(nombres.get(i)), i);
    }
    List<String> faltan = OBLIGATORIAS.stream().filter(c -> !columnas.containsKey(c)).toList();
    if (!faltan.isEmpty()) {
      throw archivo(
          "VAL-010",
          "La cabecera debe tener currency_pair, signal_time, signal_type y subscription_type;"
              + " faltan "
              + faltan.size()
              + ".");
    }
    Integer membresias = columnas.getOrDefault("memberships", columnas.get("membershipcodes"));

    List<Row> filas = new ArrayList<>();
    for (int i = cabecera + 1; i < lineas.length; i++) {
      if (lineas[i].isBlank()) {
        continue;
      }
      if (filas.size() == FILAS_MAXIMAS) {
        throw archivo("VAL-008", "El archivo no puede tener más de 500 señales.");
      }
      List<String> valores = campos(lineas[i], separador);
      filas.add(
          new Row(
              i + 1,
              valor(valores, columnas.get("currencypair")),
              valor(valores, columnas.get("signaltime")),
              valor(valores, columnas.get("signaltype")),
              valor(valores, columnas.get("subscriptiontype")),
              codigos(valor(valores, membresias))));
    }
    if (filas.isEmpty()) {
      throw archivo("VAL-007", "El archivo no tiene filas de señales.");
    }
    return filas;
  }

  private static String decodificar(byte[] archivo) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(archivo))
          .toString();
    } catch (CharacterCodingException noEsUtf8) {
      return new String(archivo, Charset.forName("windows-1252"));
    }
  }

  /** Los campos de una línea; las comillas dobles agrupan y {@code ""} es una comilla. */
  private static List<String> campos(String linea, char separador) {
    List<String> campos = new ArrayList<>();
    StringBuilder actual = new StringBuilder();
    boolean entreComillas = false;
    for (int i = 0; i < linea.length(); i++) {
      char c = linea.charAt(i);
      if (entreComillas) {
        if (c == '"' && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
          actual.append('"');
          i++;
        } else if (c == '"') {
          entreComillas = false;
        } else {
          actual.append(c);
        }
      } else if (c == '"') {
        entreComillas = true;
      } else if (c == separador) {
        campos.add(actual.toString());
        actual.setLength(0);
      } else {
        actual.append(c);
      }
    }
    campos.add(actual.toString());
    return campos;
  }

  private static String normalizar(String nombre) {
    return nombre.trim().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
  }

  private static String valor(List<String> valores, Integer columna) {
    if (columna == null || columna >= valores.size()) {
      return null;
    }
    String valor = valores.get(columna).trim();
    return valor.isEmpty() ? null : valor;
  }

  private static List<String> codigos(String celda) {
    if (celda == null) {
      return List.of();
    }
    return Arrays.stream(celda.split("\\|"))
        .map(String::trim)
        .filter(c -> !c.isEmpty())
        .map(c -> c.toUpperCase(Locale.ROOT))
        .distinct()
        .toList();
  }

  private static ValidationException archivo(String codigo, String mensaje) {
    return new ValidationException(
        codigo, mensaje, List.of(new FieldError("file", codigo, mensaje)));
  }
}
