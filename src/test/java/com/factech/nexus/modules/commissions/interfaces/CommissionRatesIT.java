package com.factech.nexus.modules.commissions.interfaces;

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

/**
 * El alta de tarifas de comisión (`RF-CM-001` · `T-15`, `T-17`).
 *
 * <p>Lo que más importa aquí no es el camino feliz sino <b>las dos mitades de `RN-CM-003`</b> —la
 * persona debe portar el rol— y sobre todo <b>el no solapamiento</b>, que lo sostiene una
 * restricción del motor y no el caso de uso.
 */
@AutoConfigureMockMvc
class CommissionRatesIT extends IntegrationTestBase {

  /** `MANAGER`, sembrado por `V7` con `role_type = 'VENDEDOR'`. */
  private static final String VENDEDOR = "01a02a33-4c00-7005-9c4f-5e7ad1000005";

  /** `CONTABILIDAD`, que es funcionario: sirve para la mitad negativa de `RN-CM-001`. */
  private static final String NO_VENDEDOR = "01a02a33-4c00-7003-9c4f-5e7ad1000003";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID vendedora;

  @BeforeEach
  void preparar() {
    limpiar();
    vendedora = sembrarPersonaConRol("vendedora", VENDEDOR);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName("`CA-CM-001` — registra la tarifa por omisión del rol, con su grado")
  void tarifaPorOmisionDelRol() throws Exception {
    mvc.perform(alta(cuerpo(VENDEDOR, null, null, "10.00", "2026-01-01", null)))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/")))
        .andExpect(jsonPath("$.scope").value("ROL"))
        .andExpect(jsonPath("$.role.code").value("MANAGER"))
        // Nulos y PRESENTES: su ausencia es la que da el alcance, y un campo que
        // falta es indistinguible de uno que el cliente no conoce.
        .andExpect(jsonPath("$.product").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.user").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.validTo").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("`CA-CM-003` — registra la excepción de una persona que porta el rol")
  void excepcionDeUnaPersona() throws Exception {
    mvc.perform(alta(cuerpo(VENDEDOR, null, vendedora.toString(), "15.00", "2026-01-01", null)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.scope").value("PERSONA"))
        .andExpect(jsonPath("$.user.username").value("vendedora"));
  }

  @Test
  @DisplayName("`CA-CM-004` — el porcentaje CERO se registra, y no es lo mismo que no tener tarifa")
  void elCeroSeRegistra() throws Exception {
    mvc.perform(alta(cuerpo(VENDEDOR, null, null, "0", "2026-01-01", null)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.percentage").value(0));

    assertThat(cuantasTarifas()).isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-CM-006` — un rol que no es vendedor se rechaza")
  void soloComisionanLosVendedores() throws Exception {
    mvc.perform(alta(cuerpo(NO_VENDEDOR, null, null, "10.00", "2026-01-01", null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));

    assertThat(cuantasTarifas()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-CM-008` — una persona que NO porta el rol se rechaza: es la mitad que se olvida")
  void laPersonaDebePortarElRol() throws Exception {
    UUID ajena = sembrarPersonaConRol("ajena", null);

    mvc.perform(alta(cuerpo(VENDEDOR, null, ajena.toString(), "15.00", "2026-01-01", null)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));

    // Sin esta comprobación la tarifa quedaría registrada y no se aplicaría
    // nunca: no falla, se queda callada.
    assertThat(cuantasTarifas()).isZero();
  }

  @Test
  @DisplayName("la persona inexistente se distingue de la que no porta el rol")
  void personaInexistente() throws Exception {
    mvc.perform(
            alta(cuerpo(VENDEDOR, null, UUID.randomUUID().toString(), "15.00", "2026-01-01", null)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"));
  }

  @Test
  @DisplayName("`CA-CM-009` — dos tarifas del mismo caso que se solapan: la segunda recibe 409")
  void elSolapamientoSeRechaza() throws Exception {
    mvc.perform(alta(cuerpo(VENDEDOR, null, null, "10.00", "2026-01-01", "2026-06-30")))
        .andExpect(status().isCreated());

    mvc.perform(alta(cuerpo(VENDEDOR, null, null, "12.00", "2026-06-01", "2026-12-31")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-007"));

    assertThat(cuantasTarifas()).isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-CM-010` — dos consecutivas que NO comparten día se admiten: son el historial")
  void lasConsecutivasSeAdmiten() throws Exception {
    mvc.perform(alta(cuerpo(VENDEDOR, null, null, "10.00", "2026-01-01", "2026-06-30")))
        .andExpect(status().isCreated());

    mvc.perform(alta(cuerpo(VENDEDOR, null, null, "12.00", "2026-07-01", null)))
        .andExpect(status().isCreated());

    assertThat(cuantasTarifas()).isEqualTo(2);
  }

  @Test
  @DisplayName("el DÍA DE CORTE es inclusive: empezar el mismo día en que otra termina choca")
  void elDiaDeCorteEsInclusive() throws Exception {
    mvc.perform(alta(cuerpo(VENDEDOR, null, null, "10.00", "2026-01-01", "2026-06-30")))
        .andExpect(status().isCreated());

    // Con el rango semiabierto que PostgreSQL usa por omisión, esto NO chocaría
    // y el día 30 quedaría cubierto dos veces.
    mvc.perform(alta(cuerpo(VENDEDOR, null, null, "12.00", "2026-06-30", null)))
        .andExpect(status().isConflict());

    assertThat(cuantasTarifas()).isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-CM-013` — el mismo rol y periodo con productos distintos conviven")
  void productosDistintosConviven() throws Exception {
    UUID uno = sembrarProducto("BOT_UNO");
    UUID otro = sembrarProducto("BOT_DOS");

    mvc.perform(alta(cuerpo(VENDEDOR, uno.toString(), null, "10.00", "2026-01-01", null)))
        .andExpect(status().isCreated());
    mvc.perform(alta(cuerpo(VENDEDOR, otro.toString(), null, "20.00", "2026-01-01", null)))
        .andExpect(status().isCreated());

    assertThat(cuantasTarifas()).isEqualTo(2);
  }

  @Test
  @DisplayName("`CA-CM-011` y `CA-CM-012` — porcentaje fuera de rango y vigencia al revés")
  void validacionesDeForma() throws Exception {
    mvc.perform(alta(cuerpo(VENDEDOR, null, null, "101", "2026-01-01", null)))
        .andExpect(status().isBadRequest());

    mvc.perform(alta(cuerpo(VENDEDOR, null, null, "10.00", "2026-06-01", "2026-01-01")))
        .andExpect(status().isBadRequest());

    assertThat(cuantasTarifas()).isZero();
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  private static String cuerpo(
      String rol, String producto, String persona, String porcentaje, String desde, String hasta) {
    StringBuilder json = new StringBuilder("{\"roleId\":\"").append(rol).append("\"");
    if (producto != null) {
      json.append(",\"productId\":\"").append(producto).append("\"");
    }
    if (persona != null) {
      json.append(",\"userId\":\"").append(persona).append("\"");
    }
    json.append(",\"percentage\":").append(porcentaje);
    json.append(",\"validFrom\":\"").append(desde).append("\"");
    if (hasta != null) {
      json.append(",\"validTo\":\"").append(hasta).append("\"");
    }
    return json.append("}").toString();
  }

  private MockHttpServletRequestBuilder alta(String json) {
    return post("/api/v1/commission-rates")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  private UUID sembrarPersonaConRol(String usuario, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, username, email, first_name, last_name, password_hash, status)"
            + " VALUES (CAST(? AS uuid), ?, ?, 'Persona', 'De prueba', 'x', 'ACTIVO')",
        id.toString(),
        usuario,
        usuario + "@factech.co");
    if (rol != null) {
      jdbc.update(
          "INSERT INTO user_roles (user_id, role_id) VALUES (CAST(? AS uuid), CAST(? AS uuid))",
          id.toString(),
          rol);
    }
    return id;
  }

  private UUID sembrarProducto(String codigo) {
    UUID id = UUID.randomUUID();
    String moneda =
        jdbc.queryForObject("SELECT CAST(id AS text) FROM currencies LIMIT 1", String.class);
    jdbc.update(
        "INSERT INTO products (id, code, type, name, price, currency_id, status)"
            + " VALUES (CAST(? AS uuid), ?, 'BOT', ?, 10.00, CAST(? AS uuid), 'INACTIVO')",
        id.toString(),
        codigo,
        "Producto " + codigo,
        moneda);
    return id;
  }

  private long cuantasTarifas() {
    return jdbc.queryForObject("SELECT count(*) FROM commission_rates", Long.class);
  }

  private void limpiar() {
    jdbc.update("DELETE FROM commission_rates");
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM user_roles WHERE user_id <> CAST(? AS uuid)", SUPERADMIN.toString());
    jdbc.update("DELETE FROM users WHERE id <> CAST(? AS uuid)", SUPERADMIN.toString());
  }
}
