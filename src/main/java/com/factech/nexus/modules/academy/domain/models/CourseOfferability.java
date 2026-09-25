package com.factech.nexus.modules.academy.domain.models;

/**
 * `RN-AC-015` en un solo sitio: si un curso <b>se ofrece</b>, y si no, <b>el primer motivo</b> que
 * falla, en un orden fijo que es el orden en que hay que arreglarlo.
 *
 * <p>Los cuatro motivos, desde el 25-09-2026 (`requirements/ac.md` §5.2.12): retirado → inactivo →
 * sin descripción → sin módulo activo con lección activa con contenido. <b>Las membresías y los
 * servicios ya no son un motivo</b>: deciden a quién se abre el curso —a quien los tenga, o a todos
 * si no declara ninguno—, no si se ofrece. Hasta ese día un curso sin ninguno no se ofrecía. <b>Se
 * calcula en cada lectura y nunca se guarda</b>: administración lo ve como {@code offerable} con
 * {@code offerableReason}, como el paquete de `PM`; el aula solo enseña lo ofrecido.
 *
 * <p><b>Recibe entradas planas y no la entidad</b>, a propósito: la cuenta de módulos ofrecibles
 * viene de una sentencia —el detalle, el listado, los cursos de una categoría, el aula— y no de una
 * navegación, de modo que el número de sentencias no depende de cuántos cursos se miren.
 */
public final class CourseOfferability {

  public static final String RETIRADO = "El curso está retirado.";
  public static final String INACTIVO = "El curso está inactivo.";
  public static final String SIN_DESCRIPCION = "El curso no tiene descripción corta o larga.";
  public static final String SIN_MODULO =
      "El curso no tiene ningún módulo activo con al menos una lección activa con contenido.";

  private CourseOfferability() {}

  /** Lo que decide: si se ofrece, con el motivo nulo; o no, con el primero que falla. */
  public record Resultado(boolean offerable, String reason) {}

  public static Resultado decidir(
      boolean retirado,
      CourseStatus estado,
      boolean tieneDescripcionCorta,
      boolean tieneDescripcionLarga,
      long modulosOfrecibles) {
    if (retirado) {
      return new Resultado(false, RETIRADO);
    }
    if (estado != CourseStatus.ACTIVO) {
      return new Resultado(false, INACTIVO);
    }
    if (!tieneDescripcionCorta || !tieneDescripcionLarga) {
      return new Resultado(false, SIN_DESCRIPCION);
    }
    if (modulosOfrecibles <= 0) {
      return new Resultado(false, SIN_MODULO);
    }
    return new Resultado(true, null);
  }

  /** La misma decisión con el estado como llega de una proyección. */
  public static Resultado decidir(
      boolean retirado,
      String estado,
      boolean tieneDescripcionCorta,
      boolean tieneDescripcionLarga,
      long modulosOfrecibles) {
    return decidir(
        retirado,
        CourseStatus.valueOf(estado),
        tieneDescripcionCorta,
        tieneDescripcionLarga,
        modulosOfrecibles);
  }
}
