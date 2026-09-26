package com.factech.nexus.modules.academy.domain.models;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Qué se le abre a un alumno (`RN-AC-013`, `RN-AC-014`, `RN-AC-020`): la única forma de decidir
 * {@code accessible} en las tres vistas del aula (`RF-AC-033` a `RF-AC-035`).
 *
 * <p><b>Un curso se abre</b> si no declara ninguna llave —es de todos desde el 25-09-2026,
 * `requirements/ac.md` §5.2.12—, si la membresía vigente del alumno está en su lista, o si el
 * alumno tiene vigente uno de sus servicios. <b>Pertenencia y no nivel</b> (`RN-AC-012`): no recibe
 * el nivel, y {@code PLATINO} no abre un curso de {@code ORO}. <b>Una lección se abre</b> si su
 * curso se abre o si está abierta.
 */
public final class StudentAccess {

  private StudentAccess() {}

  /**
   * @param membresiaVigente la del alumno, o nulo si no tiene vigente
   * @param productosVigentes los que tiene vigentes; nunca nulo
   * @param membresiasDelCurso las que abren el curso
   * @param serviciosDelCurso los que abren el curso
   */
  public static boolean courseAccessible(
      UUID membresiaVigente,
      Set<UUID> productosVigentes,
      Collection<UUID> membresiasDelCurso,
      Collection<UUID> serviciosDelCurso) {
    if (membresiasDelCurso.isEmpty() && serviciosDelCurso.isEmpty()) {
      return true;
    }
    if (membresiaVigente != null && membresiasDelCurso.contains(membresiaVigente)) {
      return true;
    }
    return serviciosDelCurso.stream().anyMatch(productosVigentes::contains);
  }

  public static boolean lessonAccessible(boolean cursoAccesible, boolean abierta) {
    return cursoAccesible || abierta;
  }
}
