package com.factech.nexus.modules.commissions.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.commissions.application.CommissionClosingResponse;
import com.factech.nexus.modules.commissions.application.NextClosingResponse;
import com.factech.nexus.modules.commissions.domain.models.PaymentMode;
import com.factech.nexus.modules.commissions.domain.repository.CommissionClosingRepository;
import com.factech.nexus.modules.commissions.domain.repository.PaymentChoiceRepository;
import com.factech.nexus.modules.commissions.domain.service.ChoosePaymentModeService;
import com.factech.nexus.modules.commissions.domain.service.CloseCommissionPeriodService;
import com.factech.nexus.modules.commissions.domain.service.ClosingSchedule;
import com.factech.nexus.modules.commissions.domain.service.GetNextClosingService;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.factech.nexus.shared.time.BusinessCalendar;
import com.factech.nexus.testing.CommissionCleanup;
import com.factech.nexus.testing.ConcurrencyHarness;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Elegir cómo se paga el próximo cierre (`RF-CM-029`, `CA-CM-386` a `CA-CM-392`).
 *
 * <p>Como {@code NextClosingIT}: la ventana se prueba con el servicio construido a mano —horario
 * encendido, reloj fijo— y en una transacción de la prueba, porque así construido no tiene la suya;
 * por HTTP, el apagado, la validación y los permisos.
 */
@AutoConfigureMockMvc
class ChoosePaymentModeIT extends IntegrationTestBase {

  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  private static final OffsetDateTime NOVIEMBRE = OffsetDateTime.parse("2026-11-01T05:00:00Z");
  private static final String EN_LA_VENTANA = "2026-10-30T12:00:00Z";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PaymentChoiceRepository elecciones;
  @Autowired private CommissionClosingRepository cierres;
  @Autowired private CloseCommissionPeriodService cierre;
  @Autowired private UuidV7Generator ids;
  @Autowired private AuditWriter auditoria;
  @Autowired private PlatformTransactionManager transacciones;

  private UUID admin;

  @BeforeEach
  void sembrar() {
    SettlementFixtures.limpiar(jdbc);
    jdbc.update("DELETE FROM audit_change_log WHERE entity = 'commission_payment_choices'");
    admin = SettlementFixtures.persona(jdbc, "pm-admin", null);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    SettlementFixtures.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "CA-CM-386 — dentro de la ventana, elegir MANUAL lo deja elegido con quién y cuándo; responde"
          + " como RF-CM-028")
  void eligeManual() {
    NextClosingResponse respuesta = elegirA(EN_LA_VENTANA, PaymentMode.MANUAL);

    assertThat(respuesta.scheduledFor().toInstant()).isEqualTo(NOVIEMBRE.toInstant());
    assertThat(respuesta.windowOpen()).isTrue();
    assertThat(respuesta.paymentMode()).isEqualTo("MANUAL");
    assertThat(respuesta.chosen()).isTrue();
    assertThat(respuesta.chosenBy()).isEqualTo(admin);
    assertThat(respuesta.chosenAt().toInstant())
        .isEqualTo(OffsetDateTime.parse(EN_LA_VENTANA).toInstant());
  }

  @Test
  @DisplayName(
      "CA-CM-387 — elegir otra vez cambia la elección: gana la última, y AUTOMATICO vuelve al pago"
          + " automático")
  void ganaLaUltima() {
    elegirA(EN_LA_VENTANA, PaymentMode.MANUAL);

    NextClosingResponse respuesta = elegirA("2026-10-31T12:00:00Z", PaymentMode.AUTOMATICO);

    assertThat(respuesta.paymentMode()).isEqualTo("AUTOMATICO");
    assertThat(respuesta.chosen()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM commission_payment_choices WHERE scheduled_for = ?",
                Integer.class,
                NOVIEMBRE))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("CA-CM-388 — antes de abrirse la ventana, 409 y nada cambia")
  void antesDeLaVentana() {
    assertThatThrownBy(() -> elegirA("2026-10-29T12:00:00Z", PaymentMode.MANUAL))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("cerrada");

    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM commission_payment_choices", Integer.class))
        .isZero();
  }

  @Test
  @DisplayName("CA-CM-389 — con el cierre de su turno ya empezado, elegir responde 409")
  void conElCierreEmpezado() {
    cierre.closeScheduled(NOVIEMBRE);

    assertThatThrownBy(() -> elegirA("2026-10-31T23:00:00Z", PaymentMode.MANUAL))
        .isInstanceOf(BusinessRuleException.class);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM commission_payment_choices", Integer.class))
        .isZero();
  }

