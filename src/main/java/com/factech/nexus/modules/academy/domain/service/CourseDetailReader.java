package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseDetailResponse;
import com.factech.nexus.modules.academy.application.CourseDetailResponse.ModuleDetail;
import com.factech.nexus.modules.academy.domain.models.CourseOfferability;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.CourseRow;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.LessonRow;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ModuleRow;
import com.factech.nexus.shared.audit.DeletionReasonReader;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Arma la respuesta de administración del curso (`RF-AC-010` §8): detalle con instructor →
 * categorías → recomendados → membresías → módulos → lecciones → ofrecibilidad → motivo de retiro.
 *
 * <p><b>Los servicios</b> (`RF-AC-037`) son una sentencia más, siempre: no hay cuenta en la fila
 * que permita saltarla, y la lectura es de un curso.
 *
 * <p><b>Un solo sitio</b> para las nueve operaciones que devuelven el curso, y <b>la escalera ya
 * está en su orden definitivo</b>: cada peldaño es una lectura del repositorio que hoy devuelve
 * vacío sin consultar y que su requerimiento llena sin cambiar esta forma. <b>Una sentencia</b> hoy
 * —el curso con su instructor— y <b>una más</b> con motivo de retiro (`CA-AC-054`); cada enmienda
 * declara las suyas.
 *
 * <p>La duración total y la cuenta de lecciones se suman <b>sobre lo vivo</b>: un módulo o una
 * lección retirados se devuelven marcados y no cuentan (`RF-AC-010` `FA-002`).
 */
@Component
public class CourseDetailReader {

  static final String MODULO = "AC";
  static final String ENTIDAD = "courses";

  private final CourseQueryRepository consultas;
  private final DeletionReasonReader motivos;

  public CourseDetailReader(CourseQueryRepository consultas, DeletionReasonReader motivos) {
    this.consultas = consultas;
    this.motivos = motivos;
  }

  /** El detalle, o el `404` de `RF-AC-010` `EX-001`. Un retirado <b>no</b> es un `404`. */
  public CourseDetailResponse leer(UUID id) {
    CourseRow fila =
        consultas
            .findDetail(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un curso con ese identificador."));

    List<ModuleRow> modulos = consultas.findModulesOf(fila.id());
    Map<UUID, List<LessonRow>> lecciones =
        modulos.isEmpty()
            ? Map.of()
            : consultas.findLessonsOfModules(modulos.stream().map(ModuleRow::id).toList()).stream()
                .collect(Collectors.groupingBy(LessonRow::moduleId));

    List<ModuleDetail> arbol = new ArrayList<>();
    long duracion = 0;
    long cuenta = 0;
    for (ModuleRow modulo : modulos) {
      List<LessonRow> suyas = lecciones.getOrDefault(modulo.id(), List.of());
      arbol.add(CourseDetailResponse.modulo(modulo, suyas, modulo.ofrecibilidad().offerable()));
      if (!modulo.retirado()) {
        duracion += modulo.durationSeconds();
        cuenta += suyas.stream().filter(leccion -> !leccion.retirada()).count();
      }
    }

    CourseOfferability.Resultado ofrecibilidad = fila.ofrecibilidad();
    // El nulo aquí significa que el curso está vivo, y la respuesta lo omite
    // del JSON en lugar de enviarlo en nulo — como la categoría.
    String motivo =
        fila.retirado() ? motivos.reasonFor(MODULO, ENTIDAD, fila.id()).orElse(null) : null;
    return CourseDetailResponse.from(
        fila,
        consultas.findCategoriesOf(fila.id()),
        consultas.findRecommendedOf(fila.id()),
        consultas.findMembershipsOf(fila.id()),
        consultas.findProductsOf(fila.id()),
        arbol,
        duracion,
        cuenta,
        ofrecibilidad,
        motivo);
  }
}
