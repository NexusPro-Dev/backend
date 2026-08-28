package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
 * El retiro de un producto (`RF-PM-006` · `T-07`, `T-08` y `T-09`).
 *
 * <p>Cubre los criterios de `spec.md` §12. Las dos pruebas que más importan no son las del camino
 * feliz: que el <b>estado no se toca</b> —el registro tiene que decir si el producto estaba a la
 * venta— y que la <b>instantánea es la anterior</b> al retiro. Las dos se hacen mal sin fallar: el
 * sistema funciona y el registro miente.
 */
@AutoConfigureMockMvc
class ProductDeletionIT extends IntegrationTestBase {

  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID oro;

  @BeforeEach
  void sembrarCatalogo() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM memberships");
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'PM'");
    oro = membresia("ORO", "Oro", 1);
  }

  @AfterEach
  void vaciarCatalogo() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'PM'");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'PM'");
  }

  @Test
  @DisplayName("`CA-PM-048` — retira el producto y este deja de ofrecerse, conservando su fila")
  void retira() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", "Atención prioritaria.");

    mvc.perform(retirar(bot, "Se descontinuó la línea.")).andExpect(status().isNoContent());

    // La fila sigue ahí: lo vendido tiene que seguir resolviendo a lo que se
    // vendió (`RN-PM-010`).
    assertThat(sigueLaFila(bot)).isTrue();
    assertThat(fechaDeRetiro(bot)).isNotNull();

    // Y deja de aparecer en el catálogo salvo que se pidan los retirados.
    mvc.perform(listado("")).andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(listado("?includeDeleted=true")).andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  @DisplayName("`CA-PM-049` — sin motivo se rechaza, y NO retira nada")
  void sinMotivoNoRetiraNada() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", "Atención prioritaria.");

    mvc.perform(
            post("/api/v1/products/{id}/deletion", bot)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"))
        .andExpect(jsonPath("$.errors[0].field").value("reason"));

    assertThat(fechaDeRetiro(bot)).isNull();
  }

  @Test
  @DisplayName("`CA-PM-050` — un motivo de solo espacios se rechaza igual que uno ausente")
  void motivoEnBlanco() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", "Atención prioritaria.");

    mvc.perform(retirar(bot, "     "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));

    assertThat(fechaDeRetiro(bot)).isNull();
  }

  @Test
  @DisplayName("`VAL-003` — el motivo demasiado largo se rechaza y tampoco retira nada")
  void motivoDemasiadoLargo() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", "Atención prioritaria.");

    mvc.perform(retirar(bot, "x".repeat(501)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));

    assertThat(fechaDeRetiro(bot)).isNull();
  }

  @Test
  @DisplayName("`CA-PM-051` — el registro guarda el motivo LITERAL y el estado completo")
  void registraMotivoEInstantanea() throws Exception {
    UUID upgrade = upgrade("UPGRADE_ORO", "Ascenso a Oro", oro, "Sube al nivel oro.");

    mvc.perform(retirar(upgrade, "Se reemplaza por la nueva promoción."))
        .andExpect(status().isNoContent());

    assertThat(motivoRegistrado(upgrade)).isEqualTo("Se reemplaza por la nueva promoción.");

    String instantanea = instantaneaRegistrada(upgrade);
    assertThat(instantanea)
        .contains("UPGRADE_ORO")
        .contains("Ascenso a Oro")
        .contains("Sube al nivel oro.")
        .contains("UPGRADE_MEMBRESIA")
        .contains(oro.toString())
        .contains("49.99");
  }

  @Test
  @DisplayName(
      "`CA-PM-052` · `CA-PM-086` — retira un ACTIVO sin exigir desactivarlo, y lo conserva")
  void elRegistroConservaQueEstabaActivo() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", "Atención prioritaria.");
    mvc.perform(activar(bot)).andExpect(status().isOk());

    mvc.perform(retirar(bot, "Se descontinuó la línea.")).andExpect(status().isNoContent());

    // El estado NO se toca al retirar: si se desactivara «de paso», todos los
    // registros dirían «inactivo» y ese dato dejaría de significar nada.
    assertThat(estadoDe(bot)).isEqualTo("ACTIVO");
    assertThat(instantaneaRegistrada(bot)).contains("ACTIVO");
  }

  @Test
  @DisplayName("`CA-PM-087` — retirar NO emite evento de seguridad")
  void sinEventoDeSeguridad() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", "Atención prioritaria.");
    long antes = eventosDeSeguridad();

    mvc.perform(retirar(bot, "Se descontinuó la línea.")).andExpect(status().isNoContent());

    // Un producto no concede privilegios sobre el sistema, y el catálogo de
    // `security.md` §8.1 es cerrado: no hay código que emitir.
    assertThat(eventosDeSeguridad()).isEqualTo(antes);
  }

  @Test
  @DisplayName("`T-08` — el retiro libera el DESTINO y el NOMBRE, pero NUNCA el código")
  void queLiberaYQueNo() throws Exception {
    UUID primero = upgrade("UPGRADE_ORO", "Ascenso a Oro", oro, "Sube al nivel oro.");
    UUID segundo = upgrade("UPGRADE_ORO_2", "Ascenso a Oro premium", oro, "Otra vía al oro.");
    mvc.perform(activar(primero)).andExpect(status().isOk());

    mvc.perform(retirar(primero, "Se reemplaza.")).andExpect(status().isNoContent());

    // `CA-PM-053` — el destino queda libre: `uq_products_upgrade_target` es
    // parcial y la fila retirada deja de contar.
    mvc.perform(activar(segundo)).andExpect(status().isOk());

    // `CA-PM-054` — el nombre también: `uq_products_name` es parcial.
    mvc.perform(alta("OTRO_ORO", "Ascenso a Oro")).andExpect(status().isCreated());

    // `CA-PM-069` — EL CÓDIGO NO, y esta es la asimetría que ES el diseño:
    // `uq_products_code` es una restricción TOTAL. El día que una factura diga
    // `UPGRADE_ORO` tiene que resolver a un solo producto para siempre.
    mvc.perform(alta("UPGRADE_ORO", "Otro nombre cualquiera"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].field").value("code"));
  }

  @Test
  @DisplayName("`CA-PM-055` — retirar uno YA retirado devuelve 409: no es idempotente a propósito")
  void elSegundoRetiroSeRechaza() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", "Atención prioritaria.");
    mvc.perform(retirar(bot, "El primer motivo.")).andExpect(status().isNoContent());

    mvc.perform(retirar(bot, "Un motivo distinto."))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    // UN SOLO registro, con el motivo del hecho real. Dos motivos sobre un solo
    // hecho es evidencia contradictoria.
    assertThat(cuantosRegistros(bot)).isEqualTo(1);
    assertThat(motivoRegistrado(bot)).isEqualTo("El primer motivo.");
  }

  @Test
  @DisplayName("`CA-PM-056` — un identificador que no corresponde a ningún producto es 404")
  void inexistente() throws Exception {
    mvc.perform(retirar(UUID.randomUUID(), "Da igual el motivo.")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("`VAL-001` — el identificador no canónico es 400, no 404")
  void identificadorNoCanonico() throws Exception {
    mvc.perform(
            post("/api/v1/products/{id}/deletion", "1-1-1-1-1")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Da igual.\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-PM-057` — sin `products:delete`, el retiro se rechaza y no retira nada")
  void sinPermiso() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", "Atención prioritaria.");

    // `products:update` no basta: retirar y corregir son decisiones distintas.
    mvc.perform(
            post("/api/v1/products/{id}/deletion", bot)
                .with(user(UUID.randomUUID().toString()).authorities(() -> "products:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Se descontinuó.\"}"))
        .andExpect(status().isForbidden());

    assertThat(fechaDeRetiro(bot)).isNull();
  }

  @Test
  @DisplayName("el detalle de un producto retirado devuelve el motivo que se escribió aquí")
  void elDetalleDevuelveElMotivoDeEsteRetiro() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", "Atención prioritaria.");

    mvc.perform(retirar(bot, "Se descontinuó la línea de soporte."))
        .andExpect(status().isNoContent());

    // ESTE ES EL RECORRIDO COMPLETO que `RF-PM-003` no pudo escribir: allí el
    // retiro se sembraba a mano porque este requerimiento no existía. Ahora se
    // retira por su endpoint y el detalle lee el motivo por el puerto de
    // `shared/audit`.
    mvc.perform(
            get("/api/v1/products/{id}", bot)
                .with(user(UUID.randomUUID().toString()).authorities(() -> "products:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedAt").exists())
        .andExpect(jsonPath("$.deletionReason").value("Se descontinuó la línea de soporte."))
        // Y sigue diciendo que estaba inactivo, que es lo que era.
        .andExpect(jsonPath("$.status").value("INACTIVO"));
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder retirar(UUID id, String motivo) {
    return post("/api/v1/products/{id}/deletion", id)
        .with(admin())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"" + motivo + "\"}");
  }

  private MockHttpServletRequestBuilder activar(UUID id) {
    return patch("/api/v1/products/{id}/status", id)
        .with(user(UUID.randomUUID().toString()).authorities(() -> "products:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"ACTIVO\"}");
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

  private MockHttpServletRequestBuilder listado(String consulta) {
    return get("/api/v1/products" + consulta)
        .with(user(UUID.randomUUID().toString()).authorities(() -> "products:read"));
  }

  private static RequestPostProcessor admin() {
    return user(UUID.randomUUID().toString()).authorities(() -> "products:delete");
  }

  private boolean sigueLaFila(UUID id) {
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM products WHERE id = CAST(? AS uuid)",
            Integer.class,
            id.toString());
    return filas != null && filas == 1;
  }

  private OffsetDateTime fechaDeRetiro(UUID id) {
    return jdbc.queryForObject(
        "SELECT deleted_at FROM products WHERE id = CAST(? AS uuid)",
        OffsetDateTime.class,
        id.toString());
  }

  private String estadoDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT status FROM products WHERE id = CAST(? AS uuid)", String.class, id.toString());
  }

  private String motivoRegistrado(UUID id) {
    return jdbc.queryForObject(
        "SELECT reason FROM audit_deletion_log WHERE entity_id = CAST(? AS uuid)"
            + " ORDER BY occurred_at DESC LIMIT 1",
        String.class,
        id.toString());
  }

  private String instantaneaRegistrada(UUID id) {
    return jdbc.queryForObject(
        "SELECT snapshot::text FROM audit_deletion_log WHERE entity_id = CAST(? AS uuid)"
            + " ORDER BY occurred_at DESC LIMIT 1",
        String.class,
        id.toString());
  }

  private int cuantosRegistros(UUID id) {
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_deletion_log WHERE entity_id = CAST(? AS uuid)",
            Integer.class,
            id.toString());
    return filas == null ? 0 : filas;
  }

  private long eventosDeSeguridad() {
    Long filas = jdbc.queryForObject("SELECT count(*) FROM audit_security_log", Long.class);
    return filas == null ? 0 : filas;
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

  private UUID bot(String codigo, String nombre, String descripcion) {
    return producto(codigo, "BOT", nombre, descripcion, null, "10.00");
  }

  private UUID upgrade(String codigo, String nombre, UUID destino, String descripcion) {
    return producto(codigo, "UPGRADE_MEMBRESIA", nombre, descripcion, destino, "49.99");
  }

  private UUID producto(
      String codigo, String tipo, String nombre, String descripcion, UUID destino, String precio) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (id, code, type, name, description, target_membership_id, price,"
            + " currency_id, validity_days, status, created_at, updated_at)"
            + " VALUES (CAST(? AS uuid), ?, ?, ?, CAST(? AS text), CAST(? AS uuid),"
            + " CAST(? AS numeric), CAST(? AS uuid), NULL, 'INACTIVO', ?, ?)",
        id.toString(),
        codigo,
        tipo,
        nombre,
        descripcion,
        destino == null ? null : destino.toString(),
        precio,
        USD,
        BASE,
        BASE);
    return id;
  }
}
