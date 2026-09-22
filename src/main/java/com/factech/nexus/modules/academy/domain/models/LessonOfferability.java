package com.factech.nexus.modules.academy.domain.models;

/**
 * Si una lección <b>se ofrece</b> (`RN-AC-015`): está {@code ACTIVA}, no retirada y <b>con
 * contenido</b> — el tercer motivo desde el 18-09-2026 (`requirements/ac.md` §5.2.7).
 *
 * <p>Es lo que {@link ModuleOfferability} cuenta, sacado a un sitio con nombre para que el árbol
 * del aula (`RF-AC-034`) y el contenido de una lección (`RF-AC-035`) lo pregunten igual, y para que
 * el fragmento SQL que lo cuenta en las lecturas tenga su gemelo en Java con el mismo texto.
 */
public final class LessonOfferability {

  private LessonOfferability() {}

  public static boolean offered(String status, boolean retirada, boolean tieneContenido) {
    return !retirada && CourseStatus.ACTIVO.name().equals(status) && tieneContenido;
  }
}
