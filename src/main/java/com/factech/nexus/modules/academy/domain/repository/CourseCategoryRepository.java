package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.CourseCategory;
import java.util.Optional;
import java.util.UUID;

/**
 * Escritura y lectura bloqueante de la categoría (`RF-AC-001`, `RF-AC-004`, `RF-AC-005`).
 *
 * <p>La forma de `PM`: unicidad del nombre comprobada antes y <b>traducida</b> después —el índice
 * parcial muerde en el {@code INSERT} y sale como el mismo {@code 409}—, y dos lecturas con
 * bloqueo: la de la viva, para la corrección, y la de cualquiera, para el retiro, que necesita
 * distinguir «no existe» de «ya está retirada».
 */
public interface CourseCategoryRepository {

  /** ¿Hay una categoría viva con ese nombre, sin distinguir acentos ni mayúsculas? */
  boolean existsAliveName(String name);

  /** Lo mismo, excluyendo a la propia categoría: es lo que pregunta la corrección. */
  boolean existsAliveNameForOther(String name, UUID categoryId);

  CourseCategory save(CourseCategory categoria);

  Optional<CourseCategory> findAliveByIdForUpdate(UUID id);

  Optional<CourseCategory> findByIdForUpdate(UUID id);

  /** Vacía los cambios pendientes traduciendo la unicidad, para quien escribe sin {@link #save}. */
  void flush();
}
