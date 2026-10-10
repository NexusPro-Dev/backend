package com.factech.nexus.modules.system.teams.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.modules.system.roles.domain.models.RoleType;
import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.repository.RoleCatalog;
import com.factech.nexus.modules.system.users.domain.security.CommercialStructure;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `RN-SP-051` aislada (`RF-SP-069` · `T-13`, enmendada el 09-10-2026): <b>a un equipo solo
 * pertenece un director</b>.
 *
 * <p><b>Sin Spring y sin base de datos.</b> El catálogo de roles es un doble en memoria con la
 * forma de la jerarquía real —`ADMIN` administrativo, `MANAGER` colgando de él, `DIRECTOR` de
 * `MANAGER`, `AGENTE` de `DIRECTOR` y `CLIENTE` consumidor—, que es lo único que la regla mira: el
 * director es el rol cuyo rol exigido al superior es la cúspide.
 *
 * <p>Por eso esta prueba dice algo que la de integración no puede decir tan claro: la regla no
 * nombra a `DIRECTOR` en ninguna parte, y un rol renombrado sigue siendo director.
 */
class TeamMembershipRulesTest {

  private static final UUID ADMIN = UUID.randomUUID();
  private static final UUID MANAGER = UUID.randomUUID();
  private static final UUID DIRECTOR = UUID.randomUUID();
  private static final UUID AGENTE = UUID.randomUUID();
  private static final UUID CLIENTE = UUID.randomUUID();

  private static final AssignableRole ROL_ADMIN = rol(ADMIN, "ADMIN", RoleType.FUNCIONARIO, null);
  private static final AssignableRole ROL_MANAGER =
      rol(MANAGER, "MANAGER", RoleType.VENDEDOR, ADMIN);
  private static final AssignableRole ROL_DIRECTOR =
      rol(DIRECTOR, "DIRECTOR", RoleType.VENDEDOR, MANAGER);
  private static final AssignableRole ROL_AGENTE =
      rol(AGENTE, "AGENTE", RoleType.VENDEDOR, DIRECTOR);
  private static final AssignableRole ROL_CLIENTE =
      rol(CLIENTE, "CLIENTE", RoleType.CONSUMIDOR, ADMIN);

  private final TeamMembershipRules reglas =
      new TeamMembershipRules(new CommercialStructure(new CatalogoEnMemoria()));

  @Test
  @DisplayName(
      "`CA-SP-982` — el director entra; el manager, el agente, el cliente y quien no tiene rol, no")
  void soloElDirector() {
    UUID manager = UUID.randomUUID();
    UUID director = UUID.randomUUID();
    UUID agente = UUID.randomUUID();
    UUID cliente = UUID.randomUUID();
    UUID sinRol = UUID.randomUUID();

    Map<UUID, List<AssignableRole>> roles = new HashMap<>();
    roles.put(manager, List.of(ROL_MANAGER));
    roles.put(director, List.of(ROL_DIRECTOR));
    roles.put(agente, List.of(ROL_AGENTE));
    roles.put(cliente, List.of(ROL_CLIENTE));

    TeamMembershipRules.Veredicto veredicto =
        reglas.evaluar(List.of(manager, director, agente, cliente, sinRol), roles);

    assertThat(veredicto.admitidos()).containsExactly(director);
    assertThat(veredicto.rechazados()).containsExactly(manager, agente, cliente, sinRol);
    assertThat(veredicto.todosAdmitidos()).isFalse();
  }

  @Test
  @DisplayName("un director solo se admite")
  void unDirector() {
    UUID director = UUID.randomUUID();

    TeamMembershipRules.Veredicto veredicto =
        reglas.evaluar(List.of(director), Map.of(director, List.of(ROL_DIRECTOR)));

    assertThat(veredicto.todosAdmitidos()).isTrue();
    assertThat(veredicto.rechazados()).isEmpty();
  }

