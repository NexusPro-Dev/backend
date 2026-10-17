package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.domain.repository.CommissionClosingRepository;
import com.factech.nexus.modules.movements.application.SaleLineTeamTransfer;
import com.factech.nexus.modules.system.users.application.SupervisorChangeFollower;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RN-CM-055`: el periodo abierto empieza en el corte del último cierre, y es lo que un cambio de
 * superior se lleva a la oficina nueva (`RN-SP-083`, `RN-MV-080`). No toca lotes ni comisiones.
 */
@Service
public class FollowSupervisorChangeService implements SupervisorChangeFollower {

  // Sin ningún cierre el periodo abierto es todo: ninguna venta es anterior a 1970.
  private static final OffsetDateTime SIN_CIERRE = Instant.EPOCH.atOffset(ZoneOffset.UTC);

  private final CommissionClosingRepository cierres;
  private final SaleLineTeamTransfer oficinas;

  public FollowSupervisorChangeService(
      CommissionClosingRepository cierres, SaleLineTeamTransfer oficinas) {
    this.cierres = cierres;
    this.oficinas = oficinas;
  }

  @Override
  @Transactional
  public void supervisorChanged(UUID userId, OffsetDateTime at) {
    oficinas.transfer(userId, cierres.lastCut().orElse(SIN_CIERRE));
  }
}
