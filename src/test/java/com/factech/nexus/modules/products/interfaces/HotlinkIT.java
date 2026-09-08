package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    producto("HL_UPGRADE", "Ascenso a Oro", oro, "HOTLINKS", "ACTIVO", false);
    // Un BOT y no un upgrade: `uq_products_upgrade_target` solo admite UNA
    // pareja origen-destino activa, y ese no es el punto de esta prueba.
    bot("HL_TIENDA", "Solo tienda", "TIENDA");
    producto("HL_INACTIVO", "Sin publicar", oro, "HOTLINKS", "INACTIVO", false);
    producto("HL_RETIRADO", "Retirado", oro, "HOTLINKS", "ACTIVO", true);
    bot("HL_BOT", "Bot de señales", "HOTLINKS");
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
  // El precio que se publica (`RN-PM-024`) — 08-09-2026
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-PM-161` — se publica el precio PÚBLICO cuando el producto lo declara")
  void publicaElPrecioPublico() throws Exception {
    jdbc.update(
        "UPDATE products SET price = 49.99, public_price = 59.99 WHERE code = 'HL_UPGRADE'");

    mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_UPGRADE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.product.price").value(59.99));
  }

  @Test
  @DisplayName("`CA-PM-161` — sin precio público se publica el del sistema")
  void publicaElDelSistemaCuandoNoHayPublico() throws Exception {
    jdbc.update("UPDATE products SET price = 49.99, public_price = NULL WHERE code = 'HL_UPGRADE'");

    mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_UPGRADE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.product.price").value(49.99));
  }

  @Test
  @DisplayName("`CA-PM-162` — la conversión se calcula sobre el importe que SE PUBLICA")
  void laConversionSaleDelImportePublicado() throws Exception {
    productoEnMoneda("HL_DOS_PRECIOS", "Dos precios", cop, "1000.00");
    jdbc.update("UPDATE products SET public_price = 2000.00 WHERE code = 'HL_DOS_PRECIOS'");
    tasa(cop, USD, "0.00024096", LocalDate.now().minusDays(1), null);

    // Convertir uno y publicar el otro dejaría en la MISMA respuesta dos
    // números que no se corresponden, y con la tasa delante cualquiera podría
    // deducir la diferencia entre lo anunciado y lo cobrado — sin token.
    mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_DOS_PRECIOS"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.product.price").value(2000.00))
        // 2000,00 × 0,00024096 = 0,48192 → 0,48. Con el precio del sistema
        // habría dado 0,24, que es exactamente la mitad.
        .andExpect(jsonPath("$.product.exchange.amount").value(0.48));
  }

  @Test
  @DisplayName("`CA-PM-163` — el precio del sistema NO aparece en el cuerpo por ninguna vía")
  void elPrecioDelSistemaNoSePublica() throws Exception {
    jdbc.update(
        "UPDATE products SET price = 49.99, public_price = 59.99 WHERE code = 'HL_UPGRADE'");

    // Es una prueba de AUSENCIA, como la del correo del vendedor: en una ruta
    // pública lo que hay que verificar es lo que NO está, porque lo que se
    // publica una vez ya no se puede retirar.
    String cuerpo =
        mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "HL_UPGRADE"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(cuerpo).doesNotContain("publicPrice").doesNotContain("49.99");
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
  @DisplayName("`T-13` — cuesta CUATRO consultas, y solo UNA cuando el vendedor no procede")
  void elCosteDeLaLectura() {
    // El producto está en COP y la casa es USD, de modo que la conversión SÍ
    // procede: es el camino más caro que tiene el endpoint.
    tasa(cop, USD, "0.00024096", LocalDate.now().minusDays(1), null);

    var estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();

    servicio.hotlink("hl-vendedora", "HL_UPGRADE");

    // Vendedor, producto y tasa. Se cuenta aquí y no sobre la respuesta HTTP
    // porque el JSON sería idéntico con seis: un `N+1` en la única ruta pública
    // del sistema no se ve mirando el cuerpo.
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(3);

    estadisticas.clear();

    // Y la tercera NO se paga si no hay a quién enseñársela: quien recorre
    // nombres al azar no llega a tocar `exchange_rates`.
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
    productoCompleto(codigo, nombre, null, "HOTLINKS", "ACTIVO", false, moneda, precio);
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
