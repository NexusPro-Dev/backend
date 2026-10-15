package com.factech.nexus.modules.system.brokers.domain.service;

import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountWriter;
import com.factech.nexus.modules.system.users.application.FirstDepositSale;
import com.factech.nexus.modules.system.users.domain.service.ConfirmFirstDepositService;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que trae el primer depósito a la persona titular de la cuenta (`RN-SP-073`, `RN-SP-079`).
 *
 * <p><b>Primero la activación del alta por enlace</b>: quien espera en {@code FTD_PENDIENTE} con su
 * venta del alta sale de ahí con lo que compró. <b>Si no activó a nadie</b>, la persona se asoció a
 * su cuenta desde la plataforma y no tiene venta de la beca: se la pide a `MV`, con el vendedor de
 * la cuenta de origen o sin él. `MV` decide si procede.
 *
 * <p>{@code MANDATORY}: la llama quien mueve la cuenta del broker, en su transacción.
 */
@Component
public class FirstDepositBeca {

  private final ConfirmFirstDepositService primerDeposito;
  private final FirstDepositSale beca;
  private final BrokerAccountWriter cuentas;

  public FirstDepositBeca(
      ConfirmFirstDepositService primerDeposito,
      FirstDepositSale beca,
      BrokerAccountWriter cuentas) {
    this.primerDeposito = primerDeposito;
    this.beca = beca;
    this.cuentas = cuentas;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void apply(UUID brokerAccountId, UUID userId) {
    if (primerDeposito.confirm(userId)) {
      return;
    }
    beca.grantBeca(userId, cuentas.referrerSellerUsername(brokerAccountId).orElse(null));
  }
}
