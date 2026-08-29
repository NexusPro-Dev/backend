package com.factech.nexus.modules.system.roles.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.modules.system.permissions.application.PermissionItem;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `RF-SP-001` · `T-11` — la fábrica del agregado.
 *
 * <p><b>Sin Spring y sin base de datos</b>, que es lo que `plan.md` §11 exige de las reglas de
 * negocio. Que la clase esté anotada con JPA no lo impide mientras nadie la persista: lo que se
 * perdió con la disposición de `architecture.md` §5.1 —y queda anotado allí— es la garantía de que
 * <i>no pueda</i> depender de la base de datos, no la posibilidad de probarla sin ella.
 */
class RoleTest {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 8, 22, 14, 32, 11, 0, ZoneOffset.UTC);

  private static final PermissionItem LEER_ROLES =
      new PermissionItem(
          UUID.fromString("01a029fc-5d80-7001-9c4f-5e7ad0000001"),
          "roles:read",
          "roles",
          "read",
          "Consultar roles",
          null);

  private static final PermissionItem LEER_AUDITORIA =
      new PermissionItem(
          UUID.fromString("01a029fc-5d80-7002-9c4f-5e7ad0000002"),
          "audit:read-changes",
          "audit",
          "read-changes",
          "Consultar auditoría de cambios",
          null);

  private static final PermissionItem LEER_SEGURIDAD =
      new PermissionItem(
          UUID.fromString("01a029fc-5d80-7003-9c4f-5e7ad0000003"),
          "audit:read-security",
          "audit",
          "read-security",
          "Consultar auditoría de seguridad",
          null);

  @Test
  @DisplayName("CA-SP-001 — alta válida con permisos contenidos en el padre")
  void altaValida() {
    Role padre = padreCon(LEER_ROLES, LEER_AUDITORIA);

    Role rol =
        Role.create(
            UUID.randomUUID(),
            new RoleCode("ADMIN"),
            "Contabilidad",
            "Rol del área contable.",
            RoleType.FUNCIONARIO,
            padre,
            List.of(LEER_AUDITORIA),
            Set.of("roles:read", "audit:read-changes"),
            AHORA);

    assertThat(rol.getCode().value()).isEqualTo("ADMIN");
    assertThat(rol.getParentRoleId()).isEqualTo(padre.getId());
    assertThat(rol.getPermissionIds()).containsExactly(LEER_AUDITORIA.id());
    assertThat(rol.getCreatedAt()).isEqualTo(AHORA);
    assertThat(rol.getUpdatedAt()).isEqualTo(AHORA);
    assertThat(rol.getDeletedAt()).isNull();
  }

  @Test
  @DisplayName("CA-SP-146 — nace ACTIVO y no de sistema, sin recibirlo como dato")
  void naceActivo() {
    Role rol = altaSimpleBajo(padreCon(LEER_ROLES));

    assertThat(rol.getStatus()).isEqualTo(RoleStatus.ACTIVO);
    assertThat(rol.isSystem()).isFalse();
  }

  @Test
  @DisplayName("CA-SP-005 — FA-001: se admite el alta sin permisos y se omiten las contenciones")
  void sinPermisos() {
    // El actor no tiene NINGÚN permiso y aun así el alta procede: sin permisos
    // declarados no hay nada que contener, ni en el padre ni en el actor.
    Role rol =
        Role.create(
            UUID.randomUUID(),
            new RoleCode("VACIO"),
            "Rol sin permisos",
            null,
            RoleType.FUNCIONARIO,
            padreCon(LEER_ROLES),
            List.of(),
            Set.of(),
            AHORA);

    assertThat(rol.getPermissionIds()).isEmpty();
  }

  @Test
  @DisplayName(
      "CA-SP-003 — RN-SEG-003 rechaza el permiso que el padre no posee, y los nombra todos")
  void permisoFueraDelPadre() {
    Role padre = padreCon(LEER_ROLES);

    assertThatThrownBy(
            () ->
                Role.create(
                    UUID.randomUUID(),
                    new RoleCode("EXCEDIDO"),
                    "Excedido",
                    null,
                    RoleType.FUNCIONARIO,
                    padre,
                    List.of(LEER_AUDITORIA, LEER_SEGURIDAD),
                    Set.of("roles:read", "audit:read-changes", "audit:read-security"),
                    AHORA))
        .isInstanceOf(BusinessRuleException.class)
        .satisfies(
            fallo -> {
              BusinessRuleException regla = (BusinessRuleException) fallo;
              assertThat(regla.errorCode()).isEqualTo("RN-SEG-003");
              // Los DOS infractores, no el primero: devolverlos de a uno
              // convierte una corrección en varias vueltas.
              assertThat(regla.errors())
                  .hasSize(2)
                  .extracting(FieldError::field)
                  .containsOnly("permissionIds");
              assertThat(regla.errors())
                  .extracting(FieldError::message)
                  .anySatisfy(m -> assertThat(m).contains("audit:read-changes"))
                  .anySatisfy(m -> assertThat(m).contains("audit:read-security"));
            });
  }

  @Test
  @DisplayName("CA-SP-004 — RN-SEG-010 rechaza el permiso que el actor no posee")
  void permisoFueraDelActor() {
    // El padre SÍ lo tiene; el actor no. Es la diferencia entre las dos reglas,
    // y por eso el padre se construye con el permiso completo.
    Role padre = padreCon(LEER_ROLES, LEER_SEGURIDAD);

    assertThatThrownBy(
            () ->
                Role.create(
                    UUID.randomUUID(),
                    new RoleCode("EXCEDIDO"),
                    "Excedido",
                    null,
                    RoleType.FUNCIONARIO,
                    padre,
                    List.of(LEER_SEGURIDAD),
                    Set.of("roles:read"),
                    AHORA))
        .isInstanceOf(BusinessRuleException.class)
        .satisfies(
            fallo -> {
              BusinessRuleException regla = (BusinessRuleException) fallo;
              assertThat(regla.errorCode()).isEqualTo("RN-SEG-010");
              assertThat(regla.errors())
                  .singleElement()
                  .extracting(FieldError::message)
                  .satisfies(m -> assertThat(m).contains("audit:read-security"));
            });
  }

  @Test
  @DisplayName("CA-SP-145 — la clasificación no se compara con la del padre")
  void clasificacionIndependiente() {
    // ESTUDIANTE es consumidor y cuelga de ADMIN, que es funcionario: el
    // catálogo aprobado exige que esto funcione.
    Role padreFuncionario = padreCon(LEER_ROLES);

    Role consumidor =
        Role.create(
            UUID.randomUUID(),
            new RoleCode("ESTUDIANTE"),
            "Estudiante",
            null,
            RoleType.CONSUMIDOR,
            padreFuncionario,
            List.of(),
            Set.of(),
            AHORA);

    assertThat(consumidor.getRoleType()).isEqualTo(RoleType.CONSUMIDOR);
    assertThat(padreFuncionario.getRoleType()).isEqualTo(RoleType.FUNCIONARIO);
  }

  @Test
  @DisplayName(
      "el nombre y la descripción se recortan; sin ellos la unicidad se burla con un espacio")
  void recorte() {
    Role rol =
        Role.create(
            UUID.randomUUID(),
            new RoleCode("ADMIN"),
            "  Contabilidad  ",
            "   ",
            RoleType.FUNCIONARIO,
            padreCon(LEER_ROLES),
            List.of(),
            Set.of(),
            AHORA);

    assertThat(rol.getName()).isEqualTo("Contabilidad");
    // Una descripción de solo espacios es ausencia de descripción, no una
    // descripción en blanco: así la columna guarda NULL y no basura.
    assertThat(rol.getDescription()).isNull();
  }

  @Test
  @DisplayName("RN-SP-002 y RN-SP-003: sin padre o sin clasificación es un defecto, no una entrada")
  void argumentosObligatorios() {
    assertThatThrownBy(
            () ->
                Role.create(
                    UUID.randomUUID(),
                    new RoleCode("X"),
                    "X",
                    null,
                    RoleType.FUNCIONARIO,
                    null,
                    List.of(),
                    Set.of(),
                    AHORA))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                Role.create(
                    UUID.randomUUID(),
                    new RoleCode("X"),
                    "X",
                    null,
                    null,
                    padreCon(LEER_ROLES),
                    List.of(),
                    Set.of(),
                    AHORA))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private Role altaSimpleBajo(Role padre) {
    return Role.create(
        UUID.randomUUID(),
        new RoleCode("NUEVO"),
        "Nuevo",
        null,
        RoleType.FUNCIONARIO,
        padre,
        List.of(),
        Set.of(),
        AHORA);
  }

  /**
   * Construye un padre con los permisos dados.
   *
   * <p>Se usa reflexión y no la fábrica porque el padre de un padre no existe en este contexto:
   * {@code Role.create} exige a su vez un padre, y montar la cadena entera para una prueba unitaria
   * la ataría a un detalle que no está probando. El rol raíz real lo siembra {@code V7}.
   */
  private static Role padreCon(PermissionItem... permisos) {
    try {
      Role padre = crearVacio();
      asignar(padre, "id", UUID.randomUUID());
      asignar(padre, "code", new RoleCode("PADRE"));
      asignar(padre, "name", "Padre");
      asignar(padre, "roleType", RoleType.FUNCIONARIO);
      asignar(padre, "status", RoleStatus.ACTIVO);
      Set<UUID> ids = new LinkedHashSet<>();
      for (PermissionItem permiso : permisos) {
        ids.add(permiso.id());
      }
      asignar(padre, "permissionIds", ids);
      return padre;
    } catch (ReflectiveOperationException fallo) {
      throw new IllegalStateException(fallo);
    }
  }

  private static Role crearVacio() throws ReflectiveOperationException {
    var constructor = Role.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    return constructor.newInstance();
  }

  private static void asignar(Role rol, String campo, Object valor)
      throws ReflectiveOperationException {
    Field field = Role.class.getDeclaredField(campo);
    field.setAccessible(true);
    field.set(rol, valor);
  }
}
