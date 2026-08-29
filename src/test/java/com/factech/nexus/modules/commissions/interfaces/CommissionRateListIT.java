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
 * El listado de tarifas (`RF-CM-002` · `T-08`).
 *
 * <p><b>Este listado devuelve lo declarado y NO resuelve precedencia</b>, y esa es la propiedad que
 * más importa aquí: filtrar por persona devuelve las declaradas <b>para</b> esa persona, no las que
 * <b>le aplican</b>. Lo segundo es `RF-CM-005`, y confundirlos haría que este endpoint empezara a
 * resolver precedencias por su cuenta.
 */
@AutoConfigureMockMvc
class CommissionRateListIT extends IntegrationTestBase {

  private static final String VENDEDOR = "01a02a33-4c00-7005-9c4f-5e7ad1000005";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID vendedora;
  private UUID producto;

  @BeforeEach
  void preparar() {
    limpiar();
    vendedora = persona("vendedora");
    producto = producto("BOT_UNO");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "`CA-CM-014` y `CA-CM-016` — ordena por vigencia descendente, con los ausentes nulos y PRESENTES")
  void ordenYCamposPresentes() throws Exception {
    tarifa(null, null, "10.00", "2026-01-01", "2026-06-30", false);
    tarifa(null, null, "12.00", "2026-07-01", null, false);

    mvc.perform(listado())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        // La más reciente por inicio de vigencia encabeza.
        .andExpect(jsonPath("$.content[0].percentage").value(12.00))
        .andExpect(jsonPath("$.content[0].scope").value("ROL"))
        .andExpect(jsonPath("$.content[0].product").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.content[0].user").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.sort").value("validFrom,desc"));
  }

  @Test
  @DisplayName("`CA-CM-015` — filtra por rol, producto y persona")
  void filtros() throws Exception {
    tarifa(null, null, "10.00", "2026-01-01", null, false);
    tarifa(producto, null, "20.00", "2026-01-01", null, false);
    tarifa(null, vendedora, "30.00", "2026-01-01", null, false);

    mvc.perform(listado().param("productId", producto.toString()))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].scope").value("PRODUCTO"));

    mvc.perform(listado().param("userId", vendedora.toString()))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].scope").value("PERSONA"));

    mvc.perform(listado().param("roleId", VENDEDOR))
        .andExpect(jsonPath("$.totalElements").value(3));
  }

  @Test
  @DisplayName(
      "`T-10` — filtrar por persona devuelve las declaradas PARA ella, no las que le aplican")
  void elListadoNoResuelvePrecedencia() throws Exception {
    // La del rol le APLICA a la vendedora, pero no está declarada para ella.
    tarifa(null, null, "10.00", "2026-01-01", null, false);
    tarifa(null, vendedora, "30.00", "2026-01-01", null, false);

    mvc.perform(listado().param("userId", vendedora.toString()))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].percentage").value(30.00));
  }

  @Test
  @DisplayName(
      "`CA-CM-017` y `CA-CM-018` — sin fecha vienen las vencidas; con fecha, solo las que rigen")
  void elHistorialYLaFecha() throws Exception {
    tarifa(null, null, "10.00", "2026-01-01", "2026-06-30", false);
    tarifa(null, null, "12.00", "2026-07-01", null, false);

    // Sin filtro de fecha, el historial completo: es la mitad del valor de tener
    // vigencia.
    mvc.perform(listado()).andExpect(jsonPath("$.totalElements").value(2));

    mvc.perform(listado().param("onDate", "2026-03-01"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].percentage").value(10.00));

    // Y una fecha futura devuelve la programada, que es la forma de comprobar un
    // cambio antes de que entre en vigor.
    mvc.perform(listado().param("onDate", "2027-01-01"))
        .andExpect(jsonPath("$.content[0].percentage").value(12.00));
  }

  @Test
  @DisplayName(
      "`CA-CM-019` — las retiradas se excluyen por omisión y se incluyen marcadas si se piden")
  void lasRetiradas() throws Exception {
    tarifa(null, null, "10.00", "2026-01-01", null, true);

    mvc.perform(listado()).andExpect(jsonPath("$.totalElements").value(0));

    mvc.perform(listado().param("includeDeleted", "true"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].deletedAt").value(org.hamcrest.Matchers.notNullValue()));
  }

  @Test
  @DisplayName("`CA-CM-020` — un filtro sin resultados devuelve la colección vacía, no un error")
  void sinResultadosNoEsError() throws Exception {
    mvc.perform(listado().param("roleId", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("`CA-CM-022` — el historial de un caso concreto, filtrando por los tres sin fecha")
  void historialDeUnCaso() throws Exception {
    tarifa(producto, vendedora, "10.00", "2026-01-01", "2026-06-30", false);
    tarifa(producto, vendedora, "15.00", "2026-07-01", null, false);
    tarifa(null, null, "5.00", "2026-01-01", null, false);

    mvc.perform(
            listado()
                .param("roleId", VENDEDOR)
                .param("productId", producto.toString())
                .param("userId", vendedora.toString()))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].percentage").value(15.00))
        .andExpect(jsonPath("$.content[1].percentage").value(10.00));
  }

  @Test
  @DisplayName("sin el permiso de lectura, 403")
  void sinPermiso() throws Exception {
    mvc.perform(
            get("/api/v1/commission-rates")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:create")))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder listado() {
    return get("/api/v1/commission-rates")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read"));
  }

  private void tarifa(
      UUID producto, UUID persona, String pct, String desde, String hasta, boolean retirada) {
    jdbc.update(
        "INSERT INTO commission_rates (id, role_id, product_id, user_id, percentage, valid_from,"
            + " valid_to, deleted_at) VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid),"
            + " CAST(? AS uuid), CAST(? AS numeric), CAST(? AS date), CAST(? AS date), "
            + (retirada ? "now()" : "NULL")
            + ")",
        UUID.randomUUID().toString(),
        VENDEDOR,
        producto == null ? null : producto.toString(),
        persona == null ? null : persona.toString(),
        pct,
        desde,
        hasta);
  }

  private UUID persona(String usuario) {
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
        VENDEDOR);
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
