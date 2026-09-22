package com.factech.nexus.modules.system.brokers.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Los indicadores de la red comercial (`RF-SP-058`).
 *
 * <p><b>Cada nodo lleva DOS bloques de números y eso no es redundancia</b>: es la respuesta al
 * cuidado que se pidió poner. El {@code network} de un director <b>ya contiene</b> el de sus
 * agentes, de modo que <b>sumar una columna de {@code network} cuenta dos veces</b>. Publicar solo
 * el total invita a ese error; publicar los dos obliga a elegir cuál se suma, y elegir es
 * acordarse.
 *
 * <p><b>{@code unassigned} es la pieza que hace que los números cuadren</b> (`RN-SP-048`): las
 * cuentas de consumidores que no cuelgan de ningún vendedor no entran en ningún nodo, y sin
 * publicarlas el árbol sumaría <b>menos</b> que {@code GET /api/v1/broker-accounts} sin que nadie
 * pudiera saber si falta algo o si el cálculo está mal. La igualdad que se cumple es:
 *
 * <pre>{@code totals + unassigned == total de RF-SP-057}</pre>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record NetworkIndicatorsResponse(
    List<Node> nodes, Indicators totals, Indicators unassigned) {

  /**
   * Un vendedor del árbol, con sus números y sus hijos.
   *
   * <p><b>Los consumidores NO son nodos</b> (`RN-SP-048`): aportan el número y no aparecen.
   * Publicarlos convertiría un indicador de gestión en el listado de clientes de la empresa.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "NetworkNode")
  public record Node(
      Holder user,
      String roleCode,
      /** Lo que cuelga <b>directamente</b> de esta persona. */
      Indicators own,
      /** Esta persona <b>y todo lo que cuelga de ella</b>, a cualquier profundidad. */
      Indicators network,
      /**
       * <b>Va siempre, aunque vaya vacío</b>: una hoja y un nodo sin cargar no pueden parecerse.
       *
       * <p><b>El {@code @ArraySchema} es obligatorio y no decorativo</b>: es un tipo que se
       * referencia a sí mismo, y sin él springdoc <b>omite la propiedad entera</b> del contrato —no
       * falla, no avisa: el árbol se publica sin ramas y el cliente generado no sabe que anida—.
       * Solo se ve leyendo {@code docs/api/openapi.json}.
       *
       * <p><b>Y va por {@code ref} y no por {@code implementation}</b>, que es lo primero que se
       * intenta: aquel apunta al esquema ya definido y <b>corta</b> la recursión, mientras que este
       * la reentra y <b>tumba la generación entera</b> con un {@code StackOverflowError} — el
       * contrato deja de emitirse y las veintinueve pruebas del contrato caen a la vez.
       */
      @ArraySchema(schema = @Schema(ref = "#/components/schemas/NetworkNode"))
          List<Node> children) {}

  /** La persona del nodo, con lo justo para nombrarla. */
  @Schema(name = "NetworkNodeUser")
  public record Holder(UUID id, String username, String firstName, String lastName) {}

  /**
   * El bloque de números, idéntico en los cuatro sitios donde aparece.
   *
   * <p><b>{@code accounts} cuenta CUENTAS y {@code consumers} cuenta PERSONAS</b>, y no tienen por
   * qué coincidir: un cliente con dos cuentas es una persona y dos cuentas. Van los dos porque
   * responden preguntas distintas —cuánto dinero entró y cuánta gente activó— y tenerlos juntos es
   * lo que impide confundirlos.
   *
   * <p><b>{@code conversion} es NULA cuando no hay cuentas, no cero.</b> Cero se lee como «nadie
   * convirtió» y la verdad es «no hay nada que convertir» — la misma distinción que `RN-SP-040`
   * hace con el nulo del nombre de usuario en el broker.
   *
   * <p><b>Es un cociente entre 0 y 1 y no un porcentaje</b>: quien lo pinte decide el formato, y un
   * {@code 44.44} obligaría a saber que ya viene multiplicado — el primero que lo multiplicara otra
   * vez no se daría cuenta.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "NetworkIndicators")
  public record Indicators(
      int accounts, int ftd, int pending, BigDecimal conversion, int consumers) {}
}
