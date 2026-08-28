package com.factech.nexus.modules.system.users.domain.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.roles.domain.models.RoleType;
import com.factech.nexus.modules.system.users.application.AssignRolesRequest;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.repository.MembershipCatalog;
import com.factech.nexus.modules.system.users.domain.repository.RoleCatalog;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.modules.system.users.domain.security.CommercialStructure;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * El <b>orden</b> en que se verifica el alta de roles a una persona (`RF-SP-030` · `T-06`).
 *
 * <h2>Por qué el orden merece pruebas propias</h2>
 *
 * <p>La suite de integración comprueba <b>el resultado</b>: que una petición mal formada se
 * rechaza. Eso no dice nada del orden, y el orden es lo que decide <b>qué error recibe quien
 * incumple varias reglas a la vez</b>. Alguien que pide un rol fuera de su alcance y además olvida
 * la membresía debe recibir `RN-SEG-010` —el intento de escalada— y no un aviso sobre la membresía:
 * de los dos, el que hay que registrar y responder es el primero.
 *
 * <p>Un cambio de orden <b>no rompe ninguna prueba de resultado</b>. Sigue habiendo un rechazo,
 * sigue siendo un `4xx`, y solo cambia cuál. Por eso `plan.md` §4 lo fija paso a paso y `tasks.md`
 * pedía verificarlo con dobles: es lo único que lo sujeta.
 *
 * <h2>Se verifica por lo que NO se llega a preguntar</h2>
 *
 * <p>Cada prueba comprueba que los colaboradores de los pasos posteriores <b>no se tocan</b>. Es
 * más fuerte que mirar el código de error: si mañana alguien reordena y el error acaba siendo el
 * mismo por casualidad, la interacción sobrante lo delata igual.
 */
class AssignUserRolesOrderTest {

  private static final UUID PERSONA = UUID.fromString("01a02a33-4c00-7aaa-9c4f-5e7ad1000001");
  private static final UUID ROL = UUID.fromString("01a02a33-4c00-7bbb-9c4f-5e7ad1000002");

  private final UserRepository usuarios = mock(UserRepository.class);
  private final RoleCatalog roles = mock(RoleCatalog.class);
  private final MembershipCatalog membresias = mock(MembershipCatalog.class);
  private final CommercialStructure estructura = mock(CommercialStructure.class);
  private final AuthenticatedActor actor = mock(AuthenticatedActor.class);
  private final AuditWriter auditoria = mock(AuditWriter.class);
  private final UuidV7Generator ids = mock(UuidV7Generator.class);

  private final AssignUserRolesService servicio =
      new AssignUserRolesService(
          usuarios,
          roles,
          membresias,
          estructura,
          actor,
          auditoria,
          ids,
          Clock.fixed(Instant.parse("2026-08-27T10:00:00Z"), ZoneOffset.UTC));

  @Nested
  @DisplayName("paso 2 — la persona, antes que nada")
  class LaPersonaPrimero {

    @Test
    @DisplayName("si no existe, NO se llega a mirar el catálogo de roles")
    void personaInexistente() {
      when(usuarios.findNotDeletedByIdForUpdate(PERSONA)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> servicio.assign(PERSONA, peticion()))
          .isInstanceOf(ResourceNotFoundException.class);

      /*
       * Resolver los roles de una petición dirigida a alguien que no existe es
       * trabajo tirado, y además abriría una vía para averiguar qué roles hay
       * en el catálogo desde una ruta que responde `404`.
       */
      verifyNoInteractions(roles, membresias, estructura, actor, auditoria);
    }
  }

  @Nested
  @DisplayName("pasos 3 y 4 — los roles, antes que el alcance y que la coherencia")
  class LosRolesAntes {

    @Test
    @DisplayName("un rol inexistente se rechaza sin preguntarle al actor qué alcanza")
    void rolInexistente() {
      hayPersona();
      when(roles.roleIdsOf(PERSONA)).thenReturn(Set.of());
      when(roles.findAllById(anySet())).thenReturn(List.of());

      assertThatThrownBy(() -> servicio.assign(PERSONA, peticion()))
          .isInstanceOf(UnprocessableEntityException.class)
          .hasMessageContaining("no existen");

      verifyNoInteractions(membresias, estructura);
      verify(actor, never()).permissions();
    }

    @Test
    @DisplayName("un rol inactivo también, y con su propio código")
    void rolInactivo() {
      hayPersona();
      when(roles.roleIdsOf(PERSONA)).thenReturn(Set.of());
      when(roles.findAllById(anySet())).thenReturn(List.of(rol(false, RoleType.FUNCIONARIO)));

      assertThatThrownBy(() -> servicio.assign(PERSONA, peticion()))
          .isInstanceOf(UnprocessableEntityException.class)
          .hasMessageContaining("inactivos");

      verifyNoInteractions(membresias, estructura);
      verify(actor, never()).permissions();
    }
  }

  @Nested
  @DisplayName("paso 5 — el alcance del actor, ANTES que la membresía y el superior")
  class ElAlcanceAntesQueLaCoherencia {

    @Test
    @DisplayName("un rol fuera del alcance se rechaza aunque además falte la membresía")
    void alcanceAntesQueMembresia() {
      /*
       * Es el caso que `plan.md` §4 justifica por escrito: la petición incumple
       * DOS cosas a la vez —el rol excede al actor y, siendo `CONSUMIDOR`, no
       * trae membresía— y lo que debe responderse es el intento de escalada.
       * Con el orden invertido, quien intenta escalar recibiría un aviso sobre
       * la membresía y el intento no quedaría registrado como lo que es.
       */
      hayPersona();
      when(roles.roleIdsOf(PERSONA)).thenReturn(Set.of());
      when(roles.findAllById(anySet())).thenReturn(List.of(rol(true, RoleType.CONSUMIDOR)));
      // El actor no tiene el permiso que ese rol concede.
      when(actor.permissions()).thenReturn(Set.of("users:read"));

      assertThatThrownBy(() -> servicio.assign(PERSONA, peticion()))
          .isInstanceOf(BusinessRuleException.class)
          .hasMessageContaining("exceden sus propios permisos");

      verifyNoInteractions(membresias, estructura);
      verify(usuarios, never()).addRoles(any(), anySet());
      verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("y nada se escribe: un rechazo no deja el alta a medias")
    void nadaSeEscribe() {
      hayPersona();
      when(roles.roleIdsOf(PERSONA)).thenReturn(Set.of());
      when(roles.findAllById(anySet())).thenReturn(List.of(rol(true, RoleType.FUNCIONARIO)));
      when(actor.permissions()).thenReturn(Set.of());

      assertThatThrownBy(() -> servicio.assign(PERSONA, peticion()))
          .isInstanceOf(BusinessRuleException.class);

      verify(usuarios, never()).addRoles(any(), anySet());
      verify(usuarios, never()).assignMembership(any(), any(), any(), any());
      verify(usuarios, never()).assignSupervisor(any(), any(), any(), any());
    }
  }

  private void hayPersona() {
    when(usuarios.findNotDeletedByIdForUpdate(PERSONA)).thenReturn(Optional.of(mock(User.class)));
  }

  private static AssignableRole rol(boolean activo, RoleType tipo) {
    return new AssignableRole(
        ROL,
        "CONTABILIDAD",
        "Contabilidad",
        tipo,
        false,
        activo,
        null,
        Set.of("users:read", "roles:read"));
  }

  private static AssignRolesRequest peticion() {
    return new AssignRolesRequest(List.of(ROL), null, null, null);
  }
}
