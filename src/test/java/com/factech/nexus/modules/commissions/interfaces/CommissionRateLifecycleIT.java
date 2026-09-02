package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.DIRECTOR;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Corregir y retirar una tasa de rol (`RF-CM-003` y `RF-CM-004`).
 *
 * <p><b>La prueba que más importa de este archivo es la del retiro con asociaciones vivas.</b> Es
 * una condición que `cm.md` no declara y que se añadió al construir el módulo: sin ella, retirar
 * una tasa asociada haría que el producto <b>dejara de comisionar sin que nada lo dijera</b>,
 * porque la asociación sobreviviría apuntando a una fila que la resolución ya no mira.
 *
 * <p>Y la segunda: <b>corregir borra el pasado</b>. Sin vigencia no hay historial, de modo que el
 * registro de auditoría del cambio es hoy el único sitio donde queda escrito el porcentaje
 * anterior.
 */
@AutoConfigureMockMvc
class CommissionRateLifecycleIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID tasa;

  @BeforeEach
  void preparar() {
    limpiar();
    tasa = CommissionFixtures.sembrarTasaDeRol(jdbc, MANAGER, "10.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // Corregir
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("corrige el porcentaje y devuelve la tasa con el rol resuelto")
  void corrigeElPorcentaje() throws Exception {
    mvc.perform(correccion(tasa, "{\"percentage\":12.50}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.percentage").value(12.50))
        .andExpect(jsonPath("$.role.code").value("MANAGER"))
        .andExpect(jsonPath("$.associatedProducts").value(0));
  }

  @Test
  @DisplayName("corregir BORRA el porcentaje anterior, y solo la auditoría lo conserva")
  void corregirBorraElPasado() throws Exception {
    mvc.perform(correccion(tasa, "{\"percentage\":12.00}")).andExpect(status().isOk());

    // En la tabla ya no queda ni rastro del 10: no hay dos filas contando cada
    // una su parte, hay una que ahora dice otra cosa.
    assertThat(porcentajeEnBase()).isEqualByComparingTo("12.00");

    // De modo que ESTE registro es la única copia del valor previo que existe en
    // todo el sistema. Si dejara de escribirse, el 10 desaparecería.
    String cambio =
        jdbc.queryForObject(
            "SELECT CAST(changes AS text) FROM audit_change_log WHERE entity = 'commission_rates'"
                + " AND action = 'UPDATE' ORDER BY occurred_at DESC LIMIT 1",
            String.class);
    assertThat(cambio).contains("10.00").contains("12.00");
  }

  @Test
  @DisplayName("una corrección que no cambia nada no mueve `updated_at`")
  void correccionQueNoCambiaNada() throws Exception {
    var antes = actualizadaEn();
    mvc.perform(correccion(tasa, "{\"percentage\":10.00}")).andExpect(status().isOk());
    assertThat(actualizadaEn()).isEqualTo(antes);
  }

  @Test
  @DisplayName("el rol NO se corrige, y se rechaza en vez de ignorarse")
  void elRolNoSeCorrige() throws Exception {
    mvc.perform(correccion(tasa, "{\"roleId\":\"" + DIRECTOR + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-009"));

    // Ignorarlo haría creer que el cambio se aplicó, y la tasa habría arrastrado
    // sus asociaciones a un rol que nadie eligió.
    assertThat(rolEnBase()).isEqualTo(MANAGER);
  }

  @Test
  @DisplayName("vaciar el porcentaje se rechaza: una tasa sin porcentaje no significa nada")
  void elPorcentajeNoSeVacia() throws Exception {
    mvc.perform(correccion(tasa, "{\"percentage\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
  }

  @Test
  @DisplayName("una petición vacía se rechaza")
  void peticionVacia() throws Exception {
    mvc.perform(correccion(tasa, "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-010"));
  }

  @Test
  @DisplayName("una tasa retirada se trata como inexistente")
  void retiradaEsInexistente() throws Exception {
    retirar(tasa);
    mvc.perform(correccion(tasa, "{\"percentage\":12.00}")).andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------------------
  // Retirar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("retira con motivo, y la fila permanece")
  void retiraConMotivo() throws Exception {
    mvc.perform(retiro(tasa, "se declaró por error")).andExpect(status().isNoContent());

    assertThat(estaRetirada()).isTrue();
    // `RN-CM-005`: la fila permanece para que una liquidación pasada siga
    // resolviendo con qué porcentaje se pagó.
    assertThat(cuantasFilas()).isEqualTo(1);
  }

  @Test
  @DisplayName("UNA TASA ASOCIADA NO SE RETIRA: si no, el producto dejaría de pagar en silencio")
  void noSeRetiraLoQueRige() throws Exception {
    UUID producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_A");
    CommissionFixtures.asociar(jdbc, tasa, producto, MANAGER);

    mvc.perform(retiro(tasa, "ya no aplica"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"));

    // Sin esta condición la asociación seguiría ahí apuntando a una fila que la
    // resolución ya no mira, y el producto pasaría a no comisionar sin que nada
    // lo indicara. Es la silenciosidad de `RN-CM-012` por la puerta de atrás.
    assertThat(estaRetirada()).isFalse();
  }

  @Test
  @DisplayName("desasociada primero, la misma tasa sí se retira")
  void desasociarDesbloqueaElRetiro() throws Exception {
    UUID producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_A");
    CommissionFixtures.asociar(jdbc, tasa, producto, MANAGER);

    mvc.perform(
            post("/api/v1/commission-rates/" + tasa + "/products/" + producto + "/deletion")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"deja de comisionar\"}"))
        .andExpect(status().isNoContent());

    mvc.perform(retiro(tasa, "ya no se usa")).andExpect(status().isNoContent());
    assertThat(estaRetirada()).isTrue();
  }

  @Test
  @DisplayName("retirar sin motivo se rechaza antes de tocar nada")
  void motivoObligatorio() throws Exception {
    mvc.perform(retiro(tasa, "   "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-007"));

    assertThat(estaRetirada()).isFalse();
  }

  @Test
  @DisplayName("retirar dos veces da 409 y no 404: la tasa existe, y el retiro YA ocurrió")
  void noEsIdempotente() throws Exception {
    retirar(tasa);
    mvc.perform(retiro(tasa, "otra vez")).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("retirar lo inexistente da 404")
  void retirarLoInexistente() throws Exception {
    mvc.perform(retiro(UUID.randomUUID(), "un motivo")).andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder correccion(UUID id, String json) {
    return patch("/api/v1/commission-rates/" + id)
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  private MockHttpServletRequestBuilder retiro(UUID id, String motivo) {
    return post("/api/v1/commission-rates/" + id + "/deletion")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:delete"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"" + motivo + "\"}");
  }

  private void retirar(UUID id) throws Exception {
    mvc.perform(retiro(id, "motivo de la primera vez")).andExpect(status().isNoContent());
  }

  private java.math.BigDecimal porcentajeEnBase() {
    return jdbc.queryForObject(
        "SELECT percentage FROM commission_rates WHERE id = CAST(? AS uuid)",
        java.math.BigDecimal.class,
        tasa.toString());
  }

  private String rolEnBase() {
    return jdbc.queryForObject(
        "SELECT CAST(role_id AS text) FROM commission_rates WHERE id = CAST(? AS uuid)",
        String.class,
        tasa.toString());
  }

  private Object actualizadaEn() {
    return jdbc.queryForObject(
        "SELECT updated_at FROM commission_rates WHERE id = CAST(? AS uuid)",
        Object.class,
        tasa.toString());
  }

  private boolean estaRetirada() {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT deleted_at IS NOT NULL FROM commission_rates WHERE id = CAST(? AS uuid)",
            Boolean.class,
            tasa.toString()));
  }

  private long cuantasFilas() {
    return jdbc.queryForObject("SELECT count(*) FROM commission_rates", Long.class);
  }

  private void limpiar() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'CM'");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'CM'");
  }
}
