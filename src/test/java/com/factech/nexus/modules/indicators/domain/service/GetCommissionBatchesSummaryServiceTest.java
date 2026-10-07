package com.factech.nexus.modules.indicators.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.modules.commissions.application.CommissionBatchFigures.StatusTotals;
import com.factech.nexus.modules.indicators.application.CommissionBatchesSummaryResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** `RF-IN-007` · `T-03` — el reparto por estado del resumen de lotes. */
class GetCommissionBatchesSummaryServiceTest {

  private static final UUID USD = UUID.randomUUID();
  private static final UUID COP = UUID.randomUUID();

  @Test
  @DisplayName("sin filas, los cuatro bloques en cero")
  void sinFilas() {
    CommissionBatchesSummaryResponse r =
        new GetCommissionBatchesSummaryService(moneda -> List.of()).get(null);

    for (CommissionBatchesSummaryResponse.Block b :
        List.of(r.open(), r.pending(), r.paid(), r.total())) {
      assertThat(b.batches()).isZero();
      assertThat(b.amounts()).isEmpty();
    }
  }

  @Test
  @DisplayName("un estado desconocido no se pierde: cuenta solo en el total")
  void unEstadoDesconocido() {
    CommissionBatchesSummaryResponse r =
        new GetCommissionBatchesSummaryService(
                moneda ->
                    List.of(
                        new StatusTotals("PENDIENTE", USD, "USD", 2, new BigDecimal("20.00")),
                        new StatusTotals("ANULADO", USD, "USD", 1, new BigDecimal("4.00")),
                        new StatusTotals("ABIERTO", COP, "COP", 1, new BigDecimal("1000.00"))))
            .get(null);

    assertThat(r.pending().batches()).isEqualTo(2);
    assertThat(r.open().batches()).isEqualTo(1);
    assertThat(r.paid().batches()).isZero();
    assertThat(r.total().batches()).isEqualTo(4);
    assertThat(r.total().amounts())
        .extracting(a -> a.currency().code(), a -> a.amount())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("COP", new BigDecimal("1000.00")),
            org.assertj.core.groups.Tuple.tuple("USD", new BigDecimal("24.00")));
  }
}
