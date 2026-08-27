package com.factech.nexus.modules.products.interfaces;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hamcrest.Matchers;
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
 * El detalle de un producto (`RF-PM-003` · `T-07` a `T-09` y `T-12`).
 *
 * <p>Cubre los criterios de `spec.md` §12. Dos merecen leerse antes que el camino feliz: el
 * producto retirado <b>se devuelve</b> en lugar de responder que no existe (`CA-PM-026`), y la
 * respuesta <b>no lleva autoría en ninguna forma</b> (`CA-PM-081`) — ni siquiera resuelta desde la
 * auditoría, que sí la sabe.
 *
 * <p><b>El retiro se simula sembrando las dos mitades</b>: la marca en {@code products} y el
 * registro en {@code audit_deletion_log}. Es lo que `RF-PM-006` hará en una transacción, y este
 * requerimiento no puede esperarlo porque la dependencia entre los dos va al revés del orden en que
 * están numerados. Cuando `RF-PM-006` exista, el recorrido de extremo a extremo se hace por su
 * endpoint y esta siembra sobra.
 */
@AutoConfigureMockMvc
class ProductDetailIT extends IntegrationTestBase {

  /** La moneda sembrada por `V15`, con dos decimales. */
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID oro;
  private UUID upgrade;
  private UUID servicio;

  @BeforeEach
  void sembrarCatalogo() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM memberships");
    jdbc.update("DELETE FROM currencies WHERE is_default = false");
    oro = membresia("ORO", "Oro", 1);

