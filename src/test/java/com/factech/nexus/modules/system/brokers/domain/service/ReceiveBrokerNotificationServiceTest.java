package com.factech.nexus.modules.system.brokers.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.factech.nexus.modules.system.brokers.application.BrokerNotice;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountWriter;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerNotificationRepository;
import com.factech.nexus.modules.system.brokers.infrastructure.BrokerNotificationSettings;
import com.factech.nexus.modules.system.users.domain.service.ConfirmFirstDepositService;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.ServiceUnavailableException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que la suite de integración no puede probar: la dirección común sin secreto configurado; y la
 * lectura del importe de un depósito, caso por caso.
 */
class ReceiveBrokerNotificationServiceTest {

  @Test
  @DisplayName("CA-SP-951 — sin el secreto común configurado, 503 (EX-002) y no se guarda nada")
  void sinSecretoComun() {
    BrokerNotificationRepository avisos = mock(BrokerNotificationRepository.class);
    ReceiveBrokerNotificationService servicio =
        new ReceiveBrokerNotificationService(
            avisos,
            new BrokerNotificationSettings(" ", null, null, null, null),
            mock(UuidV7Generator.class),
            new ObjectMapper(),
            mock(BrokerAccountWriter.class),
            mock(AuditWriter.class),
            mock(ConfirmFirstDepositService.class),
            mock(FirstDepositBeca.class));

    assertThatThrownBy(
            () ->
                servicio.receiveCommon(
                    new BrokerNotice(
                        "GET", "advertiser=iq_option&token=x", Map.of(), null, null, null)))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessageContaining("secreto");
    verifyNoInteractions(avisos);
  }

  @Test
  @DisplayName(
      "CA-SP-1030 — el importe del depósito en centésimas: mayor que cero, redondeado al céntimo;"
          + " cualquier otra cosa es nula")
  void elImporteDelDeposito() {
    assertThat(ReceiveBrokerNotificationService.importe(Optional.of("150.50"))).isEqualTo(15050L);
    assertThat(ReceiveBrokerNotificationService.importe(Optional.of("20"))).isEqualTo(2000L);
    assertThat(ReceiveBrokerNotificationService.importe(Optional.of("10.005"))).isEqualTo(1001L);
    assertThat(ReceiveBrokerNotificationService.importe(Optional.of("1e2"))).isEqualTo(10000L);
    assertThat(ReceiveBrokerNotificationService.importe(Optional.of("0.004"))).isNull();
    assertThat(ReceiveBrokerNotificationService.importe(Optional.of("0"))).isNull();
    assertThat(ReceiveBrokerNotificationService.importe(Optional.of("-5"))).isNull();
    assertThat(ReceiveBrokerNotificationService.importe(Optional.of("1,5"))).isNull();
    assertThat(ReceiveBrokerNotificationService.importe(Optional.of("abc"))).isNull();
    assertThat(ReceiveBrokerNotificationService.importe(Optional.of("1e20"))).isNull();
    assertThat(ReceiveBrokerNotificationService.importe(Optional.empty())).isNull();
  }
}
