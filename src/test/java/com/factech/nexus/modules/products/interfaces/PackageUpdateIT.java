package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.time.LocalDate;
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

/**
 * Corregir un paquete (`RF-PM-020`, `CA-PM-285` a `CA-PM-290`).
 *
 * <p>Las dos que pesan: <b>`CA-PM-287`</b> —código y moneda se rechazan, no se ignoran— y
 * <b>`CA-PM-289`</b> —vaciar la descripción de un paquete activo lo deja sin ofrecer, sin cambiar
 * su estado—.
 */
@AutoConfigureMockMvc
class PackageUpdateIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private static final LocalDate HOY = LocalDate.now(ZoneOffset.UTC);

  private UUID paquete;

  @BeforeEach
  void prepararCatalogo() {
    PackageTestSupport.limpiarCatalogoYSembrarMembresias(jdbc);
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'PM'");
    paquete = PackageTestSupport.paquete(jdbc, "COMBO", "Dos bots.", "ACTIVO", "AMBOS");
    UUID a = PackageTestSupport.bot(jdbc, "BOT_A", "10.00");
    UUID b = PackageTestSupport.bot(jdbc, "BOT_B", "20.00");
    PackageTestSupport.asociar(jdbc, paquete, a, "FIJO", "0");
    PackageTestSupport.asociar(jdbc, paquete, b, "FIJO", "0");
  }

  @AfterEach
  void vaciar() {
    PackageTestSupport.limpiarPaquetes(jdbc);
    jdbc.update("DELETE FROM products");
  }

  @Test
  @DisplayName(
      "`CA-PM-285` y `CA-PM-290` — corrige por separado y juntos, avanza updatedAt y audita antes y después")
  void corrige() throws Exception {
    mvc.perform(corregir("{\"name\":\"  Combo Plus  \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Combo Plus"));
    mvc.perform(corregir("{\"description\":\"Otra.\",\"scope\":\"TIENDA\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Otra."))
        .andExpect(jsonPath("$.scope").value("TIENDA"))
        .andExpect(jsonPath("$.offerable").value(true));

    String ultimo =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE module = 'PM' AND entity = 'product_packages'"
                + " AND action = 'UPDATE' AND entity_id = ? ORDER BY occurred_at DESC LIMIT 1",
            String.class,
            paquete);
    assertThat(ultimo)
        .contains("\"description\": {\"after\": \"Otra.\", \"before\": \"Dos bots.\"}")
        .contains("\"scope\": {\"after\": \"TIENDA\", \"before\": \"AMBOS\"}");
  }

  @Test
  @DisplayName(
      "`CA-PM-286` — el nulo explícito vacía la descripción y se rechaza en nombre y alcance")
  void nuloExplicito() throws Exception {
    mvc.perform(corregir("{\"description\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value(nullValue()));
    mvc.perform(corregir("{\"name\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(corregir("{\"name\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(corregir("{\"scope\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    mvc.perform(corregir("{\"scope\":\"HOTLINKS\"}")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-PM-287` — code y currencyId se RECHAZAN con 400, y el cuerpo vacío también")
  void inmutablesYCuerpoVacio() throws Exception {
    mvc.perform(corregir("{\"code\":\"OTRO\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(containsString("no se pueden modificar")));
    mvc.perform(corregir("{\"currencyId\":\"" + UUID.randomUUID() + "\",\"name\":\"X\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("currencyId"));
    mvc.perform(corregir("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));
    assertThat(
            jdbc.queryForObject(
                "SELECT name FROM product_packages WHERE id = ?", String.class, paquete))
        .isEqualTo("Paquete COMBO");
  }

  @Test
  @DisplayName(
      "`CA-PM-288` — el nombre de otro vivo es 409, el de un retirado se admite, y el paquete retirado es 404")
  void nombreYRetirado() throws Exception {
    UUID otro = PackageTestSupport.paquete(jdbc, "OTRO", null, "INACTIVO", "TIENDA");
    mvc.perform(corregir("{\"name\":\"paquete otro\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    // El propio nombre no choca consigo mismo.
    mvc.perform(corregir("{\"name\":\"Paquete COMBO\"}")).andExpect(status().isOk());
    jdbc.update("UPDATE product_packages SET deleted_at = now() WHERE id = ?", otro);
    mvc.perform(corregir("{\"name\":\"Paquete OTRO\"}")).andExpect(status().isOk());

    jdbc.update("UPDATE product_packages SET deleted_at = now() WHERE id = ?", paquete);
    mvc.perform(corregir("{\"name\":\"Da igual\"}")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "`CA-PM-289` — vaciar la descripción de un paquete ACTIVO lo deja offerable false sin cambiar su estado")
  void vaciarLaDescripcionDeUnActivo() throws Exception {
    mvc.perform(corregir("{\"description\":\"\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(jsonPath("$.offerableReason").value(containsString("descripción")));
  }

  @Test
  @DisplayName("`CA-PM-290` — sin cambios de valor responde 200 sin avanzar updatedAt ni auditar")
  void sinCambios() throws Exception {
    String antes =
        jdbc.queryForObject(
            "SELECT updated_at::text FROM product_packages WHERE id = ?", String.class, paquete);
    mvc.perform(
            corregir(
                "{\"name\":\"Paquete COMBO\",\"description\":\"Dos bots.\",\"scope\":\"AMBOS\"}"))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT updated_at::text FROM product_packages WHERE id = ?",
                String.class,
                paquete))
        .isEqualTo(antes);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE module = 'PM' AND action = 'UPDATE'",
                Integer.class))
        .isZero();
  }

  @Test
  @DisplayName(
      "`CA-PM-374` — corrige las dos fechas por separado y juntas con su antes y su después; validTo:null vacía, validFrom:null se rechaza; la pareja resultante con el fin antes del inicio es 400 aunque venga uno solo")
  void corrigeLaVigencia() throws Exception {
    mvc.perform(corregir("{\"validTo\":\"" + HOY.plusDays(30) + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validFrom").value(HOY.toString()))
        .andExpect(jsonPath("$.validTo").value(HOY.plusDays(30).toString()));
    // Solo el inicio, más allá del fin que ya había: 400, y nada cambia.
    mvc.perform(corregir("{\"validFrom\":\"" + HOY.plusDays(31) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-007"))
        .andExpect(jsonPath("$.errors[0].field").value("validTo"));
    // Solo el fin, antes del inicio que ya había: lo mismo.
    mvc.perform(corregir("{\"validTo\":\"" + HOY.minusDays(1) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-007"));
    // Las dos juntas, y pasan.
    mvc.perform(
            corregir(
                "{\"validFrom\":\""
                    + HOY.plusDays(31)
                    + "\",\"validTo\":\""
                    + HOY.plusDays(40)
                    + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validFrom").value(HOY.plusDays(31).toString()))
        .andExpect(jsonPath("$.validTo").value(HOY.plusDays(40).toString()));
    // El inicio no admite vaciarse; el fin sí, y vuelve a indefinido.
    mvc.perform(corregir("{\"validFrom\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-006"));
    mvc.perform(corregir("{\"validTo\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validTo").value(nullValue()));

    String ultimo =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE module = 'PM' AND entity = 'product_packages'"
                + " AND action = 'UPDATE' AND entity_id = ? ORDER BY occurred_at DESC LIMIT 1",
            String.class,
            paquete);
    assertThat(ultimo)
        .contains("\"valid_to\": {\"after\": \"\", \"before\": \"" + HOY.plusDays(40) + "\"}");
  }

  @Test
  @DisplayName(
      "`CA-PM-375` — validTo en ayer deja a un ACTIVO ofrecible con offerable false y la fecha en el motivo, sin cambiar su estado; vaciarlo lo devuelve")
  void cerrarLaVigenciaDeUnActivo() throws Exception {
    mvc.perform(corregir("{\"name\":\"Paquete COMBO\"}"))
        .andExpect(jsonPath("$.offerable").value(true));
    // El inicio de hoy se mueve atrás para poder cerrar en ayer.
    mvc.perform(
            corregir(
                "{\"validFrom\":\""
                    + HOY.minusDays(10)
                    + "\",\"validTo\":\""
                    + HOY.minusDays(1)
                    + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(
            jsonPath("$.offerableReason")
                .value("La vigencia del paquete terminó el " + HOY.minusDays(1) + "."));
    mvc.perform(corregir("{\"validTo\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.offerable").value(true));
    // Y la otra mitad: el inicio en mañana lo programa.
    mvc.perform(corregir("{\"validFrom\":\"" + HOY.plusDays(1) + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(
            jsonPath("$.offerableReason")
                .value("El paquete todavía no está vigente: empieza el " + HOY.plusDays(1) + "."));
  }

  private MockHttpServletRequestBuilder corregir(String json) {
    return patch("/api/v1/packages/" + paquete)
        .with(user(UUID.randomUUID().toString()).authorities(() -> "packages:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }
}
