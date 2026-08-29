package com.factech.nexus.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El registro de peticiones (Art. XV.2 y XV.4, issue #23).
 *
 * <p>Lo que estas pruebas vigilan es <b>lo que antes no dejaba rastro</b>: un `404`, un `400` de
 * formato, un barrido de rutas. El manejador global decide no auditar esos casos «porque
 * `request_log` ya lo cubre», y hasta ahora la tabla no existía — de modo que no los cubría nadie.
 */
@AutoConfigureMockMvc
class RequestLogIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void empezarConLaTablaVacia() {
    jdbc.update("DELETE FROM request_log");
  }

  @Test
  @DisplayName("una petición atendida deja su fila, con todo lo que el Art. XV.2 exige")
  void laPeticionAtendidaSeRegistra() throws Exception {
    // La consulta va en la URL y no con `.param()`: aquella la rellena MockMvc
    // sin tocar el `query string`, y lo que esta prueba comprueba es justo que
    // los parámetros quedan registrados como llegan en un contenedor real.
    mvc.perform(
            get("/api/v1/roles?size=5")
                .with(user(SUPERADMIN.toString()).authorities(() -> "roles:read")))
        .andExpect(status().isOk());

    Map<String, Object> fila = ultima();

    assertThat(fila.get("method")).isEqualTo("GET");
    assertThat(fila.get("path")).isEqualTo("/api/v1/roles");
    assertThat(fila.get("query_string")).isEqualTo("size=5");
    assertThat(((Number) fila.get("status")).intValue()).isEqualTo(200);
    assertThat(fila.get("correlation_id")).isNotNull();
    // La duración es lo que hace verificables los umbrales p95 del Art. XV.9,
    // que hasta ahora no se podían medir porque no había de dónde.
    assertThat(((Number) fila.get("duration_ms")).longValue()).isGreaterThanOrEqualTo(0);
    // Autenticada: el actor no es anónimo. Se lee al TERMINAR la petición,
    // porque al empezar el filtro la seguridad todavía no ha resuelto nada.
    assertThat(fila.get("actor_id")).isEqualTo(SUPERADMIN);
  }

  @Test
  @DisplayName("el `404` deja rastro: es lo que el manejador global da por hecho y faltaba")
  void elCuatrocientosCuatroSeRegistra() throws Exception {
    mvc.perform(
            get("/api/v1/roles/{id}", UUID.randomUUID())
                .with(user(SUPERADMIN.toString()).authorities(() -> "roles:read")))
        .andExpect(status().isNotFound());

    assertThat(((Number) ultima().get("status")).intValue()).isEqualTo(404);
  }

  @Test
  @DisplayName("un barrido de rutas inexistentes queda registrado entero")
  void elBarridoDeRutasQuedaRegistrado() throws Exception {
    // El reconocimiento previo a un ataque es exactamente esto: peticiones que
    // no llegan a ejecutar nada. Antes eran invisibles.
    for (String ruta : java.util.List.of("/api/v1/admin", "/api/v1/.env", "/api/v1/config")) {
      mvc.perform(get(ruta).with(user("curioso")));
    }

    assertThat(cuantas()).isEqualTo(3);
  }

  @Test
  @DisplayName("el `400` de formato también, y sin el cuerpo que lo provocó")
  void elCuatrocientosSeRegistraSinCuerpo() throws Exception {
    mvc.perform(
            post("/api/v1/roles")
                .with(user(SUPERADMIN.toString()).authorities(() -> "roles:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"\",\"secreto\":\"ClaveSuperSecreta2026\"}"))
        .andExpect(status().isBadRequest());

    Map<String, Object> fila = ultima();
    assertThat(((Number) fila.get("status")).intValue()).isEqualTo(400);

    // NI CUERPO NI CABECERAS. Ahí viajan contraseñas, y ningún saneador es de
    // fiar sobre un contenido arbitrario: la única forma segura de no registrar
    // un secreto es no registrar el cuerpo (Art. VI.5).
    assertThat(fila.toString()).doesNotContain("ClaveSuperSecreta2026");
    assertThat(fila).doesNotContainKeys("body", "headers");
  }

  @Test
  @DisplayName("la correlación de la fila es la MISMA que el cliente recibió en la respuesta")
  void laCorrelacionEnlazaConLaRespuesta() throws Exception {
    String devuelta =
        mvc.perform(
                get("/api/v1/permissions")
                    .with(user(SUPERADMIN.toString()).authorities(() -> "permissions:read")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("X-Correlation-Id");

    // Es lo que hace útil al identificador que se le muestra a quien reporta un
    // error: sin esta igualdad, citarlo no lleva a ninguna parte.
    assertThat(ultima().get("correlation_id")).hasToString(devuelta);
  }

  @Test
  @DisplayName("una petición anónima se registra con actor nulo, que significa anónimo")
  void laPeticionAnonimaSeRegistra() throws Exception {
    mvc.perform(get("/api/v1/roles")).andExpect(status().isUnauthorized());

    Map<String, Object> fila = ultima();
    assertThat(((Number) fila.get("status")).intValue()).isEqualTo(401);
    assertThat(fila.get("actor_id")).isNull();
  }

  @Test
  @DisplayName("la sonda de salud NO se registra: son ocho mil filas diarias que no dicen nada")
  void laSondaDeSaludNoSeRegistra() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());

    assertThat(cuantas()).isZero();
  }

  @Test
  @DisplayName("el registro sobrevive a una operación revertida: la petición ocurrió igual")
  void laOperacionFallidaTambienDejaRastro() throws Exception {
    // Es la diferencia con los cuatro registros de auditoría, que se unen a la
    // transacción de negocio para desaparecer con ella. Este va aparte a
    // propósito (Art. XV.7): que el negocio fallara no significa que nadie
    // llamara.
    mvc.perform(
            post("/api/v1/roles")
                .with(user(SUPERADMIN.toString()).authorities(() -> "roles:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    // `ADMIN` ya existe en la siembra: el alta muere con 409 y
                    // la transacción de negocio se revierte entera.
                    "{\"code\":\"ADMIN\",\"name\":\"Duplicado\",\"roleType\":\"FUNCIONARIO\","
                        + "\"parentRoleId\":\"01a02a33-4c00-7002-9c4f-5e7ad1000002\"}"))
        .andExpect(status().isConflict());

    assertThat(((Number) ultima().get("status")).intValue()).isEqualTo(409);
  }

  private Map<String, Object> ultima() {
    return jdbc.queryForMap("SELECT * FROM request_log ORDER BY occurred_at DESC, id DESC LIMIT 1");
  }

  private long cuantas() {
    return jdbc.queryForObject("SELECT count(*) FROM request_log", Long.class);
  }
}
