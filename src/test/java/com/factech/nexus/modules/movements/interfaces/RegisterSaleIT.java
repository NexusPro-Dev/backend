package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.shared.persistence.MinorUnits;
import com.factech.nexus.testing.CommissionCleanup;
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

  @Autowired private com.factech.nexus.modules.movements.FakeCardGateway pasarela;

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
  private UUID upRenovacion;
  private UUID upOro;
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
    // La RENOVACIÓN de `free`: hasta el 03-10-2026 este producto era un
    // `free → platino`, un salto de dos escalones, y las pruebas que lo usaban
    // no miraban el nivel —solo el precio copiado y que hubiera dos upgrades—.
    // Desde `RN-PM-018` la oferta ya no publica saltos, y una renovación sigue en
    // ella sin cambiar lo que esas pruebas comprueban.
    upRenovacion =
        upgrade("VTA_UP_RENUEVA", "Renovación de Free", free, free, "50.00", 30, "ACTIVO", false);
    // DECLARADO DESDE `free` COMO LOS DEMÁS, y es un SALTO de tres escalones:
    // sembrado por la base porque el alta ya no lo admite (`RN-PM-018`), y es
    // lo que `CA-MV-526` necesita — coincide por origen y aun así no se vende.
    upOro = upgrade("VTA_UP_ORO", "Ascenso a Oro", free, oro, "100.00", 365, "ACTIVO", false);

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
  @DisplayName(
      "CA-MV-434 — la venta que registra un funcionario con tarjeta nace pendiente SIN cobro y"
          + " sin secreto: no hay nadie al otro lado para escribir la tarjeta (01-10-2026)")
  void elFuncionarioNoAbreCobro() throws Exception {
    pasarela.reiniciar();
    pasarela.encender(true);
    try {
      String cuerpo =
          mvc.perform(venta(cliente, TARJETA, linea(botCopy, 1)))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.status").value("PENDIENTE"))
              .andExpect(jsonPath("$.cardCharge").doesNotExist())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(pasarela.abiertos()).isEmpty();
      assertThat(
              jdbc.queryForObject(
                  "SELECT provider_reference FROM payments WHERE movement_id = CAST(? AS uuid)",
                  String.class,
                  (String) com.jayway.jsonpath.JsonPath.read(cuerpo, "$.id")))
          .isNull();
    } finally {
      pasarela.reiniciar();
    }
  }

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
            MinorUnits.fromMinor(
                jdbc.queryForObject("SELECT line_discount FROM movement_details", Long.class)))
        .isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName(
      "CA-MV-216 — la venta nace con UN pago pendiente con el método indicado; la misma clave de"
          + " idempotencia otra vez no registra otra venta (26-09-2026)")
  void naceConSuPrimerPago() throws Exception {
    mvc.perform(venta(cliente, TARJETA, linea(botCopy, 1)).header("Idempotency-Key", "compra-0001"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.payments.length()").value(1))
        .andExpect(jsonPath("$.payments[0].status").value("PENDIENTE"))
        .andExpect(jsonPath("$.payments[0].paymentMethod.code").value("CREDIT_CARD"))
        .andExpect(jsonPath("$.payments[0].amount").value(15.50));

    // DESVIACIÓN DECLARADA (`RF-MV-018` · `tasks.md` §3): la spec pide devolver
    // la misma venta; se responde 409 y NO se registra otra, que es lo que
    // importa — un reintento no duplica la compra.
    mvc.perform(venta(cliente, TARJETA, linea(botCopy, 1)).header("Idempotency-Key", "compra-0001"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-MV-040"));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM movements", Integer.class)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE idempotency_key = 'compra-0001'",
                Integer.class))
        .isEqualTo(1);

    // Sin clave, la pone el sistema: dos compras iguales son dos ventas.
    mvc.perform(venta(cliente, TARJETA, linea(botCopy, 1))).andExpect(status().isCreated());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movements", Integer.class)).isEqualTo(2);
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
    mvc.perform(venta(cliente, TARJETA, linea(upRenovacion, 1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines[0].unitPrice").value(50.00))
        .andExpect(jsonPath("$.lines[0].validityDays").value(30));

    UUID ventaId = jdbc.queryForObject("SELECT id FROM movements", UUID.class);

    // LA COPIA SOLO SE PUEDE VERIFICAR CAMBIANDO EL ORIGINAL. Comparar el precio
    // al registrar no probaría nada: si la venta releyera el catálogo al
    // mostrarse, un precio idéntico pasaría la prueba igual.
    jdbc.update(
        // 999.00 en centésimas (ADR-006).
        "UPDATE products SET price = 99900, validity_days = 1 WHERE id = CAST(? AS uuid)",
        upRenovacion.toString());

    Map<String, Object> linea =
        jdbc.queryForMap(
            "SELECT unit_price, line_amount, validity_days FROM movement_details"
                + " WHERE movement_id = CAST(? AS uuid)",
            ventaId.toString());

    assertThat(MinorUnits.fromMinor(linea.get("unit_price"))).isEqualByComparingTo("50.00");
    assertThat(MinorUnits.fromMinor(linea.get("line_amount"))).isEqualByComparingTo("50.00");
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
            "SELECT membership_id FROM user_products WHERE user_id = CAST(? AS uuid)",
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
    mvc.perform(venta(cliente, TARJETA, linea(upVip, 1), linea(upRenovacion, 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));
  }

  @Test
  @DisplayName(
      "`CA-MV-526` — un upgrade que SALTA niveles no se vende: por HTTP lo frena la oferta"
          + " (`EX-004`) y no queda venta, línea ni pago")
  void elUpgradeQueSaltaNoSeVende() throws Exception {
    // `free → oro` coincide por origen con el cliente, que está en `free`, y
    // salta tres escalones. Por esta entrada la oferta lo excluye antes de
    // `SaleRules` (`tasks.md` §3, como `CA-MV-011`); la rama de `EX-005` la
    // alcanza `RegisterSaleServiceTest`.
    mvc.perform(venta(cliente, TARJETA, linea(upOro, 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM movements", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movement_details", Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isZero();
  }

  @Test
  @DisplayName("`CA-MV-527` — un upgrade a la membresía INMEDIATAMENTE superior se registra")
  void elEscalonSeRegistra() throws Exception {
    // `free(4) → vip(3)`: un escalón, el caso normal.
    mvc.perform(venta(cliente, TARJETA, linea(upVip, 1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDIENTE"));
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

  // ---------------------------------------------------------------------------
  // La oficina de cada línea (`RN-MV-078`, 09-10-2026): `CA-MV-704` a `CA-MV-710`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-704 — vende un AGENTE: cada línea lleva la oficina de su director, en la respuesta,"
          + " en lo guardado y en la instantánea de la auditoría")
  void elAgenteVendeEnLaOficinaDeSuDirector() throws Exception {
    UUID director = persona("venta-director");
    UUID oficina = equipo("VTA Oficina Norte");
    pertenencia(oficina, director, BASE.minusDays(30), null);
    reportaA(vendedor, director, BASE.minusDays(30), null);

    String cuerpo =
        mvc.perform(venta(cliente, TARJETA, linea(botCopy, 1), linea(botSenales, 1)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.lines[0].team.id").value(oficina.toString()))
            .andExpect(jsonPath("$.lines[0].team.name").value("VTA Oficina Norte"))
            .andExpect(jsonPath("$.lines[1].team.id").value(oficina.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID ventaId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(cuerpo, "$.id"));

    assertThat(oficinasGuardadas(ventaId)).containsOnly(oficina.toString());
    assertThat(
            jdbc.queryForObject(
                "SELECT changes::text FROM audit_change_log WHERE module = 'MV' AND entity_id = ?",
                String.class,
                ventaId))
        .contains("\"team_id\": \"" + oficina + "\"");
  }

  @Test
  @DisplayName("CA-MV-705 — vende un DIRECTOR: cada línea lleva su propia oficina")
  void elDirectorVendeEnLaSuya() throws Exception {
    UUID oficina = equipo("VTA Oficina Propia");
    pertenencia(oficina, vendedor, BASE.minusDays(30), null);

    mvc.perform(venta(cliente, TARJETA, linea(botCopy, 1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines[0].team.id").value(oficina.toString()));
  }

  @Test
  @DisplayName(
      "CA-MV-706 y CA-MV-707 — vende un MANAGER, o alguien sin director con equipo: la venta se"
          + " registra como siempre y sus líneas no tienen oficina, presente y nula")
  void sinOficinaNoHayError() throws Exception {
    // El vendedor cuelga de un manager sin equipo: nadie de la cadena tiene
    // pertenencia, que es lo mismo que vende el manager mismo.
    UUID manager = persona("venta-manager");
    reportaA(vendedor, manager, BASE.minusDays(30), null);

    String cuerpo =
        mvc.perform(venta(cliente, TARJETA, linea(botCopy, 1)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.lines[0].seller.id").value(vendedor.toString()))
            .andExpect(jsonPath("$.lines[0].team").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.lines[0]", org.hamcrest.Matchers.hasKey("team")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID ventaId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(cuerpo, "$.id"));
    assertThat(oficinasGuardadas(ventaId)).containsOnlyNulls();
  }

  @Test
  @DisplayName(
      "CA-MV-708 — después de la venta el agente cambia de director y su director anterior cambia"
          + " de equipo: la oficina de lo ya vendido no cambia")
  void unTrasladoNoMueveLoVendido() throws Exception {
    UUID director = persona("venta-director");
    UUID otroDirector = persona("venta-otro-director");
    UUID norte = equipo("VTA Oficina Norte");
    UUID sur = equipo("VTA Oficina Sur");
    pertenencia(norte, director, BASE.minusDays(30), null);
    reportaA(vendedor, director, BASE.minusDays(30), null);

    String cuerpo =
        mvc.perform(venta(cliente, TARJETA, linea(botCopy, 1)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID ventaId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(cuerpo, "$.id"));

    // El traslado: el agente pasa a otro director, y su director anterior se
    // muda al sur.
    jdbc.update("UPDATE user_supervisors SET ended_at = now() WHERE user_id = ?", vendedor);
    reportaA(vendedor, otroDirector, OffsetDateTime.now(ZoneOffset.UTC), null);
    jdbc.update("UPDATE team_members SET ended_at = now() WHERE user_id = ?", director);
    pertenencia(sur, director, OffsetDateTime.now(ZoneOffset.UTC), null);

    assertThat(oficinasGuardadas(ventaId)).containsOnly(norte.toString());
  }

  @Test
  @DisplayName(
      "CA-MV-709 — una venta registrada hoy con fecha del hecho ANTERIOR a un traslado lleva la"
          + " oficina de aquel día, no la de hoy")
  void laFechaDelHechoDecide() throws Exception {
    UUID antes = persona("venta-director");
    UUID despues = persona("venta-otro-director");
    UUID norte = equipo("VTA Oficina Norte");
    UUID sur = equipo("VTA Oficina Sur");
    OffsetDateTime traslado = OffsetDateTime.parse("2026-07-20T00:00:00Z");
    pertenencia(norte, antes, BASE.minusDays(60), null);
    pertenencia(sur, despues, BASE.minusDays(60), null);
    reportaA(vendedor, antes, BASE.minusDays(60), traslado);
    reportaA(vendedor, despues, traslado, null);

    mvc.perform(
            post("/api/v1/movements")
                .with(comoActor())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    ("{\"userId\":\"%s\",\"paymentMethodId\":\"%s\","
                            + "\"occurredAt\":\"2026-07-12T03:00:00Z\",\"lines\":[%s]}")
                        .formatted(cliente, TARJETA, linea(botCopy, 1))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines[0].team.id").value(norte.toString()));

    // Y la de hoy, para la misma estructura, es la otra.
    mvc.perform(venta(cliente, TARJETA, linea(botSenales, 1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines[0].team.id").value(sur.toString()));
  }

  @Test
  @DisplayName(
      "CA-MV-710 — la venta a un cliente con VARIOS vendedores nace con sus líneas sin vendedor y"
          + " sin oficina")
  void variosVendedoresSinOficina() throws Exception {
    UUID otro = persona("venta-otro-vendedor");
    UUID oficina = equipo("VTA Oficina Norte");
    pertenencia(oficina, vendedor, BASE.minusDays(30), null);
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin, first_movement_id, created_at)"
            + " VALUES (CAST(? AS uuid), CAST(? AS uuid), 'HOTLINK', NULL, ?)",
        cliente.toString(),
        otro.toString(),
        BASE);

    String cuerpo =
        mvc.perform(venta(cliente, TARJETA, linea(botCopy, 1)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.typeStatus").value("VALIDAR_COMISIONES"))
            .andExpect(jsonPath("$.lines[0].seller").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.lines[0].team").value(org.hamcrest.Matchers.nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID ventaId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(cuerpo, "$.id"));
    assertThat(oficinasGuardadas(ventaId)).containsOnlyNulls();
  }

  private java.util.List<String> oficinasGuardadas(UUID ventaId) {
    return jdbc.queryForList(
        "SELECT team_id::text FROM movement_details WHERE movement_id = ?", String.class, ventaId);
  }

  private UUID equipo(String nombre) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO teams (id, name) VALUES (?, ?)", id, nombre);
    return id;
  }

  private void pertenencia(UUID equipo, UUID persona, OffsetDateTime desde, OffsetDateTime hasta) {
    jdbc.update(
        "INSERT INTO team_members (id, team_id, user_id, started_at, ended_at)"
            + " VALUES (gen_random_uuid(), ?, ?, ?, ?)",
        equipo,
        persona,
        desde,
        hasta);
  }

  private void reportaA(UUID persona, UUID superior, OffsetDateTime desde, OffsetDateTime hasta) {
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at, ended_at)"
            + " VALUES (gen_random_uuid(), ?, ?, ?, ?)",
        persona,
        superior,
        desde,
        hasta);
  }

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
    CommissionCleanup.limpiar(jdbc);
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM payment_method_exclusions");
    CommissionCleanup.limpiar(jdbc);
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
    // Las oficinas de `RN-MV-078`: las pertenencias antes que las personas y los
    // equipos (`fk_team_members_user` es RESTRICT).
    jdbc.update(
        "DELETE FROM team_members WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'venta-%')");
    jdbc.update(
        "DELETE FROM team_members WHERE team_id IN (SELECT id FROM teams WHERE name LIKE 'VTA %')");
    jdbc.update("DELETE FROM teams WHERE name LIKE 'VTA %'");
    jdbc.update("DELETE FROM user_products");
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
            + " CAST(? AS uuid), CAST(? AS numeric) * 100, CAST(? AS uuid), CAST(? AS integer), ?, ?, ?,"
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
    // CON `id` DESDE `V56`: `user_products` es un historial y `user_id` ya no
    // es la clave primaria. El fixture abre la fila y nunca cierra ninguna, que
    // es todo lo que estas pruebas necesitan; `gen_random_uuid()` basta porque
    // aquí el identificador no ordena nada.
    jdbc.update(
        "INSERT INTO user_products (id, user_id, membership_id, started_at, ends_at)"
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
