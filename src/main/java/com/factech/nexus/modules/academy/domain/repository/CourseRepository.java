package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.Course;
import java.util.Optional;
import java.util.UUID;

/**
 * Escritura y lectura bloqueante del curso (`RF-AC-008`, `RF-AC-011`, `RF-AC-012`, `RF-AC-013`, y
 * las tres relaciones y los módulos, que bloquean el curso antes de escribir).
 *
 * <p>La forma de la categoría: unicidad del título comprobada antes y <b>traducida</b> después —el
 * índice parcial muerde en el {@code INSERT} y sale como el mismo {@code 409}—, y dos lecturas con
 * bloqueo: la del vivo, para todo lo que corrige, y la de cualquiera, para el retiro.
 */
public interface CourseRepository {

  /** ¿Hay un curso vivo con ese título, sin distinguir acentos ni mayúsculas? */
  boolean existsAliveTitle(String title);

  /** Lo mismo, excluyendo al propio curso: es lo que pregunta la corrección. */
  boolean existsAliveTitleForOther(String title, UUID courseId);

  Course save(Course curso);

  Optional<Course> findAliveByIdForUpdate(UUID id);

  Optional<Course> findByIdForUpdate(UUID id);

  /** Vacía los cambios pendientes traduciendo la unicidad, para quien escribe sin {@link #save}. */
  void flush();
}
