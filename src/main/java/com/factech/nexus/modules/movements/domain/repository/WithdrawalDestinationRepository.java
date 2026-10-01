package com.factech.nexus.modules.movements.domain.repository;

import java.util.Optional;
import java.util.UUID;

/** Puerto de la copia del destino de cada retiro (`RN-MV-056`). Solo se escribe una vez. */
public interface WithdrawalDestinationRepository {

  void insert(UUID movementId, DestinationRow destino);

  Optional<DestinationRow> findByMovement(UUID movementId);

  record DestinationRow(
      UUID payoutAccountId,
      String institutionCode,
      String institutionName,
      String institutionKind,
      String accountType,
      String number,
      String holderName,
      String holderDocumentType,
      String holderDocumentNumber) {}
}
