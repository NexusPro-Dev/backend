package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * `RF-MV-016` — los estados por tipo de movimiento (`RN-MV-033`), el estado inicial de una venta
 * (`RN-MV-034`) y la asignación de sus vendedores (`RN-MV-035`).
 *
 * <p><b>Las ventas se registran por la API</b> (`RF-MV-001`) y no con {@code INSERT} a mano: lo que
 * se prueba es precisamente cómo nacen, y un fixture que escribiera el estado lo daría por hecho.
 */
@AutoConfigureMockMvc
class SellerAssignmentIT extends IntegrationTestBase {

  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";
  private static final String VIP = "01a04ad0-e800-7002-9c4f-5e7ad7000002";
  private static final String BECA = "01a04ad0-e800-7001-9c4f-5e7ad7000001";
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";

  private static final String ASIGNAR = "movements:assign-sellers";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID cliente;
  private UUID ana;
  private UUID pedro;
  private UUID ajeno;
  private UUID botA;
  private UUID botB;

  @BeforeEach
  void sembrar() {
    limpiar();
    jdbc.update("DELETE FROM user_products");
    jdbc.update("DELETE FROM memberships");
    jdbc.update(
        """
        INSERT INTO memberships (id, code, name, description, parent_membership_id, level, color)
        VALUES (CAST(? AS uuid), 'VIP', 'VIP', 'Primer nivel de pago.', NULL, 3, '7E57C2'),
               (CAST(? AS uuid), 'BECA', 'Beca', 'Nivel de entrada.', CAST(? AS uuid), 4, '9E9E9E')
        """,
        VIP,
        BECA,
        VIP);
    cliente = persona("sa-cliente");
    ana = persona("sa-ana");
    pedro = persona("sa-pedro");
    ajeno = persona("sa-ajeno");
    botA = producto("SA_BOT_A");
    botB = producto("SA_BOT_B");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // `RN-MV-034` — cómo nace
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-143 — con UN vendedor la venta nace VALIDADO y él va en cada línea")
  void unVendedor() throws Exception {
    vincular(cliente, ana, "REGISTRO");

    mvc.perform(registrar(cliente, botA, botB))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.typeStatus").value("VALIDADO"))
        .andExpect(jsonPath("$.lines[0].seller.id").value(ana.toString()))
        .andExpect(jsonPath("$.lines[1].seller.id").value(ana.toString()));
  }

  @Test
  @DisplayName(
      "CA-MV-144 — con VARIOS vendedores nace VALIDAR_COMISIONES y ninguna línea lleva vendedor")
  void variosVendedores() throws Exception {
    vincular(cliente, ana, "REGISTRO");
    vincular(cliente, pedro, "HOTLINK");

    String cuerpo =
        mvc.perform(registrar(cliente, botA, botB))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.typeStatus").value("VALIDAR_COMISIONES"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // NULO Y PRESENTE, mirado en crudo: `doesNotExist()` pasaría sin la clave.
    assertThat(cuerpo).contains("\"seller\":null");
    assertThat(sinVendedor()).isEqualTo(2);
    // Y NO se escogió el principal en silencio: nadie es su vendedor todavía.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM movement_details WHERE seller_id = ?", Integer.class, ana))
        .isZero();
  }

  @Test
  @DisplayName(
      "CA-MV-146 — quien no es cliente de nadie conserva la atribución de siempre, VALIDADO")
  void sinVinculos() throws Exception {
    // Ni `client_sellers` ni superior: se vende a sí mismo (`RN-MV-003`).
    mvc.perform(registrar(cliente, botA))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.typeStatus").value("VALIDADO"))
        .andExpect(jsonPath("$.lines[0].seller.id").value(cliente.toString()));
  }

  @Test
  @DisplayName(
      "CA-MV-148 — el esquema exige el estado del tipo, sin DEFAULT, y lo ata al tipo del"
          + " movimiento")
  void elEsquemaAtaElEstadoAlTipo() {
    UUID tipoAjeno = UUID.randomUUID();
    UUID estadoAjeno = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO movement_types (id, code, name, prefix) VALUES (?, 'SA_AJENO', 'Ajeno', 'AJE')",
        tipoAjeno);
    jdbc.update(
        "INSERT INTO movement_type_statuses (id, movement_type_id, code, name)"
            + " VALUES (?, ?, 'VALIDADO', 'Validado')",
        estadoAjeno,
        tipoAjeno);
    try {
      // Sin estado: `NOT NULL` y SIN DEFAULT, a propósito.
      assertThatThrownBy(() -> insertarVenta(null))
          .isInstanceOf(DataIntegrityViolationException.class);
      // Con el VALIDADO de OTRO tipo: la clave compuesta lo rechaza.
      assertThatThrownBy(() -> insertarVenta(estadoAjeno))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      jdbc.update("DELETE FROM movement_type_statuses WHERE movement_type_id = ?", tipoAjeno);
      jdbc.update("DELETE FROM movement_types WHERE id = ?", tipoAjeno);
    }
    // Y la siembra de `V36` está: los dos estados de la venta.
    assertThat(
            jdbc.queryForList(
                "SELECT code FROM movement_type_statuses WHERE movement_type_id = CAST(? AS uuid)"
                    + " ORDER BY code",
                String.class,
                VENTA))
        .containsExactly("VALIDADO", "VALIDAR_COMISIONES");
  }

