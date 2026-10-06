package com.factech.nexus.modules.indicators.domain.service;

import com.factech.nexus.modules.movements.application.SalesFigures.SalesScope;
import com.factech.nexus.modules.system.users.application.CommercialReach;
import com.factech.nexus.modules.system.users.application.CommercialReach.Reach;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * De «hasta dónde llega esta persona» a «sobre qué vendedores se suma» (`RF-IN-001` · `T-05`,
 * `plan.md` §3.2, `RN-IN-002`).
 *
 * <p><b>El alcance lo resuelve `SP`, lo traduce `IN` y lo aplica `MV`.</b> Así hay una definición
 * de «mi red» —la de {@link CommercialReach}— y esta clase solo aplica el filtro de vendedor y el
 * corte.
 *
 * <p><b>Vacío es «fuera del alcance»</b>, y quien lo recibe responde ceros <b>sin consultar</b>. No
 * es una optimización: es la regla. Fuera del alcance la respuesta es cero por definición, y no
 * porque la sentencia no encuentre filas; escrito así, el filtro no se convierte en la forma de
 * descubrir quién cuelga de quién aunque alguien cambie la sentencia.
 *
 * <p><b>{@code OWN} es «lo que vendió él»</b>, y no sus compras como en `RF-MV-015`: aquí se suma
 * lo vendido, y lo único vendido que puede ser de alguien sin red es lo que vendió él.
 */
@Component
public class SalesScopeResolver {

  private final CommercialReach alcance;

  public SalesScopeResolver(CommercialReach alcance) {
    this.alcance = alcance;
  }

  /**
   * @param actor quien pregunta
   * @param sellerId el vendedor por el que se acota, o nulo
   * @return el alcance de la suma; vacío si {@code sellerId} queda fuera
   */
  public Optional<SalesScope> resolve(UUID actor, UUID sellerId) {
    Reach hastaDonde = alcance.reachOf(actor);
    return switch (hastaDonde.kind()) {
      case EVERYTHING ->
          Optional.of(
              sellerId == null ? SalesScope.everything() : SalesScope.sellers(Set.of(sellerId)));
      case NETWORK -> {
        if (sellerId == null) {
          yield Optional.of(SalesScope.sellers(hastaDonde.sellers()));
        }
        yield hastaDonde.sellers().contains(sellerId)
            ? Optional.of(SalesScope.sellers(Set.of(sellerId)))
            : Optional.empty();
      }
      case OWN ->
          sellerId == null || sellerId.equals(actor)
              ? Optional.of(SalesScope.sellers(Set.of(actor)))
              : Optional.empty();
    };
  }
}
