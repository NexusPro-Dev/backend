package com.factech.nexus.modules.system.users.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.users.application.CommercialReach;
import com.factech.nexus.modules.system.users.application.CommercialReach.Kind;
import com.factech.nexus.modules.system.users.application.CommercialReach.Reach;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * `RF-MV-015` · `T-03` — la definición de «hasta dónde llega una persona» que `SP` publica
 * (`RN-MV-031`), probada donde vive.
 *
 * <p>Un árbol con dos ramas bajo un manager, un agente suelto y uno que dejó la red: lo que
 * distingue un recorrido correcto de uno plausible es que <b>la rama de al lado no entra</b> y que
 * <b>la relación cerrada no cuenta</b>.
 */
class CommercialReachIT extends IntegrationTestBase {

  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000003";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";
  private static final String CLIENTE = "01a02a33-4c00-7008-9c4f-5e7ad1000008";

  @Autowired private CommercialReach alcance;
  @Autowired private JdbcTemplate jdbc;

  private UUID funcionario;
  private UUID manager;
  private UUID director1;
  private UUID director2;
  private UUID agente1;
  private UUID agente2;
  private UUID agente3;
  private UUID suelto;
  private UUID exagente;
  private UUID cliente;

  @BeforeEach
  void sembrar() {
    limpiar();
    funcionario = persona("reach-funcionario", ADMIN);
    manager = persona("reach-manager", MANAGER);
    director1 = persona("reach-director1", DIRECTOR);
    director2 = persona("reach-director2", DIRECTOR);
    agente1 = persona("reach-agente1", AGENTE);
    agente2 = persona("reach-agente2", AGENTE);
    agente3 = persona("reach-agente3", AGENTE);
    suelto = persona("reach-suelto", AGENTE);
    exagente = persona("reach-exagente", AGENTE);
    cliente = persona("reach-cliente", CLIENTE);

    reportar(director1, manager);
    reportar(director2, manager);
    reportar(agente1, director1);
    reportar(agente2, director1);
    reportar(agente3, director2);
    // Colgó de director1 y ya no: la relación tiene fin.
    jdbc.update(
        """
        INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at, ended_at)
        VALUES (gen_random_uuid(), ?, ?, now() - interval '30 days', now() - interval '1 day')
        """,
        exagente,
        director1);
  }

  @AfterEach
  void devolverLaBaseASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName("un rol de tipo FUNCIONARIO alcanza TODO, y el conjunto va vacío")
  void funcionarioAlcanzaTodo() {
    Reach hastaDonde = alcance.reachOf(funcionario);
    assertThat(hastaDonde.kind()).isEqualTo(Kind.EVERYTHING);
    assertThat(hastaDonde.sellers()).isEmpty();
  }

  @Test
  @DisplayName("el manager alcanza su red en TODA la profundidad, con él dentro, y no al suelto")
  void managerAlcanzaLaRedEntera() {
    Reach hastaDonde = alcance.reachOf(manager);
    assertThat(hastaDonde.kind()).isEqualTo(Kind.NETWORK);
    assertThat(hastaDonde.sellers())
        .containsExactlyInAnyOrder(manager, director1, director2, agente1, agente2, agente3);
  }

  @Test
  @DisplayName("el director alcanza a sus agentes y no a los del otro director ni al que se fue")
  void directorAlcanzaSuRama() {
    assertThat(alcance.reachOf(director1).sellers())
        .containsExactlyInAnyOrder(director1, agente1, agente2)
        .doesNotContain(agente3, exagente, manager);
    assertThat(alcance.reachOf(director2).sellers()).containsExactlyInAnyOrder(director2, agente3);
  }

  @Test
  @DisplayName("un agente sin nadie a cargo se alcanza a sí mismo: la raíz se incluye")
  void agenteSoloSeAlcanzaASiMismo() {
    Reach hastaDonde = alcance.reachOf(agente1);
    assertThat(hastaDonde.kind()).isEqualTo(Kind.NETWORK);
    assertThat(hastaDonde.sellers()).containsExactly(agente1);
    assertThat(alcance.reachOf(suelto).sellers()).containsExactly(suelto);
  }

  @Test
  @DisplayName("un consumidor alcanza solo a sí mismo; sin roles, o eliminado, también")
  void consumidorYNadie() {
    assertThat(alcance.reachOf(cliente).kind()).isEqualTo(Kind.OWN);

    UUID sinRoles = persona("reach-sinroles", null);
    assertThat(alcance.reachOf(sinRoles).kind()).isEqualTo(Kind.OWN);

    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", manager);
    assertThat(alcance.reachOf(manager).kind()).isEqualTo(Kind.OWN);

    assertThat(alcance.reachOf(null).kind()).isEqualTo(Kind.OWN);
    assertThat(alcance.reachOf(UUID.randomUUID()).kind()).isEqualTo(Kind.OWN);
  }

  @Test
  @DisplayName("la precedencia: funcionario antes que vendedor, vendedor antes que consumidor")
  void precedencia() {
    darRol(director1, ADMIN);
    assertThat(alcance.reachOf(director1).kind()).isEqualTo(Kind.EVERYTHING);

    darRol(agente3, CLIENTE);
    Reach hastaDonde = alcance.reachOf(agente3);
    assertThat(hastaDonde.kind()).isEqualTo(Kind.NETWORK);
    assertThat(hastaDonde.sellers()).containsExactly(agente3);
  }

  // ---------------------------------------------------------------------------
  // Auxiliares
  // ---------------------------------------------------------------------------

  private void limpiar() {
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'reach-%') OR supervisor_id IN (SELECT id FROM users WHERE username LIKE 'reach-%')");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'reach-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'reach-%'");
  }

  private UUID persona(String username, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co");
    if (rol != null) {
      darRol(id, rol);
    }
    return id;
  }

  private void darRol(UUID persona, String rol) {
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        persona,
        rol);
  }

  private void reportar(UUID subordinado, UUID superior) {
    jdbc.update(
        """
        INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at)
        VALUES (gen_random_uuid(), ?, ?, now())
        """,
        subordinado,
        superior);
  }
}
