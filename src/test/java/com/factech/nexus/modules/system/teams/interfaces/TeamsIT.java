package com.factech.nexus.modules.system.teams.interfaces;

import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.con;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.eliminar;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.equipo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * El alta de equipos (`RF-SP-063` · `T-09`): `CA-SP-731` a `CA-SP-735` y `CA-SP-739`. La carrera
 * (`CA-SP-736`) vive en {@link TeamConcurrencyIT} y el esquema (`CA-SP-737`) en {@link
 * TeamsSchemaIT}.
 *
 * <p>Lo que más importa aquí no es el camino feliz sino <b>lo que el equipo no tiene</b> —ni
 * código, ni estado que declarar, ni miembros en el cuerpo— y lo que sí tiene siempre: un nombre
 * único entre los no eliminados, sin distinguir mayúsculas ni acentos.
 */
@AutoConfigureMockMvc
class TeamsIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  @AfterEach
  void limpiar() {
    TeamTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-SP-731` — registra el equipo en la forma del detalle: ACTIVO, cero miembros, lista"
          + " vacía, descripción presente y nula, y createdAt igual a updatedAt")
  void altaEnLaFormaDelDetalle() throws Exception {
    mvc.perform(alta("{\"name\":\"  Equipo Norte  \"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", startsWith("/api/v1/teams/")))
        .andExpect(jsonPath("$.name").value("Equipo Norte"))
        .andExpect(jsonPath("$.description").value(nullValue()))
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.memberCount").value(0))
        .andExpect(jsonPath("$.members", hasSize(0)))
        .andExpect(jsonPath("$.deletedAt").doesNotExist())
        .andExpect(jsonPath("$.deletionReason").doesNotExist());

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT status, description, created_at = updated_at AS iguales, deleted_at"
                + " FROM teams WHERE name = 'Equipo Norte'");
    assertThat(fila.get("status")).isEqualTo("ACTIVO");
    assertThat(fila.get("description")).isNull();
    assertThat(fila.get("iguales")).isEqualTo(Boolean.TRUE);
    assertThat(fila.get("deleted_at")).isNull();
  }

  @Test
  @DisplayName(
      "`CA-SP-732` — 409 con un nombre que ya usa un equipo no eliminado, sin distinguir"
          + " mayúsculas ni acentos; el de uno eliminado SÍ se admite")
  void nombreUnicoEntreLosNoEliminados() throws Exception {
    equipo(jdbc, "Equipo Norte");

    mvc.perform(alta("{\"name\":\"equipo norte\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));

    mvc.perform(alta("{\"name\":\"Equipo Nórte\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));

    // Un eliminado NO compite por la unicidad: la baja libera el nombre
    // (`RN-SP-050`), porque no hay código que conservar.
    UUID viejo = equipo(jdbc, "Equipo Sur");
    eliminar(jdbc, viejo);
    mvc.perform(alta("{\"name\":\"Equipo Sur\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.memberCount").value(0));
  }

  @Test
  @DisplayName(
      "`CA-SP-733` — 400 con el nombre ausente y la descripción larga, JUNTOS; una descripción de"
          + " solo espacios se guarda nula")
  void validacionesJuntas() throws Exception {
    mvc.perform(alta("{\"description\":\"" + "x".repeat(501) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasSize(2)))
        .andExpect(jsonPath("$.errors[*].field").value(hasItems("name", "description")))
        .andExpect(jsonPath("$.errors[*].code").value(hasItems("VAL-001", "VAL-002")));

    mvc.perform(alta("{\"name\":\"   \"}")).andExpect(status().isBadRequest());
    mvc.perform(alta("{\"name\":\"" + "x".repeat(101) + "\"}")).andExpect(status().isBadRequest());

    mvc.perform(alta("{\"name\":\"Equipo Centro\",\"description\":\"   \"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.description").value(nullValue()));
    assertThat(
            jdbc.queryForObject(
                "SELECT description IS NULL FROM teams WHERE name = 'Equipo Centro'",
                Boolean.class))
        .isTrue();
  }

  @Test
  @DisplayName("`CA-SP-734` — 400 con un cuerpo que traiga status, members o code")
  void camposDesconocidos() throws Exception {
    mvc.perform(alta("{\"name\":\"Equipo A\",\"status\":\"INACTIVO\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(alta("{\"name\":\"Equipo B\",\"members\":[]}")).andExpect(status().isBadRequest());
    mvc.perform(alta("{\"name\":\"Equipo C\",\"code\":\"NORTE\"}"))
        .andExpect(status().isBadRequest());

    assertThat(jdbc.queryForObject("SELECT count(*) FROM teams", Integer.class)).isZero();
  }

  @Test
  @DisplayName(
      "`CA-SP-735` — deja una fila CREATE en audit_change_log con el actor, y NINGUNA en"
          + " audit_security_log: un equipo no concede permisos")
  void auditaLaCreacionYNoLaSeguridad() throws Exception {
    UUID actor = UUID.randomUUID();
    int seguridadAntes =
        jdbc.queryForObject("SELECT count(*) FROM audit_security_log", Integer.class);

    String id =
        com.jayway.jsonpath.JsonPath.read(
            mvc.perform(alta("{\"name\":\"Auditado\",\"description\":\"Con motivo.\"}", actor))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, action, changes::text AS changes FROM"
                + " audit_change_log WHERE module = 'SP' AND entity = 'teams' AND entity_id ="
                + " CAST(? AS uuid)",
            id);
    assertThat(fila.get("actor")).isEqualTo(actor.toString());
    assertThat(fila.get("action")).isEqualTo("CREATE");
    assertThat((String) fila.get("changes"))
        .contains("\"name\": \"Auditado\"")
        .contains("\"status\": \"ACTIVO\"");

    assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_security_log", Integer.class))
        .isEqualTo(seguridadAntes);
  }

  @Test
  @DisplayName(
      "`CA-SP-739` — sin teams:create responde 403 aunque el actor porte los otros siete teams:")
  void losOtrosSieteNoHabilitan() throws Exception {
    mvc.perform(
            post("/api/v1/teams")
                .with(
                    con(
                        "teams:list",
                        "teams:read",
                        "teams:update",
                        "teams:change-status",
                        "teams:delete",
                        "teams:assign-members",
                        "teams:remove-members"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Prohibido\"}"))
        .andExpect(status().isForbidden());

    assertThat(jdbc.queryForObject("SELECT count(*) FROM teams", Integer.class)).isZero();
  }

  private MockHttpServletRequestBuilder alta(String cuerpo) {
    return alta(cuerpo, UUID.randomUUID());
  }

  private MockHttpServletRequestBuilder alta(String cuerpo, UUID actor) {
    RequestPostProcessor actorCon = con(actor, "teams:create");
    return post("/api/v1/teams")
        .with(actorCon)
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }
}
