package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.products.interfaces.PackageTestSupport.Membresias;
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
 * Quitar un producto de un paquete (`RF-PM-025`, `CA-PM-321` a `CA-PM-326`).
 *
 * <p>La que define el requerimiento es <b>`CA-PM-322`</b>: la fila {@code ASSOCIATION} sin motivo,
 * con el descuento y el precio del producto en la instantánea.
 */
@AutoConfigureMockMvc
class PackageDissociationIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private Membresias membresias;
  private UUID paquete;
  private UUID botA;
  private UUID botB;

  @BeforeEach
  void prepararCatalogo() {
    membresias = PackageTestSupport.limpiarCatalogoYSembrarMembresias(jdbc);
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'PM'");
    paquete = PackageTestSupport.paquete(jdbc, "COMBO", "Dos bots.", "ACTIVO", "AMBOS");
    botA = PackageTestSupport.bot(jdbc, "BOT_A", "100.00");
    botB = PackageTestSupport.bot(jdbc, "BOT_B", "50.00");
    PackageTestSupport.asociar(jdbc, paquete, botA, "FIJO", "20.00");
    PackageTestSupport.asociar(jdbc, paquete, botB, "PORCENTAJE", "10");
  }

  @AfterEach
  void vaciar() {
    PackageTestSupport.limpiarPaquetes(jdbc);
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'PM'");
  }

  @Test
  @DisplayName(
      "`CA-PM-321`, `CA-PM-322` y `CA-PM-324` — 200 con el paquete rehecho, la fila desaparece, ASSOCIATION sin motivo, y con uno queda offerable false")
  void desasociaYAudita() throws Exception {
    UUID actor = UUID.randomUUID();
    mvc.perform(
            delete("/api/v1/packages/" + paquete + "/products/" + botA)
                .with(user(actor.toString()).authorities(() -> "packages:update")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].product.code").value("BOT_B"))
        .andExpect(jsonPath("$.price").value(45.00))
        .andExpect(jsonPath("$.listPrice").value(50.00))
        .andExpect(jsonPath("$.savings").value(5.00))
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(jsonPath("$.offerableReason").value(containsString("menos de dos")));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM product_package_items WHERE product_id = ?",
                Integer.class,
                botA))
        .isZero();

    var fila =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, deletion_type, reason, snapshot::text AS snapshot"
                + " FROM audit_deletion_log WHERE module = 'PM' AND entity = 'product_package_items'"
                + " AND entity_id = ?",
            paquete);
    assertThat(fila.get("actor")).isEqualTo(actor.toString());
    assertThat(fila.get("deletion_type")).isEqualTo("ASSOCIATION");
    assertThat(fila.get("reason")).isNull();
    assertThat((String) fila.get("snapshot"))
        .contains("\"product_id\": \"" + botA + "\"")
        .contains("\"discount_type\": \"FIJO\"")
        .contains("\"discount_value\": \"20.0000\"")
        .contains("\"product_price\": \"100.0000\"");
  }

  @Test
  @DisplayName("`CA-PM-323` — 404 al que no está, al que YA se quitó, y al paquete retirado")
  void noEsta() throws Exception {
    mvc.perform(desasociar(paquete, botA)).andExpect(status().isOk());
    mvc.perform(desasociar(paquete, botA))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Ese producto no está en el paquete."));
    mvc.perform(desasociar(paquete, UUID.randomUUID())).andExpect(status().isNotFound());
    jdbc.update("UPDATE product_packages SET deleted_at = now() WHERE id = ?", paquete);
    mvc.perform(desasociar(paquete, botB)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("`CA-PM-325` — quitado el único upgrade, entra otro de OTRO origen")
  void elOrigenQuedaLibre() throws Exception {
    UUID desdeBeca =
        PackageTestSupport.upgrade(
            jdbc, "UP_BECA", "100.00", membresias.beca(), membresias.platino());
    UUID desdePlatino =
        PackageTestSupport.upgrade(
            jdbc, "UP_PLATINO", "200.00", membresias.platino(), membresias.oro());
    mvc.perform(asociar(paquete, desdeBeca)).andExpect(status().isCreated());
    mvc.perform(asociar(paquete, desdePlatino))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-007"));
    mvc.perform(desasociar(paquete, desdeBeca)).andExpect(status().isOk());
    mvc.perform(asociar(paquete, desdePlatino)).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("`CA-PM-326` — el producto sigue activo en el catálogo y en los demás paquetes")
  void elProductoNoCambia() throws Exception {
    UUID otroPaquete = PackageTestSupport.paquete(jdbc, "OTRO", "Otro.", "INACTIVO", "TIENDA");
    PackageTestSupport.asociar(jdbc, otroPaquete, botA, "FIJO", "1.00");
    mvc.perform(desasociar(paquete, botA)).andExpect(status().isOk());
    assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, botA))
        .isEqualTo("ACTIVO");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM product_package_items WHERE product_id = ?",
                Integer.class,
                botA))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("sin `packages:update` responde 403")
  void sinPermiso() throws Exception {
    mvc.perform(
            delete("/api/v1/packages/" + paquete + "/products/" + botA)
                .with(user(UUID.randomUUID().toString()).authorities(() -> "products:update")))
        .andExpect(status().isForbidden());
  }

  private MockHttpServletRequestBuilder desasociar(UUID paquete, UUID producto) {
    return delete("/api/v1/packages/" + paquete + "/products/" + producto)
        .with(user(UUID.randomUUID().toString()).authorities(() -> "packages:update"));
  }

  private MockHttpServletRequestBuilder asociar(UUID paquete, UUID producto) {
    return post("/api/v1/packages/" + paquete + "/products")
        .with(user(UUID.randomUUID().toString()).authorities(() -> "packages:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(PackageProductsIT.json(producto, "FIJO", "0"));
  }
}
