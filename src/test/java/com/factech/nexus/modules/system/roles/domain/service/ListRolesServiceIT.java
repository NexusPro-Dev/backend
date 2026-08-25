package com.factech.nexus.modules.system.roles.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.roles.application.ListRolesRequest;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Coste del listado de roles (`RF-SP-002` · `T-05` y `T-07`).
 *
 * <p>Lo que se comprueba aquí <b>no se ve en el resultado</b>: cuántas sentencias cuesta la
 * consulta. Es la única forma de que el {@code N+1} no vuelva en una refactorización posterior —una
 * página de veinte roles con padre que empezara a costar veintiuna consultas devolvería exactamente
 * el mismo JSON, y ninguna prueba funcional lo notaría.
 */
class ListRolesServiceIT extends IntegrationTestBase {

  @Autowired private ListRolesService service;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;

  @BeforeEach
  void reiniciarEstadisticas() {
    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
  }

  @Test
  @DisplayName("una página que no se llena cuesta UNA sentencia: el conteo se deduce")
  void sinConteoCuandoLaPaginaNoSeLlena() {
    // El catálogo de sistema son ocho roles y caben de sobra en la página por
    // omisión: si una página no se llena desde el desplazamiento cero, ella
    // misma ES el total y contar sería una sentencia regalada.
    var pagina = service.list(sinFiltros());

    assertThat(pagina.totalElements()).isEqualTo(pagina.content().size());
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("una página llena cuesta DOS: la página y el conteo, nunca una por fila")
  void dosSentenciasComoMaximo() {
    // Con tamaño 3 la página se llena, de modo que hace falta contar de verdad.
    // Dos sentencias es el techo: el rol padre viaja en el LEFT JOIN de la
    // misma consulta y `role_permissions` no se toca en ningún momento.
    var pagina = service.list(new ListRolesRequest(0, 3, null, null, null, null, null, null));

    assertThat(pagina.content()).hasSize(3);
    assertThat(pagina.totalElements()).isGreaterThan(3);
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("la página vacía SÍ cuenta: su total no puede deducirse del desplazamiento")
  void laPaginaVaciaCuenta() {
    var pagina = service.list(new ListRolesRequest(99, 20, null, null, null, null, null, null));

    assertThat(pagina.content()).isEmpty();
    // Y no 1980, que es lo que daría deducir el total del desplazamiento.
    assertThat(pagina.totalElements()).isGreaterThan(0).isLessThan(100);
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("el filtro y el conteo no pueden divergir: salen del mismo predicado")
  void elConteoAplicaElMismoFiltro() {
    var vendedores =
        service.list(new ListRolesRequest(0, 2, null, null, "VENDEDOR", null, null, null));

    // Tres roles de la fuerza comercial, en páginas de dos: el total dice tres
    // aunque la página traiga dos. Un conteo escrito aparte —sin el filtro—
    // diría ocho, y el defecto solo se ve comparando ambos números.
    assertThat(vendedores.content()).hasSize(2);
    assertThat(vendedores.totalElements()).isEqualTo(3);
  }

  private static ListRolesRequest sinFiltros() {
    return new ListRolesRequest(null, null, null, null, null, null, null, null);
  }
}
