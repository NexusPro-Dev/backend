package com.factech.nexus.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CORS de extremo a extremo, sobre la cadena de seguridad real.
 *
 * <p>Se prueba con la <b>comprobación previa</b> del navegador ({@code OPTIONS} con {@code
 * Access-Control-Request-Method}) porque es lo que decide si la llamada real llega a emitirse. Que
 * el endpoint funcione con {@code curl} no dice nada: {@code curl} no envía origen y no mira la
 * respuesta.
 *
 * <p>La lista de orígenes se declara aquí como propiedad de prueba, igual que hace {@link
 * IntegrationTestBase} con las demás variables: lo que se prueba es la configuración real, y solo
 * se le da el origen de los datos.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "CORS_ALLOWED_ORIGINS=https://app.nexus.test")
class CorsIT extends IntegrationTestBase {

  private static final String AUTORIZADO = "https://app.nexus.test";
  private static final String AJENO = "https://sitio-de-otro.test";

  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("la comprobación previa de un origen autorizado pasa sin token")
  void elOrigenAutorizadoPasaLaComprobacionPrevia() throws Exception {
    // El navegador NUNCA adjunta `Authorization` en la comprobación previa. Si
    // la cadena de seguridad la exigiera, toda llamada del frontend a una ruta
    // protegida moriría antes de emitirse — que es el motivo por el que el
    // filtro de CORS va delante de la autorización.
    mvc.perform(
            options("/api/v1/users")
                .header("Origin", AUTORIZADO)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", AUTORIZADO))
        .andExpect(header().string("Access-Control-Allow-Headers", "Authorization"))
        // Sin cookies de sesión (D-08) no se autoriza el envío de credenciales.
        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
  }

  @Test
  @DisplayName("un origen no declarado se rechaza en la comprobación previa")
  void elOrigenAjenoNoPasa() throws Exception {
    mvc.perform(
            options("/api/v1/users")
                .header("Origin", AJENO)
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }

  @Test
  @DisplayName("el 401 también lleva la cabecera de origen")
  void elRechazoDeAutorizacionSigueSiendoLegible() throws Exception {
    // Sin la cabecera en la respuesta de error, el navegador oculta el cuerpo y
    // el frontend no puede distinguir «no autenticado» de «el servidor no
    // responde»: acabaría mostrando un fallo de red ante un 401 perfectamente
    // normal.
    mvc.perform(get("/api/v1/users").header("Origin", AUTORIZADO))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("Access-Control-Allow-Origin", AUTORIZADO));
  }

  @Test
  @DisplayName("el método no declarado se rechaza aunque el origen sea el correcto")
  void unMetodoFueraDelContratoNoSeAutoriza() throws Exception {
    mvc.perform(
            options("/api/v1/users")
                .header("Origin", AUTORIZADO)
                .header("Access-Control-Request-Method", "TRACE"))
        .andExpect(status().isForbidden());
  }
}
