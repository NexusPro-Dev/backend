package com.factech.nexus.modules.products.domain.models;

/**
 * Qué derecho otorga un producto (`RN-PM-001`).
 *
 * <p><b>El tipo se fija al crear y no lo cambia ninguna operación.</b> No es una etiqueta: decide
 * qué otras columnas son obligatorias —`RN-PM-002`—, cuáles están prohibidas —`RN-PM-016`— y qué se
 * adquiere al comprarlo. Convertir un {@link #BOT} en {@link #UPGRADE_MEMBRESIA} después de
 * venderlo reescribiría qué compró quien lo compró.
 *
 * <p><b>{@code BOT} se llamó {@code SERVICIO} hasta el 28-08-2026</b>, por decisión del responsable
 * del proyecto. Fue un renombrado y no un cambio de semántica: sigue siendo el producto que da
 * derecho a una prestación y no toca el nivel de acceso de nadie. Se pudo hacer porque todavía no
 * existe ninguna tabla de compras que apunte a un producto — ver {@code V43}.
 */
public enum ProductType {

  /** Da derecho a pasar a la membresía que el producto declara como destino. */
  UPGRADE_MEMBRESIA,

  /** Da derecho a una prestación del sistema, sin efecto sobre el nivel de acceso de nadie. */
  BOT;

  /** ¿Este tipo exige membresía destino? Es la primera mitad de `RN-PM-002`. */
  public boolean exigeDestino() {
    return this == UPGRADE_MEMBRESIA;
  }

  /**
   * ¿Este tipo admite icono? (`RN-PM-016`)
   *
   * <p>Se pregunta en positivo —«admite»— y no en negativo, porque la regla no obliga a nada: un
   * upgrade <b>puede</b> declarar icono y un {@link #BOT} no puede. Es la asimetría que la
   * distingue de {@link #exigeDestino()}, donde la primera mitad sí obliga.
   */
  public boolean admiteIcono() {
    return this == UPGRADE_MEMBRESIA;
  }
}
