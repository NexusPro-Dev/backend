package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.LedgerFixtures;
import com.factech.nexus.modules.movements.PayoutFixtures;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * El catálogo de entidades de cobro: registrar (`RF-MV-032`), consultar (`RF-MV-033`) y editar
 * (`RF-MV-034`).
 */
@AutoConfigureMockMvc
class PayoutInstitutionsIT extends IntegrationTestBase {

  private static final String CREAR = "movements:create-payout-institution";
  private static final String LEER = "movements:read-payout-institutions";
  private static final String EDITAR = "movements:update-payout-institution";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID administrador;
  private UUID colombia;
  private UUID otroPais;
  private UUID paisInactivo;

  @BeforeEach
  void sembrar() {
    limpiar();
    administrador = persona("pi-admin");
    colombia = jdbc.queryForObject("SELECT id FROM countries WHERE code = 'COL'", UUID.class);
    otroPais = pais("ZPA", true);
    paisInactivo = pais("ZPI", false);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-032`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-358 y CA-MV-359 — se registra activa, con el país resuelto, el código en mayúsculas"
          + " y el nombre sin espacios a los lados")
  void registra() throws Exception {
    mvc.perform(crear("zbanco_uno", "  Banco Uno  ", "banco", colombia))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("ZBANCO_UNO"))
        .andExpect(jsonPath("$.name").value("Banco Uno"))
        .andExpect(jsonPath("$.kind").value("BANCO"))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.country.id").value(colombia.toString()))
        .andExpect(jsonPath("$.country.code").value("COL"))
        .andExpect(jsonPath("$.createdAt").exists());
    assertThat(
            jdbc.queryForObject(
                "SELECT is_active FROM payout_institutions WHERE code = 'ZBANCO_UNO'",
                Boolean.class))
        .isTrue();
  }

  @Test
  @DisplayName(
      "CA-MV-360 — un código que ya existe, también en otro país e inactiva, es 409 y no registra"
          + " nada")
  void codigoRepetido() throws Exception {
    mvc.perform(crear("ZREPETIDO", "Original", "BANCO", colombia)).andExpect(status().isCreated());
    jdbc.update("UPDATE payout_institutions SET is_active = false WHERE code = 'ZREPETIDO'");

    mvc.perform(crear("zrepetido", "Otra", "BILLETERA_MOVIL", otroPais))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
    assertThat(entidades()).isEqualTo(1);
  }

  @Test
  @DisplayName("CA-MV-361 — dos registros simultáneos con el mismo código dejan una entidad")
  void carreraDelCodigo() throws Exception {
    CountDownLatch salida = new CountDownLatch(1);
    ExecutorService hilos = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> respuestas = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        respuestas.add(
            hilos.submit(
                () -> {
                  salida.await();
                  return mvc.perform(crear("ZCARRERA", "Carrera", "BANCO", colombia))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      salida.countDown();
      List<Integer> codigos = new ArrayList<>();
      for (Future<Integer> r : respuestas) {
        codigos.add(r.get());
      }
      assertThat(codigos).containsExactlyInAnyOrder(201, 409);
    } finally {
      hilos.shutdownNow();
    }
    assertThat(entidades()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "CA-MV-362 — código mal formado, nombre en blanco, tipo desconocido y país ausente: 400"
          + " con los cuatro problemas juntos, y nada cambia")
  void validaciones() throws Exception {
    mvc.perform(
            post("/api/v1/movements/payout-institutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"1-mal\",\"name\":\"   \",\"kind\":\"CRIPTO\"}")
                .with(con(CREAR)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(4))
        .andExpect(jsonPath("$.errors[0].field").value("code"))
        .andExpect(jsonPath("$.errors[1].field").value("name"))
        .andExpect(jsonPath("$.errors[2].field").value("kind"))
        .andExpect(jsonPath("$.errors[3].field").value("countryId"));
    mvc.perform(crear("Z", "Corto", "BANCO", colombia)).andExpect(status().isBadRequest());
    assertThat(entidades()).isZero();
  }

  @Test
  @DisplayName("CA-MV-363 — país inexistente: 422; país inactivo: 409. Nada cambia")
  void paises() throws Exception {
    mvc.perform(crear("ZSINPAIS", "Sin país", "BANCO", UUID.randomUUID()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    mvc.perform(crear("ZPAISOFF", "País apagado", "BANCO", paisInactivo))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
    assertThat(entidades()).isZero();
  }

  @Test
  @DisplayName(
      "CA-MV-364 y CA-MV-365 — sin el permiso 403, sin token 401; y el registro queda auditado"
          + " con quién lo hizo")
  void permisoYAuditoria() throws Exception {
    mvc.perform(
            post("/api/v1/movements/payout-institutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("ZPERMISO", "Permiso", "BANCO", colombia))
                .with(con(LEER)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/movements/payout-institutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("ZPERMISO", "Permiso", "BANCO", colombia)))
        .andExpect(status().isUnauthorized());
    assertThat(entidades()).isZero();

    UUID id = crearYLeer("ZAUDITADA", "Auditada", "BANCO", colombia);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE entity = 'payout_institutions'"
                    + " AND entity_id = ? AND action = 'CREATE' AND actor_id = ?",
                Integer.class,
                id,
                administrador))
        .isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-033`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-366 a CA-MV-368 — por omisión solo las activas, ordenadas por país y nombre; los"
          + " filtros de país, tipo y estado")
  void consulta() throws Exception {
    crearYLeer("ZB_COL_B", "Beta", "BANCO", colombia);
    crearYLeer("ZB_COL_A", "Alfa", "BANCO", colombia);
    crearYLeer("ZM_COL", "Móvil", "BILLETERA_MOVIL", colombia);
    crearYLeer("ZB_OTRO", "Gamma", "BANCO", otroPais);
    UUID apagada = crearYLeer("ZB_APAGADA", "Apagada", "BANCO", colombia);
    jdbc.update("UPDATE payout_institutions SET is_active = false WHERE id = ?", apagada);

    // CA-MV-366: sin filtros, solo activas; dentro de Colombia, por nombre.
    String todas =
        mvc.perform(get("/api/v1/movements/payout-institutions").with(con(LEER)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> codigos = JsonPath.read(todas, "$[*].code");
    assertThat(codigos).doesNotContain("ZB_APAGADA").contains("ZB_OTRO");
    List<String> deColombia = JsonPath.read(todas, "$[?(@.country.code == 'COL')].code");
    assertThat(deColombia).containsExactly("ZB_COL_A", "ZB_COL_B", "ZM_COL");

    // CA-MV-367
    mvc.perform(
            get("/api/v1/movements/payout-institutions")
                .param("countryId", colombia.toString())
                .param("kind", "BILLETERA_MOVIL")
                .with(con(LEER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].code").value("ZM_COL"));

    // CA-MV-368
    mvc.perform(
            get("/api/v1/movements/payout-institutions")
                .param("countryId", colombia.toString())
                .param("status", "INACTIVA")
                .with(con(LEER)))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].code").value("ZB_APAGADA"))
        .andExpect(jsonPath("$[0].active").value(false));
    mvc.perform(
            get("/api/v1/movements/payout-institutions")
                .param("countryId", colombia.toString())
                .param("status", "TODAS")
                .with(con(LEER)))
        .andExpect(jsonPath("$.length()").value(4));
  }

  @Test
  @DisplayName(
      "CA-MV-369 — un país sin entidades o inexistente da lista vacía; un tipo o un estado"
          + " desconocidos, 400")
  void filtrosVaciosYMalos() throws Exception {
    mvc.perform(
            get("/api/v1/movements/payout-institutions")
                .param("countryId", paisInactivo.toString())
                .with(con(LEER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(
            get("/api/v1/movements/payout-institutions")
                .param("countryId", UUID.randomUUID().toString())
                .with(con(LEER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(
            get("/api/v1/movements/payout-institutions").param("kind", "CRIPTO").with(con(LEER)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/movements/payout-institutions").param("status", "BORRADA").with(con(LEER)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "CA-MV-370 — un CLIENTE la consulta con el permiso de su tipo de rol; sin el permiso 403 y"
          + " sin token 401")
  void permisoDeConsulta() throws Exception {
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM role_permissions rp
                  JOIN roles r ON r.id = rp.role_id
                  JOIN permissions p ON p.id = rp.permission_id
                 WHERE r.code = 'CLIENTE' AND p.code = ?
                """,
                Integer.class,
                LEER))
        .isEqualTo(1);
    mvc.perform(get("/api/v1/movements/payout-institutions").with(con(LEER)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/movements/payout-institutions").with(con(CREAR)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/movements/payout-institutions")).andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-034`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-371 y CA-MV-372 — cambia el nombre; desactivarla la saca del catálogo y"
          + " reactivarla la devuelve")
  void editaYDesactiva() throws Exception {
    UUID id = crearYLeer("ZEDITAR", "Antes", "BANCO", colombia);

    mvc.perform(editar(id, "{\"name\":\"Después\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Después"))
        .andExpect(jsonPath("$.code").value("ZEDITAR"));

    mvc.perform(editar(id, "{\"active\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
    assertThat(codigosDelCatalogo()).doesNotContain("ZEDITAR");

    mvc.perform(editar(id, "{\"active\":true}")).andExpect(status().isOk());
    assertThat(codigosDelCatalogo()).contains("ZEDITAR");
  }

  @Test
  @DisplayName(
      "CA-MV-373 y CA-MV-374 — desactivarla no toca las cuentas, y renombrarla no cambia la copia"
          + " de un retiro ya pedido")
  void noTocaCuentasNiCopias() throws Exception {
    UUID id = crearYLeer("ZCONCUENTAS", "Con cuentas", "BANCO", colombia);
    UUID cliente = persona("pi-cliente");
    UUID cuenta = PayoutFixtures.cuenta(jdbc, cliente, id, "998877665544", true);
    UUID retiro = retiroConCopia(cliente, cuenta, "Con cuentas");

    mvc.perform(editar(id, "{\"name\":\"Renombrada\",\"active\":false}"))
        .andExpect(status().isOk());

    // CA-MV-373
    assertThat(
            jdbc.queryForObject(
                "SELECT is_principal AND deleted_at IS NULL FROM payout_accounts WHERE id = ?",
                Boolean.class,
                cuenta))
        .isTrue();
    // CA-MV-374
    assertThat(
            jdbc.queryForObject(
                "SELECT institution_name FROM withdrawal_destinations WHERE movement_id = ?",
                String.class,
                retiro))
        .isEqualTo("Con cuentas");
  }

  @Test
  @DisplayName("CA-MV-375 — enviar lo que ya tiene no escribe nada, ni auditoría")
  void sinCambios() throws Exception {
    UUID id = crearYLeer("ZIGUAL", "Igual", "BANCO", colombia);
    int antes = auditorias(id);
    mvc.perform(editar(id, "{\"name\":\"Igual\",\"active\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Igual"));
    assertThat(auditorias(id)).isEqualTo(antes);
  }

  @Test
  @DisplayName(
      "CA-MV-376 — cuerpo vacío, nombre en blanco, o con código, tipo o país: 400 y nada cambia;"
          + " una entidad inexistente, 404")
  void edicionesMalas() throws Exception {
    UUID id = crearYLeer("ZMALA", "Mala", "BANCO", colombia);
    mvc.perform(editar(id, "{}")).andExpect(status().isBadRequest());
    mvc.perform(editar(id, "{\"name\":\"  \"}")).andExpect(status().isBadRequest());
    mvc.perform(editar(id, "{\"code\":\"ZOTRO\"}")).andExpect(status().isBadRequest());
    mvc.perform(editar(id, "{\"kind\":\"BILLETERA_MOVIL\"}")).andExpect(status().isBadRequest());
    mvc.perform(editar(id, "{\"countryId\":\"" + otroPais + "\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(editar(UUID.randomUUID(), "{\"name\":\"Nadie\"}")).andExpect(status().isNotFound());
    assertThat(
            jdbc.queryForObject(
                "SELECT code || '|' || name || '|' || kind FROM payout_institutions WHERE id = ?",
                String.class,
                id))
        .isEqualTo("ZMALA|Mala|BANCO");
  }

  @Test
  @DisplayName(
      "CA-MV-377 — sin el permiso 403, sin token 401; y un cambio queda auditado con el valor"
          + " anterior")
  void permisoYAuditoriaDeEditar() throws Exception {
    UUID id = crearYLeer("ZAUDITAR", "Primero", "BANCO", colombia);
    mvc.perform(
            patch("/api/v1/movements/payout-institutions/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Otro\"}")
                .with(con(CREAR)))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/v1/movements/payout-institutions/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Otro\"}"))
        .andExpect(status().isUnauthorized());

    mvc.perform(editar(id, "{\"name\":\"Segundo\"}")).andExpect(status().isOk());
    String cambios =
        jdbc.queryForObject(
            "SELECT CAST(changes AS text) FROM audit_change_log WHERE entity_id = ?"
                + " AND action = 'UPDATE'",
            String.class,
            id);
    assertThat(cambios).contains("Primero").contains("Segundo");
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder crear(
      String codigo, String nombre, String tipo, UUID pais) {
    return post("/api/v1/movements/payout-institutions")
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(codigo, nombre, tipo, pais))
        .with(con(CREAR));
  }

  private UUID crearYLeer(String codigo, String nombre, String tipo, UUID pais) throws Exception {
    String cuerpo =
        mvc.perform(crear(codigo, nombre, tipo, pais))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(cuerpo, "$.id"));
  }

  private MockHttpServletRequestBuilder editar(UUID id, String cuerpo) {
    return patch("/api/v1/movements/payout-institutions/{id}", id)
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo)
        .with(con(EDITAR));
  }

  private static String cuerpo(String codigo, String nombre, String tipo, UUID pais) {
    return "{\"code\":\"%s\",\"name\":\"%s\",\"kind\":\"%s\",\"countryId\":\"%s\"}"
        .formatted(codigo, nombre, tipo, pais);
  }

  private RequestPostProcessor con(String permiso) {
    return user(administrador.toString()).authorities(() -> permiso);
  }

  private List<String> codigosDelCatalogo() throws Exception {
    String cuerpo =
        mvc.perform(get("/api/v1/movements/payout-institutions").with(con(LEER)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(cuerpo, "$[*].code");
  }

  private int entidades() {
    return jdbc.queryForObject("SELECT count(*) FROM payout_institutions", Integer.class);
  }

  private int auditorias(UUID id) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_change_log WHERE entity_id = ?", Integer.class, id);
  }

  /** Un retiro escrito directamente, con su copia, para ver que nadie la reescribe. */
  private UUID retiroConCopia(UUID persona, UUID cuenta, String nombreDeLaEntidad) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id, currency_id, code,
                               status, total_amount, discount_amount, payable_amount, occurred_at)
        SELECT ?, t.id, s.id, ?, CAST(? AS uuid), ?, 'PENDIENTE', 1000, 0, 1000, now()
          FROM movement_types t
          JOIN movement_type_statuses s ON s.movement_type_id = t.id AND s.code = 'REGISTRADO'
         WHERE t.code = 'RETIRO'
        """,
        id,
        persona,
        LedgerFixtures.USD,
        "RET-PI-" + id.toString().substring(0, 8));
    jdbc.update(
        """
        INSERT INTO withdrawal_destinations (movement_id, payout_account_id, institution_code,
            institution_name, institution_kind, account_type, number, holder_name,
            holder_document_type, holder_document_number)
        VALUES (?, ?, 'ZCONCUENTAS', ?, 'BANCO', 'AHORROS', '998877665544', 'Nombre Apellido',
                'CC', '1234567')
        """,
        id,
        cuenta,
        nombreDeLaEntidad);
    return id;
  }

  private UUID pais(String codigo, boolean activo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO countries (id, code, name, is_active) VALUES (?, ?, ?, ?)",
        id,
        codigo,
        "País " + codigo,
        activo);
    return id;
  }

  private void limpiar() {
    LedgerFixtures.limpiar(jdbc);
    jdbc.update("DELETE FROM audit_change_log WHERE entity = 'payout_institutions'");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'pi-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'pi-%'");
    jdbc.update("DELETE FROM countries WHERE code IN ('ZPA', 'ZPI')");
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
    darElSuelo(jdbc, id);
    return id;
  }
}
