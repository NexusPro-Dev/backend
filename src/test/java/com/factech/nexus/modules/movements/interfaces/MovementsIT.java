package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * `RF-MV-006` — todos los movimientos.
 *
 * <p><b>Los movimientos se insertan por SQL</b>, como en {@code MyMovementsIT} y por lo mismo: lo
 * que se prueba es una lectura, y montarlos por `RF-MV-001` arrastraría su catálogo entero. Lo que
 * sí se ejercita por HTTP es lo que este requerimiento decide: que el permiso es lo único que abre
 * el libro, los seis filtros y la forma de la fila.
 */
@AutoConfigureMockMvc
class MovementsIT extends IntegrationTestBase {
  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String PSE = "01a061ba-3400-7003-9c4f-5e7ad7000022";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  /** Quien administra: tiene el permiso y NO participa en ningún movimiento. */
  private UUID administrador;

  private UUID vendedor;
  private UUID cliente;
  private UUID otroCliente;
  private UUID producto;

  /** Del vendedor al cliente, pendiente, con tarjeta, el 1 de agosto. */
  private UUID pendiente;

  /** Del vendedor al otro cliente, confirmada, por PSE, el 2 de agosto. */
  private UUID confirmada;

  /** Sin vendedor en su línea, anulada, el 1 de septiembre a medianoche. */
  private UUID sinVendedor;

