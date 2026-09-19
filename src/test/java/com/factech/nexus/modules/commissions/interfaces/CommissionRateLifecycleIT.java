package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.AGENTE;
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
 * <p><b>Desde el 15-09-2026 la tasa nace con su producto</b> (`RN-CM-021`), y eso cambia las dos
 * operaciones: la corrección comprueba el tope, el gratuito y los decimales <b>contra ese único
 * producto</b> —hasta entonces recorría todos los asociados y se rechazaba entera si cualquiera se
 * pasaba—, y el retiro <b>ya no tiene condición</b>: retirar es exactamente la forma de que el
 * producto deje de pagar a ese rol, a la vista y con motivo. `RN-CM-015` queda para la
 * personalizada.
 *
 * <p>Y sigue la de siempre: <b>corregir borra el pasado</b>. Sin vigencia no hay historial, de modo
 * que el registro de auditoría del cambio es hoy el único sitio donde queda escrito el porcentaje
 * anterior.
 */
@AutoConfigureMockMvc
class CommissionRateLifecycleIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID producto;
  private UUID tasa;

  @BeforeEach
  void preparar() {
    limpiar();
    producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_A", false, "1000.00");
    tasa = CommissionFixtures.sembrarTasaDeRol(jdbc, producto, MANAGER, "10.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // Corregir
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("corrige el porcentaje y devuelve la tasa con el producto y el rol resueltos")
  void corrigeElPorcentaje() throws Exception {
    mvc.perform(correccion(tasa, "{\"rateType\":\"PORCENTAJE\",\"percentage\":12.50}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.percentage").value(12.50))
        .andExpect(jsonPath("$.product.code").value("BOT_A"))
        .andExpect(jsonPath("$.product.price").value(1000.00))
        .andExpect(jsonPath("$.product.currency.code").isNotEmpty())
        .andExpect(jsonPath("$.role.code").value("MANAGER"))
        .andExpect(jsonPath("$.associatedProducts").doesNotExist());
  }

  @Test
  @DisplayName("corregir BORRA el porcentaje anterior, y solo la auditoría lo conserva")
  void corregirBorraElPasado() throws Exception {
    mvc.perform(correccion(tasa, "{\"rateType\":\"PORCENTAJE\",\"percentage\":12.00}"))
        .andExpect(status().isOk());

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
    mvc.perform(correccion(tasa, "{\"rateType\":\"PORCENTAJE\",\"percentage\":10.00}"))
        .andExpect(status().isOk());
    assertThat(actualizadaEn()).isEqualTo(antes);
  }

  @Test
  @DisplayName("el rol NO se corrige, y se rechaza en vez de ignorarse")
  void elRolNoSeCorrige() throws Exception {
    mvc.perform(correccion(tasa, "{\"roleId\":\"" + DIRECTOR + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-009"));

    // Ignorarlo haría creer que el cambio se aplicó, y el producto habría
    // pasado a pagar a un rol que nadie eligió.
    assertThat(rolEnBase()).isEqualTo(MANAGER);
  }

  @Test
  @DisplayName("CA-CM-142 · el PRODUCTO tampoco se corrige: es un campo desconocido, 400")
  void elProductoNoSeCorrige() throws Exception {
    UUID otro = CommissionFixtures.sembrarProducto(jdbc, "BOT_B");

    // La petición no lo declara y el deserializador rechaza lo desconocido en
    // vez de descartarlo en silencio. Cambiar de producto es retirar la tasa y
    // registrar otra (`RN-CM-021`).
    mvc.perform(correccion(tasa, "{\"productId\":\"" + otro + "\"}"))
        .andExpect(status().isBadRequest());

    assertThat(productoEnBase()).isEqualTo(producto.toString());
  }

  @Test
  @DisplayName("vaciar el porcentaje se rechaza: una tasa sin porcentaje no significa nada")
  void elPorcentajeNoSeVacia() throws Exception {
    mvc.perform(correccion(tasa, "{\"rateType\":null}"))
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
    mvc.perform(correccion(tasa, "{\"rateType\":\"PORCENTAJE\",\"percentage\":12.00}"))
        .andExpect(status().isNotFound());
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
  @DisplayName(
      "CA-CM-143 · LA TASA QUE RIGE SE RETIRA SIN CONDICIÓN, y el producto deja de pagar a ese rol"
          + " — reescrito el 15-09-2026")
  void retirarEsDejarDePagar() throws Exception {
    // Hasta el 15-09-2026 esta prueba decía lo contrario: una tasa asociada no
    // se retiraba (`RN-CM-015`), porque la asociación habría sobrevivido
    // apuntando a una fila que la resolución ya no mira. Sin asociación no hay
    // nada que sobreviva: retirar ES la forma de dejar de pagar, a la vista.
    UUID vendedora = CommissionFixtures.sembrarPersonaConRol(jdbc, "vendedora", MANAGER);
    mvc.perform(efectiva(vendedora, producto)).andExpect(jsonPath("$.outcome").value("RESUELTA"));

    mvc.perform(retiro(tasa, "ya no aplica")).andExpect(status().isNoContent());

    assertThat(estaRetirada()).isTrue();
    mvc.perform(efectiva(vendedora, producto)).andExpect(jsonPath("$.outcome").value("SIN_TARIFA"));

    // Y la ruta de desasociar YA NO EXISTE.
    mvc.perform(
            post("/api/v1/commission-rates/" + tasa + "/products/" + producto + "/deletion")
                .with(
                    user(SUPERADMIN.toString())
                        .authorities(
                            () -> "commissions:update", () -> "user-commission-rates:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"deja de comisionar\"}"))
        .andExpect(status().isNotFound());
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
  // Corregir la FORMA (`cm.md` v0.7.0)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-CM-090 · corrige de porcentaje a IMPORTE FIJO, y la tasa queda en importe fijo")
  void cambiaLaForma() throws Exception {
    mvc.perform(correccion(tasa, "{\"rateType\":\"FIJO\",\"fixedAmount\":500}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rateType").value("FIJO"))
        .andExpect(jsonPath("$.fixedAmount").value(500))
        .andExpect(jsonPath("$.percentage").value(org.hamcrest.Matchers.nullValue()));

    assertThat(formaEnBase()).isEqualTo("FIJO");
    assertThat(porcentajeEnBase()).isNull();
  }

  @Test
  @DisplayName("CA-CM-091 · `10 %` → `10` FIJO ES UN CAMBIO, aunque las cifras comparen iguales")
  void laMismaCifraEnLaOtraFormaSiEsUnCambio() throws Exception {
    // EL CRITERIO MÁS IMPORTANTE DE LOS SEIS, Y EL ÚNICO QUE PUEDE FALLAR EN
    // SILENCIO. La tasa vale 10.00 %; se corrige a 10 de importe fijo. Los dos
    // números comparan iguales, de modo que una comparación que mirara solo la
    // cifra —que es como estaba escrita, y con razón, por `FA-002`— concluiría
    // que no hubo cambio: DEVOLVERÍA 200 sin escribir, sin auditar y sin mover
    // la marca de modificación, y la tasa seguiría pagando el 10 %.
    //
    // Va en pareja con `correccionQueNoCambiaNada`, que usa los mismos números y
    // espera lo contrario. Cualquiera de las dos se satisface rompiendo la otra.
    var antes = actualizadaEn();

    mvc.perform(correccion(tasa, "{\"rateType\":\"FIJO\",\"fixedAmount\":10.00}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rateType").value("FIJO"));

    assertThat(formaEnBase()).isEqualTo("FIJO");
    assertThat(actualizadaEn()).isNotEqualTo(antes);
  }

  @Test
  @DisplayName("CA-CM-092 · el evento lleva la FORMA anterior y la nueva, no solo los números")
  void laAuditoriaGuardaLaForma() throws Exception {
    mvc.perform(correccion(tasa, "{\"rateType\":\"FIJO\",\"fixedAmount\":500}"))
        .andExpect(status().isOk());

    // Un `before` que dijera «10.00» sin decir que era un PORCENTAJE no conserva
    // nada: quien lo lea dentro de un año no podrá saber si esa tasa pagaba una
    // décima parte de la venta o diez unidades de dinero. Y como esta tabla no
    // tiene vigencia, este registro es la única copia que queda.
    String cambio =
        jdbc.queryForObject(
            "SELECT CAST(changes AS text) FROM audit_change_log WHERE entity = 'commission_rates'"
                + " AND action = 'UPDATE' ORDER BY occurred_at DESC LIMIT 1",
            String.class);

    assertThat(cambio).contains("PORCENTAJE 10.00").contains("FIJO 500");
  }

  @Test
  @DisplayName("CA-CM-093 · el valor SIN la forma se rechaza, y la tasa queda intacta")
  void elValorSinLaForma() throws Exception {
    // Un importe suelto sobre una tasa de porcentaje puede ser «cámbiala a
    // importe fijo» o «me equivoqué de campo», y las dos peticiones se escriben
    // igual. No se deduce.
    mvc.perform(correccion(tasa, "{\"fixedAmount\":10000}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-011"));

    assertThat(formaEnBase()).isEqualTo("PORCENTAJE");
    assertThat(porcentajeEnBase()).isEqualByComparingTo("10.00");
  }

  @Test
  @DisplayName("CA-CM-094 · `150` se RECHAZA en porcentaje y se ACEPTA en importe fijo")
  void elTopeSoloExisteEnUnaDeLasDosFormas() throws Exception {
    mvc.perform(correccion(tasa, "{\"rateType\":\"PORCENTAJE\",\"percentage\":150}"))
        .andExpect(status().isBadRequest());

    // La misma cifra, la otra forma. `RN-CM-018`: cien es un límite que el
    // negocio conoce sin mirar nada; para el importe NO EXISTE ESE NÚMERO.
    mvc.perform(correccion(tasa, "{\"rateType\":\"FIJO\",\"fixedAmount\":150}"))
        .andExpect(status().isOk());

    assertThat(formaEnBase()).isEqualTo("FIJO");
  }

  // ---------------------------------------------------------------------------
  // `RN-CM-019` — el tope de cien al corregir (`cm.md` v0.8.0)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-CM-110 · corregir dentro del tope de su producto se admite con normalidad")
  void corregirDentroDelTope() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "30.00");

    // 60 + 30 = 90: cabe.
    mvc.perform(correccion(tasa, "{\"rateType\":\"PORCENTAJE\",\"percentage\":60.00}"))
        .andExpect(status().isOk());

    assertThat(porcentajeEnBase()).isEqualByComparingTo("60.00");
  }

  @Test
  @DisplayName("CA-CM-111 · corregir a un valor que pasaría de cien su producto SE RECHAZA")
  void corregirQuePasaDeCienSeRechaza() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "30.00");

    // La tasa vale 10.00; corregirla a 71 dejaría 71 + 30 = 101.
    mvc.perform(correccion(tasa, "{\"rateType\":\"PORCENTAJE\",\"percentage\":71.00}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));

    assertThat(porcentajeEnBase()).isEqualByComparingTo("10.00");
  }

  @Test
  @DisplayName(
      "CA-CM-142 · el tope se revisa contra SU producto y no contra otros: la misma cifra pasa o no"
          + " según dónde rija — reescrito el 15-09-2026")
  void elTopeEsElDeSuProducto() throws Exception {
    // Hasta el 15-09-2026 (`CA-CM-112`) una tasa regía sobre varios productos
    // y la corrección se rechazaba entera si CUALQUIERA se pasaba. Hoy una tasa
    // tiene un producto: la de BOT_A convive con un DIRECTOR de 30 y cabe hasta
    // 70; la de BOT_3, con un AGENTE de 49, solo hasta 51.
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "30.00");
    UUID p3 = CommissionFixtures.sembrarProducto(jdbc, "BOT_3");
    UUID enP3 = CommissionFixtures.sembrarTasaDeRol(jdbc, p3, MANAGER, "10.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, p3, AGENTE, "49.00");

    // 52 cabe en BOT_A (52 + 30 = 82) y no en BOT_3 (52 + 49 = 101).
    mvc.perform(correccion(tasa, "{\"rateType\":\"PORCENTAJE\",\"percentage\":52.00}"))
        .andExpect(status().isOk());
    mvc.perform(correccion(enP3, "{\"rateType\":\"PORCENTAJE\",\"percentage\":52.00}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));

    assertThat(porcentajeEnBase()).isEqualByComparingTo("52.00");
  }

  @Test
  @DisplayName(
      "CA-CM-113 · el valor fijo entra en la suma convertido contra el precio del producto")
  void corregirAValorFijoSeConvierteContraElPrecio() throws Exception {
    // El producto vale 1000; el DIRECTOR ocupa 50.
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "50.00");

    // 400 / 1000 * 100 = 40 (+ 50 = 90). Cabe.
    mvc.perform(correccion(tasa, "{\"rateType\":\"FIJO\",\"fixedAmount\":400}"))
        .andExpect(status().isOk());
    assertThat(fixedAmountEnBase()).isEqualByComparingTo("400");

    // 600 / 1000 * 100 = 60 (+ 50 = 110): se pasa de cien.
    mvc.perform(correccion(tasa, "{\"rateType\":\"FIJO\",\"fixedAmount\":600}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));
    assertThat(fixedAmountEnBase()).isEqualByComparingTo("400");
  }

  @Test
  @DisplayName(
      "CA-CM-117 · sobre un producto de PRECIO CERO, corregir a valor fijo se ADMITE sin tope y"
          + " corregir a porcentaje se rechaza — reescrito el 14-09-2026")
  void corregirConProductoGratuito() throws Exception {
    // Del 08-09-2026 al 14-09-2026 esta prueba decía justo lo contrario
    // (`RN-CM-020`, cm.md v0.13.0): un producto gratuito existe para captar,
    // y quien lo coloca cobra por colocarlo — con un importe, porque un
    // porcentaje de cero es cero.
    UUID gratis = CommissionFixtures.sembrarProducto(jdbc, "BOT_GRATIS", false, "0.0000");
    UUID enGratis = CommissionFixtures.sembrarTasaDeRol(jdbc, gratis, MANAGER, "FIJO", "1.00");

    mvc.perform(correccion(enGratis, "{\"rateType\":\"FIJO\",\"fixedAmount\":75000}"))
        .andExpect(status().isOk());

    // A porcentaje se rechaza, como el tope: sobre un gratuito no cabe.
    mvc.perform(correccion(enGratis, "{\"rateType\":\"PORCENTAJE\",\"percentage\":10}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-008"));

    assertThat(
            jdbc.queryForObject(
                "SELECT fixed_amount FROM commission_rates WHERE id = CAST(? AS uuid)",
                java.math.BigDecimal.class,
                enGratis.toString()))
        .isEqualByComparingTo("75000");
  }

  @Test
  @DisplayName("CA-CM-114 · la única tasa de su producto solo compara consigo misma al corregir")
  void laUnicaTasaDelProductoSoloSeComparaConsigoMisma() throws Exception {
    // Hasta el 15-09-2026 decía «una tasa SIN asociaciones no comprueba ningún
    // tope»; hoy siempre hay un producto, y con ella sola dentro el tope es el
    // cien por cien de ese producto: 99.99 % cabe, y en fijo, el precio entero.
    mvc.perform(correccion(tasa, "{\"rateType\":\"PORCENTAJE\",\"percentage\":99.99}"))
        .andExpect(status().isOk());

    mvc.perform(correccion(tasa, "{\"rateType\":\"FIJO\",\"fixedAmount\":1000}"))
        .andExpect(status().isOk());

    mvc.perform(correccion(tasa, "{\"rateType\":\"FIJO\",\"fixedAmount\":1000.01}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));
  }

  @Test
  @DisplayName(
      "CA-CM-142 · un importe fijo con más decimales que la moneda del producto se rechaza al"
          + " corregir (VAL-013)")
  void losDecimalesDeLaMonedaAlCorregir() throws Exception {
    int decimales =
        jdbc.queryForObject(
            "SELECT c.decimal_places FROM products p JOIN currencies c ON c.id = p.currency_id"
                + " WHERE p.id = CAST(? AS uuid)",
            Integer.class,
            producto.toString());
    String deMas = "1." + "0".repeat(decimales) + "1";

    mvc.perform(correccion(tasa, "{\"rateType\":\"FIJO\",\"fixedAmount\":" + deMas + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-013"))
        .andExpect(jsonPath("$.errors[0].field").value("fixedAmount"));

    assertThat(formaEnBase()).isEqualTo("PORCENTAJE");
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  private String formaEnBase() {
    return jdbc.queryForObject(
        "SELECT rate_type FROM commission_rates WHERE id = CAST(? AS uuid)",
        String.class,
        tasa.toString());
  }

  private MockHttpServletRequestBuilder correccion(UUID id, String json) {
    return patch("/api/v1/commission-rates/" + id)
        .with(
            user(SUPERADMIN.toString())
                .authorities(() -> "commissions:update", () -> "user-commission-rates:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  private MockHttpServletRequestBuilder retiro(UUID id, String motivo) {
    return post("/api/v1/commission-rates/" + id + "/deletion")
        .with(
            user(SUPERADMIN.toString())
                .authorities(() -> "commissions:delete", () -> "user-commission-rates:delete"))
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

  private java.math.BigDecimal fixedAmountEnBase() {
    return jdbc.queryForObject(
        "SELECT fixed_amount FROM commission_rates WHERE id = CAST(? AS uuid)",
        java.math.BigDecimal.class,
        tasa.toString());
  }

  private String rolEnBase() {
    return jdbc.queryForObject(
        "SELECT CAST(role_id AS text) FROM commission_rates WHERE id = CAST(? AS uuid)",
        String.class,
        tasa.toString());
  }

  private String productoEnBase() {
    return jdbc.queryForObject(
        "SELECT CAST(product_id AS text) FROM commission_rates WHERE id = CAST(? AS uuid)",
        String.class,
        tasa.toString());
  }

  private MockHttpServletRequestBuilder efectiva(UUID persona, UUID producto) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
            "/api/v1/commissions/effective")
        .param("userId", persona.toString())
        .param("productId", producto.toString())
        .param("onDate", "2026-09-15")
        .with(
            user(SUPERADMIN.toString())
                .authorities(
                    () -> "commissions:read",
                    () -> "commissions:read-effective",
                    () -> "user-commission-rates:read",
                    () -> "product-commission-rates:read"));
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
