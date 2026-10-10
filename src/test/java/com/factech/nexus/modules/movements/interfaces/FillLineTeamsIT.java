package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.CommissionCleanup;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Rellenar la oficina de las líneas de venta (`RF-MV-058` · `T-04`): `CA-MV-729` a `CA-MV-739`.
 *
 * <p><b>Las ventas se siembran por la base</b>, con sus líneas sin oficina, porque eso es lo que la
 * orden viene a arreglar: lo vendido antes de que hubiera directores con equipo. El registro de hoy
 * ya las escribe con oficina, y por la API no se puede producir una línea vacía con vendedor.
 *
 * <p>La estructura: dos directores con oficina —norte y sur—, un agente del norte, un manager sin
 * equipo y un vendedor suelto. Las ventas cubren los cuatro estados y los casos que no se tocan.
 */
@AutoConfigureMockMvc
class FillLineTeamsIT extends IntegrationTestBase {

  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";
  private static final String RUTA = "/api/v1/movements/sales/lines/team-fill";
  private static final String PERMISO = "movements:fill-line-teams";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID norte;
  private UUID sur;
  private UUID directorNorte;
  private UUID directorSur;
  private UUID agente;
  private UUID manager;
  private UUID suelto;
  private UUID cliente;
  private UUID botA;
  private UUID botB;

