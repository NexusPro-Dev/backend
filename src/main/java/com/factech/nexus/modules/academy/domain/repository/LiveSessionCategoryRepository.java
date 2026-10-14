package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.LiveSessionCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Las categorías de las clases en vivo (`RF-AC-063` a `RF-AC-066`). La forma de {@link
 * CourseCategoryRepository}: unicidad comprobada antes y <b>traducida</b> después, con el mismo
 * {@code 409}.
 */
public interface LiveSessionCategoryRepository {

  /**
   * ¿Hay otra viva con ese nombre, sin distinguir acentos ni mayúsculas? Nulo, ninguna excluida.
   */
  boolean existsAliveName(String name, UUID exceptId);

  /** Guarda y vuelca; el nombre repetido sale con {@code codigo}. */
  LiveSessionCategory save(LiveSessionCategory categoria, String codigo);

  /** Vuelca los cambios pendientes traduciendo la unicidad con {@code codigo}. */
  void flush(String codigo);

  Optional<LiveSessionCategory> findByIdForUpdate(UUID id);

  /** La viva, sin bloquear: lo que pregunta quien la asigna a una clase. */
  Optional<LiveSessionCategory> findAliveById(UUID id);

  /** Las vivas, por orden, nombre e identificador. */
  List<LiveSessionCategory> findAlive();
}
