package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * La corrección de un producto (`RF-PM-004` · `T-10`).
 *
 * <p>Cubre los criterios de `spec.md` §12. La prueba que sostiene todo lo demás es la de los
 * <b>tres estados</b>: vaciar la descripción la borra, enviar el nombre vacío se rechaza, y el
 * campo ausente no se toca. Si los tres no se distinguieran, la operación borraría lo que nadie
 * pidió borrar.
 */
@AutoConfigureMockMvc
class ProductUpdateIT extends IntegrationTestBase {

  /** La moneda sembrada por `V15`, con dos decimales. */
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID oro;
  private UUID producto;

  @BeforeEach
  void sembrarCatalogo() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM memberships");
    jdbc.update("DELETE FROM currencies WHERE is_default = false");
    oro = membresia("ORO", "Oro", 1);
    producto = upgrade("UPGRADE_ORO", "Ascenso a Oro", oro, "Sube al nivel oro.", 30);
  }

  @AfterEach
  void vaciarCatalogo() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'PM'");
  }

  @Test
  @DisplayName("`CA-PM-030` — corrige nombre, descripción, precio y moneda; conserva lo inmutable")
  void corrigeLoCorregible() throws Exception {
    String euro = monedaAlterna();

    mvc.perform(
            corregir(
                producto,
                """
                {"name":"Ascenso a Oro premium","description":"Ahora con soporte.",
                 "price":59.99,"currencyId":"%s"}
                """
                    .formatted(euro)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ascenso a Oro premium"))
        .andExpect(jsonPath("$.description").value("Ahora con soporte."))
        .andExpect(jsonPath("$.price").value(59.99))
        .andExpect(jsonPath("$.currency.code").value("EUR"))
        // Lo que no se puede corregir sigue donde estaba.
        .andExpect(jsonPath("$.code").value("UPGRADE_ORO"))
        .andExpect(jsonPath("$.type").value("UPGRADE_MEMBRESIA"))
        .andExpect(jsonPath("$.targetMembership.id").value(oro.toString()));
  }

  @Test
  @DisplayName("`CA-PM-031` — aplica SOLO lo enviado y deja intacto lo ausente")
  void soloLoEnviado() throws Exception {
    mvc.perform(corregir(producto, "{\"name\":\"Otro nombre\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Otro nombre"))
        .andExpect(jsonPath("$.description").value("Sube al nivel oro."))
        .andExpect(jsonPath("$.price").value(49.99))
        .andExpect(jsonPath("$.validityDays").value(30));
  }

  @Test
  @DisplayName("`CA-PM-032` — vaciar la descripción la BORRA; el nombre vacío se RECHAZA")
  void ausenteNoEsLoMismoQueVacio() throws Exception {
    mvc.perform(corregir(producto, "{\"description\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value(Matchers.nullValue()));

    // El nombre no admite vaciarse: su columna es NOT NULL, y aceptar el nulo
    // produciría una violación de integridad traducida a 500 en lugar del 400
    // que corresponde.
    mvc.perform(corregir(producto, "{\"name\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));

    mvc.perform(corregir(producto, "{\"name\":\"   \"}")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-PM-099` — el icono se corrige y se vacía con nulo explícito")
  void corregirElIcono() throws Exception {
    mvc.perform(corregir(producto, "{\"icon\":\"  CROWN  \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.icon").value("crown"));

    mvc.perform(corregir(producto, "{\"icon\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.icon").value(Matchers.nullValue()));
  }

  @Test
  @DisplayName("`CA-PM-100` — `RN-PM-016` no admite excepción por venir en un PATCH")
  void elBotSigueSinPoderLlevarIcono() throws Exception {
    UUID asesoria = bot("ASESORIA", "Asesoría", null);

    mvc.perform(corregir(asesoria, "{\"icon\":\"crown\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-013"))
        .andExpect(jsonPath("$.errors[0].field").value("icon"));

    // Vaciar el que nunca tuvo no es un cambio, y no es un error: el nulo es el
    // único valor que un bot puede llevar.
    mvc.perform(corregir(asesoria, "{\"icon\":null}")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("`CA-PM-094` — vaciar la vigencia convierte el producto en uno que no caduca")
  void vaciarLaVigencia() throws Exception {
    mvc.perform(corregir(producto, "{\"validityDays\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validityDays").value(Matchers.nullValue()));

    assertThat(vigenciaDe(producto)).isNull();
  }

  @Test
  @DisplayName("`VAL-011` — una vigencia de cero o negativa se rechaza")
  void vigenciaNoPositiva() throws Exception {
    mvc.perform(corregir(producto, "{\"validityDays\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-011"));

    mvc.perform(corregir(producto, "{\"validityDays\":-5}")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-PM-033` — el tipo, el código y el destino se RECHAZAN, no se ignoran")
  void losInmutablesSeRechazan() throws Exception {
    mvc.perform(corregir(producto, "{\"code\":\"OTRO_CODIGO\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-006"))
        .andExpect(jsonPath("$.errors[0].field").value("code"));

    mvc.perform(corregir(producto, "{\"type\":\"BOT\"}")).andExpect(status().isBadRequest());
    mvc.perform(corregir(producto, "{\"targetMembershipId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isBadRequest());

    // Y el rechazo NO aplica lo demás que venía en la misma petición.
    mvc.perform(corregir(producto, "{\"name\":\"Nombre nuevo\",\"code\":\"OTRO\"}"))
        .andExpect(status().isBadRequest());
    assertThat(nombreDe(producto)).isEqualTo("Ascenso a Oro");
  }

  @Test
  @DisplayName("los tres inmutables enviados juntos se enumeran los tres")
  void losTresInmutablesJuntos() throws Exception {
    mvc.perform(
            corregir(
                producto,
                """
                {"type":"BOT","code":"OTRO","targetMembershipId":"%s"}
                """
                    .formatted(UUID.randomUUID())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(3));
  }

  @Test
  @DisplayName("`CA-PM-034` — el nombre de OTRO producto vivo se rechaza, y no aplica nada más")
  void nombreDuplicado() throws Exception {
    bot("SOPORTE", "Soporte prioritario", "Atención prioritaria.");

    mvc.perform(
            corregir(producto, "{\"name\":\"Soporte prioritario\",\"description\":\"Otra cosa.\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));

    // NINGUNO de los cambios enviados se aplica.
    assertThat(nombreDe(producto)).isEqualTo("Ascenso a Oro");
    assertThat(descripcionDe(producto)).isEqualTo("Sube al nivel oro.");
  }

  @Test
  @DisplayName("enviar el nombre que YA TIENE no es un duplicado consigo mismo")
  void elPropioNombreNoEsDuplicado() throws Exception {
    // Sin excluir al propio producto, corregir la descripción enviando también
    // el nombre actual acabaría rechazándose.
    mvc.perform(corregir(producto, "{\"name\":\"Ascenso a Oro\",\"description\":\"Otra cosa.\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Otra cosa."));
  }

  @Test
  @DisplayName("un nombre que solo cambia en mayúsculas o acentos SÍ se admite")
  void cambiarSoloLaCaja() throws Exception {
    // El choque es contra OTROS productos, no contra uno mismo: `Ascenso a Oro`
    // y `ASCENSO A ORO` normalizan igual, y aun así este cambio es legítimo.
    mvc.perform(corregir(producto, "{\"name\":\"ASCENSO A ORO\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("ASCENSO A ORO"));
  }

  @Test
  @DisplayName("`CA-PM-035` — una moneda inactiva se rechaza y no aplica ningún cambio")
  void rechazaMonedaDesactivada() throws Exception {
    String inactiva = crearMonedaInactiva();

    mvc.perform(
            corregir(
                producto, "{\"currencyId\":\"" + inactiva + "\",\"description\":\"Otra cosa.\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    assertThat(descripcionDe(producto)).isEqualTo("Sube al nivel oro.");
  }

  @Test
  @DisplayName("`EX-003` — una moneda inexistente también se rechaza")
  void monedaInexistente() throws Exception {
    mvc.perform(corregir(producto, "{\"currencyId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
  }

  @Test
  @DisplayName("`VAL-005` — el precio se valida contra la moneda NUEVA, no contra la anterior")
  void elPrecioContraLaMonedaNueva() throws Exception {
    String pesos = monedaSinDecimales();

    // `49.99` cabe en USD, que declara dos decimales, y NO cabe en una moneda
    // de cero. Validar contra la anterior lo dejaría entrar.
    mvc.perform(corregir(producto, "{\"currencyId\":\"" + pesos + "\",\"price\":49.99}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));

    // Con un importe sin decimales, la misma moneda sí lo admite.
    mvc.perform(corregir(producto, "{\"currencyId\":\"" + pesos + "\",\"price\":50}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency.decimalPlaces").value(0));
  }

  @Test
  @DisplayName(
      "cambiar solo la moneda NO convierte el importe: el sistema no hace cambio de divisa")
  void cambiarMonedaNoConvierte() throws Exception {
    String euro = monedaAlterna();

    mvc.perform(corregir(producto, "{\"currencyId\":\"" + euro + "\"}"))
        .andExpect(status().isOk())
        // Cambiar de moneda es declarar que ese número SIEMPRE estuvo en la
        // otra, no convertirlo.
        .andExpect(jsonPath("$.price").value(49.99))
        .andExpect(jsonPath("$.currency.code").value("EUR"));
  }

  @Test
  @DisplayName("`VAL-004` — un precio de cero o negativo se rechaza")
  void precioNoPositivo() throws Exception {
    mvc.perform(corregir(producto, "{\"price\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));

    mvc.perform(corregir(producto, "{\"price\":-1}")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-PM-036` — un producto retirado NO se corrige")
  void elRetiradoNoSeCorrige() throws Exception {
    jdbc.update(
        "UPDATE products SET deleted_at = ? WHERE id = CAST(? AS uuid)",
        BASE.plusDays(1),
        producto.toString());

    // Lo que se retiró debe quedar como estaba para que lo que lo referencie
    // siga diciendo la verdad.
    mvc.perform(corregir(producto, "{\"name\":\"Otro nombre\"}")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("`CA-PM-083` — SÍ se corrige un producto inactivo, que es el estado en el que nace")
  void elInactivoSeCorrige() throws Exception {
    // Sin esto no habría forma de ponerle la descripción que `RF-PM-005` exige
    // para publicarlo: el producto quedaría atrapado en el estado inicial.
    assertThat(estadoDe(producto)).isEqualTo("INACTIVO");

    mvc.perform(corregir(producto, "{\"description\":\"Una descripción para publicar.\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("`CA-PM-037` — el registro guarda SOLO los campos que cambiaron")
  void auditaSoloLoQueCambio() throws Exception {
    mvc.perform(corregir(producto, "{\"name\":\"Otro nombre\",\"validityDays\":30}"))
        .andExpect(status().isOk());

    String cambios = ultimoCambio(producto);

    // La vigencia se envió con el MISMO valor que ya tenía: no cambió, y no
    // debe aparecer.
    assertThat(cambios).contains("name").contains("Ascenso a Oro").contains("Otro nombre");
    assertThat(cambios).doesNotContain("validity_days");
  }

  @Test
  @DisplayName("`CA-PM-038` — la petición que no cambia nada devuelve 200 y NO registra evento")
  void sinCambioNoRegistraEvento() throws Exception {
    long antes = eventosDe(producto);

    mvc.perform(corregir(producto, "{\"name\":\"Ascenso a Oro\",\"price\":49.99}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ascenso a Oro"));

    assertThat(eventosDe(producto)).isEqualTo(antes);
  }

  @Test
  @DisplayName("`VAL-002` — un cuerpo sin ningún campo corregible se rechaza")
  void cuerpoVacio() throws Exception {
    mvc.perform(corregir(producto, "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
  }

  @Test
  @DisplayName("una propiedad desconocida se rechaza, y no se ignora en silencio")
  void propiedadDesconocida() throws Exception {
    mvc.perform(corregir(producto, "{\"status\":\"ACTIVO\"}")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-PM-084` — no se exige motivo, ni siquiera al cambiar el precio")
  void sinMotivo() throws Exception {
    mvc.perform(corregir(producto, "{\"price\":99.99}")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("`VAL-001` — el identificador no canónico es 400, no 404")
  void identificadorNoCanonico() throws Exception {
    mvc.perform(
            patch("/api/v1/products/{id}", "1-1-1-1-1")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Otro\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`EX-001` — un identificador inexistente es 404")
  void inexistente() throws Exception {
    mvc.perform(corregir(UUID.randomUUID(), "{\"name\":\"Otro\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("`CA-PM-039` — sin `products:update`, la corrección se rechaza y no aplica nada")
  void sinPermiso() throws Exception {
    mvc.perform(
            patch("/api/v1/products/{id}", producto)
                .with(user(UUID.randomUUID().toString()).authorities(() -> "products:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Otro nombre\"}"))
        .andExpect(status().isForbidden());

    assertThat(nombreDe(producto)).isEqualTo("Ascenso a Oro");
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder corregir(UUID id, String cuerpo) {
    return patch("/api/v1/products/{id}", id)
        .with(admin())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private static RequestPostProcessor admin() {
    return user(UUID.randomUUID().toString()).authorities(() -> "products:update");
  }

  private String nombreDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT name FROM products WHERE id = CAST(? AS uuid)", String.class, id.toString());
  }

  private String descripcionDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT description FROM products WHERE id = CAST(? AS uuid)", String.class, id.toString());
  }

  private Integer vigenciaDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT validity_days FROM products WHERE id = CAST(? AS uuid)",
        Integer.class,
        id.toString());
  }

  private String estadoDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT status FROM products WHERE id = CAST(? AS uuid)", String.class, id.toString());
  }

  private String ultimoCambio(UUID id) {
    return jdbc.queryForObject(
        "SELECT changes::text FROM audit_change_log WHERE entity_id = CAST(? AS uuid)"
            + " AND action = 'UPDATE' ORDER BY occurred_at DESC LIMIT 1",
        String.class,
        id.toString());
  }

  private long eventosDe(UUID id) {
    Long filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE entity_id = CAST(? AS uuid)",
            Long.class,
            id.toString());
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

  /** Una moneda activa de dos decimales, distinta de la de por defecto. */
  private String monedaAlterna() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)"
            + " VALUES (CAST(? AS uuid), 'EUR', 'Euro', 'E', 2, false, true)",
        id.toString());
    return id.toString();
  }

  private String monedaSinDecimales() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)"
            + " VALUES (CAST(? AS uuid), 'COP', 'Peso colombiano', '$', 0, false, true)",
        id.toString());
    return id.toString();
  }

  private String crearMonedaInactiva() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)"
            + " VALUES (CAST(? AS uuid), 'GBP', 'Libra', 'L', 2, false, false)",
        id.toString());
    return id.toString();
  }

  private UUID upgrade(
      String codigo, String nombre, UUID destino, String descripcion, Integer vigencia) {
    return crear(codigo, "UPGRADE_MEMBRESIA", nombre, descripcion, destino, vigencia);
  }

  private UUID bot(String codigo, String nombre, String descripcion) {
    return crear(codigo, "BOT", nombre, descripcion, null, null);
  }

  private UUID crear(
      String codigo,
      String tipo,
      String nombre,
      String descripcion,
      UUID destino,
      Integer vigencia) {

    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (id, code, type, name, description, target_membership_id, price,"
            + " currency_id, validity_days, status, created_at, updated_at)"
            + " VALUES (CAST(? AS uuid), ?, ?, ?, CAST(? AS text), CAST(? AS uuid), 49.99,"
            + " CAST(? AS uuid), CAST(? AS integer), 'INACTIVO', ?, ?)",
        id.toString(),
        codigo,
        tipo,
        nombre,
        descripcion,
        destino == null ? null : destino.toString(),
        USD,
        vigencia,
        BASE,
        BASE);
    return id;
  }
}
