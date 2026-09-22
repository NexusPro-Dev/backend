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
 * <p>Cubre los criterios de `spec.md` §12, <b>`CA-MV-008` incluido desde el 09-09-2026</b>. Hasta
 * entonces era el único que no se podía escribir: el estado {@code FTD_PENDIENTE} no existía —lo
 * estrenaba `RF-SP-045`, que no tenía una línea de código— y {@code ck_users_status} lo rechazaba.
 * `V77` lo admite y el registro por enlace lo produce, de modo que la rama dejó de ser
 * inalcanzable.
 *
 * <h2>El catálogo y la estructura se siembran POR LA BASE</h2>
 *
 * <p>Hace falta fijar el estado de los productos, su origen y destino, el instante de alta y de
 * quién cuelga cada cliente, y ninguna de esas cosas se puede pasar por HTTP hoy — el registro de
 * clientes por enlace, que es quien los colgaría, es `RF-SP-045`.
 *
 * <h2>La cadena es la que fijó `V47`: 1 es la CIMA</h2>
 *
 * <p>{@code ORO(1) > PLATINO(2) > VIP(3) > BECA(4)}. Subir es ir a un número <b>menor</b>.
 */
@AutoConfigureMockMvc
class RegisterSaleIT extends IntegrationTestBase {

  /** La moneda sembrada por `V15`, estable en todos los entornos. */
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  /** El método de pago sembrado por `V54`. */
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";

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
        upgrade("VTA_UP_PLATINO", "Ascenso a Platino", free, platino, "50.00", 30, "ACTIVO", false);
    // DECLARADO DESDE `free` COMO LOS DEMÁS: desde el 07-09-2026 la oferta
    // coincide por ORIGEN y no por nivel (`RF-PM-007` · `T-20`), de modo que un
    // producto declarado desde `vip` no estaría en la oferta del cliente —que
    // está en `free`— y `RN-MV-007` lo rechazaría antes de llegar a la regla
    // que cada prueba quiere ejercitar. Es un SALTO, que `RN-PM-018` admite.
    upgrade("VTA_UP_ORO", "Ascenso a Oro", free, oro, "100.00", 365, "ACTIVO", false);

