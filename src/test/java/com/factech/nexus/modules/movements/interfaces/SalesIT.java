package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * `RF-MV-015` · `T-07` — las ventas de mi alcance (`CA-MV-122` a `CA-MV-131`).
 *
 * <p><b>Un árbol con dos ramas y UNA venta por vendedor</b>, para que cada nivel vea exactamente su
 * conjunto y ningún error de recorrido dé un número plausible:
 *
 * <pre>
 *   manager ─┬─ director1 ─┬─ agente1
 *            │             └─ agente2
 *            └─ director2 ─── agente3
 *   suelto (agente sin superior)   exagente (colgó de director1 y ya no)
 *   funcionario (ADMIN)            cliente, otroCliente (CLIENTE)
 * </pre>
 *
 * <p>Y una compra de {@code agente1} vendida por {@code director1}, para separar «lo que vendí» de
 * «lo que compré»: aquí solo lo primero.
 */
@AutoConfigureMockMvc
class SalesIT extends IntegrationTestBase {
  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String PSE = "01a061ba-3400-7003-9c4f-5e7ad7000022";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000003";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";
  private static final String CLIENTE = "01a02a33-4c00-7008-9c4f-5e7ad1000008";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

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
  private UUID otroCliente;
  private UUID producto;
  private UUID otroProducto;

  private UUID vManager;
  private UUID vDirector1;
  private UUID vAgente1;
  private UUID vAgente2;
  private UUID vDirector2;
  private UUID vAgente3;
  private UUID vSuelto;
  private UUID vExagente;

  /** Lo que agente1 COMPRÓ, vendido por director1, con dos líneas del mismo vendedor. */
  private UUID compraDeAgente1;

  @BeforeEach
  void sembrar() {
    limpiar();

    funcionario = persona("sales-funcionario", ADMIN);
    manager = persona("sales-manager", MANAGER);
    director1 = persona("sales-director1", DIRECTOR);
    director2 = persona("sales-director2", DIRECTOR);
    agente1 = persona("sales-agente1", AGENTE);
    agente2 = persona("sales-agente2", AGENTE);
    agente3 = persona("sales-agente3", AGENTE);
    suelto = persona("sales-suelto", AGENTE);
    exagente = persona("sales-exagente", AGENTE);
    cliente = persona("sales-cliente", CLIENTE);
    otroCliente = persona("sales-otro", CLIENTE);
    producto = producto("SALES_BOT");
    otroProducto = producto("SALES_BOT_2");

    reportar(director1, manager);
    reportar(director2, manager);
    reportar(agente1, director1);
    reportar(agente2, director1);
    reportar(agente3, director2);
    jdbc.update(
        """
        INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at, ended_at)
        VALUES (gen_random_uuid(), ?, ?, now() - interval '30 days', now() - interval '1 day')
        """,
        exagente,
        director1);

    vManager = venta(cliente, manager, "PENDIENTE", BASE);
    vDirector1 = venta(cliente, director1, "PENDIENTE", BASE.plusDays(1));
    vAgente1 = venta(cliente, agente1, "PENDIENTE", BASE.plusDays(2));
    vAgente2 = venta(otroCliente, agente2, "CONFIRMADA", BASE.plusDays(3));
    vDirector2 = venta(otroCliente, director2, "PENDIENTE", BASE.plusDays(4));
    vAgente3 = venta(otroCliente, agente3, "PENDIENTE", BASE.plusDays(5));
    vSuelto = venta(otroCliente, suelto, "PENDIENTE", BASE.plusDays(6));
    vExagente = venta(cliente, exagente, "PENDIENTE", BASE.plusDays(7));
    compraDeAgente1 = venta(agente1, director1, "CONFIRMADA", BASE.plusDays(8));
    // La segunda línea es de OTRO producto —`uq_movement_details_product` no
    // admite dos del mismo— y del mismo vendedor: cuenta una vez.
    linea(compraDeAgente1, otroProducto, director1);
  }

