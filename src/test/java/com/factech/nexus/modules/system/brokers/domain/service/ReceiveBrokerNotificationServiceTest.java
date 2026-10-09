package com.factech.nexus.modules.system.brokers.domain.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.factech.nexus.modules.system.brokers.application.BrokerNotice;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountWriter;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerNotificationRepository;
import com.factech.nexus.modules.system.brokers.infrastructure.BrokerNotificationSettings;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.ServiceUnavailableException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Lo que la suite de integración no puede probar: la dirección común sin secreto configurado. */
class ReceiveBrokerNotificationServiceTest {

  @Test
  @DisplayName("CA-SP-951 — sin el secreto común configurado, 503 (EX-002) y no se guarda nada")
  void sinSecretoComun() {
    BrokerNotificationRepository avisos = mock(BrokerNotificationRepository.class);
    ReceiveBrokerNotificationService servicio =
        new ReceiveBrokerNotificationService(
            avisos,
            new BrokerNotificationSettings(Map.of(), " ", null, null),
            mock(UuidV7Generator.class),
            new ObjectMapper(),
            mock(BrokerAccountWriter.class),
            mock(AuditWriter.class));

    assertThatThrownBy(
            () ->
                servicio.receiveCommon(
                    new BrokerNotice(
                        "GET", "advertiser=iq_option&token=x", Map.of(), null, null, null)))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessageContaining("secreto");
    verifyNoInteractions(avisos);
  }
}
