package com.factech.nexus.modules.indicators.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Los indicadores de cuentas de broker de la red (`RF-IN-009`, `RN-IN-015`).
 *
 * <p>El árbol de la fuerza comercial, cada nodo con <b>lo suyo</b> ({@code own}) y <b>lo de su
 * red</b> ({@code network}), que ya contiene lo de sus hijos: <b>sumar la columna de {@code
 * network} cuenta dos veces</b>. {@code unassigned} solo va con el alcance entero y sin vendedor;
 * en otro caso, nulo.
 */
public record BrokerNetworkIndicatorsResponse(
    IndicatorPeriod period,
    List<Node> nodes,
    Figures totals,
    @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(
            description =
                "Las cuentas sin origen o con origen fuera de la fuerza comercial. Solo con el"
                    + " alcance entero y sin sellerId; nulo en otro caso.")
        Figures unassigned,
    @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(description = "El tramo pedido —DAY, WEEK o MONTH—, o nulo si no se pidió.")
        String granularity,
    @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(
            description =
                "Las cuentas creadas y los FTD de los totales por tramo, todos los tramos"
                    + " presentes; nulo si no se pidió tramo.")
        List<Bucket> buckets) {

  /** Un vendedor del árbol, con sus números y sus hijos. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "BrokerNetworkNode")
  public record Node(
      User user,
      String roleCode,
      @Schema(description = "Lo que originó la cuenta VENDEDOR de esta persona.") Figures own,
      @Schema(description = "Esta persona y todo lo que cuelga de ella, a cualquier profundidad.")
          Figures network,
      // Por `ref` y no por `implementation`: es un tipo que se referencia a sí
      // mismo, y `implementation` reentra la recursión y tumba la generación del
      // contrato (la lección de `RF-SP-058`).
      @ArraySchema(schema = @Schema(ref = "#/components/schemas/BrokerNetworkNode"))
          List<Node> children) {}

  @Schema(name = "BrokerNetworkNodeUser")
  public record User(UUID id, String username, String firstName, String lastName) {}

  /**
   * El bloque de números, igual en {@code own}, {@code network}, {@code totals} y {@code
   * unassigned}.
   *
   * <p>Cada cifra con su fecha (`RN-IN-015`): {@code accounts}, {@code pending}, {@code
   * withoutHolder}, {@code consumers} y {@code conversion} son de las cuentas <b>creadas</b> en el
   * periodo; {@code ftd}, de los primeros depósitos <b>llegados</b> en él; {@code activeAccounts},
   * de las que <b>operaron</b> en él, y {@code operations}, lo que esas llevan acumulado. {@code
   * conversion} es un cociente entre 0 y 1, <b>nulo sin cuentas</b>.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "BrokerNetworkFigures")
  public record Figures(
      long accounts,
      long ftd,
      long pending,
      BigDecimal conversion,
      long withoutHolder,
      long consumers,
      long activeAccounts,
      long operations,
      @Schema(
              description =
                  "Todos los brokers del catálogo, por nombre; en cero los que no tienen.")
          List<ByBroker> byBroker) {}

  /** Las mismas cifras en un broker, salvo los consumidores: una persona puede estar en varios. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "BrokerNetworkByBroker")
  public record ByBroker(
      BrokerRef broker,
      long accounts,
      long ftd,
      long pending,
      BigDecimal conversion,
      long withoutHolder,
      long activeAccounts,
      long operations) {}

  @Schema(name = "BrokerNetworkBroker")
  public record BrokerRef(UUID id, String name) {}

  @Schema(name = "BrokerNetworkBucket")
  public record Bucket(
      @Schema(description = "Primer día del tramo.") LocalDate start,
      @Schema(description = "Cuentas creadas en el tramo.") long accounts,
      @Schema(description = "Primeros depósitos llegados en el tramo.") long ftd) {}
}
