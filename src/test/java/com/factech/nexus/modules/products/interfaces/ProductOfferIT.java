package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
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
  @Autowired private org.hibernate.SessionFactory sessionFactory;

  @Autowired private com.factech.nexus.modules.products.domain.service.GetOwnOfferService servicio;

  private UUID oro;
  private UUID platino;
  private UUID vip;
  private UUID free;

  private UUID enOro;
  private UUID enVip;
  private UUID enFree;
  private UUID otroEnFree;
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
    // Solo para que `UP_FREE` tenga un origen por debajo del suyo (`RN-PM-002`
    // exige las dos membresías desde el 02-09-2026): nadie se asigna aquí, y la
    // comparación que decide esta prueba sigue siendo por NIVEL, no por origen
    // (`T-20` de la tripleta, pendiente) — este nivel no participa en ninguna
    // aserción.
    UUID sotano = membresia("SOTANO", "Sótano de prueba", 5, free);

    // TODA LA OFERTA DE `enFree` SE DECLARA DESDE `FREE`, y eso es lo que la
    // coincidencia por origen exige de esta siembra (`T-20`, 07-09-2026): antes
    // bastaba con que el DESTINO estuviera por encima, y ahora tiene que
    // coincidir el ORIGEN. Cuatro productos desde `FREE`, del salto cero al más
    // largo:
    upgrade("UP_RENOVAR", "Renovar Free", free, free, "5.00", 30, "ACTIVO", BASE, false);
    upgrade("UP_VIP", "Ascenso a Vip", free, vip, "20.00", null, "ACTIVO", BASE, false);
    upgrade("UP_PLATINO", "Ascenso a Platino", free, platino, "50.00", 30, "ACTIVO", BASE, false);
    upgrade("UP_ORO", "Ascenso a Oro", free, oro, "100.00", 365, "ACTIVO", BASE, false);

    // AJENOS: existen, están activos y NO son de `enFree` ni de `enOro`. Son los
    // que hacen verificable que la oferta no se decide por nivel — `UP_AJENO`
    // lleva a `ORO`, y a quien YA está en `ORO` no se le ofrece porque su origen
    // no es suyo (`CA-PM-060`, `CA-PM-108`).
    upgrade(
        "UP_AJENO",
        "Ascenso a Oro desde Platino",
        platino,
        oro,
        "80.00",
        365,
        "ACTIVO",
        BASE,
        false);
    upgrade(
        "UP_DESDE_VIP",
        "Ascenso a Platino desde Vip",
        vip,
        platino,
        "60.00",
        30,
        "ACTIVO",
        BASE,
        false);
    upgrade("UP_FREE", "Ascenso a Free", sotano, free, "5.00", 7, "ACTIVO", BASE, false);

    // Lo que NO debe salir nunca (`CA-PM-058`). Se declaran DESDE `FREE` a
    // propósito: con otro origen quedarían fuera por la coincidencia y la prueba
    // no comprobaría nada. No chocan con `UP_ORO` porque
    // `uq_products_upgrade_target` es un índice PARCIAL: solo alcanza a los
    // activos y no retirados.
    upgrade(
        "UP_ORO_BORRADOR",
        "Ascenso a Oro (sin publicar)",
        free,
        oro,
        "90.00",
        365,
        "INACTIVO",
        BASE,
        false);
    upgrade(
        "UP_ORO_RETIRADO",
        "Ascenso a Oro (retirado)",
        free,
        oro,
        "80.00",
        365,
        "ACTIVO",
        BASE,
        true);

    bot("BOT_SENALES", "Bot de señales", "10.00", null, "ACTIVO", BASE.plusHours(2), false);
    bot("BOT_SOPORTE", "Bot de soporte", "99.50", 15, "ACTIVO", BASE.plusHours(3), false);
    bot("BOT_APAGADO", "Bot sin publicar", "1.00", null, "INACTIVO", BASE.plusHours(4), false);

    enOro = persona("oferta-oro");
    enVip = persona("oferta-vip");
    enFree = persona("oferta-free");
    // Un SEGUNDO actor en el mismo nivel: con la coincidencia por origen es la
    // única forma de comprobar que el precio no depende de quién mira.
    otroEnFree = persona("oferta-free-2");
    sinMembresia = persona("oferta-sin");
    conMembresiaVencida = persona("oferta-vencida");

    asignar(enOro, oro, null);
    asignar(enVip, vip, null);
    asignar(enFree, free, null);
    asignar(otroEnFree, free, null);
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
  @DisplayName(
      "`CA-PM-059`, `CA-PM-060`, `CA-PM-061` — los tres casos de ORIGEN, en una sola vista")
  void losTresCasosDeOrigen() throws Exception {
    // Quien está en VIP ve EXACTAMENTE lo declarado desde VIP, y nada más.
    //
    // Antes del 07-09-2026 esta prueba miraba niveles y esperaba dos upgrades
    // —los de destino superior—. Con la coincidencia por origen espera UNO, y
    // la diferencia es justo lo que el cambio compra: `UP_AJENO` lleva a `ORO`
    // igual que antes, y **no es suyo**.
    mvc.perform(oferta(enVip))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content.length()").value(1))
        .andExpect(jsonPath("$.upgrades.content[0].code").value("UP_DESDE_VIP"))
        // El declarado desde PLATINO no es suyo, aunque lleve más arriba.
        .andExpect(
            jsonPath("$.upgrades.content[*].code", Matchers.not(Matchers.hasItem("UP_AJENO"))))
        // Ni los declarados desde FREE, que llevan a donde él ya llegó.
        .andExpect(jsonPath("$.upgrades.content[*].code", Matchers.not(Matchers.hasItem("UP_VIP"))))
        // Y ninguna bajada: la sostiene `RN-PM-017` AL REGISTRAR, no este filtro.
        .andExpect(
            jsonPath("$.upgrades.content[*].code", Matchers.not(Matchers.hasItem("UP_FREE"))));
  }

  @Test
  @DisplayName(
      "`CA-PM-060` y `CA-PM-108` — a quien YA está en ORO no se le ofrece un `X → ORO` ajeno")
  void elUpgradeAjenoHaciaMiNivelNoSeOfrece() throws Exception {
    // `UP_AJENO` es `PLATINO → ORO`. Comparando niveles con `<=` —que es lo que
    // haría falta para que cupiera la renovación— este producto SE LE
    // OFRECERÍA a quien está en ORO: el salto de otro que acaba donde él está.
    // Es el caso que obligó a construir la coincidencia por origen.
    mvc.perform(oferta(enOro))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content.length()").value(0));
  }

  @Test
  @DisplayName(
      "`CA-PM-089` y `CA-PM-107` — ve TODOS los declarados desde su membresía, no solo uno")
  void todosLosDeclaradosDesdeSuMembresia() throws Exception {
    // Ofrecer solo el inmediato obligaría a comprar tres veces para recorrer la
    // cadena, que es una fuga de ventas disfrazada de simplicidad. Y con la
    // renovación dentro son CUATRO, del salto cero al más largo.
    mvc.perform(oferta(enFree))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content.length()").value(4))
        .andExpect(
            jsonPath(
                "$.upgrades.content[*].code",
                Matchers.contains("UP_RENOVAR", "UP_VIP", "UP_PLATINO", "UP_ORO")));
  }

  @Test
  @DisplayName("`CA-PM-126` — la RENOVACIÓN se ofrece: es el `X → X` declarado desde su membresía")
  void laRenovacionSeOfrece() throws Exception {
    // Va PRIMERA porque el orden es por nivel de destino descendente —del salto
    // más corto al más largo— y una renovación es el salto de longitud cero.
    mvc.perform(oferta(enFree))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content[0].code").value("UP_RENOVAR"))
        .andExpect(jsonPath("$.upgrades.content[0].targetMembership.code").value("FREE"))
        .andExpect(jsonPath("$.upgrades.content[0].validityDays").value(30));

    // Y no se la ve nadie más: su origen es `FREE`.
    mvc.perform(oferta(enVip))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.upgrades.content[*].code", Matchers.not(Matchers.hasItem("UP_RENOVAR"))));
  }

  @Test
  @DisplayName("`CA-PM-062` — sin nada declarado desde su membresía, la lista llega vacía")
  void enLaCimaLaListaLlegaVacia() throws Exception {
    mvc.perform(oferta(enOro))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content").isArray())
        .andExpect(jsonPath("$.upgrades.content.length()").value(0))
        // Nadie declaró un `ORO → X` ni una renovación de `ORO`, y el `X → ORO`
        // ajeno no es suyo. No es un mensaje especial: los bots siguen ahí.
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
        // ORO(1) — y con la RENOVACIÓN delante, que es el salto cero: FREE(4).
        // Es el único orden en el que «subir» significa algo — ni el
        // precio ni el nombre lo expresan.
        .andExpect(
            jsonPath("$.upgrades.content[*].targetMembership.level", Matchers.contains(4, 3, 2, 1)))
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
        // `UP_VIP` no caduca: la clave existe y vale nulo. Va SEGUNDO, porque
        // la renovación —salto cero— abre la lista.
        .andExpect(jsonPath("$.upgrades.content[1].code").value("UP_VIP"))
        .andExpect(content().string(Matchers.containsString("\"validityDays\":null")))
        .andExpect(jsonPath("$.upgrades.content[0].validityDays").value(30))
        .andExpect(jsonPath("$.upgrades.content[2].validityDays").value(30))
        .andExpect(jsonPath("$.upgrades.content[3].validityDays").value(365));
  }

  @Test
  @DisplayName("`CA-PM-090` — el mismo producto vale lo mismo mire quien mire")
  void elPrecioNoSeAjustaPorNivel() throws Exception {
    // Un importe distinto según quién mira sería un descuento, y los descuentos
    // son promociones — fuera de alcance a propósito.
    //
    // DESDE EL 07-09-2026 SE COMPRUEBA CON DOS PERSONAS DEL MISMO NIVEL, y no
    // con dos de niveles distintos: con la coincidencia por origen, dos actores
    // de niveles distintos **no comparten ningún producto**, de modo que aquella
    // comparación dejó de poder hacerse. La pregunta que el criterio protege
    // sigue siendo la misma — el precio no depende de quién mira.
    mvc.perform(oferta(enFree))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content[3].code").value("UP_ORO"))
        .andExpect(jsonPath("$.upgrades.content[3].price").value(100.00));

    mvc.perform(oferta(otroEnFree))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content[3].code").value("UP_ORO"))
        .andExpect(jsonPath("$.upgrades.content[3].price").value(100.00));
  }

  // ---------------------------------------------------------------------------
  // El precio que se publica (`RN-PM-024`) — 08-09-2026
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-PM-158` — con precio público viajan LOS DOS importes")
  void publicaLosDosImportes() throws Exception {
    declararPrecioPublico("UP_ORO", "149.00");

    mvc.perform(oferta(enFree))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content[3].code").value("UP_ORO"))
        // `price` es SIEMPRE el del sistema desde el 08-09-2026, y ya no «el
        // que se muestra»: hasta ese día aquí se esperaba 149,00.
        .andExpect(jsonPath("$.upgrades.content[3].price").value(100.00))
        .andExpect(jsonPath("$.upgrades.content[3].publicPrice").value(149.00));
  }

  @Test
  @DisplayName("`CA-PM-159` — sin precio público, `publicPrice` llega NULO Y PRESENTE")
  void elPublicoLlegaNuloYPresente() throws Exception {
    mvc.perform(oferta(enFree))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content[3].code").value("UP_ORO"))
        .andExpect(jsonPath("$.upgrades.content[3].price").value(100.00))
        .andExpect(jsonPath("$.upgrades.content[3].publicPrice").value(Matchers.nullValue()));
  }

  @Test
  @DisplayName("`CA-PM-167` — cada producto trae su conversión, presente y nula si no procede")
  void cadaProductoTraeSuConversion() throws Exception {
    // Los productos sembrados están en la moneda de casa, de modo que no hay
    // nada que convertir. Lo que se comprueba es que el campo ESTÁ: ausente
    // sería indistinguible de uno que el cliente no conoce.
    String cuerpo =
        mvc.perform(oferta(enFree))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.upgrades.content[3].exchange").value(Matchers.nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(cuerpo).contains("\"exchange\":null");
  }

  @Test
  @DisplayName("`CA-PM-168` — la conversión de la oferta cuesta DOS consultas, no dos por producto")
  void laConversionNoSePagaPorProducto() {
    // Se cuenta sobre el SERVICIO y no sobre la llamada HTTP: por el filtro
    // pasan escrituras que no son de esta lectura, y contarlas mediría el
    // arranque de la petición en lugar del coste del caso de uso.
    var estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();

    // El actor se pone a mano porque el servicio lo lee del contexto de
    // seguridad y aquí no hay petición HTTP que lo traiga.
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                enFree.toString(), null, java.util.List.of()));
    try {
      servicio.offer();
    } finally {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // Cuatro upgrades y dos bots en la respuesta. Tres consultas: la oferta, la
    // membresía vigente del actor y —desde el 08-09-2026— la moneda de casa y
    // las tasas, que son dos más y NO dos por producto. Con la conversión
    // resuelta fila a fila serían más de diez, y el cuerpo sería idéntico.
    assertThat(estadisticas.getPrepareStatementCount()).isLessThanOrEqualTo(4);
  }

  /** Le pone precio público a un producto ya sembrado, que es lo que la siembra no hace. */
  private void declararPrecioPublico(String codigo, String importe) {
    jdbc.update(
        "UPDATE products SET public_price = CAST(? AS numeric) WHERE code = ?", importe, codigo);
  }

  // ---------------------------------------------------------------------------
  // Autorización y forma de la ruta
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-PM-065` — responde a quien porta `products:sale`")
  void respondeConElPermisoDeVenta() throws Exception {
    // `comoActor` concede exactamente `products:sale`, y nada más. No es
    // `products:read`: ese daría a cada cliente el catálogo administrativo
    // entero para ver tres líneas, y no es lo que este permiso gobierna.
    mvc.perform(oferta(enFree)).andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "`CA-PM-101` — sin `products:sale` responde `403`, aunque el actor tenga otros"
          + " permisos de `products:`")
  void sinPermisoDeVentaEsForbidden() throws Exception {
    RequestPostProcessor sinVenta = user(enFree.toString()).authorities(() -> "products:read");

    mvc.perform(get("/api/v1/products/available").with(sinVenta)).andExpect(status().isForbidden());
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
        .andExpect(jsonPath("$.upgrades.content.length()").value(1));
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

  @Test
  @DisplayName("`CA-PM-123` — la oferta devuelve el alcance y la implementación de cada producto")
  void publicaAlcanceEImplementacion() throws Exception {
    jdbc.update("UPDATE products SET implementation = 'AUTOMATICA' WHERE code = 'UP_DESDE_VIP'");

    mvc.perform(oferta(enVip))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content[0].scope").value("TIENDA"))
        // La implementación viaja para que quien compra sepa ANTES DE PAGAR si
        // lo que se lleva se le entrega en el acto.
        .andExpect(jsonPath("$.upgrades.content[0].implementation").exists())
        .andExpect(jsonPath("$.services.content[0].scope").value("TIENDA"))
        .andExpect(jsonPath("$.services.content[0].implementation").value("MANUAL"));
  }

  @Test
  @DisplayName("`CA-PM-124` — los DOS alcances llegan a la tienda: la escala no filtra aquí")
  void laEscalaNoFiltraLaOferta() throws Exception {
    // Es la prueba que verifica que nadie añadió el filtro «por simetría» con
    // `RF-PM-002`. `HOTLINKS` INCLUYE la tienda, de modo que un predicado
    // sobre esta columna devolvería siempre lo mismo que no ponerlo — y quien
    // lo escribiera dejaría fuera de la tienda productos que deben estar.
    jdbc.update("UPDATE products SET scope = 'HOTLINKS' WHERE code = 'BOT_SENALES'");

    mvc.perform(oferta(enFree))
        .andExpect(status().isOk())
        // LOS DOS bots activos siguen ahí: uno de cada alcance, y ninguno se
        // queda fuera por el suyo.
        .andExpect(jsonPath("$.services.content.length()").value(2))
        .andExpect(jsonPath("$.services.content[0].code").value("BOT_SENALES"))
        .andExpect(jsonPath("$.services.content[0].scope").value("HOTLINKS"))
        .andExpect(jsonPath("$.services.content[1].code").value("BOT_SOPORTE"))
        .andExpect(jsonPath("$.services.content[1].scope").value("TIENDA"));
  }

  // ---------------------------------------------------------------------------
  // Preparación
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-PM-144` — la oferta trae el COLOR del destino y el de la membresía del actor")
  void laOfertaTraeElColorDeLasMembresias() throws Exception {
    // El de `currentMembership` es el que más se nota: la pantalla pinta
    // «estás en X, sube a Y», y hasta hoy no tenía con qué colorear el «X».
    mvc.perform(oferta(enFree))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.currentMembership.color").value(Matchers.matchesPattern("^[0-9A-F]{6}$")))
        .andExpect(
            jsonPath(
                "$.upgrades.content[*].targetMembership.color",
                Matchers.everyItem(Matchers.matchesPattern("^[0-9A-F]{6}$"))));
  }

  private MockHttpServletRequestBuilder oferta(UUID quien) {
    return get("/api/v1/products/available").with(comoActor(quien));
  }

  /**
   * Autenticado con <b>exactamente</b> {@code products:sale}, desde el 02-09-2026.
   *
   * <p>Ni de más ni de menos: con otro permiso de sobra no se distinguiría un actor correctamente
   * autorizado de uno con privilegios que no debería tener, y sin él ninguna de las pruebas de
   * comportamiento de este archivo llegaría a ejecutarse — todas pasarían por el {@code 403} de
   * {@code sinPermisoDeVentaEsForbidden} en lugar de probar lo que dicen probar.
   */
  private RequestPostProcessor comoActor(UUID quien) {
    return user(quien.toString()).authorities(() -> "products:sale");
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
                           must_change_password, status, country_id)
        VALUES (CAST(? AS uuid), ?, ?, 'Ana', 'Ruiz', 'no-se-usa-en-esta-prueba', false, 'ACTIVO', (SELECT id FROM countries WHERE code = 'COL'))
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
        INSERT INTO user_memberships (id, user_id, membership_id, started_at, ends_at,
                                      created_at, updated_at)
        VALUES (gen_random_uuid(), CAST(? AS uuid), CAST(? AS uuid), now() - interval '30 days',
                CAST(? AS timestamptz), now(), now())
        """,
        quien.toString(),
        membresia.toString(),
        fin == null ? null : fin.toString());
  }

  private void upgrade(
      String codigo,
      String nombre,
      UUID origen,
      UUID destino,
      String precio,
      Integer vigencia,
      String estado,
      OffsetDateTime creado,
      boolean retirado) {
    insertar(
        codigo,
        "UPGRADE_MEMBRESIA",
        nombre,
        origen,
        destino,
        precio,
        vigencia,
        estado,
        creado,
        retirado);
  }

  private void bot(
      String codigo,
      String nombre,
      String precio,
      Integer vigencia,
      String estado,
      OffsetDateTime creado,
      boolean retirado) {
    insertar(codigo, "BOT", nombre, null, null, precio, vigencia, estado, creado, retirado);
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
      UUID origen,
      UUID destino,
      String precio,
      Integer vigencia,
      String estado,
      OffsetDateTime creado,
      boolean retirado) {

    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description, source_membership_id,"
            + " target_membership_id, price,"
            + " currency_id, validity_days, status, created_at, updated_at, deleted_at)"
            + " VALUES ('TIENDA', 'MANUAL', CAST(? AS uuid), ?, ?, ?, 'Descripción de prueba', CAST(? AS uuid),"
            + " CAST(? AS uuid), CAST(? AS numeric), CAST(? AS uuid), CAST(? AS integer), ?, ?,"
            + " ?, CAST(? AS timestamptz))",
        UUID.randomUUID().toString(),
        codigo,
        tipo,
        nombre,
        origen == null ? null : origen.toString(),
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
