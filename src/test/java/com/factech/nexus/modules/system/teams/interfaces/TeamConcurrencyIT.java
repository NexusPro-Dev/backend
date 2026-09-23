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

  private static final String[] PERSONAS = {"carreraequipo1", "carreraequipo2"};

  @BeforeEach
  @AfterEach
  void limpiar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, PERSONAS);
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

  @Test
  @DisplayName(
      "`CA-SP-777` — dos eliminaciones simultáneas del mismo equipo: un 204, un 409 y UNA sola fila"
          + " de auditoría")
  void dosEliminacionesDelMismoEquipo() throws Exception {
    // Las dos peticiones leen el equipo vivo antes de que ninguna lo marque. Lo
    // que las ordena es el bloqueo de `findByIdForUpdate`: la segunda espera, ve
    // el `deleted_at` que dejó la primera y sale por `EX-002` en lugar de
    // escribir una segunda baja sobre la misma fila — que dejaría dos registros
    // de eliminación del mismo equipo con dos motivos distintos.
    UUID equipo = TeamTestSupport.equipo(jdbc, "Equipo Que Se Disuelve");

    List<Outcome<Integer>> resultados = runTogether(2, indice -> estadoDe(baja(equipo)));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 204).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_deletion_log WHERE entity = 'teams' AND entity_id = ?",
                Integer.class,
                equipo))
        .isEqualTo(1);
  }

  private MockHttpServletRequestBuilder baja(UUID id) {
    return post("/api/v1/teams/" + id + "/deletion")
        .with(con("teams:delete"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"Se disuelve por reorganizacion.\"}");
  }

  @Test
  @DisplayName(
      "`RN-SP-052` — la misma persona asignada a dos equipos a la vez: UNA sola pertenencia"
          + " vigente al final, ningún 500, y ninguna de las dos peticiones se pierde")
  void lamismaPersonaADosEquiposALaVez() throws Exception {
    UUID primero = TeamTestSupport.equipo(jdbc, "Equipo Carrera Uno");
    UUID segundo = TeamTestSupport.equipo(jdbc, "Equipo Carrera Dos");
    UUID manager = TeamTestSupport.personaConRol(jdbc, PERSONAS[0], "MANAGER");

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(asignar(indice == 0 ? primero : segundo, manager)));

    // LO QUE SE AFIRMA ES LA INVARIANTE, y no cuál de los dos desenlaces
    // ocurrió, porque LOS DOS SON CORRECTOS y cuál toca depende de si las
    // peticiones llegan a solaparse:
    //
    //   · Si se solapan, el bloqueo del equipo no ordena nada —son equipos
    //     distintos— y decide `uq_team_members_vigente`: una entra con `200` y la
    //     otra recibe el `409` traducido, nunca un `500`.
    //   · Si NO se solapan —y en CI es lo normal, porque va más rápido que una
    //     máquina de desarrollo—, la segunda VE la pertenencia que dejó la
    //     primera y MUEVE a la persona: dos `200` y la anterior cerrada, que es
    //     literalmente lo que `RN-SP-052` manda hacer al asignar a otro equipo.
    //
    // Exigir «exactamente un 200» afirmaba el primer desenlace y convertía el
    // segundo —correcto— en un fallo. Costó una corrida de CI el 23-09-2026.
    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(resultados).allMatch(Outcome::succeeded);
    assertThat(resultados.stream().filter(r -> r.value() == 200 || r.value() == 409).count())
        .as("toda peticion acaba en 200 o en 409: %s", resultados)
        .isEqualTo(2);
    assertThat(resultados.stream().filter(r -> r.value() == 200).count())
        .as("al menos una entra: %s", resultados)
        .isGreaterThanOrEqualTo(1);

    // La invariante de `RN-SP-052`, que es lo único que no puede fallar nunca:
    // UNA pertenencia vigente, pase lo que pase.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM team_members WHERE user_id = ? AND ended_at IS NULL",
                Integer.class,
                manager))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-SP-777` (la mitad cruzada) — asignar contra eliminar el mismo equipo: nunca queda un"
          + " equipo eliminado con alguien dentro")
  void asignarContraEliminar() throws Exception {
    // Es la media carrera que `RF-SP-068` `T-09` dejó declarada esperando a esta
    // operación. Las dos piden el MISMO equipo con bloqueo, de modo que el motor
    // las ordena: si gana la baja, la asignación recibe `404`; si gana la
    // asignación, la baja recibe su `409` por tener miembros. Lo que no puede
    // pasar —y es lo único que se afirma— es que ocurran las dos.
    UUID equipo = TeamTestSupport.equipo(jdbc, "Equipo Que Se Disputa");
    UUID manager = TeamTestSupport.personaConRol(jdbc, PERSONAS[1], "MANAGER");

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(indice == 0 ? asignar(equipo, manager) : baja(equipo)));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    boolean eliminado =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM teams WHERE id = ?", Boolean.class, equipo));
    int vigentes =
        jdbc.queryForObject(
            "SELECT count(*) FROM team_members WHERE team_id = ? AND ended_at IS NULL",
            Integer.class,
            equipo);

    // El estado prohibido es «eliminado y con gente dentro».
    assertThat(eliminado && vigentes > 0).isFalse();
    // Y una de las dos ocurrió de verdad: o el equipo quedó eliminado y vacío, o
    // vivo con su miembro dentro. Ninguna se perdió en silencio.
    assertThat(eliminado ? vigentes == 0 : vigentes == 1).isTrue();
  }

  private MockHttpServletRequestBuilder asignar(UUID equipo, UUID persona) {
    return post("/api/v1/teams/" + equipo + "/members")
        .with(con("teams:assign-members"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"memberIds\":[\"" + persona + "\"],\"reason\":\"Carrera de prueba.\"}");
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
