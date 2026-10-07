package com.factech.nexus.modules.indicators.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.CommissionCleanup;
import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * `RF-IN-007` · `T-05` — el resumen de lotes de comisiones (`CA-IN-072` a `CA-IN-079`).
 *
 * <p>Los lotes se siembran por SQL, en centésimas, y se cambian por SQL como los cambian el cierre,
 * el pago y la retirada de `CM`: el indicador lee el estado y el total de cada lote, y esas
 * operaciones tienen sus propias suites (`tasks.md` §3).
 *
 * <pre>
 *   b1 agente1 USD ABIERTO    10,00   desde 01-10
 *   b2 agente2 USD ABIERTO     5,50   desde 01-10
 *   b3 agente1 COP ABIERTO  1000,00   desde 01-10
 *   b4 agente1 USD PENDIENTE  20,00   01-09 → 01-10
 *   b5 agente2 USD PENDIENTE   0,00   01-09 → 01-10   ← vaciado por retiradas
 *   b6 agente1 USD PAGADO     30,00   01-08 → 01-09
 * </pre>
 */
@AutoConfigureMockMvc
class CommissionBatchesSummaryIT extends IntegrationTestBase {
  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";
  private static final String COP = "01a03336-6d00-7002-9c4f-5e7ad3000002";
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";

  private static final String RUTA = "/api/v1/indicators/commissions/batches/summary";
  private static final String PERMISO = "indicators:read-commission-batches-summary";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory emf;

  private UUID funcionario;
  private UUID director;
  private UUID agente1;
  private UUID agente2;
  private UUID cierre;
  private UUID b1;
  private UUID b4;

  @BeforeEach
  void sembrar() {
    limpiar();
    funcionario = persona("icb-funcionario", ADMIN);
    director = persona("icb-director", DIRECTOR);
    agente1 = persona("icb-agente1", AGENTE);
    agente2 = persona("icb-agente2", AGENTE);
    reponerElSuelo(jdbc);
    cierre = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO commission_closings (id, origin, triggered_by, started_at, closed_at,"
            + " created_at) VALUES (?, 'MANUAL', ?, now(), now(), now())",
        cierre,
        funcionario);

    b1 = lote(agente1, USD, "ABIERTO", "2026-10-01", null, 1000);
    lote(agente2, USD, "ABIERTO", "2026-10-01", null, 550);
    lote(agente1, COP, "ABIERTO", "2026-10-01", null, 100000);
    b4 = lote(agente1, USD, "PENDIENTE", "2026-09-01", "2026-10-01", 2000);
    lote(agente2, USD, "PENDIENTE", "2026-09-01", "2026-10-01", 0);
    lote(agente1, USD, "PAGADO", "2026-08-01", "2026-09-01", 3000);
  }

