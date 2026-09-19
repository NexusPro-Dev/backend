package com.factech.nexus.modules.academy.domain.models;

/**
 * Si un módulo <b>se ofrece</b> dentro de un curso ofrecido (`RN-AC-015`), y si no, el primer
 * motivo en orden fijo: retirado → inactivo → sin lección activa con contenido.
 *
 * <p>Hermano pequeño de {@link CourseOfferability}: recibe entradas planas —la cuenta de lecciones
 * ofrecibles viene de la sentencia que leyó el módulo— y es lo que el curso cuenta como «módulo
 * ofrecible». El tercer motivo dice «con contenido» desde el 18-09-2026 (`requirements/ac.md`
 * §5.2.7): una lección activa vaciada deja de ofrecerse, y el módulo que solo la tenía a ella
 * también.
 */
public final class ModuleOfferability {

  public static final String RETIRADO = "El módulo está retirado.";
  public static final String INACTIVO = "El módulo está inactivo.";
  public static final String SIN_LECCION =
      "El módulo no tiene ninguna lección activa con contenido.";

  private ModuleOfferability() {}

  public record Resultado(boolean offerable, String reason) {}

  public static Resultado decidir(boolean retirado, String estado, long leccionesOfrecibles) {
    if (retirado) {
      return new Resultado(false, RETIRADO);
    }
    if (!CourseStatus.ACTIVO.name().equals(estado)) {
      return new Resultado(false, INACTIVO);
    }
    if (leccionesOfrecibles <= 0) {
      return new Resultado(false, SIN_LECCION);
    }
    return new Resultado(true, null);
  }
}