  @Test
  @DisplayName(
      "se mira el rol de MAYOR RANGO y no «alguno»: quien porta AGENTE además de DIRECTOR entra, y"
          + " quien porta DIRECTOR además de MANAGER no")
  void miraElDeMayorRango() {
    UUID esDirector = UUID.randomUUID();
    UUID sigueSiendoManager = UUID.randomUUID();

    TeamMembershipRules.Veredicto veredicto =
        reglas.evaluar(
            List.of(esDirector, sigueSiendoManager),
            Map.of(
                esDirector,
                List.of(ROL_AGENTE, ROL_DIRECTOR),
                sigueSiendoManager,
                List.of(ROL_DIRECTOR, ROL_MANAGER)));

    assertThat(veredicto.admitidos()).containsExactly(esDirector);
    assertThat(veredicto.rechazados()).containsExactly(sigueSiendoManager);
  }

  @Test
  @DisplayName(
      "`CA-SP-982` — la regla no nombra a DIRECTOR: el rol renombrado, con la misma forma, sigue"
          + " entrando")
  void elDirectorEsLaFormaDeLaJerarquia() {
    AssignableRole renombrado = rol(DIRECTOR, "JEFE_DE_OFICINA", RoleType.VENDEDOR, MANAGER);
    CatalogoEnMemoria catalogo = new CatalogoEnMemoria();
    catalogo.agregar(renombrado);
    TeamMembershipRules conRenombre = new TeamMembershipRules(new CommercialStructure(catalogo));

    UUID jefe = UUID.randomUUID();
    TeamMembershipRules.Veredicto veredicto =
        conRenombre.evaluar(List.of(jefe), Map.of(jefe, List.of(renombrado)));

    assertThat(veredicto.admitidos()).containsExactly(jefe);
  }

  @Test
  @DisplayName(
      "si nace un rango por encima de MANAGER, el director pasa a ser el MANAGER: el que está justo"
          + " debajo de la cúspide")
  void unRangoNuevoMueveAlDirector() {
    UUID regionalId = UUID.randomUUID();
    AssignableRole regional = rol(regionalId, "REGIONAL", RoleType.VENDEDOR, ADMIN);
    AssignableRole managerBajoRegional = rol(MANAGER, "MANAGER", RoleType.VENDEDOR, regionalId);

    CatalogoEnMemoria catalogo = new CatalogoEnMemoria();
    catalogo.agregar(regional);
    catalogo.agregar(managerBajoRegional);
    TeamMembershipRules conRegional = new TeamMembershipRules(new CommercialStructure(catalogo));

    UUID manager = UUID.randomUUID();
    UUID director = UUID.randomUUID();

    TeamMembershipRules.Veredicto veredicto =
        conRegional.evaluar(
            List.of(manager, director),
            Map.of(manager, List.of(managerBajoRegional), director, List.of(ROL_DIRECTOR)));

    assertThat(veredicto.admitidos()).containsExactly(manager);
    assertThat(veredicto.rechazados()).containsExactly(director);
  }

  private static AssignableRole rol(UUID id, String codigo, RoleType tipo, UUID padre) {
    return new AssignableRole(id, codigo, codigo, tipo, false, true, padre, Set.of());
  }

  /** El catálogo mínimo que {@link CommercialStructure} necesita: resolver el rol padre. */
  private static final class CatalogoEnMemoria implements RoleCatalog {

    private final Map<UUID, AssignableRole> roles = new HashMap<>();

    private CatalogoEnMemoria() {
      List.of(ROL_ADMIN, ROL_MANAGER, ROL_DIRECTOR, ROL_AGENTE, ROL_CLIENTE).forEach(this::agregar);
    }

    private void agregar(AssignableRole rol) {
      roles.put(rol.id(), rol);
    }

    @Override
    public List<AssignableRole> findAllById(Set<UUID> ids) {
      return ids.stream().map(roles::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public Optional<AssignableRole> findById(UUID id) {
      return Optional.ofNullable(roles.get(id));
    }

    @Override
    public Optional<AssignableRole> findByCode(String code) {
      return roles.values().stream().filter(rol -> rol.code().equals(code)).findFirst();
    }

    @Override
    public Set<UUID> roleIdsOf(UUID userId) {
      return Set.of();
    }

    @Override
    public Map<UUID, Set<UUID>> roleIdsOfAll(Set<UUID> userIds) {
      return Map.of();
    }
  }
}
