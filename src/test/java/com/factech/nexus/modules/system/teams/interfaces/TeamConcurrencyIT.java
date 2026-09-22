package com.factech.nexus.modules.system.teams.interfaces;

import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.con;
import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Las carreras de los equipos. Hoy, la del alta (`CA-SP-736`); `RF-SP-068` le añadirá la de dos
 * eliminaciones y `RF-SP-069` las dos suyas —la misma persona a dos equipos, y asignar contra
 * eliminar.
 *
 * <p>Lo que se comprueba no es que una gane —eso lo garantiza el motor— sino que la otra reciba
 * <b>el mismo {@code 409}</b> que habría recibido por la comprobación previa, y no un {@code 500}
 * de una restricción sin traducir. Es lo que justifica que {@code uq_teams_name} se traduzca por
 * nombre de restricción en el repositorio.
 */
@AutoConfigureMockMvc
class TeamConcurrencyIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  @AfterEach
  void limpiar() {
    TeamTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName("`CA-SP-736` — dos altas simultáneas con el mismo nombre: una fila y un 409")
  void dosAltasConElMismoNombre() throws Exception {
    List<Outcome<Integer>> resultados = runTogether(2, indice -> estadoDe(alta("Equipo Norte")));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(cuantos()).isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 201).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-SP-758` — dos renombrados simultáneos al mismo nombre: uno 200, otro 409, ningún 500")
  void dosRenombradosAlMismoNombre() throws Exception {
    // Dos equipos distintos que a la vez quieren llamarse igual. La comprobación
    // previa de cada petición mira una instantánea en la que el nombre todavía
    // está libre, de modo que las dos pasan de largo y quien decide es
    // `uq_teams_name`. Si no estuviera traducido por nombre de restricción, el
    // perdedor recibiría un 500 por un choque que tiene respuesta de negocio.
    UUID primero = TeamTestSupport.equipo(jdbc, "Equipo Uno");
    UUID segundo = TeamTestSupport.equipo(jdbc, "Equipo Dos");

    List<Outcome<Integer>> resultados =
        runTogether(
            2, indice -> estadoDe(renombrar(indice == 0 ? primero : segundo, "Equipo Unificado")));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 200).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .isEqualTo(1);
    // Y queda UNO con ese nombre, no dos: la unicidad no se negoció.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM teams WHERE name = 'Equipo Unificado'", Integer.class))
        .isEqualTo(1);
  }

  private MockHttpServletRequestBuilder renombrar(UUID id, String nombre) {
    return patch("/api/v1/teams/" + id)
        .with(con("teams:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"" + nombre + "\"}");
  }

  private MockHttpServletRequestBuilder alta(String nombre) {
    return post("/api/v1/teams")
        .with(con("teams:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"" + nombre + "\"}");
  }

  private int estadoDe(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }

  private int cuantos() {
    Integer filas = jdbc.queryForObject("SELECT count(*) FROM teams", Integer.class);
    return filas == null ? 0 : filas;
  }
}
