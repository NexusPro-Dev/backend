package com.factech.nexus.modules.system.teams.interfaces;

import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.con;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.eliminar;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.equipo;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.persona;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.pertenencia;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import java.util.Map;
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
 * El cambio de estado de un equipo (`RF-SP-067` · `T-06`): `CA-SP-763` a `CA-SP-765` y `CA-SP-767`
 * a `CA-SP-769`.
 *
 * <p><b>El fixture tiene un equipo con dos miembros porque lo que hay que probar es lo que NO
 * pasa.</b> Suspender no vacía (`RN-SP-053`): ni el detalle pierde a nadie ni el recuento del
 * listado baja. Si alguien «optimizara» cerrando las pertenencias al desactivar, `CA-SP-764` es la
 * prueba que se pondría roja.
 *
 * <p>`CA-SP-766` —que un equipo `INACTIVO` no recibe miembros— <b>no vive aquí todavía</b>: la
 * regla la aplica la asignación, y se escribe con `RF-SP-069` (`T-07`, bloqueo 2 de la tripleta).
 */
@AutoConfigureMockMvc
class TeamStatusIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private static final String[] GENTE = {"estadoequipo1", "estadoequipo2", "estadoequipo3"};

  private UUID poblado;
  private UUID suspendido;
  private UUID eliminado;

  @BeforeEach
  void sembrar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, GENTE);

    poblado = equipo(jdbc, "Equipo Con Gente", "ACTIVO", "Dos managers dentro");
    suspendido = equipo(jdbc, "Equipo En Pausa", "INACTIVO", null);
    eliminado = equipo(jdbc, "Equipo Disuelto Por Estado");
    eliminar(jdbc, eliminado);

    pertenencia(jdbc, poblado, persona(jdbc, GENTE[0]));
    pertenencia(jdbc, poblado, persona(jdbc, GENTE[1]));
  }

  @AfterEach
  void limpiar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, GENTE);
  }

  @Test
  @DisplayName(
      "`CA-SP-763` — desactiva y reactiva, devolviendo en los dos casos 200 con la forma del detalle"
          + " y el estado nuevo")
  void desactivaYReactiva() throws Exception {
    mvc.perform(cambiar(poblado, "{\"status\":\"INACTIVO\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(poblado.toString()))
        .andExpect(jsonPath("$.name").value("Equipo Con Gente"))
        .andExpect(jsonPath("$.description").value("Dos managers dentro"))
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.memberCount").value(2))
        .andExpect(jsonPath("$.members").exists())
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists());

    assertThat(estadoEnBase(poblado)).isEqualTo("INACTIVO");

    // Y vuelve, con la misma forma: la suspensión no dejó nada pendiente de
    // rehacer (`FA-003`). El estado se admite además en cualquier caja.
    mvc.perform(cambiar(poblado, "{\"status\":\"activo\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.memberCount").value(2));

    assertThat(estadoEnBase(poblado)).isEqualTo("ACTIVO");
  }

  @Test
  @DisplayName(
      "`CA-SP-764` — desactivar CONSERVA las pertenencias vigentes: el detalle sigue devolviendo a"
          + " los dos y el listado sigue contándolos")
  void suspenderNoVacia() throws Exception {
    mvc.perform(cambiar(poblado, "{\"status\":\"INACTIVO\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.memberCount").value(2))
        .andExpect(jsonPath("$.members", hasSize(2)));

    // Ninguna fila de team_members se movió: ni cerrada, ni borrada.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM team_members WHERE team_id = ? AND ended_at IS NULL",
                Integer.class,
                poblado))
        .isEqualTo(2);

    // El detalle lo sigue diciendo, y el recuento del listado de `RF-SP-064`
    // tampoco depende del estado.
    mvc.perform(get("/api/v1/teams/" + poblado).with(con("teams:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.members", hasSize(2)));

    mvc.perform(get("/api/v1/teams").param("q", "Con Gente").with(con("teams:list")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].status").value("INACTIVO"))
        .andExpect(jsonPath("$.content[0].memberCount").value(2));
  }

  @Test
  @DisplayName(
      "`CA-SP-765` — pedir el estado que ya tiene responde 200 sin escribir: ninguna fila de"
          + " auditoría y updatedAt sin avanzar")
  void idempotente() throws Exception {
    // El equipo nace con created_at = updated_at, y la petición que no cambia
    // nada no debe moverlo (`FA-001`).
    assertThat(avanzo(suspendido)).isFalse();

    mvc.perform(cambiar(suspendido, "{\"status\":\"INACTIVO\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVO"));

    assertThat(avanzo(suspendido)).isFalse();
    assertThat(filasDeAuditoria(suspendido)).isZero();

    // Repetirla tampoco: el reintento de un cliente con mala red no ensucia la
    // auditoría.
    mvc.perform(cambiar(suspendido, "{\"status\":\"inactivo\"}")).andExpect(status().isOk());
    assertThat(filasDeAuditoria(suspendido)).isZero();

    // Y el cambio de verdad sí escribe y sí avanza la marca, para que el cero de
    // arriba signifique algo.
    mvc.perform(cambiar(suspendido, "{\"status\":\"ACTIVO\"}")).andExpect(status().isOk());
    assertThat(avanzo(suspendido)).isTrue();
    assertThat(filasDeAuditoria(suspendido)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-SP-767` — 400 con el estado ausente, desconocido, el identificador mal formado y un"
          + " cuerpo con campos no admitidos; 404 con el inexistente y el eliminado")
  void rechazaLoMalFormado() throws Exception {
    mvc.perform(cambiar(poblado, "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(startsWith("El estado indicado no es válido")))
        .andExpect(jsonPath("$.errors[0].field").value("status"))
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));

    mvc.perform(cambiar(poblado, "{\"status\":\"PAUSADO\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(startsWith("El estado indicado no es válido")));

    mvc.perform(cambiar(poblado, "{\"status\":\"   \"}")).andExpect(status().isBadRequest());

    // `VAL-002`: el identificador de la ruta.
    mvc.perform(
            patch("/api/v1/teams/no-es-un-uuid/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVO\"}")
                .with(con("teams:change-status")))
        .andExpect(status().isBadRequest());

    // `VAL-003`: ni motivo, ni nombre, ni miembros. El motivo es la barrera de lo
    // irreversible y esto se deshace con una petición.
    mvc.perform(cambiar(poblado, "{\"status\":\"INACTIVO\",\"reason\":\"Porque sí\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(cambiar(poblado, "{\"status\":\"INACTIVO\",\"name\":\"Otro\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(cambiar(poblado, "{\"status\":\"INACTIVO\",\"members\":[]}"))
        .andExpect(status().isBadRequest());

    // Nada de lo anterior tocó el estado.
    assertThat(estadoEnBase(poblado)).isEqualTo("ACTIVO");

    mvc.perform(cambiar(UUID.randomUUID(), "{\"status\":\"INACTIVO\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No existe un equipo con ese identificador."));

    // Sobre un eliminado no hay estado que cambiar, y el mismo 404 lo dice.
    mvc.perform(cambiar(eliminado, "{\"status\":\"INACTIVO\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No existe un equipo con ese identificador."));
  }

  @Test
  @DisplayName(
      "`CA-SP-768` — audit_change_log recibe una fila UPDATE con el actor y los dos estados, y"
          + " audit_security_log NINGUNA: nadie gana ni pierde permisos")
  void auditaElCambioYNoLaSeguridad() throws Exception {
    UUID actor = UUID.randomUUID();
    int seguridadAntes =
        jdbc.queryForObject("SELECT count(*) FROM audit_security_log", Integer.class);

    mvc.perform(
            patch("/api/v1/teams/" + poblado + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVO\"}")
                .with(con(actor, "teams:change-status")))
        .andExpect(status().isOk());

    List<Map<String, Object>> filas =
        jdbc.queryForList(
            "SELECT actor_id::text AS actor, action, changes::text AS changes FROM audit_change_log"
                + " WHERE module = 'SP' AND entity = 'teams' AND entity_id = ?",
            poblado);
    assertThat(filas).hasSize(1);
    assertThat(filas.get(0).get("actor")).isEqualTo(actor.toString());
    assertThat(filas.get(0).get("action")).isEqualTo("UPDATE");
    assertThat((String) filas.get(0).get("changes"))
        .contains("\"before\": \"ACTIVO\"")
        .contains("\"after\": \"INACTIVO\"");

    assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_security_log", Integer.class))
        .isEqualTo(seguridadAntes);
  }

  @Test
  @DisplayName(
      "`CA-SP-769` — sin teams:change-status responde 403 aunque el actor porte teams:update y"
          + " teams:read")
  void teamsUpdateNoHabilita() throws Exception {
    mvc.perform(
            patch("/api/v1/teams/" + poblado + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVO\"}")
                .with(con("teams:update", "teams:read", "teams:list")))
        .andExpect(status().isForbidden());

    assertThat(estadoEnBase(poblado)).isEqualTo("ACTIVO");
    assertThat(filasDeAuditoria(poblado)).isZero();
  }

  @Test
  @DisplayName(
      "`CA-SP-766` — un equipo INACTIVO no admite miembros nuevos: la asignación responde 409"
          + " mientras siga suspendido, y vuelve a admitirlos al reactivarlo")
  void elSuspendidoNoRecibeMiembros() throws Exception {
    // La deuda que `RF-SP-067` `T-07` dejó declarada: la regla que este estado
    // significa (`RN-SP-053`) la aplica QUIEN INTENTA ENTRAR, y por eso se
    // verifica desde la asignación y no desde el cambio de estado. Vive en esta
    // suite, y no en la de miembros, porque lo que prueba es qué significa el
    // estado — no cómo se asigna.
    UUID manager = TeamTestSupport.personaConRol(jdbc, GENTE[2], "MANAGER");

    mvc.perform(cambiar(poblado, "{\"status\":\"INACTIVO\"}")).andExpect(status().isOk());

    mvc.perform(asignarA(poblado, manager))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "El equipo está inactivo y no admite miembros nuevos. Actívelo antes de"
                        + " asignar."));
    assertThat(vigentesDe(poblado)).isEqualTo(2);

    // Y en cuanto vuelve a estar activo, entra.
    mvc.perform(cambiar(poblado, "{\"status\":\"ACTIVO\"}")).andExpect(status().isOk());
    mvc.perform(asignarA(poblado, manager)).andExpect(status().isOk());
    assertThat(vigentesDe(poblado)).isEqualTo(3);
  }

  private MockHttpServletRequestBuilder asignarA(UUID equipo, UUID persona) {
    return post("/api/v1/teams/" + equipo + "/members")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"memberIds\":[\"" + persona + "\"],\"reason\":\"Se incorpora a la region.\"}")
        .with(con("teams:assign-members"));
  }

  private int vigentesDe(UUID equipo) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM team_members WHERE team_id = ? AND ended_at IS NULL",
        Integer.class,
        equipo);
  }

  private MockHttpServletRequestBuilder cambiar(UUID id, String cuerpo) {
    return patch("/api/v1/teams/" + id + "/status")
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo)
        .with(con("teams:change-status"));
  }

  private String estadoEnBase(UUID id) {
    return jdbc.queryForObject("SELECT status FROM teams WHERE id = ?", String.class, id);
  }

  private boolean avanzo(UUID id) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT created_at <> updated_at FROM teams WHERE id = ?", Boolean.class, id));
  }

  private int filasDeAuditoria(UUID id) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_change_log WHERE entity = 'teams' AND entity_id = ?",
        Integer.class,
        id);
  }
}
