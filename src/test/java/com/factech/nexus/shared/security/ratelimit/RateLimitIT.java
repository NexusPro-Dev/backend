package com.factech.nexus.shared.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Límite de tasa de los endpoints públicos de autenticación (issue #21).
 *
 * <p><b>Las cotas se bajan a tres y dos</b> para que la prueba no tenga que emitir sesenta
 * peticiones. Lo que se verifica no son los números de producción —esos viven en `application.yml`
 * y se justifican allí— sino las propiedades: que la cota existe, que distingue origen de
 * identidad, que el rechazo dice cuánto esperar y que **no inunda la auditoría**.
 *
 * <p>El límite está apagado para el resto de la suite ({@code IntegrationTestBase}), porque varias
 * clases provocan ráfagas contra el inicio de sesión a propósito. Aquí se enciende.
 */
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      // La clave concreta y NO el marcador `RATE_LIMIT_ENABLED`: `@DynamicPropertySource`
      // de la clase base gana a `@TestPropertySource`, de modo que apagar el
      // limite alli dejaria esta clase sin nada que probar — y en silencio.
      "nexus.security.rate-limit.enabled=true",
      "nexus.security.rate-limit.login.por-origen=3",
      "nexus.security.rate-limit.login.por-identidad=2",
      "nexus.security.rate-limit.login.ventana=PT1M",
      "nexus.security.rate-limit.refresh.por-origen=2",
      "nexus.security.rate-limit.refresh.ventana=PT1M",
      "nexus.security.rate-limit.hotlink.por-origen=2",
      "nexus.security.rate-limit.hotlink.ventana=PT1M",
      "nexus.security.rate-limit.public-catalog.por-origen=2",
      "nexus.security.rate-limit.public-catalog.ventana=PT1M"
    })
class RateLimitIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RateLimitLedger contador;

  @BeforeEach
  void empezarSinRafagaHeredada() {
    // El contador vive en memoria y sobrevive entre pruebas de la misma clase:
    // sin esto, la segunda prueba empezaría con la ventana ya gastada por la
    // primera y fallaría por algo que no está comprobando.
    contador.limpiar();
    jdbc.update("DELETE FROM audit_security_log WHERE event_type = 'RATE_LIMIT_EXCEEDED'");
  }

  @Test
  @DisplayName("la cota por identidad corta antes que la del origen, y dice cuánto esperar")
  void cotaPorIdentidad() throws Exception {
    // Dos por identidad: la tercera con el mismo identificador no se atiende.
    atendida(login("jperez"));
    atendida(login("jperez"));

    login("jperez")
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(
            jsonPath("$.type").value("https://nexus.factech.co/errors/demasiadas-peticiones"))
        .andExpect(jsonPath("$.status").value(429))
        .andExpect(jsonPath("$.retryAfterSeconds").value(org.hamcrest.Matchers.greaterThan(0)))
        // El mismo formato de error que el resto de la API, aunque lo escriba
        // un filtro y no el manejador global.
        .andExpect(jsonPath("$.correlationId").isNotEmpty())
        .andExpect(jsonPath("$.errors").isArray());
  }

  @Test
  @DisplayName("la respuesta NO revela contra qué identidad se topó el límite")
  void elRechazoNoDelataLaCuenta() throws Exception {
    atendida(login("amartinez"));
    atendida(login("amartinez"));

    String cuerpo =
        login("amartinez")
            .andExpect(status().isTooManyRequests())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Decir «esta cuenta está limitada» confirmaría que existe.
    assertThat(cuerpo).doesNotContain("amartinez");
  }

  @Test
  @DisplayName("la cota por origen alcanza a identidades distintas")
  void cotaPorOrigen() throws Exception {
    // Tres por origen, con identidades distintas para no topar antes con la
    // cota por identidad: es el rociado de contraseñas, que NO dispara el
    // bloqueo por cuenta porque deja un solo fallo en cada una.
    atendida(login("uno"));
    atendida(login("dos"));
    atendida(login("tres"));

    login("cuatro").andExpect(status().isTooManyRequests());
  }

  @Test
  @DisplayName("cada endpoint tiene su propia cota: el refresco no hereda la del login")
  void cotasIndependientes() throws Exception {
    atendida(login("uno"));
    atendida(login("dos"));
    atendida(login("tres"));
    login("cuatro").andExpect(status().isTooManyRequests());

    // El refresco conserva las suyas.
    atendida(refresh());
    atendida(refresh());
    refresh().andExpect(status().isTooManyRequests());
  }

  @Test
  @DisplayName("la auditoría registra UNA vez por ventana, no una por petición rechazada")
  void laDefensaNoInundaLaAuditoria() throws Exception {
    atendida(login("jperez"));
    atendida(login("jperez"));

    // Diez rechazos seguidos. Una fila por cada uno convertiría la defensa en el
    // ataque: mil peticiones por segundo son mil filas por segundo en la tabla
    // que sirve para investigar.
    for (int i = 0; i < 10; i++) {
      login("jperez").andExpect(status().isTooManyRequests());
    }

    java.util.List<String> detalles =
        jdbc.queryForList(
            "SELECT detail::text FROM audit_security_log"
                + " WHERE event_type = 'RATE_LIMIT_EXCEEDED' ORDER BY occurred_at",
            String.class);

    // DOS eventos y no doce: uno por EJE, no uno por petición rechazada. Son dos
    // hechos distintos y los dos interesan —la ráfaga topó primero con la cota
    // de la identidad y después con la del origen—, mientras que repetirlos en
    // cada intento sepultaría el registro que sirve para investigar.
    assertThat(detalles).hasSize(2);
    assertThat(detalles).anyMatch(detalle -> detalle.contains("identidad"));
    assertThat(detalles).anyMatch(detalle -> detalle.contains("origen"));

    // Y ninguno delata contra qué cuenta iba la ráfaga.
    assertThat(detalles).allMatch(detalle -> detalle.contains("/api/v1/auth/login"));
    assertThat(detalles).noneMatch(detalle -> detalle.contains("jperez"));
  }

  @Test
  @DisplayName("el evento se escribe con severidad ALTA y resultado de fallo")
  void severidadDelEvento() throws Exception {
    atendida(login("jperez"));
    atendida(login("jperez"));
    login("jperez").andExpect(status().isTooManyRequests());

    java.util.Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT severity, outcome FROM audit_security_log"
                + " WHERE event_type = 'RATE_LIMIT_EXCEEDED' LIMIT 1");

    // Quien topa con el límite hace algo que ningún cliente legítimo hace: debe
    // poder encontrarse buscando por severidad, junto a los intentos de escalada.
    assertThat(fila.get("severity")).isEqualTo("ALTA");
    assertThat(fila.get("outcome")).isEqualTo("FAILURE");
  }

  @Test
  @DisplayName("el cuerpo llega intacto al controlador pese a leerse en el filtro")
  void elCuerpoSobreviveAlFiltro() throws Exception {
    // Si el filtro consumiera el flujo sin devolverlo, el caso de uso recibiría
    // un cuerpo vacío y respondería 400 por campos obligatorios que el cliente
    // sí envió — un fallo desconcertante y difícil de atribuir al filtro. Por eso
    // lo que se afirma aquí es que NO hay 400: el controlador vio el cuerpo.
    int estado = login("jperez").andReturn().getResponse().getStatus();

    assertThat(estado)
        .as("el controlador recibió un cuerpo vacío: el filtro se quedó con el flujo")
        .isNotEqualTo(400);
    assertThat(estado).isNotEqualTo(429);
  }

  @Test
  @DisplayName("recuperación — cinco por minuto, y la sexta cuesta cinco minutos de espera")
  void recuperacionPenalizaAlSuperarLaCota() throws Exception {
    for (int i = 1; i <= 5; i++) {
      atendida(recuperar("olvidadiza@factech.co"));
    }

    // La sexta topa. Lo que se afirma es la ESPERA: sin penalización sería la
    // que le quede a la ventana —a lo sumo sesenta segundos—, y con ella son
    // los trescientos del castigo.
    recuperar("olvidadiza@factech.co")
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.retryAfterSeconds").value(org.hamcrest.Matchers.greaterThan(60)))
        .andExpect(
            jsonPath("$.retryAfterSeconds").value(org.hamcrest.Matchers.lessThanOrEqualTo(300)));
  }

  @Test
  @DisplayName("insistir durante el castigo no lo alarga: la espera decrece, no vuelve a empezar")
  void insistirNoReiniciaElCastigo() throws Exception {
    for (int i = 1; i <= 5; i++) {
      atendida(recuperar("insistente@factech.co"));
    }

    int primera = esperaDe(recuperar("insistente@factech.co"));
    int segunda = esperaDe(recuperar("insistente@factech.co"));
    int tercera = esperaDe(recuperar("insistente@factech.co"));

    // No se puede adelantar el reloj de un contexto real, de modo que lo que se
    // comprueba es que la espera NO CRECE: si cada intento renovara el castigo,
    // las tres dirían trescientos y quien reintenta solo quedaría encerrado.
    // El caso con el reloj en la mano está en `RateLimitLedgerTest`.
    assertThat(segunda).isLessThanOrEqualTo(primera);
    assertThat(tercera).isLessThanOrEqualTo(segunda);
  }

  @Test
  @DisplayName("hotlink — el recorrido a ciegas topa aunque cada nombre probado sea distinto")
  void elHotlinkSeAcotaPorOrigenYNoPorEnlace() throws Exception {
    // `CA-PM-137`. Y lo que de verdad se afirma aquí son los NOMBRES DISTINTOS:
    // quien barre el padrón no repite ninguno, de modo que una cota contada por
    // URI le daría un cubo nuevo en cada intento y no cortaría jamás. Con dos
    // por origen, la tercera topa aunque las tres rutas sean tres rutas.
    atendida(hotlink("ana", "UPGRADE_ORO"));
    atendida(hotlink("beatriz", "UPGRADE_ORO"));

    hotlink("carlos", "UPGRADE_ORO")
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.status").value(429))
        // El `instance` sí es la ruta pedida y no el prefijo: lo que se cuenta
        // por familia se responde por petición.
        .andExpect(jsonPath("$.instance").value("/api/v1/hotlinks/carlos/UPGRADE_ORO"));
  }

  @Test
  @DisplayName(
      "hotlink del paquete — comparte la cota de la familia: dos de producto y la tercera de paquete topa, y al revés")
  void elHotlinkDelPaqueteCompartelaCotaDeLaFamilia() throws Exception {
    // `CA-PM-333`. `RateLimitFilter` decide por PREFIJO y no por patrón, de
    // modo que la ruta de tres segmentos hereda la cota sin política nueva. Es
    // la mitad que SÍ era cierta de lo que `pm.md` v0.31.0 decía; la otra —la
    // declaración pública— la comprueba `PackageHotlinkIT`.
    atendida(hotlink("ana", "UPGRADE_ORO"));
    atendida(hotlink("beatriz", "UPGRADE_ORO"));
    mvc.perform(get("/api/v1/hotlinks/{usuario}/packages/{codigo}", "carlos", "COMBO"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.instance").value("/api/v1/hotlinks/carlos/packages/COMBO"));
  }

  @Test
  @DisplayName("hotlink del paquete — y al revés: dos de paquete agotan la tercera de producto")
  void yAlReves() throws Exception {
    atendida(mvc.perform(get("/api/v1/hotlinks/{usuario}/packages/{codigo}", "ana", "COMBO")));
    atendida(mvc.perform(get("/api/v1/hotlinks/{usuario}/packages/{codigo}", "beatriz", "COMBO")));
    hotlink("carlos", "UPGRADE_ORO").andExpect(status().isTooManyRequests());
  }

  @Test
  @DisplayName("hotlink — la auditoría del rechazo no se queda con el nombre probado")
  void elRechazoDelHotlinkNoRegistraElNombreProbado() throws Exception {
    atendida(hotlink("ana", "UPGRADE_ORO"));
    atendida(hotlink("beatriz", "UPGRADE_ORO"));
    hotlink("carlos", "UPGRADE_ORO").andExpect(status().isTooManyRequests());

    java.util.List<String> detalles =
        jdbc.queryForList(
            "SELECT detail::text FROM audit_security_log"
                + " WHERE event_type = 'RATE_LIMIT_EXCEEDED'",
            String.class);

    // La ruta lleva dentro el nombre de usuario que alguien probó. Guardar uno
    // al azar de los mil de un recorrido es un dato personal a cambio de nada:
    // lo que hay que investigar es el origen, y ese sí queda.
    assertThat(detalles).hasSize(1);
    assertThat(detalles).allMatch(detalle -> detalle.contains("GET /api/v1/hotlinks/"));
    assertThat(detalles).noneMatch(detalle -> detalle.contains("carlos"));
  }

  @Test
  @DisplayName("reseñas — identificadores distintos topan con la MISMA cota: se cuenta por familia")
  void lasResenasSeAcotanPorFamiliaYNoPorProducto() throws Exception {
    // `CA-PM-208`. Como en el hotlink, lo que se afirma son los IDENTIFICADORES
    // DISTINTOS: quien recorre productos al azar buscando cuales responden 200
    // no repite ninguno, y una cota por URI le daria un cubo nuevo cada vez.
    atendida(resenas(java.util.UUID.randomUUID()));
    atendida(resenas(java.util.UUID.randomUUID()));

    java.util.UUID tercero = java.util.UUID.randomUUID();
    resenas(tercero)
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.instance").value("/api/v1/products/" + tercero + "/comments"));
  }

  @Test
  @DisplayName("reseñas — la familia no alcanza a la reseña propia ni al POST")
  void laFamiliaNoAlcanzaALasRutasHermanas() throws Exception {
    atendida(resenas(java.util.UUID.randomUUID()));
    atendida(resenas(java.util.UUID.randomUUID()));

    // Sin token responden 401, no 429: el filtro de tasa no las mira.
    mvc.perform(get("/api/v1/products/{id}/comments/mine", java.util.UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/products/{id}/comments", java.util.UUID.randomUUID())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "portadas — identificadores distintos topan con la MISMA cota: se cuenta por familia")
  void lasPortadasSeAcotanPorFamiliaYNoPorImagen() throws Exception {
    // `CA-PM-260`. La octava cota, y la primera que acota bytes: la ruta lleva
    // el identificador de la imagen, y contar por URI daria un cubo por imagen.
    atendida(portada(java.util.UUID.randomUUID()));
    atendida(portada(java.util.UUID.randomUUID()));

    java.util.UUID tercera = java.util.UUID.randomUUID();
    portada(tercera)
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.instance").value("/api/v1/product-images/" + tercera));

    // Y el registro del rechazo lleva el prefijo, no la ruta con el identificador.
    java.util.List<String> detalles =
        jdbc.queryForList(
            "SELECT detail::text FROM audit_security_log"
                + " WHERE event_type = 'RATE_LIMIT_EXCEEDED'",
            String.class);
    assertThat(detalles).hasSize(1);
    assertThat(detalles).allMatch(detalle -> detalle.contains("GET /api/v1/product-images/"));
    assertThat(detalles).noneMatch(detalle -> detalle.contains(tercera.toString()));
  }

  private org.springframework.test.web.servlet.ResultActions portada(java.util.UUID imagen)
      throws Exception {
    // La imagen no existe, y da igual: lo atendido responde 404 y lo cortado 429.
    return mvc.perform(get("/api/v1/product-images/{id}", imagen));
  }

  private org.springframework.test.web.servlet.ResultActions resenas(java.util.UUID producto)
      throws Exception {
    // El producto no existe, y da igual: lo atendido responde 404 y lo cortado 429.
    return mvc.perform(get("/api/v1/products/{id}/comments", producto));
  }

  private org.springframework.test.web.servlet.ResultActions hotlink(String usuario, String codigo)
      throws Exception {
    // Ni el usuario ni el producto existen, y da igual: el filtro corta antes
    // del controlador, de modo que lo atendido responde 404 y lo cortado 429.
    return mvc.perform(get("/api/v1/hotlinks/{usuario}/{codigo}", usuario, codigo));
  }

  private org.springframework.test.web.servlet.ResultActions recuperar(String identidad)
      throws Exception {
    return mvc.perform(
        post("/api/v1/auth/password-recovery")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"identifier\":\"%s\"}".formatted(identidad)));
  }

  /** Los segundos que el rechazo dice que hay que esperar. */
  private int esperaDe(org.springframework.test.web.servlet.ResultActions resultado)
      throws Exception {
    String cuerpo =
        resultado
            .andExpect(status().isTooManyRequests())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return com.jayway.jsonpath.JsonPath.read(cuerpo, "$.retryAfterSeconds");
  }

  /**
   * Una peticion atendida: lo unico que se afirma es que NO la corto el limite.
   *
   * <p>Deliberadamente no se fija el codigo concreto. Una credencial invalida responde `401`, y
   * `RF-SP-034` puede devolver `423` en cuanto su contador de intentos entra en juego: atarse a uno
   * de los dos haria fallar esta clase por un cambio en la politica de bloqueo, que es otra cosa.
   */
  private void atendida(org.springframework.test.web.servlet.ResultActions resultado)
      throws Exception {
    int estado = resultado.andReturn().getResponse().getStatus();
    assertThat(estado).as("la peticion no deberia haber topado con el limite").isNotEqualTo(429);
  }

  private org.springframework.test.web.servlet.ResultActions login(String identificador)
      throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"identifier\":\"%s\",\"password\":\"ClaveIncorrecta2026\"}"
                    .formatted(identificador)));
  }

  private org.springframework.test.web.servlet.ResultActions refresh() throws Exception {
    return mvc.perform(
        post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"no-existe\"}"));
  }
}
