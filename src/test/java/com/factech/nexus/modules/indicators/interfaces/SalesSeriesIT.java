package com.factech.nexus.modules.indicators.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.testing.CommissionCleanup;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

/**
 * `RF-IN-002` · `T-05` — la evolución de las ventas (`CA-IN-015` a `CA-IN-022`).
 *
 * <pre>
 *   director1 ─── agente1        director2 (otra rama)
 * </pre>
 *
 * <p>Todo en septiembre de 2026; el 7, el 14 y el 21 son lunes. Los importes, en centésimas.
 */
@AutoConfigureMockMvc
class SalesSeriesIT extends IntegrationTestBase {
  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";
  private static final String COP = "01a03336-6d00-7002-9c4f-5e7ad3000002";
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";

  private static final String SERIE = "/api/v1/indicators/sales/series";
  private static final String RESUMEN = "/api/v1/indicators/sales/summary";
  private static final String PERMISO = "indicators:read-sales-series";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory emf;

  private UUID director1;
  private UUID director2;
  private UUID agente1;
  private UUID producto;

  @BeforeEach
  void sembrar() {
    limpiar();
    director1 = persona("ins-director1", DIRECTOR);
    director2 = persona("ins-director2", DIRECTOR);
    agente1 = persona("ins-agente1", AGENTE);
    producto = producto("INS_BOT");
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at)"
            + " VALUES (gen_random_uuid(), ?, ?, now())",
        agente1,
        director1);

    venta(agente1, "CONFIRMADA", USD, "2026-09-08T15:00:00Z", 1000);
    venta(agente1, "CONFIRMADA", COP, "2026-09-10T15:00:00Z", 100000);
    venta(agente1, "PENDIENTE", USD, "2026-09-11T15:00:00Z", 700);
    // Las 20:00 del 15 en Bogotá, que en UTC ya es el 16.
    venta(agente1, "CONFIRMADA", USD, "2026-09-16T01:00:00Z", 100);
    venta(director1, "CONFIRMADA", USD, "2026-09-22T15:00:00Z", 10000);
    venta(director2, "CONFIRMADA", USD, "2026-09-09T15:00:00Z", 500000);
  }

  @AfterEach
  void devolverLaBaseASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "CA-IN-016 y CA-IN-017 — todos los días, también los vacíos, y en cada uno todas las"
          + " monedas del periodo, en orden")
  void diasCompletos() throws Exception {
    serie(director1, "2026-09-08", "2026-09-14", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.granularity").value("DAY"))
        .andExpect(jsonPath("$.currencies[*].code", contains("COP", "USD")))
        .andExpect(jsonPath("$.buckets.length()").value(7))
        .andExpect(jsonPath("$.buckets[0].start").value("2026-09-08"))
        .andExpect(jsonPath("$.buckets[0].sales").value(1))
        .andExpect(jsonPath("$.buckets[0].amounts[0].currency.code").value("COP"))
        .andExpect(jsonPath("$.buckets[0].amounts[0].amount").value(0.0))
        .andExpect(jsonPath("$.buckets[0].amounts[1].amount").value(10.0))
        // El 9 vendió director2, que no es de esta rama.
        .andExpect(jsonPath("$.buckets[1].sales").value(0))
        .andExpect(jsonPath("$.buckets[1].amounts.length()").value(2))
        .andExpect(jsonPath("$.buckets[2].amounts[0].amount").value(1000.0))
        // El 11 hay una pendiente: no es lo vendido.
        .andExpect(jsonPath("$.buckets[3].sales").value(0))
        .andExpect(jsonPath("$.buckets[6].start").value("2026-09-14"));
  }

  @Test
  @DisplayName("CA-IN-015 — la suma de los tramos es lo confirmado del resumen, por moneda")
  void cuadraConElResumen() throws Exception {
    for (String tramo : new String[] {"DAY", "WEEK", "MONTH"}) {
      cuadran(director1, tramo, null);
    }
  }

  @Test
  @DisplayName(
      "CA-IN-101 — con oficina, la suma de los tramos es lo confirmado del resumen con la misma"
          + " oficina; un traslado no mueve lo vendido; lo sin vendedor no cuenta; fuera del"
          + " alcance o inexistente, ceros; mal formada, 400")
  void conOficina() throws Exception {
    // A es la oficina de director1 y B la de director2; las líneas guardan la
    // suya. Una venta sin vendedor, que no tiene oficina, el 12.
    UUID funcionario = persona("ins-funcionario", ADMIN);
    UUID a = oficina("A", director1);
    UUID b = oficina("B", director2);
    guardarOficina(a, director1, agente1);
    guardarOficina(b, director2);
    venta(null, "CONFIRMADA", USD, "2026-09-12T15:00:00Z", 30000);
    // Después, director1 se traslada a la oficina C.
    UUID nueva = oficina("C", null);
    trasladar(director1, nueva);

    for (String tramo : new String[] {"DAY", "WEEK", "MONTH"}) {
      // Las 4 confirmadas de la rama de director1, en A: ni la del 12 ni la de B.
      assertThat(cuadran(funcionario, tramo, a.toString())).as(tramo).isEqualTo(4);
      assertThat(cuadran(director1, tramo, a.toString())).as(tramo).isEqualTo(4);
    }
    serieDeOficina(funcionario, a)
        .andExpect(jsonPath("$.currencies[*].code", contains("COP", "USD")))
        .andExpect(jsonPath("$.buckets[0].sales").value(4))
        .andExpect(jsonPath("$.buckets[0].amounts[0].amount").value(1000.0))
        .andExpect(jsonPath("$.buckets[0].amounts[1].amount").value(111.0));
    serieDeOficina(funcionario, b)
        .andExpect(jsonPath("$.currencies[*].code", contains("USD")))
        .andExpect(jsonPath("$.buckets[0].sales").value(1))
        .andExpect(jsonPath("$.buckets[0].amounts[0].amount").value(5000.0));

    // La oficina de hoy de director1, la ajena para él, y una inexistente:
    // todos los tramos en cero.
    for (UUID[] caso :
        new UUID[][] {{funcionario, nueva}, {director1, b}, {funcionario, UUID.randomUUID()}}) {
      serieDeOficina(caso[0], caso[1])
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.buckets.length()").value(1))
          .andExpect(jsonPath("$.currencies").isEmpty())
          .andExpect(jsonPath("$.buckets[0].sales").value(0));
    }
    mvc.perform(
            get(SERIE)
                .param("teamId", "no-es-uuid")
                .with(user(funcionario.toString()).authorities(() -> PERMISO)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));
  }

  @Test
  @DisplayName(
      "CA-IN-018 — semanas de lunes y meses del calendario, recortados al periodo, con su inicio")
  void tramosDelCalendario() throws Exception {
    // Del jueves 10 al miércoles 23: la primera semana empieza el lunes 7, pero
    // la venta del 8 queda fuera del periodo.
    serie(director1, "2026-09-10", "2026-09-23", "WEEK")
        .andExpect(
            jsonPath("$.buckets[*].start", contains("2026-09-07", "2026-09-14", "2026-09-21")))
        .andExpect(jsonPath("$.buckets[*].sales", contains(1, 1, 1)))
        .andExpect(jsonPath("$.buckets[0].amounts[0].amount").value(1000.0))
        .andExpect(jsonPath("$.buckets[0].amounts[1].amount").value(0.0))
        .andExpect(jsonPath("$.buckets[1].amounts[1].amount").value(1.0))
        .andExpect(jsonPath("$.buckets[2].amounts[1].amount").value(100.0));

    serie(director1, "2026-08-31", "2026-10-01", "month")
        .andExpect(jsonPath("$.granularity").value("MONTH"))
        .andExpect(
            jsonPath("$.buckets[*].start", contains("2026-08-01", "2026-09-01", "2026-10-01")))
        .andExpect(jsonPath("$.buckets[*].sales", contains(0, 4, 0)));
  }

  @Test
  @DisplayName("CA-IN-019 — una venta a las 20:00 de Bogotá cae en su día, no en el de UTC")
  void diaDeBogota() throws Exception {
    serie(agente1, "2026-09-15", "2026-09-16", "DAY")
        .andExpect(jsonPath("$.buckets[*].sales", contains(1, 0)))
        .andExpect(jsonPath("$.buckets[0].amounts[0].amount").value(1.0));
  }

  @Test
  @DisplayName("CA-IN-020 — sin tramo, días; sin fechas, el mes en curso hasta hoy")
  void porDefecto() throws Exception {
    // CA-IN-055: sin fechas, desde el tramo de la primera venta del alcance —la
    // del 8 de septiembre de agente1— hasta hoy.
    LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));
    LocalDate primera = LocalDate.of(2026, 9, 8);
    mvc.perform(get(SERIE).with(user(director1.toString()).authorities(() -> PERMISO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.granularity").value("DAY"))
        .andExpect(jsonPath("$.period.from").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.period.to").value(hoy.toString()))
        .andExpect(jsonPath("$.buckets[0].start").value(primera.toString()))
        .andExpect(
            jsonPath("$.buckets.length()").value((int) ChronoUnit.DAYS.between(primera, hoy) + 1));
    // Sin ventas en el alcance, un solo tramo: el de hoy.
    mvc.perform(
            get(SERIE)
                .param("sellerId", director2.toString())
                .with(user(director1.toString()).authorities(() -> PERMISO)))
        .andExpect(jsonPath("$.buckets.length()").value(1))
        .andExpect(jsonPath("$.buckets[0].start").value(hoy.toString()));
  }

  @Test
  @DisplayName(
      "CA-IN-021 — el alcance del resumen: la otra rama no; un vendedor ajeno, ceros con todos los"
          + " tramos")
  void alcance() throws Exception {
    serie(director2, "2026-09-01", "2026-09-30", "MONTH")
        .andExpect(jsonPath("$.buckets[0].sales").value(1))
        .andExpect(jsonPath("$.currencies[*].code", contains("USD")))
        .andExpect(jsonPath("$.buckets[0].amounts[0].amount").value(5000.0));

    mvc.perform(
            get(SERIE)
                .param("from", "2026-09-08")
                .param("to", "2026-09-14")
                .param("sellerId", director2.toString())
                .with(user(director1.toString()).authorities(() -> PERMISO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.buckets.length()").value(7))
        .andExpect(jsonPath("$.currencies").isEmpty())
        .andExpect(jsonPath("$.buckets[*].sales", contains(0, 0, 0, 0, 0, 0, 0)));
  }

  @Test
  @DisplayName(
      "CA-IN-022 — tramo desconocido y rango invertido juntos; 366 días; sin el permiso, 403, y"
          + " el del resumen no abre")
  void validacionesYPermisos() throws Exception {
    mvc.perform(
            get(SERIE)
                .param("from", "2026-09-30")
                .param("to", "2026-09-01")
                .param("granularity", "HORA")
                .with(user(director1.toString()).authorities(() -> PERMISO)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(jsonPath("$.errors[*].code", contains("VAL-002", "VAL-005")));
    // CA-IN-056: sin tope, una serie diaria de más de 366 días es válida.
    serie(director1, "2025-01-01", "2026-01-02", "DAY")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.buckets.length()").value(367));

    mvc.perform(get(SERIE).with(user(director1.toString()))).andExpect(status().isForbidden());
    mvc.perform(
            get(SERIE)
                .with(
                    user(director1.toString()).authorities(() -> "indicators:read-sales-summary")))
        .andExpect(status().isForbidden());
    mvc.perform(get(SERIE)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("RNF-PERF — una sentencia de suma sea cual sea el número de tramos")
  void elCosteNoCreceConLosTramos() throws Exception {
    Statistics estadisticas = emf.unwrap(SessionFactory.class).getStatistics();

    estadisticas.clear();
    serie(director1, "2026-09-08", "2026-09-14", "DAY").andExpect(status().isOk());
    long siete = estadisticas.getPrepareStatementCount();

    estadisticas.clear();
    serie(director1, "2026-07-01", "2026-09-28", "DAY").andExpect(status().isOk());
    long noventa = estadisticas.getPrepareStatementCount();

    assertThat(noventa).isEqualTo(siete);
  }

  // ---------------------------------------------------------------------------

  private ResultActions serie(UUID actor, String desde, String hasta, String tramo)
      throws Exception {
    var peticion =
        get(SERIE)
            .param("from", desde)
            .param("to", hasta)
            .with(user(actor.toString()).authorities(() -> PERMISO));
    return mvc.perform(tramo == null ? peticion : peticion.param("granularity", tramo));
  }

  /** La serie de septiembre en un solo tramo de mes, con la oficina dada. */
  private ResultActions serieDeOficina(UUID actor, UUID oficina) throws Exception {
    return mvc.perform(
        get(SERIE)
            .param("from", "2026-09-01")
            .param("to", "2026-09-30")
            .param("granularity", "MONTH")
            .param("teamId", oficina.toString())
            .with(user(actor.toString()).authorities(() -> PERMISO)));
  }

  /**
   * Comprueba que la suma de los tramos de septiembre es lo confirmado del resumen, por moneda, con
   * la oficina dada si no es nula. Devuelve las ventas sumadas.
   */
  private long cuadran(UUID actor, String tramo, String oficina) throws Exception {
    var deLaSerie =
        get(SERIE)
            .param("from", "2026-09-01")
            .param("to", "2026-09-30")
            .param("granularity", tramo)
            .with(user(actor.toString()).authorities(() -> PERMISO));
    var delResumen =
        get(RESUMEN)
            .param("from", "2026-09-01")
            .param("to", "2026-09-30")
            .with(user(actor.toString()).authorities(() -> "indicators:read-sales-summary"));
    if (oficina != null) {
      deLaSerie.param("teamId", oficina);
      delResumen.param("teamId", oficina);
    }
    String serie = mvc.perform(deLaSerie).andReturn().getResponse().getContentAsString();
    String resumen = mvc.perform(delResumen).andReturn().getResponse().getContentAsString();

    List<Map<String, Object>> tramos = JsonPath.read(serie, "$.buckets");
    long ventas = 0;
    long lineas = 0;
    long unidades = 0;
    Map<String, BigDecimal> porMoneda = new HashMap<>();
    for (Map<String, Object> t : tramos) {
      ventas += ((Number) t.get("sales")).longValue();
      lineas += ((Number) t.get("lines")).longValue();
      unidades += ((Number) t.get("units")).longValue();
      List<Map<String, Object>> importes = JsonPath.read(t, "$.amounts");
      for (Map<String, Object> i : importes) {
        String moneda = JsonPath.read(i, "$.currency.code");
        porMoneda.merge(moneda, new BigDecimal(i.get("amount").toString()), BigDecimal::add);
      }
    }
    assertThat(ventas)
        .as(tramo)
        .isEqualTo(((Number) JsonPath.read(resumen, "$.confirmed.sales")).longValue());
    assertThat(lineas)
        .as(tramo)
        .isEqualTo(((Number) JsonPath.read(resumen, "$.confirmed.lines")).longValue());
    assertThat(unidades)
        .as(tramo)
        .isEqualTo(((Number) JsonPath.read(resumen, "$.confirmed.units")).longValue());
    List<Map<String, Object>> importesDelResumen = JsonPath.read(resumen, "$.confirmed.amounts");
    assertThat(porMoneda).as(tramo).hasSize(importesDelResumen.size());
    for (Map<String, Object> i : importesDelResumen) {
      String moneda = JsonPath.read(i, "$.currency.code");
      assertThat(porMoneda.get(moneda))
          .as(tramo + " " + moneda)
          .isEqualByComparingTo(new BigDecimal(i.get("amount").toString()));
    }
    return ventas;
  }

  /** Una oficina; con {@code director}, este pertenece a ella desde hace un mes. */
  private UUID oficina(String letra, UUID director) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO teams (id, name) VALUES (?, ?)", id, "INS Oficina " + letra);
    if (director != null) {
      jdbc.update(
          "INSERT INTO team_members (id, team_id, user_id, started_at)"
              + " VALUES (gen_random_uuid(), ?, ?, now() - interval '30 days')",
          id,
          director);
    }
    return id;
  }

  /** Cierra ayer la pertenencia vigente de {@code persona} y la abre hoy en {@code destino}. */
  private void trasladar(UUID persona, UUID destino) {
    jdbc.update(
        "UPDATE team_members SET ended_at = now() - interval '1 day'"
            + " WHERE user_id = ? AND ended_at IS NULL",
        persona);
    jdbc.update(
        "INSERT INTO team_members (id, team_id, user_id, started_at)"
            + " VALUES (gen_random_uuid(), ?, ?, now() - interval '12 hours')",
        destino,
        persona);
  }

  /** La oficina que guardan todas las líneas de {@code vendedores}. */
  private void guardarOficina(UUID oficina, UUID... vendedores) {
    for (UUID vendedor : vendedores) {
      jdbc.update("UPDATE movement_details SET team_id = ? WHERE seller_id = ?", oficina, vendedor);
    }
  }

  private void limpiar() {
    CommissionCleanup.limpiar(jdbc);
    jdbc.update("DELETE FROM movement_details");
    jdbc.update(
        "DELETE FROM team_members WHERE team_id IN (SELECT id FROM teams WHERE name LIKE"
            + " 'INS Oficina %') OR user_id IN (SELECT id FROM users WHERE username LIKE 'ins-%')");
    jdbc.update("DELETE FROM teams WHERE name LIKE 'INS Oficina %'");
    jdbc.update("DELETE FROM payments");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM products WHERE code LIKE 'INS_BOT%'");
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'ins-%') OR supervisor_id IN (SELECT id FROM users WHERE username LIKE 'ins-%')");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'ins-%')");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'ins-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'ins-%'");
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

  private UUID producto(String codigo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description,"
            + " source_membership_id, target_membership_id, price, currency_id, validity_days,"
            + " status) VALUES ('TIENDA', 'MANUAL', ?, ?, 'BOT', ?, 'Producto de prueba', NULL,"
            + " NULL, 10000, CAST(? AS uuid), NULL, 'ACTIVO')",
        id,
        codigo,
        "Bot " + codigo,
        USD);
    return id;
  }

  private void venta(UUID vendedor, String estado, String moneda, String cuando, long centesimas) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id,
                               currency_id, code, status, total_amount, discount_amount,
                               payable_amount, occurred_at, confirmed_at)
        VALUES (?, CAST(? AS uuid),
                (SELECT s.id FROM movement_type_statuses s
                  WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = 'VALIDADO'),
                ?, CAST(? AS uuid), ?, ?, ?, 0, ?, CAST(? AS timestamptz),
                CASE WHEN ? = 'CONFIRMADA' THEN CAST(? AS timestamptz) END)
        """,
        id,
        VENTA,
        VENTA,
        director1,
        moneda,
        "INS-" + id.toString().substring(0, 8).toUpperCase(),
        estado,
        centesimas,
        centesimas,
        cuando,
        estado,
        cuando);
    PaymentFixtures.pagoDe(jdbc, id, TARJETA);
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                      product_description, quantity, unit_price,
                                      line_amount, validity_days, implementation)
        VALUES (?, ?, ?, ?, 'Bot de la serie', 'Lo que decía el catálogo', 1, ?, ?, NULL,
                'MANUAL')
        """,
        UUID.randomUUID(),
        id,
        producto,
        vendedor,
        centesimas,
        centesimas);
  }
}
