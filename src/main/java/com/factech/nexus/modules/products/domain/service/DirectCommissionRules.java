package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.ProductPrice;
import com.factech.nexus.modules.products.domain.models.DirectCommission;
import com.factech.nexus.modules.products.domain.models.DirectCommissionType;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.math.BigDecimal;
import java.util.List;

/**
 * `RN-PM-051` entera, escrita una vez para el alta y la edición (`RF-PM-001` `plan.md` §12.2,
 * `RF-PM-004` `plan.md` §12).
 *
 * <p><b>Se comprueba contra el precio y la moneda QUE QUEDAN</b>, no contra los que había: en la
 * edición la directa y el precio están en la misma fila, y bajar el precio por debajo de una
 * directa fija tiene que rechazarse aunque la directa no viaje.
 *
 * <p><b>Los códigos los pone quien llama</b>, como en {@code ProductLinkBuilder}: el alta y la
 * edición numeran sus validaciones cada una por su cuenta.
 */
public final class DirectCommissionRules {

  private static final String CAMPO = "directCommission";
  private static final BigDecimal CIEN = BigDecimal.valueOf(100);

  /** Los códigos de cada operación. */
  public record Codigos(String obligatoria, String forma, String rangos, String tope, String ftd) {}

  public static final Codigos ALTA =
      new Codigos("VAL-024", "VAL-025", "VAL-026", "VAL-027", "VAL-028");

  /** En la edición la forma y los rangos comparten código (`RF-PM-004` `spec.md` §11). */
  public static final Codigos EDICION =
      new Codigos("VAL-019", "VAL-020", "VAL-020", "VAL-021", "VAL-022");

  private DirectCommissionRules() {}

  /**
   * @param directa la que queda; nula si no hay
   * @param ftd si el producto es un FTD, que no la lleva
   * @param precio el precio del sistema que queda
   * @param decimales los de la moneda que queda
   */
  public static void verificar(
      DirectCommission directa, boolean ftd, BigDecimal precio, int decimales, Codigos c) {
    if (ftd) {
      if (directa != null) {
        rechazar(c.ftd(), "Un producto FTD no lleva comisión por venta directa.");
      }
      return;
    }
    if (directa == null || directa.type() == null) {
      rechazar(
          c.obligatoria(),
          "El producto debe declarar su comisión por venta directa, de tipo porcentaje o fijo.");
    }
    // `RN-CM-016`: el tipo manda y el campo de la otra forma va vacío. Se mira
    // antes que los rangos para que el mensaje diga lo que de verdad está mal.
    boolean porcentaje = directa.type() == DirectCommissionType.PORCENTAJE;
    BigDecimal valor = porcentaje ? directa.percentage() : directa.fixedAmount();
    BigDecimal sobra = porcentaje ? directa.fixedAmount() : directa.percentage();
    if (valor == null || sobra != null) {
      rechazar(
          c.forma(),
          "La comisión por venta directa lleva solo el porcentaje o solo el importe fijo, según"
              + " su tipo.");
    }
    boolean fueraDeRango =
        valor.signum() < 0
            || (porcentaje && valor.compareTo(CIEN) > 0)
            || (!porcentaje && !ProductPrice.cabeEn(valor, decimales))
            || (porcentaje && !ProductPrice.cabeEn(valor, 2));
    if (fueraDeRango) {
      rechazar(
          c.rangos(),
          "El porcentaje de la comisión directa va de 0 a 100, y el importe fijo no puede ser"
              + " negativo ni tener más decimales que su moneda.");
    }
    // El tope contra el precio. Sobre precio cero no hay «cien por ciento» del
    // que pasarse, y un porcentaje de nada es nada: solo fijo, sin tope
    // (`RN-CM-020`).
    boolean gratuito = precio.signum() == 0;
    boolean excede = gratuito ? porcentaje : !porcentaje && valor.compareTo(precio) > 0;
    if (excede) {
      rechazar(
          c.tope(),
          "La comisión directa no puede superar el precio del producto; en un producto gratuito"
              + " solo puede ser un importe fijo.");
    }
  }

  private static void rechazar(String codigo, String mensaje) {
    throw new ValidationException(codigo, mensaje, List.of(new FieldError(CAMPO, codigo, mensaje)));
  }
}
