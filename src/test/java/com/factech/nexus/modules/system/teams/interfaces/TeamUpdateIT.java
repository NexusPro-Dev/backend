package com.factech.nexus.modules.system.teams.interfaces;

import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.con;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.eliminar;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.equipo;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.persona;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.pertenencia;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * La corrección de un equipo (`RF-SP-066` · `T-07`): `CA-SP-756` a `CA-SP-762`.
 *
 * <p><b>Lo que más importa aquí es lo que NO cambia.</b> El fixture tiene un equipo `INACTIVO` con
 * dos miembros justamente para eso: editarlo no lo reactiva, no lo vacía y no le mueve una sola
 * pertenencia. Un `PATCH` que pudiera hacer cualquiera de esas tres cosas convertiría un permiso de
 * corrección en un permiso de reorganización.
 */
@AutoConfigureMockMvc
class TeamUpdateIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private static final String[] GENTE = {"correccionequipo1", "correccionequipo2"};

  private UUID norte;
  private UUID sur;
  private UUID inactivo;
  private UUID eliminado;

  @BeforeEach
  void sembrar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, GENTE);

    norte = equipo(jdbc, "Equipo Norte", "ACTIVO", "La del norte");
    sur = equipo(jdbc, "Equipo Sur");
    inactivo = equipo(jdbc, "Equipo Suspendido", "INACTIVO", null);
    eliminado = equipo(jdbc, "Equipo Disuelto");
    eliminar(jdbc, eliminado);

    pertenencia(jdbc, inactivo, persona(jdbc, GENTE[0]));
    pertenencia(jdbc, inactivo, persona(jdbc, GENTE[1]));
  }

  @AfterEach
  void limpiar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, GENTE);
  }

  @Test
  @DisplayName(
      "`CA-SP-756` — cambia el nombre con 200, devuelve la forma del detalle con el valor nuevo y"
          + " updatedAt deja de ser igual a createdAt")
  void cambiaElNombre() throws Exception {
    mvc.perform(corregir(norte, "{\"name\":\"  Equipo Norte Andino  \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(norte.toString()))
        .andExpect(jsonPath("$.name").value("Equipo Norte Andino"))
        .andExpect(jsonPath("$.description").value("La del norte"))
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.members").exists());

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT name, created_at <> updated_at AS avanzo FROM teams WHERE id = ?", norte);
    assertThat(fila.get("name")).isEqualTo("Equipo Norte Andino");
    assertThat(fila.get("avanzo")).isEqualTo(Boolean.TRUE);
  }

  @Test
  @DisplayName(
      "`CA-SP-757` — cambia solo la descripción sin tocar el nombre; null la borra y omitirla la"
          + " conserva")
  void cambiaLaDescripcion() throws Exception {
    mvc.perform(corregir(norte, "{\"description\":\"  Managers del norte  \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Equipo Norte"))
        .andExpect(jsonPath("$.description").value("Managers del norte"));

    // `null` explícito BORRA, y la respuesta la trae presente y nula.
    mvc.perform(corregir(norte, "{\"description\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value(nullValue()));

    // Omitirla la conserva: aquí lo que se corrige es el nombre.
    mvc.perform(corregir(norte, "{\"name\":\"Equipo del Norte\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Equipo del Norte"))
        .andExpect(jsonPath("$.description").value(nullValue()));

    // Y una de solo espacios se guarda nula, no en blanco.
    mvc.perform(corregir(sur, "{\"description\":\"   \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value(nullValue()));
  }

  @Test
  @DisplayName(
      "`CA-SP-758` — 409 con el nombre de otro equipo no eliminado, sin caja ni acentos; admite el"
          + " de uno eliminado y el suyo propio")
  void unicidadDelNombre() throws Exception {
    mvc.perform(corregir(norte, "{\"name\":\"equipo sur\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));

    mvc.perform(corregir(norte, "{\"name\":\"Equipo Súr\"}")).andExpect(status().isConflict());

    // El de un eliminado está libre (`RN-SP-050`).
    mvc.perform(corregir(norte, "{\"name\":\"Equipo Disuelto\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Equipo Disuelto"));

    // Y el suyo propio no compite contra sí mismo (`FA-001`).
    mvc.perform(corregir(norte, "{\"name\":\"Equipo Disuelto\"}")).andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "`CA-SP-759` — 400 con el nombre vacío o largo, la descripción larga, el cuerpo sin campos"
          + " (`VAL-003`) y el cuerpo con status o members (`VAL-004`)")
  void formaDelCuerpo() throws Exception {
    mvc.perform(corregir(norte, "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"))
        .andExpect(jsonPath("$.errors[0].field").value("body"));

    mvc.perform(corregir(norte, "{\"name\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));

    // El nombre en nulo NO borra: un equipo sin nombre no existe.
    mvc.perform(corregir(norte, "{\"name\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));

    mvc.perform(corregir(norte, "{\"name\":\"" + "N".repeat(101) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));

    mvc.perform(corregir(norte, "{\"description\":\"" + "D".repeat(501) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));

    // Los dos mal a la vez vuelven JUNTOS.
    mvc.perform(corregir(norte, "{\"name\":\"\",\"description\":\"" + "D".repeat(501) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(org.hamcrest.Matchers.hasItems("name", "description")));

    // Ni el estado ni los miembros se tocan por aquí.
    mvc.perform(corregir(norte, "{\"name\":\"Otro\",\"status\":\"INACTIVO\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(corregir(norte, "{\"name\":\"Otro\",\"members\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-SP-760` — 404 al inexistente y al eliminado, con el mismo código")
  void inexistenteYEliminado() throws Exception {
    mvc.perform(corregir(UUID.randomUUID(), "{\"name\":\"Cualquiera\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No existe un equipo con ese identificador."));

    mvc.perform(corregir(eliminado, "{\"name\":\"Cualquiera\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No existe un equipo con ese identificador."));
  }

  @Test
  @DisplayName(
      "`CA-SP-761` — la edición no toca el estado, deleted_at ni las pertenencias: un INACTIVO con"
          + " miembros se edita y sigue INACTIVO con los mismos")
  void loQueNoCambia() throws Exception {
    mvc.perform(
            corregir(inactivo, "{\"name\":\"Equipo en Pausa\",\"description\":\"Vuelve en Q2\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Equipo en Pausa"))
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.memberCount").value(2))
        .andExpect(jsonPath("$.members", hasSize(2)))
        .andExpect(jsonPath("$.deletedAt").doesNotExist());

    Map<String, Object> fila =
        jdbc.queryForMap("SELECT status, deleted_at FROM teams WHERE id = ?", inactivo);
    assertThat(fila.get("status")).isEqualTo("INACTIVO");
    assertThat(fila.get("deleted_at")).isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM team_members WHERE team_id = ? AND ended_at IS NULL",
                Integer.class,
                inactivo))
        .isEqualTo(2);
  }

  @Test
  @DisplayName(
      "`CA-SP-762` — audit_change_log recibe una fila UPDATE con el diff de lo modificado, y sin"
          + " teams:update responde 403 aunque el actor porte teams:read y teams:create")
  void auditoriaYPermiso() throws Exception {
    mvc.perform(corregir(norte, "{\"name\":\"Equipo Andino\",\"description\":\"Corregida\"}"))
        .andExpect(status().isOk());

    List<Map<String, Object>> filas =
        jdbc.queryForList(
            "SELECT action, changes::text AS diff FROM audit_change_log"
                + " WHERE entity = 'teams' AND entity_id = ?",
            norte);
    assertThat(filas).hasSize(1);
    assertThat(filas.get(0).get("action")).isEqualTo("UPDATE");
    String diff = (String) filas.get(0).get("diff");
    assertThat(diff).contains("Equipo Andino").contains("Equipo Norte").contains("Corregida");

    // Renombrar al MISMO nombre no emite una segunda fila: no cambió nada.
    mvc.perform(corregir(norte, "{\"name\":\"Equipo Andino\"}")).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE entity = 'teams' AND entity_id = ?",
                Integer.class,
                norte))
        .isEqualTo(1);

    mvc.perform(
            patch("/api/v1/teams/" + norte)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nada\"}")
                .with(con("teams:read", "teams:create")))
        .andExpect(status().isForbidden());
  }

  private MockHttpServletRequestBuilder corregir(UUID id, String cuerpo) {
    return patch("/api/v1/teams/" + id)
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo)
        .with(con("teams:update"));
  }
}