  @Test
  @DisplayName(
      "CA-CM-389 — elegir y empezar el cierre a la vez: o el cierre usa la elección, o la elección"
          + " responde 409; nunca se acepta y se ignora")
  void elegirYCerrarALaVez() {
    for (int vuelta = 0; vuelta < 6; vuelta++) {
      CommissionCleanup.limpiar(jdbc);

      List<Outcome<Object>> resultados =
          ConcurrencyHarness.runTogether(
              List.<Callable<Object>>of(
                  () -> elegirA("2026-10-31T23:59:00Z", PaymentMode.MANUAL),
                  () -> cierre.closeScheduled(NOVIEMBRE).orElseThrow()));

      Outcome<Object> eleccion = resultados.get(0);
      CommissionClosingResponse cerrado = (CommissionClosingResponse) resultados.get(1).value();
      assertThat(cerrado).as("vuelta %d", vuelta).isNotNull();
      if (eleccion.succeeded()) {
        assertThat(cerrado.paymentMode()).as("vuelta %d", vuelta).isEqualTo("MANUAL");
      } else {
        assertThat(eleccion.rootCause()).isInstanceOf(BusinessRuleException.class);
        assertThat(cerrado.paymentMode()).as("vuelta %d", vuelta).isEqualTo("AUTOMATICO");
      }
    }
  }

  @Test
  @DisplayName("CA-CM-390 — un modo ausente o que no es AUTOMATICO ni MANUAL, 400 con VAL-001")
  void modoInvalido() throws Exception {
    for (String cuerpo : new String[] {"{}", "{\"paymentMode\":\"A_VECES\"}"}) {
      mvc.perform(elegirPorHttp(cuerpo).with(como("commission-closings:set-payment-mode")))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));
    }
  }

  @Test
  @DisplayName(
      "CA-CM-391 — cada elección queda auditada, con el turno, el modo anterior y el nuevo")
  void auditada() {
    elegirA(EN_LA_VENTANA, PaymentMode.MANUAL);
    elegirA("2026-10-31T12:00:00Z", PaymentMode.AUTOMATICO);

    List<Map<String, Object>> filas =
        jdbc.queryForList(
            "SELECT action, changes::text AS cambios FROM audit_change_log"
                + " WHERE entity = 'commission_payment_choices' ORDER BY occurred_at");
    assertThat(filas).hasSize(2);
    assertThat(filas.get(0).get("action")).isEqualTo("CREATE");
    assertThat((String) filas.get(0).get("cambios")).contains("MANUAL").doesNotContain("before");
    assertThat(filas.get(1).get("action")).isEqualTo("UPDATE");
    assertThat((String) filas.get(1).get("cambios"))
        .contains("before")
        .contains("MANUAL")
        .contains("AUTOMATICO")
        .contains("2026-11-01");
  }

  @Test
  @DisplayName(
      "CA-CM-392 — sin commission-closings:set-payment-mode, 403, también con read-next; con el"
          + " cierre programado apagado, 409")
  void permisoYApagado() throws Exception {
    String cuerpo = "{\"paymentMode\":\"MANUAL\"}";
    mvc.perform(elegirPorHttp(cuerpo).with(como("commission-closings:read-next")))
        .andExpect(status().isForbidden());
    mvc.perform(elegirPorHttp(cuerpo)).andExpect(status().isUnauthorized());
    mvc.perform(elegirPorHttp(cuerpo).with(como("commission-closings:set-payment-mode")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));
  }

  // ---------------------------------------------------------------------------

  /** Elige con el reloj en {@code instante}, en una transacción de la prueba. */
  private NextClosingResponse elegirA(String instante, PaymentMode modo) {
    Clock reloj = Clock.fixed(OffsetDateTime.parse(instante).toInstant(), ZoneOffset.UTC);
    BusinessCalendar calendario = new BusinessCalendar(BOGOTA, reloj);
    ClosingSchedule horario = new ClosingSchedule(true, "0 0 0 1 * *", BOGOTA);
    ChoosePaymentModeService servicio =
        new ChoosePaymentModeService(
            horario,
            elecciones,
            cierres,
            new GetNextClosingService(horario, elecciones, calendario),
            calendario,
            ids,
            auditoria);
    return new TransactionTemplate(transacciones).execute(estado -> servicio.choose(modo, admin));
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      elegirPorHttp(String cuerpo) {
    return put("/api/v1/commission-closings/next/payment-mode")
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private RequestPostProcessor como(String permiso) {
    return user(admin.toString()).authorities(() -> permiso);
  }
}
