package com.factech.nexus.modules.system.brokers.interfaces;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * El catálogo de brokers (`RF-SP-052`).
 *
 * <p><b>Los brokers se siembran por SQL</b>, y no por el endpoint de alta, porque <b>no hay
 * endpoint de alta</b>: es exactamente lo que `RN-SP-039` decide y lo que `CA-SP-604` verifica.
 *
 * <p><b>La tabla nace vacía en el producto</b>: la siembra de verdad es una decisión de negocio que
 * todavía no se ha tomado. Estas pruebas ponen las suyas y las retiran.
 */
@AutoConfigureMockMvc
class BrokersIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void sembrar() {
    limpiar();
    // Fuera de orden alfabético a propósito: si la consulta no ordenara, el
    // orden de inserción pasaría por bueno y la prueba del orden no probaría
    // nada.
    insertar("Exness", true);
    insertar("Ávila Markets", true);
    insertar("IC Markets", false);
  }

  @AfterEach
  void vaciar() {
    limpiar();
  }

  @Test
  @DisplayName("`CA-SP-602` y `CA-SP-605` — devuelve los activos con sus tres campos, por nombre")
  void catalogoDeActivos() throws Exception {
    mvc.perform(get("/api/v1/brokers").with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        // «Ávila» antes que «Exness»: lo ordena la intercalación `es-x-icu` de
        // la columna. Con la de una base sin configuración regional, el nombre
        // acentuado caería al final y el desplegable parecería roto.
        .andExpect(jsonPath("$.content[0].name").value("Ávila Markets"))
        .andExpect(jsonPath("$.content[1].name").value("Exness"))
        .andExpect(jsonPath("$.content[0].id").isNotEmpty())
        .andExpect(jsonPath("$.content[0].isActive").value(true))
        // Sin marcas temporales: `createdAt` diría cuándo se aplicó la
        // migración de siembra, distinto en cada entorno.
        .andExpect(jsonPath("$.content[0].createdAt").doesNotExist());
  }

  @Test
  @DisplayName("`CA-SP-603` — los inactivos no aparecen salvo que se pidan, y entonces se AÑADEN")
  void inactivosBajoPeticion() throws Exception {
    mvc.perform(get("/api/v1/brokers").with(lector()))
        .andExpect(jsonPath("$.content.length()").value(2));

    // Añade, no sustituye: un filtro que ocultara los activos respondería una
    // pregunta que nadie hace.
    mvc.perform(get("/api/v1/brokers?includeInactive=true").with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.content[2].name").value("IC Markets"))
        .andExpect(jsonPath("$.content[2].isActive").value(false));
  }

  @Test
  @DisplayName("`CA-SP-604` — no hay más operación que el `GET`")
  void soloLectura() throws Exception {
    // `RN-SP-039`: el catálogo se puebla por migración. Un `POST` que
    // respondiera algo distinto de 405 sería un alta que nadie decidió.
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/brokers")
                .with(lector())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Inventado\"}"))
        .andExpect(status().isMethodNotAllowed());

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/v1/brokers")
                .with(lector()))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  @DisplayName("`CA-SP-605` — el cliente no puede cambiar el orden")
  void ordenFijo() throws Exception {
    // El DTO declara un solo campo, de modo que la garantía no depende de que
    // nadie envíe estos parámetros: se ignoran.
    mvc.perform(get("/api/v1/brokers?sort=name,desc&page=3&size=1").with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].name").value("Ávila Markets"));
  }

  @Test
  @DisplayName("`CA-SP-606` — sin `brokers:read` no se consulta")
  void sinPermiso() throws Exception {
    mvc.perform(
            get("/api/v1/brokers")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "users:read")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("`FA-002` — el catálogo sin sembrar responde 200 con la colección vacía")
  void catalogoVacio() throws Exception {
    limpiar();

    // Es el estado real del producto hasta que se den los nombres de los
    // brokers. Un `404` o un `500` aquí convertirían «falta una decisión de
    // negocio» en «el sistema está roto».
    mvc.perform(get("/api/v1/brokers").with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  @DisplayName("`CA-SP-607` — dos brokers no pueden llamarse igual, ni cambiando la caja")
  void nombreUnico() {
    // El único es FUNCIONAL sobre `f_unaccent(lower(name))`: sin él, «Exness» y
    // «exness» serían dos brokers y las cuentas se repartirían entre los dos.
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> insertar("EXNESS", true))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> insertar("Avila Markets", true))
        .as("el único es funcional: el acento tampoco distingue")
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  private RequestPostProcessor lector() {
    return user(UUID.randomUUID().toString()).authorities(() -> "brokers:read");
  }

  private void insertar(String nombre, boolean activo) {
    jdbc.update(
        "INSERT INTO brokers (id, name, is_active) VALUES (CAST(? AS uuid), ?, ?)",
        UUID.randomUUID().toString(),
        nombre,
        activo);
  }

  private void limpiar() {
    // Antes que los brokers: `user_brokers` los referencia.
    jdbc.update("DELETE FROM user_brokers");
    jdbc.update("DELETE FROM brokers");
  }
}
