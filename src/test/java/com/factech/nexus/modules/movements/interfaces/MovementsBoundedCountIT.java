package com.factech.nexus.modules.movements.interfaces;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
 * `RF-MV-006` · `CA-MV-082` — el total del libro es acotado.
 *
 * <p>Mismo techo y misma forma que {@code AuditBoundedCountIT}, y <b>las mismas propiedades</b> a
 * propósito: un contexto de Spring con una propiedad distinta es un contexto más que levantar, y
 * con la misma se comparte el que ya existe.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "nexus.pagination.count-limit=3")
class MovementsBoundedCountIT extends IntegrationTestBase {
  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID sujeto;

  @BeforeEach
  void sembrar() {
    limpiar();
    sujeto = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, 'techo-sujeto', 'techo-sujeto@factech.co', 'Nombre', 'Apellido', 'x', false,
                'ACTIVO', (SELECT id FROM countries WHERE code = 'COL'))
        """,
        sujeto);
    darElSuelo(jdbc, sujeto);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName("por debajo del techo, el total es el real y se declara exacto")
  void totalExactoBajoElTecho() throws Exception {
    movimientos(2);

    mvc.perform(listado())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalIsExact").value(true));
  }

  @Test
  @DisplayName("por encima, el total es el techo y la respuesta DECLARA que no es exacto")
  void totalAcotadoSobreElTecho() throws Exception {
    movimientos(5);

    mvc.perform(listado())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalIsExact").value(false))
        // La página sigue siendo la pedida: el techo acota el conteo, no el contenido.
        .andExpect(jsonPath("$.content.length()").value(5));
  }

  private MockHttpServletRequestBuilder listado() {
    return get("/api/v1/movements")
        .param("size", "10")
        .with(user(UUID.randomUUID().toString()).authorities(() -> "movements:read"));
  }

  private void limpiar() {
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN"
            + " (SELECT id FROM users WHERE username = 'techo-sujeto')");
    jdbc.update("DELETE FROM users WHERE username = 'techo-sujeto'");
  }

  private void movimientos(int cuantos) {
    OffsetDateTime base = OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);
    for (int i = 0; i < cuantos; i++) {
      UUID id = UUID.randomUUID();
      jdbc.update(
          """
          INSERT INTO movements (id, movement_type_id, type_status_id, user_id, payment_method_id,
                                 currency_id, code, status, total_amount, discount_amount,
                                 payable_amount, occurred_at)
          VALUES (?, CAST(? AS uuid), (SELECT s.id FROM movement_type_statuses s WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = 'VALIDADO'), ?, CAST(? AS uuid), CAST(? AS uuid), ?, 'PENDIENTE',
                  100.00, 0, 100.00, CAST(? AS timestamptz))
          """,
          id,
          VENTA,
          VENTA,
          sujeto,
          TARJETA,
          USD,
          "VTA-" + id.toString().substring(0, 8).toUpperCase(),
          base.plusMinutes(i).toString());
    }
  }
}
