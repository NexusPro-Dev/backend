package com.factech.nexus.modules.movements.application;

import java.util.List;
import java.util.UUID;

/**
 * <b>Unas líneas de venta pueden haber quedado comisionables</b> (`RN-MV-049`).
 *
 * <p>Lo publican confirmar una venta (`RF-MV-003`) y asignar vendedores (`RF-MV-016`), <b>dentro de
 * su transacción</b>, de modo que quien escucha con {@code AFTER_COMMIT} solo lo recibe si la venta
 * quedó escrita. <b>Dice dónde mirar, no qué hay</b>: quien lo escucha relee cada línea por {@link
 * CommissionableLines#of}, y un aviso repetido, tardío o de una línea que no comisiona no hace
 * daño.
 *
 * <p>Es el primer evento entre módulos del sistema, y su forma es la regla para los que vengan
 * (`specs/cm/013-devengar-comision-linea/plan.md` §8): el {@code record} vive en el {@code
 * application} de quien publica, y `MV` no sabe quién lo escucha.
 */
public record CommissionableLinesEvent(UUID movementId, List<UUID> detailIds) {

  public CommissionableLinesEvent {
    detailIds = List.copyOf(detailIds);
  }
}
