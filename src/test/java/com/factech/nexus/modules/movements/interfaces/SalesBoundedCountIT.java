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
 * `RF-MV-015` · `CA-MV-132` — el total de las ventas de mi alcance es acotado, como el del libro.
 *
 * <p>Las mismas propiedades que {@code MovementsBoundedCountIT} a propósito: comparte contexto. El
 * actor es un vendedor sin nadie a cargo: su alcance es él mismo como vendedor de la línea, y las
 * ventas se siembran con él en cada línea.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "nexus.pagination.count-limit=3")
class SalesBoundedCountIT extends IntegrationTestBase {
  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID vendedor;
  private UUID producto;

  @BeforeEach
  void sembrar() {
    limpiar();
    vendedor = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, 'techo-vendedor', 'techo-vendedor@factech.co', 'Nombre', 'Apellido', 'x', false,
                'ACTIVO', (SELECT id FROM countries WHERE code = 'COL'))
        """,
        vendedor);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        vendedor,
        AGENTE);
    producto = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description, source_membership_id,"
            + " target_membership_id, price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', 'MANUAL', ?, 'TECHO_BOT', 'BOT', 'Bot del techo', 'Producto de prueba', NULL,"
            + " NULL, CAST(? AS numeric), CAST(? AS uuid), NULL, 'ACTIVO')",
        producto,
        "100.00",
        USD);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName("por debajo del techo, el total es el real y se declara exacto")
  void totalExactoBajoElTecho() throws Exception {
    ventas(2);
    mvc.perform(listado())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalIsExact").value(true));
  }

  @Test
  @DisplayName("por encima, el total es el techo y la respuesta DECLARA que no es exacto")
  void totalAcotadoSobreElTecho() throws Exception {
    ventas(5);
    mvc.perform(listado())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalIsExact").value(false))
        .andExpect(jsonPath("$.content.length()").value(5));
  }

  private MockHttpServletRequestBuilder listado() {
    return get("/api/v1/movements/sales")
        .param("size", "10")
        .with(user(vendedor.toString()).authorities(() -> "movements:list-sales"));
  }

  private void limpiar() {
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM products WHERE code = 'TECHO_BOT'");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN"
            + " (SELECT id FROM users WHERE username = 'techo-vendedor')");
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN"
            + " (SELECT id FROM users WHERE username = 'techo-vendedor')");
    jdbc.update("DELETE FROM users WHERE username = 'techo-vendedor'");
  }

  private void ventas(int cuantas) {
    OffsetDateTime base = OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);
    for (int i = 0; i < cuantas; i++) {
      UUID id = UUID.randomUUID();
      jdbc.update(
          """
          INSERT INTO movements (id, movement_type_id, user_id, payment_method_id,
                                 currency_id, code, status, total_amount, discount_amount,
                                 payable_amount, occurred_at)
          VALUES (?, CAST(? AS uuid), ?, CAST(? AS uuid), CAST(? AS uuid), ?, 'PENDIENTE',
                  100.00, 0, 100.00, CAST(? AS timestamptz))
          """,
          id,
          VENTA,
          vendedor,
          TARJETA,
          USD,
          "VTA-" + id.toString().substring(0, 8).toUpperCase(),
          base.plusMinutes(i).toString());
      jdbc.update(
          """
          INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                        product_description, quantity, unit_price,
                                        line_amount, validity_days, implementation)
          VALUES (?, ?, ?, ?, 'Bot del techo', 'Lo que decia el catalogo', 1, 100.00, 100.00, NULL,
                  'MANUAL')
          """,
          UUID.randomUUID(),
          id,
          producto,
          vendedor);
    }
  }
}
