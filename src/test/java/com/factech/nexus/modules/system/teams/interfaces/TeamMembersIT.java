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
 * La asignación de miembros (`RF-SP-069` · `T-09`): `CA-SP-778` a `CA-SP-781` y `CA-SP-783` a
 * `CA-SP-788`.
 *
 * <p><b>Es la suite donde `memberCount` deja de decir cero.</b> El fixture tiene lo que la regla
 * distingue: dos equipos activos y uno suspendido; un manager sin equipo, otro que viene del
 * segundo equipo, un tercero desactivado, un director, un agente, un cliente, alguien sin ningún
 * rol y alguien eliminado.
 *
 * <p>`CA-SP-782` se verifica además <b>aislada y sin Spring</b> en {@code TeamMembershipRulesTest};
 * aquí se comprueba de punta a punta que el `422` los nombra a todos.
 */
@AutoConfigureMockMvc
class TeamMembersIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private org.hibernate.SessionFactory sessionFactory;

  private org.hibernate.stat.Statistics estadisticas;

  private static final String[] GENTE = {
    "miembroequipo1",
    "miembroequipo2",
    "miembroequipo3",
    "miembroequipo4",
    "miembroequipo5",
    "miembroequipo6",
    "miembroequipo7",
    "miembroequipo8"
  };
  private static final String MOTIVO = "Reorganizacion de la region norte.";
  private static final String LOTE = "loteequipo";

  private UUID norte;
  private UUID sur;
  private UUID suspendido;
  private UUID eliminado;

  private UUID managerLibre;
  private UUID managerDelSur;
  private UUID managerDesactivado;
  private UUID director;
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

    managerLibre = personaConRol(jdbc, GENTE[0], "MANAGER");
    managerDelSur = personaConRol(jdbc, GENTE[1], "MANAGER");
    managerDesactivado = personaConRol(jdbc, GENTE[2], "MANAGER");
    director = personaConRol(jdbc, GENTE[3], "DIRECTOR");
    agente = personaConRol(jdbc, GENTE[4], "AGENTE");
    cliente = personaConRol(jdbc, GENTE[5], "CLIENTE");
    sinRol = TeamTestSupport.persona(jdbc, GENTE[6]);
    personaEliminada = personaConRol(jdbc, GENTE[7], "MANAGER");

    TeamTestSupport.desactivarPersona(jdbc, managerDesactivado);
    TeamTestSupport.eliminarPersona(jdbc, personaEliminada);
    pertenencia(jdbc, sur, managerDelSur);

    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
  }

  @AfterEach
  void limpiar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, GENTE);
  }

  @Test
  @DisplayName(
      "`CA-SP-778` — asigna varios managers a la vez con 200 y devuelve el detalle con todos sus"
          + " miembros y memberCount acorde")
  void asignaVarios() throws Exception {
    mvc.perform(asignar(norte, MOTIVO, managerLibre, managerDesactivado))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(norte.toString()))
        .andExpect(jsonPath("$.memberCount").value(2))
        .andExpect(jsonPath("$.members", hasSize(2)))
        .andExpect(jsonPath("$.members[*].id").exists())
        .andExpect(jsonPath("$.members[*].joinedAt").exists());

    assertThat(vigentesDe(norte)).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "`CA-SP-779` — quien venía de otro equipo queda con la anterior CERRADA y una nueva abierta:"
          + " el origen deja de contarlo y conserva la fila cerrada")
  void mueveDeEquipo() throws Exception {
    mvc.perform(asignar(norte, MOTIVO, managerDelSur))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberCount").value(1));

    // En el destino, vigente; en el origen, cerrada y conservada.
    assertThat(vigentesDe(norte)).isEqualTo(1);
    assertThat(vigentesDe(sur)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM team_members WHERE team_id = ? AND user_id = ?"
                    + " AND ended_at IS NOT NULL",
                Integer.class,
                sur,
                managerDelSur))
        .isEqualTo(1);

    // Y el listado del origen lo refleja de inmediato.
    mvc.perform(get("/api/v1/teams").param("q", "Sur Miembros").with(con("teams:list")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].memberCount").value(0));
  }

  @Test
  @DisplayName(
      "`CA-SP-780` — quien YA pertenece a este equipo conserva su joinedAt, no se audita y la"
          + " respuesta es 200")
  void elQueYaEstaNoSeToca() throws Exception {
    mvc.perform(asignar(norte, MOTIVO, managerLibre)).andExpect(status().isOk());
    String entrada =
        jdbc.queryForObject(
            "SELECT started_at::text FROM team_members WHERE team_id = ? AND user_id = ?",
            String.class,
            norte,
            managerLibre);
    int filasAntes = filasDeAuditoria(managerLibre);

    // Segunda vez, junto a otra persona: la suya no se cierra ni se reabre.
    mvc.perform(asignar(norte, MOTIVO, managerLibre, managerDelSur))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberCount").value(2));

    assertThat(
            jdbc.queryForObject(
                "SELECT started_at::text FROM team_members WHERE team_id = ? AND user_id = ?"
                    + " AND ended_at IS NULL",
                String.class,
                norte,
                managerLibre))
        .isEqualTo(entrada);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM team_members WHERE team_id = ? AND user_id = ?",
                Integer.class,
                norte,
                managerLibre))
        .isEqualTo(1);
    assertThat(filasDeAuditoria(managerLibre)).isEqualTo(filasAntes);
  }

  @Test
  @DisplayName(
      "`CA-SP-781` — 422 y la operación ENTERA rechazada si alguien no existe o está eliminado,"
          + " informando cuáles; ninguna de las demás queda asignada")
  void rechazaSiAlguienNoExiste() throws Exception {
    UUID fantasma = UUID.randomUUID();

    mvc.perform(asignar(norte, MOTIVO, managerLibre, personaEliminada, fantasma))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail").value("Una o más personas no existen."))
        .andExpect(jsonPath("$.errors", hasSize(2)))
        .andExpect(
            jsonPath("$.errors[*].message")
                .value(
                    org.hamcrest.Matchers.hasItems(
                        "La persona '" + personaEliminada + "' no existe.",
                        "La persona '" + fantasma + "' no existe.")));

    // Nadie entró: ni el que sí podía.
    assertThat(vigentesDe(norte)).isZero();
  }

  @Test
  @DisplayName(
      "`CA-SP-782` — 422 si alguien no es de la cúspide —director, agente, cliente o sin rol—,"
          + " citando RN-SP-051 e informando cuáles")
  void rechazaAQuienNoEsCuspide() throws Exception {
    mvc.perform(asignar(norte, MOTIVO, managerLibre, director, agente, cliente, sinRol))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Solo pueden pertenecer a un equipo quienes portan el rol comercial de mayor"
                        + " rango."))
        .andExpect(jsonPath("$.errors", hasSize(4)))
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-051"));

    assertThat(vigentesDe(norte)).isZero();
  }

  @Test
  @DisplayName(
      "`CA-SP-783` — 409 al equipo INACTIVO —la restricción es del que RECIBE— y 404 al inexistente"
          + " o eliminado")
  void elEquipoTieneQueEstarActivo() throws Exception {
    mvc.perform(asignar(suspendido, MOTIVO, managerLibre))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "El equipo está inactivo y no admite miembros nuevos. Actívelo antes de"
                        + " asignar."));

    mvc.perform(asignar(eliminado, MOTIVO, managerLibre)).andExpect(status().isNotFound());
    mvc.perform(asignar(UUID.randomUUID(), MOTIVO, managerLibre)).andExpect(status().isNotFound());

    assertThat(vigentesDe(suspendido)).isZero();
  }

  @Test
  @DisplayName("`CA-SP-784` — un manager DESACTIVADO entra igual: sigue siendo manager")
  void elDesactivadoEntra() throws Exception {
    mvc.perform(asignar(norte, MOTIVO, managerDesactivado))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberCount").value(1))
        // Y el detalle publica su estado, que es lo que permite verlo sin abrir su ficha.
        .andExpect(jsonPath("$.members[0].status").value("INACTIVO"));
  }

  @Test
  @DisplayName(
      "`CA-SP-785` — 400 con la lista vacía, un identificador mal formado, más de 100, el motivo"
          + " ausente o largo y un cuerpo con campos no admitidos, sin escribir nada")
  void rechazaLoMalFormado() throws Exception {
    mvc.perform(cuerpo(norte, "{\"memberIds\":[],\"reason\":\"" + MOTIVO + "\"}"))
        .andExpect(status().isBadRequest());

    mvc.perform(cuerpo(norte, "{\"memberIds\":[\"no-es-uuid\"],\"reason\":\"" + MOTIVO + "\"}"))
        .andExpect(status().isBadRequest());

    String ciento_uno =
        IntStream.range(0, 101)
            .mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
            .collect(Collectors.joining(","));
    mvc.perform(cuerpo(norte, "{\"memberIds\":[" + ciento_uno + "],\"reason\":\"" + MOTIVO + "\"}"))
        .andExpect(status().isBadRequest());

    mvc.perform(cuerpo(norte, "{\"memberIds\":[\"" + managerLibre + "\"]}"))
        .andExpect(status().isBadRequest());
    mvc.perform(cuerpo(norte, "{\"memberIds\":[\"" + managerLibre + "\"],\"reason\":\"   \"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            cuerpo(
                norte,
                "{\"memberIds\":[\""
                    + managerLibre
                    + "\"],\"reason\":\""
                    + "x".repeat(501)
                    + "\"}"))
        .andExpect(status().isBadRequest());

    // `VAL-006`: ni la fecha de entrada ni el estado se declaran.
    mvc.perform(
            cuerpo(
                norte,
                "{\"memberIds\":[\""
                    + managerLibre
                    + "\"],\"reason\":\""
                    + MOTIVO
                    + "\",\"startedAt\":\"2026-01-01T00:00:00Z\"}"))
        .andExpect(status().isBadRequest());

    assertThat(vigentesDe(norte)).isZero();
  }

  @Test
  @DisplayName(
      "`CA-SP-786` — una fila por pertenencia abierta y otra por cada cerrada, con el motivo y el"
          + " MISMO identificador de correlación para toda la petición")
  void auditaElLoteBajoUnaSolaCorrelacion() throws Exception {
    UUID correlacion = UUID.randomUUID();
    UUID actor = UUID.randomUUID();

    mvc.perform(
            post("/api/v1/teams/" + norte + "/members")
                .header("X-Correlation-Id", correlacion.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDe(MOTIVO, managerLibre, managerDelSur))
                .with(con(actor, "teams:assign-members")))
        .andExpect(status().isOk());

    List<Map<String, Object>> filas =
        jdbc.queryForList(
            "SELECT action, actor_id::text AS actor, changes::text AS cambios"
                + " FROM audit_change_log WHERE entity = 'team_members'"
                + " AND correlation_id = ? ORDER BY action",
            correlacion);

    // Dos aperturas y un cierre: el que venía del sur deja su fila cerrada.
    assertThat(filas).hasSize(3);
    assertThat(filas.stream().filter(f -> "CREATE".equals(f.get("action"))).count()).isEqualTo(2);
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

    mvc.perform(asignar(norte, MOTIVO, managerLibre, managerDelSur)).andExpect(status().isOk());

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
                .content(cuerpoDe(MOTIVO, managerLibre))
                .with(con("teams:update", "teams:remove-members", "teams:read")))
        .andExpect(status().isForbidden());

    assertThat(vigentesDe(norte)).isZero();
  }

  @Test
  @DisplayName(
      "el número de CONSULTAS no crece con el tamaño del lote: asignar cien cuesta lo mismo que"
          + " asignar uno (`T-10`, el riesgo del N+1 del plan §10)")
  void lasConsultasNoCrecenConElLote() throws Exception {
    // Se cuentan CONSULTAS y no sentencias preparadas a propósito: las
    // inserciones sí crecen con el lote —una fila por pertenencia, y eso es el
    // trabajo—, mientras que la RESOLUCIÓN previa —el equipo, las personas, sus
    // roles, el catálogo y las pertenencias vigentes— tiene que costar lo mismo
    // con uno que con cien. Si alguien cambiara `roleIdsOfAll` por un bucle de
    // `roleIdsOf`, esta prueba es la única que se pondría roja.
    estadisticas.clear();
    mvc.perform(asignar(norte, MOTIVO, managerLibre)).andExpect(status().isOk());
    long conUno = estadisticas.getQueryExecutionCount();

    sembrarLote(100);
    try {
      List<UUID> lote =
          jdbc.queryForList(
              "SELECT id FROM users WHERE username LIKE '" + LOTE + "%' ORDER BY username",
              UUID.class);
      assertThat(lote).hasSize(100);

      // A un equipo PROPIO y vacío, para que el recuento diga exactamente el
      // tamaño del lote y no el lote más lo que el fixture ya había puesto.
      UUID delLote = TeamTestSupport.equipo(jdbc, "Equipo Del Lote");

      estadisticas.clear();
      mvc.perform(asignar(delLote, MOTIVO, lote.toArray(UUID[]::new)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.memberCount").value(100));
      long conCien = estadisticas.getQueryExecutionCount();

      assertThat(conCien).isEqualTo(conUno);
    } finally {
      borrarLote();
    }
  }

  /** Cien managers de una sentencia: sembrarlos uno a uno costaría más que la prueba. */
  private void sembrarLote(int cuantos) {
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash, status,
                           country_id)
        SELECT gen_random_uuid(), ? || i, ? || i || '@factech.co', 'Lote', 'De prueba', 'x',
               'ACTIVO', (SELECT id FROM countries WHERE code = 'COL')
          FROM generate_series(1, ?) AS i
        """,
        LOTE,
        LOTE,
        cuantos);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT u.id, r.id, r.role_type FROM users u, roles r"
            + " WHERE u.username LIKE ? AND r.code = 'MANAGER'",
        LOTE + "%");
  }

  private void borrarLote() {
    jdbc.update(
        "DELETE FROM team_members WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
        LOTE + "%");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
        LOTE + "%");
    jdbc.update("DELETE FROM users WHERE username LIKE ?", LOTE + "%");
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

  private int filasDeAuditoria(UUID persona) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_change_log WHERE entity = 'team_members' AND entity_id = ?",
        Integer.class,
        persona);
  }
}
