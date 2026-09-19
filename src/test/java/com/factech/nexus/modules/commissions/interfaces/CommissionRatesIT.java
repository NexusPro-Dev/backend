package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.AGENTE;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.DIRECTOR;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.MANAGER;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.NO_VENDEDOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/**
 * El alta de la tasa de rol <b>sobre un producto</b> (`RF-CM-001`).
 *
 * <p><b>Lo que más importa aquí es que lo registrado RIGE.</b> Desde el 15-09-2026 la tasa nace con
 * su producto (`RN-CM-021`) y `RF-CM-005` la resuelve sin ningún paso de asociación. Del 01-09-2026
 * al 15-09-2026 era al revés —el alta llenaba un catálogo que no pagaba nada hasta asociarse—, y
 * antes del 01-09-2026 una tarifa sin producto valía para todo el catálogo. Las tres lecturas se
 * distinguen en una sola prueba: la primera de esta suite.
 *
 * <p>Y con el producto llegan al alta las comprobaciones que hasta hoy vivían en la asociación:
 * producto vivo (`RN-CM-002`, `RN-CM-010`), un rol por producto (`RN-CM-013`), el tope
 * (`RN-CM-019`), el gratuito (`RN-CM-020`) y los decimales de la moneda (`RN-CM-017`).
 */
