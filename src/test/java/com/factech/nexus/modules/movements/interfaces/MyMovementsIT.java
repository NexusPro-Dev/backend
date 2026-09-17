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
 * `RF-MV-008` — los movimientos propios.
 *
 * <p><b>Los movimientos se insertan por SQL y no se registran por la API.</b> Lo que este
 * requerimiento hace es <b>leer</b>, y montarlos con `RF-MV-001` obligaría a arrastrar su catálogo
 * entero —productos, vigencias, reglas de composición— para probar una consulta. Lo que sí se
 * ejercita por HTTP es todo lo que este requerimiento decide: el alcance, el papel y el detalle.
 */
@AutoConfigureMockMvc
class MyMovementsIT extends IntegrationTestBase {
  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID vendedor;
  private UUID cliente;
  private UUID ajeno;
  private UUID producto;

  /** La venta del vendedor al cliente. */
  private UUID vendida;

  /** La que el vendedor se compró a sí mismo: es cliente Y vendedor. */
  private UUID propia;

  /**
   * Una en la que no participa ninguno de los dos, y SIN vendedor en su línea. Ninguna venta se
   * registra así desde el 16-09-2026 (`RN-MV-003`); se siembra en crudo porque es la forma que
   * tendrán los tipos de movimiento que no venden nada, y `FA-003` la declara.
   */
  private UUID deOtros;

  @BeforeEach
  void sembrar() {
    limpiar();

    vendedor = persona("mine-vendedor");
    cliente = persona("mine-cliente");
    ajeno = persona("mine-ajeno");
    producto = producto();

    vendida = movimiento(cliente, vendedor, "PENDIENTE", BASE);
    propia = movimiento(vendedor, vendedor, "CONFIRMADA", BASE.plusDays(1));
    deOtros = movimiento(ajeno, null, "PENDIENTE", BASE.plusDays(2));
  }

