package com.factech.nexus.modules.system.teams.interfaces;

import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.equipo;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.personaConRol;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.pertenencia;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * `RN-SP-055`, la pertenencia que sigue al <b>rango de director</b> (`RF-SP-070` · `T-09` a `T-11`;
 * enmiendas del 09-10-2026: `RF-SP-029` · `T-18`, `RF-SP-030` · `T-28`, `RF-SP-031` · `T-19`):
 * `CA-SP-795`, `CA-SP-796` y `CA-SP-987` a `CA-SP-991`.
 *
 * <p><b>Lo que prueba esta suite no es una operación, sino que el sistema saca a alguien de su
 * equipo SIN que nadie se lo pida.</b> Quien deja de ser director —porque se le retira el rol,
 * porque una sustitución lo asciende o lo desciende, o porque se le elimina— sale en la <b>misma
 * transacción</b>. Sin eso, un equipo podría tener como oficina a quien ya no es director.
 *
 * <p><b>Las dos mitades del contrato se prueban juntas</b>: que la baja arrastra la pertenencia, y
 * que <b>un rechazo no la arrastra</b>. Lo segundo es lo que exige que el puerto sea {@code
 * MANDATORY} y no abra transacción propia.
 *
 * <p>Cada director tiene su propio equipo: desde `V99` un equipo tiene como mucho uno vigente.
 */
