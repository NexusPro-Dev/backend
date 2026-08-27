package com.factech.nexus.modules.products.domain.models;

/**
 * Si el producto se ofrece o no (`RN-PM-009`).
 *
 * <p><b>Texto y no un {@code boolean}</b>, al revés que los catálogos de `SP`: el dominio es
 * candidato a crecer —un {@code BORRADOR} que permita preparar un producto sin publicarlo es
 * previsible— y añadir un valor a un {@code varchar} con {@code CHECK} es una migración, mientras
 * que convertir un {@code boolean} en tres estados es una reescritura de todo lo que lo consulta.
 *
 * <p><b>Todo producto nace {@link #INACTIVO}</b> (`RN-PM-012`). Con eso, `RN-PM-004` —un solo
 * upgrade activo por destino— se comprueba en un solo sitio, `RF-PM-005`, en lugar de en dos que
 * acabarían divergiendo.
 */
public enum ProductStatus {

  /** Se ofrece. Solo `RF-PM-005` puede poner un producto aquí. */
  ACTIVO,

  /** Existe y no se ofrece. Es el estado en el que nace todo producto. */
  INACTIVO
}
