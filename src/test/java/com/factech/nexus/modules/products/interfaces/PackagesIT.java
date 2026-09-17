package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
 * El alta de paquetes (`RF-PM-017` · `T-10`): `CA-PM-261` a `CA-PM-268`.
 *
 * <p>Lo que más importa aquí no es el camino feliz sino <b>lo que el paquete no tiene</b>: ni
 * precio ni productos en el cuerpo, y la respuesta con la cuenta hecha sobre cero.
 */
@AutoConfigureMockMvc
class PackagesIT extends IntegrationTestBase {

  /** La moneda sembrada por `V15`, estable en todos los entornos. */
  static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private static final LocalDate HOY = LocalDate.now(ZoneOffset.UTC);

  @BeforeEach
  void limpiar() {
    PackageTestSupport.limpiarPaquetes(jdbc);
    PackageTestSupport.limpiarMonedasDePrueba(jdbc);
  }

  @AfterEach
  void vaciar() {
    // Higiene obligatoria: `product_packages` referencia `currencies`, y varias
    // suites de `SP` empiezan borrando las monedas que no son la de casa.
    PackageTestSupport.limpiarPaquetes(jdbc);
    PackageTestSupport.limpiarMonedasDePrueba(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-PM-261` — registra el paquete vacío, INACTIVO, con tres ceros y offerable false")
  void altaVaciaEInactiva() throws Exception {
    mvc.perform(
            alta(
                """
                {"code":"combo_oro","name":"  Combo Oro  ","description":"Oro con señales.",
                 "currencyId":"%s","scope":"AMBOS","validFrom":"%s"}
                """
                    .formatted(USD, HOY)))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", startsWith("/api/v1/packages/")))
        .andExpect(jsonPath("$.code").value("COMBO_ORO"))
        .andExpect(jsonPath("$.name").value("Combo Oro"))
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.scope").value("AMBOS"))
        .andExpect(jsonPath("$.currency.code").value("USD"))
        .andExpect(jsonPath("$.currency.decimalPlaces").value(2))
        .andExpect(jsonPath("$.items", hasSize(0)))
        .andExpect(jsonPath("$.price").value(0.00))
        .andExpect(jsonPath("$.listPrice").value(0.00))
        .andExpect(jsonPath("$.savings").value(0.00))
        .andExpect(jsonPath("$.exchange").value(nullValue()))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(jsonPath("$.offerableReason").value(containsString("menos de dos")))
        .andExpect(jsonPath("$.validFrom").value(HOY.toString()))
        .andExpect(jsonPath("$.validTo").value(nullValue()))
        .andExpect(jsonPath("$.deletedAt").doesNotExist())
        .andExpect(jsonPath("$.deletionReason").doesNotExist());

    // Y en la tabla no hay columna de precio que consultar: la fila nace sin él.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'product_packages'"
                    + " AND column_name = 'price'",
                Integer.class))
        .isZero();
  }

  @Test
  @DisplayName("`FA-001` — sin descripción se registra igual, y la descripción llega nula")
  void sinDescripcion() throws Exception {
    mvc.perform(alta(cuerpo("SOLO_BOTS", "Solo bots", USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.description").value(nullValue()));
  }

  @Test
  @DisplayName(
      "`CA-PM-262` — el código ya usado responde 409, TAMBIÉN si el que lo usa está retirado")
  void codigoRepetido() throws Exception {
    mvc.perform(alta(cuerpo("COMBO", "Combo uno", USD))).andExpect(status().isCreated());
    mvc.perform(alta(cuerpo("combo", "Combo dos", USD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"))
        .andExpect(jsonPath("$.errors[0].field").value("code"));

    jdbc.update("UPDATE product_packages SET deleted_at = now() WHERE code = 'COMBO'");
    mvc.perform(alta(cuerpo("COMBO", "Combo tres", USD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));
  }

  @Test
  @DisplayName(
      "`CA-PM-263` — el nombre de un paquete vivo responde 409 sin mayúsculas ni acentos, y el de"
          + " un retirado se admite")
  void nombreRepetido() throws Exception {
    mvc.perform(alta(cuerpo("UNO", "Promoción de otoño", USD))).andExpect(status().isCreated());
    mvc.perform(alta(cuerpo("DOS", "  PROMOCION DE OTONO ", USD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));

    jdbc.update("UPDATE product_packages SET deleted_at = now() WHERE code = 'UNO'");
    mvc.perform(alta(cuerpo("TRES", "Promoción de otoño", USD))).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("`CA-PM-264` — la moneda inexistente y la inactiva responden 422 con EX-003")
  void monedaInvalida() throws Exception {
    mvc.perform(alta(cuerpo("SIN_MONEDA", "Sin moneda", UUID.randomUUID().toString())))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"))
        .andExpect(jsonPath("$.errors[0].field").value("currencyId"));

    String euroInactivo = crearMonedaInactiva();
    mvc.perform(alta(cuerpo("EN_EUROS", "En euros", euroInactivo)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
  }

  @Test
  @DisplayName("`CA-PM-265` — código mal formado, nombre, moneda y alcance ausentes: 400, JUNTOS")
  void validacionesJuntas() throws Exception {
    mvc.perform(
            alta(
                """
        {"code":"combo-oro","name":"   "}
        """))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(
                    org.hamcrest.Matchers.hasItems(
                        "code", "name", "currencyId", "scope", "validFrom")))
        .andExpect(
            jsonPath("$.errors[*].code")
                .value(
                    org.hamcrest.Matchers.hasItems(
                        "VAL-001", "VAL-002", "VAL-003", "VAL-004", "VAL-006")));

    // El alcance fuera de dominio también es 400: lo rechaza Jackson.
    mvc.perform(
            alta(
                """
        {"code":"COMBO","name":"Combo","currencyId":"%s","scope":"HOTLINKS","validFrom":"%s"}
        """
                    .formatted(USD, HOY)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-PM-266` — `price`, `products` y `status` en el cuerpo responden 400")
  void camposQueElPaqueteNoTiene() throws Exception {
    for (String extra : List.of("\"price\":100.00", "\"products\":[]", "\"status\":\"ACTIVO\"")) {
      mvc.perform(
              alta(
                  """
                  {"code":"COMBO","name":"Combo","currencyId":"%s","scope":"TIENDA",
                   "validFrom":"%s",%s}
                  """
                      .formatted(USD, HOY, extra)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(containsString("no admite")));
    }
    assertThat(cuantosPaquetes()).isZero();
  }

  @Test
  @DisplayName("`CA-PM-267` — deja una fila CREATE en audit_change_log con el actor")
  void auditaLaCreacion() throws Exception {
    UUID actor = UUID.randomUUID();
    String id =
        com.jayway.jsonpath.JsonPath.read(
            mvc.perform(alta(cuerpo("AUDITADO", "Auditado", USD), actor))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");

    var fila =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, action, changes::text AS changes FROM audit_change_log"
                + " WHERE module = 'PM' AND entity = 'product_packages' AND entity_id = CAST(? AS uuid)",
            id);
    assertThat(fila.get("actor")).isEqualTo(actor.toString());
    assertThat(fila.get("action")).isEqualTo("CREATE");
    assertThat((String) fila.get("changes"))
        .contains("\"code\": \"AUDITADO\"")
        .contains("\"status\": \"INACTIVO\"")
        .doesNotContain("price");
  }

  @Test
  @DisplayName(
      "`CA-PM-268` — sin `packages:create` responde 403 aunque el actor porte los cuatro `products:`")
  void losProductsNoHabilitan() throws Exception {
    mvc.perform(
            post("/api/v1/packages")
                .with(
                    user(UUID.randomUUID().toString())
                        .authorities(
                            () -> "products:create",
                            () -> "products:read",
                            () -> "products:update",
                            () -> "products:delete"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("SIN_PERMISO", "Sin permiso", USD)))
        .andExpect(status().isForbidden());
    assertThat(cuantosPaquetes()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-PM-372` — con inicio y sin fin, con los dos, con inicio futuro y con fin ya pasado: 201, y las fechas vuelven tal cual")
  void vigenciaEnElAlta() throws Exception {
    mvc.perform(alta(cuerpo("SIN_FIN", "Sin fin", USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.validFrom").value(HOY.toString()))
        .andExpect(jsonPath("$.validTo").value(nullValue()));
    mvc.perform(alta(conVigencia("CON_FIN", HOY, HOY.plusDays(30))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.validFrom").value(HOY.toString()))
        .andExpect(jsonPath("$.validTo").value(HOY.plusDays(30).toString()));
    // Programado: se arma hoy y se ofrece cuando llegue el día.
    mvc.perform(alta(conVigencia("FUTURO", HOY.plusDays(7), null)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.validFrom").value(HOY.plusDays(7).toString()));
    // Nace cerrado: raro, pero no es un error — cerrar y errar no se distinguen.
    mvc.perform(alta(conVigencia("CERRADO", HOY.minusDays(30), HOY.minusDays(1))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.validTo").value(HOY.minusDays(1).toString()));
    // Un solo día.
    mvc.perform(alta(conVigencia("UN_DIA", HOY, HOY))).andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "`CA-PM-373` — sin validFrom es 400 con VAL-006 JUNTO a los demás errores de forma; el fin anterior al inicio es 400 con VAL-007")
  void vigenciaMalDeclarada() throws Exception {
    mvc.perform(
            alta(
                """
                {"code":"combo-oro","name":"Combo","currencyId":"%s","scope":"TIENDA"}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[*].code")
                .value(org.hamcrest.Matchers.hasItems("VAL-001", "VAL-006")))
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(org.hamcrest.Matchers.hasItems("code", "validFrom")));
    mvc.perform(alta(conVigencia("AL_REVES", HOY, HOY.minusDays(1))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-007"))
        .andExpect(jsonPath("$.errors[0].field").value("validTo"));
    // Y una fecha que no es fecha la rechaza Jackson, también con 400.
    mvc.perform(
            alta(
                """
                {"code":"COMBO","name":"Combo","currencyId":"%s","scope":"TIENDA","validFrom":"ayer"}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest());
    assertThat(cuantosPaquetes()).isZero();
  }

  private static String conVigencia(String codigo, LocalDate desde, LocalDate hasta) {
    return """
        {"code":"%s","name":"Paquete %s","currencyId":"%s","scope":"TIENDA",
         "validFrom":"%s","validTo":%s}
        """
        .formatted(codigo, codigo, USD, desde, hasta == null ? "null" : "\"" + hasta + "\"");
  }

  // ---------------------------------------------------------------------------

  static String cuerpo(String codigo, String nombre, String moneda) {
    return """
        {"code":"%s","name":"%s","currencyId":"%s","scope":"TIENDA","validFrom":"%s"}
        """
        .formatted(codigo, nombre, moneda, LocalDate.now(ZoneOffset.UTC));
  }

  private MockHttpServletRequestBuilder alta(String cuerpo) {
    return alta(cuerpo, UUID.randomUUID());
  }

  private MockHttpServletRequestBuilder alta(String cuerpo, UUID actor) {
    return post("/api/v1/packages")
        .with(creador(actor))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private static RequestPostProcessor creador(UUID actor) {
    return user(actor.toString()).authorities(() -> "packages:create");
  }

  private String crearMonedaInactiva() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)"
            + " VALUES (?, 'EUR', 'Euro', 'E', 2, false, false)",
        id);
    return id.toString();
  }

  private int cuantosPaquetes() {
    Integer filas = jdbc.queryForObject("SELECT count(*) FROM product_packages", Integer.class);
    return filas == null ? 0 : filas;
  }
}
