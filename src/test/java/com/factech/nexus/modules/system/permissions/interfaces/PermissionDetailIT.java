package com.factech.nexus.modules.system.permissions.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Detalle de un permiso del catálogo (`RF-SP-015`).
 *
 * <p>El catálogo lo siembra {@code V3} y <b>no se toca aquí</b>: es inmutable por API
 * (`RN-SP-004`), de modo que estas pruebas leen lo que la migración dejó en lugar de preparar datos
 * propios. Si alguna vez hiciera falta insertar un permiso para probar esto, sería la señal de que
 * el catálogo dejó de ser inmutable.
 */
@AutoConfigureMockMvc
class PermissionDetailIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("CA-SP-078 — devuelve código, recurso, acción, nombre y descripción")
  void detalleCompleto() throws Exception {
    String id = idDe("roles:read");

    mvc.perform(detalle(id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.code").value("roles:read"))
        .andExpect(jsonPath("$.resource").value("roles"))
        .andExpect(jsonPath("$.action").value("read"))
        .andExpect(jsonPath("$.name").isNotEmpty())
        // Presente aunque venga vacía: un campo ausente es indistinguible de uno
        // que el cliente no conoce.
        .andExpect(jsonPath("$.description").exists());
  }

  @Test
  @DisplayName("CA-SP-079 — un identificador que no corresponde a ningún permiso es 404")
  void inexistente() throws Exception {
    mvc.perform(detalle(UUID.randomUUID().toString())).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CA-SP-080 — sin `permissions:read` no se obtiene el detalle")
  void sinPermiso() throws Exception {
    String id = idDe("roles:read");

    mvc.perform(
            get("/api/v1/permissions/{id}", id)
                .with(user(SUPERADMIN.toString()).authorities(() -> "roles:read")))
        .andExpect(status().isForbidden());

    mvc.perform(get("/api/v1/permissions/{id}", id)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("un identificador mal formado es 400, no 404")
  void identificadorNoCanonico() throws Exception {
    // La distinción importa: `404` diría que el permiso no existe, cuando lo que
    // ocurre es que nunca se preguntó por uno válido.
    mvc.perform(detalle("1-1-1-1-1")).andExpect(status().isBadRequest());
    mvc.perform(detalle("roles:read")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("el detalle lleva seis campos y ninguno más")
  void loQueElDetalleNoLleva() throws Exception {
    String cuerpo =
        mvc.perform(detalle(idDe("roles:read")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Se comprueban las CLAVES y no el texto del cuerpo: buscar la palabra
    // «roles» daría un falso fallo, porque este permiso se llama `roles:read` y
    // su recurso es `roles`.
    //
    // Lo que las ausencias significan: en una tabla que solo cambia por
    // migración, `createdAt` diría cuándo se desplegó una migración y no cuándo
    // ocurrió algo de negocio; y los roles que declaran el permiso son el
    // recorrido inverso del catálogo, que corresponde a otra consulta.
    assertThat(new ObjectMapper().readTree(cuerpo).properties())
        .extracting(java.util.Map.Entry::getKey)
        .containsExactlyInAnyOrder("id", "code", "resource", "action", "name", "description");
  }

  private MockHttpServletRequestBuilder detalle(String id) {
    return get("/api/v1/permissions/{id}", id)
        .with(user(SUPERADMIN.toString()).authorities(() -> "permissions:read"));
  }

  private String idDe(String codigo) {
    return jdbc.queryForObject("SELECT id FROM permissions WHERE code = ?", UUID.class, codigo)
        .toString();
  }
}
