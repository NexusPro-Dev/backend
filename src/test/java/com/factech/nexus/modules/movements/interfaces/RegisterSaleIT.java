package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Registrar una venta (`RF-MV-001` · `T-15`).
 *
 * <p>Cubre los criterios de `spec.md` §12 salvo <b>`CA-MV-008`</b>, que no se puede escribir: el
 * estado {@code FTD_PENDIENTE} no existe todavía —lo estrena `RF-SP-045`, que no tiene una línea de
 * código— y {@code ck_users_status} lo rechazaría. La rama que lo comprueba <b>sí está escrita</b>
 * en {@code RegisterSaleService}; lo que falta es el dato que la alcance.
 *
 * <h2>El catálogo y la estructura se siembran POR LA BASE</h2>
 *
 * <p>Hace falta fijar el estado de los productos, su origen y destino, el instante de alta y de
 * quién cuelga cada cliente, y ninguna de esas cosas se puede pasar por HTTP hoy — el registro de
 * clientes por enlace, que es quien los colgaría, es `RF-SP-045`.
 *
 * <h2>La cadena es la que fijó `V47`: 1 es la CIMA</h2>
 *
 * <p>{@code ORO(1) > PLATINO(2) > VIP(3) > FREE(4)}. Subir es ir a un número <b>menor</b>.
 */
@AutoConfigureMockMvc
class RegisterSaleIT extends IntegrationTestBase {

  /** La moneda sembrada por `V15`, estable en todos los entornos. */
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  /** El método de pago sembrado por `V54`. */
  private static final String EFECTIVO = "01a061ba-3400-7002-9c4f-5e7ad7000021";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  /** `RN-MV-016`: prefijo, día de ocho cifras y seis del alfabeto de Crockford. */
  private static final Pattern CODIGO = Pattern.compile("^VTA-\\d{8}-[0-9A-HJKMNP-TV-Z]{6}$");

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID oro;
  private UUID platino;
  private UUID vip;
  private UUID free;

  private UUID upVip;
  private UUID upPlatino;
  private UUID upFree;
  private UUID botSenales;
  private UUID botCopy;
  private UUID botRetirado;
  private UUID botEnOtraMoneda;

  private UUID cliente;
  private UUID clienteSinVendedor;
  private UUID vendedor;
  private UUID otraMoneda;
  private UUID metodoInactivo;

  @BeforeEach
  void sembrar() {
    limpiar();

    // La cadena va encadenada de verdad: `uq_memberships_parent` es UNIQUE
    // NULLS NOT DISTINCT, de modo que solo UNA membresía puede no tener
    // superior. Dos raíces reventarían en el COMMIT, lejos de aquí.
    oro = membresia("VTA_ORO", "Oro de venta", 1, null);
    platino = membresia("VTA_PLATINO", "Platino de venta", 2, oro);
    vip = membresia("VTA_VIP", "Vip de venta", 3, platino);
    free = membresia("VTA_FREE", "Free de venta", 4, vip);
    UUID sotano = membresia("VTA_SOTANO", "Sótano de venta", 5, free);

    upVip = upgrade("VTA_UP_VIP", "Ascenso a Vip", free, vip, "20.00", null, "ACTIVO", false);
    upPlatino =
        upgrade("VTA_UP_PLATINO", "Ascenso a Platino", vip, platino, "50.00", 30, "ACTIVO", false);
    upgrade("VTA_UP_ORO", "Ascenso a Oro", platino, oro, "100.00", 365, "ACTIVO", false);

    // Lleva a FREE, que es el nivel que el cliente YA tiene: la oferta no lo
    // incluye, y es lo que hace verificable `CA-MV-011` de punta a punta.
    upFree = upgrade("VTA_UP_FREE", "Ascenso a Free", sotano, free, "5.00", 7, "ACTIVO", false);

    botSenales = bot("VTA_BOT_SENALES", "Bot de señales", "10.00", null, "ACTIVO", USD, false);
    botCopy = bot("VTA_BOT_COPY", "Bot copiador", "15.50", 90, "ACTIVO", USD, false);
    botRetirado = bot("VTA_BOT_VIEJO", "Bot retirado", "9.00", null, "ACTIVO", USD, true);

    // Una segunda moneda, solo para `CA-MV-014`. El sistema no tiene ninguna
    // tasa de cambio, y esa ausencia es lo que hace que `RN-MV-012` no sea una
    // preferencia sino la única salida posible.
    otraMoneda = moneda("VTC", "Moneda de prueba");
    botEnOtraMoneda =
        bot(
            "VTA_BOT_OTRA",
            "Bot en otra moneda",
            "12.00",
            null,
            "ACTIVO",
            otraMoneda.toString(),
            false);

    vendedor = persona("venta-vendedor");
    cliente = persona("venta-cliente");
    clienteSinVendedor = persona("venta-huerfano");

    asignarMembresia(cliente, free);
    asignarMembresia(clienteSinVendedor, free);
    colgarDe(cliente, vendedor);

    metodoInactivo = metodoDePago("VTA_INACTIVO", "Método retirado", false);
  }

