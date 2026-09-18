package com.factech.nexus.modules.academy.domain.service;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * El arrastre de `RN-AC-018`: retirar un curso retira sus módulos y lecciones vivos, y retirar un
 * módulo sus lecciones, <b>con el mismo instante, el mismo motivo y una fila de auditoría por cada
 * uno</b> (`RF-AC-013` · `T-05`, `RF-AC-025`).
 *
 * <p><b>Una sola forma de arrastrar, escrita una vez</b>, para que el retiro del curso y el del
 * módulo no diverjan. <b>Hoy no tiene nada que recorrer</b>: {@code course_modules} y {@code
 * lessons} no existen hasta el bloque 3 de `ac.md` §6.1, y `RF-AC-022` · `T-08` y `RF-AC-028` ·
 * `T-06` escriben aquí el recorrido —módulos vivos del curso con {@code FOR UPDATE}, sus lecciones
 * vivas, un {@code UPDATE} por tabla y un {@code DeletionEvent LOGICAL} por fila— sin cambiar la
 * firma que el retiro del curso ya llama.
 */
@Component
public class CourseTreeRetirement {

  /** Retira los módulos vivos del curso y sus lecciones. Vacío hasta `RF-AC-022`. */
  public void retirarArbolDe(UUID courseId, String motivo, OffsetDateTime ahora) {
    // Nada que recorrer todavía: ver el Javadoc de la clase.
  }
}
