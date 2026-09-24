package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Comprar un paquete para uno mismo (`RF-MV-012` · `T-08`): los doce criterios, `CA-MV-049` a
 * `CA-MV-060`.
 *
 * <p><b>La que importa es `CA-MV-050`</b>: lo que esta operación cobra se compara con el {@code
 * price} que la oferta publica para el mismo paquete, y no con una cuenta hecha aquí. Si alguien
 * cambia el redondeo en `PM` o en `MV`, esa prueba falla.
 *
 * <h2>El catálogo se siembra POR LA BASE, como en `RegisterSaleIT`</h2>
 *
 * <p>Y con la misma advertencia sobre la cadena: {@code uq_memberships_parent} es {@code UNIQUE
 * NULLS NOT DISTINCT}, de modo que solo una membresía puede no tener superior — se borra la cadena
 * entera y se levanta {@code ORO(1) > PLATINO(2) > BECA(3)}. <b>Los paquetes se borran antes que
 * los productos</b>: {@code product_package_items} referencia {@code products}, y una fila que
 * sobreviva tumba a la siguiente suite que empiece con {@code DELETE FROM products}.
 */
@AutoConfigureMockMvc
class BuyPackageIT extends IntegrationTestBase {

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
  private UUID beca;

  private UUID upPlatinoOro;
  private UUID upPlatinoBeca;
  private UUID upPlatinoPlatino;
  private UUID botA;
  private UUID botB;
  private UUID botC;

  private UUID paqBots;
  private UUID paqUp;
  private UUID paqBaja;
  private UUID paqRenueva;
  private UUID paqRedondeo;
  private UUID paqGratis;

  private UUID comprador;
  private UUID vendedor;
  private UUID sinMembresia;
  private UUID enOro;

