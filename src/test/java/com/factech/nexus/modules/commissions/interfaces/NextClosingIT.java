package com.factech.nexus.modules.commissions.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.commissions.application.NextClosingResponse;
import com.factech.nexus.modules.commissions.domain.repository.PaymentChoiceRepository;
import com.factech.nexus.modules.commissions.domain.service.ClosingSchedule;
import com.factech.nexus.modules.commissions.domain.service.GetNextClosingService;
import com.factech.nexus.shared.time.BusinessCalendar;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El próximo cierre y cómo se pagará (`RF-CM-028`, `CA-CM-381` a `CA-CM-385`).
 *
 * <p><b>La suite apaga el cierre programado</b>, y con él la ruta responde {@code 409}. La ventana
 * se prueba con el servicio construido a mano —un horario encendido y un reloj fijo, sin otro
 * contexto de Spring— sobre el repositorio real; por HTTP, el apagado y los permisos.
 */
@AutoConfigureMockMvc
class NextClosingIT extends IntegrationTestBase {

  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  private static final OffsetDateTime NOVIEMBRE = OffsetDateTime.parse("2026-11-01T05:00:00Z");

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PaymentChoiceRepository elecciones;

  private UUID admin;

  @BeforeEach
  void sembrar() {
    SettlementFixtures.limpiar(jdbc);
    admin = SettlementFixtures.persona(jdbc, "nc-admin", null);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    SettlementFixtures.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "CA-CM-381 — fuera de la ventana: el próximo cierre, la apertura 48 h antes, ventana cerrada;"
          + " sin elección, AUTOMATICO y nadie eligió")
  void fueraDeLaVentana() {
    NextClosingResponse proximo = servicioA("2026-10-08T12:00:00Z").get();

    assertThat(proximo.scheduledFor().toInstant()).isEqualTo(NOVIEMBRE.toInstant());
    assertThat(proximo.windowOpensAt().toInstant())
        .isEqualTo(OffsetDateTime.parse("2026-10-30T05:00:00Z").toInstant());
    assertThat(proximo.windowOpen()).isFalse();
    assertThat(proximo.paymentMode()).isEqualTo("AUTOMATICO");
    assertThat(proximo.chosen()).isFalse();
    assertThat(proximo.chosenBy()).isNull();
    assertThat(proximo.chosenAt()).isNull();
  }

  @Test
  @DisplayName("CA-CM-382 — dentro de la ventana: abierta, y lo elegido con quién y cuándo")
  void dentroDeLaVentana() {
    jdbc.update(
        "INSERT INTO commission_payment_choices"
            + " (id, scheduled_for, payment_mode, chosen_by, chosen_at, created_at)"
            + " VALUES (?, ?, 'MANUAL', ?, '2026-10-30T10:00:00Z', now())",
        UUID.randomUUID(),
        NOVIEMBRE,
        admin);

    NextClosingResponse proximo = servicioA("2026-10-30T12:00:00Z").get();

    assertThat(proximo.windowOpen()).isTrue();
    assertThat(proximo.paymentMode()).isEqualTo("MANUAL");
    assertThat(proximo.chosen()).isTrue();
    assertThat(proximo.chosenBy()).isEqualTo(admin);
    assertThat(proximo.chosenAt().toInstant())
        .isEqualTo(OffsetDateTime.parse("2026-10-30T10:00:00Z").toInstant());
  }

  @Test
  @DisplayName("CA-CM-384 — con el cierre programado apagado, 409")
  void apagado() throws Exception {
    mvc.perform(get("/api/v1/commission-closings/next").with(como("commission-closings:read-next")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));
  }

  @Test
  @DisplayName(
      "CA-CM-385 — sin commission-closings:read-next, 403, también con commission-closings:read")
  void sinPermiso() throws Exception {
    mvc.perform(get("/api/v1/commission-closings/next").with(como("commission-closings:read")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/commission-closings/next")).andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------

  private GetNextClosingService servicioA(String instante) {
    Clock reloj = Clock.fixed(OffsetDateTime.parse(instante).toInstant(), ZoneOffset.UTC);
    return new GetNextClosingService(
        new ClosingSchedule(true, "0 0 0 1 * *", BOGOTA),
        elecciones,
        new BusinessCalendar(BOGOTA, reloj));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor como(String permiso) {
    return user(admin.toString()).authorities(() -> permiso);
  }
}
