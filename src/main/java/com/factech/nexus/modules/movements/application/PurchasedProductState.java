package com.factech.nexus.modules.movements.application;

/**
 * En qué estado está un producto comprado (`RF-MV-014` · `spec.md` §2.1), <b>calculado</b> de lo
 * que la venta, la entrega y la vigencia ya dicen — no se guarda en ningún sitio.
 *
 * <p>Son ocho y cerrados, y el contrato los enumera para que quien pinte la pantalla no adivine.
 *
 * <p><b>Desde el 23-09-2026 los dos finales ya no se calculan, se leen</b> (`RN-MV-036`): la
 * entrega escribe la posesión y con ella la fecha, de modo que «hasta cuándo» dejó de depender de
 * lo que el catálogo diga hoy.
 */
public enum PurchasedProductState {
  /** La venta está pendiente: lo comprado todavía no se tiene (`RN-MV-004`). */
  PENDIENTE_PAGO,
  /** La venta se confirmó y la línea es manual: espera a que alguien la autorice (`RN-MV-021`). */
  PENDIENTE_AUTORIZACION,
  /** Entregada y con la vigencia por delante, o sin vigencia (`RN-PM-015`). */
  ACTIVO,
  /** Entregada y con la vigencia pasada. */
  VENCIDO,
  /**
   * Entregada, y se dejó de tener <b>antes</b> de su fecha (`RN-MV-036`, `V38`).
   *
   * <p><b>Hoy lo produce un solo caso</b>: una membresía <b>sustituida</b> por otra que se compró
   * encima, cuya fila se cierra el día de la compra nueva. Hasta el 23-09-2026 esa línea aparecía
   * como {@code ACTIVO} hasta que pasara una fecha que ya no significaba nada.
   *
   * <p><b>Cancelar algo entregado sigue sin ser una operación del sistema</b>: `RF-MV-008` solo
   * anula ventas `PENDIENTE`, que no entregaron nada. El estado queda definido para cuando esa
   * operación exista, y no se inventa aquí.
   */
  CANCELADO,
  /** La venta se confirmó y esta línea no se entregará (`RN-MV-029`). */
  RETENIDO,
  /** La venta se rechazó. */
  RECHAZADO,
  /** La venta se anuló. */
  ANULADO;

  /**
   * Si la línea <b>está entregada</b>: {@code ACTIVO} o {@code VENCIDO} (`RN-MV-030`).
   *
   * <p>Son los dos estados que se apoyan en {@code delivery_status = ENTREGADA}, y por eso son los
   * dos —y solo los dos— en los que viaja el cupón del bot (`RN-MV-032`).
   *
   * <p><b>Un {@code VENCIDO} cuenta como entregado</b>, y conviene no leerlo como un descuido: la
   * vigencia que pasó es la de <b>lo que se compró</b>, no la del enlace. Esconder la dirección no
   * le quita a nadie el acceso que ya tiene —lo aloja un tercero— y solo haría que el registro de
   * lo comprado <b>mintiera sobre lo que se entregó</b>.
   *
   * <p><b>Vive en el enumerado y no en el servicio</b> por lo mismo que {@code
   * ProductLinkType.esMaterialDeVenta()}: es la respuesta a «¿esto ya se entregó?», y escrita en
   * cada sitio que la necesite habría una ocasión por sitio de equivocarse en el sentido que <b>no
   * falla, entrega</b>.
   */
  public boolean estaEntregado() {
    // CANCELADO entra por el mismo argumento que VENCIDO, y no por descuido: la
    // línea está `ENTREGADA` y el enlace lo aloja un tercero. Que se haya dejado
    // de tener la posesión no deshace la entrega, y esconder la dirección haría
    // que el registro de lo comprado mintiera sobre lo que se entregó.
    return this == ACTIVO || this == VENCIDO || this == CANCELADO;
  }
}
