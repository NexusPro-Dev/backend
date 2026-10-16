package com.factech.nexus.modules.indicators.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * El resumen de cuentas de broker y de mi personal (`RF-IN-010`): `CA-IN-111` a `CA-IN-115`.
 *
 * <p>{@code jefe} ← {@code medio} ← {@code base}, y {@code otro} suelto. «Hoy» es el reloj real:
 * las cuentas de hoy se crean con {@code now()} y las viejas tres días antes.
 */
@AutoConfigureMockMvc
@SuppressWarnings("unchecked") // JsonPath.read devuelve lo que se le pida
class BrokerAccountsSummaryIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000003";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";
  private static final String CLIENTE = "01a02a33-4c00-7008-9c4f-5e7ad1000008";

  private static final String RUTA = "/api/v1/indicators/broker-accounts/summary";
  private static final String PERMISO = "indicators:read-broker-accounts-summary";
  private static final String IQOPTION = "01a081f0-6000-7101-9c4f-5e7adb000001";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID medio;
  private UUID otro;

  @BeforeEach
  void preparar() {
    vaciar();
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM client_sellers");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_products");
    jdbc.update("DELETE FROM user_roles");
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        SUPERADMIN,
        SUPERADMIN_ROL);

    UUID jefe = persona("rs-a-jefe", "Ramón", MANAGER);
    medio = persona("rs-b-medio", "Ana", DIRECTOR);
    UUID base = persona("rs-c-base", "Lucía", AGENTE);
    otro = persona("rs-d-otro", "Zoe", AGENTE);
    UUID c1 = persona("rs-e-c1", "Carla", CLIENTE);
    UUID c2 = persona("rs-f-c2", "Diego", CLIENTE);
    UUID c3 = persona("rs-g-c3", "Elena", CLIENTE);
    reportar(medio, jefe);
    reportar(base, medio);

    UUID broker = broker("01a081f0-6000-7102-9c4f-5e7adb000002", "EXNOVA");
    UUID deJefe = vendedora(jefe, broker, "V-JEFE");
    UUID deMedio = vendedora(medio, broker, "V-MEDIO");
    UUID deBase = vendedora(base, broker, "V-BASE");
    UUID deAdmin = vendedora(SUPERADMIN, broker, "V-ADMIN");

    // k1: vieja, depositó hoy. k2: de hoy, sin titular. k3: de hoy y depositó hoy.
    cuenta(c1, broker, "k1", deMedio, false, true);
    cuenta(null, broker, "k2", deMedio, true, null);
    cuenta(c2, broker, "k3", deBase, true, true);
    cuenta(c3, broker, "k4", deJefe, false, null);
    // k5 sin origen y k6 con origen fuera de la fuerza comercial: sin vendedor.
    cuenta(null, broker, "k5", null, false, null);
    cuenta(null, broker, "k6", deAdmin, true, null);
    // k3 depositó y opera: no cuenta como sin operar; k1 depositó y no opera.
    jdbc.update(
        "UPDATE user_brokers SET operations_count = 2, first_operation_at = now(),"
            + " last_operation_at = now() WHERE external_id = 'k3'");
  }

  /** {@code user_brokers} vacía: su clave foránea a {@code users} es {@code RESTRICT}. */
  @AfterEach
  void vaciar() {
    jdbc.update("DELETE FROM user_brokers");
    // La estructura también: una fila que sobreviva rompe el `DELETE FROM users`
    // de la siguiente suite que lo haga sin vaciarla antes.
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM client_sellers");
  }

  @Test
  @DisplayName(
      "CA-IN-111, CA-IN-112, CA-IN-116 — administración: todas las de consumidor, sus FTD, sin"
          + " titular, sin operar, y las de hoy por su fecha; las de vendedor no cuentan")
  void losTotales() throws Exception {
    String cuerpo = pedir(como(SUPERADMIN));

    assertThat(JsonPath.<Integer>read(cuerpo, "$.totals.accounts")).isEqualTo(6);
    assertThat(JsonPath.<Integer>read(cuerpo, "$.totals.ftd")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(cuerpo, "$.totals.withoutHolder")).isEqualTo(3);
    assertThat(JsonPath.<Integer>read(cuerpo, "$.totals.withoutOperations")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(cuerpo, "$.totals.accountsToday")).isEqualTo(3);
    // k1 es vieja pero su depósito llegó hoy.
    assertThat(JsonPath.<Integer>read(cuerpo, "$.totals.ftdToday")).isEqualTo(2);
    assertThat(JsonPath.<String>read(cuerpo, "$.today")).isNotBlank();
  }

  @Test
  @DisplayName(
      "CA-IN-113 — el personal por rol de la cúspide hacia abajo, cada persona con lo que originó"
          + " ella, en cero si no tiene nada, y cada grupo suma a sus personas")
  void elPersonalPorRol() throws Exception {
    String cuerpo = pedir(como(SUPERADMIN));

    assertThat((List<String>) JsonPath.read(cuerpo, "$.staff[*].roleCode"))
        .containsExactly("MANAGER", "DIRECTOR", "AGENTE");
    assertThat((List<String>) JsonPath.read(cuerpo, "$.staff[2].people[*].user.username"))
        .containsExactly("rs-c-base", "rs-d-otro");

    // Medio: k1 y k2, no lo de base.
    assertThat(JsonPath.<Integer>read(cuerpo, "$.staff[1].people[0].accounts")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(cuerpo, "$.staff[1].people[0].ftd")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(cuerpo, "$.staff[1].people[0].accountsToday")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(cuerpo, "$.staff[1].people[0].ftdToday")).isEqualTo(1);
    // Jefe: solo k4.
    assertThat(JsonPath.<Integer>read(cuerpo, "$.staff[0].people[0].accounts")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(cuerpo, "$.staff[0].people[0].ftd")).isZero();
    // Otro sale en cero; el grupo de agentes es lo de base.
    assertThat(JsonPath.<Integer>read(cuerpo, "$.staff[2].people[1].accounts")).isZero();
    assertThat(JsonPath.<Integer>read(cuerpo, "$.staff[2].accounts")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(cuerpo, "$.staff[2].ftdToday")).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "CA-IN-114, CA-IN-116 — un vendedor ve lo originado por él y su red, a esas personas, y"
          + " lo sin operar de su alcance")
  void elAlcanceDelVendedor() throws Exception {
    String cuerpo = pedir(como(medio));

    assertThat((List<String>) JsonPath.read(cuerpo, "$.staff[*].people[*].user.username"))
        .containsExactly("rs-b-medio", "rs-c-base");
    assertThat(JsonPath.<Integer>read(cuerpo, "$.totals.accounts")).isEqualTo(3);
    assertThat(JsonPath.<Integer>read(cuerpo, "$.totals.ftd")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(cuerpo, "$.totals.withoutHolder")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(cuerpo, "$.totals.accountsToday")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(cuerpo, "$.totals.withoutOperations")).isEqualTo(1);

    String suelto = pedir(como(otro));
    assertThat((List<String>) JsonPath.read(suelto, "$.staff[*].people[*].user.username"))
        .containsExactly("rs-d-otro");
    assertThat(JsonPath.<Integer>read(suelto, "$.totals.accounts")).isZero();
  }

  @Test
  @DisplayName(
      "CA-IN-117 — cada persona trae su superior vigente y sus afftrack; la cúspide va sin"
          + " superior, y el de un vendedor puede quedar fuera de la respuesta")
  void superiorYAfftracks() throws Exception {
    String cuerpo = pedir(como(SUPERADMIN));

    // Jefe: cúspide, sin superior.
    assertThat((Object) JsonPath.read(cuerpo, "$.staff[0].people[0].supervisorId")).isNull();
    assertThat(JsonPath.<String>read(cuerpo, "$.staff[0].people[0].afftracks[0].afftrack"))
        .isEqualTo("AFF-V-JEFE");
    assertThat(JsonPath.<String>read(cuerpo, "$.staff[0].people[0].afftracks[0].brokerName"))
        .isEqualTo("EXNOVA");
    // Medio cuelga de jefe; otro no tiene cuenta VENDEDOR: lista vacía, no ausente.
    String idJefe = JsonPath.read(cuerpo, "$.staff[0].people[0].user.id");
    assertThat(JsonPath.<String>read(cuerpo, "$.staff[1].people[0].supervisorId"))
        .isEqualTo(idJefe);
    assertThat((List<?>) JsonPath.read(cuerpo, "$.staff[2].people[1].afftracks")).isEmpty();

    // Para medio, su superior no viene en la respuesta: él es la raíz.
    String suyo = pedir(como(medio));
    assertThat(JsonPath.<String>read(suyo, "$.staff[0].people[0].supervisorId")).isEqualTo(idJefe);
    assertThat((List<String>) JsonPath.read(suyo, "$.staff[*].people[*].user.id"))
        .doesNotContain(idJefe);
  }

  @Test
  @DisplayName(
      "CA-IN-118 — from/to acotan los totales y no lo de hoy; brokerId y sellerId acotan todo,"
          + " y con sellerId no entra lo no atribuido")
  void losFiltros() throws Exception {
    String ayer = java.time.LocalDate.now().minusDays(1).toString();
    String manana = java.time.LocalDate.now().plusDays(1).toString();

    // Desde ayer: las creadas hoy (k2, k3, k6) y los FTD de hoy (k1, k3).
    String reciente = pedir(RUTA + "?from=" + ayer + "&to=" + manana, como(SUPERADMIN));
    assertThat(JsonPath.<Integer>read(reciente, "$.totals.accounts")).isEqualTo(3);
    assertThat(JsonPath.<Integer>read(reciente, "$.totals.ftd")).isEqualTo(2);

    // Un periodo viejo deja los totales en cero, y lo de hoy sigue siendo de hoy.
    String viejo = pedir(RUTA + "?from=2020-01-01&to=2020-01-31", como(SUPERADMIN));
    assertThat(JsonPath.<Integer>read(viejo, "$.totals.accounts")).isZero();
    assertThat(JsonPath.<Integer>read(viejo, "$.totals.accountsToday")).isEqualTo(3);
    assertThat(JsonPath.<Integer>read(viejo, "$.totals.ftdToday")).isEqualTo(2);

    // Otro broker: todo en cero, también lo de hoy, y sin afftrack de EXNOVA.
    String deIq = pedir(RUTA + "?brokerId=" + IQOPTION, como(SUPERADMIN));
    assertThat(JsonPath.<Integer>read(deIq, "$.totals.accounts")).isZero();
    assertThat(JsonPath.<Integer>read(deIq, "$.totals.accountsToday")).isZero();
    assertThat((List<?>) JsonPath.read(deIq, "$.staff[0].people[0].afftracks")).isEmpty();

    // La rama de medio: medio y base, sin jefe ni lo no atribuido.
    String rama = pedir(RUTA + "?sellerId=" + medio, como(SUPERADMIN));
    assertThat((List<String>) JsonPath.read(rama, "$.staff[*].people[*].user.username"))
        .containsExactly("rs-b-medio", "rs-c-base");
    assertThat(JsonPath.<Integer>read(rama, "$.totals.accounts")).isEqualTo(3);
    assertThat(JsonPath.<Integer>read(rama, "$.totals.accountsToday")).isEqualTo(2);

    // Un vendedor fuera de su alcance: todo en cero.
    String ajena = pedir(RUTA + "?sellerId=" + medio, como(otro));
    assertThat((List<?>) JsonPath.read(ajena, "$.staff")).isEmpty();
    assertThat(JsonPath.<Integer>read(ajena, "$.totals.accounts")).isZero();

    mvc.perform(get(RUTA + "?from=2026-10-10&to=2026-10-01").with(como(SUPERADMIN)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("CA-IN-115 — sin el permiso es 403; V115 lo siembra a FUNCIONARIO y VENDEDOR")
  void elPermiso() throws Exception {
    mvc.perform(
            get(RUTA)
                .with(
                    user(SUPERADMIN.toString())
                        .authorities(() -> "indicators:read-broker-accounts-network")))
        .andExpect(status().isForbidden());

    assertThat(
            jdbc.queryForList(
                "SELECT DISTINCT r.role_type FROM role_permissions rp JOIN roles r"
                    + " ON r.id = rp.role_id JOIN permissions p ON p.id = rp.permission_id"
                    + " WHERE p.code = ?",
                String.class,
                PERMISO))
        .containsExactlyInAnyOrder("VENDEDOR", "FUNCIONARIO");
  }

  private String pedir(RequestPostProcessor quien) throws Exception {
    return pedir(RUTA, quien);
  }

  private String pedir(String ruta, RequestPostProcessor quien) throws Exception {
    return mvc.perform(get(ruta).with(quien))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static RequestPostProcessor como(UUID persona) {
    return user(persona.toString()).authorities(() -> PERMISO);
  }

  private UUID persona(String username, String nombre, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, ?, 'Apellido', 'x', false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co",
        nombre);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        id,
        rol);
    return id;
  }

  private void reportar(UUID subordinado, UUID superior) {
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at)"
            + " VALUES (gen_random_uuid(), ?, ?, now() - interval '30 days')",
        subordinado,
        superior);
  }

  private UUID broker(String id, String nombre) {
    jdbc.update(
        "INSERT INTO brokers (id, name) VALUES (CAST(? AS uuid), ?) ON CONFLICT DO NOTHING",
        id,
        nombre);
    return jdbc.queryForObject("SELECT id FROM brokers WHERE name = ?", UUID.class, nombre);
  }

  private UUID vendedora(UUID persona, UUID broker, String cuenta) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO user_brokers (id, user_id, broker_id, external_id, kind, afftrack)"
            + " VALUES (?, ?, ?, ?, 'VENDEDOR', ?)",
        id,
        persona,
        broker,
        cuenta,
        "AFF-" + cuenta);
    return id;
  }

  /**
   * Creada hoy o tres días antes; {@code depositoHoy} nulo es sin depósito, verdadero hoy y falso
   * tres días antes.
   */
  private void cuenta(
      UUID titular, UUID broker, String numero, UUID origen, boolean hoy, Boolean depositoHoy) {
    jdbc.update(
        """
        INSERT INTO user_brokers (id, user_id, broker_id, external_id, kind, referrer_account_id,
                                  status, created_at, first_deposit_at)
        VALUES (gen_random_uuid(), ?, ?, ?, 'CONSUMIDOR', ?,
                CASE WHEN ?::boolean IS NULL THEN 'REGISTER' ELSE 'FIRST_DEPOSIT' END,
                CASE WHEN ? THEN now() ELSE now() - interval '3 days' END,
                CASE WHEN ?::boolean THEN now()
                     WHEN NOT ?::boolean THEN now() - interval '3 days' END)
        """,
        titular,
        broker,
        numero,
        origen,
        depositoHoy,
        hoy,
        depositoHoy,
        depositoHoy);
  }
}
