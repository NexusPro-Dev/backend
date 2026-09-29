package com.factech.nexus.modules.commissions.domain.repository;

/**
 * Los fragmentos de SQL que <b>tienen que decir lo mismo en dos sitios</b> de lo afftrack.
 *
 * <p>{@link #VIGENTE_EN} es «qué escalones de persona rigen ese día» (`RN-CM-039`): lo usan el
 * listado con {@code onDate} (`RF-CM-019`) y la resolución de la escala en el cierre (`RF-CM-020`).
 * Escrito dos veces, el listado podría enseñar una escala y el cierre pagar otra; por eso
 * `CA-CM-236` promete que coinciden, y esta constante es lo que lo hace verdad.
 */
final class AfftrackSql {

  /** Sobre el alias {@code u} de {@code user_afftrack_rates} y el parámetro {@code :dia}. */
  static final String VIGENTE_EN =
      "u.deleted_at IS NULL AND u.valid_from <= CAST(:dia AS date)"
          + " AND (u.valid_to IS NULL OR u.valid_to >= CAST(:dia AS date))";

  private AfftrackSql() {}
}
