package com.factech.nexus.modules.academy.domain.models;

/**
 * `RN-AC-015` en un solo sitio: si un curso <b>se ofrece</b>, y si no, <b>el primer motivo</b> que
 * falla, en un orden fijo que es el orden en que hay que arreglarlo.
 *
 * <p>Los cinco motivos, desde el 18-09-2026 (`requirements/ac.md` §5.2.7): retirado → inactivo →
 * sin descripción → sin membresías → sin módulo activo con lección activa con contenido. <b>Se
 * calcula en cada lectura y nunca se guarda</b>: administración lo ve como {@code offerable} con
 * {@code offerableReason}, como el paquete de `PM`; el aula solo enseña lo ofrecido.
 *
 * <p><b>Recibe entradas planas y no la entidad</b>, a propósito: las cuentas de membresías y de
 * módulos ofrecibles vienen de una sentencia —el detalle, el listado, los cursos de una categoría,
 * el aula— y no de una navegación, de modo que el número de sentencias no depende de cuántos cursos
 * se miren. Nace con `RF-AC-008` cuando dos de sus cuentas son cero siempre; los bloques 3 y 4
 * cambian lo que se le pasa, no este objeto.
 */
public final class CourseOfferability {

  public static final String RETIRADO = "El curso está retirado.";
  public static final String INACTIVO = "El curso está inactivo.";
  public static final String SIN_DESCRIPCION = "El curso no tiene descripción corta o larga.";
  public static final String SIN_MEMBRESIAS = "El curso no tiene ninguna membresía que lo abra.";
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
      long membresias,
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
    if (membresias <= 0) {
      return new Resultado(false, SIN_MEMBRESIAS);
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
      long membresias,
      long modulosOfrecibles) {
    return decidir(
        retirado,
        CourseStatus.valueOf(estado),
        tieneDescripcionCorta,
        tieneDescripcionLarga,
        membresias,
        modulosOfrecibles);
  }
}
