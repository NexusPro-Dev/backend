package com.factech.nexus.modules.movements.domain.models;

/**
 * Los dos estados que el tipo {@code VENTA} declara en {@code movement_type_statuses} (`RN-MV-033`,
 * `V36`).
 *
 * <p><b>Es un eje aparte del pago</b> ({@link MovementStatus}) y de la entrega ({@link
 * DeliveryStatus}): responde «¿ya se sabe a quién se le atribuye cada línea?», y una venta puede
 * estar {@code CONFIRMADA} y en {@code VALIDAR_COMISIONES} a la vez.
 *
 * <p><b>El catálogo los declara y el caso de uso los decide</b>, como con los tipos: estos son los
 * códigos que el código sabe mover. Un estado sembrado en la tabla y ausente de aquí sería uno que
 * ninguna operación sabe producir.
 */
public enum SaleTypeStatus {
  /** Alguna línea no tiene vendedor: quien compra tenía varios, y nadie ha elegido todavía. */
  VALIDAR_COMISIONES,
  /** Todas las líneas tienen vendedor. Es el único estado sobre el que se podrá comisionar. */
  VALIDADO
}
