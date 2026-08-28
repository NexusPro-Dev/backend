package com.factech.nexus.modules.products.domain.models;

/**
 * Qué derecho otorga un producto (`RN-PM-001`).
 *
 * <p><b>El tipo se fija al crear y no lo cambia ninguna operación.</b> No es una etiqueta: decide
 * qué otras columnas son obligatorias —`RN-PM-002`— y qué se adquiere al comprarlo. Convertir un
 * {@link #SERVICIO} en {@link #UPGRADE_MEMBRESIA} después de venderlo reescribiría qué compró quien
 * lo compró.
 */
public enum ProductType {

  /** Da derecho a pasar a la membresía que el producto declara como destino. */
  UPGRADE_MEMBRESIA,

  /** Da derecho a una prestación del sistema, sin efecto sobre el nivel de acceso de nadie. */
  SERVICIO;

  /** ¿Este tipo exige membresía destino? Es la primera mitad de `RN-PM-002`. */
  public boolean exigeDestino() {
    return this == UPGRADE_MEMBRESIA;
  }
}
