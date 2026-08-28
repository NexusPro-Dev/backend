package com.factech.nexus.modules.products.interfaces;

import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
 * `RF-PM-006` · `T-10` y `T-11` — el retiro bajo concurrencia.
 *
 * <p><b>Lo que se cuenta no son los errores, son los REGISTROS.</b> Dos retiros simultáneos que
 * devolvieran `204` los dos dejarían dos filas en `audit_deletion_log` con dos motivos para un
 * único hecho, que es evidencia contradictoria: dentro de un año nadie sabría por cuál de los dos
 * se retiró.
 */
@AutoConfigureMockMvc
class ProductDeletionConcurrencyIT extends IntegrationTestBase {

  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void limpiar() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'PM'");
  }

  @AfterEach
  void vaciar() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'PM'");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'PM'");
  }

  @Test
  @DisplayName("`T-10` — dos retiros simultáneos dejan UN SOLO registro de eliminación")
  void dosRetirosSimultaneos() {
    UUID bot = bot("SOPORTE", "Soporte prioritario");

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(retirar(bot, "Motivo número " + indice + ".")));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    // LO QUE DECIDE LA PRUEBA. Los dos `204` darían el mismo recuento de éxitos
    // que uno solo si únicamente se mirara el estado HTTP.
    assertThat(cuantosRegistros(bot))
        .as("dos motivos sobre un solo hecho es evidencia contradictoria")
        .isEqualTo(1);

    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 204).count())
        .as("exactamente un retiro debía prosperar")
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .as("y el otro debía rechazarse como «ya retirado»")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("`T-11` — retirar y dar de alta otro con el MISMO nombre a la vez")
  void retirarYRegistrarElMismoNombre() {
    UUID vivo = bot("SOPORTE", "Soporte prioritario");

    List<Callable<Integer>> carrera =
        List.of(
            () -> estadoDe(retirar(vivo, "Se descontinuó.")),
            () -> estadoDe(alta("SOPORTE_2", "Soporte prioritario")));

    List<Outcome<Integer>> resultados = runTogether(carrera);

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    // El nombre se libera AL RETIRAR, de modo que los dos desenlaces valen: si
    // el alta llega antes, choca con `uq_products_name` y recibe `409`; si
    // llega después, entra. Lo que NO puede quedar es DOS VIVOS con el mismo
    // nombre — y eso lo impide el índice, no el orden.
    assertThat(cuantosVivosLlamados("Soporte prioritario"))
        .as("uno o ninguno vivo con ese nombre, nunca dos")
        .isBetween(0, 1);
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder retirar(UUID id, String motivo) {
    return post("/api/v1/products/{id}/deletion", id)
        .with(user(UUID.randomUUID().toString()).authorities(() -> "products:delete"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"" + motivo + "\"}");
  }

  private MockHttpServletRequestBuilder alta(String codigo, String nombre) {
    return post("/api/v1/products")
        .with(user(UUID.randomUUID().toString()).authorities(() -> "products:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"code":"%s","type":"BOT","name":"%s","price":10.00,"currencyId":"%s"}
            """
                .formatted(codigo, nombre, USD));
  }

  private int estadoDe(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }

  private int cuantosRegistros(UUID id) {
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_deletion_log WHERE entity_id = CAST(? AS uuid)",
            Integer.class,
            id.toString());
    return filas == null ? 0 : filas;
  }

  private int cuantosVivosLlamados(String nombre) {
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM products WHERE deleted_at IS NULL"
                + " AND f_unaccent(lower(name)) = f_unaccent(lower(CAST(? AS text)))",
            Integer.class,
            nombre);
    return filas == null ? 0 : filas;
  }

  private UUID bot(String codigo, String nombre) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (id, code, type, name, description, target_membership_id, price,"
            + " currency_id, validity_days, status, created_at, updated_at)"
            + " VALUES (CAST(? AS uuid), ?, 'BOT', ?, 'Atención prioritaria.', NULL, 10.00,"
            + " CAST(? AS uuid), NULL, 'INACTIVO', ?, ?)",
        id.toString(),
        codigo,
        nombre,
        USD,
        BASE,
        BASE);
    return id;
  }
}
