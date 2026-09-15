package com.factech.nexus.modules.products.domain.models;

/**
 * En qué vistas de venta se ofrece un producto (`RN-PM-019`, reescrita el 15-09-2026).
 *
 * <p><b>Son dos canales que se combinan, no una escala.</b> Hasta el 15-09-2026 eran dos valores
 * acumulativos —{@code TIENDA} y {@code HOTLINKS}, que <b>incluía</b> la tienda— y no había forma
 * de publicar algo <b>solo</b> en hotlinks ni de tener un producto activo que no se ofreciera en
 * ninguna parte. Los cuatro valores dicen exactamente en cuáles está: la oferta (`RF-PM-007`)
 * publica {@link #TIENDA} y {@link #AMBOS}; el hotlink (`RF-PM-008`) y su catálogo (`RF-PM-027`)
 * publican {@link #HOTLINK} y {@link #AMBOS}; {@link #NINGUNO} no se publica en ninguna vista de
 * venta y solo lo ve administración (`RF-PM-002`, `RF-PM-003`).
 *
 * <p><b>{@code HOTLINKS} dejó de existir y las filas que lo decían pasaron a {@code AMBOS}</b>
 * ({@code V92}): el renombrado es fiel al significado, y ningún producto cambió lo que mostraba. Es
 * la salida que este mismo enumerado dejó escrita el 07-09-2026 —un valor nuevo y no un cambio de
 * significado— llevada a sus últimas consecuencias: con las dos vistas construidas, los nombres ya
 * no fingen nada (`requirements/pm.md` §5.2.11).
 *
 * <p><b>{@code NINGUNO} no es un estado.</b> Un producto {@code NINGUNO} puede estar {@code
 * ACTIVO}, y la activación no lo rechaza: es un producto que existe y no se enseña, y la razón más
 * probable de que exista es que se venda dentro de un paquete, donde manda el alcance del paquete.
 */
public enum ProductScope {

  /** Solo la oferta (`RF-PM-007`). */
  TIENDA,

  /** Solo el canal de hotlinks (`RF-PM-008`, `RF-PM-027`). */
  HOTLINK,

  /** Las dos vistas. Es lo que {@code HOTLINKS} significaba hasta el 15-09-2026. */
  AMBOS,

  /** Ninguna vista de venta: solo administración. Puede estar activo. */
  NINGUNO;

  /** ¿Entra en la oferta? */
  public boolean enTienda() {
    return this == TIENDA || this == AMBOS;
  }

  /** ¿Entra en el canal de hotlinks? */
  public boolean enHotlinks() {
    return this == HOTLINK || this == AMBOS;
  }
}
