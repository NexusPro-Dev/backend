package com.factech.nexus.modules.system.users.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.CommissionCleanup;
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

/**
 * Un cambio de superior se lleva la oficina de lo vendido en el periodo abierto (`RF-SP-041` ·
 * `T-15`; `RN-SP-083`, `RN-MV-080`, `RN-CM-055`): `CA-SP-1050` y `CA-SP-1051`.
 *
 * <p>La estructura: dos managers con oficina —norte y sur— y uno sin ella; un director bajo el del
 * norte, con un agente; y un segundo director bajo el manager sin oficina.
 */
@AutoConfigureMockMvc
class SupervisorChangeMovesOfficeIT extends IntegrationTestBase {

  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000003";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";
  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID norte;
  private UUID sur;
  private UUID managerSur;
  private UUID director;
  private UUID directorSinOficina;
  private UUID agente;
  private UUID cliente;
  private UUID producto;

  @BeforeEach
  void sembrar() {
    limpiar();
    cliente = persona("tl-cliente", null);
    UUID managerNorte = persona("tl-manager-norte", MANAGER);
    managerSur = persona("tl-manager-sur", MANAGER);
    UUID managerSin = persona("tl-manager-sin", MANAGER);
    director = persona("tl-director", DIRECTOR);
    directorSinOficina = persona("tl-director-sin", DIRECTOR);
    agente = persona("tl-agente", AGENTE);
    producto = producto();

    norte = equipo("TL Oficina Norte", managerNorte);
    sur = equipo("TL Oficina Sur", managerSur);
    reporta(director, managerNorte);
    reporta(directorSinOficina, managerSin);
    reporta(agente, director);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "CA-SP-1050 — mover al director lleva a la oficina nueva lo suyo y lo de su red vendido"
          + " desde el último cierre; lo de antes del corte se queda")
  void seLlevaElPeriodoAbierto() throws Exception {
    cierre(5);
    UUID vieja = venta(agente, 10, norte);
    UUID delAgente = venta(agente, 1, norte);
    UUID delDirector = venta(director, 1, norte);

    reasignar(director, managerSur);

    assertThat(oficinaDe(vieja)).isEqualTo(norte);
    assertThat(oficinaDe(delAgente)).isEqualTo(sur);
    assertThat(oficinaDe(delDirector)).isEqualTo(sur);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE module = 'MV' AND entity = 'movements'",
                Integer.class))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("CA-SP-1050 — sin ningún cierre se mueve todo lo vendido")
  void sinCierreSeMueveTodo() throws Exception {
    UUID vieja = venta(agente, 90, norte);

    reasignar(director, managerSur);

    assertThat(oficinaDe(vieja)).isEqualTo(sur);
  }

  @Test
  @DisplayName(
      "CA-SP-1051 — quien queda sin oficina conserva la que tenía, y repetir el mismo superior no"
          + " mueve nada")
  void sinOficinaNoSeVacia() throws Exception {
    UUID delAgente = venta(agente, 1, norte);

    reasignar(agente, directorSinOficina);
    assertThat(oficinaDe(delAgente)).isEqualTo(norte);

    reasignar(agente, director);
    jdbc.update("UPDATE movement_details SET team_id = NULL WHERE movement_id = ?", delAgente);
    reasignar(agente, director);
    assertThat(oficinaDe(delAgente)).isNull();
  }

  private void reasignar(UUID subordinado, UUID superior) throws Exception {
    mvc.perform(
            patch("/api/v1/users/{id}/supervisor", subordinado)
                .with(user(SUPERADMIN.toString()).authorities(() -> "users:assign-supervisor"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supervisorId\":\"" + superior + "\",\"reason\":\"Traslado\"}"))
        .andExpect(status().isOk());
  }

  /** Un cierre a mano terminado hace {@code dias} días. */
  private void cierre(int dias) {
    jdbc.update(
        "INSERT INTO commission_closings (id, origin, triggered_by, started_at, closed_at,"
            + " created_at) VALUES (gen_random_uuid(), 'MANUAL', ?, now() - make_interval(days => ?),"
            + " now() - make_interval(days => ?), now())",
        SUPERADMIN,
        dias,
        dias);
  }

  /** Una venta de hace {@code dias} días con una línea del vendedor y la oficina indicados. */
  private UUID venta(UUID vendedor, int dias, UUID oficina) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id, currency_id, code,
                               status, total_amount, discount_amount, payable_amount, occurred_at)
        VALUES (?, CAST(? AS uuid),
                (SELECT s.id FROM movement_type_statuses s
                  WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = 'VALIDADO'),
                ?, CAST(? AS uuid), ?, 'PENDIENTE', 0, 0, 0, now() - make_interval(days => ?))
        """,
        id,
        VENTA,
        VENTA,
        cliente,
        USD,
        "VTA-TL" + id.toString().substring(0, 8).toUpperCase(),
        dias);
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, team_id,
                                      product_name, quantity, unit_price, line_amount,
                                      implementation)
        SELECT gen_random_uuid(), ?, p.id, ?, ?, p.name, 1, 0, 0, p.implementation
          FROM products p WHERE p.id = ?
        """,
        id,
        vendedor,
        oficina,
        producto);
    return id;
  }

  private UUID oficinaDe(UUID venta) {
    return jdbc.queryForObject(
        "SELECT team_id FROM movement_details WHERE movement_id = ?", UUID.class, venta);
  }

  private void limpiar() {
    CommissionCleanup.limpiar(jdbc);
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM audit_change_log WHERE module IN ('MV', 'SP')");
    jdbc.update(
        "DELETE FROM team_members WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
        "tl-%");
    jdbc.update("DELETE FROM teams WHERE name LIKE 'TL Oficina %'");
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
        "tl-%");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
        "tl-%");
    jdbc.update(
        "DELETE FROM product_links WHERE product_id IN"
            + " (SELECT id FROM products WHERE code LIKE 'TL\\_%')");
    jdbc.update("DELETE FROM products WHERE code LIKE 'TL\\_%'");
    jdbc.update("DELETE FROM users WHERE username LIKE ?", "tl-%");
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
      jdbc.update(
          "INSERT INTO user_roles (user_id, role_id, role_type)"
              + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
          id,
          rol);
    }
    return id;
  }

  private UUID equipo(String nombre, UUID encargado) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO teams (id, name) VALUES (?, ?)", id, nombre);
    jdbc.update(
        "INSERT INTO team_members (id, team_id, user_id, started_at)"
            + " VALUES (gen_random_uuid(), ?, ?, now() - interval '100 days')",
        id,
        encargado);
    return id;
  }

  private void reporta(UUID subordinado, UUID superior) {
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at)"
            + " VALUES (gen_random_uuid(), ?, ?, now() - interval '100 days')",
        subordinado,
        superior);
  }

  private UUID producto() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description,"
            + " price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', 'AUTOMATICA', ?, 'TL_BOT', 'BOT', 'Producto TL', 'x', 1000,"
            + " CAST(? AS uuid), 30, 'ACTIVO')",
        id,
        USD);
    return id;
  }
}