  // ---------------------------------------------------------------------------
  // `RN-MV-035` — asignar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-149 — asignar TODAS las líneas deja la venta VALIDADO en la misma respuesta")
  void asignarTodas() throws Exception {
    UUID venta = ventaPorValidar();

    mvc.perform(asignar(venta, par(botA, ana), par(botB, pedro)).with(conPermiso(ASIGNAR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.typeStatus").value("VALIDADO"))
        .andExpect(
            jsonPath("$.lines[?(@.productId == '" + botA + "')].seller.id").value(ana.toString()))
        .andExpect(
            jsonPath("$.lines[?(@.productId == '" + botB + "')].seller.id")
                .value(pedro.toString()));

    assertThat(estadoDelTipo(venta)).isEqualTo("VALIDADO");
    assertThat(sinVendedor()).isZero();
  }

  @Test
  @DisplayName("CA-MV-150 — asignar una parte deja la venta por validar")
  void asignarUnaParte() throws Exception {
    UUID venta = ventaPorValidar();

    mvc.perform(asignar(venta, par(botA, ana)).with(conPermiso(ASIGNAR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.typeStatus").value("VALIDAR_COMISIONES"))
        .andExpect(
            jsonPath("$.lines[?(@.productId == '" + botA + "')].seller.id").value(ana.toString()));

    assertThat(estadoDelTipo(venta)).isEqualTo("VALIDAR_COMISIONES");
    assertThat(sinVendedor()).isOne();
  }

  @Test
  @DisplayName(
      "CA-MV-151 — un vendedor que no es de los del cliente es 422, y NINGUNA pareja se escribe")
  void vendedorAjeno() throws Exception {
    UUID venta = ventaPorValidar();

    // La primera pareja es válida; la segunda no. Todo o nada.
    mvc.perform(asignar(venta, par(botA, ana), par(botB, ajeno)).with(conPermiso(ASIGNAR)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"));

    assertThat(sinVendedor()).isEqualTo(2);
    assertThat(estadoDelTipo(venta)).isEqualTo("VALIDAR_COMISIONES");
  }

  @Test
  @DisplayName("CA-MV-152 — un producto que no es línea de la venta es 422, y nada cambia")
  void lineaAjena() throws Exception {
    UUID venta = ventaPorValidar();

    mvc.perform(asignar(venta, par(UUID.randomUUID(), ana)).with(conPermiso(ASIGNAR)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));

    assertThat(sinVendedor()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "CA-MV-153 y CA-MV-160 — confirmada por validar: entrega y sigue por validar; lo que falta"
          + " se asigna, lo asignado no se corrige")
  void confirmada() throws Exception {
    UUID venta = ventaPorValidar();
    mvc.perform(asignar(venta, par(botA, ana)).with(conPermiso(ASIGNAR)))
        .andExpect(status().isOk());

    // CA-MV-160: confirmar no espera a la atribución.
    mvc.perform(
            post("/api/v1/movements/{id}/confirmation", venta)
                .with(conPermiso("movements:confirm")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMADA"))
        .andExpect(jsonPath("$.typeStatus").value("VALIDAR_COMISIONES"))
        .andExpect(jsonPath("$.lines[0].deliveryStatus").value("ENTREGADA"));

    // Corregir la que ya tenía vendedor: congelada.
    mvc.perform(asignar(venta, par(botA, pedro)).with(conPermiso(ASIGNAR)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    // La que faltaba sí, y con ella la venta se valida.
    mvc.perform(asignar(venta, par(botB, pedro)).with(conPermiso(ASIGNAR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.typeStatus").value("VALIDADO"));

    assertThat(
            jdbc.queryForObject(
                "SELECT seller_id FROM movement_details WHERE product_id = ?", UUID.class, botA))
        .isEqualTo(ana);
  }

  @Test
  @DisplayName(
      "CA-MV-154 — validada y pendiente, el vendedor de una línea se corrige y sigue VALIDADO")
  void corregirPendiente() throws Exception {
    vincular(cliente, ana, "REGISTRO");
    UUID venta = registrada(cliente, botA);
    // Después de la venta, el cliente gana otro vendedor: ya es elegible.
    vincular(cliente, pedro, "HOTLINK");

    mvc.perform(asignar(venta, par(botA, pedro)).with(conPermiso(ASIGNAR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.typeStatus").value("VALIDADO"))
        .andExpect(jsonPath("$.lines[0].seller.id").value(pedro.toString()));
  }

  @Test
  @DisplayName("CA-MV-155 — una venta anulada es 409 diciendo su estado")
  void anulada() throws Exception {
    UUID venta = ventaPorValidar();
    mvc.perform(
            post("/api/v1/movements/{id}/voiding", venta)
                .with(conPermiso("movements:void"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Registrada por error.\"}"))
        .andExpect(status().isOk());

    mvc.perform(asignar(venta, par(botA, ana)).with(conPermiso(ASIGNAR)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ANULADA")));

    assertThat(sinVendedor()).isEqualTo(2);
  }

  @Test
  @DisplayName("CA-MV-156 — una venta que no existe es 404")
  void inexistente() throws Exception {
    mvc.perform(asignar(UUID.randomUUID(), par(botA, ana)).with(conPermiso(ASIGNAR)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "CA-MV-157 — sin líneas, con una incompleta o con un producto repetido es 400, y nada cambia")
  void formaInvalida() throws Exception {
    UUID venta = ventaPorValidar();

    mvc.perform(conCuerpo(venta, "{\"lines\":[]}").with(conPermiso(ASIGNAR)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(conCuerpo(venta, "{}").with(conPermiso(ASIGNAR)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(
            conCuerpo(venta, "{\"lines\":[{\"productId\":\"" + botA + "\"}]}")
                .with(conPermiso(ASIGNAR)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    mvc.perform(asignar(venta, par(botA, ana), par(botA, pedro)).with(conPermiso(ASIGNAR)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));

    assertThat(sinVendedor()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "CA-MV-158 — sin movements:assign-sellers es 403, también con confirm y create; sin token 401")
  void permisos() throws Exception {
    UUID venta = ventaPorValidar();

    mvc.perform(asignar(venta, par(botA, ana)).with(conPermiso("movements:confirm")))
        .andExpect(status().isForbidden());
    mvc.perform(asignar(venta, par(botA, ana)).with(conPermiso("movements:create")))
        .andExpect(status().isForbidden());
    mvc.perform(asignar(venta, par(botA, ana))).andExpect(status().isUnauthorized());

    assertThat(sinVendedor()).isEqualTo(2);
  }

  @Test
  @DisplayName("CA-MV-159 — el cambio queda auditado: vendedor y estado, antes y después")
  void auditoria() throws Exception {
    UUID venta = ventaPorValidar();
    mvc.perform(asignar(venta, par(botA, ana), par(botB, pedro)).with(conPermiso(ASIGNAR)))
        .andExpect(status().isOk());

    String asiento =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE entity = 'movements'"
                + " AND entity_id = ? AND action = 'UPDATE'",
            String.class,
            venta);
    assertThat(asiento)
        .contains("VALIDAR_COMISIONES")
        .contains("VALIDADO")
        .contains(ana.toString())
        .contains(pedro.toString())
        .contains("\"seller_id\": null");
  }

  // ---------------------------------------------------------------------------
  // Las lecturas
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-161 — los dos listados de administración publican typeStatus y filtran por él")
  void listados() throws Exception {
    UUID porValidar = ventaPorValidar();
    // Otra venta, de otro cliente con un solo vendedor: validada.
    UUID otroCliente = persona("sa-otro-cliente");
    vincular(otroCliente, ana, "REGISTRO");
    UUID validada = registrada(otroCliente, botA);

    mvc.perform(
            get("/api/v1/movements")
                .param("typeStatus", "validar_comisiones")
                .with(conPermiso("movements:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(porValidar.toString()))
        .andExpect(jsonPath("$.content[0].typeStatus").value("VALIDAR_COMISIONES"))
        // Sin vendedor asignado, la lista va VACÍA y presente.
        .andExpect(jsonPath("$.content[0].sellers.length()").value(0));
    mvc.perform(
            get("/api/v1/movements")
                .param("typeStatus", "VALIDADO")
                .with(conPermiso("movements:read")))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(validada.toString()));
    mvc.perform(
            get("/api/v1/movements")
                .param("typeStatus", "INVENTADO")
                .with(conPermiso("movements:read")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-006"));

    // Las ventas de mi alcance, vistas por un funcionario: todas.
    UUID funcionario = personaConRol("sa-funcionario", ADMIN);
    mvc.perform(
            get("/api/v1/movements/sales")
                .param("typeStatus", "VALIDAR_COMISIONES")
                .with(user(funcionario.toString()).authorities(() -> "movements:list-sales")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].typeStatus").value("VALIDAR_COMISIONES"));
    mvc.perform(
            get("/api/v1/movements/sales")
                .param("typeStatus", "INVENTADO")
                .with(user(funcionario.toString()).authorities(() -> "movements:list-sales")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));
  }

  @Test
  @DisplayName("CA-MV-162 — «mis compras» no publica el estado del tipo")
  void misComprasNoLoPublica() throws Exception {
    ventaPorValidar();

    // `/mine/shopping` y no `/mine`: el listado se mudo al integrarse `RF-MV-008`
    // (`api/index.md` 1.56.0, cambio incompatible), y `/mine` a secas ya no existe.
    // Lo que esta prueba afirma no cambia por la mudanza: el listado propio no
    // publica el estado del tipo.
    String cuerpo =
        mvc.perform(
                get("/api/v1/movements/mine/shopping")
                    .with(user(cliente.toString()).authorities(() -> "movements:list-own")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(cuerpo).doesNotContain("typeStatus");
  }

  // ---------------------------------------------------------------------------
  // Auxiliares
  // ---------------------------------------------------------------------------

  /** Una venta de los dos bots, registrada por la API a nombre de un cliente con dos vendedores. */
  private UUID ventaPorValidar() throws Exception {
    vincular(cliente, ana, "REGISTRO");
    vincular(cliente, pedro, "HOTLINK");
    UUID venta = registrada(cliente, botA, botB);
    assertThat(estadoDelTipo(venta)).isEqualTo("VALIDAR_COMISIONES");
    return venta;
  }

  private UUID registrada(UUID quien, UUID... productos) throws Exception {
    String cuerpo =
        mvc.perform(registrar(quien, productos))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(com.jayway.jsonpath.JsonPath.read(cuerpo, "$.id"));
  }

  private static MockHttpServletRequestBuilder registrar(UUID quien, UUID... productos) {
    String lineas =
        Arrays.stream(productos)
            .map(p -> "{\"productId\":\"%s\",\"quantity\":1}".formatted(p))
            .collect(Collectors.joining(","));
    return post("/api/v1/movements")
        .with(user(SUPERADMIN.toString()).authorities(() -> "movements:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"userId\":\"%s\",\"paymentMethodId\":\"%s\",\"lines\":[%s]}"
                .formatted(quien, TARJETA, lineas));
  }

  private static String par(UUID producto, UUID vendedor) {
    return "{\"productId\":\"%s\",\"sellerId\":\"%s\"}".formatted(producto, vendedor);
  }

  private static MockHttpServletRequestBuilder asignar(UUID venta, String... pares) {
    return conCuerpo(venta, "{\"lines\":[" + String.join(",", pares) + "]}");
  }

  private static MockHttpServletRequestBuilder conCuerpo(UUID venta, String cuerpo) {
    return post("/api/v1/movements/{id}/seller-assignments", venta)
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private static RequestPostProcessor conPermiso(String permiso) {
    return user(SUPERADMIN.toString()).authorities(() -> permiso);
  }

  private String estadoDelTipo(UUID venta) {
    return jdbc.queryForObject(
        "SELECT s.code FROM movements m JOIN movement_type_statuses s ON s.id = m.type_status_id"
            + " WHERE m.id = ?",
        String.class,
        venta);
  }

  private int sinVendedor() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM movement_details WHERE seller_id IS NULL", Integer.class);
  }

  private void insertarVenta(UUID estado) {
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id, payment_method_id,
                               currency_id, code, status, total_amount, discount_amount,
                               payable_amount, occurred_at)
        VALUES (?, CAST(? AS uuid), ?, ?, CAST(? AS uuid), CAST(? AS uuid), ?, 'PENDIENTE',
                10.00, 0, 10.00, now())
        """,
        UUID.randomUUID(),
        VENTA,
        estado,
        cliente,
        TARJETA,
        USD,
        "VTA-SA-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
  }

  private void vincular(UUID quien, UUID vendedor, String origen) {
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin, first_movement_id, created_at)"
            + " VALUES (?, ?, ?, NULL, now())",
        quien,
        vendedor,
        origen);
  }

  private void limpiar() {
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'MV'");
    jdbc.update(
        "DELETE FROM product_links WHERE product_id IN"
            + " (SELECT id FROM products WHERE code LIKE 'SA\\_%')");
    jdbc.update("DELETE FROM products WHERE code LIKE 'SA\\_%'");
    jdbc.update(
        "DELETE FROM client_sellers WHERE client_id IN (SELECT id FROM users WHERE username LIKE ?)",
        "sa-%");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
        "sa-%");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE ?)",
        "sa-%");
    jdbc.update("DELETE FROM users WHERE username LIKE ?", "sa-%");
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

  private UUID personaConRol(String username, String rol) {
    UUID id = persona(username);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        id,
        rol);
    return id;
  }

  private UUID producto(String codigo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description,"
            + " price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', 'AUTOMATICA', ?, ?, 'BOT', ?, 'x', 10.00, CAST(? AS uuid), 30,"
            + " 'ACTIVO')",
        id,
        codigo,
        "Producto " + codigo,
        USD);
    return id;
  }
}
