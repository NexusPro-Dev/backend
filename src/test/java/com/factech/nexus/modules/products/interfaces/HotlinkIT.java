package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.products.domain.service.GetHotlinkService;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El hotlink público (`RF-PM-008`).
 *
 * <p><b>La prueba que define este requerimiento es {@link #elMismoCuerpoEnLosSeisCasos()}</b>: los
 * seis rechazos comparan el <b>cuerpo entero</b>, no el estado. Sin ella, el día que alguien
 * «mejore» un mensaje para depurar, el enlace pasaría a decir quién existe y nadie se enteraría.
 */
@AutoConfigureMockMvc
class HotlinkIT extends IntegrationTestBase {

  /** `USD`, sembrada por `V15` y **la moneda por omisión** del sistema. */
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  /** `AGENTE`, sembrado por `V7` con `role_type = VENDEDOR`. */
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";

  /** `CLIENTE`, sembrado por `V30`: es de tipo `CONSUMIDOR`, no fuerza comercial. */
  private static final String CLIENTE = "01a02a33-4c00-7008-9c4f-5e7ad1000008";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  /**
   * Para `T-13`, que cuenta sentencias.
   *
   * <p>Se cuentan sobre el <b>servicio</b> y no sobre la llamada HTTP: por el filtro pasan
   * escrituras que no son de esta lectura —el registro de peticiones, entre otras—, y contarlas
   * todas mediría el arranque de la petición en lugar del coste del caso de uso.
   */
  @Autowired private SessionFactory sessionFactory;

  @Autowired private GetHotlinkService servicio;

  private UUID oro;
  private UUID free;
  private String cop;

  @BeforeEach
  void sembrar() {
    limpiar();
    oro = membresia("HL_ORO", "Oro de hotlink", 1, null);
    // La cadena encadenada de verdad: `uq_memberships_parent` es UNIQUE NULLS
    // NOT DISTINCT, de modo que solo UNA puede no tener superior.
    free = membresia("HL_FREE", "Free de hotlink", 2, oro);
    cop = moneda("COP", "Peso colombiano");

    persona("vendedora", "Ana", "Ruiz", AGENTE);
    persona("clienta", "Lucia", "Paz", CLIENTE);

    producto("HL_UPGRADE", "Ascenso a Oro", oro, "AMBOS", "ACTIVO", false);
    // Un BOT y no un upgrade: `uq_products_upgrade_target` solo admite UNA
    // pareja origen-destino activa, y ese no es el punto de esta prueba.
    bot("HL_TIENDA", "Solo tienda", "TIENDA");
    producto("HL_INACTIVO", "Sin publicar", oro, "AMBOS", "INACTIVO", false);
    producto("HL_RETIRADO", "Retirado", oro, "AMBOS", "ACTIVO", true);
    bot("HL_BOT", "Bot de señales", "AMBOS");
  }

  @AfterEach
  void vaciar() {
    limpiar();
  }

  @Test
  @DisplayName("`CA-PM-127` — devuelve, SIN token, el vendedor y el producto en una sola llamada")
  void devuelveVendedorYProductoSinToken() throws Exception {
    mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_UPGRADE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.seller.firstName").value("Ana"))
        .andExpect(jsonPath("$.seller.lastName").value("Ruiz"))
        .andExpect(jsonPath("$.product.code").value("HL_UPGRADE"))
        .andExpect(jsonPath("$.product.price").value(49.99));
  }

  @Test
  @DisplayName("`CA-PM-135` — no publica correo, identificador, estado ni roles del vendedor")
  void noPublicaNadaMasDelVendedor() throws Exception {
    String cuerpo =
        mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_UPGRADE"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Se comprueba el CUERPO ENTERO y no los campos esperados: lo que importa
    // aquí es lo que NO está.
    assertThat(cuerpo).doesNotContain("email").doesNotContain("username");
    assertThat(cuerpo).doesNotContain("ACTIVO").doesNotContain("roles");
  }

  @Test
  @DisplayName(
      "`CA-PM-138` y `CA-PM-139` — el upgrade trae código, nombre y COLOR, sin id ni nivel")
  void laMembresiaLlegaRecortadaYConColor() throws Exception {
    mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_UPGRADE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.product.membership.code").value("HL_ORO"))
        .andExpect(jsonPath("$.product.membership.name").value("Oro de hotlink"))
        .andExpect(jsonPath("$.product.membership.color").value("001337"))
        // Los dos que NO se publican, y es la mitad del criterio.
        .andExpect(jsonPath("$.product.membership.id").doesNotExist())
        .andExpect(jsonPath("$.product.membership.level").doesNotExist());
  }

  @Test
  @DisplayName("`CA-PM-140` — un bot devuelve la membresía vacía y PRESENTE")
  void elBotNoLlevaMembresia() throws Exception {
    mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_BOT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.product.membership").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.product.code").value("HL_BOT"));
  }

  @Test
  @DisplayName("`CA-PM-128` — con tasa vigente llega la tasa aplicada y el importe convertido")
  void conTasaVigenteLlegaLaConversion() throws Exception {
    // `USD` es la moneda por omisión, de modo que para que haya algo que
    // convertir el producto tiene que estar en OTRA.
    productoEnMoneda("HL_EN_COP", "En pesos", cop, "1000.00");
    tasa(cop, USD, "0.00024096", LocalDate.now().minusDays(1), null);

    mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_EN_COP"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.product.currency.code").value("COP"))
        // La tasa viaja como CADENA y con sus ocho decimales intactos.
        .andExpect(jsonPath("$.product.exchange.rate").value("0.00024096"))
        .andExpect(jsonPath("$.product.exchange.currency.code").value("USD"))
        // 1000,00 × 0,00024096 = 0,24096 → 0,24 con los dos decimales de USD.
        .andExpect(jsonPath("$.product.exchange.amount").value(0.24));
  }

  @Test
  @DisplayName(
      "`CA-PM-129` — sin tasa vigente el producto se devuelve IGUAL, con la conversión vacía")
  void sinTasaVigenteElProductoSeDevuelveIgual() throws Exception {
    productoEnMoneda("HL_SIN_TASA", "Sin tasa", cop, "1000.00");

    // Responder `404` escondería un producto perfectamente vendible porque
    // nadie declaró una tasa.
    mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_SIN_TASA"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.product.code").value("HL_SIN_TASA"))
        .andExpect(jsonPath("$.product.exchange").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("`CA-PM-130` — si el producto ya está en la moneda de casa, no hay conversión")
  void enLaMonedaDeCasaNoHayConversion() throws Exception {
    mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_UPGRADE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.product.exchange").value(org.hamcrest.Matchers.nullValue()));
  }

  // ---------------------------------------------------------------------------
  // El precio que se publica (`RN-PM-024`) — 08-09-2026; el de compra SALE de
  // aquí desde el 12-09-2026
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-PM-161` — se publica `price` aunque el producto tenga precio de compra")
  void publicaElPrecioQueSeCobra() throws Exception {
    jdbc.update(
        "UPDATE products SET price = 49.99, purchase_price = 30.00 WHERE code = 'HL_UPGRADE'");

    mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_UPGRADE"))
        .andExpect(status().isOk())
        // `price` es SIEMPRE el que se cobra, y el costo no lo altera.
        .andExpect(jsonPath("$.product.price").value(49.99));
  }

  @Test
  @DisplayName("`CA-PM-161` — sin precio de compra, la respuesta es exactamente la misma")
  void sinPrecioDeCompraLaRespuestaNoCambia() throws Exception {
    jdbc.update(
        "UPDATE products SET price = 49.99, purchase_price = NULL WHERE code = 'HL_UPGRADE'");
    String sinCosto =
        mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_UPGRADE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.product.price").value(49.99))
            .andReturn()
            .getResponse()
            .getContentAsString();

    jdbc.update("UPDATE products SET purchase_price = 30.00 WHERE code = 'HL_UPGRADE'");
    String conCosto =
        mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_UPGRADE"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Es la prueba de que la columna NO SE SELECCIONA: un cambio en el costo no
    // puede notarse desde un enlace público, ni en el precio ni en la
    // conversión.
    assertThat(conCosto).isEqualTo(sinCosto);
  }

  @Test
  @DisplayName("`CA-PM-162` — la conversión se calcula sobre `price`, nunca sobre el costo")
  void laConversionSaleDelPrecio() throws Exception {
    productoEnMoneda("HL_DOS_PRECIOS", "Dos precios", cop, "1000.00");
    jdbc.update("UPDATE products SET purchase_price = 2000.00 WHERE code = 'HL_DOS_PRECIOS'");
    tasa(cop, USD, "0.00024096", LocalDate.now().minusDays(1), null);

    // Si la conversión saliera del costo, con la tasa delante cualquiera podría
    // deducirlo aunque el campo no viajara — sin token. Por eso se prueba con
    // un costo que da un resultado DISTINTO al del precio.
    mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_DOS_PRECIOS"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.product.price").value(1000.00))
        // 1000,00 × 0,00024096 = 0,24096 → 0,24. Con el costo habría dado 0,48,
        // que es exactamente el doble.
        .andExpect(jsonPath("$.product.exchange.amount").value(0.24));
  }

  @Test
  @DisplayName("`CA-PM-229` — el hotlink publica `videoUrl` sin token, tal cual, y sin el costo")
  void elVideoViajaSinToken() throws Exception {
    // Sin video: presente y nulo, también sin token.
    String sinVideo =
        mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_BOT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.product.videoUrl").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(sinVideo).contains("\"videoUrl\":null");

    // Con video y con costo declarado: viaja el primero TAL CUAL —la dirección
    // que administración escribió, sin seguirla— y el segundo no. Es la única
    // pareja de columnas opcionales que esta lectura separa (`pm.md` §5.2.8).
    jdbc.update(
        "UPDATE products SET purchase_price = 30.00,"
            + " video_url = 'https://Vimeo.com/123456/' WHERE code = 'HL_BOT'");

    String cuerpo =
        mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_BOT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.product.videoUrl").value("https://Vimeo.com/123456/"))
            .andExpect(jsonPath("$.product.purchasePrice").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(cuerpo).doesNotContain("purchasePrice").doesNotContain("30.00");
  }

  @Test
  @DisplayName(
      "`CA-PM-238` — el hotlink publica `coverImageUrl` sin token, y la imagen tampoco lo pide")
  void laPortadaViajaSinToken() throws Exception {
    String sinPortada =
        mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_BOT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.product.coverImageUrl").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(sinPortada).contains("\"coverImageUrl\":null");

    UUID imagen = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO product_images (id, content_type, content) VALUES (CAST(? AS uuid),"
            + " 'image/png', decode('89504E470D0A1A0A00', 'hex'))",
        imagen.toString());
    jdbc.update(
        "UPDATE products SET cover_image_id = CAST(? AS uuid), purchase_price = 30.00"
            + " WHERE code = 'HL_BOT'",
        imagen.toString());

    String cuerpo =
        mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_BOT"))
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.product.coverImageUrl").value("/api/v1/product-images/" + imagen))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(cuerpo).doesNotContain("purchasePrice").doesNotContain("30.00");

    // Y la dirección que publica responde sin token (`RF-PM-016`).
    mvc.perform(get("/api/v1/product-images/{id}", imagen))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "image/png"));

    jdbc.update("UPDATE products SET cover_image_id = NULL, purchase_price = NULL");
    jdbc.update("DELETE FROM product_images");
  }

  @Test
  @DisplayName("`CA-PM-352` — HOTLINK y AMBOS resuelven; TIENDA y NINGUNO reciben el mismo 404")
  void losCuatroAlcances() throws Exception {
    // `HL_BOT` nace `AMBOS` (era `HOTLINKS`); se recorre el dominio entero.
    for (String alcance : new String[] {"HOTLINK", "AMBOS"}) {
      jdbc.update("UPDATE products SET scope = ? WHERE code = 'HL_BOT'", alcance);
      mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_BOT"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.product.code").value("HL_BOT"));
    }
    String tienda = cuerpo404("TIENDA");
    String ninguno = cuerpo404("NINGUNO");
    assertThat(ninguno).isEqualTo(tienda);
    jdbc.update("UPDATE products SET scope = 'AMBOS' WHERE code = 'HL_BOT'");
  }

  private String cuerpo404(String alcance) throws Exception {
    jdbc.update("UPDATE products SET scope = ? WHERE code = 'HL_BOT'", alcance);
    return mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_BOT"))
        .andExpect(status().isNotFound())
        .andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll("\"correlationId\":\"[^\"]*\"", "");
  }

  @Test
  @DisplayName("`CA-PM-163` — el precio de compra NO aparece en el cuerpo, bajo ningún nombre")
  void elPrecioDeCompraNoViajaSinToken() throws Exception {
    jdbc.update(
        "UPDATE products SET price = 49.99, purchase_price = 30.00 WHERE code = 'HL_UPGRADE'");

    String cuerpo =
        mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_UPGRADE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.product.purchasePrice").doesNotExist())
            .andExpect(jsonPath("$.product.publicPrice").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Esta prueba se ha INVERTIDO DOS VECES. Hasta el 08-09-2026 exigía que el
    // precio del sistema no apareciera; ese día pasó a AFIRMAR que viajaban los
    // dos (`CA-PM-169`); el 12-09-2026, con el segundo importe convertido en el
    // costo de NEXUS, vuelve a exigir la ausencia. Se invierte en vez de
    // borrarse para que la tercera vez haga falta decidirlo — y no lo descubra
    // quien reciba un enlace por mensajería.
    assertThat(cuerpo).contains("49.99");
    assertThat(cuerpo).doesNotContain("30.00").doesNotContain("30.0,");
    assertThat(cuerpo).doesNotContain("purchasePrice").doesNotContain("publicPrice");
  }

  @Test
  @DisplayName("`CA-PM-136` — responde lo mismo con un token válido que sin él")
  void elTokenNoCambiaNada() throws Exception {
    String sinToken =
        mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_UPGRADE"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String conToken =
        mvc.perform(
                get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_UPGRADE")
                    .with(
                        org.springframework.security.test.web.servlet.request
                            .SecurityMockMvcRequestPostProcessors.user(UUID.randomUUID().toString())
                            .authorities(() -> "products:read")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(conToken).isEqualTo(sinToken);
  }

  @Test
  @DisplayName("`CA-PM-131` a `CA-PM-134` — LOS SEIS CASOS RESPONDEN EL MISMO CUERPO")
  void elMismoCuerpoEnLosSeisCasos() throws Exception {
    // ES LA PRUEBA QUE DEFINE EL REQUERIMIENTO. Distinguir estos seis
    // convertiría el enlace en un oráculo: bastaría fijar un código bueno e ir
    // variando el usuario para saber quién existe — y, peor, quién es cliente.
    String[][] casos = {
      {"hl-nadie", "HL_UPGRADE"}, // el usuario no existe
      {"hl-clienta", "HL_UPGRADE"}, // existe y NO es fuerza comercial
      {"hl-vendedora", "NO_EXISTE"}, // el código no existe
      {"hl-vendedora", "HL_TIENDA"}, // alcance TIENDA: no se publica
      {"hl-vendedora", "HL_INACTIVO"}, // inactivo
      {"hl-vendedora", "HL_RETIRADO"} // retirado
    };

    List<String> cuerpos = new ArrayList<>();
    for (String[] caso : casos) {
      cuerpos.add(
          mvc.perform(get("/api/v1/hotlinks/{u}/{c}", caso[0], caso[1]))
              .andExpect(status().isNotFound())
              .andReturn()
              .getResponse()
              .getContentAsString());
    }

    // No basta con que los seis sean `404`: el CUERPO tiene que ser el mismo.
    // Se compara contra el primero, campo a campo, salvo lo que varía por
    // petición —la ruta y el identificador de correlación—.
    for (String cuerpo : cuerpos) {
      assertThat(normalizar(cuerpo))
          .as("los seis rechazos deben ser indistinguibles")
          .isEqualTo(normalizar(cuerpos.get(0)));
    }
  }

  /**
   * Quita lo que cambia por petición: la ruta pedida y la correlación.
   *
   * <p><b>La ruta viaja en {@code instance} y no en {@code path}</b>: el cuerpo de error del
   * sistema es un {@code ProblemDetail} de la RFC 9457. Normalizar solo {@code path} dejaba esta
   * prueba fallando siempre, porque {@code instance} lleva la URI pedida y esas <b>son</b>
   * distintas en los seis casos — que es justo lo que no se puede comparar aquí.
   */
  private static String normalizar(String cuerpo) {
    return cuerpo
        .replaceAll("\"instance\"\s*:\s*\"[^\"]*\"", "\"instance\":\"?\"")
        .replaceAll("\"path\"\s*:\s*\"[^\"]*\"", "\"path\":\"?\"")
        .replaceAll("\"correlationId\"\s*:\s*\"[^\"]*\"", "\"correlationId\":\"?\"")
        .replaceAll("\"timestamp\"\s*:\s*\"[^\"]*\"", "\"timestamp\":\"?\"");
  }

  @Test
  @DisplayName("el nombre de usuario y el código se comparan SIN distinguir mayúsculas")
  void laCajaNoImporta() throws Exception {
    // Un enlace se teclea y se comparte por mensajería: exigir la caja exacta
    // rompería la mitad de las visitas.
    mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "HL-VENDEDORA", "hl_upgrade"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.product.code").value("HL_UPGRADE"));
  }

  @Test
  @DisplayName("`T-13` — TRES consultas, CUATRO si hay conversión, UNA si el vendedor no procede")
  void elCosteDeLaLectura() {

    var estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();

    servicio.hotlink("hl-vendedora", "HL_UPGRADE");

    // TRES con un producto en la moneda de casa: el vendedor, el producto y la
    // moneda por omisión. La consulta de tasas NO se paga, porque la única
    // moneda de la respuesta es la de destino y no hay nada que convertir.
    //
    // Se cuenta aquí y no sobre la respuesta HTTP porque el JSON sería idéntico
    // con seis: un `N+1` en la única ruta pública del sistema no se ve mirando
    // el cuerpo.
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(3);

    estadisticas.clear();

    // CUATRO cuando sí hay algo que convertir: entra la consulta de tasas.
    productoEnMoneda("HL_COSTE", "En pesos", cop, "1000.00");
    tasa(cop, USD, "0.00024096", LocalDate.now().minusDays(1), null);
    estadisticas.clear();

    servicio.hotlink("hl-vendedora", "HL_COSTE");

    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(4);

    estadisticas.clear();

    // Y NADA de eso se paga si no hay a quién enseñárselo: quien recorre
    // nombres al azar no llega ni al producto.
    assertThatThrownBy(() -> servicio.hotlink("hl-clienta", "HL_UPGRADE"))
        .isInstanceOf(ResourceNotFoundException.class);

    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // Preparación
  // ---------------------------------------------------------------------------

  private void limpiar() {
    jdbc.update("DELETE FROM exchange_rates");
    jdbc.update("DELETE FROM products");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'hl-%')");
    // Antes que las membresías: `user_memberships` las referencia, y `V57` da
    // una a toda persona.
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'hl-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'hl-%'");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM memberships");
    jdbc.update("DELETE FROM currencies WHERE is_default = false");
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

  private String moneda(String codigo, String nombre) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)"
            + " VALUES (CAST(? AS uuid), ?, ?, '$', 2, false, true)",
        id.toString(),
        codigo,
        nombre);
    return id.toString();
  }

  /** Una persona con el rol indicado. El nombre de usuario lleva prefijo para poder limpiarlo. */
  private void persona(String usuario, String nombre, String apellido, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, username, email, first_name, last_name, password_hash, status,"
            + " country_id)"
            + " VALUES (CAST(? AS uuid), ?, ?, ?, ?, 'x', 'ACTIVO',"
            + " (SELECT id FROM countries ORDER BY code LIMIT 1))",
        id.toString(),
        "hl-" + usuario,
        "hl-" + usuario + "@nexus.test",
        nombre,
        apellido);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT CAST(? AS uuid), CAST(? AS uuid), role_type FROM roles WHERE id = CAST(? AS uuid)",
        id.toString(),
        rol,
        rol);
  }

  private void producto(
      String codigo, String nombre, UUID destino, String alcance, String estado, boolean retirado) {
    productoCompleto(codigo, nombre, destino, alcance, estado, retirado, USD, "49.99");
  }

  private void productoEnMoneda(String codigo, String nombre, String moneda, String precio) {
    productoCompleto(codigo, nombre, null, "AMBOS", "ACTIVO", false, moneda, precio);
  }

  private void bot(String codigo, String nombre, String alcance) {
    productoCompleto(codigo, nombre, null, alcance, "ACTIVO", false, USD, "10.00");
  }

  private void productoCompleto(
      String codigo,
      String nombre,
      UUID destino,
      String alcance,
      String estado,
      boolean retirado,
      String moneda,
      String precio) {
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, source_membership_id,"
            + " target_membership_id, price, currency_id, status, deleted_at)"
            + " VALUES (?, 'MANUAL', CAST(? AS uuid), ?, ?, ?, CAST(? AS uuid),"
            + " CAST(? AS uuid), CAST(? AS numeric), CAST(? AS uuid), ?, CAST(? AS timestamptz))",
        alcance,
        UUID.randomUUID().toString(),
        codigo,
        destino == null ? "BOT" : "UPGRADE_MEMBRESIA",
        nombre,
        destino == null ? null : free.toString(),
        destino == null ? null : destino.toString(),
        precio,
        moneda,
        estado,
        retirado ? "2026-09-01T00:00:00Z" : null);
  }

  private void tasa(
      String origen, String destino, String precio, LocalDate desde, LocalDate hasta) {
    jdbc.update(
        "INSERT INTO exchange_rates (id, source_currency_id, target_currency_id, price,"
            + " valid_from, valid_to, is_active)"
            + " VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), CAST(? AS numeric),"
            + " CAST(? AS date), CAST(? AS date), true)",
        UUID.randomUUID().toString(),
        origen,
        destino,
        precio,
        desde.toString(),
        hasta == null ? null : hasta.toString());
  }
}
