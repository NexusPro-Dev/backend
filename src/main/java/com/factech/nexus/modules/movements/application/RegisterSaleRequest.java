package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/movements} (`RF-MV-001`).
 *
 * <h2>Lo que este registro NO tiene es la mitad del diseño de la operación</h2>
 *
 * <p><b>No hay precio.</b> Quien registra la venta indica qué productos y cuántos, y <b>nunca
 * cuánto cuestan</b>. Un precio que llega en la petición es un precio que elige quien vende, y eso
 * tiene un nombre: es un descuento — que hoy no existe por decisión del responsable del proyecto
 * (`requirements/mv.md` §1.3). La consecuencia es que <b>el importe de la venta no es negociable en
 * el momento de registrarla</b>.
 *
 * <p><b>No hay moneda.</b> Es la del producto que se vende, y por eso `RN-MV-012` no comprueba un
 * campo enviado sino <b>las líneas entre sí</b>: si dos productos vienen en monedas distintas, no
 * hay ninguna venta posible que las contenga.
 *
 * <p><b>No hay vendedor.</b> Sale del cliente (`RN-MV-003`). Pedirlo permitiría atribuirse la venta
 * de otro, que es exactamente lo que congelarlo evita.
 *
 * <p><b>No hay estado.</b> Toda venta nace pendiente y no existe camino por el que esta operación
 * produzca otra cosa (`RN-MV-004`).
 *
 * @param occurredAt <b>el único campo opcional</b>, y existe por un caso real: un funcionario
 *     registra el lunes la venta que se cerró el sábado. Sin él, todo lo vendido llevaría la fecha
 *     en que alguien tuvo tiempo de teclearlo — y esa fecha es además <b>la que sale impresa en el
 *     código del comprobante</b> (`RN-MV-016`). Por omisión, ahora. No puede estar en el futuro
 *     (`VAL-007`), porque una venta que aún no ha ocurrido no es un hecho; el pasado remoto sí se
 *     admite, que es justo lo que hace falta para registrar lo que ya ocurrió
 */
public record RegisterSaleRequest(
    @NotNull(message = "VAL-001: El cliente de la venta es obligatorio.") UUID clientId,
    @NotNull(message = "VAL-002: El método de pago es obligatorio.") UUID paymentMethodId,
    @NotEmpty(message = "VAL-003: Una venta debe llevar al menos un producto.") @Valid
        List<Line> lines,
    OffsetDateTime occurredAt) {

  /**
   * Una línea: qué y cuántos.
   *
   * <p><b>`VAL-005` acota la cantidad por abajo y no por arriba.</b> Nada acota cuántos bots caben
   * en una venta, y ponerle un número aquí sería inventarlo (`spec.md` §13). Que en un upgrade sea
   * <b>uno</b> lo comprueba `RN-MV-015` en el caso de uso, y no aquí: si el producto es un upgrade
   * no se sabe mirando la petición.
   *
   * <p><b>El nombre publicado NO es {@code Line}</b>, que es como se llamaría por ser un registro
   * anidado. En el contrato los esquemas viven en un espacio de nombres <b>plano y compartido por
   * todos los módulos</b>, y el día que otro publique su propia «línea» —de una factura, de un
   * gráfico— springdoc emitiría {@code Line} y {@code Line_1} <b>sin garantizar cuál es cuál</b>:
   * el cliente generado del frontend cambiaría de tipo sin que nada fallara. Es el mismo defecto
   * que ya tienen los {@code operationId} de este contrato, y aquí no cuesta nada evitarlo.
   */
  @Schema(name = "SaleLineRequest")
  public record Line(
      @NotNull(message = "VAL-004: Cada línea debe indicar su producto.") UUID productId,
      @NotNull(message = "VAL-005: La cantidad de cada línea es obligatoria.")
          @Min(value = 1, message = "VAL-005: La cantidad de cada línea debe ser mayor que cero.")
          Integer quantity) {}
}