  @BeforeEach
  void sembrar() {
    limpiar();

    administrador = persona("all-admin");
    vendedor = persona("all-vendedor");
    cliente = persona("all-cliente");
    otroCliente = persona("all-otro");
    producto = producto("ALL_BOT", "Bot del libro");

    pendiente = movimiento(cliente, vendedor, "PENDIENTE", TARJETA, BASE);
    confirmada = movimiento(otroCliente, vendedor, "CONFIRMADA", PSE, BASE.plusDays(1));
    sinVendedor =
        movimiento(
            otroCliente,
            null,
            "ANULADA",
            TARJETA,
            OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC));
  }

  // ---------------------------------------------------------------------------
  // El permiso — lo que sostiene el requerimiento
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-068 — con movements:read se ven movimientos en los que no se participa")
  void elPermisoAbreElLibro() throws Exception {
    mvc.perform(get("/api/v1/movements").with(conPermiso(administrador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalIsExact").value(true))
        .andExpect(jsonPath("$.content.length()").value(3));
  }

  @Test
  @DisplayName("CA-MV-069 — sin el permiso es 403, AUNQUE se tengan movimientos propios")
  void sinPermisoEsProhibido() throws Exception {
    // El vendedor tiene dos movimientos propios y aun así no entra por aquí:
    // lo propio se pregunta por /mine. Si alguien quitara la anotación, esto
    // respondería 200 y lo delataría.
    mvc.perform(get("/api/v1/movements").with(user(vendedor.toString())))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("CA-MV-070 — sin autenticar responde 401")
  void sinAutenticar() throws Exception {
    mvc.perform(get("/api/v1/movements")).andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------
  // Orden y paginación
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-071 — del más reciente al más antiguo, y estable entre páginas")
  void ordenYPaginas() throws Exception {
    mvc.perform(get("/api/v1/movements?page=0&size=2").with(conPermiso(administrador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.content[0].id").value(sinVendedor.toString()))
        .andExpect(jsonPath("$.content[1].id").value(confirmada.toString()));

    mvc.perform(get("/api/v1/movements?page=1&size=2").with(conPermiso(administrador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(pendiente.toString()));
  }

  // ---------------------------------------------------------------------------
  // Los seis filtros
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-072 — el filtro por estado devuelve solo los de ese estado")
  void filtroPorEstado() throws Exception {
    mvc.perform(get("/api/v1/movements?status=pendiente").with(conPermiso(administrador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(pendiente.toString()));
  }

  @Test
  @DisplayName("CA-MV-072 — un estado que no existe es 400 y no una página vacía")
  void estadoInexistente() throws Exception {
    mvc.perform(get("/api/v1/movements?status=INVENTADO").with(conPermiso(administrador)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("status"))
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
  }

  @Test
  @DisplayName("CA-MV-073 — el filtro por sujeto devuelve lo que es a nombre de esa persona")
  void filtroPorSujeto() throws Exception {
    mvc.perform(
            get("/api/v1/movements")
                .param("userId", otroCliente.toString())
                .with(conPermiso(administrador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].id").value(sinVendedor.toString()))
        .andExpect(jsonPath("$.content[1].id").value(confirmada.toString()));
  }

  @Test
  @DisplayName("CA-MV-073 — un sujeto que no existe da una página vacía, no un error")
  void sujetoInexistente() throws Exception {
    mvc.perform(
            get("/api/v1/movements")
                .param("userId", UUID.randomUUID().toString())
                .with(conPermiso(administrador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @Test
  @DisplayName("CA-MV-074 — el filtro por vendedor encuentra sus líneas, y cada venta UNA vez")
  void filtroPorVendedor() throws Exception {
    // Dos líneas del mismo vendedor en la misma venta: es el caso que un JOIN
    // habría multiplicado, y el de toda compra de paquete.
    linea(pendiente, producto("ALL_BOT_2", "Otro bot del libro"), vendedor);

    mvc.perform(
            get("/api/v1/movements")
                .param("sellerId", vendedor.toString())
                .with(conPermiso(administrador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].id").value(confirmada.toString()))
        .andExpect(jsonPath("$.content[1].id").value(pendiente.toString()))
        .andExpect(jsonPath("$.content[1].sellers.length()").value(1));
  }

  @Test
  @DisplayName("CA-MV-075 — el filtro por método de pago")
  void filtroPorMetodo() throws Exception {
    mvc.perform(
            get("/api/v1/movements").param("paymentMethodId", PSE).with(conPermiso(administrador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(confirmada.toString()));
  }

  @Test
  @DisplayName("CA-MV-076 — el filtro por código encuentra el comprobante, escrito como sea")
  void filtroPorCodigo() throws Exception {
    String codigo =
        jdbc.queryForObject("SELECT code FROM movements WHERE id = ?", String.class, pendiente);

    mvc.perform(
            get("/api/v1/movements")
                .param("code", codigo.toLowerCase())
                .with(conPermiso(administrador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(pendiente.toString()))
        .andExpect(jsonPath("$.content[0].code").value(codigo));
  }

  @Test
  @DisplayName("CA-MV-077 — el periodo incluye `from` y excluye `to`")
  void periodoSemiabierto() throws Exception {
    // Agosto entero: el de la medianoche del 1 de septiembre NO cae aquí...
    mvc.perform(
            get("/api/v1/movements")
                .param("from", "2026-08-01T00:00:00Z")
                .param("to", "2026-09-01T00:00:00Z")
                .with(conPermiso(administrador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));

    // ...y sí cae en septiembre, de modo que dos periodos consecutivos no lo
    // cuentan dos veces. Sin `to`: abierto por ese lado.
    mvc.perform(
            get("/api/v1/movements")
                .param("from", "2026-09-01T00:00:00Z")
                .with(conPermiso(administrador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(sinVendedor.toString()));
  }

  @Test
  @DisplayName("CA-MV-077 y VAL-004 — `from` posterior a `to` es 400, junto con el estado")
  void rangoInvertidoYEstadoJuntos() throws Exception {
    // Los problemas se devuelven JUNTOS: dos parámetros mal escritos, dos
    // errores en una sola respuesta.
    mvc.perform(
            get("/api/v1/movements")
                .param("status", "INVENTADO")
                .param("from", "2026-09-01T00:00:00Z")
                .param("to", "2026-08-01T00:00:00Z")
                .with(conPermiso(administrador)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(jsonPath("$.errors[?(@.field == 'status')].code").value("VAL-002"))
        .andExpect(jsonPath("$.errors[?(@.field == 'from')].code").value("VAL-004"));
  }

  @Test
  @DisplayName("VAL-003 — un identificador malformado es 400")
  void identificadorMalformado() throws Exception {
    mvc.perform(
            get("/api/v1/movements").param("sellerId", "1-1-1-1-1").with(conPermiso(administrador)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));
  }

  @Test
  @DisplayName("CA-MV-078 — los filtros se combinan")
  void filtrosCombinados() throws Exception {
    // El otro cliente tiene dos movimientos; con tarjeta, solo uno.
    mvc.perform(
            get("/api/v1/movements")
                .param("userId", otroCliente.toString())
                .param("paymentMethodId", TARJETA)
                .with(conPermiso(administrador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(sinVendedor.toString()));
  }

  // ---------------------------------------------------------------------------
  // Forma de la fila
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-079 a CA-MV-081 — la fila: tipo, partes, importes, confirmación; sin papel ni líneas")
  void formaDeLaFila() throws Exception {
    String cuerpo =
        mvc.perform(get("/api/v1/movements").with(conPermiso(administrador)))
            .andExpect(status().isOk())
            // La confirmada: con fecha de confirmación y con su vendedor.
            .andExpect(jsonPath("$.content[1].id").value(confirmada.toString()))
            .andExpect(jsonPath("$.content[1].type").value("VENTA"))
            .andExpect(jsonPath("$.content[1].status").value("CONFIRMADA"))
            .andExpect(jsonPath("$.content[1].user.username").value("all-otro"))
            .andExpect(jsonPath("$.content[1].sellers.length()").value(1))
            .andExpect(jsonPath("$.content[1].sellers[0].username").value("all-vendedor"))
            .andExpect(jsonPath("$.content[1].currency.code").value("USD"))
            .andExpect(jsonPath("$.content[1].paymentMethod").isNotEmpty())
            .andExpect(jsonPath("$.content[1].totalAmount").value(100.00))
            .andExpect(jsonPath("$.content[1].discountAmount").value(0))
            .andExpect(jsonPath("$.content[1].payableAmount").value(100.00))
            .andExpect(jsonPath("$.content[1].occurredAt").isNotEmpty())
            .andExpect(jsonPath("$.content[1].confirmedAt").isNotEmpty())
            // Lo que NO lleva.
            .andExpect(jsonPath("$.content[1].role").doesNotExist())
            .andExpect(jsonPath("$.content[1].lines").doesNotExist())
            // La anulada sin vendedor: lista vacía y sin confirmación.
            .andExpect(jsonPath("$.content[0].id").value(sinVendedor.toString()))
            .andExpect(jsonPath("$.content[0].sellers").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // EN CRUDO: «nulo» y «vacía» tienen que distinguirse de «ausente». Un
    // jsonPath que pase con la clave ausente no lo probaría.
    assertThat(cuerpo).contains("\"sellers\":[]");
    assertThat(cuerpo).contains("\"confirmedAt\":null");
  }

  // ---------------------------------------------------------------------------
  // Auxiliares
  // ---------------------------------------------------------------------------

  private RequestPostProcessor conPermiso(UUID persona) {
    return user(persona.toString()).authorities(() -> "movements:read");
  }

  private void limpiar() {
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM products WHERE code LIKE 'ALL_%'");
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'all-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'all-%'");
  }

  private UUID persona(String username) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, 'ACTIVO', (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co");
    darElSuelo(jdbc, id);
    return id;
  }

  private UUID producto(String codigo, String nombre) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description, source_membership_id,"
            + " target_membership_id, price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', 'MANUAL', ?, ?, 'BOT', ?, 'Producto de prueba', NULL,"
            + " NULL, CAST(? AS numeric), CAST(? AS uuid), NULL, 'ACTIVO')",
        id,
        codigo,
        nombre,
        "100.00",
        USD);
    return id;
  }

  private UUID movimiento(
      UUID sujeto, UUID vendedor, String estado, String metodo, OffsetDateTime cuando) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, user_id, payment_method_id,
                               currency_id, code, status, total_amount, discount_amount,
                               payable_amount, occurred_at, confirmed_at,
                               voided_at, void_reason)
        VALUES (?, CAST(? AS uuid), ?, CAST(? AS uuid), CAST(? AS uuid), ?, ?,
                100.00, 0, 100.00, CAST(? AS timestamptz),
                CASE WHEN ? = 'CONFIRMADA' THEN CAST(? AS timestamptz) ELSE NULL END,
                -- `ck_movements_voided`: una anulada lleva fecha y motivo, y solo ella.
                CASE WHEN ? = 'ANULADA' THEN now() ELSE NULL END,
                CASE WHEN ? = 'ANULADA' THEN 'Sembrada anulada' ELSE NULL END)
        """,
        id,
        VENTA,
        sujeto,
        metodo,
        USD,
        "VTA-" + id.toString().substring(0, 8).toUpperCase(),
        estado,
        cuando.toString(),
        // `ck_movements_confirmed` ata las dos columnas: confirmada implica
        // fecha de confirmación, y al revés.
        estado,
        cuando.toString(),
        estado,
        estado);

    linea(id, producto, vendedor);
    return id;
  }

  private void linea(UUID movimiento, UUID producto, UUID vendedor) {
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                      product_description, quantity, unit_price,
                                      line_amount, validity_days, implementation)
        VALUES (?, ?, ?, ?, 'Bot de prueba', 'Lo que decia el catalogo', 1, 100.00, 100.00, NULL,
                'AUTOMATICA')
        """,
        UUID.randomUUID(),
        movimiento,
        producto,
        vendedor);
  }
}
