package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.MANAGER;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.NO_VENDEDOR;
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

/**
 * El alta del catálogo de tasas por rol (`RF-CM-001`).
 *
 * <p><b>Lo que más importa aquí no es el camino feliz sino que lo registrado NO RIGE.</b> El alta
 * llena un catálogo; lo que pone una tasa en vigor es asociarla (`RN-CM-012`). Antes del 01-09-2026
 * era al revés —una tarifa sin producto valía para todo el catálogo—, y esa inversión es lo que hay
 * que dejar clavado en una prueba: si alguien la deshace, el sistema empezaría a pagar por
 * productos que nadie configuró.
 */
@AutoConfigureMockMvc
class CommissionRatesIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void preparar() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
  }

  @Test
  @DisplayName("registra la tasa del rol, y nace SIN REGIR: cero productos asociados")
  void naceSinRegir() throws Exception {
    mvc.perform(alta(cuerpo(MANAGER, "10.00")))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/")))
        .andExpect(jsonPath("$.role.code").value("MANAGER"))
        .andExpect(jsonPath("$.percentage").value(10.00))
        // ESTE CERO ES LA PRUEBA. Sin asociación la tasa no paga nada a nadie, y
        // sin este campo el cliente vería un rol con su porcentaje y concluiría
        // que está configurada.
        .andExpect(jsonPath("$.associatedProducts").value(0));

    assertThat(cuantasAsociaciones()).isZero();
  }

  @Test
  @DisplayName("la respuesta ya no lleva producto, persona, vigencia ni grado")
  void loQuePerdioElAlta() throws Exception {
    mvc.perform(alta(cuerpo(MANAGER, "10.00")))
        .andExpect(status().isCreated())
        // Los cuatro grados desaparecieron con el rediseño: no hay `scope` que
        // devolver porque no hay nada que graduar.
        .andExpect(jsonPath("$.scope").doesNotExist())
        .andExpect(jsonPath("$.product").doesNotExist())
        .andExpect(jsonPath("$.user").doesNotExist())
        .andExpect(jsonPath("$.validFrom").doesNotExist())
        .andExpect(jsonPath("$.validTo").doesNotExist());
  }

  @Test
  @DisplayName("VARIAS tasas del mismo rol son legítimas: se asociarán a productos distintos")
  void variasTasasPorRol() throws Exception {
    mvc.perform(alta(cuerpo(MANAGER, "10.00"))).andExpect(status().isCreated());
    mvc.perform(alta(cuerpo(MANAGER, "15.00"))).andExpect(status().isCreated());

    // Lo que no puede repetirse es un rol sobre el MISMO producto, y eso lo
    // cierra la clave primaria de la asociación — no esta tabla.
    assertThat(cuantasTasas()).isEqualTo(2);
  }

  @Test
  @DisplayName("el porcentaje CERO se registra, y no es lo mismo que no tener tasa")
  void elCeroSeRegistra() throws Exception {
    mvc.perform(alta(cuerpo(MANAGER, "0")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.percentage").value(0));

    assertThat(cuantasTasas()).isEqualTo(1);
  }

  @Test
  @DisplayName("un rol que no es vendedor se rechaza con 400")
  void soloComisionanLosVendedores() throws Exception {
    mvc.perform(alta(cuerpo(NO_VENDEDOR, "10.00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));

    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("el rol inexistente se distingue del que no es vendedor: 422 y no 400")
  void rolInexistente() throws Exception {
    mvc.perform(alta(cuerpo(UUID.randomUUID().toString(), "10.00")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("el porcentaje fuera de [0, 100] se rechaza")
  void porcentajeFueraDeRango() throws Exception {
    mvc.perform(alta(cuerpo(MANAGER, "100.01"))).andExpect(status().isBadRequest());
    mvc.perform(alta(cuerpo(MANAGER, "-1"))).andExpect(status().isBadRequest());

    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("el rol es obligatorio")
  void rolObligatorio() throws Exception {
    mvc.perform(alta("{\"percentage\":10.00}")).andExpect(status().isBadRequest());
    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("sin el permiso de alta se rechaza")
  void exigeElPermiso() throws Exception {
    mvc.perform(
            post("/api/v1/commission-rates")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(MANAGER, "10.00")))
        .andExpect(status().isForbidden());

    assertThat(cuantasTasas()).isZero();
  }

  // ---------------------------------------------------------------------------
  // El valor fijo (`cm.md` v0.7.0)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-CM-079 · registra una tasa EN VALOR FIJO, con la forma junto al valor")
  void altaEnValorFijo() throws Exception {
    mvc.perform(alta(fijo(MANAGER, "10000.0000")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rateType").value("FIJO"))
        .andExpect(jsonPath("$.fixedAmount").value(10000.0000))
        // Vacío y PRESENTE. Un campo que desaparece del resultado es
        // indistinguible de uno que el cliente no conoce.
        .andExpect(jsonPath("$.percentage").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.associatedProducts").value(0));

    assertThat(cuantasTasas()).isEqualTo(1);
  }

  @Test
  @DisplayName("CA-CM-080 · las DOS formas a la vez se rechazan: no se suman")
  void lasDosFormasALaVez() throws Exception {
    mvc.perform(
            alta(
                "{\"roleId\":\""
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
    mvc.perform(alta("{\"roleId\":\"" + MANAGER + "\",\"rateType\":\"FIJO\",\"percentage\":10.00}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-011"));

    // Y sin forma. Es la petición que funcionaba antes del 02-09-2026: se rompe
    // A PROPÓSITO, porque suponer PORCENTAJE aceptaría como válida la petición
    // de quien quiso declarar un importe y se equivocó de campo.
    mvc.perform(alta("{\"roleId\":\"" + MANAGER + "\",\"percentage\":10.00}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));

    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("CA-CM-082 · el valor fijo negativo se rechaza")
  void valorFijoNegativo() throws Exception {
    mvc.perform(alta(fijo(MANAGER, "-0.0001"))).andExpect(status().isBadRequest());
    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("CA-CM-083 · el valor fijo CERO se registra: es «no comisiona», no una ausencia")
  void valorFijoCero() throws Exception {
    mvc.perform(alta(fijo(MANAGER, "0")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rateType").value("FIJO"))
        .andExpect(jsonPath("$.fixedAmount").value(0));

    assertThat(cuantasTasas()).isEqualTo(1);
  }

  @Test
  @DisplayName("CA-CM-084 · un importe MAYOR QUE CUALQUIER PRECIO entra sin resistencia")
  void nadieVigilaElImporte() throws Exception {
    // ESTA PRUEBA AFIRMA QUE EL SISTEMA NO HACE NADA, y es la más importante de
    // las seis. `RN-CM-018`: nada acota el importe por arriba, y aquí no podría
    // — al registrar la tasa NO HAY NINGÚN PRODUCTO contra cuyo precio comparar
    // (`RN-CM-012`), y aunque lo hubiera, mañana podría asociarse a otro más
    // barato. La defensa está en la liquidación, que no existe.
    //
    // El día que alguien añada un tope aquí, esto falla y la discusión pasa por
    // `cm.md` en lugar de resolverse en silencio con un número inventado.
    mvc.perform(alta(fijo(MANAGER, "99999999.9999")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.fixedAmount").value(99999999.9999));

    assertThat(cuantasTasas()).isEqualTo(1);
  }

  @Test
  @DisplayName("`V49` · un INSERT sin `rate_type` FALLA: la forma no tiene valor por defecto")
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
                    "INSERT INTO commission_rates (id, role_id, percentage)"
                        + " VALUES (gen_random_uuid(), CAST(? AS uuid), 10.00)",
                    MANAGER))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);

    assertThat(cuantasTasas()).isZero();
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  private static String fijo(String rol, String importe) {
    return "{\"roleId\":\"" + rol + "\",\"rateType\":\"FIJO\",\"fixedAmount\":" + importe + "}";
  }

  private static String cuerpo(String rol, String porcentaje) {
    return "{\"roleId\":\""
        + rol
        + "\",\"rateType\":\"PORCENTAJE\",\"percentage\":"
        + porcentaje
        + "}";
  }

  private MockHttpServletRequestBuilder alta(String json) {
    return post("/api/v1/commission-rates")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  private long cuantasTasas() {
    return jdbc.queryForObject("SELECT count(*) FROM commission_rates", Long.class);
  }

  private long cuantasAsociaciones() {
    return jdbc.queryForObject("SELECT count(*) FROM product_commission_rates", Long.class);
  }
}
