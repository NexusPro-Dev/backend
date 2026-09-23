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
 * `RN-SP-055`, la pertenencia que sigue al rol (`RF-SP-070` · `T-09` a `T-11`): `CA-SP-795` y
 * `CA-SP-796`.
 *
 * <p><b>Lo que prueba esta suite no es una operación, sino que el sistema saca a alguien de su
 * equipo SIN que nadie se lo pida.</b> Quien deja de ser manager —porque se le retira el rol
 * comercial de mayor rango o porque se le elimina— sale en la <b>misma transacción</b>. Sin eso, un
 * equipo podría contener a quien ya no es de la cúspide y `RN-SP-051` se cumpliría al asignar y
 * dejaría de cumplirse después, sin que nadie lo notara.
 *
 * <p><b>Vive en un solo sitio a propósito.</b> Es una verificación que cruza tres requerimientos
 * —`RF-SP-029`, `RF-SP-031` y `RF-SP-070`— y repartirla entre las suites de cada uno la haría
 * ilegible: es el mismo criterio con el que `CA-SP-363` se quedó en una sola clase.
 *
 * <p><b>Las dos mitades del contrato se prueban juntas</b>: que la baja arrastra la pertenencia, y
 * que <b>un rechazo no la arrastra</b> —si el retiro del rol falla, la persona sigue siendo manager
 * y tiene que seguir en su equipo—. Lo segundo es lo que exige que el puerto sea {@code MANDATORY}
 * y no abra transacción propia.
 */
@AutoConfigureMockMvc
class TeamMembershipRetirementIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private static final String[] GENTE = {
    "rnsp055uno", "rnsp055dos", "rnsp055tres", "rnsp055cuatro"
  };
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000003";

  private UUID norte;
  private UUID degradado;
  private UUID eliminable;
  private UUID soloManager;
  private UUID suspendible;

  @BeforeEach
  void sembrar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, GENTE);

    norte = equipo(jdbc, "Equipo Norte RN-SP-055");

    // Con DOS roles: al quitarle el de manager conserva uno y `RN-SP-023` no se
    // interpone, que es lo que permite ver la cascada en verde.
    degradado = personaConRol(jdbc, GENTE[0], "MANAGER");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.code = 'CLIENTE'",
        degradado);

    eliminable = personaConRol(jdbc, GENTE[1], "MANAGER");
    // Con UN solo rol: retirárselo choca contra `RN-SP-023` y el retiro falla,
    // que es la otra mitad de `CA-SP-795`.
    soloManager = personaConRol(jdbc, GENTE[2], "MANAGER");
    suspendible = personaConRol(jdbc, GENTE[3], "MANAGER");

    pertenencia(jdbc, norte, degradado);
    pertenencia(jdbc, norte, eliminable);
    pertenencia(jdbc, norte, soloManager);
    pertenencia(jdbc, norte, suspendible);
  }

  @AfterEach
  void limpiar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, GENTE);
  }

  @Test
  @DisplayName(
      "`CA-SP-795` — retirar el rol de manager CIERRA la pertenencia en la misma transacción y con"
          + " la misma correlación")
  void retirarElRolCierraLaPertenencia() throws Exception {
    UUID correlacion = UUID.randomUUID();
    assertThat(vigente(degradado)).isTrue();

    mvc.perform(
            post("/api/v1/users/" + degradado + "/roles/revocations")
                .header("X-Correlation-Id", correlacion.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[\"" + MANAGER + "\"]}")
                .with(quienPuedeRetirarAlManager()))
        .andExpect(status().isOk());

    assertThat(vigente(degradado)).isFalse();
    // La fila no se borra: se cierra, como cualquier pertenencia.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM team_members WHERE user_id = ?", Integer.class, degradado))
        .isEqualTo(1);
    // Y bajo la MISMA correlación que el retiro del rol, de modo que la
    // reorganización se lea de una pieza.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE entity = 'team_members'"
                    + " AND entity_id = ? AND correlation_id = ?",
                Integer.class,
                degradado,
                correlacion))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-SP-795` (la otra mitad) — si el retiro del rol FALLA, la pertenencia no se cierra: la"
          + " persona sigue siendo manager")
  void siElRetiroFallaLaPertenenciaSigueAbierta() throws Exception {
    // `RN-SP-023`: nadie se queda sin ningún rol. El retiro se rechaza entero, y
    // con él tiene que revertirse el cierre — que es lo que el puerto MANDATORY
    // garantiza.
    mvc.perform(
            post("/api/v1/users/" + soloManager + "/roles/revocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[\"" + MANAGER + "\"]}")
                .with(quienPuedeRetirarAlManager()))
        .andExpect(status().is4xxClientError());

    assertThat(vigente(soloManager)).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE entity = 'team_members'"
                    + " AND entity_id = ?",
                Integer.class,
                soloManager))
        .isZero();
  }

  @Test
  @DisplayName(
      "`CA-SP-796` — eliminar a la persona cierra su pertenencia en la misma transacción; cambiar"
          + " su estado NO la saca del equipo")
  void laBajaCierraYElEstadoNo() throws Exception {
    // Desactivar no saca a nadie: un manager suspendido sigue en su equipo.
    mvc.perform(
            patch("/api/v1/users/" + suspendible + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVO\",\"reason\":\"Permiso sin sueldo.\"}")
                .with(actor("users:change-status")))
        .andExpect(status().isOk());

    assertThat(vigente(suspendible)).isTrue();
    assertThat(vigentesDe(norte)).isEqualTo(4);

    // Eliminar sí, y en la misma transacción.
    mvc.perform(
            post("/api/v1/users/" + eliminable + "/deletion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Sale de la empresa.\"}")
                .with(actor("users:delete")))
        .andExpect(status().isNoContent());

    assertThat(vigente(eliminable)).isFalse();
    assertThat(vigentesDe(norte)).isEqualTo(3);
    // El historial se conserva: quien ya no existe siguió estando.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM team_members WHERE user_id = ? AND ended_at IS NOT NULL",
                Integer.class,
                eliminable))
        .isEqualTo(1);
  }

  private boolean vigente(UUID persona) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM team_members WHERE user_id = ? AND ended_at IS NULL)",
            Boolean.class,
            persona));
  }

  private int vigentesDe(UUID equipo) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM team_members WHERE team_id = ? AND ended_at IS NULL",
        Integer.class,
        equipo);
  }

  /**
   * Quien retira el rol de manager tiene que portar <b>los permisos que ese rol concede</b>
   * (`RN-SEG-010`): quien no puede conceder un privilegio tampoco puede manipularlo. Desde `V31` el
   * rol comercial declara los de alcance propio, de modo que un actor con solo {@code
   * users:revoke-roles} recibiría `409` y esta suite estaría probando otra cosa.
   *
   * <p><b>Se leen de la base y no se escriben a mano</b>: así la prueba no envejece con la
   * siguiente migración que le añada uno al rol.
   */
  private RequestPostProcessor quienPuedeRetirarAlManager() {
    List<String> permisos =
        jdbc.queryForList(
            "SELECT p.code FROM role_permissions rp"
                + " JOIN permissions p ON p.id = rp.permission_id"
                + " JOIN roles r ON r.id = rp.role_id WHERE r.code = 'MANAGER'",
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
