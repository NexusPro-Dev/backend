package com.factech.nexus.modules.system.teams.interfaces;

import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.con;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.eliminar;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.equipo;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.personaConRol;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.pertenencia;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * La asignación del director de un equipo (`RF-SP-069` · `T-17`, enmendada el 09-10-2026):
 * `CA-SP-779` a `CA-SP-781`, `CA-SP-783` a `CA-SP-788` y `CA-SP-980` a `CA-SP-983`.
 *
 * <p><b>Desde el 09-10-2026 un equipo es la oficina de UN director</b> (`RN-SP-051`, `RN-SP-052`):
 * entra quien tiene el rango de director, una persona por petición, y un equipo con otro director
 * vigente responde `409`. El fixture tiene lo que la regla distingue: dos equipos activos y uno
 * suspendido; un director sin equipo, otro que viene del segundo equipo, un tercero desactivado,
 * otro más, un manager, un agente, un cliente, alguien sin rol y un director eliminado.
 *
 * <p>`CA-SP-982` se verifica además <b>aislada y sin Spring</b> en {@code TeamMembershipRulesTest}.
 * La prueba del lote de cien (`T-10`) se retiró: con una persona por petición no hay lote.
 */
@AutoConfigureMockMvc
class TeamMembersIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private static final String[] GENTE = {
    "miembroequipo1",
    "miembroequipo2",
    "miembroequipo3",
    "miembroequipo4",
    "miembroequipo5",
    "miembroequipo6",
    "miembroequipo7",
    "miembroequipo8",
    "miembroequipo9"
  };
  private static final String MOTIVO = "Reorganizacion de la region norte.";
  private static final String NO_ES_DIRECTOR =
      "Solo pueden pertenecer a un equipo quienes tienen el rango de director.";
  private static final String EQUIPO_CON_DIRECTOR =
      "El equipo ya tiene un director vigente. Retírelo antes de asignar otro.";

  private UUID norte;
  private UUID sur;
  private UUID suspendido;
  private UUID eliminado;

  private UUID directorLibre;
  private UUID directorDelSur;
  private UUID directorDesactivado;
  private UUID otroDirector;
  private UUID manager;
  private UUID agente;
  private UUID cliente;
  private UUID sinRol;
  private UUID personaEliminada;

  @BeforeEach
  void sembrar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, GENTE);

    norte = equipo(jdbc, "Equipo Norte Miembros");
    sur = equipo(jdbc, "Equipo Sur Miembros");
    suspendido = equipo(jdbc, "Equipo Suspendido Miembros", "INACTIVO", null);
    eliminado = equipo(jdbc, "Equipo Disuelto Miembros");
    eliminar(jdbc, eliminado);

    directorLibre = personaConRol(jdbc, GENTE[0], "DIRECTOR");
    directorDelSur = personaConRol(jdbc, GENTE[1], "DIRECTOR");
    directorDesactivado = personaConRol(jdbc, GENTE[2], "DIRECTOR");
    manager = personaConRol(jdbc, GENTE[3], "MANAGER");
    agente = personaConRol(jdbc, GENTE[4], "AGENTE");
    cliente = personaConRol(jdbc, GENTE[5], "CLIENTE");
    sinRol = TeamTestSupport.persona(jdbc, GENTE[6]);
    personaEliminada = personaConRol(jdbc, GENTE[7], "DIRECTOR");
    otroDirector = personaConRol(jdbc, GENTE[8], "DIRECTOR");

    TeamTestSupport.desactivarPersona(jdbc, directorDesactivado);
    TeamTestSupport.eliminarPersona(jdbc, personaEliminada);
    pertenencia(jdbc, sur, directorDelSur);
  }

  @AfterEach
  void limpiar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, GENTE);
  }

  @Test
  @DisplayName(
      "`CA-SP-980` — asigna UN director a un equipo activo sin director con 200: el detalle lo trae"
          + " como único miembro vigente y memberCount en uno")
  void asignaUnDirector() throws Exception {
    mvc.perform(asignar(norte, MOTIVO, directorLibre))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(norte.toString()))
        .andExpect(jsonPath("$.memberCount").value(1))
        .andExpect(jsonPath("$.members", hasSize(1)))
        .andExpect(jsonPath("$.members[0].id").value(directorLibre.toString()))
        .andExpect(jsonPath("$.members[0].joinedAt").exists());

    assertThat(vigentesDe(norte)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-SP-779` — el director que venía de otro equipo queda con la anterior CERRADA y una nueva"
          + " abierta: el origen deja de contarlo y conserva la fila cerrada")
  void mueveDeEquipo() throws Exception {
    mvc.perform(asignar(norte, MOTIVO, directorDelSur))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberCount").value(1));

    assertThat(vigentesDe(norte)).isEqualTo(1);
    assertThat(vigentesDe(sur)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM team_members WHERE team_id = ? AND user_id = ?"
                    + " AND ended_at IS NOT NULL",
                Integer.class,
                sur,
                directorDelSur))
        .isEqualTo(1);

    mvc.perform(get("/api/v1/teams").param("q", "Sur Miembros").with(con("teams:list")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].memberCount").value(0));
  }

  @Test
  @DisplayName(
      "`CA-SP-780` — pedir al director que YA es el de este equipo conserva su joinedAt, no se"
          + " audita y la respuesta es 200")
  void elQueYaEstaNoSeToca() throws Exception {
    mvc.perform(asignar(norte, MOTIVO, directorLibre)).andExpect(status().isOk());
    String entrada =
        jdbc.queryForObject(
            "SELECT started_at::text FROM team_members WHERE team_id = ? AND user_id = ?",
            String.class,
            norte,
            directorLibre);
    int filasAntes = filasDeAuditoria(directorLibre);

    mvc.perform(asignar(norte, MOTIVO, directorLibre))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberCount").value(1));

    assertThat(
            jdbc.queryForObject(
                "SELECT started_at::text FROM team_members WHERE team_id = ? AND user_id = ?"
                    + " AND ended_at IS NULL",
                String.class,
                norte,
                directorLibre))
        .isEqualTo(entrada);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM team_members WHERE team_id = ? AND user_id = ?",
                Integer.class,
                norte,
                directorLibre))
        .isEqualTo(1);
    assertThat(filasDeAuditoria(directorLibre)).isEqualTo(filasAntes);
  }

  @Test
  @DisplayName(
      "`CA-SP-981` — 409 si el equipo tiene OTRO director vigente, sin escribir nada —tampoco cierra"
          + " la pertenencia de la persona pedida—; tras retirar al actual, la misma petición pasa")
  void elEquipoOcupadoRechaza() throws Exception {
    mvc.perform(asignar(norte, MOTIVO, directorLibre)).andExpect(status().isOk());

    mvc.perform(asignar(norte, MOTIVO, directorDelSur))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(EQUIPO_CON_DIRECTOR))
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-052"));

    // Nada cambió: el norte sigue con el suyo y el del sur sigue en el sur.
    assertThat(vigenteDe(norte)).isEqualTo(directorLibre);
    assertThat(vigenteDe(sur)).isEqualTo(directorDelSur);

    mvc.perform(
            post("/api/v1/teams/" + norte + "/members/removals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDe(MOTIVO, directorLibre))
                .with(con("teams:remove-members")))
        .andExpect(status().isOk());

    mvc.perform(asignar(norte, MOTIVO, directorDelSur))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members[0].id").value(directorDelSur.toString()));
    assertThat(vigentesDe(norte)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-SP-781` — 422 si la persona no existe o está eliminada, informándola; nada queda"
          + " asignado")
  void rechazaSiNoExiste() throws Exception {
    UUID fantasma = UUID.randomUUID();

    for (UUID persona : List.of(personaEliminada, fantasma)) {
      mvc.perform(asignar(norte, MOTIVO, persona))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.detail").value("Una o más personas no existen."))
          .andExpect(jsonPath("$.errors", hasSize(1)))
          .andExpect(
              jsonPath("$.errors[0].message").value("La persona '" + persona + "' no existe."));
    }

    assertThat(vigentesDe(norte)).isZero();
  }

  @Test
  @DisplayName(
      "`CA-SP-982` — 422 al manager, al agente, al cliente y a quien no tiene rol comercial,"
          + " citando RN-SP-051 e informándolo")
  void rechazaAQuienNoEsDirector() throws Exception {
    for (UUID persona : List.of(manager, agente, cliente, sinRol)) {
      mvc.perform(asignar(norte, MOTIVO, persona))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.detail").value(NO_ES_DIRECTOR))
          .andExpect(jsonPath("$.errors", hasSize(1)))
          .andExpect(jsonPath("$.errors[0].code").value("RN-SP-051"));
    }

    assertThat(vigentesDe(norte)).isZero();
  }

  @Test
  @DisplayName(
      "`CA-SP-982` — el manager pedido sobre un equipo OCUPADO recibe el 422 de quien nunca podrá"
          + " entrar, no el 409 del sitio tomado")
  void elRangoSeInformaAntesQueElEquipoOcupado() throws Exception {
    mvc.perform(asignar(norte, MOTIVO, directorLibre)).andExpect(status().isOk());

    mvc.perform(asignar(norte, MOTIVO, manager))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-051"));
  }

  @Test
  @DisplayName(
      "`CA-SP-983` — 400 con más de una persona distinta, sin escribir nada; el mismo identificador"
          + " repetido cuenta como uno y pasa")
  void unaPersonaPorPeticion() throws Exception {
    mvc.perform(asignar(norte, MOTIVO, directorLibre, otroDirector))
        .andExpect(status().isBadRequest());
    assertThat(vigentesDe(norte)).isZero();

    mvc.perform(asignar(norte, MOTIVO, directorLibre, directorLibre))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberCount").value(1));
  }

  @Test
  @DisplayName(
      "`CA-SP-783` — 409 al equipo INACTIVO —la restricción es del que RECIBE— y 404 al inexistente"
          + " o eliminado")
  void elEquipoTieneQueEstarActivo() throws Exception {
    mvc.perform(asignar(suspendido, MOTIVO, directorLibre))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "El equipo está inactivo y no admite miembros nuevos. Actívelo antes de"
                        + " asignar."));

    mvc.perform(asignar(eliminado, MOTIVO, directorLibre)).andExpect(status().isNotFound());
    mvc.perform(asignar(UUID.randomUUID(), MOTIVO, directorLibre)).andExpect(status().isNotFound());

    assertThat(vigentesDe(suspendido)).isZero();
  }

  @Test
  @DisplayName("`CA-SP-784` — un director DESACTIVADO entra igual: sigue siendo director")
  void elDesactivadoEntra() throws Exception {
    mvc.perform(asignar(norte, MOTIVO, directorDesactivado))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberCount").value(1))
        .andExpect(jsonPath("$.members[0].status").value("INACTIVO"));
  }

  @Test
  @DisplayName(
      "`CA-SP-785` — 400 con la lista vacía, un identificador mal formado, más de una persona, el"
          + " motivo ausente o largo y un cuerpo con campos no admitidos, sin escribir nada")
  void rechazaLoMalFormado() throws Exception {
    mvc.perform(cuerpo(norte, "{\"memberIds\":[],\"reason\":\"" + MOTIVO + "\"}"))
        .andExpect(status().isBadRequest());

    mvc.perform(cuerpo(norte, "{\"memberIds\":[\"no-es-uuid\"],\"reason\":\"" + MOTIVO + "\"}"))
        .andExpect(status().isBadRequest());

    mvc.perform(asignar(norte, MOTIVO, directorLibre, directorDesactivado))
        .andExpect(status().isBadRequest());

    mvc.perform(cuerpo(norte, "{\"memberIds\":[\"" + directorLibre + "\"]}"))
        .andExpect(status().isBadRequest());
    mvc.perform(cuerpo(norte, "{\"memberIds\":[\"" + directorLibre + "\"],\"reason\":\"   \"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            cuerpo(
                norte,
                "{\"memberIds\":[\""
                    + directorLibre
                    + "\"],\"reason\":\""
                    + "x".repeat(501)
                    + "\"}"))
        .andExpect(status().isBadRequest());

    mvc.perform(
            cuerpo(
                norte,
                "{\"memberIds\":[\""
                    + directorLibre
                    + "\"],\"reason\":\""
                    + MOTIVO
                    + "\",\"startedAt\":\"2026-01-01T00:00:00Z\"}"))
        .andExpect(status().isBadRequest());

    assertThat(vigentesDe(norte)).isZero();
  }

  @Test
  @DisplayName(
      "`CA-SP-786` — una fila por la pertenencia abierta y otra por la cerrada, con el motivo y el"
          + " MISMO identificador de correlación")
  void auditaBajoUnaSolaCorrelacion() throws Exception {
    UUID correlacion = UUID.randomUUID();
    UUID actor = UUID.randomUUID();

    mvc.perform(
            post("/api/v1/teams/" + norte + "/members")
                .header("X-Correlation-Id", correlacion.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDe(MOTIVO, directorDelSur))
                .with(con(actor, "teams:assign-members")))
        .andExpect(status().isOk());

    List<Map<String, Object>> filas =
        jdbc.queryForList(
            "SELECT action, actor_id::text AS actor, changes::text AS cambios"
                + " FROM audit_change_log WHERE entity = 'team_members'"
                + " AND correlation_id = ? ORDER BY action",
            correlacion);

    // Una apertura en el norte y el cierre de la del sur.
    assertThat(filas).hasSize(2);
    assertThat(filas.stream().filter(f -> "CREATE".equals(f.get("action"))).count()).isEqualTo(1);
    assertThat(filas.stream().filter(f -> "UPDATE".equals(f.get("action"))).count()).isEqualTo(1);
    assertThat(filas).allSatisfy(fila -> assertThat(fila.get("actor")).isEqualTo(actor.toString()));
    assertThat(filas).allSatisfy(fila -> assertThat((String) fila.get("cambios")).contains(MOTIVO));
  }

  @Test
  @DisplayName("`CA-SP-787` — la operación no toca user_supervisors ni user_roles")
  void noTocaLaCadenaDeMandoNiLosRoles() throws Exception {
    int superioresAntes =
        jdbc.queryForObject("SELECT count(*) FROM user_supervisors", Integer.class);
    int rolesAntes = jdbc.queryForObject("SELECT count(*) FROM user_roles", Integer.class);

    mvc.perform(asignar(norte, MOTIVO, directorDelSur)).andExpect(status().isOk());

    assertThat(jdbc.queryForObject("SELECT count(*) FROM user_supervisors", Integer.class))
        .isEqualTo(superioresAntes);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM user_roles", Integer.class))
        .isEqualTo(rolesAntes);
  }

  @Test
  @DisplayName(
      "`CA-SP-788` — sin teams:assign-members responde 403 aunque el actor porte teams:update y"
          + " teams:remove-members")
  void losPermisosVecinosNoHabilitan() throws Exception {
    mvc.perform(
            post("/api/v1/teams/" + norte + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDe(MOTIVO, directorLibre))
                .with(con("teams:update", "teams:remove-members", "teams:read")))
        .andExpect(status().isForbidden());

    assertThat(vigentesDe(norte)).isZero();
  }

  private MockHttpServletRequestBuilder asignar(UUID equipo, String motivo, UUID... personas) {
    return cuerpo(equipo, cuerpoDe(motivo, personas));
  }

  private MockHttpServletRequestBuilder cuerpo(UUID equipo, String json) {
    return post("/api/v1/teams/" + equipo + "/members")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .with(con("teams:assign-members"));
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

  private UUID vigenteDe(UUID equipo) {
    return jdbc.queryForObject(
        "SELECT user_id FROM team_members WHERE team_id = ? AND ended_at IS NULL",
        UUID.class,
        equipo);
  }

  private int filasDeAuditoria(UUID persona) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_change_log WHERE entity = 'team_members' AND entity_id = ?",
        Integer.class,
        persona);
  }
}
