package com.factech.nexus.modules.products.interfaces;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * El alta de productos (`RF-PM-001` · `T-14`).
 *
 * <p>Cubre los criterios de `spec.md` §12. Lo que más importa aquí no es el camino feliz sino las
 * dos mitades de `RN-PM-002` y el estado inicial: un producto que naciera activo rompería el
 * reparto de `RN-PM-004`, que vive entero en `RF-PM-005`.
 */
@AutoConfigureMockMvc
class ProductsIT extends IntegrationTestBase {

  /** La moneda sembrada por `V15`, estable en todos los entornos. */
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private String oro;

  @BeforeEach
  void prepararCatalogo() {
    jdbc.update("DELETE FROM currencies WHERE is_default = false");
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM memberships");
    oro = crearMembresia("ORO", "Oro", 1);
  }

  @Test
  @DisplayName("`CA-PM-001` — registra un upgrade y devuelve el destino resuelto con su nivel")
  void altaDeUpgrade() throws Exception {
    mvc.perform(
            alta(
                """
                {"code":"UPGRADE_ORO","type":"UPGRADE_MEMBRESIA","name":"Ascenso a Oro",
                 "description":"Acceso al nivel oro.","targetMembershipId":"%s",
                 "price":49.99,"currencyId":"%s","validityDays":30}
                """
                    .formatted(oro, USD)))
        .andExpect(status().isCreated())
        .andExpect(
            header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/products/")))
        .andExpect(jsonPath("$.code").value("UPGRADE_ORO"))
        .andExpect(jsonPath("$.targetMembership.code").value("ORO"))
        .andExpect(jsonPath("$.targetMembership.level").value(1))
        .andExpect(jsonPath("$.currency.code").value("USD"))
        .andExpect(jsonPath("$.validityDays").value(30));
  }

  @Test
  @DisplayName(
      "`CA-PM-002` — registra un servicio sin membresía destino, que llega null y presente")
  void altaDeServicio() throws Exception {
    mvc.perform(
            alta(
                """
                {"code":"ASESORIA","type":"SERVICIO","name":"Asesoría",
                 "price":10.00,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        // Presente y nulo, no ausente: un campo que falta es indistinguible de
        // uno que el cliente no conoce.
        .andExpect(jsonPath("$.targetMembership").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.validityDays").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("`CA-PM-068` — el producto nace INACTIVO, y enviar `status` devuelve 400")
  void naceInactivoYNoSePuedeForzar() throws Exception {
    mvc.perform(
            alta(
                """
                {"code":"ASESORIA","type":"SERVICIO","name":"Asesoría","price":10.00,
                 "currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("INACTIVO"));

    // No se ignora en silencio: quien lo envía tiene que enterarse de que el
    // estado inicial no se decide desde fuera.
    mvc.perform(
            alta(
                """
                {"code":"OTRO","type":"SERVICIO","name":"Otro","price":10.00,
                 "currencyId":"%s","status":"ACTIVO"}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-PM-003` y `CA-PM-004` — la condición de `RN-PM-002`, en los dos sentidos")
  void condicionCruzadaEnLosDosSentidos() throws Exception {
    mvc.perform(
            alta(
                """
                {"code":"UPGRADE_ORO","type":"UPGRADE_MEMBRESIA","name":"Ascenso",
                 "price":49.99,"currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-007"));

    // La mitad que se olvida, y la peligrosa: no falla, promete.
    mvc.perform(
            alta(
                """
                {"code":"ASESORIA","type":"SERVICIO","name":"Asesoría","targetMembershipId":"%s",
                 "price":10.00,"currencyId":"%s"}
                """
                    .formatted(oro, USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-008"));

    assertThat(cuantosProductos()).isZero();
  }

  @Test
  @DisplayName("`CA-PM-005` — un precio de cero o negativo se rechaza")
  void precioNoPositivo() throws Exception {
    for (String precio : new String[] {"0", "0.00", "-1.50"}) {
      mvc.perform(
              alta(
                  """
                  {"code":"ASESORIA","type":"SERVICIO","name":"Asesoría","price":%s,
                   "currencyId":"%s"}
                  """
                      .formatted(precio, USD)))
          .andExpect(status().isBadRequest());
    }
    assertThat(cuantosProductos()).isZero();
  }

  @Test
  @DisplayName("`CA-PM-006` — el precio con más decimales de los que admite su moneda se rechaza")
  void decimalesSegunLaMoneda() throws Exception {
    // USD declara dos decimales: tres no caben, y no lo puede decir un CHECK
    // porque la escala vive en otra tabla.
    mvc.perform(
            alta(
                """
                {"code":"ASESORIA","type":"SERVICIO","name":"Asesoría","price":10.005,
                 "currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));

    mvc.perform(
            alta(
                """
                {"code":"ASESORIA","type":"SERVICIO","name":"Asesoría","price":10.00,
                 "currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        // Y sale con los decimales de su moneda, no con la escala de la columna.
        .andExpect(jsonPath("$.price").value(10.00));
  }

  @Test
  @DisplayName("`CA-PM-010` — un destino inexistente es 422 y no 404: es un dato, no el recurso")
  void destinoInexistente() throws Exception {
    mvc.perform(
            alta(
                """
                {"code":"UPGRADE_X","type":"UPGRADE_MEMBRESIA","name":"Ascenso",
                 "targetMembershipId":"%s","price":49.99,"currencyId":"%s"}
                """
                    .formatted(UUID.randomUUID(), USD)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
  }

  @Test
  @DisplayName("`CA-PM-007` — la moneda inexistente se distingue de la desactivada")
  void monedaInexistenteODesactivada() throws Exception {
    mvc.perform(
            alta(
                """
                {"code":"ASESORIA","type":"SERVICIO","name":"Asesoría","price":10.00,
                 "currencyId":"%s"}
                """
                    .formatted(UUID.randomUUID())))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no existe")));

    // La moneda POR DEFECTO no se puede desactivar: lo impide
    // `ck_currencies_default_active`, de `RF-SP-023`. Se siembra otra.
    String euro = crearMonedaInactiva();

    mvc.perform(
            alta(
                """
                {"code":"ASESORIA","type":"SERVICIO","name":"Asesoría","price":10.00,
                 "currencyId":"%s"}
                """
                    .formatted(euro)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("desactivada")));
  }

  @Test
  @DisplayName("`CA-PM-008` — el nombre que solo difiere en mayúsculas o acentos se rechaza")
  void nombreEquivalente() throws Exception {
    mvc.perform(
            alta(
                """
                {"code":"ASESORIA","type":"SERVICIO","name":"Asesoría","price":10.00,
                 "currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated());

    mvc.perform(
            alta(
                """
                {"code":"OTRO","type":"SERVICIO","name":"asesoria","price":10.00,
                 "currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  @DisplayName("`CA-PM-069` — el código duplicado se rechaza y se distingue del nombre")
  void codigoDuplicado() throws Exception {
    mvc.perform(
            alta(
                """
                {"code":"ASESORIA","type":"SERVICIO","name":"Asesoría","price":10.00,
                 "currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated());

    mvc.perform(
            alta(
                """
                {"code":"asesoria","type":"SERVICIO","name":"Otro nombre","price":10.00,
                 "currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].field").value("code"));
  }

  @Test
  @DisplayName("`CA-PM-092` y `CA-PM-093` — la vigencia es opcional y, si llega, mayor que cero")
  void vigencia() throws Exception {
    mvc.perform(
            alta(
                """
                {"code":"PERMANENTE","type":"SERVICIO","name":"Permanente","price":10.00,
                 "currencyId":"%s"}
                """
                    .formatted(USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.validityDays").value(org.hamcrest.Matchers.nullValue()));

    for (String vigencia : new String[] {"0", "-30"}) {
      mvc.perform(
              alta(
                  """
                  {"code":"OTRO","type":"SERVICIO","name":"Otro","price":10.00,
                   "currencyId":"%s","validityDays":%s}
                  """
                      .formatted(USD, vigencia)))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  @DisplayName(
      "`CA-PM-011` — el alta registra un evento de creación con el estado inicial completo")
  void auditoriaDelAlta() throws Exception {
    UUID correlacion = UUID.randomUUID();

    mvc.perform(
            post("/api/v1/products")
                .with(admin())
                .header("X-Correlation-Id", correlacion.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"UPGRADE_ORO","type":"UPGRADE_MEMBRESIA","name":"Ascenso a Oro",
                     "targetMembershipId":"%s","price":49.99,"currencyId":"%s","validityDays":30}
                    """
                        .formatted(oro, USD)))
        .andExpect(status().isCreated());

    String cambios =
        jdbc.queryForObject(
            """
            SELECT changes::text FROM audit_change_log
             WHERE correlation_id = ? AND module = 'PM' AND action = 'CREATE'
            """,
            String.class,
            correlacion);

    assertThat(cambios)
        .contains("UPGRADE_ORO")
        .contains("49.99")
        .contains("INACTIVO")
        .contains("validity_days");
  }

  @Test
  @DisplayName("el alta NO emite evento de seguridad: un producto no concede privilegios")
  void sinEventoDeSeguridad() throws Exception {
    UUID correlacion = UUID.randomUUID();

    mvc.perform(
            post("/api/v1/products")
                .with(admin())
                .header("X-Correlation-Id", correlacion.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"ASESORIA","type":"SERVICIO","name":"Asesoría","price":10.00,
                     "currencyId":"%s"}
                    """
                        .formatted(USD)))
        .andExpect(status().isCreated());

    Integer eventos =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_security_log WHERE correlation_id = ?",
            Integer.class,
            correlacion);

    assertThat(eventos).isZero();
  }

  @Test
  @DisplayName("`CA-PM-012` — sin `products:create` responde 403 y no registra nada")
  void sinPermiso() throws Exception {
    mvc.perform(
            post("/api/v1/products")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "products:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"ASESORIA","type":"SERVICIO","name":"Asesoría","price":10.00,
                     "currencyId":"%s"}
                    """
                        .formatted(USD)))
        .andExpect(status().isForbidden());

    assertThat(cuantosProductos()).isZero();
  }

  // ---------------------------------------------------------------------------

  private RequestPostProcessor admin() {
    return user(UUID.randomUUID().toString()).authorities(() -> "products:create");
  }

  private MockHttpServletRequestBuilder alta(String cuerpo) {
    return post("/api/v1/products")
        .with(admin())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private String crearMembresia(String codigo, String nombre, int nivel) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, parent_membership_id, level, color)"
            + " VALUES (?, ?, ?, NULL, ?, upper(lpad(to_hex(? * 4919), 6, '0')))",
        id,
        codigo,
        nombre,
        nivel,
        nivel);
    return id.toString();
  }

  /** Una moneda inactiva y NO por defecto: la de por defecto no se puede desactivar. */
  private String crearMonedaInactiva() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)"
            + " VALUES (?, 'EUR', 'Euro', 'E', 2, false, false)",
        id);
    return id.toString();
  }

  private int cuantosProductos() {
    Integer filas = jdbc.queryForObject("SELECT count(*) FROM products", Integer.class);
    return filas == null ? 0 : filas;
  }

  /**
   * Deja `products` vacía al terminar CADA prueba.
   *
   * <p><b>No es higiene: es lo que impide romper a otras clases.</b> Un producto que sobreviva a
   * esta clase mantiene una clave foránea sobre `memberships`, y varias pruebas de `SP` empiezan
   * con `DELETE FROM memberships WHERE level > 0`. Ese borrado falla con violación de integridad, y
   * el fallo aparece <b>lejos de aquí</b> —en la clase que borra— y solo cuando el orden de
   * ejecución las pone en ese orden, que es la peor forma de romper una suite.
   */
  @AfterEach
  void vaciarCatalogo() {
    jdbc.update("DELETE FROM products");
  }
}
