package com.factech.nexus.modules.system.teams.interfaces;

import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.con;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.eliminar;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.equipo;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.persona;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.pertenencia;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.pertenenciaCerrada;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
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
 * La baja de un equipo (`RF-SP-068` · `T-08`): `CA-SP-770` a `CA-SP-776`.
 *
 * <p><b>El fixture tiene los cuatro equipos que la regla distingue</b>, y eso es lo que hace
 * verificable `RN-SP-054`: uno vacío, uno con <b>solo pertenencias cerradas</b> —que se elimina—,
 * uno con una <b>vigente</b> —que no— y uno ya eliminado. Si alguien «simplificara» la comprobación
 * a un {@code count(*)} sobre {@code team_members}, el segundo equipo es el que se pondría rojo.
 *
 * <p>La carrera de dos eliminaciones simultáneas (`CA-SP-777`) vive en {@link TeamConcurrencyIT}.
 */
@AutoConfigureMockMvc
class TeamDeletionIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private static final String[] GENTE = {"bajaequipo1", "bajaequipo2", "bajaequipo3"};
  private static final String MOTIVO = "Se fusiono con el Equipo Centro.";

  private UUID vacio;
  private UUID conHistorial;
  private UUID conVigente;
  private UUID yaEliminado;
  private UUID pasoPorAqui;
  private UUID tambienPaso;

  @BeforeEach
  void sembrar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, GENTE);

    vacio = equipo(jdbc, "Equipo Vacio", "INACTIVO", "Se dejo de usar");
    conHistorial = equipo(jdbc, "Equipo Con Historial");
    conVigente = equipo(jdbc, "Equipo Con Gente Dentro");
    yaEliminado = equipo(jdbc, "Equipo Ya Retirado");
    eliminar(jdbc, yaEliminado);

    pasoPorAqui = persona(jdbc, GENTE[0]);
    tambienPaso = persona(jdbc, GENTE[1]);
    pertenenciaCerrada(jdbc, conHistorial, pasoPorAqui);
    pertenenciaCerrada(jdbc, conHistorial, tambienPaso);
    pertenencia(jdbc, conVigente, persona(jdbc, GENTE[2]));
  }

  @AfterEach
  void limpiar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, GENTE);
  }

  @Test
  @DisplayName(
      "`CA-SP-770` — elimina con 204 un equipo vacío, y la fila conserva status y todo lo demás"
          + " salvo deleted_at")
  void eliminaElVacio() throws Exception {
    Map<String, Object> antes =
        jdbc.queryForMap(
            "SELECT name, description, status, updated_at FROM teams WHERE id = ?", vacio);

    mvc.perform(baja(vacio, MOTIVO)).andExpect(status().isNoContent());

    Map<String, Object> despues =
        jdbc.queryForMap(
            "SELECT name, description, status, updated_at, deleted_at FROM teams WHERE id = ?",
            vacio);
    assertThat(despues.get("deleted_at")).isNotNull();
    // Nada más se movió: ni el estado —un eliminado conserva el que tenía— ni la
    // marca de actualización, porque la baja no es una corrección.
    assertThat(despues.get("name")).isEqualTo(antes.get("name"));
    assertThat(despues.get("description")).isEqualTo(antes.get("description"));
    assertThat(despues.get("status")).isEqualTo("INACTIVO");
    assertThat(despues.get("updated_at")).isEqualTo(antes.get("updated_at"));
  }

  @Test
  @DisplayName(
      "`CA-SP-771` — 409 con miembros vigentes, con el mensaje que nombra las dos salidas, y sin"
          + " escribir nada")
  void rechazaElQueTieneGente() throws Exception {
    mvc.perform(baja(conVigente, MOTIVO))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "El equipo tiene miembros y no puede eliminarse. Retírelos o reasígnelos"
                        + " antes."))
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at IS NULL FROM teams WHERE id = ?", Boolean.class, conVigente))
        .isTrue();
    assertThat(filasDeBaja(conVigente)).isZero();
  }

  @Test
  @DisplayName(
      "`CA-SP-772` — el equipo con SOLO pertenencias cerradas se elimina, y esas filas siguen ahí"
          + " después: el historial sobrevive a la baja")
  void eliminaElQueSoloTieneHistorial() throws Exception {
    mvc.perform(baja(conHistorial, MOTIVO)).andExpect(status().isNoContent());

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM team_members WHERE team_id = ?", Integer.class, conHistorial))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM team_members WHERE team_id = ? AND ended_at IS NOT NULL",
                Integer.class,
                conHistorial))
        .isEqualTo(2);
  }

  @Test
  @DisplayName(
      "`CA-SP-773` — 400 con el motivo ausente, vacío, de solo espacios o de más de 500, y SIN"
          + " consultar nada: sobre un identificador inexistente responde 400 y no 404")
  void exigeMotivo() throws Exception {
    mvc.perform(baja(vacio, null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(baja(vacio, ""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(baja(vacio, "     "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("El motivo de la eliminación es obligatorio."));
    mvc.perform(baja(vacio, "x".repeat(501)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));

    // El de 500 exactos se admite: el tope es inclusivo.
    mvc.perform(baja(conHistorial, "y".repeat(500))).andExpect(status().isNoContent());

    // Y aquí está la prueba de que el motivo se valida ANTES de consultar: sobre
    // un equipo que no existe, un motivo ausente responde 400 y no 404.
    mvc.perform(baja(UUID.randomUUID(), null)).andExpect(status().isBadRequest());

    // Un cuerpo sin motivo no eliminó nada.
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at IS NULL FROM teams WHERE id = ?", Boolean.class, vacio))
        .isTrue();
  }

  @Test
  @DisplayName("`CA-SP-774` — 404 al inexistente y 409 al ya eliminado, distinguiéndolos")
  void distingueInexistenteDeYaEliminado() throws Exception {
    mvc.perform(baja(UUID.randomUUID(), MOTIVO))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No existe un equipo con ese identificador."));

    mvc.perform(baja(yaEliminado, MOTIVO))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("El equipo ya está eliminado."))
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    // Eliminar dos veces el mismo: la segunda es el 409, no un 204 silencioso.
    mvc.perform(baja(vacio, MOTIVO)).andExpect(status().isNoContent());
    mvc.perform(baja(vacio, MOTIVO)).andExpect(status().isConflict());
    assertThat(filasDeBaja(vacio)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-SP-775` — audit_deletion_log recibe la fila LOGICAL con el motivo, el actor y la"
          + " instantánea con los identificadores de QUIENES PASARON, sin deleted_at dentro")
  void auditaLaBajaConLaInstantanea() throws Exception {
    UUID actor = UUID.randomUUID();

    mvc.perform(
            post("/api/v1/teams/" + conHistorial + "/deletion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + MOTIVO + "\"}")
                .with(con(actor, "teams:delete")))
        .andExpect(status().isNoContent());

    Map<String, Object> registro =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, deletion_type, reason, snapshot::text AS snapshot"
                + " FROM audit_deletion_log WHERE module = 'SP' AND entity = 'teams'"
                + " AND entity_id = ?",
            conHistorial);
    assertThat(registro.get("actor")).isEqualTo(actor.toString());
    assertThat(registro.get("deletion_type")).isEqualTo("LOGICAL");
    assertThat(registro.get("reason")).isEqualTo(MOTIVO);

    String instantanea = (String) registro.get("snapshot");
    assertThat(instantanea)
        .contains("\"name\": \"Equipo Con Historial\"")
        .contains("\"status\": \"ACTIVO\"")
        // Los dos que pasaron por el equipo, aunque ninguno siguiera dentro: un
        // registro que dice «tuve gente» sin decir quién no reconstruye nada.
        .contains(pasoPorAqui.toString())
        .contains(tambienPaso.toString())
        // La foto es de ANTES de la baja.
        .doesNotContain("deleted_at");

    // Y el equipo vacío la trae vacía, no ausente.
    mvc.perform(baja(vacio, MOTIVO)).andExpect(status().isNoContent());
    assertThat(
            (String)
                jdbc.queryForMap(
                        "SELECT snapshot::text AS snapshot FROM audit_deletion_log"
                            + " WHERE entity = 'teams' AND entity_id = ?",
                        vacio)
                    .get("snapshot"))
        .contains("\"member_ids\": []");
  }

  @Test
  @DisplayName(
      "`CA-SP-776` — el eliminado sale del listado salvo includeDeleted, su detalle lo devuelve con"
          + " deletedAt y deletionReason, y su nombre se reutiliza sin heredar nada")
  void desapareceDelListadoYLiberaElNombre() throws Exception {
    mvc.perform(baja(conHistorial, MOTIVO)).andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/teams").param("q", "Con Historial").with(con("teams:list")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)));

    mvc.perform(
            get("/api/v1/teams")
                .param("q", "Con Historial")
                .param("includeDeleted", "true")
                .with(con("teams:list")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].deletedAt").exists())
        // Un eliminado dice cero miembros vigentes, que es lo que era antes de
        // poder eliminarse.
        .andExpect(jsonPath("$.content[0].memberCount").value(0));

    mvc.perform(get("/api/v1/teams/" + conHistorial).with(con("teams:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedAt").exists())
        .andExpect(jsonPath("$.deletionReason").value(MOTIVO))
        .andExpect(jsonPath("$.members", hasSize(0)));

    // El nombre quedó libre, y el equipo nuevo NO hereda nada: ni identificador,
    // ni miembros, ni el historial de las dos pertenencias cerradas.
    String nuevo =
        com.jayway.jsonpath.JsonPath.read(
            mvc.perform(
                    post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Equipo Con Historial\"}")
                        .with(con("teams:create")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberCount").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    assertThat(nuevo).isNotEqualTo(conHistorial.toString());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM team_members WHERE team_id = CAST(? AS uuid)",
                Integer.class,
                nuevo))
        .isZero();
    // Y el historial del viejo sigue colgando del viejo.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM team_members WHERE team_id = ?", Integer.class, conHistorial))
        .isEqualTo(2);
  }

  @Test
  @DisplayName(
      "`CA-SP-777` (mitad del permiso) — sin teams:delete responde 403 aunque el actor porte"
          + " teams:change-status y teams:update")
  void losOtrosPermisosNoHabilitan() throws Exception {
    mvc.perform(
            post("/api/v1/teams/" + vacio + "/deletion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + MOTIVO + "\"}")
                .with(con("teams:change-status", "teams:update", "teams:read", "teams:list")))
        .andExpect(status().isForbidden());

    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at IS NULL FROM teams WHERE id = ?", Boolean.class, vacio))
        .isTrue();
    assertThat(filasDeBaja(vacio)).isZero();
  }

  private MockHttpServletRequestBuilder baja(UUID id, String motivo) {
    MockHttpServletRequestBuilder peticion =
        post("/api/v1/teams/" + id + "/deletion")
            .contentType(MediaType.APPLICATION_JSON)
            .with(con("teams:delete"));
    return motivo == null
        ? peticion.content("{}")
        : peticion.content(
            "{\"reason\":\"" + motivo.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
  }

  private int filasDeBaja(UUID id) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_deletion_log WHERE entity = 'teams' AND entity_id = ?",
        Integer.class,
        id);
  }
}
