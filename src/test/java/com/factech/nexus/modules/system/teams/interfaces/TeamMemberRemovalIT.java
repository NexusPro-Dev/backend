package com.factech.nexus.modules.system.teams.interfaces;

import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.con;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.eliminar;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.equipo;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.personaConRol;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.pertenencia;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
 * El retiro de miembros (`RF-SP-070` · `T-08`): `CA-SP-789` a `CA-SP-794` y `CA-SP-797`.
 *
 * <p><b>La prueba que cierra el círculo del submódulo es `CA-SP-791`</b>: vaciar un equipo
 * suspendido y comprobar que después `RF-SP-068` ya <b>no</b> responde `409`. Es el recorrido
 * entero de `RN-SP-054` —no se elimina un equipo con gente dentro, y esta es una de las dos
 * salidas— y la única que ejercita tres requerimientos seguidos.
 *
 * <p>`CA-SP-795` y `CA-SP-796` —`RN-SP-055`, la pertenencia que sigue al rol— viven en {@code
 * TeamMembershipRetirementIT}, porque lo que prueban no es esta operación sino que el sistema saca
 * a alguien <b>sin</b> pasar por ella.
 */
@AutoConfigureMockMvc
class TeamMemberRemovalIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private static final String[] GENTE = {
    "retiroequipo1", "retiroequipo2", "retiroequipo3", "retiroequipo4"
  };
  private static final String MOTIVO = "Sale de la estructura comercial.";

  private UUID norte;
  private UUID suspendido;
  private UUID eliminado;

  private UUID unoDelNorte;
  private UUID otroDelNorte;
  private UUID elDelSuspendido;
  private UUID sinEquipo;

  @BeforeEach
  void sembrar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, GENTE);

    norte = equipo(jdbc, "Equipo Norte Retiro");
    suspendido = equipo(jdbc, "Equipo Suspendido Retiro", "INACTIVO", null);
    eliminado = equipo(jdbc, "Equipo Disuelto Retiro");
    eliminar(jdbc, eliminado);

    unoDelNorte = personaConRol(jdbc, GENTE[0], "MANAGER");
    otroDelNorte = personaConRol(jdbc, GENTE[1], "MANAGER");
    elDelSuspendido = personaConRol(jdbc, GENTE[2], "MANAGER");
    sinEquipo = personaConRol(jdbc, GENTE[3], "MANAGER");

    pertenencia(jdbc, norte, unoDelNorte);
    pertenencia(jdbc, norte, otroDelNorte);
    pertenencia(jdbc, suspendido, elDelSuspendido);
  }

  @AfterEach
  void limpiar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, GENTE);
  }

  @Test
  @DisplayName(
      "`CA-SP-789` — retira uno o varios con 200, devuelve el detalle sin ellos, y la fila queda"
          + " CERRADA y no borrada")
  void retiraYConservaLaFila() throws Exception {
    mvc.perform(retirar(norte, MOTIVO, unoDelNorte))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberCount").value(1))
        .andExpect(jsonPath("$.members", hasSize(1)))
        .andExpect(jsonPath("$.members[0].id").value(otroDelNorte.toString()));

    // La fila sigue existiendo, con su fecha de fin: es historial.
    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT ended_at, started_at FROM team_members WHERE team_id = ? AND user_id = ?",
            norte,
            unoDelNorte);
    assertThat(fila.get("ended_at")).isNotNull();
    assertThat(fila.get("started_at")).isNotNull();
    assertThat(vigentesDe(norte)).isEqualTo(1);

    // Y varios a la vez: el equipo queda vacío.
    mvc.perform(retirar(norte, MOTIVO, otroDelNorte))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberCount").value(0))
        .andExpect(jsonPath("$.members", hasSize(0)));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM team_members WHERE team_id = ?", Integer.class, norte))
        .isEqualTo(2);
  }

  @Test
  @DisplayName(
      "`CA-SP-790` — 422 y la operación ENTERA rechazada si alguien no pertenece hoy a este equipo,"
          + " sin distinguir «no tiene equipo» de «está en otro»")
  void rechazaAQuienNoPertenece() throws Exception {
    mvc.perform(retirar(norte, MOTIVO, unoDelNorte, sinEquipo, elDelSuspendido))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail").value("Estas personas no pertenecen hoy a este equipo."))
        .andExpect(jsonPath("$.errors", hasSize(2)))
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    // Nadie salió: ni el que sí pertenecía.
    assertThat(vigentesDe(norte)).isEqualTo(2);
    assertThat(vigentesDe(suspendido)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-SP-791` — se retira de un equipo INACTIVO, y tras vaciarlo RF-SP-068 ya NO responde 409:"
          + " el recorrido entero de RN-SP-054")
  void vaciarUnSuspendidoPermiteEliminarlo() throws Exception {
    // Con gente dentro, la baja se rechaza.
    mvc.perform(baja(suspendido)).andExpect(status().isConflict());

    // Un equipo suspendido SUELTA aunque no reciba: si no, no podría vaciarse
    // nunca y por tanto no podría eliminarse.
    mvc.perform(retirar(suspendido, MOTIVO, elDelSuspendido))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.memberCount").value(0));

    // Y ahora sí.
    mvc.perform(baja(suspendido)).andExpect(status().isNoContent());

    // El eliminado responde 404 a un retiro posterior.
    mvc.perform(retirar(eliminado, MOTIVO, unoDelNorte)).andExpect(status().isNotFound());
    mvc.perform(retirar(UUID.randomUUID(), MOTIVO, unoDelNorte)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "`CA-SP-792` — 400 con la lista vacía, un identificador mal formado, más de 100, el motivo"
          + " ausente o largo y un cuerpo con campos no admitidos, sin escribir nada")
  void rechazaLoMalFormado() throws Exception {
    mvc.perform(cuerpo(norte, "{\"memberIds\":[],\"reason\":\"" + MOTIVO + "\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(cuerpo(norte, "{\"memberIds\":[\"no-es-uuid\"],\"reason\":\"" + MOTIVO + "\"}"))
        .andExpect(status().isBadRequest());

    String cientoUno =
        IntStream.range(0, 101)
            .mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
            .collect(Collectors.joining(","));
    mvc.perform(cuerpo(norte, "{\"memberIds\":[" + cientoUno + "],\"reason\":\"" + MOTIVO + "\"}"))
        .andExpect(status().isBadRequest());

    mvc.perform(cuerpo(norte, "{\"memberIds\":[\"" + unoDelNorte + "\"]}"))
        .andExpect(status().isBadRequest());
    mvc.perform(cuerpo(norte, "{\"memberIds\":[\"" + unoDelNorte + "\"],\"reason\":\"  \"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            cuerpo(
                norte,
                "{\"memberIds\":[\"" + unoDelNorte + "\"],\"reason\":\"" + "x".repeat(501) + "\"}"))
        .andExpect(status().isBadRequest());

    // `VAL-006`: ni la fecha de fin ni el equipo se declaran en el cuerpo.
    mvc.perform(
            cuerpo(
                norte,
                "{\"memberIds\":[\""
                    + unoDelNorte
                    + "\"],\"reason\":\""
                    + MOTIVO
                    + "\",\"endedAt\":\"2026-01-01T00:00:00Z\"}"))
        .andExpect(status().isBadRequest());

    assertThat(vigentesDe(norte)).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "`CA-SP-793` — una fila UPDATE por cierre, con el actor, el motivo y un mismo identificador"
          + " de correlación para toda la petición")
  void auditaElRetiro() throws Exception {
    UUID correlacion = UUID.randomUUID();
    UUID actor = UUID.randomUUID();

    mvc.perform(
            post("/api/v1/teams/" + norte + "/members/removals")
                .header("X-Correlation-Id", correlacion.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDe(MOTIVO, unoDelNorte, otroDelNorte))
                .with(con(actor, "teams:remove-members")))
        .andExpect(status().isOk());

    List<Map<String, Object>> filas =
        jdbc.queryForList(
            "SELECT action, actor_id::text AS actor, changes::text AS cambios"
                + " FROM audit_change_log WHERE entity = 'team_members' AND correlation_id = ?",
            correlacion);
    assertThat(filas).hasSize(2);
    assertThat(filas)
        .allSatisfy(
            fila -> {
              assertThat(fila.get("action")).isEqualTo("UPDATE");
              assertThat(fila.get("actor")).isEqualTo(actor.toString());
              assertThat((String) fila.get("cambios")).contains(MOTIVO).contains("\"ended\": true");
            });
  }

  @Test
  @DisplayName(
      "`CA-SP-794` — el retiro no toca user_roles, user_supervisors ni el estado de las personas")
  void noTocaNadaDeLaPersona() throws Exception {
    int rolesAntes = jdbc.queryForObject("SELECT count(*) FROM user_roles", Integer.class);
    int superioresAntes =
        jdbc.queryForObject("SELECT count(*) FROM user_supervisors", Integer.class);

    mvc.perform(retirar(norte, MOTIVO, unoDelNorte)).andExpect(status().isOk());

    assertThat(jdbc.queryForObject("SELECT count(*) FROM user_roles", Integer.class))
        .isEqualTo(rolesAntes);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM user_supervisors", Integer.class))
        .isEqualTo(superioresAntes);
    assertThat(
            jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, unoDelNorte))
        .isEqualTo("ACTIVO");
    // Sigue siendo manager: salir de un equipo no degrada a nadie.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_roles ur JOIN roles r ON r.id = ur.role_id"
                    + " WHERE ur.user_id = ? AND r.code = 'MANAGER'",
                Integer.class,
                unoDelNorte))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-SP-797` — sin teams:remove-members responde 403 aunque el actor porte"
          + " teams:assign-members")
  void asignarNoHabilitaRetirar() throws Exception {
    mvc.perform(
            post("/api/v1/teams/" + norte + "/members/removals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDe(MOTIVO, unoDelNorte))
                .with(con("teams:assign-members", "teams:update", "teams:read")))
        .andExpect(status().isForbidden());

    assertThat(vigentesDe(norte)).isEqualTo(2);
  }

  private MockHttpServletRequestBuilder retirar(UUID equipo, String motivo, UUID... personas) {
    return cuerpo(equipo, cuerpoDe(motivo, personas));
  }

  private MockHttpServletRequestBuilder cuerpo(UUID equipo, String json) {
    return post("/api/v1/teams/" + equipo + "/members/removals")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .with(con("teams:remove-members"));
  }

  private MockHttpServletRequestBuilder baja(UUID equipo) {
    return post("/api/v1/teams/" + equipo + "/deletion")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"Se disuelve tras vaciarlo.\"}")
        .with(con("teams:delete"));
  }

  private static String cuerpoDe(String motivo, UUID... personas) {
    String lista =
        java.util.Arrays.stream(personas)
            .map(persona -> "\"" + persona + "\"")
            .collect(Collectors.joining(","));
    return "{\"memberIds\":[" + lista + "],\"reason\":\"" + motivo + "\"}";
  }

  private int vigentesDe(UUID equipo) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM team_members WHERE team_id = ? AND ended_at IS NULL",
        Integer.class,
        equipo);
  }
}
