package com.factech.nexus.modules.commissions.interfaces;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * La resolución de la comisión efectiva (`RF-CM-005` · `T-07`, `T-08`).
 *
 * <p><b>Es la suite que verifica `RN-CM-004`</b>, y ninguna de estas pruebas mira el camino feliz:
 * un porcentaje devuelto siempre es <b>plausible</b>, aunque venga del grado equivocado. Lo que se
 * comprueba es <b>cuál</b> gana, y que la ausencia no se confunda con el cero.
 */
@AutoConfigureMockMvc
class EffectiveCommissionIT extends IntegrationTestBase {

  private static final String VENDEDOR = "01a02a33-4c00-7005-9c4f-5e7ad1000003";
  private static final String NO_VENDEDOR = "01a02a33-4c00-7002-9c4f-5e7ad1000002";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID vendedora;
  private UUID producto;

  @BeforeEach
  void preparar() {
    limpiar();
    vendedora = persona("vendedora", VENDEDOR);
    producto = producto("BOT_UNO");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName("`CA-CM-039` — con solo la tarifa del rol, gana esa y lo dice")
  void soloLaDelRol() throws Exception {
    tarifa(null, null, "10.00", "2026-01-01", null);

    mvc.perform(efectiva(vendedora, producto, "2026-03-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("RESUELTA"))
        .andExpect(jsonPath("$.percentage").value(10.00))
        .andExpect(jsonPath("$.scope").value("ROL"));
  }

  @Test
  @DisplayName("`CA-CM-040` — con la del rol y la del producto, GANA LA DEL PRODUCTO")
  void ganaLaDelProducto() throws Exception {
    tarifa(null, null, "10.00", "2026-01-01", null);
    tarifa(producto, null, "20.00", "2026-01-01", null);

    mvc.perform(efectiva(vendedora, producto, "2026-03-01"))
        .andExpect(jsonPath("$.percentage").value(20.00))
        .andExpect(jsonPath("$.scope").value("PRODUCTO"));
  }

  @Test
  @DisplayName("`CA-CM-041` — con la del rol y la de la persona, GANA LA DE LA PERSONA")
  void ganaLaDeLaPersona() throws Exception {
    tarifa(null, null, "10.00", "2026-01-01", null);
    tarifa(null, vendedora, "30.00", "2026-01-01", null);

    mvc.perform(efectiva(vendedora, producto, "2026-03-01"))
        .andExpect(jsonPath("$.percentage").value(30.00))
        .andExpect(jsonPath("$.scope").value("PERSONA"));
  }

  @Test
  @DisplayName("`CA-CM-041` — la PERSONA pesa más que el PRODUCTO cuando compiten")
  void laPersonaPesaMasQueElProducto() throws Exception {
    tarifa(producto, null, "20.00", "2026-01-01", null);
    tarifa(null, vendedora, "30.00", "2026-01-01", null);

    // Es el escalón que decide el orden de `RN-CM-004`, y el que se implementa
    // mal si alguien reordena los criterios.
    mvc.perform(efectiva(vendedora, producto, "2026-03-01"))
        .andExpect(jsonPath("$.percentage").value(30.00))
        .andExpect(jsonPath("$.scope").value("PERSONA"));
  }

  @Test
  @DisplayName("`CA-CM-042` — con los cuatro grados, gana la de la persona PARA ESE producto")
  void ganaLaMasEspecifica() throws Exception {
    tarifa(null, null, "10.00", "2026-01-01", null);
    tarifa(producto, null, "20.00", "2026-01-01", null);
    tarifa(null, vendedora, "30.00", "2026-01-01", null);
    tarifa(producto, vendedora, "40.00", "2026-01-01", null);

    mvc.perform(efectiva(vendedora, producto, "2026-03-01"))
        .andExpect(jsonPath("$.percentage").value(40.00))
        .andExpect(jsonPath("$.scope").value("PERSONA_Y_PRODUCTO"));
  }

  @Test
  @DisplayName(
      "`CA-CM-043` y `CA-CM-045` — se ignora la que no rige, y una fecha pasada da la de entonces")
  void laFechaManda() throws Exception {
    tarifa(null, null, "10.00", "2026-01-01", "2026-06-30");
    tarifa(null, null, "12.00", "2026-07-01", null);

    mvc.perform(efectiva(vendedora, producto, "2026-03-01"))
        .andExpect(jsonPath("$.percentage").value(10.00));

    mvc.perform(efectiva(vendedora, producto, "2026-09-01"))
        .andExpect(jsonPath("$.percentage").value(12.00));

    // Antes de toda tarifa: NO se extrapola hacia atrás la más antigua.
    mvc.perform(efectiva(vendedora, producto, "2025-12-31"))
        .andExpect(jsonPath("$.outcome").value("SIN_TARIFA"));
  }

  @Test
  @DisplayName("`CA-CM-046` — una tarifa RETIRADA se ignora y gana la siguiente en precedencia")
  void laRetiradaNoGana() throws Exception {
    tarifa(null, null, "10.00", "2026-01-01", null);
    UUID especifica = tarifa(producto, vendedora, "40.00", "2026-01-01", null);

    jdbc.update(
        "UPDATE commission_rates SET deleted_at = now() WHERE id = CAST(? AS uuid)",
        especifica.toString());

    mvc.perform(efectiva(vendedora, producto, "2026-03-01"))
        .andExpect(jsonPath("$.percentage").value(10.00))
        .andExpect(jsonPath("$.scope").value("ROL"));
  }

  @Test
  @DisplayName("`CA-CM-047` y `CA-CM-048` — CERO y AUSENCIA no son lo mismo")
  void elCeroNoEsLaAusencia() throws Exception {
    // Sin ninguna tarifa: el porcentaje llega NULO y presente, nunca cero.
    mvc.perform(efectiva(vendedora, producto, "2026-03-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("SIN_TARIFA"))
        .andExpect(jsonPath("$.percentage").value(org.hamcrest.Matchers.nullValue()));

    // Con una tarifa del cero: es una respuesta afirmativa — no comisiona, y
    // alguien lo decidió.
    tarifa(producto, null, "0", "2026-01-01", null);

    mvc.perform(efectiva(vendedora, producto, "2026-03-01"))
        .andExpect(jsonPath("$.outcome").value("RESUELTA"))
        .andExpect(jsonPath("$.percentage").value(0))
        .andExpect(jsonPath("$.rateId").value(org.hamcrest.Matchers.notNullValue()));
  }

  @Test
  @DisplayName(
      "`CA-CM-050` — quien no porta rol vendedor NO COMISIONA, y se distingue de sin tarifa")
  void sinRolVendedor() throws Exception {
    UUID contable = persona("contable", NO_VENDEDOR);

    mvc.perform(efectiva(contable, producto, "2026-03-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("NO_COMISIONA"))
        .andExpect(jsonPath("$.percentage").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("`CA-CM-049` — un producto RETIRADO se resuelve con normalidad")
  void elProductoRetiradoSeResuelve() throws Exception {
    tarifa(producto, null, "20.00", "2026-01-01", null);
    jdbc.update(
        "UPDATE products SET deleted_at = now() WHERE id = CAST(? AS uuid)", producto.toString());

    // Preguntar qué se pagaba por algo que ya no se vende es legítimo, y es la
    // consulta que una liquidación atrasada necesita.
    mvc.perform(efectiva(vendedora, producto, "2026-03-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.percentage").value(20.00));
  }

  @Test
  @DisplayName(
      "la persona y el producto inexistentes se rechazan, y no se confunden con sin tarifa")
  void datosInexistentes() throws Exception {
    mvc.perform(efectiva(UUID.randomUUID(), producto, "2026-03-01"))
        .andExpect(status().isUnprocessableEntity());

    mvc.perform(efectiva(vendedora, UUID.randomUUID(), "2026-03-01"))
        .andExpect(status().isUnprocessableEntity());
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder efectiva(UUID persona, UUID producto, String fecha) {
    return get("/api/v1/commissions/effective")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read"))
        .param("userId", persona.toString())
        .param("productId", producto.toString())
        .param("onDate", fecha);
  }

  private UUID tarifa(UUID producto, UUID persona, String pct, String desde, String hasta) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO commission_rates (id, role_id, product_id, user_id, percentage, valid_from,"
            + " valid_to) VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid),"
            + " CAST(? AS uuid), CAST(? AS numeric), CAST(? AS date), CAST(? AS date))",
        id.toString(),
        VENDEDOR,
        producto == null ? null : producto.toString(),
        persona == null ? null : persona.toString(),
        pct,
        desde,
        hasta);
    return id;
  }

  private UUID persona(String usuario, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, username, email, first_name, last_name, password_hash, status)"
            + " VALUES (CAST(? AS uuid), ?, ?, 'Persona', 'De prueba', 'x', 'ACTIVO')",
        id.toString(),
        usuario,
        usuario + "@factech.co");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id) VALUES (CAST(? AS uuid), CAST(? AS uuid))",
        id.toString(),
        rol);
    return id;
  }

  private UUID producto(String codigo) {
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

  private void limpiar() {
    jdbc.update("DELETE FROM commission_rates");
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM user_roles WHERE user_id <> CAST(? AS uuid)", SUPERADMIN.toString());
    jdbc.update("DELETE FROM users WHERE id <> CAST(? AS uuid)", SUPERADMIN.toString());
  }
}
