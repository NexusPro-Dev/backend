package com.factech.nexus.modules.system.users.domain.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.modules.system.roles.domain.models.RoleType;
import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.repository.RoleCatalog;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `RN-SP-019` y `RN-SP-020` sin Spring y sin base de datos.
 *
 * <p>La cadena de prueba reproduce la del catálogo sembrado: {@code GERENTE → DIRECTOR → MANAGER →
 * AGENTE}, con {@code GERENTE} como cúspide porque su padre ya no es vendedor.
 */
class CommercialStructureTest {

  private final Catalogo catalogo = new Catalogo();
  private final CommercialStructure estructura = new CommercialStructure(catalogo);

  private final AssignableRole admin = catalogo.alta("ADMIN", RoleType.FUNCIONARIO, null);
  private final AssignableRole gerente = catalogo.alta("GERENTE", RoleType.VENDEDOR, admin);
  private final AssignableRole director = catalogo.alta("DIRECTOR", RoleType.VENDEDOR, gerente);
  private final AssignableRole manager = catalogo.alta("MANAGER", RoleType.VENDEDOR, director);
  private final AssignableRole agente = catalogo.alta("AGENTE", RoleType.VENDEDOR, manager);

  @Test
  @DisplayName("sobre AGENTE y DIRECTOR el de mayor rango es DIRECTOR")
  void mayorRango() {
    // No es «el primero» ni «el último»: es el que no desciende de ningún otro
    // de los que la persona porta.
    assertThat(estructura.rolDeMayorRango(List.of(agente, director)))
        .map(AssignableRole::code)
        .contains("DIRECTOR");
  }

  @Test
  @DisplayName("sin roles vendedores no hay rango comercial")
  void sinVendedores() {
    assertThat(estructura.rolDeMayorRango(List.of(admin))).isEmpty();
  }

  @Test
  @DisplayName("el superior de un AGENTE debe portar MANAGER: el padre INMEDIATO, no un ancestro")
  void padreInmediato() {
    // Es lo que hace que la cadena de personas herede la aciclicidad de la de
    // roles sin necesitar una regla anti-ciclos propia. Admitir un ancestro
    // cualquiera la rompería.
    assertThat(estructura.rolExigidoAlSuperior(agente))
        .map(AssignableRole::code)
        .contains("MANAGER");
    assertThat(estructura.rolExigidoAlSuperior(manager))
        .map(AssignableRole::code)
        .contains("DIRECTOR");
  }

  @Test
  @DisplayName("GERENTE es la cúspide: su padre existe pero no es vendedor")
  void cuspide() {
    assertThat(estructura.esCuspide(gerente)).isTrue();
    assertThat(estructura.rolExigidoAlSuperior(gerente)).isEmpty();
    assertThat(estructura.esCuspide(director)).isFalse();
  }

  @Test
  @DisplayName("un rol vendedor SIN padre también es cúspide")
  void cuspideSinPadre() {
    AssignableRole huerfano = catalogo.alta("SUELTO", RoleType.VENDEDOR, null);
    assertThat(estructura.esCuspide(huerfano)).isTrue();
  }

  @Test
  @DisplayName("ASCENSO: de AGENTE a DIRECTOR el rango CAMBIA")
  void ascenso() {
    // Es lo único que `RF-SP-024` no podía tener, y lo que obliga a declarar de
    // nuevo el superior: un director no puede estar a cargo de otro director.
    assertThat(estructura.cambiaElRango(List.of(agente), List.of(agente, director))).isTrue();
  }

  @Test
  @DisplayName("ASIGNACIÓN LATERAL: añadir AGENTE a un DIRECTOR no cambia el rango")
  void lateral() {
    assertThat(estructura.cambiaElRango(List.of(director), List.of(director, agente))).isFalse();
  }

  @Test
  @DisplayName("conceder el primer rol vendedor cambia el rango; conceder uno funcionario no")
  void primeroYNinguno() {
    assertThat(estructura.cambiaElRango(List.of(), List.of(agente))).isTrue();
    assertThat(estructura.cambiaElRango(List.of(agente), List.of(agente, admin))).isFalse();
  }

  /** Catálogo en memoria: la única dependencia del componente es esta consulta. */
  private static final class Catalogo implements RoleCatalog {

    private final Map<UUID, AssignableRole> porId = new HashMap<>();

    AssignableRole alta(String codigo, RoleType tipo, AssignableRole padre) {
      AssignableRole rol =
          new AssignableRole(
              UUID.randomUUID(),
              codigo,
              codigo,
              tipo,
              false,
              true,
              padre == null ? null : padre.id(),
              Set.of());
      porId.put(rol.id(), rol);
      return rol;
    }

    @Override
    public List<AssignableRole> findAllById(Set<UUID> ids) {
      return ids.stream().map(porId::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public Optional<AssignableRole> findById(UUID id) {
      return Optional.ofNullable(id).map(porId::get);
    }

    @Override
    public Set<UUID> roleIdsOf(UUID userId) {
      return Set.of();
    }
  }
}
