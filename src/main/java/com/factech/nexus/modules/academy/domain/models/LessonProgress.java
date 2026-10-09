package com.factech.nexus.modules.academy.domain.models;

/**
 * Cuánto vale lo visto de una lección (`RN-AC-021`, `RN-AC-023`): la única forma de acotar una
 * posición, de decidir si completa y de sumar el avance de un curso.
 *
 * <p><b>El 90 % vive aquí y solo aquí</b>: el SQL que escribe recibe el umbral ya convertido a
 * segundos por {@link #umbral(int)}, y las lecturas del aula, del detalle y del listado calculan el
 * porcentaje con {@link #porcentaje(long, long)}.
 */
public final class LessonProgress {

  /** El porcentaje de la duración que completa un video (`RN-AC-021`). */
  public static final int UMBRAL_PORCENTUAL = 90;

  private LessonProgress() {}

  /** La posición reportada, acotada a la duración: más allá del final cuenta como el final. */
  public static int acotar(int posicion, int duracion) {
    return Math.max(0, Math.min(posicion, duracion));
  }

  /**
   * Los segundos desde los que un video queda completado: el 90 % de la duración, redondeado hacia
   * arriba para que 89,9 % no complete.
   */
  public static int umbral(int duracion) {
    return (int) ((duracion * (long) UMBRAL_PORCENTUAL + 99) / 100);
  }

  /** Si esos segundos completan una lección de esa duración. */
  public static boolean completa(int vistos, int duracion) {
    return vistos >= umbral(duracion);
  }

  /**
   * Lo que una lección aporta al avance de su curso (`RN-AC-023`): la duración entera si está
   * completada —aunque lo visto sea menos, porque la duración subió después—, y si no lo visto
   * acotado a la duración.
   */
  public static long credito(int vistos, int duracion, boolean completada) {
    return completada ? duracion : Math.min(Math.max(vistos, 0), duracion);
  }

  /** Entero, hacia abajo, tope 100; cero si no hay nada que ver. */
  public static int porcentaje(long vistos, long total) {
    if (total <= 0) {
      return 0;
    }
    return (int) Math.min(100, Math.max(0, vistos) * 100 / total);
  }

  /** El porcentaje de una sola lección: 100 si está completada. */
  public static int porcentajeDeLeccion(int vistos, int duracion, boolean completada) {
    return porcentaje(credito(vistos, duracion, completada), duracion);
  }
}
