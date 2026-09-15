package com.factech.nexus.modules.products.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.modules.products.domain.models.PackageOfferability.Producto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `RN-PM-039` y `RN-PM-040` — cada motivo y su ORDEN (`RF-PM-019` · `T-01`, `CA-PM-281`).
 *
 * <p>Cada caso reúne más de un motivo a la vez y comprueba que sale el que va primero: es lo que
 * hace verificable el orden y no solo la lista.
 */
class PackageOfferabilityTest {

  private static final Producto ACTIVO = new Producto("BOT_A", true, false);
  private static final Producto OTRO_ACTIVO = new Producto("BOT_B", true, false);
  private static final Producto INACTIVO = new Producto("UPGRADE_ORO", false, false);
  private static final Producto RETIRADO = new Producto("BOT_VIEJO", true, true);

  @Test
  @DisplayName("activo, con descripción, dos productos activos y vivos: ofrecible y sin motivo")
  void ofrecible() {
    PackageOfferability decision =
        PackageOfferability.decidir(
            PackageStatus.ACTIVO, false, true, List.of(ACTIVO, OTRO_ACTIVO));
    assertThat(decision.offerable()).isTrue();
    assertThat(decision.reason()).isNull();
  }

  @Test
  @DisplayName(
      "1º menos de dos — gana aunque además falte la descripción, esté inactivo y retirado")
  void menosDeDosPrimero() {
    PackageOfferability decision =
        PackageOfferability.decidir(PackageStatus.INACTIVO, true, false, List.of(INACTIVO));
    assertThat(decision.offerable()).isFalse();
    assertThat(decision.reason()).contains("menos de dos");
  }

  @Test
  @DisplayName("2º sin descripción — gana sobre inactivo, retirado y producto no ofrecible")
  void sinDescripcionSegundo() {
    PackageOfferability decision =
        PackageOfferability.decidir(PackageStatus.INACTIVO, true, false, List.of(ACTIVO, INACTIVO));
    assertThat(decision.reason()).contains("no tiene descripción");
  }

  @Test
  @DisplayName("3º inactivo — gana sobre retirado y producto no ofrecible")
  void inactivoTercero() {
    PackageOfferability decision =
        PackageOfferability.decidir(PackageStatus.INACTIVO, true, true, List.of(ACTIVO, RETIRADO));
    assertThat(decision.reason()).contains("está inactivo");
  }

  @Test
  @DisplayName("4º retirado — gana sobre el producto no ofrecible")
  void retiradoCuarto() {
    PackageOfferability decision =
        PackageOfferability.decidir(PackageStatus.ACTIVO, true, true, List.of(ACTIVO, RETIRADO));
    assertThat(decision.reason()).contains("El paquete está retirado");
  }

  @Test
  @DisplayName("5º un producto no ofrecible, NOMBRADO por su código, y el primero que lo sea")
  void productoNoOfrecibleUltimo() {
    PackageOfferability inactivo =
        PackageOfferability.decidir(
            PackageStatus.ACTIVO, false, true, List.of(ACTIVO, INACTIVO, RETIRADO));
    assertThat(inactivo.offerable()).isFalse();
    assertThat(inactivo.reason()).contains("UPGRADE_ORO").contains("inactivo");

    PackageOfferability retirado =
        PackageOfferability.decidir(PackageStatus.ACTIVO, false, true, List.of(RETIRADO, ACTIVO));
    assertThat(retirado.reason()).contains("BOT_VIEJO").contains("retirado");
  }

  @Test
  @DisplayName("el paquete vacío no es ofrecible por «menos de dos», aunque esté activo")
  void vacio() {
    PackageOfferability decision =
        PackageOfferability.decidir(PackageStatus.ACTIVO, false, true, List.of());
    assertThat(decision.offerable()).isFalse();
    assertThat(decision.reason()).contains("menos de dos");
  }
}
