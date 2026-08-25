package com.factech.nexus.shared.observability;

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
 * Sondas separadas y métricas (issue #31).
 *
 * <p><b>Qué vigila esto.</b> Que las dos sondas <b>existan y sean públicas</b> —quien las consulta
 * es el orquestador, que no porta credencial— y que las métricas <b>no</b> lo sean. Un despiste en
 * cualquiera de las dos direcciones es grave y silencioso: unas sondas cerradas hacen que el
 * contenedor concluya que la aplicación está enferma cuando está sana, y unas métricas abiertas
 * publican el mapa interno del sistema a quien pase por ahí.
 */
@AutoConfigureMockMvc
class ProbesAndMetricsIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("«¿hay que reiniciarlo?» — la sonda de vida responde sin credencial")
  void laSondaDeVidaEsPublica() throws Exception {
    mvc.perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  @DisplayName("«¿le mando tráfico?» — la sonda de disponibilidad responde sin credencial")
  void laSondaDeDisponibilidadEsPublica() throws Exception {
    // Es la que usa el `healthcheck` del contenedor, y por eso importa que sea
    // pública: si respondiera `401`, Docker leería un contenedor enfermo.
    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  @DisplayName("la salud general sigue sin contar el detalle interno")
  void laSaludNoRevelaElDetalle() throws Exception {
    // El detalle es un mapa del sistema —componentes, versiones, la URL de la
    // base— para quien lo sondea sin credenciales (Art. VI.5).
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist());
  }

  @Test
  @DisplayName("las métricas NO son públicas: el Art. XV.10 solo abre la salud")
  void lasMetricasExigenCredencial() throws Exception {
    mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("y con credencial sí: `http.server.requests` es lo que vuelve medible el p95")
  void lasMetricasEstanAhiParaQuienSeAutentica() throws Exception {
    // El Art. XV.9 fija umbrales p95 por endpoint. Hasta ahora no se podían
    // comprobar porque no había de dónde: ni esta métrica ni el `request_log`.
    mvc.perform(get("/actuator/metrics").with(user("operador")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.names").isArray());
  }

  @Test
  @DisplayName("lo que no se expuso sigue cerrado, y con credencial también")
  void elRestoDeActuatorNoSeExpone() throws Exception {
    // `env` y `beans` publican configuración y estructura interna. No están en
    // la lista de exposición, de modo que ni siquiera existen como ruta —y esta
    // prueba es lo que impide que un día entren «para depurar» y se queden.
    mvc.perform(get("/actuator/env").with(user("operador"))).andExpect(status().isNotFound());
    mvc.perform(get("/actuator/beans").with(user("operador"))).andExpect(status().isNotFound());
  }
}
