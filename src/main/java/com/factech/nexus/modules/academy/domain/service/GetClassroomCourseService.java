package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.ClassroomCatalogResponse.CurrentMembershipRef;
import com.factech.nexus.modules.academy.application.ClassroomCourseResponse;
import com.factech.nexus.modules.academy.application.ClassroomCourseResponse.ClassroomLessonItem;
import com.factech.nexus.modules.academy.application.ClassroomCourseResponse.ClassroomModuleItem;
import com.factech.nexus.modules.academy.domain.models.StudentAccess;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.CourseRow;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.LessonRow;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.MembershipRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ModuleRow;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ProductRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.RecommendedCourseRow;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-034`: el curso como lo ve el alumno.
 *
 * <p><b>Las lecturas de `RF-AC-010`, otro lector</b> (`spec.md` §14.2): decide con los mismos tres
 * objetos de ofrecibilidad qué se queda, y suma sobre lo que queda. <b>El `404` se sabe con la
 * primera sentencia</b> y no pregunta a `SP`; las llaves del alumno se piden al final.
 */
@Service
public class GetClassroomCourseService {

  private final CourseQueryRepository consultas;
  private final StudentKeys llaves;

  public GetClassroomCourseService(CourseQueryRepository consultas, StudentKeys llaves) {
    this.consultas = consultas;
    this.llaves = llaves;
  }

  @Transactional(readOnly = true)
  public ClassroomCourseResponse course(UUID id) {
    CourseRow fila =
        consultas
            .findDetail(id)
            .filter(curso -> curso.ofrecibilidad().offerable())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un curso con ese identificador."));

    List<MembershipRef> membresias = consultas.findMembershipsOf(fila.id());
    List<ProductRef> servicios = consultas.findProductsOf(fila.id());
    StudentKeys.Keys alumno = llaves.ofCurrentActor();
    boolean accesible =
        alumno.opens(
            membresias.stream().map(MembershipRef::id).toList(),
            servicios.stream().map(ProductRef::id).toList());

    List<ModuleRow> ofrecibles =
        consultas.findModulesOf(fila.id()).stream()
            .filter(modulo -> modulo.ofrecibilidad().offerable())
            .toList();
    Map<UUID, List<LessonRow>> lecciones =
        ofrecibles.isEmpty()
            ? Map.of()
            : consultas
                .findLessonsOfModules(ofrecibles.stream().map(ModuleRow::id).toList())
                .stream()
                .filter(LessonRow::ofrecida)
                .collect(Collectors.groupingBy(LessonRow::moduleId));
    List<ClassroomModuleItem> arbol =
        ofrecibles.stream()
            .map(
                modulo ->
                    ClassroomModuleItem.from(
                        modulo,
                        lecciones.getOrDefault(modulo.id(), List.of()).stream()
                            .map(
                                leccion ->
                                    ClassroomLessonItem.from(
                                        leccion,
                                        StudentAccess.lessonAccessible(accesible, leccion.open())))
                            .toList()))
            .toList();

    List<RecommendedCourseRow> recomendados =
        consultas.findRecommendedOf(fila.id()).stream()
            .filter(RecommendedCourseRow::offerable)
            .toList();
    return ClassroomCourseResponse.from(
        fila,
        consultas.findCategoriesOf(fila.id()),
        recomendados,
        membresias,
        servicios,
        CurrentMembershipRef.from(alumno.membership().orElse(null)),
        accesible,
        arbol);
  }
}