@AutoConfigureMockMvc
class TeamMembershipRetirementIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private static final String[] GENTE = {
    "rnsp055uno",
    "rnsp055dos",
    "rnsp055tres",
    "rnsp055cuatro",
    "rnsp055cinco",
    "rnsp055seis",
    "rnsp055siete",
    "rnsp055ocho",
    "rnsp055nueve"
  };
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000003";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";
  private static final String CLIENTE = "01a02a33-4c00-7008-9c4f-5e7ad1000008";

  private UUID degradado;
  private UUID eliminable;
  private UUID soloDirector;
  private UUID suspendible;
  private UUID ascendido;
  private UUID descendido;
  private UUID superiorDelDescendido;
  private UUID unManager;
  private UUID managerSinRed;
  private UUID equipoDelEliminable;

  @BeforeEach
  void sembrar() {
    TeamTestSupport.limpiar(jdbc);
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'rnsp055%')");
    TeamTestSupport.borrarPersonas(jdbc, GENTE);

    // Con DOS roles: al quitarle el de director conserva uno y `RN-SP-023` no se
    // interpone, que es lo que permite ver la cascada en verde.
    degradado = director(GENTE[0], "Equipo Degradado RN-SP-055");
    conceder(degradado, "CLIENTE");

    eliminable = personaConRol(jdbc, GENTE[1], "DIRECTOR");
    equipoDelEliminable = equipo(jdbc, "Equipo Eliminable RN-SP-055");
    pertenencia(jdbc, equipoDelEliminable, eliminable);

    // Con UN solo rol: retirárselo choca contra `RN-SP-023` y el retiro falla,
    // que es la otra mitad de `CA-SP-795`.
    soloDirector = director(GENTE[2], "Equipo Solo RN-SP-055");
    suspendible = director(GENTE[3], "Equipo Suspendible RN-SP-055");
    ascendido = director(GENTE[4], "Equipo Ascenso RN-SP-055");
    descendido = director(GENTE[5], "Equipo Descenso RN-SP-055");
    superiorDelDescendido = personaConRol(jdbc, GENTE[6], "DIRECTOR");
    unManager = personaConRol(jdbc, GENTE[7], "MANAGER");
    conceder(unManager, "CLIENTE");
    // Sin nadie a cargo: retirarle el rol no choca con `RN-SP-022`.
    managerSinRed = personaConRol(jdbc, GENTE[8], "MANAGER");
    conceder(managerSinRed, "CLIENTE");

    for (UUID persona :
        List.of(
            degradado,
            eliminable,
            soloDirector,
            suspendible,
            ascendido,
            descendido,
            superiorDelDescendido,
            unManager,
            managerSinRed)) {
      darElSuelo(jdbc, persona);
    }
    // `RN-SP-019`: todo vendedor que no es la cúspide tiene superior, y la
    // asignación de roles lo comprueba. Los directores cuelgan del manager.
    for (UUID director :
        List.of(degradado, eliminable, soloDirector, suspendible, ascendido, descendido)) {
      jdbc.update(
          "INSERT INTO user_supervisors (id, user_id, supervisor_id) VALUES (?, ?, ?)",
          TeamTestSupport.IDS.next(),
          director,
          unManager);
    }
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id) VALUES (?, ?, ?)",
        TeamTestSupport.IDS.next(),
        superiorDelDescendido,
        unManager);
  }

  @AfterEach
  void limpiar() {
    TeamTestSupport.limpiar(jdbc);
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'rnsp055%')");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'rnsp055%')");
    TeamTestSupport.borrarPersonas(jdbc, GENTE);
  }

  @Test
  @DisplayName(
      "`CA-SP-795` y `CA-SP-988` — retirar el rol de director CIERRA la pertenencia en la misma"
          + " transacción y con la misma correlación, sin borrar la fila")
  void retirarElRolCierraLaPertenencia() throws Exception {
    UUID correlacion = UUID.randomUUID();
    assertThat(vigente(degradado)).isTrue();

    mvc.perform(
            post("/api/v1/users/" + degradado + "/roles/revocations")
                .header("X-Correlation-Id", correlacion.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[\"" + DIRECTOR + "\"]}")
                .with(quienAdministraLaFuerzaComercial()))
        .andExpect(status().isOk());

    assertThat(vigente(degradado)).isFalse();
    assertThat(filasDe(degradado)).isEqualTo(1);
    assertThat(auditadasCon(degradado, correlacion)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-SP-795` (la otra mitad) — si el retiro del rol FALLA, la pertenencia no se cierra: la"
          + " persona sigue siendo director")
  void siElRetiroFallaLaPertenenciaSigueAbierta() throws Exception {
    mvc.perform(
            post("/api/v1/users/" + soloDirector + "/roles/revocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[\"" + DIRECTOR + "\"]}")
                .with(quienAdministraLaFuerzaComercial()))
        .andExpect(status().is4xxClientError());

    assertThat(vigente(soloDirector)).isTrue();
    assertThat(auditadas(soloDirector)).isZero();
  }

  @Test
  @DisplayName(
      "`CA-SP-989` — retirar a un director un rol que no le quita el rango no cierra su"
          + " pertenencia; retirar su rol a un manager no escribe nada en las pertenencias")
  void retirarOtroRolNoCierra() throws Exception {
    mvc.perform(
            post("/api/v1/users/" + degradado + "/roles/revocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[\"" + CLIENTE + "\"]}")
                .with(quienAdministraLaFuerzaComercial()))
        .andExpect(status().isOk());
    assertThat(vigente(degradado)).isTrue();
    assertThat(auditadas(degradado)).isZero();

    int filasAntes = jdbc.queryForObject("SELECT count(*) FROM team_members", Integer.class);
    mvc.perform(
            post("/api/v1/users/" + managerSinRed + "/roles/revocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[\"" + MANAGER + "\"]}")
                .with(quienAdministraLaFuerzaComercial()))
        .andExpect(status().isOk());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM team_members", Integer.class))
        .isEqualTo(filasAntes);
    assertThat(auditadas(managerSinRed)).isZero();
  }

  @Test
  @DisplayName(
      "`CA-SP-990` — el ASCENSO de un director a manager cierra su pertenencia con la correlación"
          + " de la sustitución, sin borrar la fila, y su equipo queda sin director")
  void elAscensoCierra() throws Exception {
    UUID correlacion = UUID.randomUUID();

    mvc.perform(
            post("/api/v1/users/" + ascendido + "/roles")
                .header("X-Correlation-Id", correlacion.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[\"" + MANAGER + "\"]}")
                .with(quienAdministraLaFuerzaComercial()))
        .andExpect(status().isOk());

    assertThat(vigente(ascendido)).isFalse();
    assertThat(filasDe(ascendido)).isEqualTo(1);
    assertThat(auditadasCon(ascendido, correlacion)).isEqualTo(1);
    assertThat(vigentesDelEquipoDe(ascendido)).isZero();
  }

  @Test
  @DisplayName(
      "`CA-SP-990` — el DESCENSO de un director a agente cierra su pertenencia; si la asignación"
          + " falla, sigue abierta")
  void elDescensoCierraYElFalloNo() throws Exception {
    // Sin superior el descenso se rechaza (`RN-SP-019`): la pertenencia sigue.
    mvc.perform(
            post("/api/v1/users/" + descendido + "/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[\"" + AGENTE + "\"]}")
                .with(quienAdministraLaFuerzaComercial()))
        .andExpect(status().is4xxClientError());
    assertThat(vigente(descendido)).isTrue();
    assertThat(auditadas(descendido)).isZero();

    mvc.perform(
            post("/api/v1/users/" + descendido + "/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"roleIds\":[\""
                        + AGENTE
                        + "\"],\"supervisorId\":\""
                        + superiorDelDescendido
                        + "\"}")
                .with(quienAdministraLaFuerzaComercial()))
        .andExpect(status().isOk());

    assertThat(vigente(descendido)).isFalse();
    assertThat(filasDe(descendido)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-SP-991` — asignar a un director un rol no vendedor, o el mismo DIRECTOR, no cierra su"
          + " pertenencia; ascender a un agente a director no le abre ninguna")
  void loQueNoCierraNiAbre() throws Exception {
    mvc.perform(
            post("/api/v1/users/" + suspendible + "/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[\"" + CLIENTE + "\"]}")
                .with(quienAdministraLaFuerzaComercial()))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/users/" + suspendible + "/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[\"" + DIRECTOR + "\"]}")
                .with(quienAdministraLaFuerzaComercial()))
        .andExpect(status().isOk());
    assertThat(vigente(suspendible)).isTrue();
    assertThat(auditadas(suspendible)).isZero();

    // El agente que sube a director: con el superior que exige el ascenso.
    mvc.perform(
            post("/api/v1/users/" + descendido + "/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"roleIds\":[\""
                        + AGENTE
                        + "\"],\"supervisorId\":\""
                        + superiorDelDescendido
                        + "\"}")
                .with(quienAdministraLaFuerzaComercial()))
        .andExpect(status().isOk());
    int filasAntes = filasDe(descendido);
    mvc.perform(
            post("/api/v1/users/" + descendido + "/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"roleIds\":[\"" + DIRECTOR + "\"],\"supervisorId\":\"" + unManager + "\"}")
                .with(quienAdministraLaFuerzaComercial()))
        .andExpect(status().isOk());
    assertThat(vigente(descendido)).isFalse();
    assertThat(filasDe(descendido)).isEqualTo(filasAntes);
  }

  @Test
  @DisplayName(
      "`CA-SP-796` y `CA-SP-987` — eliminar a un director cierra su pertenencia con la baja y su"
          + " equipo acepta otro director; cambiar su estado NO lo saca")
  void laBajaCierraYElEstadoNo() throws Exception {
    // Desactivar no saca a nadie: un director suspendido sigue en su equipo.
    mvc.perform(
            patch("/api/v1/users/" + suspendible + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVO\",\"reason\":\"Permiso sin sueldo.\"}")
                .with(actor("users:change-status")))
        .andExpect(status().isOk());
    assertThat(vigente(suspendible)).isTrue();

    mvc.perform(
            post("/api/v1/users/" + eliminable + "/deletion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Sale de la empresa.\"}")
                .with(actor("users:delete")))
        .andExpect(status().isNoContent());

    assertThat(vigente(eliminable)).isFalse();
    // La fila se conserva, cerrada en el acto de la baja: misma transacción, y
    // la marca la pone el puerto de `teams` con su reloj, de modo que coincide
    // con `deleted_at` al segundo y no al microsegundo.
    assertThat(
            jdbc.queryForObject(
                "SELECT abs(extract(epoch FROM tm.ended_at - u.deleted_at)) < 5"
                    + " FROM team_members tm JOIN users u ON u.id = tm.user_id"
                    + " WHERE tm.user_id = ?",
                Boolean.class,
                eliminable))
        .isTrue();

    // Y el equipo, sin director, acepta a otro (`RN-SP-052`).
    mvc.perform(
            post("/api/v1/teams/" + equipoDelEliminable + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"memberIds\":[\"" + superiorDelDescendido + "\"],\"reason\":\"Relevo.\"}")
                .with(actor("teams:assign-members")))
        .andExpect(status().isOk());
  }

  private UUID director(String usuario, String nombreDelEquipo) {
    UUID persona = personaConRol(jdbc, usuario, "DIRECTOR");
    pertenencia(jdbc, equipo(jdbc, nombreDelEquipo), persona);
    return persona;
  }

  private void conceder(UUID persona, String rol) {
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.code = ?",
        persona,
        rol);
  }

  private boolean vigente(UUID persona) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM team_members WHERE user_id = ? AND ended_at IS NULL)",
            Boolean.class,
            persona));
  }

  private int filasDe(UUID persona) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM team_members WHERE user_id = ?", Integer.class, persona);
  }

  private int vigentesDelEquipoDe(UUID persona) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM team_members WHERE ended_at IS NULL AND team_id IN"
            + " (SELECT team_id FROM team_members WHERE user_id = ?)",
        Integer.class,
        persona);
  }

  private int auditadas(UUID persona) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_change_log WHERE entity = 'team_members' AND entity_id = ?",
        Integer.class,
        persona);
  }

  private int auditadasCon(UUID persona, UUID correlacion) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_change_log WHERE entity = 'team_members'"
            + " AND entity_id = ? AND correlation_id = ?",
        Integer.class,
        persona,
        correlacion);
  }

  /**
   * Quien asigna o retira roles comerciales tiene que portar <b>los permisos que esos roles
   * conceden</b> (`RN-SEG-010`). <b>Se leen de la base</b>: así la prueba no envejece con la
   * siguiente migración que le añada uno a un rol.
   */
  private RequestPostProcessor quienAdministraLaFuerzaComercial() {
    List<String> permisos =
        jdbc.queryForList(
            "SELECT DISTINCT p.code FROM role_permissions rp"
                + " JOIN permissions p ON p.id = rp.permission_id"
                + " JOIN roles r ON r.id = rp.role_id"
                + " WHERE r.code IN ('MANAGER', 'DIRECTOR', 'AGENTE', 'CLIENTE')",
            String.class);
    List<String> todos = new java.util.ArrayList<>(permisos);
    todos.add("users:revoke-roles");
    todos.add("users:assign-roles");
    return actor(todos.toArray(String[]::new));
  }

  /** Un administrador cualquiera: lo que se prueba aquí no es la autorización. */
  private static RequestPostProcessor actor(String... permisos) {
    return user(UUID.randomUUID().toString())
        .authorities(
            java.util.Arrays.stream(permisos).map(p -> (GrantedAuthority) () -> p).toList());
  }
}