  @AfterEach
  void devolverLaBaseASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // El alcance por tipo de rol — lo que sostiene el requerimiento
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-122 — un consumidor ve solo las ventas a su nombre")
  void consumidorSoloLoSuyo() throws Exception {
    assertThat(ids(mvc.perform(ventas(cliente))))
        .containsExactlyInAnyOrder(vManager, vDirector1, vAgente1, vExagente);
  }

  @Test
  @DisplayName(
      "CA-MV-123 — un agente sin nadie a cargo ve lo que vendió él: ni lo que compró ni lo del vecino")
  void agenteSoloLoQueVendio() throws Exception {
    assertThat(ids(mvc.perform(ventas(agente1)))).containsExactly(vAgente1);
    assertThat(ids(mvc.perform(ventas(suelto)))).containsExactly(vSuelto);
  }

  @Test
  @DisplayName("CA-MV-124 — un director ve lo suyo y lo de sus agentes, no lo del otro director")
  void directorVeSuRama() throws Exception {
    assertThat(ids(mvc.perform(ventas(director1))))
        .containsExactlyInAnyOrder(vDirector1, vAgente1, vAgente2, compraDeAgente1)
        .doesNotContain(vDirector2, vAgente3, vManager, vSuelto, vExagente);
    assertThat(ids(mvc.perform(ventas(director2)))).containsExactlyInAnyOrder(vDirector2, vAgente3);
  }

  @Test
  @DisplayName("CA-MV-125 — un manager ve la profundidad entera, y no al suelto ni al que se fue")
  void managerVeTodaLaRed() throws Exception {
    assertThat(ids(mvc.perform(ventas(manager))))
        .containsExactlyInAnyOrder(
            vManager, vDirector1, vAgente1, vAgente2, vDirector2, vAgente3, compraDeAgente1)
        .doesNotContain(vSuelto, vExagente);
  }

  @Test
  @DisplayName(
      "CA-MV-126 — un funcionario ve todas las ventas, también las de quien no cuelga de nadie")
  void funcionarioVeTodo() throws Exception {
    assertThat(ids(mvc.perform(ventas(funcionario)))).hasSize(9).contains(vSuelto, vExagente);
  }

