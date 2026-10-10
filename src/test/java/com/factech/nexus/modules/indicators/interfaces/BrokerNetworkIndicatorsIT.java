package com.factech.nexus.modules.indicators.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Los indicadores de cuentas de broker de la red (`RF-IN-009`, `RN-IN-015`): `CA-IN-104` a
 * `CA-IN-110`.
 *
 * <p>{@code jefe} ← {@code medio} ← {@code base}, y {@code otro} suelto. <b>{@code c1} es cliente
 * de {@code base}</b> —su principal— <b>pero su cuenta la originó el {@code afftrack} de {@code
 * medio}</b>: es lo que distingue la atribución nueva de la de `RF-SP-058`. Las fechas van fijas
 * para que cada cifra caiga, o no, en octubre por la fecha que le toca.
 */
@AutoConfigureMockMvc
@SuppressWarnings("unchecked") // JsonPath.read devuelve lo que se le pida
class BrokerNetworkIndicatorsIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000003";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";
  private static final String CLIENTE = "01a02a33-4c00-7008-9c4f-5e7ad1000008";

  private static final String RUTA = "/api/v1/indicators/broker-accounts/network";
  private static final String OCTUBRE = "?from=2026-10-01&to=2026-10-31";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID jefe;
  private UUID medio;
  private UUID base;
  private UUID otro;
  private UUID brokerA;
  private UUID brokerB;

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

    jefe = persona("in-a-jefe", "Ramón", MANAGER);
    medio = persona("in-b-medio", "Ana", DIRECTOR);
    base = persona("in-c-base", "Lucía", AGENTE);
    otro = persona("in-d-otro", "Zoe", AGENTE);
    UUID c1 = persona("in-e-c1", "Carla", CLIENTE);
    UUID c2 = persona("in-f-c2", "Diego", CLIENTE);
    UUID c3 = persona("in-g-c3", "Elena", CLIENTE);
    UUID c4 = persona("in-h-c4", "Fabio", CLIENTE);
    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", c4);

    reportar(medio, jefe);
    reportar(base, medio);
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin) VALUES (?, ?, 'REGISTRO')",
        c1,
        base);

    brokerA = broker("01a081f0-6000-7102-9c4f-5e7adb000002", "EXNOVA");
    brokerB = broker("01a081f0-6000-7101-9c4f-5e7adb000001", "IQOPTION");

    UUID deJefe = vendedora(jefe, brokerA, "V-JEFE");
    UUID deMedio = vendedora(medio, brokerA, "V-MEDIO");
    UUID deBase = vendedora(base, brokerB, "V-BASE");
    UUID deOtro = vendedora(otro, brokerA, "V-OTRO");
    UUID deAdmin = vendedora(SUPERADMIN, brokerA, "V-ADMIN");

    // k1: creada en septiembre, depósito y operaciones en octubre.
    cuenta(c1, brokerA, "k1", deMedio, "2026-09-05", "2026-10-02", 3, "2026-10-02", "2026-10-03");
    cuenta(null, brokerA, "k2", deMedio, "2026-10-01", null, 0, null, null);
    cuenta(c2, brokerB, "k3", deBase, "2026-10-05", "2026-10-06", 1, "2026-10-07", "2026-10-07");
    cuenta(c3, brokerA, "k4", deJefe, "2026-10-08", null, 0, null, null);
    // k5: en FIRST_DEPOSIT sin fecha de depósito, como las anteriores a V98.
    cuenta(c2, brokerA, "k5", deOtro, "2026-09-10", null, 0, null, null);
    jdbc.update("UPDATE user_brokers SET status = 'FIRST_DEPOSIT' WHERE external_id = 'k5'");
    cuenta(null, brokerA, "k6", null, "2026-10-02", null, 0, null, null);
    cuenta(null, brokerA, "k7", deAdmin, "2026-10-03", null, 0, null, null);
    cuenta(c4, brokerA, "k8", deJefe, "2026-10-04", null, 0, null, null);
  }

  /** {@code user_brokers} vacía: su clave foránea a {@code users} es {@code RESTRICT}. */
  @AfterEach
  void vaciar() {
    jdbc.update("DELETE FROM user_brokers");
  }

  @Test
  @DisplayName(
      "CA-IN-104 — administración ve el árbol: cada cuenta suma en el dueño de su afftrack, también"
          + " sin titular; lo sin origen o de origen ajeno a la fuerza va a lo no atribuido")
  void elArbolPorElAfftrack() throws Exception {
    String cuerpo = pedir(RUTA, admin());

    assertThat((List<String>) JsonPath.read(cuerpo, "$.nodes[*].user.username"))
        .containsExactly("in-a-jefe", "in-d-otro");
    Map<String, Object> deMedio = nodo(cuerpo, "$.nodes[0].children[0]");
    assertThat(JsonPath.<String>read(deMedio, "$.user.username")).isEqualTo("in-b-medio");
    // k1 es de un cliente de base, pero la originó medio: suma en medio.
    assertThat(JsonPath.<Integer>read(deMedio, "$.own.accounts")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(deMedio, "$.own.withoutHolder")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(deMedio, "$.own.ftd")).isEqualTo(1);
    assertThat(JsonPath.<Double>read(deMedio, "$.own.conversion")).isEqualTo(0.5);
    assertThat(JsonPath.<Integer>read(deMedio, "$.children[0].own.accounts")).isEqualTo(1);

    // La red del jefe: lo suyo (k4) más medio (k1, k2) más base (k3). k8, de un
    // titular eliminado, no cuenta.
    assertThat(JsonPath.<Integer>read(cuerpo, "$.nodes[0].own.accounts")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(cuerpo, "$.nodes[0].network.accounts")).isEqualTo(4);
    assertThat(JsonPath.<Integer>read(cuerpo, "$.nodes[0].network.ftd")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(cuerpo, "$.nodes[0].network.consumers")).isEqualTo(3);
    assertThat(JsonPath.<Integer>read(cuerpo, "$.totals.accounts")).isEqualTo(5);
    assertThat(JsonPath.<Integer>read(cuerpo, "$.unassigned.accounts")).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "CA-IN-105 — cada cifra por su fecha: las cuentas creadas en octubre, los FTD llegados en"
          + " octubre aunque la cuenta sea anterior; sin fechas, todo FIRST_DEPOSIT")
  void cadaCifraPorSuFecha() throws Exception {
    String sinFechas = pedir(RUTA, admin());
    // k1, k3 y k5 —esta sin fecha de depósito— están en FIRST_DEPOSIT.
    assertThat(JsonPath.<Integer>read(sinFechas, "$.totals.ftd")).isEqualTo(3);

    String octubre = pedir(RUTA + OCTUBRE, admin());
    // Creadas en octubre en el árbol: k2, k3, k4.
    assertThat(JsonPath.<Integer>read(octubre, "$.totals.accounts")).isEqualTo(3);
    // Depósitos llegados en octubre: k1 (creada en septiembre) y k3; k5 no tiene fecha.
    assertThat(JsonPath.<Integer>read(octubre, "$.totals.ftd")).isEqualTo(2);
    // De las creadas en octubre solo k3 depositó.
    assertThat(JsonPath.<Integer>read(octubre, "$.totals.pending")).isEqualTo(2);
    assertThat(JsonPath.<Double>read(octubre, "$.totals.conversion")).isEqualTo(0.3333);

    Map<String, Object> deMedio = nodo(octubre, "$.nodes[0].children[0]");
    assertThat(JsonPath.<Integer>read(deMedio, "$.own.accounts")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(deMedio, "$.own.ftd")).isEqualTo(1);
    assertThat(JsonPath.<Double>read(deMedio, "$.own.conversion")).isEqualTo(0.0);

    String septiembre = pedir(RUTA + "?from=2026-09-01&to=2026-09-30", admin());
    assertThat(JsonPath.<Integer>read(septiembre, "$.totals.accounts")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(septiembre, "$.totals.ftd")).isZero();
  }

  @Test
  @DisplayName(
      "CA-IN-106 — operaron las de último aviso en el periodo, con su acumulado; sin titular y"
          + " consumidores de las creadas, sin repetir persona")
  void operacionesYTitulares() throws Exception {
    String octubre = pedir(RUTA + OCTUBRE, admin());
    assertThat(JsonPath.<Integer>read(octubre, "$.totals.activeAccounts")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(octubre, "$.totals.operations")).isEqualTo(4);
    assertThat(JsonPath.<Integer>read(octubre, "$.totals.withoutHolder")).isEqualTo(1);
    // Creadas en octubre con titular: k3 (c2) y k4 (c3).
    assertThat(JsonPath.<Integer>read(octubre, "$.totals.consumers")).isEqualTo(2);

    String noviembre = pedir(RUTA + "?from=2026-11-01&to=2026-11-30", admin());
    assertThat(JsonPath.<Integer>read(noviembre, "$.totals.activeAccounts")).isZero();
    assertThat(JsonPath.<Integer>read(noviembre, "$.totals.accounts")).isZero();
    assertThat((Object) JsonPath.read(noviembre, "$.totals.conversion")).isNull();
  }

  @Test
  @DisplayName(
      "CA-IN-107 — cada bloque trae todos los brokers del catálogo, en cero los que no tienen, y el"
          + " desglose suma el bloque")
  void elDesglosePorBroker() throws Exception {
    String cuerpo = pedir(RUTA, admin());
    int brokers = jdbc.queryForObject("SELECT count(*) FROM brokers", Integer.class);

    for (String bloque :
        List.of("$.totals", "$.nodes[0].network", "$.nodes[1].own", "$.unassigned")) {
      List<Integer> cuentas = JsonPath.read(cuerpo, bloque + ".byBroker[*].accounts");
      assertThat(cuentas).hasSize(brokers);
      assertThat(cuentas.stream().mapToInt(Integer::intValue).sum())
          .isEqualTo(JsonPath.<Integer>read(cuerpo, bloque + ".accounts"));
    }
    Map<String, Object> deBase = nodo(cuerpo, "$.nodes[0].children[0].children[0]");
    List<Integer> enB =
        JsonPath.read(deBase, "$.own.byBroker[?(@.broker.name == 'IQOPTION')].accounts");
    List<Integer> enA =
        JsonPath.read(deBase, "$.own.byBroker[?(@.broker.name == 'EXNOVA')].accounts");
    assertThat(enB).containsExactly(1);
    assertThat(enA).containsExactly(0);
  }

  @Test
  @DisplayName(
      "CA-IN-108 — un vendedor ve su rama y no lo no atribuido; sellerId de su red enraíza ahí, uno"
          + " de fuera da nodos vacíos y cifras en cero")
  void elAlcanceDelVendedor() throws Exception {
    String suya = pedir(RUTA, como(medio));
    assertThat((List<String>) JsonPath.read(suya, "$.nodes[*].user.username"))
        .containsExactly("in-b-medio");
    assertThat(JsonPath.<Integer>read(suya, "$.totals.accounts")).isEqualTo(3);
    assertThat((Object) JsonPath.read(suya, "$.unassigned")).isNull();

    String deBase = pedir(RUTA + "?sellerId=" + base, como(medio));
    assertThat((List<String>) JsonPath.read(deBase, "$.nodes[*].user.username"))
        .containsExactly("in-c-base");

    String ajena = pedir(RUTA + "?sellerId=" + jefe, como(medio));
    assertThat((List<Object>) JsonPath.read(ajena, "$.nodes")).isEmpty();
    assertThat(JsonPath.<Integer>read(ajena, "$.totals.accounts")).isZero();

    // Administración con sellerId: la rama, sin lo no atribuido.
    String rama = pedir(RUTA + "?sellerId=" + medio, admin());
    assertThat((List<String>) JsonPath.read(rama, "$.nodes[*].user.username"))
        .containsExactly("in-b-medio");
    assertThat((Object) JsonPath.read(rama, "$.unassigned")).isNull();
  }

  @Test
  @DisplayName(
      "CA-IN-109 — con granularity, los totales traen cuentas y FTD por tramo, todos presentes, y"
          + " suman los totales; un tramo desconocido o from posterior a to es 400")
  void losTramos() throws Exception {
    String mes = pedir(RUTA + OCTUBRE + "&granularity=MONTH", admin());
    assertThat(JsonPath.<String>read(mes, "$.granularity")).isEqualTo("MONTH");
    assertThat((List<String>) JsonPath.read(mes, "$.buckets[*].start"))
        .containsExactly("2026-10-01");
    assertThat(JsonPath.<Integer>read(mes, "$.buckets[0].accounts")).isEqualTo(3);
    assertThat(JsonPath.<Integer>read(mes, "$.buckets[0].ftd")).isEqualTo(2);

    String dias = pedir(RUTA + OCTUBRE + "&granularity=DAY", admin());
    List<Integer> cuentas = JsonPath.read(dias, "$.buckets[*].accounts");
    List<Integer> ftd = JsonPath.read(dias, "$.buckets[*].ftd");
    assertThat(cuentas).hasSize(31);
    assertThat(cuentas.stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);
    assertThat(ftd.stream().mapToInt(Integer::intValue).sum()).isEqualTo(2);
    assertThat((Object) JsonPath.read(pedir(RUTA, admin()), "$.buckets")).isNull();

    mvc.perform(get(RUTA + "?granularity=YEAR").with(admin())).andExpect(status().isBadRequest());
    mvc.perform(get(RUTA + "?from=2026-10-10&to=2026-10-01").with(admin()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "CA-IN-110 — sin el permiso es 403; V101 lo siembra a FUNCIONARIO y VENDEDOR, retira"
          + " broker-accounts:read-indicators y la ruta vieja ya no existe")
  void elPermiso() throws Exception {
    mvc.perform(
            get(RUTA).with(user(SUPERADMIN.toString()).authorities(() -> "broker-accounts:read")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/broker-accounts/indicators").with(admin()))
        .andExpect(status().is4xxClientError());

    assertThat(
            jdbc.queryForList(
                "SELECT DISTINCT r.role_type FROM role_permissions rp JOIN roles r"
                    + " ON r.id = rp.role_id JOIN permissions p ON p.id = rp.permission_id"
                    + " WHERE p.code = 'indicators:read-broker-accounts-network'",
                String.class))
        .containsExactlyInAnyOrder("VENDEDOR", "FUNCIONARIO");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM permissions WHERE code = 'broker-accounts:read-indicators'",
                Integer.class))
        .isZero();
  }

  private String pedir(String ruta, RequestPostProcessor quien) throws Exception {
    return mvc.perform(get(ruta).with(quien))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static Map<String, Object> nodo(String cuerpo, String camino) {
    return JsonPath.read(cuerpo, camino);
  }

  private static RequestPostProcessor admin() {
    return como(SUPERADMIN);
  }

  private static RequestPostProcessor como(UUID persona) {
    return user(persona.toString()).authorities(() -> "indicators:read-broker-accounts-network");
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

  /** Con las fechas a mediodía de Bogotá, lejos de cualquier borde de día. */
  private void cuenta(
      UUID titular,
      UUID broker,
      String numero,
      UUID origen,
      String creada,
      String deposito,
      int operaciones,
      String primeraOperacion,
      String ultimaOperacion) {
    jdbc.update(
        """
        INSERT INTO user_brokers (id, user_id, broker_id, external_id, kind, referrer_account_id,
                                  status, created_at, first_deposit_at, operations_count,
                                  first_operation_at, last_operation_at)
        VALUES (gen_random_uuid(), ?, ?, ?, 'CONSUMIDOR', ?,
                CASE WHEN ?::text IS NULL THEN 'REGISTER' ELSE 'FIRST_DEPOSIT' END,
                (?::date + time '12:00') AT TIME ZONE 'America/Bogota',
                (?::date + time '12:00') AT TIME ZONE 'America/Bogota', ?,
                (?::date + time '12:00') AT TIME ZONE 'America/Bogota',
                (?::date + time '12:00') AT TIME ZONE 'America/Bogota')
        """,
        titular,
        broker,
        numero,
        origen,
        deposito,
        creada,
        deposito,
        operaciones,
        primeraOperacion,
        ultimaOperacion);
  }
}
