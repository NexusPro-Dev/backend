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
  @DisplayName("La instantánea de auditoría lleva el vendedor y las líneas con lo copiado")
  void laInstantanea() {
    Movement venta = registrar(linea("UP_VIP", 1, "20.00", 30));
    Map<String, Object> datos = venta.instantanea();

    // Es el dato que el actor no envió y que determina a quién se le va a
    // pagar: sin él, «¿por qué se le atribuyó a esta persona?» solo se responde
    // reconstruyendo la estructura comercial de aquel día.
    assertThat(datos).containsKey("seller_id");
    assertThat(datos.get("status")).isEqualTo("PENDIENTE");
    assertThat(datos.get("total_amount")).isEqualTo("20.00");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> lineas = (List<Map<String, Object>>) datos.get("lines");
    assertThat(lineas).hasSize(1);
    assertThat(lineas.get(0).get("unit_price")).isEqualTo("20.00");
    assertThat(lineas.get(0).get("validity_days")).isEqualTo(30);
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

  private Movement registrar(MovementLine... lineas) {
    return Movement.registrar(
        UUID.randomUUID(),
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
        codigo,
        "Producto " + codigo,
        cantidad,
        new BigDecimal(precio),
        vigencia);
  }
}
