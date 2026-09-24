package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
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
 * `RF-MV-011` — comprar por el hotlink de un vendedor.
 *
 * <h2>El escenario se siembra con un cliente que YA tiene principal</h2>
 *
 * <p>Y no es un detalle de la siembra: es lo único que hace que estas pruebas sirvan. Con un
 * cliente <b>sin</b> vendedores, atribuir por el enlace y atribuir por {@code client_sellers} dan
 * el mismo resultado, y toda esta suite pasaría con el código anterior —el que acreditaba la venta
 * al agente principal, que es el defecto que este requerimiento corrige—.
 */
@AutoConfigureMockMvc
class BuyByHotlinkIT extends IntegrationTestBase {

  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 9, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID cliente;
  private UUID principal;
  private UUID delEnlace;
  private UUID producto;

  @BeforeEach
  void sembrar() {
    limpiar();
    UUID rolVendedor = rol("BH_VENDEDOR", "VENDEDOR");
    principal = persona("bh-principal");
    delEnlace = persona("bh-del-enlace");
    cliente = persona("bh-cliente");
    darRol(principal, rolVendedor);
    darRol(delEnlace, rolVendedor);
    // EL CLIENTE YA TIENE AGENTE. Sin esta linea la suite no prueba nada.
    colgarDe(cliente, principal);
    producto = bot("BH_BOT");
  }

  @AfterEach
  void vaciar() {
    limpiar();
  }

