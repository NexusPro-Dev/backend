package com.factech.nexus.shared.security;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Permisos efectivos de una persona, resueltos <b>contra la base de datos</b>.
 *
 * <p>Es un puerto y no una consulta directa porque {@code shared/security} no puede depender de un
 * módulo de negocio (`architecture.md` §5.3, y la prueba de ArchUnit que lo verifica). Lo
 * implementa el módulo dueño de {@code users}.
 *
 * <p><b>Por qué de la base y no de la caché ni del token.</b> `RF-SP-001` §5 lo argumenta: la caché
 * de `security.md` §4.5 sirve a la resolución en tiempo de autorización, que es de lectura y de
 * altísima frecuencia. Aquí se decide un <b>techo de privilegios</b> en una operación poco
 * frecuente, y una entrada obsoleta se traduciría en una concesión que el actor ya no tenía derecho
 * a hacer.
 */
public interface EffectivePermissions {

  /**
   * Códigos de permiso que la persona posee hoy.
   *
   * <p><b>Devuelve un {@code Optional} y no un conjunto</b> para distinguir dos cosas que un
   * conjunto vacío confundiría: «esta persona no tiene ningún permiso» —una respuesta legítima— y
   * «no hay tal persona». Quien pregunta necesita separarlas.
   *
   * @return vacío si no existe o está eliminada; presente —aunque sin elementos— si existe
   */
  Optional<Set<String>> forUser(UUID userId);
}