  @BeforeEach
  void sembrar() {
    limpiar();

    oro = membresia("PAQ_ORO", 1, null);
    platino = membresia("PAQ_PLATINO", 2, oro);
    beca = membresia("PAQ_BECA", 3, platino);

    upPlatinoOro = upgrade("UP_PLATINO_ORO", "100.00", 30, platino, oro);
    upPlatinoBeca = upgrade("UP_PLATINO_BECA", "5.00", 7, platino, beca);
    upPlatinoPlatino = upgrade("UP_PLATINO_PLATINO", "40.00", 30, platino, platino);
    botA = bot("BOT_A", "Bot A", "Señales diarias.", "10.00", null);
    botB = bot("BOT_B", "Bot B", null, "20.00", 90);
    botC = bot("BOT_C", "Bot C", "Copiador.", "49.99", null);

    // 30.00 de lista: 50 % sobre 10.00 (5.00) y 1.00 fijo sobre 20.00 → 24.00.
    paqBots = paquete("PAQ_BOTS", "Dos bots.", "ACTIVO", "TIENDA");
    asociar(paqBots, botA, "PORCENTAJE", "50");
    asociar(paqBots, botB, "FIJO", "1.00");

    // Con upgrade desde PLATINO: solo le corresponde a quien está en PLATINO.
    paqUp = paquete("PAQ_UP", "Oro con bot.", "ACTIVO", "AMBOS");
    asociar(paqUp, upPlatinoOro, "PORCENTAJE", "10");
    asociar(paqUp, botA, "FIJO", "1.00");

    paqBaja = paquete("PAQ_BAJA", "Baja con bot.", "ACTIVO", "TIENDA");
    asociar(paqBaja, upPlatinoBeca, "FIJO", "0");
    asociar(paqBaja, botA, "FIJO", "0");

    paqRenueva = paquete("PAQ_RENUEVA", "Renueva con bot.", "ACTIVO", "TIENDA");
    asociar(paqRenueva, upPlatinoPlatino, "FIJO", "0");
    asociar(paqRenueva, botA, "FIJO", "0");

    // Donde el redondeo se ve: 33 % de 49.99 es 16.4967 y 12.5 % de 20.00 es 2.50.
    paqRedondeo = paquete("PAQ_REDONDEO", "Redondeos.", "ACTIVO", "TIENDA");
    asociar(paqRedondeo, botC, "PORCENTAJE", "33");
    asociar(paqRedondeo, botB, "PORCENTAJE", "12.5");

    paqGratis = paquete("PAQ_GRATIS", "Regalado.", "ACTIVO", "TIENDA");
    asociar(paqGratis, botA, "PORCENTAJE", "100");
    asociar(paqGratis, botB, "FIJO", "20.00");

    vendedor = persona("paq-vendedor", null);
    comprador = persona("paq-cliente", platino);
    sinMembresia = persona("paq-sin", null);
    enOro = persona("paq-oro", oro);
    colgarDe(comprador, vendedor);
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
      "CA-MV-049, CA-MV-052 y CA-MV-059: sin ningún permiso, la venta nace PENDIENTE con una línea por producto, cada una con lo copiado y su rebaja explicada, y sin vendedor en la respuesta")
  void compraDeUnPaquete() throws Exception {
    String cuerpo =
        comprar(comprador, paqBots, TARJETA)
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", matchesPattern("/api/v1/movements/mine/.+")))
            .andExpect(jsonPath("$.status").value("PENDIENTE"))
            // Un solo vendedor: nace validada (`RN-MV-034`, `CA-MV-143`).
            .andExpect(jsonPath("$.typeStatus").value("VALIDADO"))
            .andExpect(jsonPath("$.code").value(matchesPattern(CODIGO)))
            .andExpect(jsonPath("$.user.id").value(comprador.toString()))
            .andExpect(jsonPath("$.packageId").value(paqBots.toString()))
            .andExpect(jsonPath("$.currency.code").value("USD"))
            .andExpect(jsonPath("$.paymentMethod").value("CREDIT_CARD"))
            .andExpect(jsonPath("$.lines", hasSize(2)))
            // En el orden del paquete, cantidad uno, y con lo copiado.
            .andExpect(jsonPath("$.lines[0].productCode").value("BOT_A"))
            .andExpect(jsonPath("$.lines[0].productName").value("Bot A"))
            .andExpect(jsonPath("$.lines[0].productDescription").value("Señales diarias."))
            .andExpect(jsonPath("$.lines[0].quantity").value(1))
            .andExpect(jsonPath("$.lines[0].unitPrice").value(10.00))
            .andExpect(jsonPath("$.lines[0].validityDays").value(nullValue()))
            // La rebaja explicada: cómo se pactó y cuánto valió.
            .andExpect(jsonPath("$.lines[0].discounts", hasSize(1)))
            .andExpect(jsonPath("$.lines[0].discounts[0].type").value("PORCENTAJE"))
            .andExpect(jsonPath("$.lines[0].discounts[0].value").value(50.00))
            .andExpect(jsonPath("$.lines[0].discounts[0].discountValue").value(5.00))
            .andExpect(jsonPath("$.lines[0].lineDiscount").value(5.00))
            .andExpect(jsonPath("$.lines[0].lineAmount").value(5.00))
            .andExpect(jsonPath("$.lines[1].productCode").value("BOT_B"))
            .andExpect(jsonPath("$.lines[1].productDescription").value(nullValue()))
            .andExpect(jsonPath("$.lines[1].validityDays").value(90))
            .andExpect(jsonPath("$.lines[1].discounts[0].type").value("FIJO"))
            .andExpect(jsonPath("$.lines[1].discounts[0].value").value(1.00))
            .andExpect(jsonPath("$.lines[1].discounts[0].discountValue").value(1.00))
            .andExpect(jsonPath("$.lines[1].lineAmount").value(19.00))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // La respuesta NO lleva el vendedor (`RF-MV-002` §4.3): ni en la línea ni en
    // la cabecera. Y las claves nulas viajan presentes.
    assertThat(cuerpo).doesNotContain("\"seller\"").doesNotContain("\"sellers\"");
    assertThat(cuerpo).contains("\"productDescription\":null").contains("\"validityDays\":null");

    // CA-MV-059: en la base es una venta como cualquier otra —tipo VENTA, el
    // vendedor de cada línea es el superior del comprador— salvo por el paquete.
    Map<String, Object> cabecera =
        jdbc.queryForMap(
            "SELECT m.package_id::text AS paquete, m.user_id::text AS sujeto, t.code AS tipo,"
                + " m.status FROM movements m JOIN movement_types t ON t.id = m.movement_type_id");
    assertThat(cabecera.get("paquete")).isEqualTo(paqBots.toString());
    assertThat(cabecera.get("sujeto")).isEqualTo(comprador.toString());
    assertThat(cabecera.get("tipo")).isEqualTo("VENTA");
    assertThat(cabecera.get("status")).isEqualTo("PENDIENTE");
    assertThat(
            jdbc.queryForList(
                "SELECT DISTINCT seller_id::text FROM movement_details", String.class))
        .containsExactly(vendedor.toString());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movement_details", Integer.class))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movement_detail_discounts", Integer.class))
        .isEqualTo(2);
  }

  @Test
  @DisplayName(
      "CA-MV-145: con dos vendedores, la compra del paquete nace VALIDAR_COMISIONES y ninguna línea lleva vendedor")
  void conDosVendedoresNaceSinVendedor() throws Exception {
    // Un segundo vínculo, de hotlink: el cliente ya tiene dos vendedores.
    UUID otro = persona("paq-otro-vendedor", null);
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin, first_movement_id, created_at)"
            + " VALUES (?::uuid, ?::uuid, 'HOTLINK', NULL, ?)",
        comprador,
        otro,
        BASE);

    comprar(comprador, paqBots, TARJETA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.typeStatus").value("VALIDAR_COMISIONES"));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM movement_details WHERE seller_id IS NULL", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT s.code FROM movements m"
                    + " JOIN movement_type_statuses s ON s.id = m.type_status_id",
                String.class))
        .isEqualTo("VALIDAR_COMISIONES");
  }

  @Test
  @DisplayName(
      "CA-MV-050: lo que se cobra es EXACTAMENTE el price que la oferta publica para ese paquete, también donde el redondeo se ve")
  void loCobradoEsLoPublicado() throws Exception {
    // La cuenta la hace `PM` y se lee de la oferta: aquí no se calcula nada.
    String oferta =
        mvc.perform(
                get("/api/v1/products/available")
                    .with(user(comprador.toString()).authorities(() -> "products:sale")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    for (UUID paquete : List.of(paqRedondeo, paqBots, paqUp)) {
      String codigo = codigoDe(paquete);
      List<Object> publicado =
          JsonPath.read(oferta, "$.packages.content[?(@.code=='" + codigo + "')].price");
      assertThat(publicado).as("la oferta publica " + codigo).hasSize(1);

      String venta =
          comprar(comprador, paquete, TARJETA)
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getContentAsString();
      BigDecimal cobrado = new BigDecimal(JsonPath.read(venta, "$.payableAmount").toString());
      assertThat(cobrado)
          .as("lo cobrado por " + codigo)
          .isEqualByComparingTo(new BigDecimal(publicado.get(0).toString()));
    }

    // Y con los números a la vista, para que el redondeo quede documentado:
    // 33 % de 49.99 = 16.4967 → 16.50, y 12.5 % de 20.00 = 2.50; se cobra
    // 33.49 + 17.50 = 50.99, y NO 69.99 − 18.9967 redondeado.
    assertThat(
            jdbc.queryForObject(
                "SELECT payable_amount FROM movements WHERE package_id = ?::uuid",
                BigDecimal.class,
                paqRedondeo))
        .isEqualByComparingTo("50.99");
  }

  @Test
  @DisplayName(
      "CA-MV-051: el total es lo que valdría sin rebaja, el descuento la diferencia, y las tres cuadran con las líneas")
  void lasTresCifrasCuadran() throws Exception {
    comprar(comprador, paqBots, TARJETA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.totalAmount").value(30.00))
        .andExpect(jsonPath("$.discountAmount").value(6.00))
        .andExpect(jsonPath("$.payableAmount").value(24.00));

    Map<String, Object> cabecera =
        jdbc.queryForMap(
            "SELECT total_amount, discount_amount, payable_amount,"
                + " (SELECT sum(quantity * unit_price) FROM movement_details) AS bruto,"
                + " (SELECT sum(line_discount) FROM movement_details) AS rebajado,"
                + " (SELECT sum(line_amount) FROM movement_details) AS neto"
                + " FROM movements");
    assertThat((BigDecimal) cabecera.get("total_amount")).isEqualByComparingTo("30.00");
    assertThat((BigDecimal) cabecera.get("discount_amount")).isEqualByComparingTo("6.00");
    assertThat((BigDecimal) cabecera.get("payable_amount")).isEqualByComparingTo("24.00");
    assertThat((BigDecimal) cabecera.get("bruto")).isEqualByComparingTo("30.00");
    assertThat((BigDecimal) cabecera.get("rebajado")).isEqualByComparingTo("6.00");
    assertThat((BigDecimal) cabecera.get("neto")).isEqualByComparingTo("24.00");
  }

  @Test
  @DisplayName(
      "CA-MV-053: la venta recuerda el paquete, y corregir su descuento —o sacarle un producto— después no cambia lo que se cobró; la compra siguiente sí lleva lo nuevo")
  void loCongeladoNoCambia() throws Exception {
    String primera =
        comprar(comprador, paqBots, TARJETA)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(primera, "$.id");

    // Se corrige el descuento del bot A (50 % → 90 %) y se saca el bot B del
    // paquete. Lo vendido no se entera.
    jdbc.update(
        "UPDATE product_package_items SET discount_value = 90 WHERE package_id = ?::uuid AND"
            + " product_id = ?::uuid",
        paqBots,
        botA);
    jdbc.update(
        "DELETE FROM product_package_items WHERE package_id = ?::uuid AND product_id = ?::uuid",
        paqBots,
        botB);
    jdbc.update("UPDATE products SET name = 'Bot A renombrado' WHERE id = ?::uuid", botA);

    mvc.perform(get("/api/v1/movements/mine/" + id).with(propio(comprador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.packageId").value(paqBots.toString()))
        .andExpect(jsonPath("$.lines", hasSize(2)))
        .andExpect(jsonPath("$.lines[0].productName").value("Bot A"))
        .andExpect(jsonPath("$.lines[0].discounts[0].value").value(50.00))
        .andExpect(jsonPath("$.lines[0].discounts[0].discountValue").value(5.00))
        .andExpect(jsonPath("$.lines[0].lineAmount").value(5.00))
        .andExpect(jsonPath("$.payableAmount").value(24.00));

    // El paquete ahora tiene un solo producto y no se puede ofrecer: se vuelve a
    // asociar B para que la segunda compra ejercite EL DESCUENTO NUEVO.
    asociar(paqBots, botB, "FIJO", "1.00");
    comprar(comprador, paqBots, TARJETA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines[0].productName").value("Bot A renombrado"))
        .andExpect(jsonPath("$.lines[0].discounts[0].value").value(90.00))
        .andExpect(jsonPath("$.lines[0].lineAmount").value(1.00))
        .andExpect(jsonPath("$.payableAmount").value(20.00));
  }

  // ---------------------------------------------------------------------------
  // Se compra entero, y tal como está hoy
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-054: con un producto del paquete inactivo o retirado se rechaza la compra COMPLETA nombrándolo, y no queda ni la cabecera")
  void unProductoCaidoNoVendeLoQueQueda() throws Exception {
    jdbc.update("UPDATE products SET status = 'INACTIVO' WHERE id = ?::uuid", botA);
    comprar(comprador, paqBots, TARJETA)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        .andExpect(jsonPath("$.errors[0].message").value(containsString("BOT_A")))
        .andExpect(jsonPath("$.errors[0].message").value(containsString("inactivo")));

    jdbc.update(
        "UPDATE products SET status = 'ACTIVO', deleted_at = now() WHERE id = ?::uuid", botA);
    comprar(comprador, paqBots, TARJETA)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].message").value(containsString("BOT_A")))
        .andExpect(jsonPath("$.errors[0].message").value(containsString("retirado")));

    // Un bot que solo se publica por hotlink: el paquete se ofrece (PM lo mira
    // como un todo) y el producto NO está en la oferta de esa persona. Es el
    // paso 5 haciéndose aunque el paso 3 lo dé por bueno.
    jdbc.update(
        "UPDATE products SET deleted_at = NULL, scope = 'HOTLINK' WHERE id = ?::uuid", botA);
    comprar(comprador, paqBots, TARJETA)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"))
        .andExpect(jsonPath("$.errors[0].message").value(containsString("BOT_A")));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM movements", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movement_details", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE module = 'MV'", Integer.class))
        .isZero();
  }

  @Test
  @DisplayName(
      "CA-MV-055: el paquete vencido —o que aún no empieza— se rechaza con su fecha, y se distingue del inactivo y del que no se publica en la tienda")
  void fueraDeSuVigencia() throws Exception {
    jdbc.update(
        "UPDATE product_packages SET valid_from = (now() AT TIME ZONE 'UTC')::date - 10,"
            + " valid_to = (now() AT TIME ZONE 'UTC')::date - 1 WHERE id = ?::uuid",
        paqBots);
    String vencido = rechazo(comprador, paqBots, "EX-002");
    assertThat(vencido).contains("terminó");

    jdbc.update(
        "UPDATE product_packages SET valid_to = NULL, valid_from = (now() AT TIME ZONE"
            + " 'UTC')::date + 1 WHERE id = ?::uuid",
        paqBots);
    assertThat(rechazo(comprador, paqBots, "EX-002")).contains("todavía no está vigente");

    // El día de fin cuenta entero: un paquete que termina hoy se compra hoy.
    jdbc.update(
        "UPDATE product_packages SET valid_from = (now() AT TIME ZONE 'UTC')::date - 10,"
            + " valid_to = (now() AT TIME ZONE 'UTC')::date WHERE id = ?::uuid",
        paqBots);
    comprar(comprador, paqBots, TARJETA).andExpect(status().isCreated());

    jdbc.update("UPDATE product_packages SET status = 'INACTIVO' WHERE id = ?::uuid", paqBots);
    String inactivo = rechazo(comprador, paqBots, "EX-002");
    assertThat(inactivo).contains("inactivo").isNotEqualTo(vencido);

    jdbc.update(
        "UPDATE product_packages SET status = 'ACTIVO', scope = 'HOTLINK' WHERE id = ?::uuid",
        paqBots);
    assertThat(rechazo(comprador, paqBots, "EX-002")).contains("no se publica en la tienda");

    jdbc.update("UPDATE product_packages SET scope = 'AMBOS' WHERE id = ?::uuid", paqBots);
    comprar(comprador, paqBots, TARJETA).andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "CA-MV-056: el paquete que no le corresponde al actor por su nivel se rechaza, y se distingue del que no se ofrece a nadie")
  void noLeCorrespondeATi() throws Exception {
    // PAQ_UP sale de PLATINO: ni quien no tiene membresía ni quien está en ORO.
    String sinNivel = rechazo(sinMembresia, paqUp, "EX-003");
    assertThat(sinNivel).contains("PAQ_UP").doesNotContain("inactivo");
    assertThat(rechazo(enOro, paqUp, "EX-003")).isEqualTo(sinNivel);

    // Y a quien está en PLATINO sí, con el nivel subiendo.
    comprar(comprador, paqUp, TARJETA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines[0].productCode").value("UP_PLATINO_ORO"))
        .andExpect(jsonPath("$.lines[0].lineAmount").value(90.00))
        .andExpect(jsonPath("$.payableAmount").value(99.00));

    // El de solo bots le corresponde a todo el mundo, membresía o no.
    comprar(sinMembresia, paqBots, TARJETA).andExpect(status().isCreated());
    comprar(enOro, paqBots, TARJETA).andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "CA-MV-057: el upgrade del paquete que BAJA de nivel se rechaza, y el que renueva el mismo se admite")
  void elUpgradeNoBaja() throws Exception {
    // PAQ_BAJA sale de PLATINO y lleva a BECA. La oferta se lo ofrece —coincide
    // por ORIGEN— y es la regla de `MV` la que lo para (`RN-MV-006`).
    assertThat(rechazo(comprador, paqBaja, "EX-005")).contains("UP_PLATINO_BECA");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movements", Integer.class)).isZero();

    comprar(comprador, paqRenueva, TARJETA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines[0].productCode").value("UP_PLATINO_PLATINO"));
  }

  @Test
  @DisplayName(
      "CA-MV-058: la petición no admite cantidad ni productos — lo que se envíe no tiene dónde caer, y el paquete es el de la ruta")
  void noHayFormaDeNegociar() throws Exception {
    // Cada campo que no existe en la representación es un 400, y no un dato
    // ignorado: el contrato rechaza propiedades desconocidas a propósito.
    for (String extra :
        List.of(
            "\"quantity\":3",
            "\"packageId\":\"" + paqUp + "\"",
            "\"lines\":[{\"productId\":\"" + botC + "\",\"quantity\":5}]",
            "\"discount\":99",
            "\"unitPrice\":0.01")) {
      mvc.perform(
              post("/api/v1/packages/PAQ_BOTS/purchases")
                  .with(propio(comprador))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"paymentMethodId\":\"" + TARJETA + "\"," + extra + "}"))
          .andExpect(status().isBadRequest());
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movements", Integer.class)).isZero();

    // Y con el cuerpo que sí existe: una línea por producto, cantidad uno, y
    // el paquete es el de la ruta.
    comprar(comprador, paqBots, TARJETA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.packageId").value(paqBots.toString()))
        .andExpect(jsonPath("$.lines", hasSize(2)))
        .andExpect(jsonPath("$.lines[0].quantity").value(1))
        .andExpect(jsonPath("$.lines[1].quantity").value(1));
  }

  @Test
  @DisplayName(
      "CA-MV-060: la auditoría guarda la instantánea completa — el paquete, cada línea con lo copiado y cada rebaja como se pactó")
  void laInstantanea() throws Exception {
    comprar(comprador, paqBots, TARJETA).andExpect(status().isCreated());

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT action, actor_id::text AS actor, changes::text AS changes FROM audit_change_log"
                + " WHERE module = 'MV' AND entity = 'movements'");
    assertThat(fila.get("action")).isEqualTo("CREATE");
    // El actor de la auditoría es el propio cliente: el único caso del módulo
    // en que quien teclea y a nombre de quién coinciden (`RF-MV-002` §6).
    assertThat(fila.get("actor")).isEqualTo(comprador.toString());

    String cambios = (String) fila.get("changes");
    assertThat(cambios).contains("\"package_id\": \"" + paqBots + "\"");
    assertThat(cambios).contains("\"user_id\": \"" + comprador + "\"");
    assertThat(cambios).contains("\"seller_id\": \"" + vendedor + "\"");
    assertThat(cambios).contains("\"product_name\": \"Bot A\"");
    assertThat(cambios).contains("\"product_description\": \"Señales diarias.\"");
    assertThat(cambios).contains("\"type\": \"PORCENTAJE\"");
    assertThat(cambios).contains("\"value\": \"50.00\"");
    assertThat(cambios).contains("\"discount_value\": \"5.00\"");
    assertThat(cambios).contains("\"type\": \"FIJO\"");
    assertThat(cambios).contains("\"line_discount\": \"1.00\"");
    assertThat(cambios).contains("\"payable_amount\": \"24.00\"");
  }

  // ---------------------------------------------------------------------------
  // Casos límite y excepciones
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "Un paquete gratuito se registra sin método y con GRATIS; enviarle método se rechaza (RN-MV-022)")
  void elPaqueteGratuito() throws Exception {
    mvc.perform(
            post("/api/v1/packages/PAQ_GRATIS/purchases")
                .with(propio(comprador))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.paymentMethod").value("GRATIS"))
        .andExpect(jsonPath("$.totalAmount").value(30.00))
        .andExpect(jsonPath("$.discountAmount").value(30.00))
        .andExpect(jsonPath("$.payableAmount").value(0.00))
        .andExpect(jsonPath("$.lines[0].lineAmount").value(0.00))
        .andExpect(jsonPath("$.lines[1].lineAmount").value(0.00));

    // Y sin cuerpo siquiera: no hay nada que decir.
    mvc.perform(post("/api/v1/packages/PAQ_GRATIS/purchases").with(propio(comprador)))
        .andExpect(status().isCreated());

    assertThat(rechazo(comprador, paqGratis, "RN-MV-022")).contains("gratuita");

    // Y al revés: con importe, el método es obligatorio.
    mvc.perform(
            post("/api/v1/packages/PAQ_BOTS/purchases")
                .with(propio(comprador))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-MV-022"));
  }

  @Test
  @DisplayName(
      "Un fijo mayor que el precio de hoy deja la línea en cero, como publica el catálogo, y lo pactado se guarda tal cual")
  void elFijoQueSuperaElPrecio() throws Exception {
    // El hueco temporal de `RN-PM-037`: el precio bajó después de asociar.
    jdbc.update("UPDATE products SET price = 0.50 WHERE id = ?::uuid", botB);
    comprar(comprador, paqBots, TARJETA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines[1].unitPrice").value(0.50))
        .andExpect(jsonPath("$.lines[1].discounts[0].value").value(1.00))
        .andExpect(jsonPath("$.lines[1].discounts[0].discountValue").value(0.50))
        .andExpect(jsonPath("$.lines[1].lineAmount").value(0.00))
        .andExpect(jsonPath("$.payableAmount").value(5.00));
  }

  @Test
  @DisplayName(
      "EX-001: el código que no es de ningún paquete y el del retirado son 422, indistinguibles para quien compra; el código no distingue mayúsculas")
  void paqueteInexistenteORetirado() throws Exception {
    // Es el código y no el identificador: el uuid del paquete, que antes era la
    // ruta, hoy es un código que no existe.
    for (String codigo : List.of("NO_EXISTE", paqBots.toString())) {
      mvc.perform(post("/api/v1/packages/" + codigo + "/purchases").with(propio(comprador)))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.errors[0].code").value("EX-001"));
    }

    // Sin distinguir mayúsculas, como el hotlink (`RF-PM-026`).
    comprar(comprador, "paq_bots", TARJETA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.packageId").value(paqBots.toString()));

    jdbc.update("UPDATE product_packages SET deleted_at = now() WHERE id = ?::uuid", paqBots);
    mvc.perform(post("/api/v1/packages/PAQ_BOTS/purchases").with(propio(comprador)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));
  }

  @Test
  @DisplayName(
      "EX-006: a una cuenta FTD_PENDIENTE no se le vende un paquete, y el mensaje dice qué falta")
  void laCuentaNoOpera() throws Exception {
    jdbc.update("UPDATE users SET status = 'FTD_PENDIENTE' WHERE id = ?::uuid", comprador);
    assertThat(rechazo(comprador, paqBots, "EX-006")).contains("depósito");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movements", Integer.class)).isZero();
  }

  @Test
  @DisplayName(
      "EX-008: el método de pago inexistente es 422 y el desactivado 409, como en RF-MV-001")
  void elMetodoDePago() throws Exception {
    comprar(comprador, paqBots, UUID.randomUUID().toString())
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-010"));

    UUID inactivo = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO payment_methods (id, code, name, is_active) VALUES (?::uuid, 'PAQ_INACTIVO',"
            + " 'Método retirado', false)",
        inactivo);
    comprar(comprador, paqBots, inactivo.toString())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-010"));
  }

  @Test
  @DisplayName(
      "Comprar exige sesión: sin credencial es 401, y el vendedor no puede comprar a nombre de su cliente")
  void exigeSesion() throws Exception {
    mvc.perform(
            post("/api/v1/packages/PAQ_BOTS/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethodId\":\"" + TARJETA + "\"}"))
        .andExpect(status().isUnauthorized());

    // Quien compra es quien pide: el vendedor comprando queda A SU NOMBRE, y
    // como no cuelga de nadie, es su propio vendedor.
    comprar(vendedor, paqBots, TARJETA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user.id").value(vendedor.toString()));
    assertThat(
            jdbc.queryForList(
                "SELECT DISTINCT seller_id::text FROM movement_details", String.class))
        .containsExactly(vendedor.toString());
  }

  // ---------------------------------------------------------------------------
  // Ayudas
  // ---------------------------------------------------------------------------

  private ResultActions comprar(UUID quien, UUID paquete, String metodo) throws Exception {
    return mvc.perform(peticion(quien, codigoDe(paquete), metodo));
  }

  private ResultActions comprar(UUID quien, String codigo, String metodo) throws Exception {
    return mvc.perform(peticion(quien, codigo, metodo));
  }

  /** El código del paquete, que es lo que va en la ruta desde el 17-09-2026. */
  private String codigoDe(UUID paquete) {
    return jdbc.queryForObject(
        "SELECT code FROM product_packages WHERE id = ?::uuid", String.class, paquete);
  }

  private String rechazo(UUID quien, UUID paquete, String codigo) throws Exception {
    String cuerpo =
        comprar(quien, paquete, TARJETA)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errors[0].code").value(codigo))
            .andExpect(jsonPath("$.errors[0].message").value(not(containsString("null"))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(cuerpo, "$.errors[0].message");
  }

  /** Sin ninguna autoridad, a propósito: es una compra propia (`CA-MV-049`). */
  private static MockHttpServletRequestBuilder peticion(UUID quien, String codigo, String metodo) {
    return post("/api/v1/packages/" + codigo + "/purchases")
        .with(propio(quien))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"paymentMethodId\":\"" + metodo + "\"}");
  }

  private void limpiar() {
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM payment_method_exclusions");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'MV'");
    // Los paquetes ANTES que los productos: `product_package_items` los referencia.
    jdbc.update("DELETE FROM product_package_items");
    jdbc.update("DELETE FROM product_packages");
    jdbc.update("DELETE FROM products");
    jdbc.update(
        "DELETE FROM client_sellers WHERE client_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'paq-%')");
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'paq-%')");
    jdbc.update("DELETE FROM user_products");
    jdbc.update("DELETE FROM users WHERE username LIKE 'paq-%'");
    jdbc.update("DELETE FROM memberships");
    jdbc.update("DELETE FROM payment_methods WHERE code LIKE 'PAQ\\_%'");
  }

  private UUID membresia(String codigo, int nivel, UUID superior) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, parent_membership_id, level, color)"
            + " VALUES (?::uuid, ?, ?, ?::uuid, ?, upper(lpad(to_hex(? * 4919), 6, '0')))",
        id,
        codigo,
        "Membresía " + codigo,
        superior,
        nivel,
        nivel);
    return id;
  }

  private UUID upgrade(String codigo, String precio, Integer vigencia, UUID origen, UUID destino) {
    return producto(
        codigo, "UPGRADE_MEMBRESIA", "Producto " + codigo, null, precio, vigencia, origen, destino);
  }

  private UUID bot(
      String codigo, String nombre, String descripcion, String precio, Integer vigencia) {
    return producto(codigo, "BOT", nombre, descripcion, precio, vigencia, null, null);
  }

  private UUID producto(
      String codigo,
      String tipo,
      String nombre,
      String descripcion,
      String precio,
      Integer vigencia,
      UUID origen,
      UUID destino) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO products (id, code, type, name, description, icon, source_membership_id,
                              target_membership_id, price, currency_id, validity_days, status,
                              scope, implementation, created_at, updated_at)
        VALUES (?::uuid, ?, ?, ?, ?, ?, ?::uuid, ?::uuid, ?::numeric, ?::uuid, ?::integer, 'ACTIVO',
                'AMBOS', 'MANUAL', ?, ?)
        """,
        id,
        codigo,
        tipo,
        nombre,
        descripcion,
        "UPGRADE_MEMBRESIA".equals(tipo) ? "crown" : null,
        origen,
        destino,
        precio,
        USD,
        vigencia,
        BASE,
        BASE);
    return id;
  }

  private UUID paquete(String codigo, String descripcion, String estado, String alcance) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO product_packages (id, code, name, description, currency_id, status, scope,"
            + " valid_from) VALUES (?::uuid, ?, ?, ?, ?::uuid, ?, ?, (now() AT TIME ZONE"
            + " 'UTC')::date)",
        id,
        codigo,
        "Paquete " + codigo,
        descripcion,
        USD,
        estado,
        alcance);
    return id;
  }

  /** El instante de alta se fija a mano: las líneas de la venta van en el orden del paquete. */
  private int asociadas = 0;

  private void asociar(UUID paquete, UUID producto, String forma, String valor) {
    jdbc.update(
        "INSERT INTO product_package_items (package_id, product_id, discount_type, discount_value,"
            + " created_at) VALUES (?::uuid, ?::uuid, ?, ?::numeric, ?)",
        paquete,
        producto,
        forma,
        valor,
        BASE.plusSeconds(++asociadas));
  }

  private UUID persona(String username, UUID membresia) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?::uuid, ?, ?, 'Ana', 'Ruiz', 'no-se-usa-en-esta-prueba', false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@nexus.test");
    if (membresia != null) {
      jdbc.update(
          "INSERT INTO user_products (id, user_id, membership_id, started_at, ends_at)"
              + " VALUES (gen_random_uuid(), ?::uuid, ?::uuid, ?, NULL)",
          id,
          membresia,
          BASE);
    }
    return id;
  }

  /**
   * El comprador es un cliente, y su vendedor es su PRINCIPAL: la fila {@code REGISTRO} de {@code
   * client_sellers} (`RN-SP-049`, `RF-SP-059`, 18-09-2026), no una fila de {@code
   * user_supervisors}.
   */
  private void colgarDe(UUID cliente, UUID vendedor) {
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin, first_movement_id, created_at)"
            + " VALUES (?::uuid, ?::uuid, 'REGISTRO', NULL, ?)",
        cliente,
        vendedor,
        BASE);
  }

  // Desde RF-SP-062 (21-09-2026) lo propio exige permiso —autenticarse no autoriza
  // nada—: el actor porta la familia de alcance propio de MV, que es lo que V31 da a
  // todo rol. Hasta entonces bastaba con `user(id)`.
  private static RequestPostProcessor propio(UUID persona) {
    return user(persona.toString())
        .authorities(
            () -> "movements:list-own",
            () -> "movements:read-own",
            () -> "movements:read-own-products",
            () -> "packages:buy");
  }
}
