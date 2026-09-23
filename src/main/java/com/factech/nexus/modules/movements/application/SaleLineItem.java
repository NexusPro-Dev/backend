package com.factech.nexus.modules.movements.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una <b>línea</b> de venta y la venta que la explica: la fila del listado de `RF-MV-017` §6.2.
 *
 * <p><b>Es un contrato distinto de {@link SaleLineResponse}, que es la línea DENTRO del detalle</b>
 * (`RF-MV-001`). Comparten tabla y no propósito: aquella se lee ya sabiendo de qué venta es —la
 * cabecera está encima— y lleva las rebajas que explican el descuento; esta se lee suelta, en una
 * tabla de mil filas de ventas distintas, y por eso repite los datos de la venta en cada fila y no
 * baja al detalle de las rebajas. Fundirlas ataría cada cambio de una al otro, que es el mismo
 * motivo por el que {@code MovementRow} y {@code MyMovementRow} son dos registros.
 *
 * <p><b>La fila es la línea, no la venta.</b> Una venta de tres productos aporta tres filas con los
 * mismos datos de cabecera repetidos. Anidarlas haría la respuesta impaginable —el tamaño de página
 * dejaría de significar filas— y eso ya existe: es el detalle de `RF-MV-007`.
 *
 * <p><b>El nombre del producto es el CONGELADO</b> (`RN-MV-002`), el que la línea copió el día de
 * la venta; el <b>código no</b>, que se lee del catálogo porque `RN-PM-013` lo declara inmutable.
 * La asimetría no es un descuido: se copia lo que puede cambiar.
 *
 * <p><b>El estado de entrega va crudo y no derivado</b>, al contrario que en `RF-MV-014`, que
 * calcula un estado único a partir de la venta, la entrega y la vigencia. Allí lo pide quien
 * compró, que necesita una respuesta; aquí lo pide administración, que necesita saber <b>por
 * qué</b> algo está donde está — y derivarlo obligaría a repetir aquella máquina de estados, que es
 * la clase de duplicado que acaba divergiendo.
 */
/**
 * <b>Las claves nulas VIAJAN</b>, contra la configuración global de Jackson: la spec exige que
 * {@code seller}, {@code validityDays}, {@code deliveredAt} y {@code deliveryNote} salgan
 * <b>presentes y nulos</b>, porque una clave ausente se leería como que esta versión no registraba
 * el dato. Es la misma anotación —y el mismo motivo— que lleva {@link SaleLineResponse}.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SaleLineItem(
    @Schema(description = "La línea. No se publica en ninguna otra consulta.") UUID lineId,
    @Schema(description = "De qué venta viene; se abre con `GET /movements/{id}`.") UUID movementId,
    String movementCode,
    @Schema(description = "El estado de la VENTA, no el de la entrega.") String movementStatus,
    @Schema(description = "Cuándo ocurrió la venta. Es el orden del listado.")
        OffsetDateTime occurredAt,
    @Schema(description = "El SUJETO de la venta: a nombre de quién es.") Party client,
    // `types` y no `nullable`: el contrato es OpenAPI 3.1, donde springdoc
    // descarta `nullable` EN SILENCIO.
    @Schema(
            types = {"object", "null"},
            description =
                "Quien vendió ESTA línea (`RN-MV-003`). Presente y NULO cuando la línea no lo"
                    + " tiene; la fila no desaparece por eso.")
        Party seller,
    @Schema(description = "El producto, con el nombre CONGELADO del día de la venta.")
        ProductRef product,
    int quantity,
    @Schema(description = "COPIA del precio del catálogo el día de la venta. No se relee.")
        BigDecimal unitPrice,
    @Schema(description = "El descuento de la línea; sin él los importes no cuadran.")
        BigDecimal lineDiscount,
    BigDecimal lineAmount,
    @Schema(
            types = {"integer", "null"},
            description = "COPIA de la vigencia en días. NULO si lo comprado no caduca.")
        Integer validityDays,
    @Schema(description = "El código de la moneda de la venta: el importe sin ella no dice nada.")
        String currency,
    @Schema(description = "COPIA de cómo se entrega: AUTOMATICA o MANUAL.") String implementation,
    @Schema(description = "El estado de entrega DE LA LÍNEA: PENDIENTE, ENTREGADA o RETENIDA.")
        String deliveryStatus,
    @Schema(
            types = {"string", "null"},
            format = "date-time",
            description = "Cuándo se entregó. NULO si no se ha entregado.")
        OffsetDateTime deliveredAt,
    @Schema(
            types = {"string", "null"},
            description = "Por qué no se entregará. Solo en RETENIDA (`RN-MV-029`).")
        String deliveryNote) {

  /** Una persona, con lo justo para pintarla y para abrir su ficha. */
  @Schema(name = "SaleLineParty")
  public record Party(UUID id, String username, String name) {}

  /**
   * El producto <b>tal como se vendió</b>: el nombre es la copia de la línea (`RN-MV-002`) y el
   * código se lee del catálogo, que `RN-PM-013` declara inmutable.
   */
  @Schema(name = "SaleLineProductRef")
  public record ProductRef(UUID id, String code, String name) {}
}
