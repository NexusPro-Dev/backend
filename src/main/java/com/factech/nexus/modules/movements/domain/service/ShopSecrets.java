package com.factech.nexus.modules.movements.domain.service;

import java.util.UUID;

/**
 * <b>El cifrado de la clave secreta de la tienda</b> de la pasarela local (`RN-MV-063`): se guarda
 * cifrada en la conversión del país y solo se descifra para llamar a la pasarela. El país va como
 * dato asociado: un texto cifrado copiado a otro país no se descifra.
 */
public interface ShopSecrets {

  /** Si hay llave maestra en este entorno: sin ella no se cifra ni se descifra nada. */
  boolean ready();

  /**
   * @throws IllegalStateException sin llave maestra
   */
  String encrypt(String plain, UUID countryId);

  /**
   * @throws IllegalStateException sin llave maestra, o si el texto no es de esta llave y este país
   */
  String decrypt(String encrypted, UUID countryId);
}