  // ---------------------------------------------------------------------------
  // Los tres papeles
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-035 y CA-MV-037 — el vendedor ve lo que vendió y lo suyo, con su papel")
  void losPapelesDelVendedor() throws Exception {
    mvc.perform(get("/api/v1/movements/mine").with(como(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        // El orden es del más reciente al más antiguo: primero la propia.
        .andExpect(jsonPath("$.content[0].id").value(propia.toString()))
        .andExpect(jsonPath("$.content[0].role").value("BOTH"))
        .andExpect(jsonPath("$.content[1].id").value(vendida.toString()))
        .andExpect(jsonPath("$.content[1].role").value("SELLER"));
  }

  @Test
  @DisplayName("CA-MV-036 — el comprador ve lo que compró, con papel BUYER")
  void elPapelDelComprador() throws Exception {
    mvc.perform(get("/api/v1/movements/mine").with(como(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(vendida.toString()))
        .andExpect(jsonPath("$.content[0].role").value("BUYER"));
  }

  @Test
  @DisplayName("CA-MV-037 — quien es las dos cosas lo ve UNA sola vez")
  void ambosNoDuplica() throws Exception {
    // Es el caso que un `UNION` habría duplicado, y el que el `CASE` habría
    // resuelto mal con la rama de `BOTH` escrita al final.
    mvc.perform(get("/api/v1/movements/mine?status=CONFIRMADA").with(como(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].role").value("BOTH"));
  }

  // ---------------------------------------------------------------------------
  // El alcance — lo que sostiene el requerimiento
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-038 — no aparece nada ajeno, NI SIQUIERA con movements:read")
  void elPermisoDeAdministracionNoAmplia() throws Exception {
    // CON EL PERMISO PUESTO, a propósito. Si algún día alguien conecta
    // `movements:read` a esta ruta, esto lo delata en lugar de dejar que ocurra
    // por omisión — que es la única forma en que este requerimiento se rompe.
    mvc.perform(get("/api/v1/movements/mine").with(conPermisoDeLectura(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[?(@.id == '" + deOtros + "')]").isEmpty());
  }

  @Test
  @DisplayName("CA-MV-039 — quien no participó en ninguno recibe una página vacía, no un error")
  void sinMovimientos() throws Exception {
    UUID recienLlegado = persona("mine-nuevo");

    mvc.perform(get("/api/v1/movements/mine").with(como(recienLlegado)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  // ---------------------------------------------------------------------------
  // Forma de la respuesta
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-040 y CA-MV-043 — va envuelto, y trae al sujeto y a sus vendedores")
  void formaDeLaFila() throws Exception {
    mvc.perform(get("/api/v1/movements/mine").with(como(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").isNumber())
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.content[0].user.username").value("mine-cliente"))
        // Los vendedores son de las líneas y van SIN REPETIR: una lista, porque
        // una venta podría llevar varios. Hoy lleva uno.
        .andExpect(jsonPath("$.content[0].sellers.length()").value(1))
        .andExpect(jsonPath("$.content[0].sellers[0].username").value("mine-vendedor"))
        .andExpect(jsonPath("$.content[0].client").doesNotExist())
        .andExpect(jsonPath("$.content[0].seller").doesNotExist())
        .andExpect(jsonPath("$.content[0].currency.code").value("USD"))
        .andExpect(jsonPath("$.content[0].paymentMethod").isNotEmpty())
        .andExpect(jsonPath("$.content[0].payableAmount").value(100.00))
        // Las líneas NO viajan en el listado: están en el detalle.
        .andExpect(jsonPath("$.content[0].lines").doesNotExist());
  }

  @Test
  @DisplayName("CA-MV-043 — la lista de vendedores viaja VACÍA Y PRESENTE cuando no hay ninguno")
  void movimientoSinVendedor() throws Exception {
    String cuerpo =
        mvc.perform(get("/api/v1/movements/mine").with(como(ajeno)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].role").value("BUYER"))
            .andExpect(jsonPath("$.content[0].sellers").isArray())
            .andExpect(jsonPath("$.content[0].sellers").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // EN CRUDO: «no hay vendedor» tiene que distinguirse de «este endpoint no
    // informa del vendedor», y la lista vacía lo dice sin que nadie interprete
    // un nulo. `isEmpty()` sobre un jsonPath pasaría también con la clave ausente.
    assertThat(cuerpo).contains("\"sellers\":[]");
  }

  @Test
  @DisplayName("CA-MV-037 — la venta con dos líneas del mismo vendedor cuenta UNA vez")
  void variasLineasNoMultiplican() throws Exception {
    // Es el caso que un JOIN con las líneas habría multiplicado: el EXISTS deja
    // una fila por movimiento, y la lista de vendedores no repite.
    segundaLinea(vendida, vendedor);

    mvc.perform(get("/api/v1/movements/mine").with(como(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[1].id").value(vendida.toString()))
        .andExpect(jsonPath("$.content[1].sellers.length()").value(1));
  }

  @Test
  @DisplayName("CA-MV-041 — el orden es estable entre páginas")
  void ordenEstable() throws Exception {
    mvc.perform(get("/api/v1/movements/mine?page=0&size=1").with(como(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.content[0].id").value(propia.toString()));

    mvc.perform(get("/api/v1/movements/mine?page=1&size=1").with(como(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(vendida.toString()));
  }

  @Test
  @DisplayName("CA-MV-042 — el filtro por estado devuelve solo los de ese estado")
  void filtroPorEstado() throws Exception {
    mvc.perform(get("/api/v1/movements/mine?status=PENDIENTE").with(como(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(vendida.toString()));
  }

  @Test
  @DisplayName("VAL-003 — un estado que no existe es 400 y no una página vacía")
  void estadoInexistente() throws Exception {
    // La diferencia importa: una página vacía diría «no tienes ninguno así», que
    // es una respuesta falsa a una pregunta mal escrita.
    mvc.perform(get("/api/v1/movements/mine?status=INVENTADO").with(como(vendedor)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
  }

  // ---------------------------------------------------------------------------
  // El detalle
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-044 — el detalle propio devuelve sus líneas")
  void detallePropio() throws Exception {
    mvc.perform(get("/api/v1/movements/mine/{id}", vendida).with(como(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(vendida.toString()))
        .andExpect(jsonPath("$.code").isNotEmpty())
        .andExpect(jsonPath("$.status").value("PENDIENTE"))
        .andExpect(jsonPath("$.lines.length()").value(1))
        .andExpect(jsonPath("$.lines[0].productCode").value("MINE_BOT"))
        // EL NOMBRE Y LA DESCRIPCION SALEN DE LA LINEA (`RN-MV-002`): son copias,
        // y por eso el producto del catálogo puede renombrarse sin cambiar esto.
        .andExpect(jsonPath("$.lines[0].productName").value("Bot de prueba"))
        .andExpect(jsonPath("$.lines[0].productDescription").value("Lo que decia el catalogo"))
        .andExpect(jsonPath("$.lines[0].quantity").value(1))
        .andExpect(jsonPath("$.lines[0].unitPrice").value(100.00))
        // El vendedor es de la línea (`RN-MV-003`), y el detalle lo trae ahí.
        .andExpect(jsonPath("$.lines[0].seller.username").value("mine-vendedor"))
        .andExpect(jsonPath("$.user.username").value("mine-cliente"));
  }

  @Test
  @DisplayName("CA-MV-045 y EX-002 — el detalle AJENO responde 404, igual que uno inexistente")
  void detalleAjeno() throws Exception {
    // LAS DOS RESPUESTAS SON LA MISMA, y esa es la decisión: un `403` diría
    // «existe pero no es tuyo», y con un identificador que alguien esté probando
    // eso ya es información.
    mvc.perform(get("/api/v1/movements/mine/{id}", deOtros).with(como(vendedor)))
        .andExpect(status().isNotFound());

    mvc.perform(get("/api/v1/movements/mine/{id}", UUID.randomUUID()).with(como(vendedor)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CA-MV-045 — tener movements:read tampoco abre el detalle ajeno")
  void detalleAjenoConPermiso() throws Exception {
    mvc.perform(get("/api/v1/movements/mine/{id}", deOtros).with(conPermisoDeLectura(vendedor)))
        .andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------------------
  // Rutas y acceso
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-046 — responde a cualquier autenticado, sin exigir permiso")
  void sinPermisoResponde() throws Exception {
    mvc.perform(get("/api/v1/movements/mine").with(user(cliente.toString())))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("CA-MV-047 — sin autenticar responde 401")
  void sinAutenticar() throws Exception {
    mvc.perform(get("/api/v1/movements/mine")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/movements/mine/{id}", vendida)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("`mine` no lo captura una variable de ruta")
  void mineNoEsUnIdentificador() throws Exception {
    // Hoy este controlador no tiene `/{id}`, pero `RF-MV-007` lo traerá. El
    // síntoma de romperlo sería un `400` por identificador inválido en la ruta
    // que más se usa, y esta prueba lo convierte en un fallo con nombre.
    mvc.perform(get("/api/v1/movements/mine").with(como(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  // ---------------------------------------------------------------------------
  // Auxiliares
  // ---------------------------------------------------------------------------

  private RequestPostProcessor como(UUID persona) {
    return user(persona.toString());
  }

  private RequestPostProcessor conPermisoDeLectura(UUID persona) {
    return user(persona.toString()).authorities(() -> "movements:read");
  }

  private void limpiar() {
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM products WHERE code LIKE 'MINE_%'");
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'mine-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'mine-%'");
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

  private UUID producto() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description, source_membership_id,"
            + " target_membership_id, price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', 'MANUAL', ?, 'MINE_BOT', 'BOT', 'Bot de prueba', 'Producto de prueba', NULL,"
            + " NULL, CAST(? AS numeric), CAST(? AS uuid), NULL, 'ACTIVO')",
        id,
        "100.00",
        USD);
    return id;
  }

  private UUID movimiento(UUID cliente, UUID vendedor, String estado, OffsetDateTime cuando) {
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
        cliente,
        TARJETA,
        USD,
        "VTA-" + id.toString().substring(0, 8).toUpperCase(),
        estado,
        cuando.toString(),
        // `ck_movements_confirmed` ata las dos columnas: confirmada implica fecha de
        // confirmacion, y al reves. Ponerla siempre —o nunca— hace fallar la mitad.
        estado,
        cuando.toString());

    linea(id, producto, vendedor);
    return id;
  }

  /** Una segunda línea, de otro producto, para el mismo vendedor. */
  private void segundaLinea(UUID movimiento, UUID vendedor) {
    UUID otro = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description, source_membership_id,"
            + " target_membership_id, price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', 'MANUAL', ?, 'MINE_BOT_2', 'BOT', 'Otro bot', 'Producto de prueba', NULL,"
            + " NULL, CAST(? AS numeric), CAST(? AS uuid), NULL, 'ACTIVO')",
        otro,
        "100.00",
        USD);
    linea(movimiento, otro, vendedor);
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
