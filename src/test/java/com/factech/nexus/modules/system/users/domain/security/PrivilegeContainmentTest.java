package com.factech.nexus.modules.system.users.domain.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.modules.system.roles.domain.models.RoleType;
import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** `RN-SEG-010` en el único sitio donde vive. */
class PrivilegeContainmentTest {

  @Test
  @DisplayName("la comparación es por PERMISOS, no por rol ni por posición en la jerarquía")
  void porPermisosYNoPorRol() {
    // Es la propiedad que hace correcta la regla: el actor no necesita portar el
    // rol, sino todo lo que ese rol declara. Un administrador puede conceder
    // CONTABILIDAD sin ser contable.
    AssignableRole contabilidad = rol("CONTABILIDAD", "audit:read-changes", "audit:read-deletions");
    Set<String> delActor = Set.of("audit:read-changes", "audit:read-deletions", "users:create");

    assertThat(PrivilegeContainment.excesos(List.of(contabilidad), delActor)).isEmpty();
    assertThat(PrivilegeContainment.loAlcanza(contabilidad, delActor)).isTrue();
  }

  @Test
  @DisplayName("basta UN permiso que falte para que el rol entero quede fuera de alcance")
  void unSoloPermisoQueFalta() {
    AssignableRole rol = rol("AUDITOR", "audit:read-changes", "audit:read-security");

    assertThat(PrivilegeContainment.excesos(List.of(rol), Set.of("audit:read-changes")))
        .containsExactly(rol);
  }

  @Test
  @DisplayName("devuelve TODOS los infractores, no el primero")
  void enumeraTodos() {
    // Sin esto, quien recibe el rechazo corrige un rol, vuelve a enviar y es
    // rechazado otra vez por el siguiente.
    AssignableRole uno = rol("UNO", "a");
    AssignableRole dos = rol("DOS", "b");
    AssignableRole admisible = rol("TRES", "c");

    assertThat(PrivilegeContainment.excesos(List.of(uno, dos, admisible), Set.of("c")))
        .containsExactly(uno, dos);
  }

  @Test
  @DisplayName("un rol sin permisos lo puede conceder cualquiera")
  void rolSinPermisos() {
    // No es un caso raro: `V7` siembra cuatro roles sin permisos a la espera de
    // `RF-SP-005`. Rechazarlos habría hecho inasignable medio catálogo.
    assertThat(PrivilegeContainment.excesos(List.of(rol("VACIO")), Set.of())).isEmpty();
  }

  @Test
  @DisplayName("un actor sin permisos no puede conceder nada que conceda algo")
  void actorSinPermisos() {
    assertThat(PrivilegeContainment.excesos(List.of(rol("ALGO", "x")), Set.of())).hasSize(1);
  }

  private static AssignableRole rol(String codigo, String... permisos) {
    return new AssignableRole(
        UUID.randomUUID(),
        codigo,
        codigo,
        RoleType.FUNCIONARIO,
        false,
        true,
        null,
        Set.of(permisos));
  }
}
