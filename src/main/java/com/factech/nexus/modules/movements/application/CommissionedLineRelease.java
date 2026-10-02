package com.factech.nexus.modules.movements.application;

import java.util.UUID;

/**
 * <b>Liberar una línea comisionada para que cambie de vendedor</b> (`RN-MV-053`, `RF-MV-016`): la
 * pregunta que `MV` hace antes de corregir el vendedor de una línea de una venta confirmada.
 *
 * <p><b>La declara `MV` y la implementa `CM`</b> (`RF-CM-024`; `architecture.md` §15.2, la segunda
 * inversión). Si una comisión está pagada lo sabe `CM`, y `CM` ya depende de `MV`: que `MV`
 * importara una interfaz de `CM` cerraría el ciclo. Con el puerto aquí, la dependencia de
 * compilación sigue siendo `CM` → `MV`, y <b>`MV` no sabe qué es una comisión</b>.
 *
 * <p><b>No es una lectura</b>: si la línea puede cambiar de dueño, quien la implementa <b>revierte
 * su cadena de comisiones</b> antes de responder. Por eso <b>corre dentro de la transacción de
 * quien llama y exige que haya una</b>: si la corrección se rechaza después —otra línea de la misma
 * petición se negó—, la reversión se deshace con ella.
 *
 * <p><b>Responde, no lanza</b>: una negativa es una respuesta legítima, y qué {@code 4xx} produce
 * lo decide `MV`, que tiene el contrato HTTP. Un fallo inesperado sí sube, y revierte todo.
 */
public interface CommissionedLineRelease {

  /**
   * @param movementDetailId la línea cuyo vendedor se va a corregir
   * @param actorId quien corrige, al que se atribuye la reversión
   */
  ReleaseOutcome release(UUID movementDetailId, UUID actorId);

  /** Lo que responde {@link #release}. */
  enum ReleaseOutcome {
    /** Puede cambiar de vendedor, y su cadena vieja ya está revertida. */
    LIBERADA,
    /** Alguna comisión de su cadena está en un lote pagado. */
    COMISION_PAGADA,
    /** Es un FTD que ya se contó en una liquidación afftrack. */
    FTD_CONTADO
  }
}
