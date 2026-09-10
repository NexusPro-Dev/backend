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
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"UPGRADE_ORO","type":"UPGRADE_MEMBRESIA","name":"Ascenso a Oro",
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
  @DisplayName("`CA-PM-098` — el icono es opcional: un upgrade sin él llega null y presente")
  void elIconoEsOpcional() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"UPGRADE_ORO","type":"UPGRADE_MEMBRESIA","name":"Ascenso a Oro",
                 "sourceMembershipId":"%s","targetMembershipId":"%s","price":49.99,
                 "currencyId":"%s"}
                """
                    .formatted(free, oro, USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.icon").value(org.hamcrest.Matchers.nullValue()));
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
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"UPGRADE_ORO","type":"UPGRADE_MEMBRESIA","name":"Ascenso",
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
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"SALTO_ORO","type":"UPGRADE_MEMBRESIA","name":"De Free a Oro",
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
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"SIN_ORIGEN","type":"UPGRADE_MEMBRESIA","name":"Sin origen",
                 "targetMembershipId":"%s","price":49.99,"currencyId":"%s"}
                """
                    .formatted(oro, USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-007"))
        .andExpect(jsonPath("$.errors[0].field").value("sourceMembershipId"));

    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"SIN_DESTINO","type":"UPGRADE_MEMBRESIA","name":"Sin destino",
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
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"DESCENSO","type":"UPGRADE_MEMBRESIA","name":"Bajada disfrazada",
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
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"RENOVAR_ORO","type":"UPGRADE_MEMBRESIA","name":"Renovar Oro",
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
                 "price":0,"publicPrice":0,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.price").value(0))
        .andExpect(jsonPath("$.publicPrice").value(0));
  }

  @Test
  @DisplayName("`CA-PM-145` — el alta admite los DOS precios y la respuesta devuelve los dos")
  void losDosPrecios() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"publicPrice":59.99,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        // Los dos con los decimales de SU moneda, no con la escala de la
        // columna: `59.99`, no `59.9900`.
        .andExpect(jsonPath("$.price").value(49.99))
        .andExpect(jsonPath("$.publicPrice").value(59.99));
  }

  @Test
  @DisplayName("`CA-PM-146` — sin precio público el campo llega PRESENTE y nulo, no ausente")
  void sinPrecioPublico() throws Exception {
    // Su nulo SIGNIFICA «se anuncia con el precio del sistema», y un campo que
    // desaparece del JSON no puede decir eso — sería indistinguible de uno que
    // el cliente no conoce.
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.publicPrice").doesNotExist())
        .andExpect(jsonPath("$").value(org.hamcrest.Matchers.hasKey("publicPrice")));
  }

  @Test
  @DisplayName("`CA-PM-147` — un precio público negativo se rechaza, y el error nombra SU campo")
  void precioPublicoNegativo() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"publicPrice":-1,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest())
        // Con dos importes, un mensaje que no distingue obliga a probar los dos.
        .andExpect(jsonPath("$.errors[0].field").value("publicPrice"));

    assertThat(cuantosProductos()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-PM-148` — el precio público con decimales de más se rechaza aunque el otro quepa")
  void decimalesDelPrecioPublico() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":10.00,"publicPrice":10.005,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"))
        // El del sistema SÍ cabe: sin el campo en el error, quien lo recibe
        // tendría que probar los dos para saber cuál corregir.
        .andExpect(jsonPath("$.errors[0].field").value("publicPrice"));

    assertThat(cuantosProductos()).isZero();
  }

  @Test
  @DisplayName("`CA-PM-150` — la instantánea del evento de creación incluye el precio público")
  void laInstantaneaLlevaElPrecioPublico() throws Exception {
    mvc.perform(
            alta(
                """
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"ASESORIA","type":"BOT","name":"Asesoría",
                 "price":49.99,"publicPrice":59.99,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated());

    // Es el único sitio donde queda escrito CON QUÉ SE ANUNCIABA un producto
    // que después se corrige.
    String cambios =
        jdbc.queryForObject(
            """
            SELECT changes::text FROM audit_change_log
             WHERE module = 'PM' AND entity = 'products' AND action = 'CREATE'
             ORDER BY occurred_at DESC LIMIT 1
            """,
            String.class);
    assertThat(cambios).contains("public_price").contains("59.99");
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
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"UPGRADE_X","type":"UPGRADE_MEMBRESIA","name":"Ascenso",
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
                    {"scope":"TIENDA","implementation":"AUTOMATICA","code":"UPGRADE_ORO","type":"UPGRADE_MEMBRESIA","name":"Ascenso a Oro",
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
                .with(user(UUID.randomUUID().toString()).authorities(() -> "products:read"))
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
  @DisplayName("`CA-PM-113` — un BOT admite `HOTLINKS` y `MANUAL`: ninguna depende del tipo")
  void ningunaDependeDelTipo() throws Exception {
    // Es la prueba que separa estas dos reglas de `RN-PM-002` y `RN-PM-016`,
    // que SÍ dependen del tipo. Un bot también se muestra en algún sitio y
    // también se entrega de alguna forma.
    mvc.perform(
            alta(
                """
                {"scope":"HOTLINKS","implementation":"MANUAL","code":"ASESORIA","type":"BOT",
                 "name":"Asesoría","price":10.00,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.scope").value("HOTLINKS"))
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
                    {"scope":"HOTLINKS","implementation":"MANUAL","code":"UPGRADE_ORO",
                     "type":"UPGRADE_MEMBRESIA","name":"Ascenso a Oro","sourceMembershipId":"%s",
                     "targetMembershipId":"%s","price":49.99,"currencyId":"%s"}
                    """
                        .formatted(free, oro, USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.scope").value("HOTLINKS"))
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
    assertThat(cambios).contains("HOTLINKS").contains("MANUAL").contains("implementation");
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
                {"scope":"TIENDA","implementation":"AUTOMATICA","code":"UPGRADE_ORO","type":"UPGRADE_MEMBRESIA","name":"Ascenso a Oro",
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
    jdbc.update("DELETE FROM products");
  }
}
