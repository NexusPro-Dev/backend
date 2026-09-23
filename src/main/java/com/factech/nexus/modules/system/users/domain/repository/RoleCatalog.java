package com.factech.nexus.modules.system.users.domain.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Acceso de solo lectura al catálogo de roles desde el alta de personas.
 *
 * <p><b>Es un puerto propio y no el repositorio de roles</b>, aunque lea la misma tabla: lo que
 * este caso de uso necesita —clasificación, rol padre y permisos declarados— es una proyección
 * distinta de la que usa el agregado {@code Role}, y compartir el puerto ataría cada cambio de
 * aquel a este. Roles y usuarios son dos agregados del mismo módulo, de modo que `architecture.md`
 * §5.3 —que prohíbe cruzar <b>módulos</b>— no se infringe.
 */
public interface RoleCatalog {

  /** Resuelve identificadores. Devuelve los que existen, sin fallar por los que no. */
  List<AssignableRole> findAllById(Set<UUID> ids);

  /** Un rol concreto, exista o no y sirva o no. */
  Optional<AssignableRole> findById(UUID id);

  /**
   * Un rol por su CÓDIGO, para el registro por enlace (`RF-SP-045`).
   *
   * <p><b>Por código y no por identificador cableado</b>, y es la misma convención que el proyecto
   * ya eligió para la membresía gratuita el 01-09-2026: un UUID literal en el código de un caso de
   * uso es un dato de siembra escondido en una clase, y el día que alguien lo cambie el fallo sale
   * lejos de aquí. El código es estable y se lee.
   */
  Optional<AssignableRole> findByCode(String code);

  /** Roles que porta una persona. Lo necesita `RN-SP-020` para mirar al superior. */
  Set<UUID> roleIdsOf(UUID userId);

  /**
   * Los roles de VARIAS personas, en <b>una</b> consulta (`RF-SP-069`).
   *
   * <p>No es azúcar sobre {@link #roleIdsOf}: quien asigna un lote de hasta cien managers a un
   * equipo tiene que decidir sobre todos antes de escribir nada, y hacerlo persona a persona serían
   * cien viajes a la base — el `N+1` que el plan de `RF-SP-069` declara como riesgo.
   *
   * <p>Una persona sin ningún rol <b>no aparece</b> en el mapa, en lugar de aparecer con un
   * conjunto vacío: quien pregunta ya tiene la lista de a quién preguntó, y un mapa con huecos
   * obliga a decidir qué significa el hueco en el sitio donde importa.
   */
  Map<UUID, Set<UUID>> roleIdsOfAll(Set<UUID> userIds);
}