  // ---------------------------------------------------------------------------
  // El filtro por persona — lo que protege el requerimiento
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-127 — userId dentro del alcance acota; fuera, inexistente o ajeno, página vacía")
  void filtroPorPersona() throws Exception {
    assertThat(ids(mvc.perform(ventas(director1).param("userId", agente1.toString()))))
        .containsExactly(vAgente1);
    assertThat(ids(mvc.perform(ventas(manager).param("userId", agente3.toString()))))
        .containsExactly(vAgente3);

    // Fuera de mi red: vacío, 200, y no un 403 ni un 404 que confirmen la estructura.
    mvc.perform(ventas(director1).param("userId", agente3.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.totalIsExact").value(true));
    mvc.perform(ventas(director1).param("userId", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    // Para el consumidor, cualquiera que no sea él.
    mvc.perform(ventas(cliente).param("userId", manager.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    // Y quien administra puede pedir a cualquiera.
    assertThat(ids(mvc.perform(ventas(funcionario).param("userId", suelto.toString()))))
        .containsExactly(vSuelto);
  }

  @Test
  @DisplayName(
      "CA-MV-128 — quien dejó de colgar de mí deja de aparecer, con lo que vendió mientras colgaba")
  void quienSeFueDesaparece() throws Exception {
    assertThat(ids(mvc.perform(ventas(director1)))).doesNotContain(vExagente);
    // Ni pidiéndolo por su identificador: ya no es de mi red.
    mvc.perform(ventas(director1).param("userId", exagente.toString()))
        .andExpect(jsonPath("$.totalElements").value(0));
    // Él sí ve la suya: la raíz se incluye aunque no cuelgue de nadie hoy.
    assertThat(ids(mvc.perform(ventas(exagente)))).containsExactly(vExagente);
  }

  @Test
  @DisplayName(
      "CA-MV-129 — estado y periodo acotan dentro del alcance y se combinan; los errores, juntos")
  void filtrosCombinados() throws Exception {
    assertThat(ids(mvc.perform(ventas(manager).param("status", "confirmada"))))
        .containsExactlyInAnyOrder(vAgente2, compraDeAgente1);
    mvc.perform(ventas(manager).param("status", "PENDIENTE").param("userId", agente2.toString()))
        .andExpect(jsonPath("$.totalElements").value(0));
    assertThat(
            ids(
                mvc.perform(
                    ventas(manager)
                        .param("from", BASE.plusDays(4).toString())
                        .param("to", BASE.plusDays(6).toString()))))
        .containsExactlyInAnyOrder(vDirector2, vAgente3);

    mvc.perform(
            ventas(manager)
                .param("status", "INVENTADO")
                .param("from", BASE.plusDays(2).toString())
                .param("to", BASE.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(jsonPath("$.errors[?(@.field == 'status')].code").value("VAL-002"))
        .andExpect(jsonPath("$.errors[?(@.field == 'from')].code").value("VAL-004"));
  }

  @Test
  @DisplayName(
      "CA-MV-136 — método de pago y código acotan DENTRO del alcance; el comprobante ajeno da vacío")
  void metodoYCodigoDentroDelAlcance() throws Exception {
    jdbc.update(
        "UPDATE movements SET payment_method_id = CAST(? AS uuid) WHERE id = ?", PSE, vAgente3);
    assertThat(ids(mvc.perform(ventas(manager).param("paymentMethodId", PSE))))
        .containsExactly(vAgente3);
    // Para director1, agente3 no es de su red: el método no le enseña nada.
    mvc.perform(ventas(director1).param("paymentMethodId", PSE))
        .andExpect(jsonPath("$.totalElements").value(0));

    String deLosMios =
        jdbc.queryForObject("SELECT code FROM movements WHERE id = ?", String.class, vAgente1);
    String ajeno =
        jdbc.queryForObject("SELECT code FROM movements WHERE id = ?", String.class, vSuelto);
    assertThat(ids(mvc.perform(ventas(director1).param("code", deLosMios.toLowerCase()))))
        .containsExactly(vAgente1);
    mvc.perform(ventas(director1).param("code", ajeno))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    assertThat(ids(mvc.perform(ventas(funcionario).param("code", ajeno)))).containsExactly(vSuelto);
    // Y combinados con el estado y la persona.
    mvc.perform(
            ventas(manager)
                .param("code", deLosMios)
                .param("status", "PENDIENTE")
                .param("userId", agente1.toString()))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  // ---------------------------------------------------------------------------
  // El permiso
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-130 — sin movements:list-sales es 403, también con movements:read o list-own; sin token, 401")
  void elPermisoEsElUnicoQueAbre() throws Exception {
    mvc.perform(get("/api/v1/movements/sales").with(user(manager.toString())))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/movements/sales")
                .with(user(funcionario.toString()).authorities(() -> "movements:read")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/movements/sales")
                .with(user(agente1.toString()).authorities(() -> "movements:list-own")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/movements/sales")).andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------
  // La forma y el orden
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-131 — la fila es la de GET /movements, sin papel; dos líneas del mismo vendedor cuentan una vez")
  void formaDeLaFila() throws Exception {
    String cuerpo =
        mvc.perform(ventas(director1).param("userId", director1.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].id").value(compraDeAgente1.toString()))
            .andExpect(jsonPath("$.content[0].type").value("VENTA"))
            .andExpect(jsonPath("$.content[0].status").value("CONFIRMADA"))
            .andExpect(jsonPath("$.content[0].user.username").value("sales-agente1"))
            .andExpect(jsonPath("$.content[0].sellers.length()").value(1))
            .andExpect(jsonPath("$.content[0].sellers[0].username").value("sales-director1"))
            .andExpect(jsonPath("$.content[0].confirmedAt").isNotEmpty())
            .andExpect(jsonPath("$.content[0].role").doesNotExist())
            .andExpect(jsonPath("$.content[0].lines").doesNotExist())
            .andExpect(jsonPath("$.content[1].id").value(vDirector1.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(cuerpo).contains("\"confirmedAt\":null");
  }

  @Test
  @DisplayName("CA-MV-132 — paginado, del más reciente al más antiguo y estable entre páginas")
  void ordenYPaginacion() throws Exception {
    List<UUID> primera = ids(mvc.perform(ventas(manager).param("size", "3").param("page", "0")));
    List<UUID> segunda = ids(mvc.perform(ventas(manager).param("size", "3").param("page", "1")));
    List<UUID> tercera = ids(mvc.perform(ventas(manager).param("size", "3").param("page", "2")));

    assertThat(primera).containsExactly(compraDeAgente1, vAgente3, vDirector2);
    assertThat(segunda).containsExactly(vAgente2, vAgente1, vDirector1);
    assertThat(tercera).containsExactly(vManager);
  }

  // ---------------------------------------------------------------------------
  // Auxiliares
  // ---------------------------------------------------------------------------

  private static MockHttpServletRequestBuilder ventas(UUID actor) {
    // Sin tamano: el de serie (veinte) cubre las nueve ventas, y la prueba de
    // paginacion pone el suyo.
    return get("/api/v1/movements/sales").with(conPermiso(actor));
  }

  private static RequestPostProcessor conPermiso(UUID persona) {
    return user(persona.toString()).authorities(() -> "movements:list-sales");
  }

  private List<UUID> ids(org.springframework.test.web.servlet.ResultActions resultado)
      throws Exception {
    JsonNode raiz =
        json.readTree(
            resultado.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    List<UUID> ids = new ArrayList<>();
    for (JsonNode fila : raiz.get("content")) {
      ids.add(UUID.fromString(fila.get("id").asText()));
    }
    return ids;
  }

  private void limpiar() {
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM products WHERE code LIKE 'SALES_BOT%'");
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'sales-%') OR supervisor_id IN (SELECT id FROM users WHERE username LIKE 'sales-%')");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'sales-%')");
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'sales-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'sales-%'");
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
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        id,
        rol);
    return id;
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

  private UUID producto(String codigo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description, source_membership_id,"
            + " target_membership_id, price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', 'MANUAL', ?, ?, 'BOT', ?, 'Producto de prueba', NULL,"
            + " NULL, CAST(? AS numeric), CAST(? AS uuid), NULL, 'ACTIVO')",
        id,
        codigo,
        "Bot " + codigo,
        "100.00",
        USD);
    return id;
  }

  private UUID venta(UUID sujeto, UUID vendedor, String estado, OffsetDateTime cuando) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, user_id, payment_method_id,
                               currency_id, code, status, total_amount, discount_amount,
                               payable_amount, occurred_at, confirmed_at)
        VALUES (?, CAST(? AS uuid), ?, CAST(? AS uuid), CAST(? AS uuid), ?, ?,
                100.00, 0, 100.00, CAST(? AS timestamptz),
                CASE WHEN ? = 'CONFIRMADA' THEN CAST(? AS timestamptz) ELSE NULL END)
        """,
        id,
        VENTA,
        sujeto,
        TARJETA,
        USD,
        "VTA-" + id.toString().substring(0, 8).toUpperCase(),
        estado,
        cuando.toString(),
        estado,
        cuando.toString());
    linea(id, producto, vendedor);
    return id;
  }

  private void linea(UUID movimiento, UUID producto, UUID vendedor) {
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                      product_description, quantity, unit_price,
                                      line_amount, validity_days, implementation)
        VALUES (?, ?, ?, ?, 'Bot de ventas', 'Lo que decia el catalogo', 1, 100.00, 100.00, NULL,
                'MANUAL')
        """,
        UUID.randomUUID(),
        movimiento,
        producto,
        vendedor);
  }
}
