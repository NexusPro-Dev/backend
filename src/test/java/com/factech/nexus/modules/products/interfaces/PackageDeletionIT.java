package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
 * Retirar un paquete (`RF-PM-022`, `CA-PM-298` a `CA-PM-303`).
 *
 * <p>La que define el requerimiento es <b>`CA-PM-301`</b>: la fila {@code LOGICAL} con el motivo,
 * el actor y la instantánea <b>con los productos y sus descuentos</b>.
 */
@AutoConfigureMockMvc
class PackageDeletionIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;
  private UUID paquete;
  private UUID botA;

  @BeforeEach
  void prepararCatalogo() {
    PackageTestSupport.limpiarCatalogoYSembrarMembresias(jdbc);
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'PM'");
    paquete = PackageTestSupport.paquete(jdbc, "COMBO", "Dos bots.", "ACTIVO", "AMBOS");
    botA = PackageTestSupport.bot(jdbc, "BOT_A", "10.00");
    UUID botB = PackageTestSupport.bot(jdbc, "BOT_B", "20.00");
    PackageTestSupport.asociar(jdbc, paquete, botA, "FIJO", "1.00");
    PackageTestSupport.asociar(jdbc, paquete, botB, "PORCENTAJE", "10");
    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
  }

  @AfterEach
  void vaciar() {
    PackageTestSupport.limpiarPaquetes(jdbc);
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'PM'");
  }

  @Test
  @DisplayName(
      "`CA-PM-298` y `CA-PM-301` — 204; la fila conserva status y sus asociaciones; LOGICAL con motivo, actor e instantánea con los productos")
  void retiraYAudita() throws Exception {
    UUID actor = UUID.randomUUID();
    mvc.perform(retirar(paquete, "{\"reason\":\"Se descontinuó.\"}", actor))
        .andExpect(status().isNoContent());

    var fila =
        jdbc.queryForMap("SELECT status, deleted_at FROM product_packages WHERE id = ?", paquete);
    assertThat(fila.get("status")).isEqualTo("ACTIVO");
    assertThat(fila.get("deleted_at")).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM product_package_items WHERE package_id = ?",
                Integer.class,
                paquete))
        .isEqualTo(2);

    var registro =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, deletion_type, reason, snapshot::text AS snapshot"
                + " FROM audit_deletion_log WHERE module = 'PM' AND entity = 'product_packages' AND entity_id = ?",
            paquete);
    assertThat(registro.get("actor")).isEqualTo(actor.toString());
    assertThat(registro.get("deletion_type")).isEqualTo("LOGICAL");
    assertThat(registro.get("reason")).isEqualTo("Se descontinuó.");
    assertThat((String) registro.get("snapshot"))
        .contains("\"code\": \"COMBO\"")
        .contains("\"status\": \"ACTIVO\"")
        .contains("\"product_id\": \"" + botA + "\"")
        .contains("\"discount_type\": \"PORCENTAJE\"")
        .doesNotContain("price\"");
  }

  @Test
  @DisplayName("`CA-PM-299` — motivo ausente, vacío o de espacios es 400 SIN consultar nada")
  void motivoAntesQueNada() throws Exception {
    for (String cuerpo : new String[] {"{}", "{\"reason\":\"\"}", "{\"reason\":\"   \"}"}) {
      estadisticas.clear();
      mvc.perform(retirar(paquete, cuerpo, UUID.randomUUID()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
      assertThat(estadisticas.getPrepareStatementCount()).isZero();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at FROM product_packages WHERE id = ?", Object.class, paquete))
        .isNull();
  }

  @Test
  @DisplayName("`CA-PM-300` — el inexistente es 404 y el YA retirado es 409, distinguidos")
  void inexistenteYYaRetirado() throws Exception {
    mvc.perform(retirar(UUID.randomUUID(), "{\"reason\":\"x\"}", UUID.randomUUID()))
        .andExpect(status().isNotFound());
    mvc.perform(retirar(paquete, "{\"reason\":\"Primera.\"}", UUID.randomUUID()))
        .andExpect(status().isNoContent());
    mvc.perform(retirar(paquete, "{\"reason\":\"Segunda.\"}", UUID.randomUUID()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
  }

  @Test
  @DisplayName(
      "`CA-PM-302` — el retirado sigue en el catálogo con includeDeleted y su detalle trae el motivo")
  void sigueEnElCatalogo() throws Exception {
    mvc.perform(retirar(paquete, "{\"reason\":\"Se descontinuó.\"}", UUID.randomUUID()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/packages").with(lector()))
        .andExpect(jsonPath("$.content", hasSize(0)));
    mvc.perform(get("/api/v1/packages?includeDeleted=true").with(lector()))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].offerable").value(false));
    mvc.perform(get("/api/v1/packages/" + paquete).with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletionReason").value("Se descontinuó."))
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.offerable").value(false));
  }

  @Test
  @DisplayName(
      "`CA-PM-303` — sus productos siguen activos y en los demás paquetes, y el código retirado NO se reutiliza")
  void losProductosNoCambianYElCodigoNoSeLibera() throws Exception {
    UUID otro = PackageTestSupport.paquete(jdbc, "OTRO", "Otro.", "INACTIVO", "TIENDA");
    PackageTestSupport.asociar(jdbc, otro, botA, "FIJO", "0");
    mvc.perform(retirar(paquete, "{\"reason\":\"Se descontinuó.\"}", UUID.randomUUID()))
        .andExpect(status().isNoContent());

    assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, botA))
        .isEqualTo("ACTIVO");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM product_package_items WHERE package_id = ?",
                Integer.class,
                otro))
        .isEqualTo(1);

    mvc.perform(
            post("/api/v1/packages")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "packages:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(PackagesIT.cuerpo("COMBO", "Combo nuevo", PackageTestSupport.USD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));
  }

  @Test
  @DisplayName("sin `packages:delete` responde 403, también con `products:delete`")
  void sinPermiso() throws Exception {
    mvc.perform(
            post("/api/v1/packages/" + paquete + "/deletion")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "products:delete"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"x\"}"))
        .andExpect(status().isForbidden());
  }

  private MockHttpServletRequestBuilder retirar(UUID id, String cuerpo, UUID actor) {
    return post("/api/v1/packages/" + id + "/deletion")
        .with(user(actor.toString()).authorities(() -> "packages:delete"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor lector() {
    return user(UUID.randomUUID().toString()).authorities(() -> "packages:read");
  }
}
