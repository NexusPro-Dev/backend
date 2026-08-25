package com.factech.nexus.modules.system.audit.interfaces;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * El conteo acotado de los listados de auditoría (`RF-SP-011`).
 *
 * <p><b>El techo se baja a tres para poder probarlo.</b> Con el valor real —diez mil— haría falta
 * sembrar diez mil filas para ver la diferencia, y una prueba que tarda un minuto en preparar datos
 * acaba borrándose. Que el techo sea configuración y no una constante es justo lo que permite
 * probarlo: si estuviera escrito en el código, esta clase no existiría.
 *
 * <p>Lo que se verifica no es el número sino <b>las dos propiedades</b> que hacen útil al conteo
 * acotado: que por debajo del techo el total es el real, y que por encima la respuesta <b>declara
 * que no lo es</b> en lugar de mentir. Un total inexacto sin la marca sería peor que no darlo.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "nexus.pagination.count-limit=3")
class AuditBoundedCountIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void limpiar() {
    jdbc.update("DELETE FROM audit_error_log");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    jdbc.update("DELETE FROM audit_error_log");
  }

  @Test
  @DisplayName("por debajo del techo, el total es el real y se declara exacto")
  void totalExactoBajoElTecho() throws Exception {
    sembrar(2);

    mvc.perform(errores())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalIsExact").value(true));
  }

  @Test
  @DisplayName("justo en el techo sigue siendo exacto: el límite es «más de», no «hasta»")
  void totalExactoEnElTecho() throws Exception {
    sembrar(3);

    mvc.perform(errores())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalIsExact").value(true));
  }

  @Test
  @DisplayName("por encima, el total es el techo y la respuesta DECLARA que no es exacto")
  void totalAcotadoSobreElTecho() throws Exception {
    sembrar(7);

    mvc.perform(errores())
        .andExpect(status().isOk())
        // Ni 7 ni un número inventado: el techo, con la marca que dice «hay más».
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalIsExact").value(false));
  }

  @Test
  @DisplayName("el techo acota el conteo, NO el contenido: la página trae lo que quepa en ella")
  void elTechoNoLimitaLaPagina() throws Exception {
    sembrar(7);

    mvc.perform(errores().param("size", "5"))
        .andExpect(status().isOk())
        // Cinco filas de verdad, aunque el total diga tres: son dos preguntas
        // distintas y confundirlas convertiría el techo en un muro.
        .andExpect(jsonPath("$.content.length()").value(5))
        .andExpect(jsonPath("$.totalIsExact").value(false));
  }

  @Test
  @DisplayName("una página más allá de la cota sigue devolviendo lo que hay")
  void masAllaDeLaCota() throws Exception {
    sembrar(7);

    // `totalPages` vale 3 con techo 3 y tamaño 1, pero la página 5 existe: si el
    // techo cerrara el paso, las filas por encima de él serían inalcanzables.
    mvc.perform(errores().param("size", "1").param("page", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1));

    mvc.perform(errores().param("size", "1").param("page", "99"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  private MockHttpServletRequestBuilder errores() {
    return get("/api/v1/audit/errors")
        .with(user(SUPERADMIN.toString()).authorities(() -> "audit:read-errors"));
  }

  /** Filas de error sintéticas: aquí lo que se prueba es el conteo, no lo que se escribió. */
  private void sembrar(int cuantas) {
    for (int i = 0; i < cuantas; i++) {
      jdbc.update(
          """
          INSERT INTO audit_error_log (id, occurred_at, actor_id, correlation_id, ip_address,
                                       user_agent, resource, entity_id, operation, error_code,
                                       error_type, http_status, severity, message)
          VALUES (?, now() - (? * interval '1 second'), NULL, NULL, NULL, NULL,
                  'pruebas', NULL, 'GET /pruebas', 'ERR-500', 'UNHANDLED', 500, 'ALTA', 'Fallo.')
          """,
          UUID.randomUUID(),
          i);
    }
  }
}
