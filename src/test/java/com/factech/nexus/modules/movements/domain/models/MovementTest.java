package com.factech.nexus.modules.movements.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El agregado, sin base de datos (`RF-MV-001` · `T-08`).
 *
 * <p>Lo que estas pruebas fijan no es el {@code INSERT}: es que <b>no exista forma</b> de construir
 * una venta que incumpla `RN-MV-013`. Si el total lo sumara el caso de uso, la regla sería cierta
 * mientras nadie se equivocara; sumado aquí, no hay ningún instante en que no lo sea.
 */
class MovementTest {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 9, 4, 12, 0, 0, 0, ZoneOffset.UTC);

  @Test
  @DisplayName(
      "RN-MV-013: el total ES la suma de las líneas, y el importe a pagar es igual al total")
  void elTotalEsLaSuma() {
    Movement venta =
        registrar(
            linea("BOT_A", 2, "15.50", null),
            linea("BOT_B", 1, "10.00", 90),
            linea("UP_VIP", 1, "20.00", null));

    assertThat(venta.getTotalAmount()).isEqualByComparingTo("61.00");
    // Hoy no hay descuentos, y la columna existe igual: el día que lleguen, no
    // habrá que tocar ni una fila de lo ya vendido.
    assertThat(venta.getDiscountAmount()).isEqualByComparingTo("0.00");
    assertThat(venta.getPayableAmount()).isEqualByComparingTo(venta.getTotalAmount());
  }

  @Test
  @DisplayName("El importe de línea es cantidad por precio, y tampoco se puede pasar por parámetro")
  void elImporteDeLineaSeCalcula() {
    MovementLine linea = linea("BOT_A", 3, "15.50", null);
    assertThat(linea.getLineAmount()).isEqualByComparingTo("46.50");
  }

  @Test
  @DisplayName("Nace PENDIENTE, y no hay forma de construirla en otro estado")
  void nacePendiente() {
    assertThat(registrar(linea("BOT_A", 1, "10.00", null)).getStatus())
        .isEqualTo(MovementStatus.PENDIENTE);
  }

  @Test
  @DisplayName("RN-MV-009: una venta no existe sin al menos una línea")
  void sinLineasNoHayVenta() {
    // Protege de que un camino futuro —una venta armada desde otro sitio—
    // produzca una cabecera con total cero y nada que la explique.
    assertThatThrownBy(() -> registrar()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("La instantánea de auditoría lleva el sujeto, y el vendedor en cada línea")
  void laInstantanea() {
    Movement venta = registrar(linea("UP_VIP", 1, "20.00", 30));
    Map<String, Object> datos = venta.instantanea();

    // La cabecera lleva UN sujeto (`RN-MV-026`) y ninguna clave de vendedor:
    // desde el 16-09-2026 esa clave es de la línea.
    assertThat(datos).containsKey("user_id");
    assertThat(datos).doesNotContainKeys("client_id", "seller_id");
    assertThat(datos.get("status")).isEqualTo("PENDIENTE");
    assertThat(datos.get("total_amount")).isEqualTo("20.00");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> lineas = (List<Map<String, Object>>) datos.get("lines");
    // Es el dato que el actor no envió y que determina a quién se le va a
    // pagar: sin él, «¿por qué se le atribuyó a esta persona?» solo se responde
    // reconstruyendo la estructura comercial de aquel día.
    assertThat(lineas.get(0)).containsEntry("seller_id", VENDEDOR.toString());
    assertThat(lineas).hasSize(1);
    assertThat(lineas.get(0).get("unit_price")).isEqualTo("20.00");
    assertThat(lineas.get(0).get("validity_days")).isEqualTo(30);
    // `RN-MV-027`: el descuento y el paquete se escriben aunque estén vacíos,
    // con la clave presente, por lo mismo que la vigencia.
    assertThat(lineas.get(0)).containsEntry("line_discount", "0.00");
    assertThat(lineas.get(0)).containsEntry("package_id", null);
    assertThat(lineas.get(0).get("discounts")).isEqualTo(List.of());
  }

  // ---------------------------------------------------------------------------
  // `RN-MV-027` — el descuento es de la línea
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "Un porcentaje se congela en dinero sobre el precio unitario, redondeado a la moneda")
  void elPorcentajeSeCongelaEnDinero() {
    // 10 % de 49.99 = 4.999 → 5.00 a la mitad hacia arriba: se redondea LA
    // REBAJA y no el resultado, como `PM` (`RN-PM-036`).
    LineDiscount rebaja =
        LineDiscount.de(MovementDiscountType.PORCENTAJE, new BigDecimal("10"), precio("49.99"), 2);

    assertThat(rebaja.getDiscountValue()).isEqualByComparingTo("5.00");
    assertThat(rebaja.getValue()).isEqualByComparingTo("10");
  }

  @Test
  @DisplayName("Un fijo vale lo que dice, y la línea multiplica por la cantidad")
  void elFijoSeMultiplicaPorLaCantidad() {
    LineDiscount rebaja =
        LineDiscount.de(MovementDiscountType.FIJO, new BigDecimal("5"), precio("20.00"), 2);
    MovementLine linea = lineaConRebajas("BOT_A", 2, "20.00", List.of(rebaja));

    assertThat(rebaja.getDiscountValue()).isEqualByComparingTo("5.00");
    assertThat(linea.getLineDiscount()).isEqualByComparingTo("10.00");
    assertThat(linea.getLineAmount()).isEqualByComparingTo("30.00");
  }

  @Test
  @DisplayName("La cabecera suma las tres cifras desde las líneas")
  void laCabeceraSumaLasLineas() {
    LineDiscount diez =
        LineDiscount.de(MovementDiscountType.PORCENTAJE, new BigDecimal("10"), precio("20.00"), 2);
    Movement venta =
        registrar(
            lineaConRebajas("BOT_A", 2, "20.00", List.of(diez)), linea("BOT_B", 1, "10.00", null));

    // total = 2×20 + 1×10 = 50; descuento = 2×2.00 = 4; a pagar = 46.
    assertThat(venta.getTotalAmount()).isEqualByComparingTo("50.00");
    assertThat(venta.getDiscountAmount()).isEqualByComparingTo("4.00");
    assertThat(venta.getPayableAmount()).isEqualByComparingTo("46.00");
  }

  @Test
  @DisplayName("Ninguna rebaja deja la línea por debajo de cero, ni es negativa, ni pasa de cien")
  void lasRebajasImposibles() {
    LineDiscount seis =
        LineDiscount.de(MovementDiscountType.FIJO, new BigDecimal("6"), precio("5.00"), 2);
    assertThatThrownBy(() -> lineaConRebajas("BOT_A", 1, "5.00", List.of(seis)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                LineDiscount.de(MovementDiscountType.FIJO, new BigDecimal("-1"), precio("5.00"), 2))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                LineDiscount.de(
                    MovementDiscountType.PORCENTAJE, new BigDecimal("101"), precio("5.00"), 2))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("La instantánea de una línea rebajada lleva la rebaja como se pactó y como se cobró")
  void laInstantaneaDeLaRebaja() {
    LineDiscount diez =
        LineDiscount.de(MovementDiscountType.PORCENTAJE, new BigDecimal("10"), precio("20.00"), 2);
    UUID paquete = UUID.randomUUID();
    Movement venta = registrar(lineaConRebajas("BOT_A", 1, "20.00", paquete, List.of(diez)));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> lineas = (List<Map<String, Object>>) venta.instantanea().get("lines");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rebajas = (List<Map<String, Object>>) lineas.get(0).get("discounts");

    assertThat(lineas.get(0)).containsEntry("package_id", paquete.toString());
    assertThat(lineas.get(0)).containsEntry("line_discount", "2.00");
    assertThat(rebajas).hasSize(1);
    assertThat(rebajas.get(0)).containsEntry("type", "PORCENTAJE");
    assertThat(rebajas.get(0)).containsEntry("value", "10");
    assertThat(rebajas.get(0)).containsEntry("discount_value", "2.00");
  }

  private static BigDecimal precio(String valor) {
    return new BigDecimal(valor);
  }

  private static MovementLine lineaConRebajas(
      String codigo, int cantidad, String precio, List<LineDiscount> rebajas) {
    return lineaConRebajas(codigo, cantidad, precio, null, rebajas);
  }

  private static MovementLine lineaConRebajas(
      String codigo, int cantidad, String precio, UUID paquete, List<LineDiscount> rebajas) {
    return MovementLine.copiarDe(
        UUID.randomUUID(),
        VENDEDOR,
        paquete,
        codigo,
        "Producto " + codigo,
        cantidad,
        new BigDecimal(precio),
        null,
        rebajas);
  }

  @Test
  @DisplayName("La vigencia nula viaja como clave presente, no como clave ausente")
  void laVigenciaNulaSeEscribe() {
    Movement venta = registrar(linea("BOT_A", 1, "10.00", null));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> lineas = (List<Map<String, Object>>) venta.instantanea().get("lines");

    // Omitir la clave se leería como «esta versión no lo registraba»; el nulo
    // dice «no caduca», que es lo que significa.
    assertThat(lineas.get(0)).containsKey("validity_days");
    assertThat(lineas.get(0).get("validity_days")).isNull();
  }

  @Test
  @DisplayName(
      "El código se puede reemplazar sin rehacer la venta: es lo que el reintento necesita")
  void elCodigoSeReemplaza() {
    Movement venta = registrar(linea("BOT_A", 1, "10.00", null));
    BigDecimal totalAntes = venta.getTotalAmount();

    venta.reemplazarCodigo("VTA-20260904-ZZZZZZ");

    assertThat(venta.getCode()).isEqualTo("VTA-20260904-ZZZZZZ");
    // Al chocar contra el índice único la venta no cambia: los mismos
    // productos, el mismo importe, el mismo cliente. Solo hace falta otro
    // comprobante.
    assertThat(venta.getTotalAmount()).isEqualByComparingTo(totalAntes);
  }

  @Test
  @DisplayName("No existe forma de construir una línea de venta sin vendedor")
  void sinVendedorNoHayLinea() {
    // `RN-MV-003`: en una venta el vendedor es obligatorio, y el esquema no
    // puede sostenerlo porque «obligatorio en VENTA» exige mirar otra tabla.
    assertThatThrownBy(
            () ->
                MovementLine.copiarDe(
                    UUID.randomUUID(), null, "UP_VIP", "Producto", 1, new BigDecimal("20.00"), 30))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static final UUID VENDEDOR = UUID.randomUUID();

  private Movement registrar(MovementLine... lineas) {
    return Movement.registrar(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "VTA-20260904-K7M2QX",
        List.of(lineas),
        2,
        AHORA,
        AHORA);
  }

  private static MovementLine linea(String codigo, int cantidad, String precio, Integer vigencia) {
    return MovementLine.copiarDe(
        UUID.randomUUID(),
        VENDEDOR,
        codigo,
        "Producto " + codigo,
        cantidad,
        new BigDecimal(precio),
        vigencia);
  }
}
