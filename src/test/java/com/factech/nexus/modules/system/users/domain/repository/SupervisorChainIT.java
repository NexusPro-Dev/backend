package com.factech.nexus.modules.system.users.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.users.application.SupervisorChain;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** La cadena de mando en un instante (`RF-CM-013` · `T-06`, `requirements/sp.md` v1.88.0). */
class SupervisorChainIT extends IntegrationTestBase {

  @Autowired private SupervisorChain cadenas;
  @Autowired private JdbcTemplate jdbc;

  private UUID agente;
  private UUID director;
  private UUID manager;
  private UUID otro;

  @BeforeEach
  void sembrar() {
    limpiar();
    agente = persona("sc-agente");
    director = persona("sc-director");
    manager = persona("sc-manager");
    otro = persona("sc-otro");
    // El agente colgó del director hasta el 20-09, y desde entonces del otro.
    superior(agente, director, "2026-01-01T00:00:00Z", "2026-09-20T00:00:00Z");
    superior(agente, otro, "2026-09-20T00:00:00Z", null);
    superior(director, manager, "2026-01-01T00:00:00Z", null);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "la cadena de un instante pasado usa las filas vigentes ENTONCES, cerradas incluidas")
  void cadenaPasada() {
    assertThat(cadenas.chainAt(agente, OffsetDateTime.parse("2026-09-10T15:00:00Z")))
        .containsExactly(agente, director, manager);
  }

  @Test
  @DisplayName("la cadena de hoy usa la relación vigente")
  void cadenaVigente() {
    assertThat(cadenas.chainAt(agente, OffsetDateTime.parse("2026-09-25T15:00:00Z")))
        .containsExactly(agente, otro);
  }

  @Test
  @DisplayName("quien no tiene superior es su propia cadena")
  void sinSuperior() {
    assertThat(cadenas.chainAt(manager, OffsetDateTime.parse("2026-09-25T15:00:00Z")))
        .containsExactly(manager);
  }

  @Test
  @DisplayName("antes de que empezara la relación, no la hay")
  void antesDeEmpezar() {
    assertThat(cadenas.chainAt(agente, OffsetDateTime.parse("2025-12-31T15:00:00Z")))
        .containsExactly(agente);
  }

  @Test
  @DisplayName("un ciclo —que el esquema no impide— no cuelga la consulta")
  void unCicloNoCuelga() {
    superior(manager, agente, "2026-01-01T00:00:00Z", null);

    assertThat(cadenas.chainAt(agente, OffsetDateTime.parse("2026-09-10T15:00:00Z")))
        .containsExactly(agente, director, manager);
  }

  private UUID persona(String usuario) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, username, email, first_name, last_name, password_hash, status,"
            + " country_id) VALUES (?, ?, ?, 'Persona', 'De prueba', 'x', 'ACTIVO', (SELECT id"
            + " FROM countries WHERE code = 'COL'))",
        id,
        usuario,
        usuario + "@factech.co");
    return id;
  }

  private void superior(UUID persona, UUID jefe, String desde, String hasta) {
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at, ended_at)"
            + " VALUES (?, ?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz))",
        UUID.randomUUID(),
        persona,
        jefe,
        desde,
        hasta);
  }

  private void limpiar() {
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'sc-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'sc-%'");
  }
}
