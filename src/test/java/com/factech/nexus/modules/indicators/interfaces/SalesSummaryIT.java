package com.factech.nexus.modules.indicators.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.testing.CommissionCleanup;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * `RF-IN-001` · `T-08` — el resumen de ventas (`CA-IN-001` a `CA-IN-014`).
 *
 * <p><b>El árbol de `SalesIT`, con un importe distinto en cada línea</b>, para que un error de suma
 * por venta en lugar de por línea, o de recorrido, dé un número que no coincide con ninguno
 * plausible:
 *
 * <pre>
 *   manager ─┬─ director1 ─┬─ agente1
 *            │             └─ agente2
 *            └─ director2 ─── agente3
 *   suelto (sin superior)   exagente (colgó de director1 y ya no)   funcionario (ADMIN)
 * </pre>
 *
 * <p>Todo en septiembre de 2026 en Bogotá, salvo lo que prueba el borde. Los importes, en
 * centésimas como los guarda la base.
 */
@AutoConfigureMockMvc
class SalesSummaryIT extends IntegrationTestBase {
  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String COMPRA_PUNTOS = "01a0ef9c-6800-7001-9c4f-5e7ad7000015";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";
  private static final String COP = "01a03336-6d00-7002-9c4f-5e7ad3000002";

  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000003";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";

  private static final String PERMISO = "indicators:read-sales-summary";
  private static final String RUTA = "/api/v1/indicators/sales/summary";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory emf;

  private UUID funcionario;
  private UUID manager;
  private UUID director1;
  private UUID director2;
  private UUID agente1;
  private UUID agente2;
  private UUID agente3;
  private UUID suelto;
  private UUID exagente;
  private UUID producto;
  private UUID otroProducto;

  @BeforeEach
  void sembrar() {
    limpiar();

    funcionario = persona("ind-funcionario", ADMIN);
    manager = persona("ind-manager", MANAGER);
    director1 = persona("ind-director1", DIRECTOR);
    director2 = persona("ind-director2", DIRECTOR);
    agente1 = persona("ind-agente1", AGENTE);
    agente2 = persona("ind-agente2", AGENTE);
    agente3 = persona("ind-agente3", AGENTE);
    suelto = persona("ind-suelto", AGENTE);
    exagente = persona("ind-exagente", AGENTE);
    producto = producto("IND_BOT");
    otroProducto = producto("IND_BOT_2");

    reportar(director1, manager);
    reportar(director2, manager);
    reportar(agente1, director1);
    reportar(agente2, director1);
    reportar(agente3, director2);
    jdbc.update(
        """
        INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at, ended_at)
        VALUES (gen_random_uuid(), ?, ?, now() - interval '30 days', now() - interval '1 day')
        """,
        exagente,
        director1);

    venta(manager, "CONFIRMADA", USD, "2026-09-02T15:00:00Z", 100000, 1);
    venta(director1, "CONFIRMADA", USD, "2026-09-03T15:00:00Z", 20000, 2);
    venta(agente1, "CONFIRMADA", USD, "2026-09-04T15:00:00Z", 3000, 3);
    venta(agente2, "PENDIENTE", USD, "2026-09-05T15:00:00Z", 400, 1);
    venta(director2, "CONFIRMADA", COP, "2026-09-06T15:00:00Z", 5000000, 1);
    venta(agente3, "ANULADA", USD, "2026-09-07T15:00:00Z", 50, 1);
    venta(suelto, "CONFIRMADA", USD, "2026-09-08T15:00:00Z", 700000, 1);
    venta(exagente, "CONFIRMADA", USD, "2026-09-09T15:00:00Z", 60000, 1);
    // CA-IN-005: una venta con una línea de cada rama.
    UUID mixta = venta(agente1, "CONFIRMADA", USD, "2026-09-10T15:00:00Z", 7, 1);
    linea(mixta, otroProducto, agente3, 80, 1);
    // FA-005: una venta por validar, con la línea sin vendedor.
    venta(null, "CONFIRMADA", USD, "2026-09-11T15:00:00Z", 900, 1);
    // CA-IN-009: las 20:00 del 30 de septiembre en Bogotá, que en UTC ya es octubre…
    venta(agente1, "CONFIRMADA", USD, "2026-10-01T01:00:00Z", 3, 1);
    // …y las 23:00 del 31 de agosto en Bogotá, que en UTC ya es septiembre.
    venta(agente1, "CONFIRMADA", USD, "2026-09-01T04:00:00Z", 1, 1);
    // CA-IN-008: el alta gratuita, importe cero; y una compra de puntos, que no es venta.
    venta(agente2, "CONFIRMADA", USD, "2026-09-12T15:00:00Z", 0, 1);
    movimiento(COMPRA_PUNTOS, agente1, "CONFIRMADA", USD, "2026-09-13T15:00:00Z", 55500, 1);
  }