  @AfterEach
  void borrar() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // El camino feliz
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-001 y CA-MV-004: la venta nace PENDIENTE, con su código y sus importes")
  void ventaSimple() throws Exception {
    mvc.perform(venta(cliente, EFECTIVO, linea(botCopy, 2)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDIENTE"))
        .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern(CODIGO)))
        .andExpect(jsonPath("$.lines.length()").value(1))
        .andExpect(jsonPath("$.lines[0].unitPrice").value(15.50))
        .andExpect(jsonPath("$.lines[0].lineAmount").value(31.00))
        .andExpect(jsonPath("$.totalAmount").value(31.00))
        // El descuento se devuelve AUNQUE VALGA SIEMPRE CERO: omitirlo obligaría
        // a añadirlo al contrato el día que exista.
        .andExpect(jsonPath("$.discountAmount").value(0.00))
        .andExpect(jsonPath("$.payableAmount").value(31.00))
        .andExpect(jsonPath("$.currency.code").value("USD"))
        .andExpect(jsonPath("$.paymentMethod").value("EFECTIVO"));
  }

  @Test
  @DisplayName("CA-MV-002: devuelve el vendedor resuelto, que el actor no envió")
  void elVendedorSaleDelCliente() throws Exception {
    mvc.perform(venta(cliente, EFECTIVO, linea(botSenales, 1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.client.id").value(cliente.toString()))
        .andExpect(jsonPath("$.seller.id").value(vendedor.toString()))
        .andExpect(jsonPath("$.seller.username").value("venta-vendedor"))
        // El nombre y no solo el identificador: la respuesta es el único momento
        // en que quien registra ve a quién acaba de atribuirse lo que vendió.
        .andExpect(jsonPath("$.seller.name").value("Ana Ruiz"));
  }

  @Test
  @DisplayName("CA-MV-005: varias líneas, con un upgrade y varios bots (FA-002)")
  void variasLineas() throws Exception {
    mvc.perform(venta(cliente, EFECTIVO, linea(upVip, 1), linea(botSenales, 1), linea(botCopy, 3)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines.length()").value(3))
        // 20.00 + 10.00 + 46.50
        .andExpect(jsonPath("$.totalAmount").value(76.50));
  }

  @Test
  @DisplayName(
      "CA-MV-003: el precio y la vigencia se COPIAN, y corregir el producto después no los cambia")
  void laCopiaSobreviveALaCorreccion() throws Exception {
    mvc.perform(venta(cliente, EFECTIVO, linea(upPlatino, 1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines[0].unitPrice").value(50.00))
        .andExpect(jsonPath("$.lines[0].validityDays").value(30));

    UUID ventaId = jdbc.queryForObject("SELECT id FROM movements", UUID.class);

    // LA COPIA SOLO SE PUEDE VERIFICAR CAMBIANDO EL ORIGINAL. Comparar el precio
    // al registrar no probaría nada: si la venta releyera el catálogo al
    // mostrarse, un precio idéntico pasaría la prueba igual.
    jdbc.update(
        "UPDATE products SET price = 999.00, validity_days = 1 WHERE id = CAST(? AS uuid)",
        upPlatino.toString());

    Map<String, Object> linea =
        jdbc.queryForMap(
            "SELECT unit_price, line_amount, validity_days FROM movement_details"
                + " WHERE movement_id = CAST(? AS uuid)",
            ventaId.toString());

    assertThat((BigDecimal) linea.get("unit_price")).isEqualByComparingTo("50.00");
    assertThat((BigDecimal) linea.get("line_amount")).isEqualByComparingTo("50.00");
    assertThat(linea.get("validity_days")).isEqualTo(30);
  }

  @Test
  @DisplayName("CA-MV-006 y FA-005: el código lleva el día de la FECHA DEL HECHO, no el de hoy")
  void elCodigoLlevaElDiaDelHecho() throws Exception {
    // 03:00 UTC del 12 de julio son las 22:00 del DIA ANTERIOR en Bogotá. Con
    // el corte en UTC el comprobante llevaría el 12, y el papel que se le
    // entrega al cliente diría un día que no es el de la venta.
    String cuerpo =
        ("{\"clientId\":\"%s\",\"paymentMethodId\":\"%s\",\"occurredAt\":\"2026-07-12T03:00:00Z\","
                + "\"lines\":[{\"productId\":\"%s\",\"quantity\":1}]}")
            .formatted(cliente, EFECTIVO, botSenales);

    mvc.perform(
            post("/api/v1/movements")
                .with(comoActor())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("VTA-20260711-")))
        .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern(CODIGO)));
  }

  @Test
  @DisplayName("CA-MV-007: registrar una venta NO cambia el nivel de nadie")
  void registrarNoConcedeNada() throws Exception {
    mvc.perform(venta(cliente, EFECTIVO, linea(upVip, 1))).andExpect(status().isCreated());

    // Es el criterio que sostiene todo el módulo. Sin él, la diferencia entre
    // registrar y confirmar es una palabra en un documento; con él, es algo que
    // falla si alguien la borra.
    UUID nivel =
        jdbc.queryForObject(
            "SELECT membership_id FROM user_memberships WHERE user_id = CAST(? AS uuid)",
            UUID.class,
            cliente.toString());

    assertThat(nivel).isEqualTo(free);
  }

  @Test
  @DisplayName("CA-MV-018: la auditoría guarda la instantánea completa, con el vendedor congelado")
  void laAuditoriaGuardaElVendedor() throws Exception {
    mvc.perform(venta(cliente, EFECTIVO, linea(botCopy, 1))).andExpect(status().isCreated());

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT action, changes::text AS changes FROM audit_change_log"
                + " WHERE module = 'MV' AND entity = 'movements'");

    assertThat(fila.get("action")).isEqualTo("CREATE");
    String cambios = (String) fila.get("changes");
    // Sin el vendedor aquí, «¿por qué esta venta se le atribuyó a esta
    // persona?» solo se puede responder reconstruyendo cómo estaba la
    // estructura comercial ese día.
    assertThat(cambios).contains("\"seller_id\": \"" + vendedor + "\"");
    assertThat(cambios).contains("\"status\": \"PENDIENTE\"");
    assertThat(cambios).contains("\"payable_amount\": \"15.50\"");
    assertThat(cambios).contains("\"lines\"");
  }

  // ---------------------------------------------------------------------------
  // Las negativas
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-009: el cliente inexistente es 422, y se distingue del que no puede operar")
  void clienteInexistente() throws Exception {
    mvc.perform(venta(UUID.randomUUID(), EFECTIVO, linea(botSenales, 1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));
  }

  @Test
  @DisplayName("CA-MV-017: sin vendedor la venta se NIEGA A EXISTIR, en lugar de nacer sin dueño")
  void clienteSinVendedor() throws Exception {
    // `RN-SP-027` promete que esto no ocurre, y se comprueba igual: una promesa
    // de otro módulo no es una comprobación de este.
    mvc.perform(venta(clienteSinVendedor, EFECTIVO, linea(botSenales, 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
  }

  @Test
  @DisplayName("CA-MV-010: el producto inexistente es 422 y el que está fuera de la oferta, 409")
  void productoInexistenteFrenteAFueraDeLaOferta() throws Exception {
    mvc.perform(venta(cliente, EFECTIVO, linea(UUID.randomUUID(), 1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-011"));

    // Retirado: existe, y no se le ofrece a nadie. El mensaje NOMBRA el
    // producto, que es lo que evita probar de uno en uno en una venta de cinco
    // líneas.
    mvc.perform(venta(cliente, EFECTIVO, linea(botRetirado, 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"))
        .andExpect(
            jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("VTA_BOT_VIEJO")));
  }

  @Test
  @DisplayName("CA-MV-011: un upgrade que no sube de nivel se rechaza AL REGISTRAR")
  void elUpgradeQueNoSube() throws Exception {
    // Lleva a FREE, que es el nivel que el cliente ya tiene.
    //
    // HOY LO RECHAZA `EX-004` Y NO `EX-005`, y no es un defecto: la oferta de
    // `RF-PM-007` ya excluye lo que no sube, de modo que la petición no llega a
    // la comprobación de nivel. Lo que este criterio exige es que se rechace AL
    // REGISTRAR —lo único que evita cobrarle a alguien por algo que no le da
    // nada— y eso es lo que se comprueba aquí.
    //
    // Que `RN-MV-006` exista POR SU CUENTA, y no prestada de `PM`, lo prueba
    // `RegisterSaleServiceTest`: allí la oferta se amplía y `EX-005` se
    // alcanza. Las dos pruebas juntas son el argumento del riesgo de
    // `plan.md` §3.2.
    mvc.perform(venta(cliente, EFECTIVO, linea(upFree, 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
  }

  @Test
  @DisplayName("CA-MV-012: dos upgrades en la misma venta")
  void dosUpgrades() throws Exception {
    mvc.perform(venta(cliente, EFECTIVO, linea(upVip, 1), linea(upPlatino, 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));
  }

  @Test
  @DisplayName("CA-MV-013: el producto repetido es 400 y la cantidad en un upgrade, 409")
  void repetidoYCantidadEnUpgrade() throws Exception {
    // `VAL-006` es de ENTRADA aunque `RN-MV-011` sea una regla: la repetición
    // se ve mirando la petición, sin consultar nada.
    mvc.perform(venta(cliente, EFECTIVO, linea(botSenales, 1), linea(botSenales, 2)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-006"));

    mvc.perform(venta(cliente, EFECTIVO, linea(upVip, 2)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-009"));
  }

  @Test
  @DisplayName("CA-MV-014: productos en monedas distintas, sin conversión posible")
  void monedasDistintas() throws Exception {
    mvc.perform(venta(cliente, EFECTIVO, linea(botSenales, 1), linea(botEnOtraMoneda, 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-008"));
  }

  @Test
  @DisplayName("CA-MV-015: el método de pago inexistente es 422 y el desactivado, 409")
  void metodoDePago() throws Exception {
    mvc.perform(venta(cliente, UUID.randomUUID().toString(), linea(botSenales, 1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-010"));

    mvc.perform(venta(cliente, metodoInactivo.toString(), linea(botSenales, 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-010"));
  }

  @Test
  @DisplayName("CA-MV-016: sin líneas, sin cliente y con fecha futura")
  void peticionesMalFormadas() throws Exception {
    mvc.perform(venta(cliente, EFECTIVO)).andExpect(status().isBadRequest());

    mvc.perform(
            post("/api/v1/movements")
                .with(comoActor())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"paymentMethodId\":\"%s\",\"lines\":[{\"productId\":\"%s\",\"quantity\":1}]}"
                        .formatted(EFECTIVO, botSenales)))
        .andExpect(status().isBadRequest());

    // Una venta que aún no ha ocurrido no es un hecho. El pasado remoto sí se
    // admite, que es justo lo que hace falta para registrar lo que ya ocurrió.
    mvc.perform(
            post("/api/v1/movements")
                .with(comoActor())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    ("{\"clientId\":\"%s\",\"paymentMethodId\":\"%s\","
                            + "\"occurredAt\":\"2099-01-01T00:00:00Z\","
                            + "\"lines\":[{\"productId\":\"%s\",\"quantity\":1}]}")
                        .formatted(cliente, EFECTIVO, botSenales)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-007"));
  }

  @Test
  @DisplayName("Sin `movements:create` no se registra nada, aunque el actor pueda leer ventas")
  void sinElPermiso() throws Exception {
    RequestPostProcessor soloLectura =
        user(SUPERADMIN.toString()).authorities(() -> "movements:read");

    mvc.perform(
            post("/api/v1/movements")
                .with(soloLectura)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(cliente.toString(), EFECTIVO, linea(botSenales, 1))))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // Ayudas
  // ---------------------------------------------------------------------------

  private RequestPostProcessor comoActor() {
    return user(SUPERADMIN.toString()).authorities(() -> "movements:create");
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder venta(
      UUID clienteId, String metodo, String... lineas) {
    return post("/api/v1/movements")
        .with(comoActor())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(clienteId.toString(), metodo, lineas));
  }

  private static String cuerpo(String clienteId, String metodo, String... lineas) {
    return "{\"clientId\":\"%s\",\"paymentMethodId\":\"%s\",\"lines\":[%s]}"
        .formatted(clienteId, metodo, String.join(",", lineas));
  }

  private static String linea(UUID producto, int cantidad) {
    return "{\"productId\":\"%s\",\"quantity\":%d}".formatted(producto, cantidad);
  }

  private void limpiar() {
    // El orden lo manda la integridad referencial: las líneas antes que las
    // cabeceras, y las cabeceras antes que los productos y las personas —sus
    // claves foráneas son RESTRICT a propósito, para que un borrado físico no
    // se lleve por delante la atribución de una venta.
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'MV'");
    // El catálogo y la cadena se borran ENTEROS, como en `ProductOfferIT` y por
    // el mismo motivo: `uq_memberships_parent` es UNIQUE NULLS NOT DISTINCT, de
    // modo que solo UNA membresía del sistema puede no tener superior. Dejar en
    // pie la cadena sembrada por `V46` y añadir otra al lado no es posible — la
    // segunda raíz revienta en el COMMIT, lejos de aquí y sin decir por qué.
    jdbc.update("DELETE FROM products");
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'venta-%')");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM users WHERE username LIKE 'venta-%'");
    jdbc.update("DELETE FROM memberships");
    jdbc.update("DELETE FROM payment_methods WHERE code LIKE 'VTA\\_%'");
    jdbc.update("DELETE FROM currencies WHERE code = 'VTC'");
  }

  private UUID membresia(String codigo, String nombre, int nivel, UUID superior) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, parent_membership_id, level, color)"
            + " VALUES (CAST(? AS uuid), ?, ?, CAST(? AS uuid), ?,"
            + " upper(lpad(to_hex(? * 4919), 6, '0')))",
        id.toString(),
        codigo,
        nombre,
        superior == null ? null : superior.toString(),
        nivel,
        nivel);
    return id;
  }

  private UUID upgrade(
      String codigo,
      String nombre,
      UUID origen,
      UUID destino,
      String precio,
      Integer vigencia,
      String estado,
      boolean retirado) {
    return producto(
        codigo,
        "UPGRADE_MEMBRESIA",
        nombre,
        origen,
        destino,
        precio,
        vigencia,
        estado,
        USD,
        retirado);
  }

  private UUID bot(
      String codigo,
      String nombre,
      String precio,
      Integer vigencia,
      String estado,
      String moneda,
      boolean retirado) {
    return producto(codigo, "BOT", nombre, null, null, precio, vigencia, estado, moneda, retirado);
  }

  private UUID producto(
      String codigo,
      String tipo,
      String nombre,
      UUID origen,
      UUID destino,
      String precio,
      Integer vigencia,
      String estado,
      String moneda,
      boolean retirado) {

    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (id, code, type, name, description, source_membership_id,"
            + " target_membership_id, price, currency_id, validity_days, status, created_at,"
            + " updated_at, deleted_at)"
            + " VALUES (CAST(? AS uuid), ?, ?, ?, 'Producto de prueba', CAST(? AS uuid),"
            + " CAST(? AS uuid), CAST(? AS numeric), CAST(? AS uuid), CAST(? AS integer), ?, ?, ?,"
            + " CAST(? AS timestamptz))",
        id.toString(),
        codigo,
        tipo,
        nombre,
        origen == null ? null : origen.toString(),
        destino == null ? null : destino.toString(),
        precio,
        moneda,
        vigencia,
        estado,
        BASE,
        BASE,
        retirado ? BASE.toString() : null);
    return id;
  }

  private UUID persona(String username) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status)
        VALUES (CAST(? AS uuid), ?, ?, 'Ana', 'Ruiz', 'no-se-usa-en-esta-prueba', false, 'ACTIVO')
        """,
        id.toString(),
        username,
        username + "@nexus.test");
    return id;
  }

  private void asignarMembresia(UUID persona, UUID membresia) {
    // Sin `id`: `RN-SP-014` pone `user_id` como clave primaria, de modo que
    // «dos membresías a la vez» es imposible por construcción (`V20`).
    jdbc.update(
        "INSERT INTO user_memberships (user_id, membership_id, started_at, ends_at)"
            + " VALUES (CAST(? AS uuid), CAST(? AS uuid), ?, NULL)",
        persona.toString(),
        membresia.toString(),
        BASE);
  }

  private void colgarDe(UUID persona, UUID superior) {
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at, ended_at)"
            + " VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), ?, NULL)",
        UUID.randomUUID().toString(),
        persona.toString(),
        superior.toString(),
        BASE);
  }

  private UUID moneda(String codigo, String nombre) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)"
            + " VALUES (CAST(? AS uuid), ?, ?, '¤', 2, false, true)",
        id.toString(),
        codigo,
        nombre);
    return id;
  }

  private UUID metodoDePago(String codigo, String nombre, boolean activo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO payment_methods (id, code, name, is_active)"
            + " VALUES (CAST(? AS uuid), ?, ?, ?)",
        id.toString(),
        codigo,
        nombre,
        activo);
    return id;
  }
}