  @Test
  @DisplayName(
      "`CA-MV-189`, `CA-MV-190` y `CA-MV-192` — la venta es del DUEÑO DEL ENLACE, nace el vínculo"
          + " y queda VALIDADO")
  void laVentaEsDelDuenoDelEnlace() throws Exception {
    String cuerpo =
        mvc.perform(comprar(cliente, "bh-del-enlace", "BH_BOT"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID venta = idDe(cuerpo);

    // `CA-MV-189`: la linea es del dueño del enlace y NO del principal, que es el
    // resultado que daba el sistema antes de este requerimiento.
    assertThat(vendedorDeLaLinea(venta)).isEqualTo(delEnlace);
    assertThat(vendedorDeLaLinea(venta)).isNotEqualTo(principal);

    // `CA-MV-190`: el vinculo nace, con ESTA venta como la primera.
    Map<String, Object> vinculo = vinculo(cliente, delEnlace);
    assertThat(vinculo).containsEntry("origin", "HOTLINK");
    assertThat(vinculo.get("first_movement_id")).hasToString(venta.toString());

    // `CA-MV-192`: VALIDADO aunque el cliente quede con DOS vendedores. Es la
    // excepcion a `RN-MV-034`, y sin ella esta venta naceria por validar.
    assertThat(cuantosVendedores(cliente)).isEqualTo(2);
    assertThat(estadoDelTipo(venta)).isEqualTo("VALIDADO");
  }

  @Test
  @DisplayName(
      "`CA-MV-191` — el agente PRINCIPAL no se mueve: comprar suma un vendedor, no sustituye")
  void elPrincipalNoSeMueve() throws Exception {
    Map<String, Object> antes = vinculo(cliente, principal);

    mvc.perform(comprar(cliente, "bh-del-enlace", "BH_BOT")).andExpect(status().isCreated());

    Map<String, Object> despues = vinculo(cliente, principal);
    // LA MISMA PERSONA Y LA MISMA FECHA. Es la afirmacion que no falla sola: la
    // venta se atribuye bien aunque el principal se hubiera reescrito.
    assertThat(despues).containsEntry("origin", "REGISTRO");
    assertThat(despues.get("created_at")).isEqualTo(antes.get("created_at"));
    assertThat(cuantosPrincipales(cliente)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-MV-193` — comprar DOS veces por el mismo enlace deja UN vínculo, con la primera venta")
  void dosComprasUnSoloVinculo() throws Exception {
    String primera =
        mvc.perform(comprar(cliente, "bh-del-enlace", "BH_BOT"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID ventaUno = idDe(primera);

    mvc.perform(comprar(cliente, "bh-del-enlace", "BH_BOT")).andExpect(status().isCreated());

    assertThat(cuantosVendedores(cliente)).isEqualTo(2);
    // Y CONSERVA LA PRIMERA: `first_movement_id` significa la venta que creo el
    // vinculo, no la ultima que hubo.
    assertThat(vinculo(cliente, delEnlace).get("first_movement_id"))
        .hasToString(ventaUno.toString());
  }

  @Test
  @DisplayName("`CA-MV-194` — por el PROPIO enlace se rechaza, y no deja venta ni vínculo")
  void porElPropioEnlaceSeRechaza() throws Exception {
    long ventasAntes = cuantasVentas();

    mvc.perform(comprar(delEnlace, "bh-del-enlace", "BH_BOT"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    // LAS DOS MITADES: ni venta ni vinculo. Un rechazo que dejara la venta seria
    // peor que no rechazar, porque quedaria sin atribucion.
    assertThat(cuantasVentas()).isEqualTo(ventasAntes);
    assertThat(cuantosVendedores(delEnlace)).isZero();
  }

  @Test
  @DisplayName(
      "`CA-MV-195` — el enlace que no resuelve responde el MISMO cuerpo, falle lo que falle")
  void elEnlaceQueNoResuelve() throws Exception {
    String porElVendedor =
        mvc.perform(comprar(cliente, "no-existe-nadie", "BH_BOT"))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String porElProducto =
        mvc.perform(comprar(cliente, "bh-del-enlace", "NO_EXISTE"))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // IDENTICOS SALVO LA MARCA DE TIEMPO: distinguirlos convertiria la ruta en un
    // oraculo para averiguar quien trabaja aqui y con que nombre de usuario.
    assertThat(sinInstante(porElVendedor)).isEqualTo(sinInstante(porElProducto));
  }

  @Test
  @DisplayName("`CA-MV-196` — sin `products:buy-by-hotlink`, 403; con él, 201")
  void acceso() throws Exception {
    mvc.perform(
            post("/api/v1/hotlinks/{u}/{c}/purchases", "bh-del-enlace", "BH_BOT")
                .with(user(cliente.toString()).authorities(() -> "movements:list-own"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());

    mvc.perform(comprar(cliente, "bh-del-enlace", "BH_BOT")).andExpect(status().isCreated());
  }

  // ---------------------------------------------------------------- peticiones

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder comprar(
      UUID quien, String username, String codigo) {
    return post("/api/v1/hotlinks/{u}/{c}/purchases", username, codigo)
        .with(comprador(quien))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"paymentMethodId\":\"" + TARJETA + "\"}");
  }

  private static RequestPostProcessor comprador(UUID persona) {
    return user(persona.toString()).authorities(() -> "products:buy-by-hotlink");
  }

  // ---------------------------------------------------------------- lecturas

  private static UUID idDe(String cuerpo) {
    return UUID.fromString(cuerpo.replaceAll(".*\"id\"\\s*:\\s*\"([0-9a-f-]{36})\".*", "$1"));
  }

  /** El cuerpo del error sin su marca de tiempo, que es lo único que puede diferir. */
  private static String sinInstante(String cuerpo) {
    return cuerpo.replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "\"timestamp\":\"\"");
  }

  private UUID vendedorDeLaLinea(UUID venta) {
    return jdbc.queryForObject(
        "SELECT seller_id FROM movement_details WHERE movement_id = ?::uuid", UUID.class, venta);
  }

  private String estadoDelTipo(UUID venta) {
    return jdbc.queryForObject(
        "SELECT s.code FROM movements m JOIN movement_type_statuses s ON s.id = m.type_status_id"
            + " WHERE m.id = ?::uuid",
        String.class,
        venta);
  }

  private Map<String, Object> vinculo(UUID cliente, UUID vendedor) {
    return jdbc.queryForMap(
        "SELECT origin, first_movement_id, created_at FROM client_sellers"
            + " WHERE client_id = ?::uuid AND seller_id = ?::uuid",
        cliente,
        vendedor);
  }

  private int cuantosVendedores(UUID cliente) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM client_sellers WHERE client_id = ?::uuid", Integer.class, cliente);
  }

  private int cuantosPrincipales(UUID cliente) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM client_sellers WHERE client_id = ?::uuid AND origin = 'REGISTRO'",
        Integer.class,
        cliente);
  }

  private long cuantasVentas() {
    return jdbc.queryForObject("SELECT count(*) FROM movements", Long.class);
  }

  // ---------------------------------------------------------------- siembra

  private UUID rol(String codigo, String tipo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO roles (id, code, name, role_type) VALUES (?::uuid, ?, ?, ?)",
        id,
        codigo,
        codigo,
        tipo);
    return id;
  }

  private void darRol(UUID persona, UUID rol) {
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?::uuid, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        persona,
        rol);
  }

  private UUID persona(String username) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?::uuid, ?, ?, 'Ana', 'Ruiz', 'no-se-usa', false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@nexus.test");
    return id;
  }

  private void colgarDe(UUID cliente, UUID vendedor) {
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin, first_movement_id, created_at)"
            + " VALUES (?::uuid, ?::uuid, 'REGISTRO', NULL, ?)",
        cliente,
        vendedor,
        BASE);
  }

  /** Un bot de alcance `AMBOS`: se ofrece por la tienda y por el enlace (`RN-PM-021`). */
  private UUID bot(String codigo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO products (id, code, type, name, description, price, currency_id,
                              validity_days, status, scope, implementation, created_at, updated_at)
        VALUES (?::uuid, ?, 'BOT', ?, 'Sembrado por BuyByHotlinkIT', 100.00, ?::uuid,
                30, 'ACTIVO', 'AMBOS', 'MANUAL', ?, ?)
        """,
        id,
        codigo,
        codigo,
        USD,
        BASE,
        BASE);
    return id;
  }

  private void limpiar() {
    // EL ORDEN IMPORTA: los detalles apuntan a productos y a personas.
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update(
        "DELETE FROM client_sellers WHERE client_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'bh-%')"
            + " OR seller_id IN (SELECT id FROM users WHERE username LIKE 'bh-%')");
    jdbc.update("DELETE FROM products WHERE code LIKE 'BH\\_%'");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'bh-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'bh-%'");
    jdbc.update("DELETE FROM roles WHERE code LIKE 'BH\\_%'");
  }
}
