package com.factech.nexus.modules.system.users.domain.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.users.application.MembershipGrant;
import com.factech.nexus.modules.system.users.application.MembershipGrant.GrantOrder;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

/**
 * La norma de `architecture.md` §15.2.1 —una escritura publicada se une a la transacción del que
 * llama y no abre una propia— no es una convención: {@code MANDATORY} la convierte en un fallo
 * inmediato, y esta prueba fija que lo sea.
 */
class PublishedMembershipGrantIT extends IntegrationTestBase {

  @Autowired private MembershipGrant concesion;

  @Test
  @DisplayName("sin transacción del que llama, conceder falla antes de tocar nada")
  void sinTransaccionFalla() {
    assertThatThrownBy(
            () ->
                concesion.grant(
                    new GrantOrder(UUID.randomUUID(), UUID.randomUUID(), 30, OffsetDateTime.now())))
        .isInstanceOf(IllegalTransactionStateException.class);
  }
}
