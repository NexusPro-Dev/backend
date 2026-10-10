package com.factech.nexus.modules.system.brokers.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
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
 * Las cuentas de broker que originó mi red (`RF-SP-083`, `RN-SP-075`): `CA-SP-1003` a `CA-SP-1006`.
 *
 * <p><b>La red tiene tres niveles</b> —{@code jefe} ← {@code medio} ← {@code base}— para que «toda
 * la red» se distinga de «el equipo directo», y hay un <b>exsubordinado</b> con la relación
 * cerrada, para que «vigente» se distinga de «alguna vez».
 */
@AutoConfigureMockMvc
class ReferredBrokerAccountsIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000003";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";
  private static final String CLIENTE = "01a02a33-4c00-7008-9c4f-5e7ad1000008";

  private static final String RUTA = "/api/v1/users/me/referred-broker-accounts";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID jefe;
  private UUID medio;
  private UUID base;
  private UUID ajeno;
  private UUID exbase;
  private UUID solo;

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

    jefe = crearPersona("rr-jefe", "Ramón", MANAGER);
    medio = crearPersona("rr-medio", "Ana", DIRECTOR);
    base = crearPersona("rr-base", "Lucía", AGENTE);
    ajeno = crearPersona("rr-ajeno", "Zoe", AGENTE);
    exbase = crearPersona("rr-exbase", "Iván", AGENTE);
    solo = crearPersona("rr-solo", "Sara", AGENTE);
    UUID cliente1 = crearPersona("rr-cliente1", "Carla", CLIENTE);
    UUID cliente2 = crearPersona("rr-cliente2", "Diego", CLIENTE);

    reportar(medio, jefe, false);
    reportar(base, medio, false);
    reportar(exbase, medio, true);

    brokerA = brokerAsegurado("01a081f0-6000-7102-9c4f-5e7adb000002", "EXNOVA");
    brokerB = brokerAsegurado("01a081f0-6000-7101-9c4f-5e7adb000001", "IQOPTION");

    UUID deJefe = vendedora(jefe, brokerA, "V-JEFE", "AFF-JEFE");
    UUID deMedio = vendedora(medio, brokerA, "V-MEDIO", "AFF-MEDIO");
    UUID deBase = vendedora(base, brokerB, "V-BASE", "AFF-BASE");
    UUID deAjeno = vendedora(ajeno, brokerA, "V-AJENO", "AFF-AJENO");
    UUID deExbase = vendedora(exbase, brokerA, "V-EXBASE", "AFF-EXBASE");

    consumidora(cliente1, brokerA, "1001", deJefe, "REGISTER", null);
    consumidora(null, brokerA, "1002", deMedio, "FIRST_DEPOSIT", "trader_maria");
    consumidora(cliente2, brokerB, "1003", deBase, "REGISTER", null);
    consumidora(null, brokerA, "1004", deAjeno, "REGISTER", null);
    consumidora(null, brokerA, "1005", deExbase, "REGISTER", null);
    consumidora(null, brokerA, "1006", null, "REGISTER", null);
  }

  /** {@code user_brokers} vacía: su clave foránea a {@code users} es {@code RESTRICT}. */
  @AfterEach
  void vaciar() {
    jdbc.update("DELETE FROM user_brokers");
  }

  @Test
  @DisplayName(
      "`CA-SP-1003` — el vendedor ve las cuentas de consumidor que originó su cuenta y las de toda"
          + " su red, también sin titular, con el vendedor de origen y su nombre")
  void veLasDeSuRed() throws Exception {
    mvc.perform(get(RUTA).with(como(jefe)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content[*].accountId", containsInAnyOrder("1001", "1002", "1003")))
        .andExpect(jsonPath("$.summary.accounts.total").value(3))
        .andExpect(jsonPath("$.summary.firstDeposit.total").value(1));

    mvc.perform(get(RUTA + "?search=1002").with(como(jefe)))
        .andExpect(jsonPath("$.content[0].user").isEmpty())
        .andExpect(jsonPath("$.content[0].kind").value("CONSUMIDOR"))
        .andExpect(jsonPath("$.content[0].referrer.afftrack").value("AFF-MEDIO"))
        .andExpect(jsonPath("$.content[0].referrer.userId").value(medio.toString()))
        .andExpect(jsonPath("$.content[0].referrer.username").value("rr-medio"))
        .andExpect(jsonPath("$.content[0].referrer.firstName").value("Ana"))
        .andExpect(jsonPath("$.content[0].referrer.lastName").value("Apellido"));

    // Un nivel más abajo, solo lo suyo y lo de los suyos.
    mvc.perform(get(RUTA).with(como(medio)))
        .andExpect(jsonPath("$.content[*].accountId", containsInAnyOrder("1002", "1003")));
  }

  @Test
  @DisplayName(
      "`CA-SP-1004` — no ve las de su superior, otra rama o quien dejó su red, ni cuentas"
          + " VENDEDOR; sin cuentas originadas, la página vacía con el resumen en ceros")
  void noVeLoAjeno() throws Exception {
    mvc.perform(get(RUTA).with(como(base)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].accountId").value("1003"));
    mvc.perform(get(RUTA).with(como(medio)))
        .andExpect(jsonPath("$.content[*].accountId", containsInAnyOrder("1002", "1003")));
    mvc.perform(get(RUTA).with(como(ajeno)))
        .andExpect(jsonPath("$.content[*].accountId", containsInAnyOrder("1004")));

    mvc.perform(get(RUTA).with(como(solo)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.summary.accounts.total").value(0));
  }

  @Test
  @DisplayName(
      "`CA-SP-1005` — status, brokerId, sellerId, hasHolder, search por número o por usuario del"
          + " broker y fechas acotan la página y el resumen; un status desconocido es 400")
  void losFiltros() throws Exception {
    unico("?status=FIRST_DEPOSIT", "1002");
    unico("?brokerId=" + brokerB, "1003");
    unico("?sellerId=" + base, "1003");
    unico("?hasHolder=false", "1002");
    unico("?search=TRADER_MAR", "1002");
    unico("?search=1001", "1001");

    mvc.perform(get(RUTA + "?sellerId=" + ajeno).with(como(jefe)))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get(RUTA + "?from=2999-01-01T00:00:00Z").with(como(jefe)))
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.summary.accounts.total").value(0));
    mvc.perform(get(RUTA + "?status=PENDIENTE").with(como(jefe)))
        .andExpect(status().isBadRequest());
    mvc.perform(get(RUTA + "?from=2026-10-10T00:00:00Z&to=2026-10-09T00:00:00Z").with(como(jefe)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "`CA-SP-1006` — sin broker-accounts:read-own-referred es 403; V100 lo siembra a los roles"
          + " VENDEDOR y FUNCIONARIO y a ningún CONSUMIDOR")
  void elPermiso() throws Exception {
    mvc.perform(
            get(RUTA)
                .with(user(jefe.toString()).authorities(() -> "broker-accounts:read-own-team")))
        .andExpect(status().isForbidden());

    List<String> tipos =
        jdbc.queryForList(
            "SELECT DISTINCT r.role_type FROM role_permissions rp JOIN roles r ON r.id = rp.role_id"
                + " JOIN permissions p ON p.id = rp.permission_id"
                + " WHERE p.code = 'broker-accounts:read-own-referred'",
            String.class);
    assertThat(tipos).containsExactlyInAnyOrder("VENDEDOR", "FUNCIONARIO");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM roles r WHERE r.role_type IN ('VENDEDOR', 'FUNCIONARIO')"
                    + " AND NOT EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p"
                    + " ON p.id = rp.permission_id WHERE rp.role_id = r.id"
                    + " AND p.code = 'broker-accounts:read-own-referred')",
                Integer.class))
        .isZero();
  }

  private void unico(String consulta, String cuenta) throws Exception {
    mvc.perform(get(RUTA + consulta).with(como(jefe)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].accountId").value(cuenta))
        .andExpect(jsonPath("$.summary.accounts.total").value(1));
  }

  private static RequestPostProcessor como(UUID persona) {
    return user(persona.toString()).authorities(() -> "broker-accounts:read-own-referred");
  }

  private UUID crearPersona(String username, String nombre, String rol) {
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

  /** Con {@code cerrada}, la relación terminó ayer: ya no es red. */
  private void reportar(UUID subordinado, UUID superior, boolean cerrada) {
    jdbc.update(
        """
        INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at, ended_at)
        VALUES (gen_random_uuid(), ?, ?, now() - interval '2 days',
                CASE WHEN ? THEN now() - interval '1 day' END)
        """,
        subordinado,
        superior,
        cerrada);
  }

  private UUID vendedora(UUID persona, UUID broker, String cuenta, String afftrack) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO user_brokers (id, user_id, broker_id, external_id, kind, afftrack)
        VALUES (?, ?, ?, ?, 'VENDEDOR', ?)
        """,
        id,
        persona,
        broker,
        cuenta,
        afftrack);
    return id;
  }

  private void consumidora(
      UUID persona, UUID broker, String cuenta, UUID origen, String estado, String usuario) {
    jdbc.update(
        """
        INSERT INTO user_brokers (id, user_id, broker_id, external_id, kind, referrer_account_id,
                                  status, broker_username)
        VALUES (gen_random_uuid(), ?, ?, ?, 'CONSUMIDOR', ?, ?, ?)
        """,
        persona,
        broker,
        cuenta,
        origen,
        estado,
        usuario);
  }

  private UUID brokerAsegurado(String id, String nombre) {
    jdbc.update(
        "INSERT INTO brokers (id, name) VALUES (CAST(? AS uuid), ?) ON CONFLICT DO NOTHING",
        id,
        nombre);
    return jdbc.queryForObject("SELECT id FROM brokers WHERE name = ?", UUID.class, nombre);
  }
}
