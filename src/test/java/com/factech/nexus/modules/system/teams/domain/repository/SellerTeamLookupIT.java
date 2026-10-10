package com.factech.nexus.modules.system.teams.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.teams.application.SellerTeamLookup;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * El puerto de la oficina (`RF-MV-001` · `T-49`, `RN-MV-078`): {@link SellerTeamLookup} contra la
 * base, con la cadena y las pertenencias sembradas a mano y en el tiempo.
 *
 * <p><b>Lo que esta suite fija es la regla, no un caso de uso</b>: el equipo del PRIMERO de la
 * cadena —el vendedor incluido— con pertenencia vigente en el instante. El registro, la compra de
 * paquetes, la asignación y el relleno lo usan tal cual.
 */
class SellerTeamLookupIT extends IntegrationTestBase {

  private static final OffsetDateTime HACE_UN_MES =
      OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
  private static final String PREFIJO = "oficina-lookup-";

  @Autowired private SellerTeamLookup oficinas;
  @Autowired private JdbcTemplate jdbc;

  private UUID manager;
  private UUID director;
  private UUID agente;
  private UUID norte;

  @BeforeEach
  void sembrar() {
    limpiar();
    manager = persona("manager");
    director = persona("director");
    agente = persona("agente");
    norte = equipo("Oficina Lookup Norte");
    reportaA(director, manager, HACE_UN_MES, null);
    reportaA(agente, director, HACE_UN_MES, null);
    pertenencia(norte, director, HACE_UN_MES, null);
  }

  @AfterEach
  void limpiar() {
    jdbc.update(
        "DELETE FROM team_members WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
        PREFIJO + "%");
    jdbc.update("DELETE FROM teams WHERE name LIKE 'Oficina Lookup %'");
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE ?)",
        PREFIJO + "%");
    jdbc.update("DELETE FROM users WHERE username LIKE ?", PREFIJO + "%");
  }

  @Test
  @DisplayName(
      "el agente está en la oficina de su director; el director, en la suya; el manager, en"
          + " ninguna")
  void laCadenaParaEnElPrimerMiembro() {
    OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);

    assertThat(oficinas.teamAt(agente, ahora)).contains(norte);
    assertThat(oficinas.teamAt(director, ahora)).contains(norte);
    assertThat(oficinas.teamAt(manager, ahora)).isEmpty();
    assertThat(oficinas.teamAt(null, ahora)).isEmpty();
    assertThat(oficinas.teamAt(agente, null)).isEmpty();
  }

  @Test
  @DisplayName(
      "un instante ANTERIOR a un traslado responde la oficina de entonces, y el de hoy la nueva")
  void elInstanteDecide() {
    UUID otroDirector = persona("otro-director");
    UUID sur = equipo("Oficina Lookup Sur");
    pertenencia(sur, otroDirector, HACE_UN_MES, null);
    OffsetDateTime traslado = OffsetDateTime.now(ZoneOffset.UTC).minusDays(10);
    jdbc.update("UPDATE user_supervisors SET ended_at = ? WHERE user_id = ?", traslado, agente);
    reportaA(agente, otroDirector, traslado, null);

    assertThat(oficinas.teamAt(agente, traslado.minusDays(1))).contains(norte);
    assertThat(oficinas.teamAt(agente, OffsetDateTime.now(ZoneOffset.UTC))).contains(sur);
  }

  @Test
  @DisplayName(
      "un ciclo sembrado —que RN-SP-020 prohíbe y el esquema no impide— no cuelga la consulta")
  void unCicloNoCuelga() {
    UUID uno = persona("ciclo-uno");
    UUID dos = persona("ciclo-dos");
    reportaA(uno, dos, HACE_UN_MES, null);
    reportaA(dos, uno, HACE_UN_MES, null);

    assertThat(oficinas.teamAt(uno, OffsetDateTime.now(ZoneOffset.UTC))).isEmpty();
    assertThat(oficinas.currentTeamsOf(List.of(uno, dos))).isEmpty();
  }

  @Test
  @DisplayName(
      "en lote, una sola respuesta para todos: el que tiene oficina con su entrada, y el que no,"
          + " sin ella")
  void enLote() {
    Map<UUID, UUID> hoy = oficinas.currentTeamsOf(List.of(agente, director, manager));

    assertThat(hoy).containsOnly(Map.entry(agente, norte), Map.entry(director, norte));
    assertThat(oficinas.currentTeamsOf(List.of())).isEmpty();
  }

  private UUID persona(String sufijo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, username, email, first_name, last_name, password_hash, status,"
            + " country_id) VALUES (?, ?, ?, 'Persona', 'De prueba', 'x', 'ACTIVO',"
            + " (SELECT id FROM countries WHERE code = 'COL'))",
        id,
        PREFIJO + sufijo,
        PREFIJO + sufijo + "@factech.co");
    return id;
  }

  private UUID equipo(String nombre) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO teams (id, name) VALUES (?, ?)", id, nombre);
    return id;
  }

  private void pertenencia(UUID equipo, UUID persona, OffsetDateTime desde, OffsetDateTime hasta) {
    jdbc.update(
        "INSERT INTO team_members (id, team_id, user_id, started_at, ended_at)"
            + " VALUES (gen_random_uuid(), ?, ?, ?, ?)",
        equipo,
        persona,
        desde,
        hasta);
  }

  private void reportaA(UUID persona, UUID superior, OffsetDateTime desde, OffsetDateTime hasta) {
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at, ended_at)"
            + " VALUES (gen_random_uuid(), ?, ?, ?, ?)",
        persona,
        superior,
        desde,
        hasta);
  }
}
