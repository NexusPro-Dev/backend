package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.TaughtCourseItem;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.security.CurrentActor;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-057`: los cursos que dicta quien pregunta —los no retirados, los mismos en los
 * que `RF-AC-049` le deja programar (`RN-AC-028`)—.
 */
@Service
public class ListTaughtCoursesService {

  private final CourseQueryRepository cursos;
  private final CurrentActor actor;

  public ListTaughtCoursesService(CourseQueryRepository cursos, CurrentActor actor) {
    this.cursos = cursos;
    this.actor = actor;
  }

  @Transactional(readOnly = true)
  public List<TaughtCourseItem> listMine() {
    UUID instructor =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));
    return cursos.findTaughtBy(instructor).stream()
        .map(c -> new TaughtCourseItem(c.id(), c.title(), c.status()))
        .toList();
  }
}
