package com.factech.nexus.modules.system.teams.interfaces;

import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.equipo;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.persona;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Las restricciones de `V33` (`CA-SP-737`), ejercitadas con {@code INSERT} directos.
 *
 * <p><b>Una restricción que nadie ejercita es una restricción que nadie sabe si funciona</b>, y
 * aquí eso importa el doble: {@code uq_team_members_vigente} es `RN-SP-052` declarada en el motor y
 * **nadie escribirá en esa tabla hasta `RF-SP-069`**. Si estuviera mal, el defecto no aparecería
 * hasta la primera reorganización real.
 */
class TeamsSchemaIT extends IntegrationTestBase {

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  @AfterEach
  void limpiar() {
    TeamTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-SP-737` — uq_teams_name es funcional y parcial: rechaza el duplicado sin acentos ni"
          + " caja, y admite el nombre de un equipo eliminado")
  void unicidadDelNombre() {
    equipo(jdbc, "Equipo Norte");

    assertThatThrownBy(() -> equipo(jdbc, "equipo nórte")).hasMessageContaining("uq_teams_name");

    UUID eliminado = equipo(jdbc, "Equipo Sur");
    TeamTestSupport.eliminar(jdbc, eliminado);
    assertThat(equipo(jdbc, "Equipo Sur")).isNotNull();
  }

  @Test
  @DisplayName("`CA-SP-737` — ck_teams_status y ck_teams_name_not_blank rechazan lo que no es")
  void dominioYNombreNoVacio() {
    assertThatThrownBy(() -> equipo(jdbc, "Equipo X", "SUSPENDIDO", null))
        .hasMessageContaining("ck_teams_status");

    assertThatThrownBy(() -> equipo(jdbc, "   ", "ACTIVO", null))
        .hasMessageContaining("ck_teams_name_not_blank");

    assertThatThrownBy(() -> equipo(jdbc, "Equipo Y", "ACTIVO", "x".repeat(501)))
        .hasMessageContaining("ck_teams_description_length");
  }

  @Test
  @DisplayName(
      "`CA-SP-737` — uq_team_members_vigente impide DOS pertenencias vigentes de la misma persona,"
          + " aunque sean de equipos distintos y se inserten a mano (RN-SP-052)")
  void unSoloEquipoVigentePorPersona() {
    UUID norte = equipo(jdbc, "Equipo Norte");
    UUID sur = equipo(jdbc, "Equipo Sur");
    UUID manager = persona(jdbc, "manager-esquema");

    TeamTestSupport.pertenencia(jdbc, norte, manager);

    assertThatThrownBy(() -> TeamTestSupport.pertenencia(jdbc, sur, manager))
        .hasMessageContaining("uq_team_members_vigente");

    // Cerrada la primera, la segunda entra: el historial es ilimitado y lo que
    // se acota es UNA vigente.
    jdbc.update("UPDATE team_members SET ended_at = now() WHERE user_id = ?", manager);
    assertThat(TeamTestSupport.pertenencia(jdbc, sur, manager)).isNotNull();
  }

  @Test
  @DisplayName("`CA-SP-737` — ck_team_members_periodo rechaza un fin anterior al comienzo")
  void periodoCoherente() {
    UUID norte = equipo(jdbc, "Equipo Norte");
    UUID manager = persona(jdbc, "manager-periodo");
    UUID pertenencia = TeamTestSupport.pertenencia(jdbc, norte, manager);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE team_members SET ended_at = started_at - interval '1 day' WHERE id = ?",
                    pertenencia))
        .hasMessageContaining("ck_team_members_periodo");
  }

  @Test
  @DisplayName("`CA-SP-737` — las claves foráneas atan a teams y a users, y esta no borra a nadie")
  void clavesForaneas() {
    UUID manager = persona(jdbc, "manager-fk");

    assertThatThrownBy(() -> TeamTestSupport.pertenencia(jdbc, UUID.randomUUID(), manager))
        .hasMessageContaining("fk_team_members_team");

    UUID norte = equipo(jdbc, "Equipo Norte");
    assertThatThrownBy(() -> TeamTestSupport.pertenencia(jdbc, norte, UUID.randomUUID()))
        .hasMessageContaining("fk_team_members_user");
  }
}