  @AfterEach
  void devolverLaBaseASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // El alcance — CA-IN-001 a CA-IN-005
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-IN-001 — un funcionario ve toda la plataforma, con el suelto, el que se fue y lo sin"
          + " vendedor")
  void funcionarioVeTodo() throws Exception {
    septiembre(funcionario)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmed.sales").value(10))
        .andExpect(jsonPath("$.confirmed.lines").value(11))
        .andExpect(jsonPath("$.confirmed.units").value(14))
        .andExpect(jsonPath("$.confirmed.amounts.length()").value(2))
        .andExpect(jsonPath("$.confirmed.amounts[0].currency.code").value("COP"))
        .andExpect(jsonPath("$.confirmed.amounts[0].amount").value(50000.0))
        .andExpect(jsonPath("$.confirmed.amounts[1].currency.code").value("USD"))
        .andExpect(jsonPath("$.confirmed.amounts[1].amount").value(8839.9))
        .andExpect(jsonPath("$.pending.sales").value(1))
        .andExpect(jsonPath("$.pending.amounts[0].amount").value(4.0))
        .andExpect(jsonPath("$.voided.sales").value(1))
        .andExpect(jsonPath("$.voided.amounts[0].amount").value(0.5));
  }

  @Test
  @DisplayName(
      "CA-IN-002 y CA-IN-005 — un agente ve lo que vendió él: de la venta mixta, solo su línea")
  void agenteSoloLoSuyo() throws Exception {
    septiembre(agente1)
        .andExpect(jsonPath("$.confirmed.sales").value(3))
        .andExpect(jsonPath("$.confirmed.lines").value(3))
        .andExpect(jsonPath("$.confirmed.units").value(5))
        .andExpect(jsonPath("$.confirmed.amounts.length()").value(1))
        .andExpect(jsonPath("$.confirmed.amounts[0].amount").value(30.1))
        .andExpect(jsonPath("$.pending.sales").value(0))
        .andExpect(jsonPath("$.pending.amounts").isEmpty());
  }

  @Test
  @DisplayName(
      "CA-IN-003 y CA-IN-012 — un director ve lo suyo y lo de sus agentes; no la otra rama ni"
          + " al que se fue")
  void directorVeSuRama() throws Exception {
    septiembre(director1)
        .andExpect(jsonPath("$.confirmed.sales").value(5))
        .andExpect(jsonPath("$.confirmed.lines").value(5))
        .andExpect(jsonPath("$.confirmed.units").value(8))
        .andExpect(jsonPath("$.confirmed.amounts.length()").value(1))
        .andExpect(jsonPath("$.confirmed.amounts[0].amount").value(230.1))
        .andExpect(jsonPath("$.pending.sales").value(1))
        .andExpect(jsonPath("$.voided.sales").value(0));

    // La otra rama: su línea de la venta mixta, y nada más de ella.
    septiembre(director2)
        .andExpect(jsonPath("$.confirmed.sales").value(2))
        .andExpect(jsonPath("$.confirmed.lines").value(2))
        .andExpect(jsonPath("$.confirmed.amounts[0].currency.code").value("COP"))
        .andExpect(jsonPath("$.confirmed.amounts[0].amount").value(50000.0))
        .andExpect(jsonPath("$.confirmed.amounts[1].amount").value(0.8))
        .andExpect(jsonPath("$.voided.sales").value(1));
  }

  @Test
  @DisplayName(
      "CA-IN-004 y CA-IN-005 — un manager ve la profundidad entera, y la venta mixta cuenta una"
          + " vez con sus dos líneas")
  void managerVeTodaLaRed() throws Exception {
    septiembre(manager)
        .andExpect(jsonPath("$.confirmed.sales").value(7))
        .andExpect(jsonPath("$.confirmed.lines").value(8))
        .andExpect(jsonPath("$.confirmed.units").value(11))
        .andExpect(jsonPath("$.confirmed.amounts[0].amount").value(50000.0))
        .andExpect(jsonPath("$.confirmed.amounts[1].amount").value(1230.9))
        .andExpect(jsonPath("$.pending.sales").value(1))
        .andExpect(jsonPath("$.voided.sales").value(1));
  }

  // ---------------------------------------------------------------------------
  // Qué cuenta — CA-IN-006 a CA-IN-010
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-IN-006 a CA-IN-008 — por moneda; separadas por situación; ni la compra de puntos ni el"
          + " importe del alta")
  void queCuenta() throws Exception {
    // El alta de agente2 suma una venta y cero; la compra de puntos de agente1, nada.
    rango(agente2, "2026-09-12", "2026-09-13")
        .andExpect(jsonPath("$.confirmed.sales").value(1))
        .andExpect(jsonPath("$.confirmed.amounts[0].amount").value(0.0));
    rango(agente1, "2026-09-13", "2026-09-13")
        .andExpect(jsonPath("$.confirmed.sales").value(0))
        .andExpect(jsonPath("$.confirmed.amounts").isEmpty());
  }

  @Test
  @DisplayName(
      "CA-IN-038 y CA-IN-039 — el total es la suma de los tres estados, y las gratuitas se"
          + " cuentan en cada uno")
  void totalYGratuitas() throws Exception {
    // Funcionario: 10 confirmadas, 1 pendiente y 1 anulada; la única gratuita
    // es el alta de agente2, confirmada.
    septiembre(funcionario)
        .andExpect(jsonPath("$.total.sales").value(12))
        .andExpect(jsonPath("$.total.free").value(1))
        .andExpect(jsonPath("$.confirmed.free").value(1))
        .andExpect(jsonPath("$.pending.free").value(0))
        .andExpect(jsonPath("$.voided.free").value(0));
    septiembre(manager)
        .andExpect(jsonPath("$.total.sales").value(9))
        .andExpect(jsonPath("$.total.free").value(1));
    // Un agente sin gratuitas, y el filtro por vendedor acota el total igual.
    septiembre(agente1)
        .andExpect(jsonPath("$.total.sales").value(3))
        .andExpect(jsonPath("$.total.free").value(0));
    mvc.perform(septiembreDe(director1).param("sellerId", agente2.toString()))
        .andExpect(jsonPath("$.total.sales").value(2))
        .andExpect(jsonPath("$.total.free").value(1))
        .andExpect(jsonPath("$.confirmed.free").value(1))
        .andExpect(jsonPath("$.pending.sales").value(1));
  }

  @Test
  @DisplayName(
      "CA-IN-040 — la gratuidad es de la venta entera: una cobrada con una línea a cero no lo es,"
          + " y una gratuita lo es para cada vendedor que tenga una línea en ella")
  void gratuidadDeLaVentaEntera() throws Exception {
    // Cobrada (5,00 la venta) con una línea de agente1 a cero.
    UUID cobrada = venta(agente3, "CONFIRMADA", USD, "2026-09-20T15:00:00Z", 500, 1);
    linea(cobrada, otroProducto, agente1, 0, 1);
    // Gratuita, con una línea de cada rama.
    UUID gratuita = venta(agente1, "CONFIRMADA", USD, "2026-09-21T15:00:00Z", 0, 1);
    linea(gratuita, otroProducto, agente3, 0, 1);

    rango(agente1, "2026-09-20", "2026-09-21")
        .andExpect(jsonPath("$.total.sales").value(2))
        .andExpect(jsonPath("$.total.free").value(1))
        .andExpect(jsonPath("$.confirmed.free").value(1));
    rango(director2, "2026-09-20", "2026-09-21")
        .andExpect(jsonPath("$.total.sales").value(2))
        .andExpect(jsonPath("$.confirmed.free").value(1));
  }

  @Test
  @DisplayName(
      "CA-IN-009 — los días son de Bogotá: las 20:00 del 30 entra en septiembre; las 23:00 del 31"
          + " de agosto, no")
  void diasDeBogota() throws Exception {
    rango(agente1, "2026-09-30", "2026-09-30")
        .andExpect(jsonPath("$.confirmed.sales").value(1))
        .andExpect(jsonPath("$.confirmed.amounts[0].amount").value(0.03));
    rango(agente1, "2026-08-31", "2026-08-31")
        .andExpect(jsonPath("$.confirmed.sales").value(1))
        .andExpect(jsonPath("$.confirmed.amounts[0].amount").value(0.01));
  }

  @Test
  @DisplayName("CA-IN-010 — sin fechas, el mes en curso de Bogotá hasta hoy, y se devuelve")
  void periodoPorDefecto() throws Exception {
    // CA-IN-050: sin fechas, TODA la historia, también una venta de 2020.
    venta(suelto, "CONFIRMADA", USD, "2020-05-05T15:00:00Z", 100, 1);
    LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));
    mvc.perform(get(RUTA).with(conPermiso(funcionario)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.period.from").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.period.to").value(hoy.toString()))
        .andExpect(jsonPath("$.period.zone").value("America/Bogota"))
        // Las 10 confirmadas de septiembre, la del 31 de agosto, la de 2020, una
        // pendiente y una anulada.
        .andExpect(jsonPath("$.total.sales").value(14))
        .andExpect(jsonPath("$.granularity").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.buckets").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("CA-IN-051 y CA-IN-052 — una sola fecha deja la otra abierta; y no hay tope de días")
  void unaSolaFechaYSinTope() throws Exception {
    // Solo «desde» el 30: la de las 20:00 del 30 en Bogotá, hasta hoy.
    mvc.perform(get(RUTA).param("from", "2026-09-30").with(conPermiso(funcionario)))
        .andExpect(jsonPath("$.total.sales").value(1));
    // Solo «hasta» el 31 de agosto: desde el principio, la del 31.
    mvc.perform(get(RUTA).param("to", "2026-08-31").with(conPermiso(funcionario)))
        .andExpect(jsonPath("$.period.from").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.total.sales").value(1));
    rango(funcionario, "2020-01-01", "2026-09-30").andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "CA-IN-053 — con tramo, los mismos bloques por tramo, todos presentes, y su suma es el"
          + " total")
  void porTramos() throws Exception {
    mvc.perform(
            get(RUTA)
                .param("from", "2026-08-01")
                .param("to", "2026-09-30")
                .param("granularity", "month")
                .with(conPermiso(funcionario)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.granularity").value("MONTH"))
        .andExpect(jsonPath("$.total.sales").value(13))
        .andExpect(jsonPath("$.buckets.length()").value(2))
        .andExpect(jsonPath("$.buckets[0].start").value("2026-08-01"))
        .andExpect(jsonPath("$.buckets[0].total.sales").value(1))
        .andExpect(jsonPath("$.buckets[0].confirmed.amounts[0].amount").value(0.01))
        .andExpect(jsonPath("$.buckets[1].total.sales").value(12))
        .andExpect(jsonPath("$.buckets[1].confirmed.free").value(1))
        .andExpect(jsonPath("$.buckets[1].pending.sales").value(1));
    // Por días, en un tramo sin ventas, ceros.
    mvc.perform(
            get(RUTA)
                .param("from", "2026-09-13")
                .param("to", "2026-09-14")
                .param("granularity", "DAY")
                .with(conPermiso(agente1)))
        .andExpect(jsonPath("$.buckets.length()").value(2))
        .andExpect(jsonPath("$.buckets[1].total.sales").value(0))
        .andExpect(jsonPath("$.buckets[1].confirmed.amounts").isEmpty());
  }

  // ---------------------------------------------------------------------------
  // Los filtros — CA-IN-011
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-IN-011 — el vendedor acota dentro del alcance; fuera o inexistente, ceros y no error;"
          + " la moneda acota")
  void filtros() throws Exception {
    mvc.perform(septiembreDe(director1).param("sellerId", agente1.toString()))
        .andExpect(jsonPath("$.confirmed.sales").value(3))
        .andExpect(jsonPath("$.confirmed.amounts[0].amount").value(30.1));

    for (UUID fuera : new UUID[] {agente3, suelto, UUID.randomUUID()}) {
      mvc.perform(septiembreDe(director1).param("sellerId", fuera.toString()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.confirmed.sales").value(0))
          .andExpect(jsonPath("$.confirmed.amounts").isEmpty())
          .andExpect(jsonPath("$.pending.sales").value(0))
          .andExpect(jsonPath("$.voided.sales").value(0));
    }
    // Un agente, por cualquier otra persona, ceros.
    mvc.perform(septiembreDe(agente1).param("sellerId", agente2.toString()))
        .andExpect(jsonPath("$.confirmed.sales").value(0));

    mvc.perform(septiembreDe(manager).param("currencyId", COP))
        .andExpect(jsonPath("$.confirmed.sales").value(1))
        .andExpect(jsonPath("$.confirmed.amounts.length()").value(1))
        .andExpect(jsonPath("$.confirmed.amounts[0].currency.code").value("COP"))
        .andExpect(jsonPath("$.pending.sales").value(0));
    mvc.perform(septiembreDe(manager).param("currencyId", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmed.sales").value(0));
  }

  // ---------------------------------------------------------------------------
  // La oficina — CA-IN-098 a CA-IN-100
  //
  // Dos oficinas con director: A la de director1 y B la de director2. Las líneas
  // de cada rama guardan la suya; también la de exagente, que vendió cuando aún
  // colgaba de director1. Sin oficina quedan la venta del manager, la de suelto
  // —que no tiene director— y la que está sin vendedor.
  //
  //   A confirmado: director1 200,00 ×2, agente1 30,00 ×3 + 0,07 + 0,03,
  //                 agente2 0,00 (el alta), exagente 600,00     → 6 ventas, 830,10
  //   A pendiente:  agente2 4,00
  //   B confirmado: director2 50000,00 COP, agente3 0,80 (de la mixta)
  //   B anulado:    agente3 0,50
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-IN-098 — con oficina, el funcionario ve solo sus líneas: la venta de dos oficinas cuenta"
          + " una vez en cada una con su parte; ni lo sin vendedor ni la venta del manager")
  void oficinaSoloSusLineas() throws Exception {
    UUID[] oficinas = repartirOficinas();

    mvc.perform(septiembreDe(funcionario).param("teamId", oficinas[0].toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmed.sales").value(6))
        .andExpect(jsonPath("$.confirmed.lines").value(6))
        .andExpect(jsonPath("$.confirmed.units").value(9))
        .andExpect(jsonPath("$.confirmed.amounts.length()").value(1))
        .andExpect(jsonPath("$.confirmed.amounts[0].currency.code").value("USD"))
        .andExpect(jsonPath("$.confirmed.amounts[0].amount").value(830.1))
        .andExpect(jsonPath("$.pending.sales").value(1))
        .andExpect(jsonPath("$.pending.amounts[0].amount").value(4.0))
        .andExpect(jsonPath("$.voided.sales").value(0));
    // La mixta vuelve a contar en B, ahora solo con los 0,80 de agente3.
    mvc.perform(septiembreDe(funcionario).param("teamId", oficinas[1].toString()))
        .andExpect(jsonPath("$.confirmed.sales").value(2))
        .andExpect(jsonPath("$.confirmed.lines").value(2))
        .andExpect(jsonPath("$.confirmed.units").value(2))
        .andExpect(jsonPath("$.confirmed.amounts.length()").value(2))
        .andExpect(jsonPath("$.confirmed.amounts[0].currency.code").value("COP"))
        .andExpect(jsonPath("$.confirmed.amounts[0].amount").value(50000.0))
        .andExpect(jsonPath("$.confirmed.amounts[1].amount").value(0.8))
        .andExpect(jsonPath("$.pending.sales").value(0))
        .andExpect(jsonPath("$.voided.sales").value(1))
        .andExpect(jsonPath("$.voided.amounts[0].amount").value(0.5));
    // Las 10 confirmadas sin filtro: 6 de A y 2 de B, con la mixta en las dos
    // —7 distintas—, y las 3 sin oficina: el manager, suelto y la sin vendedor.
    septiembre(funcionario).andExpect(jsonPath("$.confirmed.sales").value(10));
  }

  @Test
  @DisplayName(
      "CA-IN-099 — la oficina es la guardada en la línea: trasladar al director o al agente no"
          + " mueve lo ya vendido")
  void oficinaGuardadaNoSeMueve() throws Exception {
    UUID[] oficinas = repartirOficinas();
    UUID nueva = oficina("C", null);

    // director1 pasa a la oficina C, y agente1 a colgar de director2, en B.
    trasladar(director1, nueva);
    jdbc.update(
        "UPDATE user_supervisors SET started_at = now() - interval '30 days',"
            + " ended_at = now() - interval '1 day' WHERE user_id = ? AND ended_at IS NULL",
        agente1);
    reportar(agente1, director2);

    // A sigue contando lo que se vendió en ella…
    mvc.perform(septiembreDe(funcionario).param("teamId", oficinas[0].toString()))
        .andExpect(jsonPath("$.confirmed.sales").value(6))
        .andExpect(jsonPath("$.confirmed.amounts[0].amount").value(830.1));
    // …B no se lleva lo de agente1, ni siquiera para director2, que hoy lo ve…
    mvc.perform(septiembreDe(funcionario).param("teamId", oficinas[1].toString()))
        .andExpect(jsonPath("$.confirmed.sales").value(2))
        .andExpect(jsonPath("$.confirmed.amounts[1].amount").value(0.8));
    mvc.perform(septiembreDe(director2).param("teamId", oficinas[1].toString()))
        .andExpect(jsonPath("$.confirmed.sales").value(2))
        .andExpect(jsonPath("$.confirmed.amounts[1].amount").value(0.8));
    // …y C, la oficina de hoy de director1, no tiene nada vendido.
    mvc.perform(septiembreDe(funcionario).param("teamId", nueva.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total.sales").value(0))
        .andExpect(jsonPath("$.confirmed.amounts").isEmpty());
  }

  @Test
  @DisplayName(
      "CA-IN-100 — la oficina se combina con el alcance; inexistente, ceros; mal formada, 400; con"
          + " tramo, la suma de los tramos es el total filtrado")
  void oficinaYAlcance() throws Exception {
    UUID[] oficinas = repartirOficinas();
    String a = oficinas[0].toString();
    String b = oficinas[1].toString();

    // director1 en A: lo suyo y lo de sus agentes, sin los 600,00 de exagente,
    // que ya no cuelga de él aunque su línea sea de A.
    mvc.perform(septiembreDe(director1).param("teamId", a))
        .andExpect(jsonPath("$.confirmed.sales").value(5))
        .andExpect(jsonPath("$.confirmed.lines").value(5))
        .andExpect(jsonPath("$.confirmed.units").value(8))
        .andExpect(jsonPath("$.confirmed.amounts[0].amount").value(230.1))
        .andExpect(jsonPath("$.pending.sales").value(1));
    // Un agente en su oficina ve lo suyo; en otra, ceros. Un director en la
    // ajena, también ceros.
    mvc.perform(septiembreDe(agente1).param("teamId", a))
        .andExpect(jsonPath("$.confirmed.sales").value(3))
        .andExpect(jsonPath("$.confirmed.amounts[0].amount").value(30.1));
    for (UUID actor : new UUID[] {agente1, director1}) {
      mvc.perform(septiembreDe(actor).param("teamId", b))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total.sales").value(0))
          .andExpect(jsonPath("$.confirmed.amounts").isEmpty());
    }
    // Con vendedor y oficina a la vez.
    mvc.perform(septiembreDe(director1).param("teamId", a).param("sellerId", agente2.toString()))
        .andExpect(jsonPath("$.confirmed.sales").value(1))
        .andExpect(jsonPath("$.pending.sales").value(1));

    mvc.perform(septiembreDe(funcionario).param("teamId", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total.sales").value(0))
        .andExpect(jsonPath("$.confirmed.amounts").isEmpty());
    mvc.perform(septiembreDe(funcionario).param("teamId", "no-es-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));

    // Por meses: la del 31 de agosto de agente1 es de A; septiembre, 6
    // confirmadas y una pendiente.
    mvc.perform(
            get(RUTA)
                .param("from", "2026-08-01")
                .param("to", "2026-09-30")
                .param("granularity", "MONTH")
                .param("teamId", a)
                .with(conPermiso(funcionario)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total.sales").value(8))
        .andExpect(jsonPath("$.buckets.length()").value(2))
        .andExpect(jsonPath("$.buckets[0].total.sales").value(1))
        .andExpect(jsonPath("$.buckets[0].confirmed.amounts[0].amount").value(0.01))
        .andExpect(jsonPath("$.buckets[1].total.sales").value(7))
        .andExpect(jsonPath("$.buckets[1].confirmed.amounts[0].amount").value(830.1));
  }

  // ---------------------------------------------------------------------------
  // Validaciones y permisos — CA-IN-013, CA-IN-014
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-IN-013 — rango invertido, más de 366 días y valores mal formados son 400")
  void validaciones() throws Exception {
    rango(funcionario, "2026-09-30", "2026-09-01")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    // CA-IN-054: un tramo desconocido y un rango invertido, juntos.
    mvc.perform(
            get(RUTA)
                .param("from", "2026-09-30")
                .param("to", "2026-09-01")
                .param("granularity", "HORA")
                .with(conPermiso(funcionario)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(jsonPath("$.errors[1].code").value("VAL-005"));
    mvc.perform(get(RUTA).param("from", "30-09-2026").with(conPermiso(funcionario)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));
    mvc.perform(get(RUTA).param("sellerId", "no-es-uuid").with(conPermiso(funcionario)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));
  }

  @Test
  @DisplayName(
      "CA-IN-014 — sin el permiso, 403; ni el listado de ventas ni otro indicador lo abren; sin"
          + " token, 401")
  void elPermisoEsElUnicoQueAbre() throws Exception {
    mvc.perform(get(RUTA).with(user(manager.toString()))).andExpect(status().isForbidden());
    mvc.perform(get(RUTA).with(user(manager.toString()).authorities(() -> "movements:list-sales")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get(RUTA)
                .with(user(manager.toString()).authorities(() -> "indicators:read-sales-series")))
        .andExpect(status().isForbidden());
    mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("el permiso lo portan FUNCIONARIO y VENDEDOR por su tipo, y CONSUMIDOR no (V74)")
  void elRepartoDeV74() {
    assertThat(
            jdbc.queryForList(
                """
                SELECT DISTINCT r.role_type FROM roles r
                  JOIN role_permissions rp ON rp.role_id = r.id
                  JOIN permissions p ON p.id = rp.permission_id
                 WHERE p.code = ? AND r.is_system
                """,
                String.class,
                PERMISO))
        .containsExactlyInAnyOrder("FUNCIONARIO", "VENDEDOR");
  }

  @Test
  @DisplayName("RNF-PERF — el coste no crece con la red: el manager y un agente, mismas sentencias")
  void elCosteNoCreceConLaRed() throws Exception {
    Statistics estadisticas = emf.unwrap(SessionFactory.class).getStatistics();

    estadisticas.clear();
    septiembre(agente1).andExpect(status().isOk());
    long deUnAgente = estadisticas.getPrepareStatementCount();

    estadisticas.clear();
    septiembre(manager).andExpect(status().isOk());
    long deUnManager = estadisticas.getPrepareStatementCount();

    assertThat(deUnManager).isEqualTo(deUnAgente);
  }

  // ---------------------------------------------------------------------------

  private ResultActions septiembre(UUID actor) throws Exception {
    return mvc.perform(septiembreDe(actor));
  }

  private static MockHttpServletRequestBuilder septiembreDe(UUID actor) {
    return get(RUTA).param("from", "2026-09-01").param("to", "2026-09-30").with(conPermiso(actor));
  }

  private ResultActions rango(UUID actor, String desde, String hasta) throws Exception {
    return mvc.perform(get(RUTA).param("from", desde).param("to", hasta).with(conPermiso(actor)));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor conPermiso(
      UUID persona) {
    return user(persona.toString()).authorities(() -> PERMISO);
  }

  private void limpiar() {
    CommissionCleanup.limpiar(jdbc);
    jdbc.update("DELETE FROM movement_details");
    jdbc.update(
        "DELETE FROM team_members WHERE team_id IN (SELECT id FROM teams WHERE name LIKE"
            + " 'IND Oficina %') OR user_id IN (SELECT id FROM users WHERE username LIKE 'ind-%')");
    jdbc.update("DELETE FROM teams WHERE name LIKE 'IND Oficina %'");
    jdbc.update("DELETE FROM payments");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM products WHERE code LIKE 'IND_BOT%'");
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'ind-%') OR supervisor_id IN (SELECT id FROM users WHERE username LIKE 'ind-%')");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'ind-%')");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'ind-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'ind-%'");
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

  private void reportar(UUID subordinado, UUID superior) {
    jdbc.update(
        """
        INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at)
        VALUES (gen_random_uuid(), ?, ?, now())
        """,
        subordinado,
        superior);
  }

  /**
   * Las oficinas A (director1) y B (director2), y la oficina guardada en las líneas ya sembradas,
   * como la habría copiado la venta: A para la rama de director1 y para exagente, B para la de
   * director2. Devuelve {A, B}.
   */
  private UUID[] repartirOficinas() {
    UUID a = oficina("A", director1);
    UUID b = oficina("B", director2);
    guardarOficina(a, director1, agente1, agente2, exagente);
    guardarOficina(b, director2, agente3);
    return new UUID[] {a, b};
  }

  /** Una oficina; con {@code director}, este pertenece a ella desde hace un mes. */
  private UUID oficina(String letra, UUID director) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO teams (id, name) VALUES (?, ?)", id, "IND Oficina " + letra);
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

  /** Una venta de una línea de {@code producto}; un vendedor nulo la deja por validar. */
  private UUID venta(
      UUID vendedor, String estado, String moneda, String cuando, long centesimas, int unidades) {
    return movimiento(VENTA, vendedor, estado, moneda, cuando, centesimas, unidades);
  }

  private UUID movimiento(
      String tipo,
      UUID vendedor,
      String estado,
      String moneda,
      String cuando,
      long centesimas,
      int unidades) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id,
                               currency_id, code, status, total_amount, discount_amount,
                               payable_amount, occurred_at, confirmed_at, voided_at, void_reason)
        VALUES (?, CAST(? AS uuid),
                (SELECT s.id FROM movement_type_statuses s
                  WHERE s.movement_type_id = CAST(? AS uuid)
                  ORDER BY (s.code = ?) DESC LIMIT 1),
                (SELECT id FROM users WHERE username = 'ind-funcionario'),
                CAST(? AS uuid), ?, ?, ?, 0, ?, CAST(? AS timestamptz),
                CASE WHEN ? = 'CONFIRMADA' THEN CAST(? AS timestamptz) END,
                CASE WHEN ? = 'ANULADA' THEN CAST(? AS timestamptz) END,
                CASE WHEN ? = 'ANULADA' THEN 'Prueba' END)
        """,
        id,
        tipo,
        tipo,
        vendedor == null ? "VALIDAR_COMISIONES" : "VALIDADO",
        moneda,
        "IND-" + id.toString().substring(0, 8).toUpperCase(),
        estado,
        centesimas,
        centesimas,
        cuando,
        estado,
        cuando,
        estado,
        cuando,
        estado);
    PaymentFixtures.pagoDe(jdbc, id, TARJETA);
    linea(id, producto, vendedor, centesimas, unidades);
    return id;
  }

  private void linea(UUID movimiento, UUID producto, UUID vendedor, long centesimas, int unidades) {
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                      product_description, quantity, unit_price,
                                      line_amount, validity_days, implementation)
        VALUES (?, ?, ?, ?, 'Bot de indicadores', 'Lo que decía el catálogo', ?, ?, ?, NULL,
                'MANUAL')
        """,
        UUID.randomUUID(),
        movimiento,
        producto,
        vendedor,
        unidades,
        centesimas / Math.max(unidades, 1),
        centesimas);
  }
}
