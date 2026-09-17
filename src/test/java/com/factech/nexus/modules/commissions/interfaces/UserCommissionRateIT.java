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
 * <p><b>Desde el 16-09-2026 la tasa nace con su producto</b> (`RN-CM-021`): una vigente por
 * persona, producto y día. Todo lo que del 11-09-2026 al 16-09-2026 se probaba al asociar se prueba
 * aquí al registrar, y `RN-CM-006` <b>vuelve al motor</b>: la prueba concurrente que lo verifica
 * está en {@code CommissionRateConcurrencyIT}.
 *
 * <p>Y sigue lo que el modelo del 01-09-2026 cambió y es fácil dar por supuesto al revés: <b>ya no
 * hace falta ser vendedor</b> para tener una, porque el rol desapareció de esta tabla y con él la
 * protección que impedía que una excepción sobreviviera a que su titular dejara de vender.
 */
@AutoConfigureMockMvc
class UserCommissionRateIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID vendedora;
  private UUID producto;

  @BeforeEach
  void preparar() {
    limpiar();
    vendedora = CommissionFixtures.sembrarPersonaConRol(jdbc, "vendedora", MANAGER);
    producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_A", false, "1000.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // El alta con producto (`CA-CM-146` a `CA-CM-149`, 16-09-2026)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-CM-146 · registra la tasa CON su producto, sin rol, y RIGE desde su inicio")
  void registraConSuProducto() throws Exception {
    mvc.perform(alta(cuerpo(vendedora, producto, "12.00", "2026-01-01", null)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user.username").value("vendedora"))
        .andExpect(jsonPath("$.product.id").value(producto.toString()))
        .andExpect(jsonPath("$.product.code").value("BOT_A"))
        .andExpect(jsonPath("$.product.name").value("Producto BOT_A"))
        .andExpect(jsonPath("$.product.price").value(1000.00))
        .andExpect(jsonPath("$.product.currency.code").isNotEmpty())
        .andExpect(jsonPath("$.product.currency.decimalPlaces").isNumber())
        .andExpect(jsonPath("$.percentage").value(12.00))
        // Nulo y PRESENTE: su ausencia significa «rige indefinidamente», y un
        // campo que falta es indistinguible de uno que el cliente no conoce.
        .andExpect(jsonPath("$.validTo").value(org.hamcrest.Matchers.nullValue()))
        // Sin rol: la tasa es de la persona.
        .andExpect(jsonPath("$.role").doesNotExist())
        .andExpect(jsonPath("$.associatedProducts").doesNotExist());

    // ESTA ES LA PRUEBA: sin ningún paso más, la vendedora ya cobra por él.
    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("RESUELTA"))
        .andExpect(jsonPath("$.source").value("PERSONALIZADA"))
        .andExpect(jsonPath("$.value").value(12.00));
  }

  @Test
  @DisplayName("CA-CM-146 · sin producto se rechaza con VAL-013, y nada se escribe")
  void productoObligatorio() throws Exception {
    mvc.perform(
            alta(
                "{\"userId\":\""
                    + vendedora
                    + "\",\"rateType\":\"PORCENTAJE\",\"percentage\":12.00,"
                    + "\"validFrom\":\"2026-01-01\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-013"))
        .andExpect(jsonPath("$.errors[0].field").value("productId"));

    assertThat(cuantas()).isZero();
  }

  @Test
  @DisplayName("SE ADMITE a quien no porta rol vendedor, y esa tasa RIGE")
  void noHaceFaltaSerVendedor() throws Exception {
    UUID ajena = CommissionFixtures.sembrarPersonaConRol(jdbc, "ajena", null);

    // Hasta el 01-09-2026 esto era un 422: la tarifa decía «esta persona, EN
    // ESTE ROL». Al quitarle el rol, la protección desapareció — y es una
    // consecuencia declarada en `cm.md` §5.3, no un descuido.
    mvc.perform(alta(cuerpo(ajena, producto, "12.00", "2026-01-01", null)))
        .andExpect(status().isCreated());

    mvc.perform(efectiva(ajena, producto, "2026-05-01"))
        .andExpect(jsonPath("$.source").value("PERSONALIZADA"));
  }

  @Test
  @DisplayName(
      "CA-CM-054 · `RN-CM-006` — dos tasas de la misma persona no cubren el mismo día EN UN PRODUCTO")
  void noSolapanEnElMismoProducto() throws Exception {
    // Del 11-09-2026 al 16-09-2026 la regla se comprobaba al asociar y las dos
    // altas pasaban; hoy vuelve a ser del alta, y del motor.
    mvc.perform(alta(cuerpo(vendedora, producto, "12.00", "2026-01-01", "2026-06-30")))
        .andExpect(status().isCreated());

    mvc.perform(alta(cuerpo(vendedora, producto, "15.00", "2026-06-01", null)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));

    // Sobre OTRO producto la misma segunda tasa entra sin problema: dos
    // excepciones simultáneas de la misma persona son legítimas mientras hablen
    // de productos distintos.
    UUID otro = CommissionFixtures.sembrarProducto(jdbc, "BOT_SOL2");
    mvc.perform(alta(cuerpo(vendedora, otro, "15.00", "2026-06-01", null)))
        .andExpect(status().isCreated());

    assertThat(cuantas()).isEqualTo(2);
  }

  @Test
  @DisplayName("CA-CM-147 · `V10` — el EXCLUDE del motor rechaza lo que el caso de uso no mire")
  void elMotorSostieneLaRegla() throws Exception {
    // La prueba concurrente está en `CommissionRateConcurrencyIT`; esta es la
    // secuencial que delata que la restricción exista: un INSERT directo, sin
    // pasar por el caso de uso, tiene que fallar.
    CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, producto, "12.00", "2026-01-01", null);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                CommissionFixtures.sembrarTasaPersonal(
                    jdbc, vendedora, producto, "15.00", "2026-06-01", null))
        .isInstanceOf(org.springframework.dao.DataAccessException.class)
        .hasMessageContaining("uq_user_commission_rates_vigente");

    // Y una retirada NO estorba: la restricción es parcial sobre las vivas.
    jdbc.update(
        "UPDATE user_commission_rates SET deleted_at = now() WHERE user_id = CAST(? AS uuid)",
        vendedora.toString());
    CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, producto, "15.00", "2026-06-01", null);
  }

  @Test
  @DisplayName("el día de corte cuenta: si una termina el 30, la siguiente no empieza el 30")
  void elDiaDeCorteCuenta() throws Exception {
    mvc.perform(alta(cuerpo(vendedora, producto, "12.00", "2026-01-01", "2026-06-30")))
        .andExpect(status().isCreated());

    // El rango lleva los dos extremos incluidos: es LA MISMA expresión que el
    // `EXCLUDE`, para que la consulta previa y el motor digan lo mismo.
    mvc.perform(alta(cuerpo(vendedora, producto, "15.00", "2026-06-30", null)))
        .andExpect(status().isConflict());
    mvc.perform(alta(cuerpo(vendedora, producto, "15.00", "2026-07-01", null)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("varias CONSECUTIVAS sobre el mismo producto son legítimas: son el historial")
  void variasConsecutivas() throws Exception {
    mvc.perform(alta(cuerpo(vendedora, producto, "10.00", "2026-01-01", "2026-03-31")))
        .andExpect(status().isCreated());
    mvc.perform(alta(cuerpo(vendedora, producto, "12.00", "2026-04-01", "2026-06-30")))
        .andExpect(status().isCreated());
    mvc.perform(alta(cuerpo(vendedora, producto, "15.00", "2026-07-01", null)))
        .andExpect(status().isCreated());

    // Y es el único historial que le queda al módulo: las de rol perdieron la
    // vigencia y con ella la capacidad de decir qué rigió cuándo.
    assertThat(cuantas()).isEqualTo(3);
  }

  @Test
  @DisplayName("dos PERSONAS distintas pueden solapar sobre el mismo producto sin problema")
  void personasDistintasNoChocan() throws Exception {
    UUID otra = CommissionFixtures.sembrarPersonaConRol(jdbc, "otra", MANAGER);

    mvc.perform(alta(cuerpo(vendedora, producto, "12.00", "2026-01-01", null)))
        .andExpect(status().isCreated());
    mvc.perform(alta(cuerpo(otra, producto, "15.00", "2026-01-01", null)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "CA-CM-119 · el producto inexistente y el retirado se rechazan DISTINTO, y los dos 422")
  void productoInexistenteYRetirado() throws Exception {
    // Se distinguen a propósito: quien recibe el rechazo tiene que saber si se
    // equivocó de identificador o si el producto ya no se vende.
    mvc.perform(alta(cuerpo(vendedora, UUID.randomUUID(), "12.00", "2026-01-01", null)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    UUID retirado = CommissionFixtures.sembrarProducto(jdbc, "BOT_RET", true);
    mvc.perform(alta(cuerpo(vendedora, retirado, "12.00", "2026-01-01", null)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));

    assertThat(cuantas()).isZero();
  }

  @Test
  @DisplayName("CA-CM-120 · al registrar, el valor fijo NO puede superar el precio del producto")
  void elValorFijoSeAcotaAlRegistrar() throws Exception {
    // `RN-CM-018` dejaba esta tasa sin tope «porque no conocía el precio de
    // nada». Hoy lo conoce desde el alta.
    UUID barato = CommissionFixtures.sembrarProducto(jdbc, "BOT_BARATO", false, "5000.00");
    mvc.perform(alta(fijo(vendedora, barato, "5001", "2026-01-01")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-007"));

    // Y en uno caro entra: el MISMO valor, y lo que decide es el producto.
    UUID caro = CommissionFixtures.sembrarProducto(jdbc, "BOT_CARO", false, "100000.00");
    mvc.perform(alta(fijo(vendedora, caro, "5001", "2026-01-01"))).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("CA-CM-134 · sobre un producto de PRECIO CERO, el valor fijo entra sin tope")
  void elValorFijoEntraSinTopeEnElGratuito() throws Exception {
    // `RN-CM-020`: no hay cien por ciento de cero, y el tope individual no
    // aplica a los gratuitos.
    UUID gratis = CommissionFixtures.sembrarProducto(jdbc, "BOT_GRATIS", false, "0.0000");
    mvc.perform(alta(fijo(vendedora, gratis, "250000", "2026-01-01")))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "CA-CM-135 · sobre un producto de PRECIO CERO, el porcentaje se rechaza con EX-008 y el"
          + " mismo porcentaje entra en uno con precio")
  void elPorcentajeNoEntraEnElGratuito() throws Exception {
    UUID gratis = CommissionFixtures.sembrarProducto(jdbc, "BOT_GRATIS", false, "0.0000");
    mvc.perform(alta(cuerpo(vendedora, gratis, "12.00", "2026-01-01", null)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-008"));

    UUID conPrecio = CommissionFixtures.sembrarProducto(jdbc, "BOT_PAGO", false, "100.0000");
    mvc.perform(alta(cuerpo(vendedora, conPrecio, "12.00", "2026-01-01", null)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "CA-CM-148 · un importe con más decimales que la moneda se rechaza, y con los justos entra")
  void losDecimalesDeLaMoneda() throws Exception {
    // Por primera vez la personalizada sabe en qué moneda pagará: la de su
    // producto. Hasta el 16-09-2026 «10.000 fijos» se interpretaban en tantas
    // monedas como productos hubiera (`CA-CM-089`, retirado).
    int decimales =
        jdbc.queryForObject(
            "SELECT c.decimal_places FROM products p JOIN currencies c ON c.id = p.currency_id"
                + " WHERE p.id = CAST(? AS uuid)",
            Integer.class,
            producto.toString());
    String deMas = "1." + "0".repeat(decimales) + "1";
    String justos = decimales == 0 ? "1" : "1." + "0".repeat(decimales - 1) + "1";

    mvc.perform(alta(fijo(vendedora, producto, deMas, "2026-01-01")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-014"))
        .andExpect(jsonPath("$.errors[0].field").value("fixedAmount"));
    assertThat(cuantas()).isZero();

    mvc.perform(alta(fijo(vendedora, producto, justos, "2026-01-01")))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("CA-CM-149 · las rutas de asociación de la personalizada YA NO EXISTEN")
  void lasRutasDeAsociacionSeRetiraron() throws Exception {
    UUID tasa = altaDevuelve(cuerpo(vendedora, producto, "12.00", "2026-01-01", null));
    UUID otro = CommissionFixtures.sembrarProducto(jdbc, "BOT_OTRO");

    mvc.perform(
            post("/api/v1/user-commission-rates/" + tasa + "/products")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + otro + "\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/user-commission-rates/" + tasa + "/products")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read")))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/user-commission-rates/" + tasa + "/products/" + producto + "/deletion")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Ya no aplica\"}"))
        .andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------------------
  // Retirar (`RF-CM-004`)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-CM-152 · se retira SIN CONDICIÓN, y desde ese instante deja de resolver")
  void retirarEsDejarDePagar() throws Exception {
    // Del 11-09-2026 al 16-09-2026 (`CA-CM-125`) una tasa asociada no se
    // retiraba (`RN-CM-015`). Sin asociación no hay nada que sobreviva.
    UUID tasa = altaDevuelve(cuerpo(vendedora, producto, "12.00", "2026-01-01", null));
    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.source").value("PERSONALIZADA"));

    mvc.perform(retiro(tasa, "Se declaró por error")).andExpect(status().isNoContent());

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("SIN_TARIFA"));
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
    mvc.perform(alta(cuerpo(vendedora, producto, "15.00", "2026-01-01", null)))
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

  // ---------------------------------------------------------------------------
  // Corregir (`RF-CM-003`)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("corregir vacía el fin de vigencia, y la tasa vuelve a regir indefinidamente")
  void vaciarElFinDeVigencia() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(
            jdbc, vendedora, producto, "12.00", "2026-01-01", "2026-06-30");

    mvc.perform(correccion(tasa, "{\"validTo\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validTo").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.product.code").value("BOT_A"));
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
  @DisplayName("CA-CM-151 · el PRODUCTO tampoco se corrige: es un campo desconocido, 400")
  void elProductoNoSeCorrige() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(
            jdbc, vendedora, producto, "12.00", "2026-01-01", null);
    UUID otro = CommissionFixtures.sembrarProducto(jdbc, "BOT_OTRO");

    mvc.perform(correccion(tasa, "{\"productId\":\"" + otro + "\"}"))
        .andExpect(status().isBadRequest());

    assertThat(
            jdbc.queryForObject(
                "SELECT CAST(product_id AS text) FROM user_commission_rates"
                    + " WHERE id = CAST(? AS uuid)",
                String.class,
                tasa.toString()))
        .isEqualTo(producto.toString());
  }

  @Test
  @DisplayName("CA-CM-151 · corregir la vigencia hasta solapar con otra del mismo producto da 409")
  void correccionQueSolapa() throws Exception {
    UUID primera =
        CommissionFixtures.sembrarTasaPersonal(
            jdbc, vendedora, producto, "10.00", "2026-01-01", "2026-03-31");
    CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, producto, "12.00", "2026-04-01", null);

    // Alargar la primera hasta pisar a la segunda: 409, y no 500 — la consulta
    // previa lo ve, y si no lo viera, el volcado explícito traduce el EXCLUDE.
    mvc.perform(correccion(primera, "{\"validTo\":\"2026-04-15\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));

    // Sobre OTRO producto la misma persona no estorba.
    UUID otro = CommissionFixtures.sembrarProducto(jdbc, "BOT_OTRO");
    UUID enOtro =
        CommissionFixtures.sembrarTasaPersonal(
            jdbc, vendedora, otro, "10.00", "2026-01-01", "2026-03-31");
    mvc.perform(correccion(enOtro, "{\"validTo\":\"2026-12-31\"}")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("CA-CM-151 · corregir el valor lo revisa contra SU producto: tope y decimales")
  void correccionContraSuProducto() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(
            jdbc, vendedora, producto, "12.00", "2026-01-01", null);

    // El producto vale 1000: 1000 fijos cabe, 1000.01 no (`EX-007`).
    mvc.perform(correccion(tasa, "{\"rateType\":\"FIJO\",\"fixedAmount\":1000}"))
        .andExpect(status().isOk());
    mvc.perform(correccion(tasa, "{\"rateType\":\"FIJO\",\"fixedAmount\":1000.01}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-007"));

    // Y los decimales de la moneda (`VAL-014`).
    int decimales =
        jdbc.queryForObject(
            "SELECT c.decimal_places FROM products p JOIN currencies c ON c.id = p.currency_id"
                + " WHERE p.id = CAST(? AS uuid)",
            Integer.class,
            producto.toString());
    String deMas = "1." + "0".repeat(decimales) + "1";
    mvc.perform(correccion(tasa, "{\"rateType\":\"FIJO\",\"fixedAmount\":" + deMas + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-014"));
  }

  @Test
  @DisplayName(
      "CA-CM-133 · la personalizada de un gratuito: corregir a fijo pasa, a porcentaje se rechaza"
          + " con EX-008")
  void corregirLaPersonalizadaConProductoGratuito() throws Exception {
    UUID gratis = CommissionFixtures.sembrarProducto(jdbc, "BOT_GRATIS", false, "0.0000");
    UUID tasa = altaDevuelve(fijo(vendedora, gratis, "1000", "2026-01-01"));

    mvc.perform(correccion(tasa, "{\"rateType\":\"FIJO\",\"fixedAmount\":900000}"))
        .andExpect(status().isOk());

    mvc.perform(correccion(tasa, "{\"rateType\":\"PORCENTAJE\",\"percentage\":10}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-008"));
  }

  @Test
  @DisplayName("el fin anterior al inicio se rechaza")
  void vigenciaInvertida() throws Exception {
    mvc.perform(alta(cuerpo(vendedora, producto, "12.00", "2026-06-01", "2026-01-01")))
        .andExpect(status().isBadRequest());

    assertThat(cuantas()).isZero();
  }

  @Test
  @DisplayName("la persona inexistente se rechaza con 422")
  void personaInexistente() throws Exception {
    mvc.perform(alta(cuerpo(UUID.randomUUID(), producto, "12.00", "2026-01-01", null)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
  }

  // ---------------------------------------------------------------------------
  // El listado (`RF-CM-002`)
  // ---------------------------------------------------------------------------

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
  @DisplayName("CA-CM-126 · el listado filtra por producto, y se combina con la persona")
  void elListadoFiltraPorProducto() throws Exception {
    UUID otra = CommissionFixtures.sembrarPersonaConRol(jdbc, "otra", MANAGER);
    UUID dos = CommissionFixtures.sembrarProducto(jdbc, "BOT_F2");
    altaDevuelve(cuerpo(vendedora, producto, "12.00", "2026-01-01", null));
    UUID tasaOtra = altaDevuelve(cuerpo(otra, dos, "15.00", "2026-01-01", null));

    // «Quién tiene excepción en este producto»: de cualquier persona.
    mvc.perform(listado().param("productId", dos.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(tasaOtra.toString()))
        .andExpect(jsonPath("$.content[0].user.username").value("otra"));

    // Combinado con la persona: «¿tiene esta persona excepción en este producto?».
    mvc.perform(
            listado().param("productId", producto.toString()).param("userId", vendedora.toString()))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(listado().param("productId", dos.toString()).param("userId", vendedora.toString()))
        .andExpect(jsonPath("$.totalElements").value(0));

    // Un producto donde nadie tiene excepción: vacío, y no es un error.
    UUID nadie = CommissionFixtures.sembrarProducto(jdbc, "BOT_F3");
    mvc.perform(listado().param("productId", nadie.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    // Sin el filtro, siguen saliendo las dos.
    mvc.perform(listado()).andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  @DisplayName(
      "CA-CM-150 · cada fila trae SU producto con precio y moneda, y ya no cuenta asociados")
  void cadaFilaTraeSuProducto() throws Exception {
    UUID dos = CommissionFixtures.sembrarProducto(jdbc, "BOT_C2", false, "250.50");
    altaDevuelve(cuerpo(vendedora, producto, "12.00", "2026-01-01", null));
    altaDevuelve(fijo(vendedora, dos, "100", "2026-01-01"));

    // Una persona con excepción en dos productos son DOS filas.
    mvc.perform(listado().param("userId", vendedora.toString()))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].associatedProducts").doesNotExist())
        .andExpect(jsonPath("$.content[?(@.product.code == 'BOT_C2')].product.price").value(250.50))
        .andExpect(
            jsonPath("$.content[?(@.product.code == 'BOT_C2')].product.currency.decimalPlaces")
                .isNotEmpty())
        .andExpect(
            jsonPath("$.content[?(@.product.code == 'BOT_A')].product.price").value(1000.00));
  }

  @Test
  @DisplayName("el alta exige commissions:create")
  void exigeElPermiso() throws Exception {
    mvc.perform(
            post("/api/v1/user-commission-rates")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(vendedora, producto, "12.00", "2026-01-01", null)))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // El valor fijo (`cm.md` v0.7.0)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-CM-085 · registra una personalizada EN VALOR FIJO, con la forma junto al valor")
  void altaEnValorFijo() throws Exception {
    mvc.perform(alta(fijo(vendedora, producto, "500", "2026-01-01")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rateType").value("FIJO"))
        .andExpect(jsonPath("$.fixedAmount").value(500))
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
    mvc.perform(alta(cuerpo(vendedora, producto, "12.00", "2026-01-01", "2026-03-31")))
        .andExpect(status().isCreated());
    mvc.perform(alta(fijo(vendedora, producto, "500", "2026-04-01")))
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

    mvc.perform(correccion(tasa, "{\"rateType\":\"FIJO\",\"fixedAmount\":500}"))
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

    assertThat(cambio).contains("PORCENTAJE 12.00").contains("FIJO 500");
  }

  @Test
  @DisplayName("el fin de vigencia SIGUE parcheándose solo, y el valor NO")
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
    mvc.perform(correccion(tasa, "{\"fixedAmount\":500}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-011"));
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  private static String cuerpo(
      UUID persona, UUID producto, String porcentaje, String desde, String hasta) {
    StringBuilder json = new StringBuilder("{\"userId\":\"").append(persona).append("\"");
    json.append(",\"productId\":\"").append(producto).append("\"");
    json.append(",\"rateType\":\"PORCENTAJE\",\"percentage\":").append(porcentaje);
    json.append(",\"validFrom\":\"").append(desde).append("\"");
    if (hasta != null) {
      json.append(",\"validTo\":\"").append(hasta).append("\"");
    }
    return json.append("}").toString();
  }

  private static String fijo(UUID persona, UUID producto, String importe, String desde) {
    return "{\"userId\":\""
        + persona
        + "\",\"productId\":\""
        + producto
        + "\",\"rateType\":\"FIJO\",\"fixedAmount\":"
        + importe
        + ",\"validFrom\":\""
        + desde
        + "\"}";
  }

  private MockHttpServletRequestBuilder alta(String json) {
    return post("/api/v1/user-commission-rates")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  /** El alta, devolviendo el identificador de la tasa creada. */
  private UUID altaDevuelve(String cuerpo) throws Exception {
    String json =
        mvc.perform(alta(cuerpo))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(com.jayway.jsonpath.JsonPath.read(json, "$.id"));
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

  private MockHttpServletRequestBuilder efectiva(UUID persona, UUID producto, String fecha) {
    return get("/api/v1/commissions/effective")
        .param("userId", persona.toString())
        .param("productId", producto.toString())
        .param("onDate", fecha)
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
