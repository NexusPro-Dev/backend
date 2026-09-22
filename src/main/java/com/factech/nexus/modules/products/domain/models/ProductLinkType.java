package com.factech.nexus.modules.products.domain.models;

/**
 * Los tipos de enlace que un producto puede declarar (`RN-PM-048`).
 *
 * <p><b>Es un enumerado y no un catálogo administrable</b>, y esa es la decisión (`pm.md` §5.2.14).
 * Un tipo de enlace no es un dato: cada uno trae consigo <b>dónde se publica</b>, y eso es código.
 * Un catálogo en base dejaría crear {@code CUPON_LOTERIA} desde la interfaz sin que nadie hubiera
 * escrito qué hace el sistema con él, y la fila existiría sin que ninguna lectura la mirara.
 *
 * <p>El dominio es el mismo que el de {@code ck_product_links_type}, para que lo que el esquema
 * admite y lo que el dominio admite sean exactamente lo mismo.
 */
public enum ProductLinkType {

  /**
   * El video que presenta el producto (`RN-PM-032`).
   *
   * <p>Recoge lo que hasta el 22-09-2026 era la columna {@code products.video_url}. Lo que aquella
   * regla decide no cambió: sale en <b>las cuatro lecturas</b>, hotlink sin token incluido, porque
   * es <b>material de venta</b> y no un costo.
   */
  VIDEO_PRESENTACION(true),

  /**
   * Dónde registra su cuenta quien ya compró el bot.
   *
   * <p><b>No es material de venta: es entrega</b> (`RN-PM-050`). No sale del catálogo público, ni
   * de la oferta, ni de los dos hotlinks —enseñarlo antes de cobrar <b>regala la prestación</b>— y
   * se publica <b>solo</b> en `RF-MV-014`, y <b>solo en la línea entregada</b> (`RN-MV-032`):
   * publicarlo en una pendiente sería entregar sin la autorización que `RN-MV-021` exige.
   *
   * <p>Administración sí lo ve, en `RF-PM-002` y `RF-PM-003`, porque lo administra.
   */
  CUPON_BOT(false);

  private final boolean materialDeVenta;

  ProductLinkType(boolean materialDeVenta) {
    this.materialDeVenta = materialDeVenta;
  }

  /**
   * Si el tipo se publica en las lecturas de venta —la oferta y los dos hotlinks— (`RN-PM-050`).
   *
   * <p><b>Vive en el enumerado y no en cada consulta</b> a propósito: es la respuesta a «¿esto se
   * puede enseñar a quien todavía no ha comprado?», y escrita en cuatro sitios habría cuatro
   * ocasiones de olvidarla. El fallo de olvidarla no se ve — <b>no falla, publica</b>.
   *
   * <p>Aun así <b>no sustituye al filtro en el predicado</b> de esas consultas: esto es lo que
   * decide, y aquel es lo que impide que el dato salga de la base.
   */
  public boolean esMaterialDeVenta() {
    return materialDeVenta;
  }
}
