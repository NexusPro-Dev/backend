package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.system.users.application.PermissionHolderLookup;
import com.factech.nexus.modules.system.users.application.UserCatalog;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * `RN-AC-006` en un solo sitio: quién puede figurar como instructor.
 *
 * <p>La persona <b>existe y no está retirada</b> —por {@link UserCatalog}, `EX-002` de `RF-AC-008`—
 * y <b>porta {@code courses:teach}</b> —por {@link PermissionHolderLookup}, `EX-003`—. Dos {@code
 * 422} distintos porque se arreglan en sitios distintos: allí la persona, aquí sus roles. <b>No se
 * mira el estado</b> (`RF-AC-008` §14.2): un instructor de baja temporal sigue siendo quien enseñó
 * el curso.
 *
 * <p>Lo comparten el alta y la reasignación (`RF-AC-011` · `T-04`), y se comprueba <b>al asignar y
 * solo al asignar</b>: que la persona pierda el permiso después no toca el curso.
 */
@Component
public class InstructorVerifier {

  static final String PERMISO_DE_INSTRUCTOR = "courses:teach";
  static final String MENSAJE_NO_EXISTE =
      "La persona indicada como instructor no existe o está retirada.";
  static final String MENSAJE_SIN_PERMISO =
      "La persona indicada no puede ser instructor: no porta el permiso courses:teach.";

  private final UserCatalog personas;
  private final PermissionHolderLookup permisos;

  public InstructorVerifier(UserCatalog personas, PermissionHolderLookup permisos) {
    this.personas = personas;
    this.permisos = permisos;
  }

  /** Lanza el {@code 422} que corresponda, o no hace nada. */
  public void verificar(UUID instructorId) {
    boolean existe = personas.find(instructorId).filter(persona -> !persona.deleted()).isPresent();
    if (!existe) {
      throw new UnprocessableEntityException(
          "EX-002",
          MENSAJE_NO_EXISTE,
          List.of(new FieldError("instructorId", "EX-002", MENSAJE_NO_EXISTE)));
    }
    if (!permisos.holds(instructorId, PERMISO_DE_INSTRUCTOR)) {
      throw new UnprocessableEntityException(
          "EX-003",
          MENSAJE_SIN_PERMISO,
          List.of(new FieldError("instructorId", "EX-003", MENSAJE_SIN_PERMISO)));
    }
  }
}