    // Lleva a BECA, que es el nivel que el cliente YA tiene: la oferta no lo
    // incluye —su ORIGEN es `sotano`, no `free`—, y es lo que hace verificable
    // `EX-004` de punta a punta. La mitad de `CA-MV-011` que se comprueba aquí
    // dejó de ser «el mismo nivel» el 07-09-2026: eso es una RENOVACIÓN y se
    // admite; lo que sigue sin admitirse es la BAJADA, y esa vive en
    // `RegisterSaleServiceTest`, donde la oferta se amplía a mano.
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
    String cuerpo =
        mvc.perform(venta(cliente, TARJETA, linea(botCopy, 2)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDIENTE"))
            .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern(CODIGO)))
            .andExpect(jsonPath("$.lines.length()").value(1))
            .andExpect(jsonPath("$.lines[0].unitPrice").value(15.50))
            // El nombre y la descripción son COPIAS de la línea (`RN-MV-002`).
            .andExpect(jsonPath("$.lines[0].productName").isNotEmpty())
            .andExpect(jsonPath("$.lines[0].packageId").doesNotExist())
            .andExpect(jsonPath("$.lines[0].lineDiscount").value(0.00))
            .andExpect(jsonPath("$.lines[0].lineAmount").value(31.00))
            .andExpect(jsonPath("$.totalAmount").value(31.00))
            // El descuento se devuelve AUNQUE VALGA SIEMPRE CERO: omitirlo obligaría
            // a añadirlo al contrato el día que exista.
            .andExpect(jsonPath("$.discountAmount").value(0.00))
            .andExpect(jsonPath("$.payableAmount").value(31.00))
            .andExpect(jsonPath("$.currency.code").value("USD"))
            .andExpect(jsonPath("$.paymentMethod").value("CREDIT_CARD"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // `RN-MV-027`: esta entrada no aplica descuentos, y la forma viaja igual —
    // lista vacía PRESENTE, comprobado sobre el JSON en crudo. Y el paquete va
    // en la CABECERA (`RN-MV-028`), nulo y presente porque esto no es un paquete.
    assertThat(cuerpo).contains("\"discounts\":[]").contains("\"packageId\":null");
    assertThat(cuerpo).doesNotContain("\"lines\":[{\"packageId\"");

    // Y en la base la línea queda con la resta en cero y sin rebajas.
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movement_detail_discounts", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT line_discount FROM movement_details", java.math.BigDecimal.class))
        .isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("CA-MV-002: cada línea devuelve el vendedor resuelto, que el actor no envió")
  void elVendedorSaleDelCliente() throws Exception {
    String cuerpo =
        mvc.perform(venta(cliente, TARJETA, linea(botSenales, 1)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.id").value(cliente.toString()))
            // EN LA LÍNEA y no en la cabecera (`RN-MV-003`, 16-09-2026): es ahí
            // donde se le creará la comisión, y cada línea puede tener el suyo.
            .andExpect(jsonPath("$.lines[0].seller.id").value(vendedor.toString()))
            .andExpect(jsonPath("$.lines[0].seller.username").value("venta-vendedor"))
            // El nombre y no solo el identificador: la respuesta es el único momento
            // en que quien registra ve a quién acaba de atribuirse lo que vendió.
            .andExpect(jsonPath("$.lines[0].seller.name").value("Ana Ruiz"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // La cabecera ya no lleva ni `client` ni `seller`, y se mira en crudo: un
    // `doesNotExist()` sobre `$.seller` pasaría también con la clave en nulo.
    assertThat(cuerpo).doesNotContain("\"client\":").doesNotContain("\"seller\":null");
  }

  @Test
  @DisplayName("CA-MV-005: varias líneas, con un upgrade y varios bots (FA-002)")
  void variasLineas() throws Exception {
    mvc.perform(venta(cliente, TARJETA, linea(upVip, 1), linea(botSenales, 1), linea(botCopy, 3)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines.length()").value(3))
        // 20.00 + 10.00 + 46.50
        .andExpect(jsonPath("$.totalAmount").value(76.50));
  }

  @Test
  @DisplayName(
      "CA-MV-003: el precio y la vigencia se COPIAN, y corregir el producto después no los cambia")
  void laCopiaSobreviveALaCorreccion() throws Exception {
    mvc.perform(venta(cliente, TARJETA, linea(upPlatino, 1)))
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
        ("{\"userId\":\"%s\",\"paymentMethodId\":\"%s\",\"occurredAt\":\"2026-07-12T03:00:00Z\","
                + "\"lines\":[{\"productId\":\"%s\",\"quantity\":1}]}")
            .formatted(cliente, TARJETA, botSenales);

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
    mvc.perform(venta(cliente, TARJETA, linea(upVip, 1))).andExpect(status().isCreated());

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
  @DisplayName("CA-MV-034: una venta con un método EXCLUIDO en un país SE REGISTRA igual")
  void laExclusionPorPaisNoImpideVender() throws Exception {
    // `RN-MV-019` declara dónde NO vale cada método de pago, y esta prueba
    // afirma que el sistema NO LO COMPRUEBA al vender. Es lo contrario de lo
    // que casi cualquiera supondría al leer la tabla, y por eso existe.
    //
    // La restricción es para el cliente que pinta el selector; el servidor no
    // sabe de qué país es quien compra, porque `users` no guarda país. Sin
    // esta prueba, «la restricción es informativa» solo estaría escrito en
    // documentos y alguien la convertiría en validación sin decidirlo — y el
    // día que eso ocurra, aquí es donde se entera.
    UUID paisExcluido = pais("PVT", "País de prueba de venta");
    jdbc.update(
        "INSERT INTO payment_method_exclusions (payment_method_id, country_id)"
            + " VALUES (CAST(? AS uuid), CAST(? AS uuid))",
        TARJETA,
        paisExcluido.toString());

    mvc.perform(venta(cliente, TARJETA, linea(botSenales, 1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDIENTE"))
        .andExpect(jsonPath("$.paymentMethod").value("CREDIT_CARD"));
  }

  @Test
  @DisplayName("CA-MV-018: la auditoría guarda la instantánea completa, con el vendedor congelado")
  void laAuditoriaGuardaElVendedor() throws Exception {
    mvc.perform(venta(cliente, TARJETA, linea(botCopy, 1))).andExpect(status().isCreated());

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT action, changes::text AS changes FROM audit_change_log"
                + " WHERE module = 'MV' AND entity = 'movements'");

    assertThat(fila.get("action")).isEqualTo("CREATE");
    String cambios = (String) fila.get("changes");
    // Sin el vendedor aquí, «¿por qué esta venta se le atribuyó a esta
    // persona?» solo se puede responder reconstruyendo cómo estaba la
    // estructura comercial ese día. Va en la línea, y la cabecera lleva al
    // sujeto (`RN-MV-026`).
    assertThat(cambios).contains("\"user_id\": \"" + cliente + "\"");
    assertThat(cambios).doesNotContain("\"client_id\"");
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
    mvc.perform(venta(UUID.randomUUID(), TARJETA, linea(botSenales, 1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));
  }

  @Test
  @DisplayName("CA-MV-008: a una cuenta FTD_PENDIENTE no se le vende, y el mensaje dice qué falta")
  void clienteQueNoPuedeOperar() throws Exception {
    // ESTA PRUEBA NO SE PODÍA ESCRIBIR HASTA EL 09-09-2026, y `tasks.md` §4 lo
    // dejó anotado: `ck_users_status` no admitía `FTD_PENDIENTE` y ningún camino
    // lo producía. `V77` lo admite y `RF-SP-045` lo produce, de modo que la
    // única rama inalcanzable de este servicio dejó de serlo.
    jdbc.update("UPDATE users SET status = 'FTD_PENDIENTE' WHERE id = ?::uuid", cliente.toString());

    mvc.perform(venta(cliente, TARJETA, linea(botSenales, 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        // Dice QUÉ LE FALTA y no solo que no se puede: la salida es confirmar el
        // depósito, no reintentar.
        .andExpect(
            jsonPath("$.errors[0].message")
                .value(org.hamcrest.Matchers.containsString("depósito")));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM movements", Integer.class)).isZero();
  }

  @Test
  @DisplayName("CA-MV-017: quien no cuelga de nadie compra, y la venta queda atribuida a él mismo")
  void compraSinSuperior() throws Exception {
    // INVERTIDO EL 04-09-2026 Y ENMENDADO EL 16-09-2026. Hasta el 04-09 esto
    // devolvía `409 EX-003` —«`RN-SP-027` promete que ningún cliente se
    // registra sin vendedor»—, con una premisa incompleta: daba por hecho que
    // quien compra es siempre un cliente. Un agente también compra, y
    // `RN-SP-019` declara que LA CÚSPIDE no declara superior.
    //
    // Entre el 04-09 y el 16-09 la venta se registraba SIN atribución, y no
    // comisionaba a nadie. Desde el 16-09 esa persona ES SU PROPIO VENDEDOR
    // (`RN-MV-003`): cada línea la atribuye a quien compra, `CM` tiene de
    // dónde arrancar la cadena, y qué hace con una autoventa lo decide `CM`.
    mvc.perform(venta(clienteSinVendedor, TARJETA, linea(botSenales, 1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDIENTE"))
        .andExpect(jsonPath("$.user.id").value(clienteSinVendedor.toString()))
        .andExpect(jsonPath("$.lines[0].seller.id").value(clienteSinVendedor.toString()))
        .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern(CODIGO)));

    // Y en la base NINGUNA línea de venta queda sin vendedor: la autoventa es
    // la única atribución que la regla admite para esa persona.
    assertThat(
            jdbc.queryForObject(
                "SELECT d.seller_id FROM movement_details d JOIN movements m ON m.id = d.movement_id"
                    + " WHERE m.user_id = CAST(? AS uuid)",
                UUID.class,
                clienteSinVendedor.toString()))
        .isEqualTo(clienteSinVendedor);
  }

  @Test
  @DisplayName("La auditoría de la autoventa lleva al comprador como vendedor de la línea")
  void laAuditoriaDeLaAutoventa() throws Exception {
    mvc.perform(venta(clienteSinVendedor, TARJETA, linea(botSenales, 1)))
        .andExpect(status().isCreated());

    String cambios =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE module = 'MV'", String.class);

    // La clave de la línea dice a quién se le paga, y aquí dice «a quien compró».
    assertThat(cambios).contains("\"seller_id\": \"" + clienteSinVendedor + "\"");
    assertThat(cambios).doesNotContain("\"seller_id\": null");
  }

  @Test
  @DisplayName("CA-MV-010: el producto inexistente es 422 y el que está fuera de la oferta, 409")
  void productoInexistenteFrenteAFueraDeLaOferta() throws Exception {
    mvc.perform(venta(cliente, TARJETA, linea(UUID.randomUUID(), 1)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-011"));

    // Retirado: existe, y no se le ofrece a nadie. El mensaje NOMBRA el
    // producto, que es lo que evita probar de uno en uno en una venta de cinco
    // líneas.
    mvc.perform(venta(cliente, TARJETA, linea(botRetirado, 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"))
        .andExpect(
            jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("VTA_BOT_VIEJO")));
  }

  @Test
  @DisplayName("CA-MV-011: un upgrade que no sube de nivel se rechaza AL REGISTRAR")
  void elUpgradeQueNoSube() throws Exception {
    // Lleva a BECA, que es el nivel que el cliente ya tiene.
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
    mvc.perform(venta(cliente, TARJETA, linea(upFree, 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
  }

  @Test
  @DisplayName("CA-MV-012: dos upgrades en la misma venta")
  void dosUpgrades() throws Exception {
    mvc.perform(venta(cliente, TARJETA, linea(upVip, 1), linea(upPlatino, 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));
  }

  @Test
  @DisplayName("CA-MV-013: el producto repetido es 400 y la cantidad en un upgrade, 409")
  void repetidoYCantidadEnUpgrade() throws Exception {
    // `VAL-006` es de ENTRADA aunque `RN-MV-011` sea una regla: la repetición
    // se ve mirando la petición, sin consultar nada.
    mvc.perform(venta(cliente, TARJETA, linea(botSenales, 1), linea(botSenales, 2)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-006"));

    mvc.perform(venta(cliente, TARJETA, linea(upVip, 2)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-009"));
  }

  @Test
  @DisplayName("CA-MV-014: productos en monedas distintas, sin conversión posible")
  void monedasDistintas() throws Exception {
    mvc.perform(venta(cliente, TARJETA, linea(botSenales, 1), linea(botEnOtraMoneda, 1)))
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
    mvc.perform(venta(cliente, TARJETA)).andExpect(status().isBadRequest());

    mvc.perform(
            post("/api/v1/movements")
                .with(comoActor())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"paymentMethodId\":\"%s\",\"lines\":[{\"productId\":\"%s\",\"quantity\":1}]}"
                        .formatted(TARJETA, botSenales)))
        .andExpect(status().isBadRequest());

    // Una venta que aún no ha ocurrido no es un hecho. El pasado remoto sí se
    // admite, que es justo lo que hace falta para registrar lo que ya ocurrió.
    mvc.perform(
            post("/api/v1/movements")
                .with(comoActor())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    ("{\"userId\":\"%s\",\"paymentMethodId\":\"%s\","
                            + "\"occurredAt\":\"2099-01-01T00:00:00Z\","
                            + "\"lines\":[{\"productId\":\"%s\",\"quantity\":1}]}")
                        .formatted(cliente, TARJETA, botSenales)))
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
                .content(cuerpo(cliente.toString(), TARJETA, linea(botSenales, 1))))
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
    return "{\"userId\":\"%s\",\"paymentMethodId\":\"%s\",\"lines\":[%s]}"
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
    jdbc.update("DELETE FROM payment_method_exclusions");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'MV'");
    // El catálogo y la cadena se borran ENTEROS, como en `ProductOfferIT` y por
    // el mismo motivo: `uq_memberships_parent` es UNIQUE NULLS NOT DISTINCT, de
    // modo que solo UNA membresía del sistema puede no tener superior. Dejar en
    // pie la cadena sembrada por `V46` y añadir otra al lado no es posible — la
    // segunda raíz revienta en el COMMIT, lejos de aquí y sin decir por qué.
    jdbc.update("DELETE FROM products");
    jdbc.update(
        "DELETE FROM client_sellers WHERE client_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'venta-%')");
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'venta-%')");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM users WHERE username LIKE 'venta-%'");
    jdbc.update("DELETE FROM memberships");
    jdbc.update("DELETE FROM payment_methods WHERE code LIKE 'VTA\\_%'");
    jdbc.update("DELETE FROM currencies WHERE code = 'VTC'");
    jdbc.update("DELETE FROM countries WHERE code = 'PVT'");
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
        "INSERT INTO products (scope, implementation, id, code, type, name, description, source_membership_id,"
            + " target_membership_id, price, currency_id, validity_days, status, created_at,"
            + " updated_at, deleted_at)"
            + " VALUES ('TIENDA', 'MANUAL', CAST(? AS uuid), ?, ?, ?, 'Producto de prueba', CAST(? AS uuid),"
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
                           must_change_password, status, country_id)
        VALUES (CAST(? AS uuid), ?, ?, 'Ana', 'Ruiz', 'no-se-usa-en-esta-prueba', false, 'ACTIVO', (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id.toString(),
        username,
        username + "@nexus.test");
    return id;
  }

  private void asignarMembresia(UUID persona, UUID membresia) {
    // CON `id` DESDE `V56`: `user_memberships` es un historial y `user_id` ya no
    // es la clave primaria. El fixture abre la fila y nunca cierra ninguna, que
    // es todo lo que estas pruebas necesitan; `gen_random_uuid()` basta porque
    // aquí el identificador no ordena nada.
    jdbc.update(
        "INSERT INTO user_memberships (id, user_id, membership_id, started_at, ends_at)"
            + " VALUES (gen_random_uuid(), CAST(? AS uuid), CAST(? AS uuid), ?, NULL)",
        persona.toString(),
        membresia.toString(),
        BASE);
  }

  /**
   * El cliente y su vendedor PRINCIPAL: la fila {@code REGISTRO} de {@code client_sellers}
   * (`RN-SP-049`, `RF-SP-059`, 18-09-2026). Hasta esa fecha era una fila de {@code
   * user_supervisors}, y el cliente ya no tiene fila allí (`RN-SP-028` revertida): es lo que hace
   * de `CA-MV-002` la prueba de que la venta lee la tabla nueva (`CA-SP-709`).
   */
  private void colgarDe(UUID cliente, UUID vendedor) {
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin, first_movement_id, created_at)"
            + " VALUES (CAST(? AS uuid), CAST(? AS uuid), 'REGISTRO', NULL, ?)",
        cliente.toString(),
        vendedor.toString(),
        BASE);
  }

  private UUID pais(String codigo, String nombre) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO countries (id, code, name, is_active) VALUES (CAST(? AS uuid), ?, ?, true)",
        id.toString(),
        codigo,
        nombre);
    return id;
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
