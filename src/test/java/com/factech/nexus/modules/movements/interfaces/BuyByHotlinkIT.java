package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.CommissionCleanup;
import com.fasterxml.jackson.databind.ObjectMapper;
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

  @Autowired private com.factech.nexus.modules.movements.FakeCardGateway pasarela;

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
      "CA-MV-426 y CA-MV-471 — con tarjeta, la compra por hotlink abre el cobro en la pasarela,"
          + " anota su referencia en el pago y devuelve el secreto (01-10-2026)")
  void conTarjetaAbreElCobro() throws Exception {
    pasarela.reiniciar();
    pasarela.encender(true);
    try {
      String cuerpo =
          mvc.perform(comprar(cliente, "bh-del-enlace", "BH_BOT"))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.status").value("PENDIENTE"))
              .andExpect(jsonPath("$.cardCharge.gateway").value("STRIPE"))
              .andExpect(jsonPath("$.cardCharge.clientSecret").value("pi_prueba_1_secret_prueba"))
              .andReturn()
              .getResponse()
              .getContentAsString();
      UUID venta = idDe(cuerpo);
      assertThat(
              jdbc.queryForObject(
                  "SELECT provider_reference FROM payments WHERE movement_id = ?",
                  String.class,
                  venta))
          .isEqualTo("pi_prueba_1");
      assertThat(pasarela.abiertos()).hasSize(1);
      assertThat(pasarela.abiertos().get(0).movementId()).isEqualTo(venta);
    } finally {
      pasarela.reiniciar();
    }
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
    assertThat(loQueDiscrimina(porElVendedor)).isEqualTo(loQueDiscrimina(porElProducto));
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

  // -------------------------------------------------------------- la oficina

  @Test
  @DisplayName(
      "CA-MV-713 — un cliente cuyo agente es de OTRA oficina compra por el enlace: la línea lleva"
          + " la oficina del director del DUEÑO DEL ENLACE, en lo guardado y en la auditoría, y el"
          + " cuerpo no la trae (09-10-2026)")
  void laOficinaEsLaDelDuenoDelEnlace() throws Exception {
    // Dos oficinas y dos directores: el principal del cliente cuelga del sur, y
    // el dueño del enlace del norte. Si la oficina se resolviera por el agente
    // del cliente —el defecto que `RF-MV-011` ya corrigió para el vendedor—,
    // saldría el sur.
    UUID directorNorte = persona("bh-director-norte");
    UUID directorSur = persona("bh-director-sur");
    UUID norte = equipo("BH Oficina Norte");
    UUID sur = equipo("BH Oficina Sur");
    pertenencia(norte, directorNorte);
    pertenencia(sur, directorSur);
    reportaA(delEnlace, directorNorte);
    reportaA(principal, directorSur);

    String cuerpo =
        mvc.perform(comprar(cliente, "bh-del-enlace", "BH_BOT"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID venta = idDe(cuerpo);

    assertThat(oficinaDeLaLinea(venta)).isEqualTo(norte);
    assertThat(
            jdbc.queryForObject(
                "SELECT changes::text FROM audit_change_log"
                    + " WHERE module = 'MV' AND action = 'CREATE' AND entity_id = ?",
                String.class,
                venta))
        .contains("\"team_id\": \"" + norte + "\"")
        .doesNotContain(sur.toString());

    // Quien compra no ve la oficina, como no ve el vendedor (`RF-MV-002` §4.3):
    // ni la clave ni el identificador en ningún otro sitio del cuerpo.
    assertThat(cuerpo)
        .doesNotContain("\"team\"")
        .doesNotContain("\"seller\"")
        .doesNotContain(norte.toString());
  }

  // ------------------------------------------------------------- el escalón

  @Test
  @DisplayName(
      "`CA-MV-530` — dos niveles por debajo del destino, un upgrade de UN escalón se rechaza con"
          + " `EX-005` porque para quien compra SALTA, y no deja venta, pago ni vínculo")
  void elEscalonEsDeQuienCompra() throws Exception {
    UUID[] m = cadena();
    // `M2 → M1` es un escalón como producto, y el hotlink lo publica: no mira el
    // origen. Para quien está en `M3` son dos escalones (`RN-MV-006`).
    upgrade("BH_UP_M2_M1", m[1], m[0]);
    asignarMembresia(cliente, m[2]);

    String cuerpo =
        mvc.perform(comprar(cliente, "bh-del-enlace", "BH_UP_M2_M1"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errors[0].code").value("EX-005"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(cuerpo).contains("salta niveles");
    assertThat(cuantasVentas()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isZero();
    assertThat(cuantosVendedores(cliente)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-MV-531` — un nivel por debajo del destino, el upgrade se registra aunque su origen no"
          + " sea el de quien compra")
  void elOrigenAjenoNoImporta() throws Exception {
    UUID[] m = cadena();
    // La renovación de `M1`: origen `M1`, que no es el del cliente (`M2`). Para
    // él es subir un escalón.
    upgrade("BH_UP_M1_M1", m[0], m[0]);
    asignarMembresia(cliente, m[1]);

    mvc.perform(comprar(cliente, "bh-del-enlace", "BH_UP_M1_M1")).andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "`CA-MV-532` — un salto ya registrado no se compra por el enlace: `404` con el mismo cuerpo"
          + " que un producto inexistente")
  void elSaltoRegistradoEsUnCuatroCientosCuatro() throws Exception {
    UUID[] m = cadena();
    // Sembrado por la base: el alta ya no lo admite (`RN-PM-018`).
    upgrade("BH_UP_M3_M1", m[2], m[0]);
    asignarMembresia(cliente, m[2]);

    String porElSalto =
        mvc.perform(comprar(cliente, "bh-del-enlace", "BH_UP_M3_M1"))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String porElInexistente =
        mvc.perform(comprar(cliente, "bh-del-enlace", "NO_EXISTE"))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(loQueDiscrimina(porElSalto)).isEqualTo(loQueDiscrimina(porElInexistente));
    assertThat(cuantasVentas()).isZero();
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

  /**
   * El identificador de la venta, leído del JSON y no con una expresión regular.
   *
   * <p>La que había aquí empezaba con {@code .*} <b>codicioso</b>, de modo que se quedaba con el
   * ÚLTIMO {@code "id"} del cuerpo —el de una línea, o el de la moneda— en lugar del de la venta.
   * No fallaba al compilar ni se veía al leerla: fallaba al consultar, con un «cero filas» que
   * parecía un problema del código de producción.
   */
  private static UUID idDe(String cuerpo) throws Exception {
    return UUID.fromString(new ObjectMapper().readTree(cuerpo).get("id").asText());
  }

  /**
   * El cuerpo del error sin los tres campos que pueden diferir <b>sin decir nada</b>.
   *
   * <p>{@code timestamp} y {@code correlationId} son distintos en cada petición por definición, y
   * {@code instance} es la ruta que se llamó — quien llama ya la conoce. Ninguno de los tres revela
   * si falló el vendedor o el producto.
   *
   * <p>Lo que <b>no</b> puede diferir es el resto —{@code type}, {@code title}, {@code status} y
   * {@code detail}—, que es donde se vería cuál de los casos ocurrió. Comparar el cuerpo entero
   * habría sido más estricto y <b>no más seguro</b>: solo habría hecho fallar la prueba por tres
   * campos que el diseño ya sabe que cambian.
   */
  private static String loQueDiscrimina(String cuerpo) {
    return cuerpo
        .replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "")
        .replaceAll("\"instance\"\\s*:\\s*\"[^\"]*\"", "")
        .replaceAll("\"correlationId\"\\s*:\\s*\"[^\"]*\"", "");
  }

  private UUID vendedorDeLaLinea(UUID venta) {
    return jdbc.queryForObject(
        "SELECT seller_id FROM movement_details WHERE movement_id = ?::uuid", UUID.class, venta);
  }

  private UUID oficinaDeLaLinea(UUID venta) {
    return jdbc.queryForObject(
        "SELECT team_id FROM movement_details WHERE movement_id = ?::uuid", UUID.class, venta);
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

  /**
   * CON PADRE, y no es opcional: `uq_roles_single_root` admite UN SOLO rol sin padre —la raiz— y un
   * rol de prueba suelto choca con el superadministrador. Cuelga de `MANAGER`, que es de su mismo
   * tipo y es donde colgaria de verdad.
   */
  private UUID rol(String codigo, String tipo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO roles (id, code, name, role_type, parent_role_id)"
            + " VALUES (?::uuid, ?, ?, ?, (SELECT r.id FROM roles r WHERE r.code = 'MANAGER'))",
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

  /** Una oficina (`RN-MV-078`), con el prefijo que `limpiar` reconoce. */
  private UUID equipo(String nombre) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO teams (id, name) VALUES (?, ?)", id, nombre);
    return id;
  }

  private void pertenencia(UUID equipo, UUID persona) {
    jdbc.update(
        "INSERT INTO team_members (id, team_id, user_id, started_at)"
            + " VALUES (gen_random_uuid(), ?, ?, now() - interval '30 days')",
        equipo,
        persona);
  }

  /** La cadena de mando: los roles no se miran, basta la fila vigente. */
  private void reportaA(UUID persona, UUID superior) {
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at)"
            + " VALUES (gen_random_uuid(), ?, ?, now() - interval '30 days')",
        persona,
        superior);
  }

  /** Un bot de alcance `AMBOS`: se ofrece por la tienda y por el enlace (`RN-PM-021`). */
  private UUID bot(String codigo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO products (id, code, type, name, description, price, currency_id,
                              validity_days, status, scope, implementation, created_at, updated_at)
        VALUES (?::uuid, ?, 'BOT', ?, 'Sembrado por BuyByHotlinkIT', 10000, ?::uuid,
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

  /**
   * Tres membresías propias, {@code BH_M1} (la más alta) a {@code BH_M3}, colgadas <b>debajo</b>
   * del eslabón más bajo que haya.
   *
   * <p>No se borra la cadena ajena para sembrar una entera, como hacen otras suites: la cadena es
   * una lista con una sola cima (`uq_memberships_parent`, `NULLS NOT DISTINCT`), y colgar la
   * nuestra al final la deja intacta sea cual sea su estado. `limpiar` las quita de abajo arriba.
   */
  private UUID[] cadena() {
    UUID[] m = new UUID[3];
    for (int i = 0; i < 3; i++) {
      UUID id = UUID.randomUUID();
      jdbc.update(
          """
          INSERT INTO memberships (id, code, name, parent_membership_id, level, color)
          SELECT ?::uuid, ?, ?, (SELECT id FROM memberships ORDER BY level DESC LIMIT 1),
                 COALESCE((SELECT max(level) FROM memberships), 0) + 1,
                 upper(lpad(to_hex(COALESCE((SELECT max(level) FROM memberships), 0) * 7919
                                   + 4096), 6, '0'))
          """,
          id,
          "BH_M" + (i + 1),
          "Nivel " + (i + 1) + " de hotlink");
      m[i] = id;
    }
    return m;
  }

  private void asignarMembresia(UUID persona, UUID membresia) {
    jdbc.update(
        "INSERT INTO user_products (id, user_id, membership_id, started_at, ends_at)"
            + " VALUES (gen_random_uuid(), ?::uuid, ?::uuid, ?, NULL)",
        persona,
        membresia,
        BASE);
  }

  /** Un upgrade de alcance `AMBOS`, sembrado por la base (el alta ya no admite saltos). */
  private UUID upgrade(String codigo, UUID origen, UUID destino) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO products (id, code, type, name, description, price, currency_id,
                              validity_days, status, scope, implementation,
                              source_membership_id, target_membership_id, created_at, updated_at)
        VALUES (?::uuid, ?, 'UPGRADE_MEMBRESIA', ?, 'Sembrado por BuyByHotlinkIT', 10000,
                ?::uuid, 30, 'ACTIVO', 'AMBOS', 'AUTOMATICA', ?::uuid, ?::uuid, ?, ?)
        """,
        id,
        codigo,
        codigo,
        USD,
        origen,
        destino,
        BASE,
        BASE);
    return id;
  }

  private void limpiar() {
    // LOS VINCULOS VAN PRIMERO, y es justo lo que este requerimiento crea:
    // `client_sellers.first_movement_id` apunta a `movements`, de modo que borrar
    // los movimientos antes falla — y el fallo no se queda aqui. Los movimientos
    // sobreviven, y TODA suite posterior que borre usuarios revienta contra
    // `fk_movements_user`, con un error que no nombra a esta clase. Costo 420
    // errores en la primera corrida verde de RF-MV-011.
    //
    // Es la misma leccion que `product_links` y `user_products` ya dejaron
    // escritas, y el mismo orden: lo que apunta, antes que lo apuntado.
    jdbc.update(
        "DELETE FROM client_sellers WHERE client_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'bh-%')"
            + " OR seller_id IN (SELECT id FROM users WHERE username LIKE 'bh-%')");
    CommissionCleanup.limpiar(jdbc);
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM products WHERE code LIKE 'BH\\_%'");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'bh-%')");
    // Las oficinas de `CA-MV-713`: las líneas ya no las nombran, y las
    // pertenencias y la cadena van antes que las personas y los equipos
    // (`fk_team_members_user` es RESTRICT). Solo los equipos de esta suite.
    jdbc.update(
        "DELETE FROM team_members WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'bh-%')"
            + " OR team_id IN (SELECT id FROM teams WHERE name LIKE 'BH %')");
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'bh-%')"
            + " OR supervisor_id IN (SELECT id FROM users WHERE username LIKE 'bh-%')");
    jdbc.update("DELETE FROM teams WHERE name LIKE 'BH %'");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'bh-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'bh-%'");
    jdbc.update("DELETE FROM roles WHERE code LIKE 'BH\\_%'");
    // De abajo arriba: cada una es la superior de la siguiente.
    for (String codigo : java.util.List.of("BH_M3", "BH_M2", "BH_M1")) {
      jdbc.update("DELETE FROM memberships WHERE code = ?", codigo);
    }
  }
}
