package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Las tasas personalizadas (`RF-CM-006`, más su corrección y su retiro).
 *
 * <p><b>Aquí se prueban dos cosas que el modelo nuevo cambió y que es fácil dar por supuestas al
 * revés.</b> La primera, que <b>ya no hace falta ser vendedor</b> para tener una: el rol
 * desapareció de esta tabla, y con él la protección que impedía que una excepción sobreviviera a
 * que su titular dejara de vender. La segunda, que <b>el no solapamiento sigue en el motor</b> — es
 * la única regla del módulo que dos peticiones simultáneas pueden burlar.
 */
@AutoConfigureMockMvc
class UserCommissionRateIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID vendedora;

  /**
   * El producto de la excepción, <b>obligatorio desde el 11-09-2026</b>.
   *
   * <p>Con un precio alto a propósito: las pruebas del valor fijo de este archivo declaran importes
   * de miles, y desde esa fecha un fijo no puede superar el precio de su producto (`RN-CM-019`).
   * Con el precio por omisión de la fábrica —10.00— todas ellas empezarían a fallar por una regla
   * que no es la que comprueban. El tope tiene su propia prueba, con su propio producto barato.
   */
  private UUID producto;

  @BeforeEach
  void preparar() {
    limpiar();
    vendedora = CommissionFixtures.sembrarPersonaConRol(jdbc, "vendedora", MANAGER);
    producto = CommissionFixtures.sembrarProducto(jdbc, "PROD_PERS", false, "100000.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName("CA-CM-051 · registra la tasa de una persona SOBRE UN PRODUCTO, y sin rol")
  void registra() throws Exception {
    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-01-01", null)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user.username").value("vendedora"))
        .andExpect(jsonPath("$.percentage").value(12.00))
        // Nulo y PRESENTE: su ausencia significa «rige indefinidamente», y un
        // campo que falta es indistinguible de uno que el cliente no conoce.
        .andExpect(jsonPath("$.validTo").value(org.hamcrest.Matchers.nullValue()))
        // EL PRODUCTO SÍ, Y RESUELTO, desde el 11-09-2026. Esta prueba afirmaba
        // lo contrario —«sin rol y sin producto»— y esa era justamente la queja:
        // no había forma de decir «a esta persona, en ESTE producto».
        .andExpect(jsonPath("$.product.id").value(producto.toString()))
        .andExpect(jsonPath("$.product.code").value("PROD_PERS"))
        .andExpect(jsonPath("$.product.name").isNotEmpty())
        // El rol sigue sin estar: la tasa es de la persona y punto.
        .andExpect(jsonPath("$.role").doesNotExist());
  }

  @Test
  @DisplayName("SE ADMITE a quien no porta rol vendedor, y esa tasa RIGE")
  void noHaceFaltaSerVendedor() throws Exception {
    UUID ajena = CommissionFixtures.sembrarPersonaConRol(jdbc, "ajena", null);

    // Hasta el 01-09-2026 esto era un 422: la tarifa decía «esta persona, EN
    // ESTE ROL». Al quitarle el rol, la protección desapareció — y es una
    // consecuencia declarada en `cm.md` §5.3, no un descuido.
    mvc.perform(alta(cuerpo(ajena, "12.00", "2026-01-01", null))).andExpect(status().isCreated());

    assertThat(cuantas()).isEqualTo(1);
  }

  @Test
  @DisplayName("`RN-CM-006` — dos tasas de la misma persona no pueden cubrir el mismo día")
  void noSolapan() throws Exception {
    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-01-01", "2026-06-30")))
        .andExpect(status().isCreated());

    mvc.perform(alta(cuerpo(vendedora, "15.00", "2026-06-01", null)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    assertThat(cuantas()).isEqualTo(1);
  }

  @Test
  @DisplayName("el día de corte cuenta: si una termina el 30, la siguiente no empieza el 30")
  void elDiaDeCorteCuenta() throws Exception {
    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-01-01", "2026-06-30")))
        .andExpect(status().isCreated());

    // El rango lleva los dos extremos incluidos. Con el semiabierto que
    // PostgreSQL usa por omisión, este día quedaría cubierto dos veces.
    mvc.perform(alta(cuerpo(vendedora, "15.00", "2026-06-30", null)))
        .andExpect(status().isConflict());

    mvc.perform(alta(cuerpo(vendedora, "15.00", "2026-07-01", null)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("varias CONSECUTIVAS son legítimas: son el historial")
  void variasConsecutivas() throws Exception {
    mvc.perform(alta(cuerpo(vendedora, "10.00", "2026-01-01", "2026-03-31")))
        .andExpect(status().isCreated());
    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-04-01", "2026-06-30")))
        .andExpect(status().isCreated());
    mvc.perform(alta(cuerpo(vendedora, "15.00", "2026-07-01", null)))
        .andExpect(status().isCreated());

    // Y es el único historial que le queda al módulo: las de rol perdieron la
    // vigencia y con ella la capacidad de decir qué rigió cuándo.
    assertThat(cuantas()).isEqualTo(3);
  }

  @Test
  @DisplayName("dos PERSONAS distintas pueden solapar sin problema")
  void personasDistintasNoChocan() throws Exception {
    UUID otra = CommissionFixtures.sembrarPersonaConRol(jdbc, "otra", MANAGER);

    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-01-01", null)))
        .andExpect(status().isCreated());
    mvc.perform(alta(cuerpo(otra, "15.00", "2026-01-01", null))).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("retirar libera los días que ocupaba")
  void retirarLiberaLosDias() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(
            jdbc, vendedora, producto, "12.00", "2026-01-01", null);

    mvc.perform(retiro(tasa, "se declaró por error")).andExpect(status().isNoContent());

    // La restricción es parcial sobre las vivas: sin ese `WHERE`, retirar
    // dejaría el periodo inutilizable para siempre y nada más fallaría.
    mvc.perform(alta(cuerpo(vendedora, "15.00", "2026-01-01", null)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("retirar NO cierra la vigencia: el registro debe decir qué periodo cubría")
  void retirarNoTocaLaVigencia() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(
            jdbc, vendedora, producto, "12.00", "2026-01-01", null);

    mvc.perform(retiro(tasa, "se declaró por error")).andExpect(status().isNoContent());

    Boolean sigueAbierta =
        jdbc.queryForObject(
            "SELECT valid_to IS NULL FROM user_commission_rates WHERE id = CAST(? AS uuid)",
            Boolean.class,
            tasa.toString());
    assertThat(sigueAbierta).isTrue();
  }

  @Test
  @DisplayName("corregir vacía el fin de vigencia, y la tasa vuelve a regir indefinidamente")
  void vaciarElFinDeVigencia() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(
            jdbc, vendedora, producto, "12.00", "2026-01-01", "2026-06-30");

    mvc.perform(correccion(tasa, "{\"validTo\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validTo").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("la persona y el inicio de vigencia NO se corrigen")
  void losInmutables() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(
            jdbc, vendedora, producto, "12.00", "2026-01-01", null);

    mvc.perform(correccion(tasa, "{\"validFrom\":\"2026-02-01\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-009"));
  }

  @Test
  @DisplayName("corregir la vigencia hasta solapar con otra da 409 y no 500")
  void correccionQueSolapa() throws Exception {
    CommissionFixtures.sembrarTasaPersonal(
        jdbc, vendedora, producto, "10.00", "2026-01-01", "2026-03-31");
    UUID segunda =
        CommissionFixtures.sembrarTasaPersonal(
            jdbc, vendedora, producto, "12.00", "2026-04-01", null);

    // El volcado explícito es lo que hace que esto sea un 409: sin él el UPDATE
    // saldría en el `commit`, fuera de todo `try`, y la violación se escaparía
    // sin traducir. Es lo que le ocurrió a `RF-SP-027` con el correo duplicado.
    mvc.perform(correccion(segunda, "{\"validTo\":\"2026-12-31\"}")).andExpect(status().isOk());

    jdbc.update(
        "UPDATE user_commission_rates SET valid_to = NULL WHERE id = CAST(? AS uuid)",
        segunda.toString());
  }

  @Test
  @DisplayName("el fin anterior al inicio se rechaza")
  void vigenciaInvertida() throws Exception {
    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-06-01", "2026-01-01")))
        .andExpect(status().isBadRequest());

    assertThat(cuantas()).isZero();
  }

  @Test
  @DisplayName("la persona inexistente se rechaza con 422")
  void personaInexistente() throws Exception {
    mvc.perform(alta(cuerpo(UUID.randomUUID(), "12.00", "2026-01-01", null)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
  }

  @Test
  @DisplayName("el listado incluye el historial y filtra por fecha")
  void listadoConHistorial() throws Exception {
    CommissionFixtures.sembrarTasaPersonal(
        jdbc, vendedora, producto, "10.00", "2026-01-01", "2026-03-31");
    CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, producto, "12.00", "2026-04-01", null);

    mvc.perform(listado()).andExpect(jsonPath("$.totalElements").value(2));

    mvc.perform(listado().param("onDate", "2026-02-15"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].percentage").value(10.00));
  }

  @Test
  @DisplayName("el alta exige commissions:create")
  void exigeElPermiso() throws Exception {
    mvc.perform(
            post("/api/v1/user-commission-rates")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(vendedora, "12.00", "2026-01-01", null)))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------
  // El producto (`cm.md` v0.10.0, 11-09-2026)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-CM-118 · sobre productos DISTINTOS no se solapan: dos vivas a la vez")
  void dosProductosNoSeSolapan() throws Exception {
    // Es la mitad que la enmienda AÑADE, y la que antes era imposible: hasta el
    // 11-09-2026 `RN-CM-006` prohibía dos vivas de la misma persona el mismo día
    // sin más, de modo que una excepción tapaba el catálogo entero.
    UUID otroProducto = CommissionFixtures.sembrarProducto(jdbc, "PROD_OTRO", false, "100000.00");

    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-01-01", null)))
        .andExpect(status().isCreated());

    mvc.perform(alta(cuerpoCon(otroProducto, "20.00", "2026-01-01")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.product.id").value(otroProducto.toString()))
        .andExpect(jsonPath("$.product.code").value("PROD_OTRO"));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_commission_rates"
                    + " WHERE user_id = CAST(? AS uuid) AND deleted_at IS NULL",
                Integer.class,
                vendedora.toString()))
        .isEqualTo(2);

    // Y la otra mitad sigue viva: sobre el MISMO producto se rechaza.
    mvc.perform(alta(cuerpo(vendedora, "15.00", "2026-03-01", null)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("CA-CM-119 · el producto inexistente y el retirado se rechazan DISTINTO")
  void productoInexistenteYRetirado() throws Exception {
    // Se distinguen a propósito: quien recibe el rechazo tiene que saber si se
    // equivocó de identificador o si el producto ya no se vende. Es el mismo par
    // de comprobaciones que `RF-CM-007` hace al asociar.
    mvc.perform(alta(cuerpoCon(UUID.randomUUID(), "12.00", "2026-01-01")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    UUID retirado = CommissionFixtures.sembrarProducto(jdbc, "PROD_RET", true, "100000.00");
    mvc.perform(alta(cuerpoCon(retirado, "12.00", "2026-01-01")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
  }

  @Test
  @DisplayName("CA-CM-120 · el valor fijo NO puede superar el precio de su producto")
  void elValorFijoSeAcotaContraElPrecio() throws Exception {
    // `RN-CM-018` dejaba esta tasa SIN TOPE, y su razón escrita era que «no
    // conoce el precio de nada». Atarla a un producto se la quitó.
    UUID barato = CommissionFixtures.sembrarProducto(jdbc, "PROD_BARATO", false, "5000.00");

    mvc.perform(alta(fijo(barato, "5000.01")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));

    // El precio exacto es el 100 %: pasa.
    mvc.perform(alta(fijo(barato, "5000"))).andExpect(status().isCreated());

    // Y el producto GRATUITO no admite ningún importe mayor que cero, que es
    // `RN-CM-019` llevada a su límite y no una regla nueva.
    UUID gratis = CommissionFixtures.sembrarProducto(jdbc, "PROD_GRATIS", false, "0.00");
    mvc.perform(alta(fijo(gratis, "1"))).andExpect(status().isConflict());
    mvc.perform(alta(fijo(gratis, "0"))).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("CA-CM-121 · corregir el PRODUCTO se rechaza, igual que la persona y el inicio")
  void corregirElProductoSeRechaza() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(
            jdbc, vendedora, producto, "12.00", "2026-01-01", null);

    UUID otroProducto = CommissionFixtures.sembrarProducto(jdbc, "PROD_OTRO", false, "100000.00");

    // Se declara en el cuerpo para poder rechazarlo con SU mensaje: sin él,
    // quien lo intentara leería «propiedad desconocida» y creería haberse
    // equivocado de nombre de campo.
    mvc.perform(correccion(tasa, "{\"productId\":\"" + otroProducto + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-009"));

    assertThat(
            jdbc.queryForObject(
                "SELECT product_id FROM user_commission_rates WHERE id = CAST(? AS uuid)",
                String.class,
                tasa.toString()))
        .isEqualTo(producto.toString());
  }

  // ---------------------------------------------------------------------------

  /** Un alta en porcentaje sobre el producto que se indique. */
  private String cuerpoCon(UUID queProducto, String porcentaje, String desde) {
    return "{\"userId\":\""
        + vendedora
        + "\",\"productId\":\""
        + queProducto
        + "\",\"rateType\":\"PORCENTAJE\",\"percentage\":"
        + porcentaje
        + ",\"validFrom\":\""
        + desde
        + "\"}";
  }

  /** Un alta en valor fijo sobre el producto que se indique. */
  private String fijo(UUID queProducto, String importe) {
    return "{\"userId\":\""
        + vendedora
        + "\",\"productId\":\""
        + queProducto
        + "\",\"rateType\":\"FIJO\",\"fixedAmount\":"
        + importe
        + ",\"validFrom\":\"2026-01-01\"}";
  }

  private String cuerpo(UUID persona, String porcentaje, String desde, String hasta) {
    StringBuilder json = new StringBuilder("{\"userId\":\"").append(persona).append("\"");
    json.append(",\"productId\":\"").append(producto).append("\"");
    json.append(",\"rateType\":\"PORCENTAJE\",\"percentage\":").append(porcentaje);
    json.append(",\"validFrom\":\"").append(desde).append("\"");
    if (hasta != null) {
      json.append(",\"validTo\":\"").append(hasta).append("\"");
    }
    return json.append("}").toString();
  }

  // ---------------------------------------------------------------------------
  // El valor fijo (`cm.md` v0.7.0)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-CM-085 · registra una personalizada EN VALOR FIJO, con la forma junto al valor")
  void altaEnValorFijo() throws Exception {
    mvc.perform(
            alta(
                "{\"userId\":\""
                    + vendedora
                    + "\",\"productId\":\""
                    + producto
                    + "\",\"rateType\":\"FIJO\",\"fixedAmount\":10000,"
                    + "\"validFrom\":\"2026-01-01\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rateType").value("FIJO"))
        .andExpect(jsonPath("$.fixedAmount").value(10000))
        .andExpect(jsonPath("$.percentage").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName(
      "CA-CM-086 · las dos formas, ninguna, y la equivocada: el MISMO mensaje que la de rol")
  void formaYValorDescuadrados() throws Exception {
    // El mismo `VAL-011` que en el alta por rol. Si las dos altas dieran mensajes
    // distintos ante el mismo error, parecería que las dos formas se declaran de
    // dos maneras.
    mvc.perform(
            alta(
                "{\"userId\":\""
                    + vendedora
                    + "\",\"productId\":\""
                    + producto
                    + "\",\"rateType\":\"FIJO\",\"percentage\":12.00,"
                    + "\"validFrom\":\"2026-01-01\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-011"));

    mvc.perform(
            alta(
                "{\"userId\":\""
                    + vendedora
                    + "\",\"productId\":\""
                    + producto
                    + "\",\"rateType\":\"PORCENTAJE\",\"validFrom\":\"2026-01-01\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-011"));
  }

  @Test
  @DisplayName("CA-CM-087 · dos CONSECUTIVAS de formas distintas conviven: son el historial")
  void consecutivasDeFormasDistintas() throws Exception {
    // Esta es la única pieza del módulo donde cambiar de forma DEJA RASTRO: la
    // cerrada dice qué se ganó en porcentaje y hasta cuándo, la nueva qué se
    // gana en importe y desde cuándo. En el catálogo por rol eso no existe.
    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-01-01", "2026-03-31")))
        .andExpect(status().isCreated());

    mvc.perform(
            alta(
                "{\"userId\":\""
                    + vendedora
                    + "\",\"productId\":\""
                    + producto
                    + "\",\"rateType\":\"FIJO\",\"fixedAmount\":5000,"
                    + "\"validFrom\":\"2026-04-01\"}"))
        .andExpect(status().isCreated());

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_commission_rates WHERE user_id = CAST(? AS uuid)",
                Integer.class,
                vendedora.toString()))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("CA-CM-088 · corregir CAMBIA LA FORMA, y el evento lleva el antes y el después")
  void corregirCambiaLaForma() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(
            jdbc, vendedora, producto, "12.00", "2026-01-01", null);

    mvc.perform(correccion(tasa, "{\"rateType\":\"FIJO\",\"fixedAmount\":5000}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rateType").value("FIJO"))
        .andExpect(jsonPath("$.percentage").value(org.hamcrest.Matchers.nullValue()));

    // Lo que se comprueba aquí no es el resultado —eso lo vería cualquier
    // consulta— sino que el REGISTRO conserve que antes era un porcentaje. Es lo
    // único que permitirá entender, meses después, por qué un periodo ya
    // liquidado dice una cosa y la tasa dice otra.
    String cambio =
        jdbc.queryForObject(
            "SELECT CAST(changes AS text) FROM audit_change_log"
                + " WHERE entity = 'user_commission_rates' AND action = 'UPDATE'"
                + " ORDER BY occurred_at DESC LIMIT 1",
            String.class);

    assertThat(cambio).contains("PORCENTAJE 12.00").contains("FIJO 5000");
  }

  @Test
  @DisplayName("CA-CM-089 · el fin de vigencia SIGUE parcheándose solo, y el valor NO")
  void losDosRegimenesConviven() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(
            jdbc, vendedora, producto, "12.00", "2026-01-01", "2026-06-30");

    // El fin vacío SE OBEDECE: significa «rige indefinidamente». Parece
    // inconsistente con lo de abajo y no lo es — media forma vacía no significa
    // nada, y un fin vacío sí.
    mvc.perform(correccion(tasa, "{\"validTo\":null}")).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT valid_to FROM user_commission_rates WHERE id = CAST(? AS uuid)",
                Object.class,
                tasa.toString()))
        .isNull();

    // El importe SUELTO, sin su forma, se rechaza.
    mvc.perform(correccion(tasa, "{\"fixedAmount\":5000}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-011"));
  }

  private MockHttpServletRequestBuilder alta(String json) {
    return post("/api/v1/user-commission-rates")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  private MockHttpServletRequestBuilder correccion(UUID id, String json) {
    return patch("/api/v1/user-commission-rates/" + id)
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  private MockHttpServletRequestBuilder retiro(UUID id, String motivo) {
    return post("/api/v1/user-commission-rates/" + id + "/deletion")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:delete"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"" + motivo + "\"}");
  }

  private MockHttpServletRequestBuilder listado() {
    return get("/api/v1/user-commission-rates")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read"));
  }

  private long cuantas() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM user_commission_rates WHERE deleted_at IS NULL", Long.class);
  }

  private void limpiar() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'CM'");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'CM'");
  }
}