    upgrade = producto("UPGRADE_ORO", "UPGRADE_MEMBRESIA", "Ascenso a Oro", oro, "49.99", 30, USD);
    servicio = producto("SOPORTE", "SERVICIO", "Soporte prioritario", null, "99.50", null, USD);
  }

  @AfterEach
  void vaciarCatalogo() {
    // Un producto que sobreviva mantiene una clave foránea sobre `memberships`,
    // y varias pruebas de `SP` empiezan borrando membresías: el fallo saldría
    // en ellas y solo con cierto orden de ejecución.
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'PM'");
  }

  @Test
  @DisplayName("`CA-PM-023` — devuelve el producto con todos sus datos y su moneda")
  void detalleCompleto() throws Exception {
    mvc.perform(detalle(upgrade))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(upgrade.toString()))
        .andExpect(jsonPath("$.code").value("UPGRADE_ORO"))
        .andExpect(jsonPath("$.type").value("UPGRADE_MEMBRESIA"))
        .andExpect(jsonPath("$.name").value("Ascenso a Oro"))
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.currency.code").value("USD"))
        .andExpect(jsonPath("$.currency.decimalPlaces").value(2))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists());
  }

  @Test
  @DisplayName("`CA-PM-024` — el destino de un upgrade llega con su código, su nombre y su nivel")
  void destinoResuelto() throws Exception {
    mvc.perform(detalle(upgrade))
        .andExpect(jsonPath("$.targetMembership.id").value(oro.toString()))
        .andExpect(jsonPath("$.targetMembership.code").value("ORO"))
        .andExpect(jsonPath("$.targetMembership.name").value("Oro"))
        .andExpect(jsonPath("$.targetMembership.level").value(1));
  }

  @Test
  @DisplayName("el nivel del destino es el ACTUAL, no el que tenía al crearse el producto")
  void elNivelEsElActual() throws Exception {
    // La cadena se reordena al insertar un eslabón (`RN-SP-007`): guardar el
    // nivel en el producto lo dejaría mintiendo a la primera reordenación.
    jdbc.update("UPDATE memberships SET level = 4 WHERE id = CAST(? AS uuid)", oro.toString());

    mvc.perform(detalle(upgrade)).andExpect(jsonPath("$.targetMembership.level").value(4));
  }

  @Test
  @DisplayName("`CA-PM-025` — un servicio trae el destino VACÍO Y PRESENTE, no ausente")
  void servicioSinDestino() throws Exception {
    mvc.perform(detalle(servicio))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.targetMembership").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.targetMembership").hasJsonPath());
  }

  @Test
  @DisplayName("`T-12` — la vigencia viaja en el detalle, vacía y presente cuando no caduca")
  void vigencia() throws Exception {
    mvc.perform(detalle(upgrade)).andExpect(jsonPath("$.validityDays").value(30));

    mvc.perform(detalle(servicio))
        .andExpect(jsonPath("$.validityDays").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.validityDays").hasJsonPath());
  }

  @Test
  @DisplayName(
      "`CA-PM-026` — un producto retirado se devuelve marcado, en lugar de decir que no existe")
  void productoRetirado() throws Exception {
    retirar(servicio, "Se descontinuó la línea de soporte.");

    mvc.perform(detalle(servicio))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SOPORTE"))
        .andExpect(jsonPath("$.deletedAt").exists());
  }

  @Test
  @DisplayName("`CA-PM-080` — el motivo del retiro llega LITERAL, y con solo `products:read`")
  void motivoDelRetiro() throws Exception {
    retirar(servicio, "Se descontinuó la línea de soporte.");

    mvc.perform(detalle(servicio))
        .andExpect(status().isOk())
        // Con `products:read` y sin `audit:read-deletions`: es la consecuencia
        // asumida de que el detalle lo devuelva.
        .andExpect(jsonPath("$.deletionReason").value("Se descontinuó la línea de soporte."));
  }

  @Test
  @DisplayName("en un producto vivo, `deletedAt` y `deletionReason` NO aparecen")
  void elProductoVivoNoLosLleva() throws Exception {
    // Su ausencia SIGNIFICA que el producto no está retirado. Enviarlos en nulo
    // obligaría a comprobar dos cosas para saber una.
    mvc.perform(detalle(upgrade))
        .andExpect(jsonPath("$.deletedAt").doesNotExist())
        .andExpect(jsonPath("$.deletionReason").doesNotExist());
  }

  @Test
  @DisplayName("`CA-PM-081` — la respuesta no lleva autoría en NINGUNA forma")
  void sinAutoria() throws Exception {
    retirar(servicio, "Se descontinuó la línea de soporte.");

    String cuerpo =
        mvc.perform(detalle(servicio))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Se comprueba sobre el cuerpo entero y no campo a campo: lo que hay que
    // demostrar es que NO HAY ninguna forma de autoría, y una lista de campos
    // concretos solo demostraría que no están los que se le ocurrieron a quien
    // la escribió.
    org.assertj.core.api.Assertions.assertThat(cuerpo)
        .doesNotContain("createdBy", "updatedBy", "deletedBy", "actor", "actorId", "userId");
  }

  @Test
  @DisplayName(
      "`CA-PM-082` — el precio llega con los decimales de su moneda, no con la de la columna")
  void precioEnLaEscalaDeSuMoneda() throws Exception {
    mvc.perform(detalle(upgrade))
        .andExpect(jsonPath("$.price").value(49.99))
        // Y como número, no como texto: `"49.99"` obligaría al cliente a
        // convertirlo y a decidir él la escala.
        .andExpect(content().string(Matchers.containsString("\"price\":49.99")));
  }

  @Test
  @DisplayName("el precio de una moneda de CERO decimales llega sin parte decimal")
  void precioEnMonedaSinDecimales() throws Exception {
    String pesos = monedaSinDecimales();
    UUID conPesos = producto("SERVICIO_COP", "SERVICIO", "Asesoría", null, "50.0000", null, pesos);

    mvc.perform(detalle(conPesos))
        .andExpect(jsonPath("$.currency.decimalPlaces").value(0))
        .andExpect(content().string(Matchers.containsString("\"price\":50")));
  }

  @Test
  @DisplayName("`CA-PM-027` — un identificador que no corresponde a ningún producto es 404")
  void inexistente() throws Exception {
    mvc.perform(detalle(UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].code").doesNotExist())
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("No existe un producto")));
  }

  @Test
  @DisplayName("`CA-PM-028` · `T-08` — el identificador no canónico es 400, NO 404")
  void identificadorNoCanonico() throws Exception {
    // `UUID.fromString` del JDK acepta `1-1-1-1-1` y lo convierte en un
    // identificador válido: sin el editor canónico de `shared/error`, quien
    // escribiera mal el identificador recibiría «no existe» y se pondría a
    // buscar un recurso que nunca pudo existir. Es el hueco que `RF-SP-018`
    // tuvo abierto dos días; aquí se PRUEBA, no se escribe.
    mvc.perform(
            get("/api/v1/products/{id}", "1-1-1-1-1")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "products:read")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));

    mvc.perform(
            get("/api/v1/products/{id}", "no-es-un-uuid")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "products:read")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-PM-029` — sin el permiso de lectura, la consulta se rechaza")
  void sinPermiso() throws Exception {
    mvc.perform(
            get("/api/v1/products/{id}", upgrade)
                .with(user(UUID.randomUUID().toString()).authorities(() -> "products:create")))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder detalle(UUID id) {
    return get("/api/v1/products/{id}", id)
        .with(user(UUID.randomUUID().toString()).authorities(() -> "products:read"));
  }

  /**
   * Retira el producto <b>como lo hará `RF-PM-006`</b>: la marca y el registro, juntos.
   *
   * <p>Las dos mitades van siempre: un producto marcado sin registro dejaría el detalle sin motivo
   * que devolver, y esta prueba estaría comprobando el caso degradado en lugar del normal.
   */
  private void retirar(UUID producto, String motivo) {
    jdbc.update(
        "UPDATE products SET deleted_at = ? WHERE id = CAST(? AS uuid)",
        BASE.plusDays(1),
        producto.toString());
    jdbc.update(
        "INSERT INTO audit_deletion_log (id, occurred_at, module, entity, entity_id,"
            + " deletion_type, reason, snapshot)"
            + " VALUES (CAST(? AS uuid), ?, 'PM', 'products', CAST(? AS uuid), 'LOGICAL', ?,"
            + " CAST(? AS jsonb))",
        UUID.randomUUID().toString(),
        BASE.plusDays(1),
        producto.toString(),
        motivo,
        "{}");
  }

  private UUID membresia(String codigo, String nombre, int nivel) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, parent_membership_id, level, color)"
            + " VALUES (?, ?, ?, NULL, ?, upper(lpad(to_hex(? * 4919), 6, '0')))",
        id,
        codigo,
        nombre,
        nivel,
        nivel);
    return id;
  }

  /** Una moneda de cero decimales, no por defecto: la de por defecto no se toca. */
  private String monedaSinDecimales() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)"
            + " VALUES (CAST(? AS uuid), 'COP', 'Peso colombiano', '$', 0, false, true)",
        id.toString());
    return id.toString();
  }

  private UUID producto(
      String codigo,
      String tipo,
      String nombre,
      UUID destino,
      String precio,
      Integer vigencia,
      String moneda) {

    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (id, code, type, name, description, target_membership_id, price,"
            + " currency_id, validity_days, status, created_at, updated_at)"
            + " VALUES (CAST(? AS uuid), ?, ?, ?, NULL, CAST(? AS uuid), CAST(? AS numeric),"
            + " CAST(? AS uuid), CAST(? AS integer), 'INACTIVO', ?, ?)",
        id.toString(),
        codigo,
        tipo,
        nombre,
        destino == null ? null : destino.toString(),
        precio,
        moneda,
        vigencia,
        BASE,
        BASE);
    return id;
  }
}
