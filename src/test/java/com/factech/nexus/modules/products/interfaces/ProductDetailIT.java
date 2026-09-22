package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import jakarta.persistence.EntityManagerFactory;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.hibernate.stat.Statistics;
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

  @Autowired private EntityManagerFactory emf;

  private UUID oro;
  private UUID free;
  private UUID upgrade;
  private UUID bot;

  @BeforeEach
  void sembrarCatalogo() {
    ProductLinkTestSupport.limpiar(jdbc);
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM memberships");
    jdbc.update("DELETE FROM currencies WHERE is_default = false");
    oro = membresia("ORO", "Oro", 1);
    // El SUELO de la cadena: es el origen de todo upgrade que se siembre
    // aqui. Va encadenado bajo `oro` porque `uq_memberships_parent` es
    // UNIQUE NULLS NOT DISTINCT — dos raices revientan en el COMMIT.
    free = membresia("BECA", "Beca", 2, oro);

    upgrade = producto("UPGRADE_ORO", "UPGRADE_MEMBRESIA", "Ascenso a Oro", oro, "49.99", 30, USD);
    bot = producto("SOPORTE", "BOT", "Soporte prioritario", null, "99.50", null, USD);
  }

  @AfterEach
  void vaciarCatalogo() {
    // Un producto que sobreviva mantiene una clave foránea sobre `memberships`,
    // y varias pruebas de `SP` empiezan borrando membresías: el fallo saldría
    // en ellas y solo con cierto orden de ejecución.
    ProductLinkTestSupport.limpiar(jdbc);
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'PM'");
    // La conversión trajo `exchange_rates` a esta clase (08-09-2026), y va
    // antes que las monedas: las tasas las referencian.
    jdbc.update("DELETE FROM exchange_rates");
    jdbc.update("DELETE FROM currencies WHERE is_default = false");
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
  @DisplayName("`CA-PM-025` — un bot trae el destino VACÍO Y PRESENTE, no ausente")
  void botSinDestino() throws Exception {
    mvc.perform(detalle(bot))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.targetMembership").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.targetMembership").hasJsonPath());
  }

  @Test
  @DisplayName("`T-12` — la vigencia viaja en el detalle, vacía y presente cuando no caduca")
  void vigencia() throws Exception {
    mvc.perform(detalle(upgrade)).andExpect(jsonPath("$.validityDays").value(30));

    mvc.perform(detalle(bot))
        .andExpect(jsonPath("$.validityDays").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.validityDays").hasJsonPath());
  }

  @Test
  @DisplayName(
      "`CA-PM-026` — un producto retirado se devuelve marcado, en lugar de decir que no existe")
  void productoRetirado() throws Exception {
    retirar(bot, "Se descontinuó la línea de soporte.");

    mvc.perform(detalle(bot))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SOPORTE"))
        .andExpect(jsonPath("$.deletedAt").exists());
  }

  @Test
  @DisplayName("`CA-PM-080` — el motivo del retiro llega LITERAL, y con solo `products:read`")
  void motivoDelRetiro() throws Exception {
    retirar(bot, "Se descontinuó la línea de soporte.");

    mvc.perform(detalle(bot))
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
    retirar(bot, "Se descontinuó la línea de soporte.");

    String cuerpo =
        mvc.perform(detalle(bot))
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
  @DisplayName("`CA-PM-152` — el detalle devuelve los DOS precios, y el de compra nulo y presente")
  void elDetalleTraeLosDosPrecios() throws Exception {
    // Sin declararlo, el campo va PRESENTE con nulo: su nulo significa «no se
    // conoce el costo», y un campo ausente no puede decirlo.
    mvc.perform(detalle(upgrade))
        .andExpect(jsonPath("$.price").value(49.99))
        .andExpect(jsonPath("$.purchasePrice").doesNotExist())
        .andExpect(content().string(Matchers.containsString("\"purchasePrice\":null")));

    jdbc.update(
        "UPDATE products SET purchase_price = CAST('59.99' AS numeric) WHERE id = CAST(? AS uuid)",
        upgrade.toString());

    // Y los dos salen con los decimales de SU moneda, con la misma función:
    // escrita dos veces, el mismo producto enseñaría uno con dos decimales y
    // el otro con cuatro.
    mvc.perform(detalle(upgrade))
        .andExpect(jsonPath("$.price").value(49.99))
        .andExpect(jsonPath("$.purchasePrice").value(59.99))
        .andExpect(content().string(Matchers.containsString("\"purchasePrice\":59.99")));
  }

  @Test
  @DisplayName("`CA-PM-224` — el detalle devuelve `links` tal cual, vacía sin ellos, y retirado")
  void elDetalleTraeSusEnlaces() throws Exception {
    mvc.perform(detalle(bot))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.links").isArray())
        .andExpect(jsonPath("$.links.length()").value(0))
        .andExpect(jsonPath("$.videoUrl").doesNotExist());

    // Tal cual se guardó: ni minúsculas ni barra final.
    ProductLinkTestSupport.enlace(
        jdbc, bot, "VIDEO_PRESENTACION", "https://Vimeo.com/123456/", null);
    mvc.perform(detalle(bot))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.links.length()").value(1))
        .andExpect(jsonPath("$.links[0].url").value("https://Vimeo.com/123456/"));

    // Y en uno retirado sigue legible, como el resto de su configuración.
    retirar(bot, "Se descontinúa el servicio.");
    mvc.perform(detalle(bot))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedAt").exists())
        .andExpect(jsonPath("$.links[0].url").value("https://Vimeo.com/123456/"));
  }

  @Test
  @DisplayName("`CA-PM-387` — el detalle enseña los DOS enlaces, crudos, también si está retirado")
  void elDetalleEnsenaLosDosEnlaces() throws Exception {
    ProductLinkTestSupport.enlace(
        jdbc, bot, "VIDEO_PRESENTACION", "https://vimeo.com/123456", null);
    ProductLinkTestSupport.enlace(jdbc, bot, "CUPON_BOT", "https://t.me/nexusbot", "cupon-15");

    // Sin filtrar por tipo: es la lectura de administración, y es donde los
    // enlaces se administran. El cupón se esconde en las lecturas de venta.
    mvc.perform(detalle(bot))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.links.length()").value(2))
        .andExpect(
            jsonPath("$.links[?(@.type == 'CUPON_BOT')].url")
                .value(Matchers.contains("https://t.me/nexusbot")))
        // Crudo, no compuesto: el identificador viaja en SU campo, que es lo
        // que `RF-PM-004` espera recibir de vuelta.
        .andExpect(
            jsonPath("$.links[?(@.type == 'CUPON_BOT')].externalId")
                .value(Matchers.contains("cupon-15")));

    retirar(bot, "Se descontinúa el servicio.");
    mvc.perform(detalle(bot))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.links.length()").value(2));
  }

  @Test
  @DisplayName("`CA-PM-388` — los enlaces cuestan UNA sentencia más, la misma con uno que con dos")
  void losEnlacesCuestanUnaSentencia() throws Exception {
    ProductLinkTestSupport.enlace(
        jdbc, bot, "VIDEO_PRESENTACION", "https://vimeo.com/123456", null);
    long conUno = sentenciasDe(detalle(bot));

    ProductLinkTestSupport.enlace(jdbc, bot, "CUPON_BOT", "https://t.me/nexusbot", "cupon-15");
    long conDos = sentenciasDe(detalle(bot));

    // Si la lectura preguntara por enlace, el segundo costaría una sentencia
    // más y este recuento crecería. Se compara, no se fija una cifra: lo que
    // se afirma es que no depende de cuántos enlaces haya.
    assertThat(conDos).isEqualTo(conUno);
  }

  /** Las sentencias preparadas que cuesta una petición, medidas por Hibernate. */
  private long sentenciasDe(MockHttpServletRequestBuilder peticion) throws Exception {
    Statistics estadisticas = emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
    estadisticas.clear();
    mvc.perform(peticion).andExpect(status().isOk());
    return estadisticas.getPrepareStatementCount();
  }

  @Test
  @DisplayName(
      "`CA-PM-233` — el detalle devuelve `coverImageUrl`, nulo y presente sin ella, y retirado")
  void elDetalleTraeLaPortada() throws Exception {
    mvc.perform(detalle(bot))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.coverImageUrl").doesNotExist())
        .andExpect(content().string(Matchers.containsString("\"coverImageUrl\":null")));

    UUID imagen = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO product_images (id, content_type, content) VALUES (CAST(? AS uuid),"
            + " 'image/png', decode('89504E470D0A1A0A00', 'hex'))",
        imagen.toString());
    jdbc.update(
        "UPDATE products SET cover_image_id = CAST(? AS uuid) WHERE id = CAST(? AS uuid)",
        imagen.toString(),
        bot.toString());
    mvc.perform(detalle(bot))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.coverImageUrl").value("/api/v1/product-images/" + imagen));

    // En uno retirado sigue: la portada es parte de lo que el producto era.
    retirar(bot, "Se descontinúa el servicio.");
    mvc.perform(detalle(bot))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedAt").exists())
        .andExpect(jsonPath("$.coverImageUrl").value("/api/v1/product-images/" + imagen));

    jdbc.update("UPDATE products SET cover_image_id = NULL");
    jdbc.update("DELETE FROM product_images");
  }

  @Test
  @DisplayName("el precio de una moneda de CERO decimales llega sin parte decimal")
  void precioEnMonedaSinDecimales() throws Exception {
    String pesos = monedaSinDecimales();
    UUID conPesos = producto("BOT_COP", "BOT", "Asesoría", null, "50.0000", null, pesos);

    mvc.perform(detalle(conPesos))
        .andExpect(jsonPath("$.currency.decimalPlaces").value(0))
        .andExpect(content().string(Matchers.containsString("\"price\":50")));
  }

  @Test
  @DisplayName(
      "`CA-PM-166` — el detalle trae la conversión, presente y nula si no hay que convertir")
  void elDetalleTraeLaConversion() throws Exception {
    // El producto está en la moneda de casa: no hay nada que convertir, y el
    // campo llega PRESENTE con nulo. Ausente sería indistinguible de uno que el
    // cliente no conoce.
    mvc.perform(detalle(upgrade))
        .andExpect(jsonPath("$.exchange").value(Matchers.nullValue()))
        .andExpect(content().string(Matchers.containsString("\"exchange\":null")));

    String pesos = monedaSinDecimales();
    UUID enPesos = producto("BOT_CONV", "BOT", "Con conversión", null, "1000.0000", null, pesos);
    jdbc.update(
        "INSERT INTO exchange_rates (id, source_currency_id, target_currency_id, price,"
            + " valid_from, valid_to, is_active)"
            + " VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), 0.00024096,"
            + " CAST(? AS date), NULL, true)",
        UUID.randomUUID().toString(),
        pesos,
        USD,
        java.time.LocalDate.now().minusDays(1).toString());

    mvc.perform(detalle(enPesos))
        .andExpect(jsonPath("$.exchange.currency.code").value("USD"))
        // La tasa como CADENA, con sus ocho decimales intactos.
        .andExpect(jsonPath("$.exchange.rate").value("0.00024096"))
        // 1000 × 0,00024096 = 0,24096 → 0,24 con los dos decimales de USD, que
        // es la moneda de DESTINO: los cero decimales del origen no mandan aquí.
        .andExpect(jsonPath("$.exchange.amount").value(0.24));
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
                .with(
                    user(UUID.randomUUID().toString())
                        .authorities(() -> "products:read", () -> "products:list")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));

    mvc.perform(
            get("/api/v1/products/{id}", "no-es-un-uuid")
                .with(
                    user(UUID.randomUUID().toString())
                        .authorities(() -> "products:read", () -> "products:list")))
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

  @Test
  @DisplayName(
      "`CA-PM-118` — el detalle devuelve el alcance y la implementación, retirado incluido")
  void alcanceEImplementacionEnElDetalle() throws Exception {
    jdbc.update(
        "UPDATE products SET scope = 'AMBOS', implementation = 'AUTOMATICA' WHERE id = ?", upgrade);

    mvc.perform(detalle(upgrade))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scope").value("AMBOS"))
        .andExpect(jsonPath("$.implementation").value("AUTOMATICA"));

    // En el bot también: ninguna de las dos depende del tipo.
    mvc.perform(detalle(bot))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scope").value("TIENDA"))
        .andExpect(jsonPath("$.implementation").value("MANUAL"));

    // Y en uno retirado: el detalle lo devuelve marcado, no como inexistente
    // (`CA-PM-026`), de modo que su configuración sigue siendo legible.
    retirar(bot, "Se descontinúa el servicio.");
    mvc.perform(detalle(bot))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedAt").exists())
        .andExpect(jsonPath("$.scope").value("TIENDA"))
        .andExpect(jsonPath("$.implementation").value("MANUAL"));
  }

  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-PM-143` — el detalle trae el COLOR de las dos membresías")
  void elDetalleTraeElColorDeLasMembresias() throws Exception {
    mvc.perform(detalle(upgrade))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.targetMembership.color").value(Matchers.matchesPattern("^[0-9A-F]{6}$")))
        .andExpect(
            jsonPath("$.sourceMembership.color").value(Matchers.matchesPattern("^[0-9A-F]{6}$")));
  }

  private MockHttpServletRequestBuilder detalle(UUID id) {
    return get("/api/v1/products/{id}", id)
        .with(
            user(UUID.randomUUID().toString())
                .authorities(() -> "products:read", () -> "products:list"));
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
    return membresia(codigo, nombre, nivel, null);
  }

  private UUID membresia(String codigo, String nombre, int nivel, UUID superior) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, parent_membership_id, level, color)"
            + " VALUES (CAST(? AS uuid), ?, ?, CAST(? AS uuid), ?,"
            + " upper(lpad(to_hex(? * 4919), 6, '0')))",
        id.toString(),
        codigo,
        nombre,
        superior == null ? null : superior.toString(),
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
    // Origen y destino VIAJAN JUNTOS: un upgrade declara los dos
    // (`RN-PM-002`) y un bot no declara ninguno. Por eso el origen se
    // deriva del destino en lugar de ser un parametro mas — nunca puede
    // quedar uno sin el otro, que es lo que `ck_products_type_target` mira.
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description, source_membership_id,"
            + " target_membership_id, price,"
            + " currency_id, validity_days, status, created_at, updated_at)"
            + " VALUES ('TIENDA', 'MANUAL', CAST(? AS uuid), ?, ?, ?, NULL,"
            + " CAST(? AS uuid), CAST(? AS uuid), CAST(? AS numeric),"
            + " CAST(? AS uuid), CAST(? AS integer), 'INACTIVO', ?, ?)",
        id.toString(),
        codigo,
        tipo,
        nombre,
        destino == null ? null : free.toString(),
        destino == null ? null : destino.toString(),
        precio,
        moneda,
        vigencia,
        BASE,
        BASE);
    return id;
  }
}
