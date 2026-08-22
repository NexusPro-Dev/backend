package com.factech.nexus.modules.system.roles.domain.models;

/**
 * Clasificación de un rol (`RN-SP-003`, `requirements/sp.md` §10.2).
 *
 * <p>Es <b>independiente</b> de la clasificación del rol padre, y no por descuido: el catálogo
 * aprobado ya lo exige —`ESTUDIANTE` es consumidor y cuelga de `ADMIN`, que es funcionario— y
 * `CA-SP-145` lo comprueba. Comparar ambas clasificaciones rompería el catálogo el día que se
 * sembrara.
 *
 * <p>Los valores son <b>exactamente</b> los que persiste {@code ck_roles_type}, sin traducir: el
 * ejemplo {@code RoleStatus.ACTIVE} de `development-guide.md` §4.2 ilustra el uso de mayúsculas, no
 * el idioma, y traducirlos obligaría a una tabla de conversión entre el enum y el {@code CHECK}.
 */
public enum RoleType {
  /** Personal interno de la empresa. */
  FUNCIONARIO,

  /** Personal de la fuerza comercial. De él depende quién declara rango comercial. */
  VENDEDOR,

  /** Cliente del sistema. Solo estos roles pueden asociarse a una membresía. */
  CONSUMIDOR
}
