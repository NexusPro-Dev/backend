package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.DIRECTOR;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Asociar y desasociar (`RF-CM-007` y `RF-CM-008`).
 *
 * <p><b>Es la suite del requerimiento que sostiene el módulo entero.</b> Sin asociación el catálogo
 * no paga nada a nadie, y con dos asociaciones del mismo rol sobre el mismo producto la resolución
 * dejaría de ser determinista — la base elegiría.
 *
 * <p>Lo que aquí se prueba y no se ve en ningún otro sitio es que <b>la clave primaria es la
 * regla</b>: `RN-CM-013` no la comprueba ningún caso de uso.
 */
@AutoConfigureMockMvc
class ProductAssociationIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID producto;
  private UUID tasaManager;

  @BeforeEach
  void preparar() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
    producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_A");
    tasaManager = CommissionFixtures.sembrarTasaDeRol(jdbc, MANAGER, "10.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
  }

  @Test
  @DisplayName("asociar pone la tasa en vigor y devuelve TODAS sus asociaciones")
  void asociar() throws Exception {
    mvc.perform(asociacion(tasaManager, producto))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].product.code").value("BOT_A"))
        .andExpect(jsonPath("$.content[0].role.code").value("MANAGER"))
        // El porcentaje viaja resuelto aunque sea de la tasa y no de la
        // asociación: es lo que hace legible «qué paga este producto».
        .andExpect(jsonPath("$.content[0].percentage").value(10.00));

    assertThat(rolCopiado()).isEqualTo(MANAGER);
  }

  @Test
  @DisplayName("el rol NO se recibe: mandarlo se rechaza, no se ignora")
  void elRolNoSeRecibe() throws Exception {
    // Se manda un `roleId` distinto a propósito. La petición no lo declara, y el
    // deserializador rechaza lo desconocido en vez de descartarlo en silencio —
    // que es lo que haría creer a quien lo envió que el rol se había aplicado.
    mvc.perform(
            post("/api/v1/commission-rates/" + tasaManager + "/products")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + producto + "\",\"roleId\":\"" + DIRECTOR + "\"}"))
        .andExpect(status().isBadRequest());

    assertThat(cuantasAsociaciones()).isZero();

    // Y por la vía buena, el rol que queda es el DE LA TASA. Que no pueda ser
    // otro no lo sostiene esta comprobación sino la clave foránea compuesta.
    mvc.perform(asociacion(tasaManager, producto)).andExpect(status().isCreated());
    assertThat(rolCopiado()).isEqualTo(MANAGER);
  }

  @Test
  @DisplayName("`RN-CM-013` — el mismo rol dos veces sobre el mismo producto se rechaza")
  void unPorcentajePorRolYProducto() throws Exception {
    UUID otra = CommissionFixtures.sembrarTasaDeRol(jdbc, MANAGER, "15.00");

    mvc.perform(asociacion(tasaManager, producto)).andExpect(status().isCreated());

    // Es OTRA tasa, del MISMO rol, sobre el MISMO producto. Si entrara, la
    // resolución tendría dos respuestas válidas y elegiría el plan de ejecución.
    mvc.perform(asociacion(otra, producto))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));

    assertThat(cuantasAsociaciones()).isEqualTo(1);
  }

  @Test
  @DisplayName("dos ROLES distintos sobre el mismo producto sí conviven: es el override")
  void variosRolesPorProducto() throws Exception {
    UUID tasaDirector = CommissionFixtures.sembrarTasaDeRol(jdbc, DIRECTOR, "4.00");

    mvc.perform(asociacion(tasaManager, producto)).andExpect(status().isCreated());
    mvc.perform(asociacion(tasaDirector, producto)).andExpect(status().isCreated());

    mvc.perform(
            get("/api/v1/product-commission-rates")
                .param("productId", producto.toString())
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2));
  }

  @Test
  @DisplayName("una MISMA tasa rige sobre varios productos sin duplicarse")
  void unaTasaSobreVariosProductos() throws Exception {
    UUID segundo = CommissionFixtures.sembrarProducto(jdbc, "BOT_B");

    mvc.perform(asociacion(tasaManager, producto)).andExpect(status().isCreated());
    mvc.perform(asociacion(tasaManager, segundo))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.content.length()").value(2));

    // Ese es el motivo de que el producto viva en una tabla aparte: como columna
    // habría obligado a duplicar la tasa y a corregir dos filas al cambiarla.
    assertThat(cuantasTasas()).isEqualTo(1);
  }

  @Test
  @DisplayName("`RN-CM-010` — no se asocia a un producto retirado")
  void productoRetirado() throws Exception {
    UUID retirado = CommissionFixtures.sembrarProducto(jdbc, "BOT_Z", true);

    // 409 y no 400: el dato es correcto y existe — lo que falla es una regla de
    // negocio sobre el estado en que está. Es la misma traducción que `PM` usa
    // para el producto retirado.
    mvc.perform(asociacion(tasaManager, retirado))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    assertThat(cuantasAsociaciones()).isZero();
  }

  @Test
  @DisplayName("el producto inexistente se distingue del retirado: 422 y no 400")
  void productoInexistente() throws Exception {
    mvc.perform(asociacion(tasaManager, UUID.randomUUID()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
  }

  @Test
  @DisplayName("una tasa retirada no se asocia: poner en vigor lo retirado es lo contrario")
  void tasaRetirada() throws Exception {
    jdbc.update(
        "UPDATE commission_rates SET deleted_at = now() WHERE id = CAST(? AS uuid)",
        tasaManager.toString());

    mvc.perform(asociacion(tasaManager, producto)).andExpect(status().isNotFound());
    assertThat(cuantasAsociaciones()).isZero();
  }

  @Test
  @DisplayName("desasociar borra la fila y deja la tasa viva en el catálogo")
  void desasociar() throws Exception {
    mvc.perform(asociacion(tasaManager, producto)).andExpect(status().isCreated());

    mvc.perform(desasociacion(tasaManager, producto, "el producto pasa a no comisionar"))
        .andExpect(status().isNoContent());

    assertThat(cuantasAsociaciones()).isZero();
    // La tasa NO se toca: sigue disponible para otros productos. Es la
    // diferencia entre dejar de pagar y destruir la configuración.
    assertThat(cuantasTasas()).isEqualTo(1);
  }

  @Test
  @DisplayName("desasociar deja registro de eliminación de tipo ASSOCIATION con su motivo")
  void desasociarDejaRegistro() throws Exception {
    mvc.perform(asociacion(tasaManager, producto)).andExpect(status().isCreated());
    mvc.perform(desasociacion(tasaManager, producto, "se renegoció el porcentaje"))
        .andExpect(status().isNoContent());

    // Como la fila desaparece, este registro es LO ÚNICO que queda de que esa
    // tasa rigió sobre ese producto.
    var registro =
        jdbc.queryForMap(
            "SELECT deletion_type, reason FROM audit_deletion_log"
                + " WHERE entity = 'product_commission_rates' ORDER BY occurred_at DESC LIMIT 1");

    assertThat(registro.get("deletion_type")).isEqualTo("ASSOCIATION");
    // `ck_deletion_reason` EXIME de motivo a las de tipo ASSOCIATION, y este
    // caso de uso lo exige igualmente: aquí no se pierde un vínculo entre dos
    // filas que siguen contándolo todo — se pierde la única constancia de que
    // ese producto pagaba a ese rol.
    assertThat(registro.get("reason")).isEqualTo("se renegoció el porcentaje");
  }

  @Test
  @DisplayName("desasociar sin motivo se rechaza antes de tocar nada")
  void motivoObligatorio() throws Exception {
    mvc.perform(asociacion(tasaManager, producto)).andExpect(status().isCreated());

    mvc.perform(desasociacion(tasaManager, producto, "   "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-007"));

    assertThat(cuantasAsociaciones()).isEqualTo(1);
  }

  @Test
  @DisplayName("desasociar lo que no está asociado da 404 y no 409: no queda rastro que consultar")
  void desasociarLoInexistente() throws Exception {
    mvc.perform(desasociacion(tasaManager, producto, "un motivo")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("la lectura desde la tasa la devuelve vacía cuando no rige sobre nada")
  void sinAsociacionesLaListaEsVacia() throws Exception {
    mvc.perform(
            get("/api/v1/commission-rates/" + tasaManager + "/products")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read")))
        .andExpect(status().isOk())
        // Y esa lista vacía significa que la tasa NO PAGA NADA A NADIE.
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  @DisplayName("asociar exige commissions:update")
  void exigeElPermiso() throws Exception {
    mvc.perform(
            post("/api/v1/commission-rates/" + tasaManager + "/products")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + producto + "\"}"))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder asociacion(UUID tasa, UUID producto) {
    return post("/api/v1/commission-rates/" + tasa + "/products")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"productId\":\"" + producto + "\"}");
  }

  private MockHttpServletRequestBuilder desasociacion(UUID tasa, UUID producto, String motivo) {
    return post("/api/v1/commission-rates/" + tasa + "/products/" + producto + "/deletion")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"" + motivo + "\"}");
  }

  private String rolCopiado() {
    return jdbc.queryForObject(
        "SELECT CAST(role_id AS text) FROM product_commission_rates LIMIT 1", String.class);
  }

  private long cuantasAsociaciones() {
    return jdbc.queryForObject("SELECT count(*) FROM product_commission_rates", Long.class);
  }

  private long cuantasTasas() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM commission_rates WHERE deleted_at IS NULL", Long.class);
  }
}