  @AfterEach
  void devolverLaBaseASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "CA-IN-072 y CA-IN-074 — por estado, cuántos lotes y su valor por moneda; el total es la"
          + " suma de los tres, sin mezclar monedas")
  void porEstadoYEnTotal() throws Exception {
    resumen(funcionario)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.open.batches").value(3))
        .andExpect(jsonPath("$.open.amounts[*].currency.code", contains("COP", "USD")))
        .andExpect(jsonPath("$.open.amounts[0].amount").value(1000.0))
        .andExpect(jsonPath("$.open.amounts[1].amount").value(15.5))
        .andExpect(jsonPath("$.pending.batches").value(2))
        .andExpect(jsonPath("$.pending.amounts[*].currency.code", contains("USD")))
        .andExpect(jsonPath("$.pending.amounts[0].amount").value(20.0))
        .andExpect(jsonPath("$.paid.batches").value(1))
        .andExpect(jsonPath("$.paid.amounts[0].amount").value(30.0))
        .andExpect(jsonPath("$.total.batches").value(6))
        .andExpect(jsonPath("$.total.amounts[*].currency.code", contains("COP", "USD")))
        .andExpect(jsonPath("$.total.amounts[0].amount").value(1000.0))
        .andExpect(jsonPath("$.total.amounts[1].amount").value(65.5))
        .andExpect(jsonPath("$.period").doesNotExist());
  }

  @Test
  @DisplayName("CA-IN-073 — sin lotes, los cuatro bloques vienen igual, en cero")
  void sinLotes() throws Exception {
    CommissionCleanup.limpiar(jdbc);
    resumen(funcionario)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.open.batches").value(0))
        .andExpect(jsonPath("$.open.amounts").isEmpty())
        .andExpect(jsonPath("$.pending.batches").value(0))
        .andExpect(jsonPath("$.pending.amounts").isEmpty())
        .andExpect(jsonPath("$.paid.batches").value(0))
        .andExpect(jsonPath("$.paid.amounts").isEmpty())
        .andExpect(jsonPath("$.total.batches").value(0))
        .andExpect(jsonPath("$.total.amounts").isEmpty());
  }

  @Test
  @DisplayName("CA-IN-075 — con una moneda, solo sus lotes; una inexistente da ceros")
  void porMoneda() throws Exception {
    mvc.perform(get(RUTA).param("currencyId", COP).with(conPermiso(funcionario)))
        .andExpect(jsonPath("$.open.batches").value(1))
        .andExpect(jsonPath("$.open.amounts[*].currency.code", contains("COP")))
        .andExpect(jsonPath("$.pending.batches").value(0))
        .andExpect(jsonPath("$.paid.batches").value(0))
        .andExpect(jsonPath("$.total.batches").value(1));

    mvc.perform(
            get(RUTA)
                .param("currencyId", UUID.randomUUID().toString())
                .with(conPermiso(funcionario)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total.batches").value(0))
        .andExpect(jsonPath("$.total.amounts").isEmpty());

    mvc.perform(get(RUTA).param("currencyId", "no-es-uuid").with(conPermiso(funcionario)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "CA-IN-076 — una foto de hoy: cerrar y pagar mueven el lote de bloque, y las fechas se"
          + " ignoran")
  void unaFotoDeHoy() throws Exception {
    String antes = resumen(funcionario).andReturn().getResponse().getContentAsString();
    String conFechas =
        mvc.perform(
                get(RUTA)
                    .param("from", "2026-01-01")
                    .param("to", "2026-01-31")
                    .param("granularity", "MONTH")
                    .with(conPermiso(funcionario)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(conFechas).isEqualTo(antes);

    // El cierre: el abierto b1 pasa a pendiente.
    jdbc.update(
        "UPDATE commission_batches SET status = 'PENDIENTE', period_end = now(), closing_id = ?"
            + " WHERE id = ?",
        cierre,
        b1);
    resumen(funcionario)
        .andExpect(jsonPath("$.open.batches").value(2))
        .andExpect(jsonPath("$.open.amounts[1].amount").value(5.5))
        .andExpect(jsonPath("$.pending.batches").value(3))
        .andExpect(jsonPath("$.pending.amounts[0].amount").value(30.0))
        .andExpect(jsonPath("$.total.batches").value(6));

    // El pago: el pendiente b4 pasa a pagado.
    jdbc.update(
        "UPDATE commission_batches SET status = 'PAGADO', paid_at = now(), movement_id = ?"
            + " WHERE id = ?",
        abono(),
        b4);
    resumen(funcionario)
        .andExpect(jsonPath("$.pending.batches").value(2))
        .andExpect(jsonPath("$.pending.amounts[0].amount").value(10.0))
        .andExpect(jsonPath("$.paid.batches").value(2))
        .andExpect(jsonPath("$.paid.amounts[0].amount").value(50.0))
        .andExpect(jsonPath("$.total.amounts[1].amount").value(65.5));
  }

  @Test
  @DisplayName(
      "CA-IN-077 — el valor es el de hoy: lo devengado suma al abierto, y lo retirado de un"
          + " pendiente pasa al abierto")
  void elValorDeHoy() throws Exception {
    // Un devengo de 2,00 en el abierto.
    jdbc.update("UPDATE commission_batches SET total_amount = total_amount + 200 WHERE id = ?", b1);
    // Una retirada de 5,00 del pendiente b4 al abierto b1, en la misma transacción.
    jdbc.update("UPDATE commission_batches SET total_amount = total_amount - 500 WHERE id = ?", b4);
    jdbc.update("UPDATE commission_batches SET total_amount = total_amount + 500 WHERE id = ?", b1);

    resumen(funcionario)
        .andExpect(jsonPath("$.open.amounts[1].amount").value(22.5))
        .andExpect(jsonPath("$.pending.amounts[0].amount").value(15.0))
        .andExpect(jsonPath("$.total.amounts[1].amount").value(67.5));
  }

  @Test
  @DisplayName("CA-IN-078 — sin alcance: un director con el permiso ve lo mismo que administración")
  void sinAlcance() throws Exception {
    String deAdmin = resumen(funcionario).andReturn().getResponse().getContentAsString();
    String deDirector = resumen(director).andReturn().getResponse().getContentAsString();
    assertThat(deDirector).isEqualTo(deAdmin);
  }

  @Test
  @DisplayName(
      "CA-IN-079 — sin el permiso, 403, también con los de lotes de CM o de otros indicadores; sin"
          + " token, 401; sembrado solo a SUPERADMIN y ADMIN")
  void permisos() throws Exception {
    mvc.perform(get(RUTA).with(user(funcionario.toString()))).andExpect(status().isForbidden());
    for (String otro :
        new String[] {
          "commission-batches:read",
          "commission-batches:list-own",
          "indicators:read-sale-lines-summary"
        }) {
      mvc.perform(get(RUTA).with(user(funcionario.toString()).authorities(() -> otro)))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());

    assertThat(
            jdbc.queryForList(
                """
                SELECT r.code FROM roles r
                  JOIN role_permissions rp ON rp.role_id = r.id
                  JOIN permissions p ON p.id = rp.permission_id
                 WHERE p.code = ?
                """,
                String.class,
                PERMISO))
        .containsExactlyInAnyOrder("SUPERADMIN", "ADMIN");
  }

  @Test
  @DisplayName("RNF-PERF — las mismas sentencias con seis lotes que con dieciséis")
  void elCosteNoCreceConLosLotes() throws Exception {
    Statistics estadisticas = emf.unwrap(SessionFactory.class).getStatistics();

    estadisticas.clear();
    resumen(funcionario).andExpect(status().isOk());
    long seis = estadisticas.getPrepareStatementCount();

    for (int i = 0; i < 10; i++) {
      UUID otra = persona("icb-otra" + i, AGENTE);
      lote(otra, i % 2 == 0 ? USD : COP, "ABIERTO", "2026-10-01", null, 100);
    }
    estadisticas.clear();
    resumen(funcionario).andExpect(jsonPath("$.total.batches").value(16));
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(seis);
  }

  // ---------------------------------------------------------------------------

  private ResultActions resumen(UUID actor) throws Exception {
    return mvc.perform(get(RUTA).with(conPermiso(actor)));
  }

  private static RequestPostProcessor conPermiso(UUID persona) {
    return user(persona.toString()).authorities(() -> PERMISO);
  }

  private void limpiar() {
    CommissionCleanup.limpiar(jdbc);
    jdbc.update(
        "DELETE FROM movements WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'icb-%')");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'icb-%')");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'icb-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'icb-%'");
  }

  private UUID persona(String username, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        id,
        rol);
    return id;
  }

  /**
   * Un lote. Uno cerrado lleva su fin y el cierre; uno pagado, además, su fecha de pago y su abono.
   */
  private UUID lote(
      UUID persona, String moneda, String estado, String desde, String hasta, long centesimas) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO commission_batches (id, code, user_id, currency_id, period_start, period_end,
                                        status, closing_id, total_amount, paid_at, movement_id,
                                        created_at, updated_at)
        VALUES (?, ?, ?, CAST(? AS uuid), CAST(? AS timestamptz), CAST(? AS timestamptz), ?, ?, ?,
                CASE WHEN ? = 'PAGADO' THEN now() END, ?, now(), now())
        """,
        id,
        "ICB-" + id.toString().substring(0, 8).toUpperCase(),
        persona,
        moneda,
        desde + "T05:00:00Z",
        hasta == null ? null : hasta + "T05:00:00Z",
        estado,
        estado.equals("ABIERTO") ? null : cierre,
        centesimas,
        estado,
        estado.equals("PAGADO") ? abono() : null);
    return id;
  }

  /** Un movimiento al que señalar como abono: el lote solo exige que exista. */
  private UUID abono() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id, currency_id, code,
                               status, total_amount, discount_amount, payable_amount, occurred_at,
                               confirmed_at)
        VALUES (?, CAST(? AS uuid),
                (SELECT s.id FROM movement_types t
                   JOIN movement_type_statuses s ON s.movement_type_id = t.id AND s.code = 'VALIDADO'
                  WHERE t.id = CAST(? AS uuid)),
                ?, CAST(? AS uuid), ?, 'CONFIRMADA', 0, 0, 0, now(), now())
        """,
        id,
        VENTA,
        VENTA,
        funcionario,
        USD,
        "ICB-" + id.toString().substring(0, 8).toUpperCase());
    return id;
  }
}