  @BeforeEach
  void sembrar() {
    limpiar();
    cliente = persona("fl-cliente");
    directorNorte = persona("fl-director-norte");
    directorSur = persona("fl-director-sur");
    agente = persona("fl-agente");
    manager = persona("fl-manager");
    suelto = persona("fl-suelto");
    botA = producto("FL_BOT_A");
    botB = producto("FL_BOT_B");

    norte = equipo("FL Oficina Norte");
    sur = equipo("FL Oficina Sur");
    // La pertenencia empieza HOY: todas las ventas son anteriores, y aun así se
    // rellenan con ella (`CA-MV-733`).
    pertenencia(norte, directorNorte);
    pertenencia(sur, directorSur);
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at)"
            + " VALUES (gen_random_uuid(), ?, ?, now() - interval '60 days')",
        agente,
        directorNorte);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "CA-MV-729, CA-MV-733 y CA-MV-735 — rellena cada línea con vendedor y sin oficina con la"
          + " oficina de HOY del director de su vendedor, en cualquier estado de la venta, y dice"
          + " cuántas")
  void rellenaConLaDeHoy() throws Exception {
    UUID pendiente = venta("PENDIENTE", agente, null);
    UUID confirmada = venta("CONFIRMADA", directorSur, null);
    UUID anulada = venta("ANULADA", agente, null);
    UUID rechazada = venta("RECHAZADA", directorNorte, null);

    mvc.perform(rellenar()).andExpect(status().isOk()).andExpect(jsonPath("$.filled").value(4));

    assertThat(oficinaDe(pendiente, botA)).isEqualTo(norte);
    assertThat(oficinaDe(confirmada, botA)).isEqualTo(sur);
    assertThat(oficinaDe(anulada, botA)).isEqualTo(norte);
    assertThat(oficinaDe(rechazada, botA)).isEqualTo(norte);
  }

  @Test
  @DisplayName(
      "CA-MV-730, CA-MV-731 y CA-MV-732 — no cambia una línea con oficina, ni toca una sin vendedor,"
          + " ni las de quien hoy no tiene director con equipo o es manager, que no cuentan")
  void loQueNoSeToca() throws Exception {
    UUID conOficina = venta("PENDIENTE", agente, sur);
    UUID mixta = venta("PENDIENTE", agente, null);
    linea(mixta, botB, null, null);
    UUID delManager = venta("PENDIENTE", manager, null);
    linea(delManager, botB, suelto, null);

    mvc.perform(rellenar()).andExpect(status().isOk()).andExpect(jsonPath("$.filled").value(1));

    assertThat(oficinaDe(conOficina, botA)).isEqualTo(sur);
    assertThat(oficinaDe(mixta, botA)).isEqualTo(norte);
    assertThat(oficinaDe(mixta, botB)).isNull();
    assertThat(oficinaDe(delManager, botA)).isNull();
    assertThat(oficinaDe(delManager, botB)).isNull();
  }

  @Test
  @DisplayName(
      "CA-MV-734 y CA-MV-737 — dos veces seguidas: la segunda responde cero sin cambiar nada; y"
          + " después de asignar TARDE a un director, rellena solo lo que seguía vacío")
  void repetible() throws Exception {
    UUID delAgente = venta("PENDIENTE", agente, null);
    UUID delSuelto = venta("PENDIENTE", suelto, null);

    mvc.perform(rellenar()).andExpect(jsonPath("$.filled").value(1));
    mvc.perform(rellenar()).andExpect(status().isOk()).andExpect(jsonPath("$.filled").value(0));
    assertThat(oficinaDe(delAgente, botA)).isEqualTo(norte);
    assertThat(oficinaDe(delSuelto, botA)).isNull();

    // El suelto pasa a tener director con oficina.
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id) VALUES (gen_random_uuid(), ?, ?)",
        suelto,
        directorSur);

    mvc.perform(rellenar()).andExpect(jsonPath("$.filled").value(1));
    assertThat(oficinaDe(delSuelto, botA)).isEqualTo(sur);
    assertThat(oficinaDe(delAgente, botA)).isEqualTo(norte);
  }

  @Test
  @DisplayName(
      "CA-MV-736 — solo cambia la oficina: el vendedor, el estado, los importes y el estado del"
          + " tipo quedan como estaban")
  void soloLaOficina() throws Exception {
    UUID venta = venta("CONFIRMADA", agente, null);
    Map<String, Object> antes = fila(venta);

    mvc.perform(rellenar()).andExpect(jsonPath("$.filled").value(1));

    Map<String, Object> despues = fila(venta);
    assertThat(despues.get("team_id")).isEqualTo(norte);
    despues.remove("team_id");
    antes.remove("team_id");
    assertThat(despues).isEqualTo(antes);
  }

  @Test
  @DisplayName(
      "CA-MV-738 — cada venta tocada queda auditada con la oficina de sus líneas, antes y después")
  void auditaPorVenta() throws Exception {
    UUID una = venta("PENDIENTE", agente, null);
    UUID otra = venta("CONFIRMADA", directorSur, null);
    venta("PENDIENTE", manager, null);

    mvc.perform(rellenar()).andExpect(jsonPath("$.filled").value(2));

    List<Map<String, Object>> filas =
        jdbc.queryForList(
            "SELECT entity_id, action, changes::text AS cambios FROM audit_change_log"
                + " WHERE module = 'MV' AND entity = 'movements'");
    assertThat(filas).hasSize(2);
    assertThat(filas).extracting(f -> f.get("entity_id")).containsExactlyInAnyOrder(una, otra);
    assertThat(filas).allSatisfy(f -> assertThat(f.get("action")).isEqualTo("UPDATE"));
    assertThat(filas)
        .anySatisfy(
            f ->
                assertThat((String) f.get("cambios"))
                    .contains("\"team_id\": null")
                    .contains("\"team_id\": \"" + norte + "\"")
                    .contains(botA.toString()));
  }

  @Test
  @DisplayName(
      "CA-MV-739 — sin movements:fill-line-teams responde 403, también con"
          + " movements:assign-sellers; sin autenticar, 401")
  void permisoPropio() throws Exception {
    UUID venta = venta("PENDIENTE", agente, null);

    mvc.perform(
            post(RUTA)
                .with(
                    user(UUID.randomUUID().toString())
                        .authorities(() -> "movements:assign-sellers", () -> "movements:read")))
        .andExpect(status().isForbidden());
    mvc.perform(post(RUTA)).andExpect(status().isUnauthorized());

    assertThat(oficinaDe(venta, botA)).isNull();
  }

  private MockHttpServletRequestBuilder rellenar() {
    return post(RUTA).with(user(UUID.randomUUID().toString()).authorities(() -> PERMISO));
  }

  /** Una venta con una línea del primer producto, del vendedor y con la oficina indicados. */
  private UUID venta(String estado, UUID vendedor, UUID oficina) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id, currency_id, code,
                               status, total_amount, discount_amount, payable_amount, occurred_at,
                               confirmed_at, voided_at, void_reason, rejected_at,
                               rejection_reason)
        VALUES (?, CAST(? AS uuid),
                (SELECT s.id FROM movement_type_statuses s
                  WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = 'VALIDADO'),
                ?, CAST(? AS uuid), ?, ?, 0, 0, 0, now() - interval '10 days',
                CASE WHEN ? = 'CONFIRMADA' THEN now() END,
                CASE WHEN ? = 'ANULADA' THEN now() END,
                CASE WHEN ? = 'ANULADA' THEN 'Prueba de relleno' END,
                CASE WHEN ? = 'RECHAZADA' THEN now() END,
                CASE WHEN ? = 'RECHAZADA' THEN 'Prueba de relleno' END)
        """,
        id,
        VENTA,
        VENTA,
        cliente,
        USD,
        "VTA-FL" + id.toString().substring(0, 8).toUpperCase(),
        estado,
        estado,
        estado,
        estado,
        estado,
        estado);
    linea(id, botA, vendedor, oficina);
    return id;
  }

  private void linea(UUID venta, UUID producto, UUID vendedor, UUID oficina) {
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, team_id,
                                      product_name, quantity, unit_price, line_amount,
                                      implementation)
        SELECT gen_random_uuid(), ?, p.id, ?, ?, p.name, 1, 0, 0, p.implementation
          FROM products p WHERE p.id = ?
        """,
        venta,
        vendedor,
        oficina,
        producto);
  }

  private UUID oficinaDe(UUID venta, UUID producto) {
    return jdbc.queryForObject(
        "SELECT team_id FROM movement_details WHERE movement_id = ? AND product_id = ?",
        UUID.class,
        venta,
        producto);
  }

  private Map<String, Object> fila(UUID venta) {
    return jdbc.queryForMap(
        "SELECT d.seller_id, d.team_id, d.line_amount, d.unit_price, d.delivery_status,"
            + " m.status, m.total_amount, m.type_status_id"
            + " FROM movement_details d JOIN movements m ON m.id = d.movement_id"
            + " WHERE m.id = ?",
        venta);
  }

  private void limpiar() {
    CommissionCleanup.limpiar(jdbc);
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'MV'");
    jdbc.update(
        "DELETE FROM team_members WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
        "fl-%");
    jdbc.update("DELETE FROM teams WHERE name LIKE 'FL Oficina %'");
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
        "fl-%");
    jdbc.update(
        "DELETE FROM product_links WHERE product_id IN"
            + " (SELECT id FROM products WHERE code LIKE 'FL\\_%')");
    jdbc.update("DELETE FROM products WHERE code LIKE 'FL\\_%'");
    jdbc.update("DELETE FROM users WHERE username LIKE ?", "fl-%");
  }

  private UUID persona(String username) {
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
    return id;
  }

  private UUID equipo(String nombre) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO teams (id, name) VALUES (?, ?)", id, nombre);
    return id;
  }

  private void pertenencia(UUID equipo, UUID persona) {
    jdbc.update(
        "INSERT INTO team_members (id, team_id, user_id) VALUES (gen_random_uuid(), ?, ?)",
        equipo,
        persona);
  }

  private UUID producto(String codigo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description,"
            + " price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', 'AUTOMATICA', ?, ?, 'BOT', ?, 'x', 1000, CAST(? AS uuid), 30,"
            + " 'ACTIVO')",
        id,
        codigo,
        "Producto " + codigo,
        USD);
    return id;
  }
}
