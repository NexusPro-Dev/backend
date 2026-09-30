package com.factech.nexus.modules.system.documenttypes.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El catálogo de tipos de documento (`RF-SP-051` · `T-08` a `T-10`): `CA-SP-584` a `CA-SP-589`.
 *
 * <p><b>Escrita el 30-09-2026</b>: el requerimiento se construyó el 08-09-2026 sin prueba propia, y
 * la matriz lo seguía dando por pendiente.
 *
 * <h2>`CA-SP-589` afirma lo contrario de lo que afirmaba</h2>
 *
 * <p>La ruta es <b>pública</b> desde el mismo 08-09-2026 (`RN-SP-041`): el formulario de registro
 * elige el tipo de documento antes de que exista la cuenta. «Sin el permiso, rechazo» pasó a ser
 * «sin token, lo mismo que con él» —igual que `CA-MV-033` en el catálogo de métodos de pago—.
 *
 * <h2>La ausencia se comprueba contra la TABLA</h2>
 *
 * <p>`CA-SP-587` es la validación de mayoría de edad entera: los documentos de un menor no se
 * rechazan, <b>no están</b>. Mirarlo en la respuesta no bastaría —un inactivo no sale y seguiría
 * existiendo—, de modo que se mira en {@code document_types}, activos o no.
 */
@AutoConfigureMockMvc
class DocumentTypesIT extends IntegrationTestBase {

  private static final String RUTA = "/api/v1/document-types";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void reactivar() {
    jdbc.update("UPDATE document_types SET is_active = true WHERE abbreviation = 'NIT'");
  }

  @Test
  @DisplayName("CA-SP-584 — cada tipo trae identificador, nombre, abreviación y estado, por nombre")
  void catalogoCompleto() throws Exception {
    List<String> nombres =
        jdbc.queryForList(
            "SELECT name FROM document_types WHERE is_active ORDER BY name COLLATE \"es-x-icu\"",
            String.class);

    mvc.perform(get(RUTA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(nombres.size()))
        .andExpect(jsonPath("$.content[*].name").value(Matchers.contains(nombres.toArray())))
        .andExpect(jsonPath("$.content[0].id").isNotEmpty())
        .andExpect(jsonPath("$.content[0].abbreviation").isNotEmpty())
        .andExpect(jsonPath("$.content[0].isActive").value(true));
  }

  @Test
  @DisplayName("CA-SP-585 — no hay alta, edición, eliminación ni cambio de estado")
  void soloLectura() throws Exception {
    var conToken = user("doc").authorities(() -> "document-types:read");
    mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{}").with(conToken))
        .andExpect(status().isMethodNotAllowed());
    mvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content("{}").with(conToken))
        .andExpect(status().isMethodNotAllowed());
    mvc.perform(patch(RUTA).contentType(MediaType.APPLICATION_JSON).content("{}").with(conToken))
        .andExpect(status().isMethodNotAllowed());
    mvc.perform(delete(RUTA).with(conToken)).andExpect(status().isMethodNotAllowed());

    // Y el contrato no publica ninguna otra operación bajo la ruta.
    String contrato =
        mvc.perform(get("/v3/api-docs").with(user("doc")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    var rutas = new com.fasterxml.jackson.databind.ObjectMapper().readTree(contrato).get("paths");
    rutas
        .fieldNames()
        .forEachRemaining(
            ruta -> {
              if (ruta.startsWith(RUTA)) {
                assertThat(ruta).isEqualTo(RUTA);
                var metodos = new java.util.ArrayList<String>();
                rutas.get(ruta).fieldNames().forEachRemaining(metodos::add);
                assertThat(metodos).containsExactly("get");
              }
            });
  }

  @Test
  @DisplayName("CA-SP-586 — un inactivo no sale salvo que se pida, y pedirlo lo añade")
  void inactivos() throws Exception {
    int activos =
        jdbc.queryForObject("SELECT count(*) FROM document_types WHERE is_active", Integer.class);
    jdbc.update("UPDATE document_types SET is_active = false WHERE abbreviation = 'NIT'");

    mvc.perform(get(RUTA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(activos - 1))
        .andExpect(jsonPath("$.content[?(@.abbreviation == 'NIT')]").isEmpty());

    mvc.perform(get(RUTA).param("includeInactive", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(activos))
        .andExpect(jsonPath("$.content[?(@.abbreviation == 'NIT')].isActive").value(false))
        .andExpect(jsonPath("$.content[?(@.abbreviation == 'CC')].isActive").value(true));
  }

  @Test
  @DisplayName("CA-SP-587 — ni tarjeta de identidad ni registro civil, por abreviación ni nombre")
  void ningunDocumentoDeMenor() {
    Integer deMenor =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM document_types
             WHERE upper(abbreviation) IN ('TI', 'RC')
                OR f_unaccent(lower(name)) LIKE '%tarjeta de identidad%'
                OR f_unaccent(lower(name)) LIKE '%registro civil%'
            """,
            Integer.class);
    assertThat(deMenor).as("documentos de menor en document_types").isZero();
  }

  @Test
  @DisplayName("CA-SP-588 — el catálogo no está vacío: hay al menos un tipo activo")
  void noEstaVacio() throws Exception {
    mvc.perform(get(RUTA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(Matchers.greaterThan(0)));
  }

  @Test
  @DisplayName("CA-SP-589 — la ruta es pública: sin token responde lo mismo que con él")
  void publica() throws Exception {
    String sinToken =
        mvc.perform(get(RUTA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    String conToken =
        mvc.perform(get(RUTA).with(user("doc").authorities(() -> "x:y")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(sinToken).isEqualTo(conToken);
  }
}
