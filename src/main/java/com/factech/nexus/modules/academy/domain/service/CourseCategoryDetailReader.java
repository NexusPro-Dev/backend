package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseCategoryDetailResponse;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryQueryRepository.CategoryCourseRow;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryQueryRepository.CourseCategoryRow;
import com.factech.nexus.shared.audit.DeletionReasonReader;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Arma la respuesta de administración de la categoría: detalle → cursos vivos → motivo de retiro
 * (`RF-AC-003` §8).
 *
 * <p><b>Un solo sitio</b>, porque la devuelven tres operaciones —el alta, el detalle y la
 * corrección— y cada una que la armara por su cuenta sería una copia de la escalera que podría
 * quedarse atrás. <b>Una sentencia</b> sin cursos, <b>dos</b> con cursos —la segunda se
 * cortocircuita cuando la cuenta de la primera es cero— y <b>una más</b> cuando está retirada y hay
 * que leer el motivo (`CA-AC-019`).
 */
@Component
public class CourseCategoryDetailReader {

  static final String MODULO = "AC";
  static final String ENTIDAD = "course_categories";

  private final CourseCategoryQueryRepository consultas;
  private final DeletionReasonReader motivos;

  public CourseCategoryDetailReader(
      CourseCategoryQueryRepository consultas, DeletionReasonReader motivos) {
    this.consultas = consultas;
    this.motivos = motivos;
  }

  /** El detalle, o el `404` de `RF-AC-003` `EX-001`. Una retirada <b>no</b> es un `404`. */
  public CourseCategoryDetailResponse leer(UUID id) {
    CourseCategoryRow fila =
        consultas
            .findDetail(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe una categoría con ese identificador."));
    List<CategoryCourseRow> cursos =
        fila.courseCount() == 0 ? List.of() : consultas.findAliveCoursesOf(fila.id());
    // El nulo aquí significa que la categoría está viva, y la respuesta lo
    // omite del JSON en lugar de enviarlo en nulo — como el producto.
    String motivo =
        fila.retirada() ? motivos.reasonFor(MODULO, ENTIDAD, fila.id()).orElse(null) : null;
    return CourseCategoryDetailResponse.from(fila, cursos, motivo);
  }
}
