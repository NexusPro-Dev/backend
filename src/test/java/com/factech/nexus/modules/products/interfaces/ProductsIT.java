package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * El alta de productos (`RF-PM-001` · `T-14`).
 *
 * <p>Cubre los criterios de `spec.md` §12. Lo que más importa aquí no es el camino feliz sino las
 * dos mitades de `RN-PM-002` y el estado inicial: un producto que naciera activo rompería el
 * reparto de `RN-PM-004`, que vive entero en `RF-PM-005`.
 */
@AutoConfigureMockMvc
class ProductsIT extends IntegrationTestBase {

  /** La moneda sembrada por `V15`, estable en todos los entornos. */
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private String oro;
  private String platino;
  private String vip;
  private String free;

  @BeforeEach
  void prepararCatalogo() {
    jdbc.update("DELETE FROM currencies WHERE is_default = false");
    jdbc.update("DELETE FROM products");
    // Antes que las membresías: `user_products` las referencia (`V57`), y
    // sin esto la suite solo pasaba cuando otra las había vaciado antes.
    jdbc.update("DELETE FROM user_products");
    jdbc.update("DELETE FROM memberships");
    // LA CADENA ENTERA, y no solo la cima: `RN-PM-018` dice que un upgrade
    // puede saltar niveles, y eso no se puede probar sin niveles que saltar.
    oro = crearMembresia("ORO", "Oro", 1, null);
    platino = crearMembresia("PLATINO", "Platino", 2, oro);
    vip = crearMembresia("VIP", "Vip", 3, platino);
    free = crearMembresia("BECA", "Beca", 4, vip);
  }

