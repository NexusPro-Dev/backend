package com.factech.nexus.shared.architecture;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El contrato publicado incluye todo endpoint declarado (Art. VIII.2, VIII.6).
 *
 * <p><b>Por qué existe.</b> Que un endpoint aparezca en la documentación depende de que springdoc
 * lo descubra, y ese descubrimiento es implícito: nadie lo declara en ningún sitio, de modo que su
 * ausencia no rompe nada y no se nota hasta que alguien abre Swagger y no encuentra lo que busca.
 * Un endpoint que existe y no está documentado incumple el Art. VIII.2 en silencio.
 *
 * <p>Esta prueba convierte ese silencio en un fallo. Cada requerimiento que estrene un endpoint
 * debería añadir aquí su ruta.
 */
@AutoConfigureMockMvc
class OpenApiContractIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("el contrato publica POST /api/v1/roles con su permiso y sus estados")
  void elAltaDeRolEstaDocumentada() throws Exception {
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/roles'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/roles'].post.summary").value("Registrar un rol"))
        .andExpect(jsonPath("$.paths['/api/v1/roles'].post.responses.201").exists())
        .andExpect(jsonPath("$.paths['/api/v1/roles'].post.responses.409").exists())
        .andExpect(jsonPath("$.paths['/api/v1/roles'].post.responses.422").exists());
  }

  @Test
  @DisplayName("el contrato sigue publicando GET /api/v1/permissions")
  void elCatalogoDePermisosSigueDocumentado() throws Exception {
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/permissions'].get").exists());
  }

  @Test
  @DisplayName("el alta de rol NO declara manejadores que el requerimiento no tiene")
  void sinVerbosNoDeclarados() throws Exception {
    // `RF-SP-001` solo declara el POST. Si algún día aparece aquí un PUT o un
    // DELETE sin que su requerimiento lo declare, es que alguien lo añadió sin
    // pasar por la compuerta.
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/roles'].put").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/roles'].delete").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/roles'].patch").doesNotExist());
  }
}