@AutoConfigureMockMvc
class CommissionRatesIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID producto;
  private UUID vendedora;

  @BeforeEach
  void preparar() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
    producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_A");
    vendedora = CommissionFixtures.sembrarPersonaConRol(jdbc, "vendedora", MANAGER);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
  }

  @Test
  @DisplayName("CA-CM-136 · registra la tasa CON su producto, y RIGE desde ese instante")
  void naceConSuProductoYRige() throws Exception {
    mvc.perform(alta(cuerpo(producto, MANAGER, "10.00")))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/")))
        .andExpect(jsonPath("$.product.id").value(producto.toString()))
        .andExpect(jsonPath("$.product.code").value("BOT_A"))
        .andExpect(jsonPath("$.product.name").value("Producto BOT_A"))
        // Con su precio y su moneda (`CA-CM-145`): la misma forma que el listado.
        .andExpect(jsonPath("$.product.price").value(10.00))
        .andExpect(jsonPath("$.product.currency.code").isNotEmpty())
        .andExpect(jsonPath("$.product.currency.decimalPlaces").isNumber())
        .andExpect(jsonPath("$.role.code").value("MANAGER"))
        .andExpect(jsonPath("$.percentage").value(10.00))
        // El contador desapareció con la asociación: no hay nada que contar.
        .andExpect(jsonPath("$.associatedProducts").doesNotExist());

    // ESTA ES LA PRUEBA. Sin ningún paso más, la vendedora ya cobra por él.
    mvc.perform(efectiva(vendedora, producto))
        .andExpect(jsonPath("$.outcome").value("RESUELTA"))
        .andExpect(jsonPath("$.source").value("ROL"))
        .andExpect(jsonPath("$.value").value(10.00));
  }

  @Test
  @DisplayName("la respuesta no lleva persona, vigencia ni grado")
  void loQuePerdioElAlta() throws Exception {
    mvc.perform(alta(cuerpo(producto, MANAGER, "10.00")))
        .andExpect(status().isCreated())
        // Los cuatro grados desaparecieron con el rediseño: no hay `scope` que
        // devolver porque no hay nada que graduar.
        .andExpect(jsonPath("$.scope").doesNotExist())
        .andExpect(jsonPath("$.user").doesNotExist())
        .andExpect(jsonPath("$.validFrom").doesNotExist())
        .andExpect(jsonPath("$.validTo").doesNotExist());
  }

  @Test
  @DisplayName("CA-CM-137 · sin producto se rechaza con VAL-013, y nada se escribe")
  void productoObligatorio() throws Exception {
    mvc.perform(
            alta("{\"roleId\":\"" + MANAGER + "\",\"rateType\":\"PORCENTAJE\",\"percentage\":10}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-013"))
        .andExpect(jsonPath("$.errors[0].field").value("productId"));

    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("CA-CM-137 · el producto inexistente y el retirado se distinguen, y los dos son 422")
  void productoInexistenteYRetirado() throws Exception {
    mvc.perform(alta(cuerpo(UUID.randomUUID(), MANAGER, "10.00")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    // `RN-CM-010`: existe, y no se configura comisión sobre lo que ya no se vende.
    UUID retirado = CommissionFixtures.sembrarProducto(jdbc, "BOT_Z", true);
    mvc.perform(alta(cuerpo(retirado, MANAGER, "10.00")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));

    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("CA-CM-138 · `RN-CM-013` — el mismo rol dos veces sobre el mismo producto: 409")
  void unRolPorProducto() throws Exception {
    mvc.perform(alta(cuerpo(producto, MANAGER, "10.00"))).andExpect(status().isCreated());

    // Si entrara, la resolución tendría dos respuestas válidas para «qué paga
    // MANAGER por este producto» y elegiría el plan de ejecución.
    mvc.perform(alta(cuerpo(producto, MANAGER, "15.00")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-007"));

    assertThat(cuantasTasas()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "CA-CM-138 · el mismo rol sobre OTRO producto, y sobre el mismo cuando la primera se retiró")
  void elMismoRolDondeSiCabe() throws Exception {
    UUID otro = CommissionFixtures.sembrarProducto(jdbc, "BOT_B");

    UUID primera = idDe(alta(cuerpo(producto, MANAGER, "10.00")));
    mvc.perform(alta(cuerpo(otro, MANAGER, "15.00"))).andExpect(status().isCreated());

    // Retirada la primera, el producto vuelve a admitir a ese rol: el índice es
    // parcial sobre las vivas, y la retirada queda como historia.
    jdbc.update(
        "UPDATE commission_rates SET deleted_at = now() WHERE id = CAST(? AS uuid)",
        primera.toString());
    mvc.perform(alta(cuerpo(producto, MANAGER, "12.00"))).andExpect(status().isCreated());

    assertThat(cuantasTasas()).isEqualTo(3);
  }

  @Test
  @DisplayName("dos ROLES distintos sobre el mismo producto sí conviven, y el producto los lista")
  void variosRolesPorProducto() throws Exception {
    mvc.perform(alta(cuerpo(producto, MANAGER, "10.00"))).andExpect(status().isCreated());
    mvc.perform(alta(cuerpo(producto, DIRECTOR, "4.00"))).andExpect(status().isCreated());

    mvc.perform(
            get("/api/v1/product-commission-rates")
                .param("productId", producto.toString())
                .with(
                    user(SUPERADMIN.toString())
                        .authorities(
                            () -> "commissions:read",
                            () -> "commissions:read-effective",
                            () -> "user-commission-rates:read",
                            () -> "product-commission-rates:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].role.code").value("DIRECTOR"))
        .andExpect(jsonPath("$.content[1].role.code").value("MANAGER"));
  }

  @Test
  @DisplayName("el porcentaje CERO se registra, y no es lo mismo que no tener tasa")
  void elCeroSeRegistra() throws Exception {
    mvc.perform(alta(cuerpo(producto, MANAGER, "0")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.percentage").value(0));

    assertThat(cuantasTasas()).isEqualTo(1);
  }

  @Test
  @DisplayName("un rol que no es vendedor se rechaza con 400")
  void soloComisionanLosVendedores() throws Exception {
    mvc.perform(alta(cuerpo(producto, NO_VENDEDOR, "10.00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));

    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("el rol inexistente se distingue del que no es vendedor: 422 y no 400")
  void rolInexistente() throws Exception {
    mvc.perform(alta(cuerpo(producto, UUID.randomUUID().toString(), "10.00")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("el porcentaje fuera de [0, 100] se rechaza")
  void porcentajeFueraDeRango() throws Exception {
    mvc.perform(alta(cuerpo(producto, MANAGER, "100.01"))).andExpect(status().isBadRequest());
    mvc.perform(alta(cuerpo(producto, MANAGER, "-1"))).andExpect(status().isBadRequest());

    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("el rol es obligatorio")
  void rolObligatorio() throws Exception {
    mvc.perform(alta("{\"productId\":\"" + producto + "\",\"percentage\":10.00}"))
        .andExpect(status().isBadRequest());
    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("sin el permiso de alta se rechaza")
  void exigeElPermiso() throws Exception {
    mvc.perform(
            post("/api/v1/commission-rates")
                .with(
                    user(SUPERADMIN.toString())
                        .authorities(
                            () -> "commissions:read",
                            () -> "commissions:read-effective",
                            () -> "user-commission-rates:read",
                            () -> "product-commission-rates:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(producto, MANAGER, "10.00")))
        .andExpect(status().isForbidden());

    assertThat(cuantasTasas()).isZero();
  }

  // ---------------------------------------------------------------------------
  // El valor fijo (`cm.md` v0.7.0)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-CM-079 · registra una tasa EN VALOR FIJO, con la forma junto al valor")
  void altaEnValorFijo() throws Exception {
    mvc.perform(alta(fijo(producto, MANAGER, "5.00")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rateType").value("FIJO"))
        .andExpect(jsonPath("$.fixedAmount").value(5.00))
        // Vacío y PRESENTE. Un campo que desaparece del resultado es
        // indistinguible de uno que el cliente no conoce.
        .andExpect(jsonPath("$.percentage").value(org.hamcrest.Matchers.nullValue()));

    assertThat(cuantasTasas()).isEqualTo(1);
  }

  @Test
  @DisplayName("CA-CM-080 · las DOS formas a la vez se rechazan: no se suman")
  void lasDosFormasALaVez() throws Exception {
    mvc.perform(
            alta(
                "{\"productId\":\""
                    + producto
                    + "\",\"roleId\":\""
                    + MANAGER
                    + "\",\"rateType\":\"PORCENTAJE\",\"percentage\":5.00,"
                    + "\"fixedAmount\":10000}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-011"));

    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("CA-CM-081 · el valor que no corresponde a la forma, y la forma ausente")
  void formaYValorDescuadrados() throws Exception {
    // Tipo FIJO con el porcentaje lleno. Comprobar solo que UNO esté presente
    // dejaría pasar esto, y es la manera fácil de escribir la regla a medias.
    mvc.perform(
            alta(
                "{\"productId\":\""
                    + producto
                    + "\",\"roleId\":\""
                    + MANAGER
                    + "\",\"rateType\":\"FIJO\",\"percentage\":10.00}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-011"));

    // Y sin forma. Es la petición que funcionaba antes del 02-09-2026: se rompe
    // A PROPÓSITO, porque suponer PORCENTAJE aceptaría como válida la petición
    // de quien quiso declarar un importe y se equivocó de campo.
    mvc.perform(
            alta(
                "{\"productId\":\""
                    + producto
                    + "\",\"roleId\":\""
                    + MANAGER
                    + "\",\"percentage\":10.00}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));

    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("CA-CM-082 · el valor fijo negativo se rechaza")
  void valorFijoNegativo() throws Exception {
    mvc.perform(alta(fijo(producto, MANAGER, "-0.01"))).andExpect(status().isBadRequest());
    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("CA-CM-083 · el valor fijo CERO se registra: es «no comisiona», no una ausencia")
  void valorFijoCero() throws Exception {
    mvc.perform(alta(fijo(producto, MANAGER, "0")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rateType").value("FIJO"))
        .andExpect(jsonPath("$.fixedAmount").value(0));

    assertThat(cuantasTasas()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "CA-CM-084 · el importe no tiene máximo PROPIO: lo acota el precio del producto — reescrito"
          + " el 15-09-2026")
  void elImporteLoAcotaElProducto() throws Exception {
    // Hasta el 15-09-2026 esta prueba afirmaba que un importe MAYOR QUE
    // CUALQUIER PRECIO entraba sin resistencia, porque al registrar no había
    // producto contra cuyo precio comparar. Hoy lo hay, y `RN-CM-019` es el
    // tope: `RN-CM-018` sigue diciendo que no existe un número que acote el
    // importe POR SÍ MISMO —la validación del cuerpo no lo mira—, y lo que lo
    // frena es el cien por cien del producto sobre el que va a regir.
    UUID caro = CommissionFixtures.sembrarProducto(jdbc, "BOT_CARO", false, "100000000.00");
    mvc.perform(alta(fijo(caro, MANAGER, "99999999.99")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.fixedAmount").value(99999999.99));

    // El mismo importe sobre uno de diez: más del cien por cien de sí mismo.
    mvc.perform(alta(fijo(producto, MANAGER, "99999999.99")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"));

    assertThat(cuantasTasas()).isEqualTo(1);
  }

  @Test
  @DisplayName("`V50` · un INSERT sin `rate_type` FALLA: la forma no tiene valor por defecto")
  void laFormaEsObligatoriaEnElEsquema() {
    // LA ÚNICA PRUEBA QUE PUEDE DELATAR QUE `ALTER COLUMN rate_type DROP DEFAULT`
    // SE CAYÓ DE LA MIGRACIÓN. Todas las demás pasan por la API, que siempre
    // envía la forma; ninguna se enteraría. Si el valor por defecto siguiera ahí,
    // esta inserción obtendría PORCENTAJE en silencio y el `INSERT` pasaría.
    //
    // Es fea —habla SQL en lugar de negocio— y es el mismo criterio que
    // `CA-CM-075`: se prueba lo que el esquema HABRÍA dejado pasar.
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO commission_rates (id, product_id, role_id, percentage)"
                        + " VALUES (gen_random_uuid(), CAST(? AS uuid), CAST(? AS uuid), 10.00)",
                    producto.toString(),
                    MANAGER))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);

    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("`V94` · un INSERT sin `product_id` FALLA: la tasa no nace sin producto")
  void elProductoEsObligatorioEnElEsquema() {
    // El mismo criterio que la anterior: si `NOT NULL` se cayera de `V94`, la
    // API seguiría exigiendo el producto y ninguna otra prueba se enteraría de
    // que el esquema volvió a admitir tasas que no rigen sobre nada.
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO commission_rates (id, role_id, rate_type, percentage)"
                        + " VALUES (gen_random_uuid(), CAST(? AS uuid), 'PORCENTAJE', 10.00)",
                    MANAGER))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);

    assertThat(cuantasTasas()).isZero();
  }

  // ---------------------------------------------------------------------------
  // `RN-CM-019` y `RN-CM-020` — el tope y el gratuito, EN EL ALTA (`CA-CM-139`)
  //
  // Hasta el 15-09-2026 vivían en la asociación (`RF-CM-007`); con la tasa
  // naciendo con su producto, es el alta la que suma.
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-CM-139 · la primera tasa del producto solo compara consigo misma: cien entra")
  void productoSinTasasPrevias() throws Exception {
    mvc.perform(alta(cuerpo(producto, DIRECTOR, "100.00"))).andExpect(status().isCreated());
    assertThat(cuantasTasas()).isEqualTo(1);
  }

  @Test
  @DisplayName("CA-CM-139 · la suma EXACTA a cien se admite")
  void sumaExactaACien() throws Exception {
    mvc.perform(alta(cuerpo(producto, MANAGER, "60.00"))).andExpect(status().isCreated());
    mvc.perform(alta(cuerpo(producto, DIRECTOR, "40.00"))).andExpect(status().isCreated());

    assertThat(cuantasTasas()).isEqualTo(2);
  }

  @Test
  @DisplayName("CA-CM-139 · la suma que PASA de cien se rechaza, y no queda fila nueva")
  void sumaQuePasaDeCien() throws Exception {
    mvc.perform(alta(cuerpo(producto, MANAGER, "60.00"))).andExpect(status().isCreated());

    // 60 + 45 = 105: se rechaza, y el rol DIRECTOR se queda sin tasa.
    mvc.perform(alta(cuerpo(producto, DIRECTOR, "45.00")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"));

    assertThat(cuantasTasas()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "CA-CM-139 · el valor fijo entra en la suma convertido contra el precio del producto")
  void valorFijoConvertidoContraElPrecio() throws Exception {
    UUID caro = CommissionFixtures.sembrarProducto(jdbc, "BOT_1000", false, "1000.0000");

    // 400 / 1000 * 100 = 40 %.
    mvc.perform(alta(fijo(caro, MANAGER, "400.00"))).andExpect(status().isCreated());
    // 40 + 60 = 100: cabe justo.
    mvc.perform(alta(cuerpo(caro, DIRECTOR, "60.00"))).andExpect(status().isCreated());
    // 40 + 60 + 1 = 101: no cabe.
    mvc.perform(alta(cuerpo(caro, AGENTE, "1.00")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"));

    assertThat(cuantasTasas()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "CA-CM-139 · sobre precio cero, un fijo se ADMITE sin tope y un PORCENTAJE se rechaza")
  void elProductoGratuito() throws Exception {
    // `RN-CM-020` (cm.md v0.13.0): un producto gratuito existe para captar, y
    // quien lo coloca cobra por colocarlo — con un importe, porque un
    // porcentaje de cero es cero.
    UUID gratis = CommissionFixtures.sembrarProducto(jdbc, "BOT_GRATIS", false, "0.0000");

    mvc.perform(alta(fijo(gratis, MANAGER, "50000.00"))).andExpect(status().isCreated());
    mvc.perform(alta(fijo(gratis, AGENTE, "99999.99"))).andExpect(status().isCreated());

    // Un porcentaje de nada es nada: registrarlo configuraría algo que no paga
    // sin que nadie lo dijera, y por eso se rechaza en vez de admitirse.
    mvc.perform(alta(cuerpo(gratis, DIRECTOR, "60.00")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "El producto BOT_GRATIS es gratuito: solo admite comisiones de importe fijo, no"
                        + " de porcentaje."));

    // Lo que decide es el producto, no la tasa: el mismo porcentaje entra en
    // uno con precio.
    mvc.perform(alta(cuerpo(producto, DIRECTOR, "60.00"))).andExpect(status().isCreated());

    assertThat(cuantasTasas()).isEqualTo(3);
  }

  // ---------------------------------------------------------------------------
  // `RN-CM-017` — los decimales de la moneda del producto (`CA-CM-140`)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-CM-140 · un importe con más decimales que la moneda se rechaza, y con los justos entra")
  void losDecimalesDeLaMoneda() throws Exception {
    // Por primera vez la tasa sabe en qué moneda pagará: la de su producto.
    // Hasta el 15-09-2026 un importe con cuatro decimales entraba sin que nadie
    // pudiera decir si era dinero.
    int decimales = decimalesDeLaMonedaDe(producto);
    String deMas = "1." + "0".repeat(decimales) + "1";
    String justos = decimales == 0 ? "1" : "1." + "0".repeat(decimales - 1) + "1";

    mvc.perform(alta(fijo(producto, MANAGER, deMas)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-014"))
        .andExpect(jsonPath("$.errors[0].field").value("fixedAmount"));
    assertThat(cuantasTasas()).isZero();

    mvc.perform(alta(fijo(producto, MANAGER, justos))).andExpect(status().isCreated());
    assertThat(cuantasTasas()).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  private static String fijo(UUID producto, String rol, String importe) {
    return "{\"productId\":\""
        + producto
        + "\",\"roleId\":\""
        + rol
        + "\",\"rateType\":\"FIJO\",\"fixedAmount\":"
        + importe
        + "}";
  }

  private static String cuerpo(UUID producto, String rol, String porcentaje) {
    return "{\"productId\":\""
        + producto
        + "\",\"roleId\":\""
        + rol
        + "\",\"rateType\":\"PORCENTAJE\",\"percentage\":"
        + porcentaje
        + "}";
  }

  private MockHttpServletRequestBuilder alta(String json) {
    return post("/api/v1/commission-rates")
        .with(
            user(SUPERADMIN.toString())
                .authorities(() -> "commissions:create", () -> "user-commission-rates:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  private UUID idDe(MockHttpServletRequestBuilder peticion) throws Exception {
    String json =
        mvc.perform(peticion)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(com.jayway.jsonpath.JsonPath.read(json, "$.id"));
  }

  private MockHttpServletRequestBuilder efectiva(UUID persona, UUID producto) {
    return get("/api/v1/commissions/effective")
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

  private int decimalesDeLaMonedaDe(UUID producto) {
    return jdbc.queryForObject(
        "SELECT c.decimal_places FROM products p JOIN currencies c ON c.id = p.currency_id"
            + " WHERE p.id = CAST(? AS uuid)",
        Integer.class,
        producto.toString());
  }

  private long cuantasTasas() {
    return jdbc.queryForObject("SELECT count(*) FROM commission_rates", Long.class);
  }
}
