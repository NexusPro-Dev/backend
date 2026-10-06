package com.factech.nexus.modules.indicators.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.modules.movements.application.SalesFigures.SalesScope;
import com.factech.nexus.modules.system.users.application.CommercialReach.Reach;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** `RF-IN-001` · `T-05` — las nueve casillas de `plan.md` §3.2. */
class SalesScopeResolverTest {

  private final UUID actor = UUID.randomUUID();
  private final UUID deMiRed = UUID.randomUUID();
  private final UUID ajeno = UUID.randomUUID();

  private Optional<SalesScope> con(Reach alcance, UUID vendedor) {
    return new SalesScopeResolver(id -> alcance).resolve(actor, vendedor);
  }

  @Test
  @DisplayName("todo: sin vendedor, todo el libro; con vendedor, ese, exista o no")
  void todo() {
    assertThat(con(Reach.everything(), null)).contains(SalesScope.everything());
    assertThat(con(Reach.everything(), ajeno)).contains(SalesScope.sellers(Set.of(ajeno)));
  }

  @Test
  @DisplayName("red: sin vendedor, la red; con uno de ella, ese; con uno ajeno, fuera")
  void red() {
    Reach red = Reach.network(Set.of(actor, deMiRed));

    assertThat(con(red, null)).contains(SalesScope.sellers(Set.of(actor, deMiRed)));
    assertThat(con(red, deMiRed)).contains(SalesScope.sellers(Set.of(deMiRed)));
    assertThat(con(red, ajeno)).isEmpty();
  }

  @Test
  @DisplayName("propio: lo que vendió él, y cualquier otro vendedor queda fuera")
  void propio() {
    assertThat(con(Reach.own(), null)).contains(SalesScope.sellers(Set.of(actor)));
    assertThat(con(Reach.own(), actor)).contains(SalesScope.sellers(Set.of(actor)));
    assertThat(con(Reach.own(), deMiRed)).isEmpty();
  }
}
