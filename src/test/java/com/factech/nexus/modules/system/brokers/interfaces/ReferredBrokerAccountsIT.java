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
 * Las cuentas de broker que originé (`RF-SP-083`, `RN-SP-084`, `RN-SP-085`): `CA-SP-1003` a
 * `CA-SP-1006`, `CA-SP-1047`, `CA-SP-1052` y `CA-SP-1053`.
 *
 * <p><b>La red tiene tres niveles</b> —{@code jefe} ← {@code medio} ← {@code base}—: desde el
 * 16-10-2026 cada uno ve solo lo suyo, y la red es lo que demuestra que lo de abajo no entra.
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

    consumidora(cliente1, brokerA, "1001", deJefe, "REGISTER");
    consumidora(null, brokerA, "1002", deMedio, "FIRST_DEPOSIT");
    consumidora(cliente2, brokerB, "1003", deBase, "REGISTER");
    consumidora(null, brokerA, "1004", deAjeno, "REGISTER");
    consumidora(null, brokerA, "1005", deExbase, "REGISTER");
    consumidora(null, brokerA, "1006", null, "REGISTER");
  }

  /** {@code user_brokers} vacía: su clave foránea a {@code users} es {@code RESTRICT}. */
  @AfterEach
  void vaciar() {
    jdbc.update("DELETE FROM user_brokers");
  }

  @Test
  @DisplayName(
      "`CA-SP-1003` y `CA-SP-1052` — el vendedor ve solo las cuentas de consumidor que originó su"
          + " propia cuenta, también sin titular, con el vendedor de origen y su nombre; las de su red no")
  void veSoloLoSuyo() throws Exception {
    mvc.perform(get(RUTA).with(como(jefe)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].accountId").value("1001"))
        .andExpect(jsonPath("$.summary.accounts.total").value(1));

    mvc.perform(get(RUTA).with(como(medio)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].accountId").value("1002"))
        .andExpect(jsonPath("$.content[0].user").isEmpty())
        .andExpect(jsonPath("$.content[0].kind").value("CONSUMIDOR"))
        .andExpect(jsonPath("$.content[0].referrer.afftrack").value("AFF-MEDIO"))
        .andExpect(jsonPath("$.content[0].referrer.userId").value(medio.toString()))
        .andExpect(jsonPath("$.content[0].referrer.username").value("rr-medio"))
        .andExpect(jsonPath("$.content[0].referrer.firstName").value("Ana"))
        .andExpect(jsonPath("$.content[0].referrer.lastName").value("Apellido"));
  }

  @Test
  @DisplayName(
      "`CA-SP-1004` — no ve las de su superior, otra rama, quien dejó su red ni su red, ni cuentas"
          + " VENDEDOR; sin cuentas originadas, la página vacía con el resumen en ceros")
  void noVeLoAjeno() throws Exception {
    mvc.perform(get(RUTA).with(como(base)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].accountId").value("1003"));
    mvc.perform(get(RUTA).with(como(ajeno)))
        .andExpect(jsonPath("$.content[*].accountId", containsInAnyOrder("1004")));

    mvc.perform(get(RUTA).with(como(solo)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.summary.accounts.total").value(0))
        .andExpect(jsonPath("$.summary.lastRegisterId").isEmpty())
        .andExpect(jsonPath("$.summary.lastFirstDepositId").isEmpty());
  }

  @Test
  @DisplayName(
      "`CA-SP-1005` y `CA-SP-1052` — status, brokerId, hasHolder, search por número y fechas"
          + " acotan la página y el resumen; sellerId se ignora; un status desconocido es 400")
  void losFiltros() throws Exception {
    masDeJefe();
    unico("?status=FIRST_DEPOSIT", "1007");
    unico("?brokerId=" + brokerB, "1008");
    unico("?hasHolder=false", "1007");
    unico("?search=1001", "1001");

    mvc.perform(get(RUTA + "?sellerId=" + base).with(como(jefe)))
        .andExpect(jsonPath("$.content[*].accountId", containsInAnyOrder("1001", "1007", "1008")));
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
      "`CA-SP-1053` — el resumen trae la última cuenta registrada y el último primer depósito de lo"
          + " filtrado, por llegada al sistema; nulo si no hay")
  void lasUltimas() throws Exception {
    masDeJefe();
    java.time.OffsetDateTime ahora = java.time.OffsetDateTime.now();
    jdbc.update(
        "UPDATE user_brokers SET created_at = ? WHERE external_id = '1001'", ahora.minusDays(3));
    jdbc.update(
        "UPDATE user_brokers SET created_at = ?, first_deposit_at = ? WHERE external_id = '1007'",
        ahora.minusDays(2),
        ahora.minusHours(1));
    jdbc.update(
        "UPDATE user_brokers SET created_at = ? WHERE external_id = '1008'", ahora.minusDays(1));

    mvc.perform(get(RUTA).with(como(jefe)))
        .andExpect(jsonPath("$.summary.lastRegisterId").value(idDe("1008").toString()))
        .andExpect(jsonPath("$.summary.lastFirstDepositId").value(idDe("1007").toString()));
    mvc.perform(get(RUTA + "?status=REGISTER").with(como(jefe)))
        .andExpect(jsonPath("$.summary.lastRegisterId").value(idDe("1008").toString()))
        .andExpect(jsonPath("$.summary.lastFirstDepositId").isEmpty());
  }

  @Test
  @DisplayName("`CA-SP-1047` — el rango entra también por el primer depósito, en página y resumen")
  void rangoPorDeposito() throws Exception {
    java.time.OffsetDateTime ahora = java.time.OffsetDateTime.now();
    UUID deMedio =
        jdbc.queryForObject(
            "SELECT id FROM user_brokers WHERE external_id = 'V-MEDIO'", UUID.class);
    consumidora(null, brokerA, "1009", deMedio, "REGISTER");
    consumidora(null, brokerA, "1010", deMedio, "REGISTER");
    jdbc.update(
        "UPDATE user_brokers SET created_at = ?, first_deposit_at = ? WHERE external_id = '1002'",
        ahora.minusDays(10),
        ahora.minusHours(1));
    jdbc.update(
        "UPDATE user_brokers SET created_at = ? WHERE external_id = '1009'", ahora.minusDays(10));

    // 1002 por su depósito, 1010 por su alta; 1009, vieja y sin depósito, fuera.
    mvc.perform(get(RUTA).param("from", ahora.minusDays(2).toString()).with(como(medio)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].accountId", containsInAnyOrder("1002", "1010")))
        .andExpect(jsonPath("$.summary.accounts.total").value(2))
        .andExpect(jsonPath("$.summary.firstDeposit.total").value(1));
  }

  /** Dos cuentas más de jefe: una con depósito y sin titular, y otra en el otro broker. */
  private void masDeJefe() {
    UUID deJefe =
        jdbc.queryForObject("SELECT id FROM user_brokers WHERE external_id = 'V-JEFE'", UUID.class);
    UUID deJefeB = vendedora(jefe, brokerB, "V-JEFE-B", "AFF-JEFE-B");
    UUID cliente = crearPersona("rr-cliente3", "Elena", CLIENTE);
    consumidora(null, brokerA, "1007", deJefe, "FIRST_DEPOSIT");
    consumidora(cliente, brokerB, "1008", deJefeB, "REGISTER");
  }

  private UUID idDe(String cuenta) {
    return jdbc.queryForObject(
        "SELECT id FROM user_brokers WHERE external_id = ?", UUID.class, cuenta);
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

  private void consumidora(UUID persona, UUID broker, String cuenta, UUID origen, String estado) {
    jdbc.update(
        """
        INSERT INTO user_brokers (id, user_id, broker_id, external_id, kind, referrer_account_id,
                                  status)
        VALUES (gen_random_uuid(), ?, ?, ?, 'CONSUMIDOR', ?, ?)
        """,
        persona,
        broker,
        cuenta,
        origen,
        estado);
  }

  private UUID brokerAsegurado(String id, String nombre) {
    jdbc.update(
        "INSERT INTO brokers (id, name) VALUES (CAST(? AS uuid), ?) ON CONFLICT DO NOTHING",
        id,
        nombre);
    return jdbc.queryForObject("SELECT id FROM brokers WHERE name = ?", UUID.class, nombre);
  }
}