  @Test
  @DisplayName("`CA-PM-001` y `CA-PM-101` — registra un upgrade y resuelve LAS DOS membresías")
  void altaDeUpgrade() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"UPGRADE_ORO","type":"UPGRADE_MEMBRESIA","icon":"crown","name":"Ascenso a Oro",
                 "description":"Acceso al nivel oro.","sourceMembershipId":"%s",
                 "targetMembershipId":"%s","price":49.99,"currencyId":"%s",
                 "validityDays":30}
                """
                    .formatted(free, oro, USD)))
        .andExpect(status().isCreated())
        .andExpect(
            header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/products/")))
        .andExpect(jsonPath("$.code").value("UPGRADE_ORO"))
        .andExpect(jsonPath("$.sourceMembership.code").value("BECA"))
        .andExpect(jsonPath("$.sourceMembership.level").value(4))
        .andExpect(jsonPath("$.targetMembership.code").value("ORO"))
        .andExpect(jsonPath("$.targetMembership.level").value(1))
        .andExpect(jsonPath("$.currency.code").value("USD"))
        .andExpect(jsonPath("$.validityDays").value(30));
  }

  @Test
  @DisplayName("`CA-PM-002` — registra un bot sin membresía destino, que llega null y presente")
  void altaDeBot() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":10.00,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        // Presente y nulo, no ausente: un campo que falta es indistinguible de
        // uno que el cliente no conoce.
        .andExpect(jsonPath("$.targetMembership").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.validityDays").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("`CA-PM-096` — un upgrade registra su icono, normalizado a minúsculas")
  void altaDeUpgradeConIcono() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"UPGRADE_ORO","type":"UPGRADE_MEMBRESIA","name":"Ascenso a Oro",
                 "icon":"  CROWN  ","sourceMembershipId":"%s","targetMembershipId":"%s",
                 "price":49.99,"currencyId":"%s"}
                """
                    .formatted(free, oro, USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.icon").value("crown"));
  }

  @Test
  @DisplayName("`CA-PM-097` — `RN-PM-016`: un bot con icono se rechaza con 400 y VAL-013")
  void altaDeBotConIconoSeRechaza() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría","icon":"crown",
                 "price":10.00,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-013"))
        .andExpect(jsonPath("$.errors[0].field").value("icon"));
  }

  @Test
  @DisplayName("`CA-PM-348` — el alta admite los cuatro alcances y rechaza HOTLINKS con VAL-015")
  void losCuatroAlcances() throws Exception {
    String[] alcances = {"TIENDA", "HOTLINK", "AMBOS", "NINGUNO"};
    for (int i = 0; i < alcances.length; i++) {
      mvc.perform(
              alta(
                  """
                  {"scope":"%s","implementation":"AUTOMATICA","code":"BOT_%d","type":"BOT","name":"Bot %d",
                   "price":9.99,"currencyId":"%s"}
                  """
                      .formatted(alcances[i], i, i, USD)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.scope").value(alcances[i]));
    }
    mvc.perform(
            alta(
                """
                {"scope":"HOTLINKS","implementation":"AUTOMATICA","code":"BOT_X","type":"BOT","name":"Bot X",
                 "price":9.99,"currencyId":"%s"}
                """
                    .formatted(USD)))
        // Como cualquier valor fuera del dominio del enumerado (`CA-PM-112`):
        // el cuerpo no es válido, y no se toma por ausente.
        .andExpect(status().isBadRequest());
    assertThat(cuantosProductos()).isEqualTo(4);
  }

  @Test
  @DisplayName(
      "`CA-PM-230` — `RN-PM-034`: un upgrade SIN icono se rechaza con VAL-018 y no registra")
  void elIconoEsObligatorioEnElUpgrade() throws Exception {
    // Era `CA-PM-098` —«el icono es opcional»— hasta el 14-09-2026. Desde la
    // portada, en el alta el icono es lo único que puede pintar un upgrade:
    // la portada llega después (`RF-PM-014`). Ausente, nulo y vacío, los tres.
    for (String icono : new String[] {"", ",\"icon\":null", ",\"icon\":\"   \""}) {
      mvc.perform(
              alta(
                  """
                  {"scope":"TIENDA","implementation":"AUTOMATICA","code":"UPGRADE_ORO","type":"UPGRADE_MEMBRESIA","name":"Ascenso a Oro",
                   "sourceMembershipId":"%s","targetMembershipId":"%s","price":49.99,
                   "currencyId":"%s"%s}
                  """
                      .formatted(free, oro, USD, icono)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].code").value("VAL-018"))
          .andExpect(jsonPath("$.errors[0].field").value("icon"));
    }
    assertThat(cuantosProductos()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-PM-231` — el bot sigue sin icono, y el alta trae `coverImageUrl` nulo y presente")
  void elBotSinIconoYLaPortadaNula() throws Exception {
    // La forma «nulo y presente» del icono sigue viva en el bot; y la portada
    // no entra por aquí: el alta la devuelve siempre presente y nula, para que
    // tenga la misma forma que el detalle.
    String cuerpo =
        mvc.perform(
                alta(
                    """
                    {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                     "price":49.99,"currencyId":"%s"}
                    """
                        .formatted(USD)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.icon").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.coverImageUrl").value(org.hamcrest.Matchers.nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(cuerpo).contains("\"icon\":null").contains("\"coverImageUrl\":null");

    // Y la instantánea del alta lleva `cover_image_id`, nulo.
    String instantanea =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE module = 'PM' AND action = 'CREATE'"
                + " ORDER BY occurred_at DESC LIMIT 1",
            String.class);
    assertThat(instantanea).contains("\"cover_image_id\": null");
  }

  @Test
  @DisplayName("`VAL-012` — el icono con forma inválida se rechaza")
  void iconoConFormaInvalida() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"UPGRADE_ORO","type":"UPGRADE_MEMBRESIA","name":"Ascenso a Oro",
                 "icon":"Crown Oro","sourceMembershipId":"%s","targetMembershipId":"%s",
                 "price":49.99,"currencyId":"%s"}
                """
                    .formatted(free, oro, USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-012"));
  }

  @Test
  @DisplayName("`CA-PM-068` — el producto nace INACTIVO, y enviar `status` devuelve 400")
  void naceInactivoYNoSePuedeForzar() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría","price":10.00,
                 "currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("INACTIVO"));

    // No se ignora en silencio: quien lo envía tiene que enterarse de que el
    // estado inicial no se decide desde fuera.
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"OTRO","type":"BOT","name":"Otro","price":10.00,
                 "currencyId":"%s","status":"ACTIVO"}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-PM-003`, `CA-PM-004` y `CA-PM-105` — `RN-PM-002` en los dos sentidos")
  void condicionCruzadaEnLosDosSentidos() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"UPGRADE_ORO","type":"UPGRADE_MEMBRESIA","icon":"crown","name":"Ascenso",
                 "price":49.99,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-007"));

    // La mitad que se olvida, y la peligrosa: no falla, promete.
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría","targetMembershipId":"%s",
                 "price":10.00,"currencyId":"%s"}
                """
                    .formatted(oro, USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-008"))
        .andExpect(jsonPath("$.errors[0].field").value("targetMembershipId"));

    // `CA-PM-105`. La prohibición vale para LAS DOS: un bot que declarara solo
    // el origen promete lo mismo que uno que declara solo el destino —que el
    // nivel de alguien va a cambiar— y nadie lo va a aplicar.
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría","sourceMembershipId":"%s",
                 "price":10.00,"currencyId":"%s"}
                """
                    .formatted(free, USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-008"))
        .andExpect(jsonPath("$.errors[0].field").value("sourceMembershipId"));

    assertThat(cuantosProductos()).isZero();
  }

  @Test
  @DisplayName("`CA-PM-102` — `RN-PM-018`: el upgrade puede SALTAR niveles, y no solo el contiguo")
  void saltarNivelesEsLegitimo() throws Exception {
    // La premisa que hace valer la prueba: entre el origen y el destino hay dos
    // eslabones. Sin comprobarla, `BECA -> ORO` sería un salto de nombre.
    assertThat(cuantasMembresias()).isEqualTo(4);

    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"SALTO_ORO","type":"UPGRADE_MEMBRESIA","icon":"crown","name":"De Free a Oro",
                 "sourceMembershipId":"%s","targetMembershipId":"%s","price":99.99,
                 "currencyId":"%s"}
                """
                    .formatted(free, oro, USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sourceMembership.level").value(4))
        .andExpect(jsonPath("$.targetMembership.level").value(1));
  }

  @Test
  @DisplayName("`CA-PM-103` — falta una de las dos y se rechaza, diciendo CUÁL")
  void faltaUnaDeLasDosMembresias() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"SIN_ORIGEN","type":"UPGRADE_MEMBRESIA","icon":"crown","name":"Sin origen",
                 "targetMembershipId":"%s","price":49.99,"currencyId":"%s"}
                """
                    .formatted(oro, USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-007"))
        .andExpect(jsonPath("$.errors[0].field").value("sourceMembershipId"));

    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"SIN_DESTINO","type":"UPGRADE_MEMBRESIA","icon":"crown","name":"Sin destino",
                 "sourceMembershipId":"%s","price":49.99,"currencyId":"%s"}
                """
                    .formatted(free, USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-007"))
        .andExpect(jsonPath("$.errors[0].field").value("targetMembershipId"));

    assertThat(cuantosProductos()).isZero();
  }

  @Test
  @DisplayName("`CA-PM-104` — `RN-PM-017`: un descenso vendido como upgrade se rechaza")
  void elOrigenNoPuedeEstarPorEncimaDelDestino() throws Exception {
    // Origen POR ENCIMA del destino: `ORO` es el nivel 1 y `BECA` el 4. Un
    // descenso con la etiqueta de ascenso. Hace falta leer el `level` de las
    // dos filas, y es 422 porque el dato existe: lo que no vale es la relación
    // entre los dos.
    //
    // Y desde `V61` esta comprobación es LO ÚNICO que sostiene la regla:
    // `ck_products_origen_distinto` se retiró con la renovación.
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"DESCENSO","type":"UPGRADE_MEMBRESIA","icon":"crown","name":"Bajada disfrazada",
                 "sourceMembershipId":"%s","targetMembershipId":"%s","price":49.99,
                 "currencyId":"%s"}
                """
                    .formatted(oro, free, USD)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-014"))
        .andExpect(jsonPath("$.errors[0].field").value("sourceMembershipId"));

    assertThat(cuantosProductos()).as("el rechazo no registró nada").isZero();
  }

  @Test
  @DisplayName("`CA-PM-125` — el origen PUEDE ser el destino: es una renovación")
  void elOrigenPuedeSerElDestino() throws Exception {
    // Lo rechazaba `VAL-014` hasta el 07-09-2026. Un `ORO → ORO` no vende un
    // cambio de nivel: vende TIEMPO, la vigencia que declara, y eso es un
    // producto legítimo (`requirements/pm.md` §5.2.3).
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"RENOVAR_ORO","type":"UPGRADE_MEMBRESIA","icon":"crown","name":"Renovar Oro",
                 "sourceMembershipId":"%s","targetMembershipId":"%s","price":49.99,
                 "currencyId":"%s","validityDays":30}
                """
                    .formatted(oro, oro, USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sourceMembership.code").value("ORO"))
        .andExpect(jsonPath("$.targetMembership.code").value("ORO"))
        .andExpect(jsonPath("$.validityDays").value(30));
  }

  @Test
  @DisplayName("`CA-PM-005` — un precio NEGATIVO se rechaza. El cero ya no: es la renovación")
  void precioNegativo() throws Exception {
    for (String precio : new String[] {"-1.50", "-0.01"}) {
      mvc.perform(
              alta(
                  """
                  {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría","price":%s,
                   "currencyId":"%s"}
                  """
                      .formatted(precio, USD)))
          .andExpect(status().isBadRequest());
    }
    assertThat(cuantosProductos()).isZero();
  }

  @Test
  @DisplayName("`CA-PM-149` — el precio de CERO se admite en los dos importes")
  void precioCero() throws Exception {
    // Hasta el 08-09-2026 esto era un 400, y lo que lo cambió no fue el precio
    // público sino la RENOVACIÓN: un `BECA → BECA` es un producto legítimo que
    // vale cero, y prohibirlo obligaba a inventarle un céntimo (`RN-PM-006`).
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":0,"purchasePrice":0,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.price").value(0))
        .andExpect(jsonPath("$.purchasePrice").value(0));
  }

  @Test
  @DisplayName("`CA-PM-145` — el alta admite los DOS precios y la respuesta devuelve los dos")
  void losDosPrecios() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"purchasePrice":59.99,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        // Los dos con los decimales de SU moneda, no con la escala de la
        // columna: `59.99`, no `59.9900`.
        .andExpect(jsonPath("$.price").value(49.99))
        .andExpect(jsonPath("$.purchasePrice").value(59.99));
  }

  @Test
  @DisplayName("`CA-PM-146` — sin precio de compra el campo llega PRESENTE y nulo, no ausente")
  void sinPrecioDeCompra() throws Exception {
    // Su nulo SIGNIFICA «no se conoce el costo», y un campo que desaparece del
    // JSON no puede decir eso — sería indistinguible de uno que el cliente no
    // conoce.
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.purchasePrice").doesNotExist())
        .andExpect(jsonPath("$").value(org.hamcrest.Matchers.hasKey("purchasePrice")));
  }

  @Test
  @DisplayName("`CA-PM-147` — un precio de compra negativo se rechaza, y el error nombra SU campo")
  void precioDeCompraNegativo() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"purchasePrice":-1,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest())
        // Con dos importes, un mensaje que no distingue obliga a probar los dos.
        .andExpect(jsonPath("$.errors[0].field").value("purchasePrice"));

    assertThat(cuantosProductos()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-PM-148` — el precio de compra con decimales de más se rechaza aunque el otro quepa")
  void decimalesDelPrecioDeCompra() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":10.00,"purchasePrice":10.005,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"))
        // El del sistema SÍ cabe: sin el campo en el error, quien lo recibe
        // tendría que probar los dos para saber cuál corregir.
        .andExpect(jsonPath("$.errors[0].field").value("purchasePrice"));

    assertThat(cuantosProductos()).isZero();
  }

  @Test
  @DisplayName("`CA-PM-150` — la instantánea del evento de creación incluye el precio de compra")
  void laInstantaneaLlevaElPrecioDeCompra() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"purchasePrice":59.99,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated());

    // Es el único sitio donde queda escrito CUÁNTO COSTÓ un producto cuyo costo
    // después se corrige.
    String cambios =
        jdbc.queryForObject(
            """
            SELECT changes::text FROM audit_change_log
             WHERE module = 'PM' AND entity = 'products' AND action = 'CREATE'
             ORDER BY occurred_at DESC LIMIT 1
            """,
            String.class);
    assertThat(cambios).contains("purchase_price").contains("59.99");
    // La clave vieja no vuelve: los eventos anteriores al 12-09-2026 la llevan.
    assertThat(cambios).doesNotContain("public_price");
  }

  @Test
  @DisplayName("`publicPrice` dejó de existir el 12-09-2026: es una propiedad desconocida y es 400")
  void elNombreViejoDelSegundoPrecioSeRechaza() throws Exception {
    // El segundo precio pasó de «lo que se anuncia» a «lo que NEXUS paga», y con
    // el significado cambió el nombre. Ignorar el viejo en silencio dejaría a un
    // cliente creyendo que declaró un precio público que ya no existe.
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"publicPrice":59.99,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "`CA-PM-219` — el alta admite un enlace de video, también en un BOT, y lo devuelve TAL CUAL")
  void conEnlaceDeVideo() throws Exception {
    // En un bot, a propósito: es donde el icono NO cabe (`RN-PM-016`) y el
    // enlace SÍ, y una prueba sobre un upgrade no distinguiría las dos reglas.
    // Con mayúsculas en el identificador y espacios alrededor: lo que se guarda
    // es lo recortado y NADA MÁS — un enlace «arreglado» puede dejar de
    // resolver.
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"currencyId":"%s",
                 "links":[{"type":"VIDEO_PRESENTACION","url":"  https://www.youtube.com/watch?v=dQw4w9WgXcQ  "}]}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.links.length()").value(1))
        .andExpect(jsonPath("$.links[0].type").value("VIDEO_PRESENTACION"))
        .andExpect(jsonPath("$.links[0].url").value("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        .andExpect(jsonPath("$.links[0].externalId").doesNotExist())
        // El campo sigue PRESENTE aunque valga nulo: no declararlo y no tenerlo
        // son lo mismo, y un campo que desaparece no lo puede decir.
        .andExpect(jsonPath("$.links[0]").value(org.hamcrest.Matchers.hasKey("externalId")));

    assertThat(
            jdbc.queryForObject(
                """
                SELECT l.url FROM product_links l
                  JOIN products p ON p.id = l.product_id
                 WHERE p.code = 'ASESORIA' AND l.type = 'VIDEO_PRESENTACION'
                """,
                String.class))
        .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
  }

  @Test
  @DisplayName("`CA-PM-220` — sin enlaces, `links` llega PRESENTE y VACÍA: no ausente y no nula")
  void sinEnlaces() throws Exception {
    // Como el precio de compra: un campo que desaparece del JSON no puede decir
    // «no tiene enlaces». Y la lista vacía es un estado legítimo, no un hueco.
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$").value(org.hamcrest.Matchers.hasKey("links")))
        .andExpect(jsonPath("$.links").isArray())
        .andExpect(jsonPath("$.links.length()").value(0));

    // Y la colección vacía explícita significa exactamente lo mismo que la
    // ausente: ninguna de las dos escribe una fila.
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA2","type":"BOT","name":"Asesoría 2",
                 "price":49.99,"currencyId":"%s","links":[]}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.links.length()").value(0));

    assertThat(cuantosEnlaces()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-PM-221` — una dirección sin forma de URL absoluta http(s) se rechaza con VAL-017,"
          + " nombrando el enlace por su índice")
  void direccionConFormaInvalida() throws Exception {
    // Las cinco variantes que la estrategia de prueba enumera: relativa, sin
    // esquema, con otro esquema, con un espacio dentro, y de 501 caracteres.
    // Se comprueba SOLO la forma: un enlace con forma y sin destino se admite
    // —`CA-PM-219` no resuelve a nada en esta suite—, y eso es la decisión de
    // `pm.md` §5.2.8, no un descuido.
    String[] invalidos = {
      "/videos/asesoria.mp4",
      "www.youtube.com/watch?v=x",
      "ftp://videos.example.com/asesoria.mp4",
      "https://www.youtube.com/watch?v=dQw4 w9WgXcQ",
      "https://example.com/" + "a".repeat(481)
    };
    for (String invalido : invalidos) {
      mvc.perform(
              alta(
                  """
                  {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                   "price":49.99,"currencyId":"%s","links":[{"type":"VIDEO_PRESENTACION","url":"%s"}]}
                  """
                      .formatted(USD, invalido)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].code").value("VAL-017"))
          .andExpect(jsonPath("$.errors[0].field").value("links[0].url"));
    }
    assertThat(cuantosProductos()).isZero();

    // El índice es el del enlace que incumple, no el primero de la colección:
    // sin él, quien envía dos tiene que probar los dos para saber cuál falló.
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"currencyId":"%s","links":[
                   {"type":"VIDEO_PRESENTACION","url":"https://vimeo.com/1"},
                   {"type":"CUPON_BOT","url":"sin-esquema"}]}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("links[1].url"));

    // El límite exacto —500— SÍ cabe: la prueba anterior no demuestra nada si
    // el tope se hubiera puesto un carácter por debajo.
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"currencyId":"%s","links":[{"type":"VIDEO_PRESENTACION","url":"%s"}]}
                """
                    .formatted(USD, "https://example.com/" + "a".repeat(480))))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("`CA-PM-222` — la instantánea del evento de creación incluye los enlaces declarados")
  void laInstantaneaLlevaLosEnlaces() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"currencyId":"%s",
                 "links":[{"type":"VIDEO_PRESENTACION","url":"https://vimeo.com/123456"}]}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated());

    String cambios = ultimaInstantanea();
    assertThat(cambios).contains("links").contains("https://vimeo.com/123456");
    // El tipo que NO se declaró no aparece con un hueco: la instantánea lleva
    // los enlaces que hay, y «no tener» es no tener entrada — que es lo mismo
    // que hace la tabla. Hasta el 22-09-2026 llevaba `video_url` nulo.
    assertThat(cambios).doesNotContain("CUPON_BOT").doesNotContain("video_url");
  }

  @Test
  @DisplayName("`CA-PM-380` — el alta admite los DOS enlaces a la vez, y los devuelve los dos")
  void losDosEnlacesALaVez() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"currencyId":"%s","links":[
                   {"type":"VIDEO_PRESENTACION","url":"https://vimeo.com/123456"},
                   {"type":"CUPON_BOT","url":"https://t.me/nexusbot","externalId":"cupon-15"}]}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.links.length()").value(2))
        .andExpect(jsonPath("$.links[0].type").value("VIDEO_PRESENTACION"))
        .andExpect(jsonPath("$.links[1].type").value("CUPON_BOT"))
        .andExpect(jsonPath("$.links[1].externalId").value("cupon-15"));

    assertThat(cuantosEnlaces()).isEqualTo(2);
  }

  @Test
  @DisplayName("`CA-PM-381` — el enlace con identificador vuelve CRUDO, no compuesto")
  void elIdentificadorVuelveSinPegar() throws Exception {
    // Es lo que `RF-PM-004` espera recibir de vuelta: quien corrige envía lo
    // que leyó, y si la lectura devolviera el enlace ya compuesto, el
    // identificador entraría dos veces en la dirección.
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"currencyId":"%s",
                 "links":[{"type":"CUPON_BOT","url":"https://t.me/nexusbot","externalId":"cupon-15"}]}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.links[0].url").value("https://t.me/nexusbot"))
        .andExpect(jsonPath("$.links[0].externalId").value("cupon-15"));
  }

  @Test
  @DisplayName(
      "`CA-PM-382` — dos enlaces del mismo tipo se rechazan con VAL-020, y NO se registra"
          + " nada")
  void dosEnlacesDelMismoTipo() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"currencyId":"%s","links":[
                   {"type":"VIDEO_PRESENTACION","url":"https://vimeo.com/1"},
                   {"type":"VIDEO_PRESENTACION","url":"https://vimeo.com/2"}]}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-020"))
        .andExpect(jsonPath("$.errors[0].field").value("links[1].type"));

    // Ni el producto ni el PRIMER enlace: la comprobación va sobre el cuerpo y
    // antes de escribir, no sobre `pk_product_links` después.
    assertThat(cuantosProductos()).isZero();
    assertThat(cuantosEnlaces()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-PM-383` — un tipo desconocido se rechaza con VAL-019 y uno sin dirección con"
          + " VAL-021")
  void tipoDesconocidoYDireccionQueFalta() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"currencyId":"%s","links":[{"type":"MANUAL_PDF","url":"https://vimeo.com/1"}]}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-019"))
        .andExpect(jsonPath("$.errors[0].field").value("links[0].type"));

    // Ausente, nulo y vacío son el MISMO rechazo: quitar un enlace es no
    // declararlo, no enviarlo sin dirección.
    String[] sinDireccion = {
      "{\"type\":\"VIDEO_PRESENTACION\"}",
      "{\"type\":\"VIDEO_PRESENTACION\",\"url\":null}",
      "{\"type\":\"VIDEO_PRESENTACION\",\"url\":\"   \"}"
    };
    for (String enlace : sinDireccion) {
      mvc.perform(
              alta(
                  """
                  {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                   "price":49.99,"currencyId":"%s","links":[%s]}
                  """
                      .formatted(USD, enlace)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].code").value("VAL-021"))
          .andExpect(jsonPath("$.errors[0].field").value("links[0].url"));
    }
    assertThat(cuantosProductos()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-PM-384` — el identificador se valida con VAL-022, y sobre una dirección con `?`"
          + " o `#` se rechaza con VAL-023")
  void elIdentificadorYSuRestriccionCruzada() throws Exception {
    for (String malo : new String[] {"cupon 15", "a".repeat(101)}) {
      mvc.perform(
              alta(
                  """
                  {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                   "price":49.99,"currencyId":"%s",
                   "links":[{"type":"CUPON_BOT","url":"https://t.me/nexusbot","externalId":"%s"}]}
                  """
                      .formatted(USD, malo)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].code").value("VAL-022"))
          .andExpect(jsonPath("$.errors[0].field").value("links[0].externalId"));
    }

    // La restricción es CRUZADA: el identificador sobre una dirección con
    // cadena de consulta daría un enlace roto que responde 200, y eso no se
    // descubre probándolo.
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"currencyId":"%s",
                 "links":[{"type":"VIDEO_PRESENTACION","url":"https://www.youtube.com/watch?v=abc","externalId":"abc"}]}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-023"))
        .andExpect(jsonPath("$.errors[0].field").value("links[0].url"));

    assertThat(cuantosProductos()).isZero();

    // Y la MISMA dirección sin identificador se admite: no es una prohibición
    // sobre la dirección — un video de YouTube es exactamente eso.
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"currencyId":"%s",
                 "links":[{"type":"VIDEO_PRESENTACION","url":"https://www.youtube.com/watch?v=abc"}]}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.links[0].url").value("https://www.youtube.com/watch?v=abc"));
  }

  @Test
  @DisplayName("`CA-PM-006` — el precio con más decimales de los que admite su moneda se rechaza")
  void decimalesSegunLaMoneda() throws Exception {
    // USD declara dos decimales: tres no caben, y no lo puede decir un CHECK
    // porque la escala vive en otra tabla.
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría","price":10.005,
                 "currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));

    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría","price":10.00,
                 "currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        // Y sale con los decimales de su moneda, no con la escala de la columna.
        .andExpect(jsonPath("$.price").value(10.00));
  }

  @Test
  @DisplayName("`CA-PM-010` — un destino inexistente es 422 y no 404: es un dato, no el recurso")
  void destinoInexistente() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"UPGRADE_X","type":"UPGRADE_MEMBRESIA","icon":"crown","name":"Ascenso",
                 "sourceMembershipId":"%s","targetMembershipId":"%s","price":49.99,
                 "currencyId":"%s"}
                """
                    .formatted(free, UUID.randomUUID(), USD)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
  }

  @Test
  @DisplayName("`CA-PM-007` — la moneda inexistente se distingue de la desactivada")
  void monedaInexistenteODesactivada() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría","price":10.00,
                 "currencyId":"%s"}
                """
                    .formatted(UUID.randomUUID())))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no existe")));

    // La moneda POR DEFECTO no se puede desactivar: lo impide
    // `ck_currencies_default_active`, de `RF-SP-023`. Se siembra otra.
    String euro = crearMonedaInactiva();

    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría","price":10.00,
                 "currencyId":"%s"}
                """
                    .formatted(euro)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("desactivada")));
  }

  @Test
  @DisplayName("`CA-PM-008` — el nombre que solo difiere en mayúsculas o acentos se rechaza")
  void nombreEquivalente() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría","price":10.00,
                 "currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated());

    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"OTRO","type":"BOT","name":"asesoria","price":10.00,
                 "currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  @DisplayName("`CA-PM-069` — el código duplicado se rechaza y se distingue del nombre")
  void codigoDuplicado() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría","price":10.00,
                 "currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated());

    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"asesoria","type":"BOT","name":"Otro nombre","price":10.00,
                 "currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].field").value("code"));
  }

  @Test
  @DisplayName("`CA-PM-092` y `CA-PM-093` — la vigencia es opcional y, si llega, mayor que cero")
  void vigencia() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"PERMANENTE","type":"BOT","name":"Permanente","price":10.00,
                 "currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.validityDays").value(org.hamcrest.Matchers.nullValue()));

    for (String vigencia : new String[] {"0", "-30"}) {
      mvc.perform(
              alta(
                  """
                  {"scope":"TIENDA","implementation":"AUTOMATICA","code":"OTRO","type":"BOT","name":"Otro","price":10.00,
                   "currencyId":"%s","validityDays":%s}
                  """
                      .formatted(USD, vigencia)))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  @DisplayName(
      "`CA-PM-011` — el alta registra un evento de creación con el estado inicial completo")
  void auditoriaDelAlta() throws Exception {
    UUID correlacion = UUID.randomUUID();

    mvc.perform(
            post("/api/v1/products")
                .with(admin())
                .header("X-Correlation-Id", correlacion.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"scope":"TIENDA","implementation":"AUTOMATICA","code":"UPGRADE_ORO","type":"UPGRADE_MEMBRESIA","icon":"crown","name":"Ascenso a Oro",
                     "sourceMembershipId":"%s","targetMembershipId":"%s","price":49.99,
                     "currencyId":"%s","validityDays":30}
                    """
                        .formatted(free, oro, USD)))
        .andExpect(status().isCreated());

    String cambios =
        jdbc.queryForObject(
            """
            SELECT changes::text FROM audit_change_log
             WHERE correlation_id = ? AND module = 'PM' AND action = 'CREATE'
            """,
            String.class,
            correlacion);

    assertThat(cambios)
        .contains("UPGRADE_ORO")
        .contains("49.99")
        .contains("INACTIVO")
        .contains("validity_days");
  }

  @Test
  @DisplayName("el alta NO emite evento de seguridad: un producto no concede privilegios")
  void sinEventoDeSeguridad() throws Exception {
    UUID correlacion = UUID.randomUUID();

    mvc.perform(
            post("/api/v1/products")
                .with(admin())
                .header("X-Correlation-Id", correlacion.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría","price":10.00,
                     "currencyId":"%s"}
                    """
                        .formatted(USD)))
        .andExpect(status().isCreated());

    Integer eventos =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_security_log WHERE correlation_id = ?",
            Integer.class,
            correlacion);

    assertThat(eventos).isZero();
  }

  @Test
  @DisplayName("`CA-PM-012` — sin `products:create` responde 403 y no registra nada")
  void sinPermiso() throws Exception {
    mvc.perform(
            post("/api/v1/products")
                .with(
                    user(UUID.randomUUID().toString())
                        .authorities(() -> "products:read", () -> "products:list"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría","price":10.00,
                     "currencyId":"%s"}
                    """
                        .formatted(USD)))
        .andExpect(status().isForbidden());

    assertThat(cuantosProductos()).isZero();
  }

  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-PM-110` y `CA-PM-111` — el alta sin alcance o sin implementación se rechaza")
  void alcanceEImplementacionSonObligatorios() throws Exception {
    mvc.perform(
            alta(
                """
                {"implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":10.00,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("scope"));

    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":10.00,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("implementation"));

    assertThat(cuantosProductos()).as("ningún rechazo registró nada").isZero();
  }

  @Test
  @DisplayName("`CA-PM-112` — un valor fuera del dominio se rechaza, y no se toma por ausente")
  void alcanceEImplementacionFueraDeDominio() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDAS","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT",
                 "name":"Asesoría","price":10.00,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest());

    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"SEMIAUTOMATICA","code":"ASESORIA","type":"BOT",
                 "name":"Asesoría","price":10.00,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest());

    assertThat(cuantosProductos()).isZero();
  }

  @Test
  @DisplayName("`CA-PM-113` — un BOT admite `AMBOS` y `MANUAL`: ninguna depende del tipo")
  void ningunaDependeDelTipo() throws Exception {
    // Es la prueba que separa estas dos reglas de `RN-PM-002` y `RN-PM-016`,
    // que SÍ dependen del tipo. Un bot también se muestra en algún sitio y
    // también se entrega de alguna forma.
    mvc.perform(
            alta(
                """
                {"scope":"AMBOS","implementation":"MANUAL","code":"ASESORIA","type":"BOT",
                 "name":"Asesoría","price":10.00,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.scope").value("AMBOS"))
        .andExpect(jsonPath("$.implementation").value("MANUAL"));
  }

  @Test
  @DisplayName("`CA-PM-114` — la respuesta las devuelve, y la instantánea del alta las guarda")
  void alcanceEImplementacionEnLaRespuestaYEnLaAuditoria() throws Exception {
    UUID correlacion = UUID.randomUUID();

    mvc.perform(
            post("/api/v1/products")
                .with(admin())
                .header("X-Correlation-Id", correlacion.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"scope":"AMBOS","implementation":"MANUAL","code":"UPGRADE_ORO",
                     "type":"UPGRADE_MEMBRESIA","icon":"crown","name":"Ascenso a Oro","sourceMembershipId":"%s",
                     "targetMembershipId":"%s","price":49.99,"currencyId":"%s"}
                    """
                        .formatted(free, oro, USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.scope").value("AMBOS"))
        .andExpect(jsonPath("$.implementation").value("MANUAL"));

    String cambios =
        jdbc.queryForObject(
            """
            SELECT changes::text FROM audit_change_log
             WHERE correlation_id = ? AND module = 'PM' AND action = 'CREATE'
            """,
            String.class,
            correlacion);

    // La instantánea es el ÚNICO sitio donde queda escrito con qué
    // configuración nació un producto que después `RF-PM-004` puede corregir.
    assertThat(cambios).contains("AMBOS").contains("MANUAL").contains("implementation");
  }

  @Test
  @DisplayName("`CA-PM-141` — la membresía resuelta trae su COLOR, junto al código, nombre y nivel")
  void laMembresiaResueltaTraeSuColor() throws Exception {
    // `oro` se siembra con nivel 1, y el color de la semilla es
    // `upper(lpad(to_hex(nivel * 4919), 6, '0'))` — para el nivel 1, `001337`.
    // Se afirma el valor EXACTO y no solo el formato: así la prueba demuestra
    // que viaja el color de ESA membresía y no el de cualquiera.
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"UPGRADE_ORO","type":"UPGRADE_MEMBRESIA","icon":"crown","name":"Ascenso a Oro",
                 "sourceMembershipId":"%s","targetMembershipId":"%s","price":49.99,
                 "currencyId":"%s"}
                """
                    .formatted(free, oro, USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.targetMembership.color").value("001337"))
        .andExpect(jsonPath("$.sourceMembership.color").value("004CDC"));
  }

  private RequestPostProcessor admin() {
    return user(UUID.randomUUID().toString()).authorities(() -> "products:create");
  }

  private MockHttpServletRequestBuilder alta(String cuerpo) {
    return post("/api/v1/products")
        .with(admin())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private String crearMembresia(String codigo, String nombre, int nivel, String superior) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, parent_membership_id, level, color)"
            + " VALUES (CAST(? AS uuid), ?, ?, CAST(? AS uuid), ?,"
            + " upper(lpad(to_hex(? * 4919), 6, '0')))",
        id.toString(),
        codigo,
        nombre,
        superior,
        nivel,
        nivel);
    return id.toString();
  }

  /** Una moneda inactiva y NO por defecto: la de por defecto no se puede desactivar. */
  private String crearMonedaInactiva() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)"
            + " VALUES (?, 'EUR', 'Euro', 'E', 2, false, false)",
        id);
    return id.toString();
  }

  private int cuantasMembresias() {
    Integer filas = jdbc.queryForObject("SELECT count(*) FROM memberships", Integer.class);
    return filas == null ? 0 : filas;
  }

  private int cuantosProductos() {
    Integer filas = jdbc.queryForObject("SELECT count(*) FROM products", Integer.class);
    return filas == null ? 0 : filas;
  }

  /** Las filas de `product_links`, para las pruebas que exigen que NO se escriba ninguna. */
  private int cuantosEnlaces() {
    Integer filas = jdbc.queryForObject("SELECT count(*) FROM product_links", Integer.class);
    return filas == null ? 0 : filas;
  }

  /** La instantánea del último evento de creación de producto. */
  private String ultimaInstantanea() {
    return jdbc.queryForObject(
        """
        SELECT changes::text FROM audit_change_log
         WHERE module = 'PM' AND entity = 'products' AND action = 'CREATE'
         ORDER BY occurred_at DESC LIMIT 1
        """,
        String.class);
  }

  /**
   * Deja `products` vacía al terminar CADA prueba.
   *
   * <p><b>No es higiene: es lo que impide romper a otras clases.</b> Un producto que sobreviva a
   * esta clase mantiene una clave foránea sobre `memberships`, y varias pruebas de `SP` empiezan
   * con `DELETE FROM memberships WHERE level > 0`. Ese borrado falla con violación de integridad, y
   * el fallo aparece <b>lejos de aquí</b> —en la clase que borra— y solo cuando el orden de
   * ejecución las pone en ese orden, que es la peor forma de romper una suite.
   */
  @AfterEach
  void vaciarCatalogo() {
    // `product_links` PRIMERO: su clave foránea no lleva `ON DELETE` —el
    // producto no se borra físicamente nunca (`RN-PM-010`)—, de modo que un
    // enlace vivo haría fallar el borrado de abajo, y el fallo aparecería en
    // la primera suite que vacíe el catálogo después de esta.
    jdbc.update("DELETE FROM product_links");
    jdbc.update("DELETE FROM products");
  }
}
