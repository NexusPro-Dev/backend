package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.ClassroomCatalogRequest;
import com.factech.nexus.modules.academy.application.ClassroomCatalogResponse;
import com.factech.nexus.modules.academy.application.ClassroomCatalogResponse.ClassroomCategoryItem;
import com.factech.nexus.modules.academy.application.ClassroomCatalogResponse.ClassroomCourseItem;
import com.factech.nexus.modules.academy.application.ClassroomCatalogResponse.CurrentMembershipRef;
import com.factech.nexus.modules.academy.domain.models.CourseDifficulty;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.CategoryRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ClassroomCandidate;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.CourseKeys;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-033`: el catálogo del alumno.
 *
 * <p><b>Solo lo ofrecido</b>, decidido por {@code CourseOfferability} sobre las cuentas de la fila
 * y no por un {@code WHERE} (`spec.md` §14.1); {@code accessible} por {@link StudentKeys} y {@code
 * StudentAccess}; y {@code onlyAccessible} al final, porque depende de las llaves del alumno
 * (`spec.md` §14.4). <b>Cuatro sentencias fijas</b> más las de los dos puertos (`CA-AC-192`): con
 * cero cursos, las llaves y las categorías de los cursos no se leen.
 */
@Service
public class GetClassroomCatalogService {

  private final CourseQueryRepository cursos;
  private final CourseCategoryQueryRepository categorias;
  private final StudentKeys llaves;

  public GetClassroomCatalogService(
      CourseQueryRepository cursos, CourseCategoryQueryRepository categorias, StudentKeys llaves) {
    this.cursos = cursos;
    this.categorias = categorias;
    this.llaves = llaves;
  }

  @Transactional(readOnly = true)
  public ClassroomCatalogResponse catalog(ClassroomCatalogRequest filtros) {
    verificar(filtros);
    StudentKeys.Keys alumno = llaves.ofCurrentActor();

    List<ClassroomCandidate> ofrecidos =
        cursos.findClassroomCandidates(filtros.categoryId(), filtros.difficulty()).stream()
            .filter(candidato -> candidato.course().ofrecibilidad().offerable())
            .toList();

    List<ClassroomCourseItem> lista = List.of();
    if (!ofrecidos.isEmpty()) {
      Map<UUID, CourseKeys> llavesDeCursos =
          cursos.findKeysOfCourses(ofrecidos.stream().map(c -> c.course().id()).toList());
      List<ClassroomCandidate> quedan = new ArrayList<>();
      List<Boolean> accesibles = new ArrayList<>();
      for (ClassroomCandidate candidato : ofrecidos) {
        CourseKeys suyas = llavesDeCursos.getOrDefault(candidato.course().id(), CourseKeys.NINGUNA);
        boolean accesible = alumno.opens(suyas.membershipIds(), suyas.productIds());
        if (!filtros.soloAccesibles() || accesible || candidato.openLessonCount() > 0) {
          quedan.add(candidato);
          accesibles.add(accesible);
        }
      }
      if (!quedan.isEmpty()) {
        Map<UUID, List<CategoryRef>> categoriasDeCursos =
            cursos.findCategoriesOfCourses(quedan.stream().map(c -> c.course().id()).toList());
        List<ClassroomCourseItem> armada = new ArrayList<>();
        for (int i = 0; i < quedan.size(); i++) {
          ClassroomCandidate candidato = quedan.get(i);
          armada.add(
              ClassroomCourseItem.from(
                  candidato,
                  categoriasDeCursos.getOrDefault(candidato.course().id(), List.of()),
                  accesibles.get(i)));
        }
        lista = armada;
      }
    }

    return new ClassroomCatalogResponse(
        CurrentMembershipRef.from(alumno.membership().orElse(null)),
        categorias.findAlive().stream().map(ClassroomCategoryItem::from).toList(),
        lista);
  }

  /** La dificultad es un dominio cerrado: fuera de él, `400` (`VAL-002`). */
  private static void verificar(ClassroomCatalogRequest filtros) {
    if (filtros.difficulty() != null
        && Arrays.stream(CourseDifficulty.values())
            .noneMatch(valor -> valor.name().equals(filtros.difficulty()))) {
      String mensaje = "La dificultad debe ser PRINCIPIANTE, INTERMEDIO o AVANZADO.";
      throw new ValidationException(
          "VAL-002", mensaje, List.of(new FieldError("difficulty", "VAL-002", mensaje)));
    }
  }
}
