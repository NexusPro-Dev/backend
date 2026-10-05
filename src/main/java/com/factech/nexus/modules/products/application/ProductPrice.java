package com.factech.nexus.modules.products.application;

import java.math.BigDecimal;

/**
 * La escala con la que sale un precio del catálogo, en <b>un solo sitio</b> (`RF-PM-003` ·
 * `plan.md` §10, riesgo 2).
 *
 * <p>Lo comparten las tres respuestas del módulo —el alta, el listado y el detalle—, y esa es toda
 * su razón de existir: escrita en cada una, el mismo producto llegaría con dos precios distintos
 * según por dónde se pidiera, y la diferencia solo se vería comparando dos respuestas que nadie
 * compara.
 *
 * <p><b>La escala la fija la MONEDA, no la columna.</b> Las centésimas en que se guarda el precio
 * (ADR-006) son una decisión de almacenamiento y no algo que el contrato deba exponer: {@code 50}
 * en una moneda sin fracción y no {@code 50.00} (`CA-PM-082`).
 */
public final class ProductPrice {

  private ProductPrice() {}

  /**
   * El precio con los decimales que declara su moneda.
   *
   * <p><b>Lo que no cabe no se redondea: se muestra.</b> `RN-PM-007` impide al escribir que un
   * precio tenga más decimales de los que su moneda admite, de modo que este caso no puede llegar
   * por la API. Si llegara —una carga directa en la base—, recortarlo <b>escondería el dato
   * inválido</b> justo en la pantalla donde alguien podría verlo, y encima cobraría de menos o de
   * más según hacia dónde se redondeara. `spec.md` §13 de `RF-PM-003` lo resuelve así: se devuelve
   * lo almacenado.
   *
   * <p>Con los valores válidos —los únicos que la API deja entrar— el resultado es exactamente la
   * escala de la moneda: {@code 49.9900} sale {@code 49.99} con dos decimales y {@code 50} con
   * cero.
   */
  /**
   * ¿Cabe este importe en una moneda de {@code decimales} decimales? (`RN-PM-007`)
   *
   * <p><b>Se mide sobre la escala significativa</b>, no sobre la que traiga el número. Y no es una
   * sutileza: el precio <b>leído de la base</b> viene con la escala de la columna —hasta {@code
   * V65} {@code numeric(14,4)}, de modo que {@code 49.99} llegaba como {@code 49.9900}; desde
   * entonces escala dos—, y compararlo en crudo daría más decimales que los de la moneda. El
   * síntoma fue exacto: cambiar <b>solo</b> la moneda de un producto, sin tocar su precio, se
   * rechazaba por decimales que ese precio no tiene.
   *
   * <p>Con la escala significativa, {@code 10.005} sigue sin caber en una moneda de dos decimales
   * —que es lo que `RN-PM-007` impide— y {@code 10.000} sí cabe, porque <b>es</b> {@code 10}.
   */
  public static boolean cabeEn(BigDecimal precio, int decimales) {
    return precio.stripTrailingZeros().scale() <= decimales;
  }

  public static BigDecimal enLaEscalaDe(BigDecimal precio, int decimales) {
    // `stripTrailingZeros` deja la escala MÍNIMA que representa el mismo
    // número, que es la que dice cuántos decimales tiene el dato de verdad.
    BigDecimal significativo = precio.stripTrailingZeros();
    return significativo.scale() > decimales ? significativo : significativo.setScale(decimales);
  }
}
