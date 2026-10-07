package com.factech.nexus.modules.indicators.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.factech.nexus.modules.commissions.application.CommissionBatchFigures;
import com.factech.nexus.modules.commissions.application.CommissionBatchFigures.BatchFilter;
import com.factech.nexus.modules.commissions.application.CommissionBatchFigures.CommissionFilter;
import com.factech.nexus.modules.commissions.application.CommissionBatchFigures.CommissionTotals;
import com.factech.nexus.modules.commissions.application.CommissionBatchFigures.StatusTotals;
import com.factech.nexus.modules.indicators.application.OwnCommissionsSummaryResponse;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.shared.time.BusinessCalendar;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** `RF-IN-008` · `T-03` — el reparto por estado del resumen de mis comisiones. */
class GetOwnCommissionsSummaryServiceTest {

  private static final UUID YO = UUID.randomUUID();
  private static final UUID USD = UUID.randomUUID();
  private static final UUID COP = UUID.randomUUID();

  @Test
  @DisplayName("sin filas, los cuatro bloques en cero")
  void sinFilas() {
    OwnCommissionsSummaryResponse r = servicio(List.of(), new ArrayList<>()).get(null, null, null);

    for (OwnCommissionsSummaryResponse.Block b :
        List.of(r.open(), r.pending(), r.paid(), r.total())) {
      assertThat(b.commissions()).isZero();
      assertThat(b.amounts()).isEmpty();
    }
  }

  @Test
  @DisplayName(
      "la persona es la del token, y un estado desconocido no se pierde: cuenta solo en el total")
  void laPersonaDelTokenYUnEstadoDesconocido() {
    List<CommissionFilter> pedidos = new ArrayList<>();
    OwnCommissionsSummaryResponse r =
        servicio(
                List.of(
                    new CommissionTotals("PENDIENTE", USD, "USD", 2, new BigDecimal("20.00")),
                    new CommissionTotals("ANULADO", USD, "USD", 1, new BigDecimal("4.00")),
                    new CommissionTotals("ABIERTO", COP, "COP", 3, new BigDecimal("1000.00"))),
                pedidos)
            .get(null, null, COP);

    assertThat(pedidos)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.userId()).isEqualTo(YO);
              assertThat(f.currencyId()).isEqualTo(COP);
            });
    assertThat(r.pending().commissions()).isEqualTo(2);
    assertThat(r.open().commissions()).isEqualTo(3);
    assertThat(r.paid().commissions()).isZero();
    assertThat(r.total().commissions()).isEqualTo(6);
    assertThat(r.total().amounts())
        .extracting(a -> a.currency().code(), a -> a.amount())
        .containsExactly(
            tuple("COP", new BigDecimal("1000.00")), tuple("USD", new BigDecimal("24.00")));
  }

  @Test
  @DisplayName("una lectura de comisiones sin persona no se puede pedir")
  void sinPersonaNo() {
    assertThatThrownBy(() -> new CommissionFilter(null, null, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  private static GetOwnCommissionsSummaryService servicio(
      List<CommissionTotals> filas, List<CommissionFilter> pedidos) {
    CommissionBatchFigures cifras =
        new CommissionBatchFigures() {
          @Override
          public List<StatusTotals> byStatus(BatchFilter filter) {
            throw new UnsupportedOperationException();
          }

          @Override
          public List<CommissionTotals> commissionsByStatus(CommissionFilter filter) {
            pedidos.add(filter);
            return filas;
          }
        };
    AuthenticatedActor actor =
        new AuthenticatedActor() {
          @Override
          public UUID id() {
            return YO;
          }

          @Override
          public Set<String> permissions() {
            return Set.of();
          }
        };
    return new GetOwnCommissionsSummaryService(
        cifras,
        new SalesPeriodResolver(
            new BusinessCalendar(ZoneId.of("America/Bogota"), Clock.systemUTC())),
        actor);
  }
}
