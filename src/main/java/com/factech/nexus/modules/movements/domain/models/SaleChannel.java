package com.factech.nexus.modules.movements.domain.models;

/**
 * Por dónde entra una venta, y por tanto <b>contra qué oferta se valida</b> (`RN-MV-007`, desde el
 * 19-09-2026).
 *
 * <p>Son los dos canales de `RN-PM-019`: la tienda publica {@code TIENDA} y {@code AMBOS}; el
 * hotlink publica {@code HOTLINK} y {@code AMBOS}. Una venta que llega por un enlace y se valida
 * contra la tienda rechaza todo lo que es solo hotlink, que es exactamente lo que ese canal existe
 * para vender. El canal lo decide la entrada —el funcionario y la compra propia venden por la
 * tienda; el enlace de registro y el hotlink, por el hotlink— y nunca la petición.
 */
public enum SaleChannel {
  TIENDA,
  HOTLINK
}
