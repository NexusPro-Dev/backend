package com.factech.nexus.modules.products.interfaces;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * La oferta disponible para uno mismo (`RF-PM-007` · `T-06` a `T-11`).
 *
 * <p>Cubre los criterios de `spec.md` §12. El catálogo y la cadena se siembran <b>por la base</b>:
 * hace falta fijar el estado, la marca de retiro y el instante de alta, y ninguno de los tres se
 * puede pasar por HTTP.
 *
 * <h2>La cadena tiene CUATRO niveles a propósito</h2>
 *
 * <p>Es el bloqueo 2 de `tasks.md`, y la preparación de datos es la mitad del trabajo de estas
 * pruebas. Con dos niveles, «todos los superiores» y «solo el inmediato» dan el mismo resultado, de
 * modo que `CA-PM-089` no distinguiría una implementación de la otra. Con cuatro, quien está en el
 * suelo debe ver <b>tres</b> upgrades, y ver uno solo es un fallo.
 *
 * <h2>Y el orden de la cadena es el que fijó `V47`: 1 es la CIMA</h2>
 *
 * <p>{@code ORO(1) > PLATINO(2) > VIP(3) > FREE(4)}. Subir es ir a un número <b>menor</b>. Esa es
 * la comparación que el riesgo 1 del plan advierte que puede escribirse al revés, y {@link
 * #losTresCasosDeNivel()} es la prueba que lo detecta: sin ella, ofrecer bajadas en lugar de
 * subidas pasaría todo lo demás.
 */
@AutoConfigureMockMvc
class ProductOfferIT extends IntegrationTestBase {

  /** La moneda sembrada por `V15`, estable en todos los entornos. */
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID oro;
  private UUID platino;
  private UUID vip;
  private UUID free;

  private UUID enOro;
  private UUID enVip;
  private UUID enFree;
  private UUID sinMembresia;
  private UUID conMembresiaVencida;

  @BeforeEach
  void sembrar() {
    limpiar();

    // La cadena va encadenada de verdad: `uq_memberships_parent` es UNIQUE
    // NULLS NOT DISTINCT, de modo que solo UNA membresía puede no tener
    // superior. Dos raíces reventarían en el COMMIT, lejos de aquí.
    oro = membresia("ORO", "Oro", 1, null);
    platino = membresia("PLATINO", "Platino", 2, oro);
    vip = membresia("VIP", "Vip", 3, platino);
    free = membresia("FREE", "Free", 4, vip);

    // Un upgrade activo hacia cada nivel. El de FREE existe y está ACTIVO a
    // propósito: es el único que hace verificable `CA-PM-061` —que no se
    // ofrecen bajadas—. Con él inactivo, quedar fuera no probaría nada, porque
    // ya lo excluiría `RN-PM-009`.
    upgrade("UP_ORO", "Ascenso a Oro", oro, "100.00", 365, "ACTIVO", BASE, false);
    upgrade("UP_PLATINO", "Ascenso a Platino", platino, "50.00", 30, "ACTIVO", BASE, false);
    upgrade("UP_VIP", "Ascenso a Vip", vip, "20.00", null, "ACTIVO", BASE, false);
    upgrade("UP_FREE", "Ascenso a Free", free, "5.00", 7, "ACTIVO", BASE, false);

    // Lo que NO debe salir nunca (`CA-PM-058`). Los dos apuntan a ORO, y no
    // chocan con `UP_ORO` porque `uq_products_upgrade_target` es un índice
    // PARCIAL: solo alcanza a los activos y no retirados.
    upgrade(
        "UP_ORO_BORRADOR",
        "Ascenso a Oro (sin publicar)",
        oro,
        "90.00",
        365,
        "INACTIVO",
        BASE,
        false);
    upgrade("UP_ORO_RETIRADO", "Ascenso a Oro (retirado)", oro, "80.00", 365, "ACTIVO", BASE, true);

    bot("BOT_SENALES", "Bot de señales", "10.00", null, "ACTIVO", BASE.plusHours(2), false);
    bot("BOT_SOPORTE", "Bot de soporte", "99.50", 15, "ACTIVO", BASE.plusHours(3), false);
    bot("BOT_APAGADO", "Bot sin publicar", "1.00", null, "INACTIVO", BASE.plusHours(4), false);

    enOro = persona("oferta-oro");
    enVip = persona("oferta-vip");
    enFree = persona("oferta-free");
    sinMembresia = persona("oferta-sin");
    conMembresiaVencida = persona("oferta-vencida");

    asignar(enOro, oro, null);
    asignar(enVip, vip, null);
    asignar(enFree, free, null);
    // Venció ayer. No se retira la fila: la vigencia se evalúa al consultarla.
    asignar(conMembresiaVencida, free, OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
  }

  @AfterEach
  void vaciar() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // La comparación de niveles, que es donde se decide el requerimiento
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-PM-059`, `CA-PM-060`, `CA-PM-061` — los tres casos de nivel, en una sola vista")
  void losTresCasosDeNivel() throws Exception {
    // Quien está en VIP (3) tiene por encima a PLATINO (2) y ORO (1), en su
    // mismo peldaño a VIP y por debajo a FREE (4).
    //
    // Escrita al revés —`m.level > :nivel`— esta llamada devolvería `UP_FREE` y
    // nada más: una bajada de nivel, cobrada. Ninguna prueba de camino feliz lo
    // vería, y por eso los tres casos van juntos aquí.
    mvc.perform(oferta(enVip))
        .andExpect(status().isOk())
        // Superiores: los dos, y solo los dos.
        .andExpect(jsonPath("$.upgrades.content.length()").value(2))
        .andExpect(
            jsonPath("$.upgrades.content[*].code", Matchers.contains("UP_PLATINO", "UP_ORO")))
        // El de su propio nivel NO está: sería cobrarle por quedarse donde está.
        .andExpect(jsonPath("$.upgrades.content[*].code", Matchers.not(Matchers.hasItem("UP_VIP"))))
        // Y el inferior tampoco.
        .andExpect(
            jsonPath("$.upgrades.content[*].code", Matchers.not(Matchers.hasItem("UP_FREE"))));
  }

  @Test
  @DisplayName("`CA-PM-089` — quien está en el suelo ve TODOS los superiores, no solo el siguiente")
  void todosLosSuperioresYNoSoloElInmediato() throws Exception {
    // Es lo que la cadena de cuatro niveles existe para poder afirmar. Ofrecer
    // solo el inmediato obligaría a comprar tres veces para recorrerla, que es
    // una fuga de ventas disfrazada de simplicidad.
    mvc.perform(oferta(enFree))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content.length()").value(3))
        .andExpect(
            jsonPath(
                "$.upgrades.content[*].code", Matchers.contains("UP_VIP", "UP_PLATINO", "UP_ORO")));
  }

  @Test
  @DisplayName("`CA-PM-062` — quien está en la cima recibe la lista vacía, y no un error")
  void enLaCimaLaListaLlegaVacia() throws Exception {
    mvc.perform(oferta(enOro))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content").isArray())
        .andExpect(jsonPath("$.upgrades.content.length()").value(0))
        // No es un mensaje especial: los bots siguen ahí.
        .andExpect(jsonPath("$.services.content.length()").value(2))
        .andExpect(jsonPath("$.currentMembership.code").value("ORO"));
  }

  // ---------------------------------------------------------------------------
  // Quien no tiene nivel
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-PM-063`, `CA-PM-088` — sin membresía: ningún upgrade y todos los bots")
  void sinMembresiaNingunUpgradeYTodosLosBots() throws Exception {
    mvc.perform(oferta(sinMembresia))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content.length()").value(0))
        .andExpect(jsonPath("$.services.content.length()").value(2))
        .andExpect(jsonPath("$.currentMembership.code").doesNotExist())
        // Nulo PRESENTE y no ausente: es la respuesta a «¿desde dónde subo?», y
        // un campo que falta es indistinguible de uno que el cliente no conoce.
        // Se comprueba sobre el JSON crudo porque es la única forma de separar
        // «la clave vale null» de «la clave no está».
        .andExpect(content().string(Matchers.containsString("\"currentMembership\":null")));
  }

  @Test
  @DisplayName("`CA-PM-063` · `FA-003` — la membresía VENCIDA se comporta como la ausencia de una")
  void laMembresiaVencidaEsComoNoTenerla() throws Exception {
    // Vencer no es lo mismo que no tener, pero para decidir «a dónde puede
    // subir» produce el mismo resultado. Esta prueba es la que se apoya en que
    // `PM` NO reimplementa la vigencia: si la copiara mal, esta persona
    // aparecería en FREE y vería tres upgrades.
    mvc.perform(oferta(conMembresiaVencida))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content.length()").value(0))
        .andExpect(jsonPath("$.services.content.length()").value(2))
        .andExpect(jsonPath("$.currentMembership.code").doesNotExist())
        .andExpect(content().string(Matchers.containsString("\"currentMembership\":null")));
  }

  // ---------------------------------------------------------------------------
  // Lo que no se ofrece, y lo que la respuesta lleva
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-PM-058` — ni inactivos ni retirados, en ninguna de las dos colecciones")
  void soloLoActivoYVivo() throws Exception {
    mvc.perform(oferta(enFree))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.upgrades.content[*].code", Matchers.not(Matchers.hasItem("UP_ORO_BORRADOR"))))
        .andExpect(
            jsonPath(
                "$.upgrades.content[*].code", Matchers.not(Matchers.hasItem("UP_ORO_RETIRADO"))))
        .andExpect(
            jsonPath("$.services.content[*].code", Matchers.not(Matchers.hasItem("BOT_APAGADO"))));
  }

  @Test
  @DisplayName("`CA-PM-067` — la oferta no publica estado, retiro ni motivo de retiro")
  void niEstadoNiRetiroNiMotivo() throws Exception {
    // No basta con que no haya productos retirados: los tres campos no existen
    // en la respuesta, y eso es lo que impide que un cliente construya una
    // condición sobre un valor que siempre sería el mismo.
    mvc.perform(oferta(enFree))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content[0].status").doesNotExist())
        .andExpect(jsonPath("$.upgrades.content[0].deletedAt").doesNotExist())
        .andExpect(jsonPath("$.upgrades.content[0].deletionReason").doesNotExist())
        .andExpect(jsonPath("$.upgrades.content[0].createdAt").doesNotExist());
  }

  @Test
  @DisplayName("`CA-PM-064` — la respuesta dice desde qué nivel mira quien consulta")
  void devuelveElNivelActual() throws Exception {
    mvc.perform(oferta(enVip))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentMembership.code").value("VIP"))
        .andExpect(jsonPath("$.currentMembership.name").value("Vip"))
        .andExpect(jsonPath("$.currentMembership.level").value(3))
        .andExpect(jsonPath("$.currentMembership.id").value(vip.toString()));
  }

  @Test
  @DisplayName("`CA-PM-078`, `CA-PM-079` — agrupada por tipo; upgrades por nivel, bots por alta")
  void agrupadaYOrdenada() throws Exception {
    mvc.perform(oferta(enFree))
        .andExpect(status().isOk())
        // Los upgrades, del salto más corto al más largo: VIP(3), PLATINO(2),
        // ORO(1). Es el único orden en el que «subir» significa algo — ni el
        // precio ni el nombre lo expresan.
        .andExpect(
            jsonPath("$.upgrades.content[*].targetMembership.level", Matchers.contains(3, 2, 1)))
        // Y ningún bot se coló entre ellos.
        .andExpect(
            jsonPath(
                "$.upgrades.content[*].type", Matchers.everyItem(Matchers.is("UPGRADE_MEMBRESIA"))))
        // Los bots, por fecha de alta.
        .andExpect(
            jsonPath("$.services.content[*].code", Matchers.contains("BOT_SENALES", "BOT_SOPORTE")))
        .andExpect(jsonPath("$.services.content[*].type", Matchers.everyItem(Matchers.is("BOT"))));
  }

  @Test
  @DisplayName("`CA-PM-091` — las dos colecciones van ENVUELTAS, no como arreglos en la raíz")
  void coleccionesEnvueltas() throws Exception {
    // Es lo que permitirá paginar los bots el día que crezcan sin cambiar el
    // tipo de la propiedad, que sería un cambio incompatible para todos a la vez.
    mvc.perform(oferta(enFree))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades").isMap())
        .andExpect(jsonPath("$.services").isMap())
        .andExpect(jsonPath("$.upgrades.content").isArray())
        .andExpect(jsonPath("$.services.content").isArray());
  }

  @Test
  @DisplayName("`CA-PM-095` — la vigencia viaja siempre, y NULA en lo que no caduca")
  void laVigenciaViajaYDistingueLoQueNoCaduca() throws Exception {
    // Sin este dato, dos upgrades al mismo nivel y al mismo precio son
    // indistinguibles aunque uno dure un mes y el otro para siempre.
    mvc.perform(oferta(enFree))
        .andExpect(status().isOk())
        // `UP_VIP` no caduca: la clave existe y vale nulo.
        .andExpect(jsonPath("$.upgrades.content[0].code").value("UP_VIP"))
        .andExpect(content().string(Matchers.containsString("\"validityDays\":null")))
        .andExpect(jsonPath("$.upgrades.content[1].validityDays").value(30))
        .andExpect(jsonPath("$.upgrades.content[2].validityDays").value(365));
  }

  @Test
  @DisplayName("`CA-PM-090` — el mismo producto vale lo mismo mire quien mire")
  void elPrecioNoSeAjustaPorNivel() throws Exception {
    // Un importe distinto según quién mira sería un descuento, y los descuentos
    // son promociones — fuera de alcance a propósito.
    mvc.perform(oferta(enFree))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content[2].code").value("UP_ORO"))
        .andExpect(jsonPath("$.upgrades.content[2].price").value(100.00));

    mvc.perform(oferta(enVip))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content[1].code").value("UP_ORO"))
        .andExpect(jsonPath("$.upgrades.content[1].price").value(100.00));
  }

  // ---------------------------------------------------------------------------
  // Autorización y forma de la ruta
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-PM-065` — responde a quien está autenticado y NO tiene un solo permiso")
  void noExigeNingunPermiso() throws Exception {
    // `comoActor` no concede ninguna autoridad. Exigir `products:read` daría a
    // cada cliente el catálogo administrativo entero para ver tres líneas.
    mvc.perform(oferta(enFree)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("`CA-PM-066` — no admite parámetros: no hay forma de preguntar por otra persona")
  void ningunParametroCambiaLaRespuesta() throws Exception {
    // La ausencia es la implementación: no hay identificador que ignorar,
    // porque no hay nada que lo lea.
    mvc.perform(
            oferta(enVip)
                .param("userId", enFree.toString())
                .param("membershipId", free.toString())
                .param("page", "3"))
        .andExpect(status().isOk())
        // Sigue siendo la oferta de quien llama, no la de la persona indicada.
        .andExpect(jsonPath("$.currentMembership.code").value("VIP"))
        .andExpect(jsonPath("$.upgrades.content.length()").value(2));
  }

  @Test
  @DisplayName("`T-08` — la ruta literal no se confunde con `/products/{id}`")
  void laRutaLiteralGanaALaVariable() throws Exception {
    // Spring resuelve antes el segmento literal, y esta prueba es lo que impide
    // que un renombrado lo rompa en silencio: el síntoma sería un 400 por
    // identificador inválido en la única ruta que un cliente usa a diario.
    mvc.perform(get("/api/v1/products/available").with(comoActor(enFree)))
        .andExpect(status().isOk());

    // Y la de detalle sigue siendo otra cosa: exige permiso.
    mvc.perform(get("/api/v1/products/{id}", UUID.randomUUID()).with(comoActor(enFree)))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // Preparación
  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder oferta(UUID quien) {
    return get("/api/v1/products/available").with(comoActor(quien));
  }

  /**
   * Autenticado y <b>sin un solo permiso</b>.
   *
   * <p>Es deliberado: este endpoint no exige ninguno, y un actor con permisos no distinguiría eso
   * de tenerlos.
   */
  private RequestPostProcessor comoActor(UUID quien) {
    return user(quien.toString()).authorities();
  }

  private void limpiar() {
    jdbc.update("DELETE FROM products");
    // Antes que las membresías: `user_memberships` las referencia.
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'oferta-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'oferta-%'");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM memberships");
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

  /**
   * {@code fin} nulo significa indefinida; con fecha pasada, vencida.
   *
   * <p><b>La asignación empieza treinta días atrás</b> y no ahora: {@code
   * ck_user_memberships_periodo} exige que el fin sea posterior al inicio, de modo que una
   * membresía que nace hoy no puede haber vencido ayer. Es la restricción diciendo lo obvio —nadie
   * termina antes de empezar—, y sembrar el inicio en el pasado es lo que permite que el caso de
   * `FA-003` exista siquiera.
   */
  private void asignar(UUID quien, UUID membresia, OffsetDateTime fin) {
    jdbc.update(
        """
        INSERT INTO user_memberships (user_id, membership_id, started_at, ends_at,
                                      created_at, updated_at)
        VALUES (CAST(? AS uuid), CAST(? AS uuid), now() - interval '30 days',
                CAST(? AS timestamptz), now(), now())
        """,
        quien.toString(),
        membresia.toString(),
        fin == null ? null : fin.toString());
  }

  private void upgrade(
      String codigo,
      String nombre,
      UUID destino,
      String precio,
      Integer vigencia,
      String estado,
      OffsetDateTime creado,
      boolean retirado) {
    insertar(
        codigo, "UPGRADE_MEMBRESIA", nombre, destino, precio, vigencia, estado, creado, retirado);
  }

  private void bot(
      String codigo,
      String nombre,
      String precio,
      Integer vigencia,
      String estado,
      OffsetDateTime creado,
      boolean retirado) {
    insertar(codigo, "BOT", nombre, null, precio, vigencia, estado, creado, retirado);
  }

  /**
   * Siembra un producto con su instante de alta y su marca de retiro fijados.
   *
   * <p>Los parámetros nulos van con {@code CAST} explícito: sin el tipo, el controlador no puede
   * decidir a qué convierte el nulo y PostgreSQL rechaza la sentencia entera.
   */
  private void insertar(
      String codigo,
      String tipo,
      String nombre,
      UUID destino,
      String precio,
      Integer vigencia,
      String estado,
      OffsetDateTime creado,
      boolean retirado) {

    jdbc.update(
        "INSERT INTO products (id, code, type, name, description, target_membership_id, price,"
            + " currency_id, validity_days, status, created_at, updated_at, deleted_at)"
            + " VALUES (CAST(? AS uuid), ?, ?, ?, 'Descripción de prueba', CAST(? AS uuid),"
            + " CAST(? AS numeric), CAST(? AS uuid), CAST(? AS integer), ?, ?, ?,"
            + " CAST(? AS timestamptz))",
        UUID.randomUUID().toString(),
        codigo,
        tipo,
        nombre,
        destino == null ? null : destino.toString(),
        precio,
        USD,
        vigencia,
        estado,
        creado,
        creado,
        retirado ? creado.toString() : null);
  }
}
