package com.factech.nexus.shared.config;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * El entorno en el que corre esta instancia (Art. IX.4).
 *
 * <p><b>Hasta el 31-08-2026 esta variable no la leía nadie.</b> Estaba declarada en {@code
 * .env.example}, en {@code docker-compose.yml} y en {@code deployment.md} §6.2, y {@code
 * deployment.md} §6.6 lo decía con todas las letras: «cambiar {@code ENVIRONMENT} a {@code
 * production} no cambia hoy ningún comportamiento». Este enum es lo primero que la consume, y
 * cierra esa parte del issue #30.
 *
 * <h2>Por qué es un dominio CERRADO y no una cadena</h2>
 *
 * <p>Lo que decide si se siembran o no quince cuentas de prueba es «el entorno no es producción».
 * Con una cadena suelta, ese «no es producción» es cierto para {@code Production}, para {@code
 * prod}, para un valor vacío y para una variable que nadie declaró — es decir, <b>la comparación
 * falla abierta justo del lado que importa</b>. Con un dominio cerrado no hay un cuarto estado: o
 * el valor es uno de los tres, o la aplicación no arranca.
 *
 * <p><b>Un valor que no se entiende tumba el arranque</b> (Art. IX.5), igual que una entrada
 * malformada de {@code TRUSTED_PROXIES}. Asumir un valor por defecto sería exactamente lo que ese
 * artículo prohíbe, y el síntoma de acertar mal —datos de prueba en un entorno que no los esperaba—
 * no menciona nunca la variable mal escrita.
 *
 * <p>El valor se compara <b>sin distinguir mayúsculas y recortando espacios</b>. Eso no reabre el
 * agujero: sigue siendo el mismo conjunto de tres, y evita que {@code Production} —que quien lo
 * escribe cree correcto— acabe clasificado como «no es producción».
 */
public enum Environment {
  DEVELOPMENT,
  TESTING,
  PRODUCTION;

  /**
   * Traduce el valor declarado en {@code ENVIRONMENT}.
   *
   * @param valor lo que llega por configuración; puede venir nulo o en blanco si nadie la declaró
   * @return el entorno correspondiente
   * @throws IllegalArgumentException si falta o no es uno de los tres del Art. IX.4
   */
  public static Environment desdeConfiguracion(String valor) {
    String normalizado = valor == null ? "" : valor.trim();
    if (normalizado.isEmpty()) {
      throw new IllegalArgumentException(
          "La variable ENVIRONMENT no está declarada. Es obligatoria (Art. IX.4) y no tiene valor"
              + " por defecto a propósito (Art. IX.5): de ella depende que NO se siembren datos"
              + " de prueba. Valores admitidos: "
              + admitidos()
              + ".");
    }
    return Arrays.stream(values())
        .filter(entorno -> entorno.name().equalsIgnoreCase(normalizado))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "La variable ENVIRONMENT trae un valor que no se reconoce: '"
                        + normalizado
                        + "'. Valores admitidos: "
                        + admitidos()
                        + " (Art. IX.4). No se asume ninguno, porque de esta variable depende que"
                        + " NO se siembren datos de prueba."));
  }

  /** Si esta instancia es el sistema real. */
  public boolean esProduccion() {
    return this == PRODUCTION;
  }

  private static String admitidos() {
    return Arrays.stream(values())
        .map(entorno -> entorno.name().toLowerCase())
        .collect(Collectors.joining(", "));
  }
}
