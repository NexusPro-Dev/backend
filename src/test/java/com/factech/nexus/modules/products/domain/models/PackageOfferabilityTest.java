package com.factech.nexus.modules.products.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.modules.products.domain.models.PackageOfferability.Producto;
import com.factech.nexus.modules.products.domain.models.PackageOfferability.Vigencia;
import java.time.LocalDate;
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

  private static final LocalDate HOY = LocalDate.of(2026, 9, 16);
  private static final Vigencia VIGENTE = new Vigencia(HOY, HOY.minusDays(10), null);
  private static final Vigencia VENCIDA = new Vigencia(HOY, HOY.minusDays(10), HOY.minusDays(1));
  private static final Vigencia FUTURA = new Vigencia(HOY, HOY.plusDays(1), null);

  @Test
  @DisplayName("activo, con descripción, dos productos activos y vivos: ofrecible y sin motivo")
  void ofrecible() {
    PackageOfferability decision =
        PackageOfferability.decidir(
            PackageStatus.ACTIVO, false, true, VIGENTE, List.of(ACTIVO, OTRO_ACTIVO));
    assertThat(decision.offerable()).isTrue();
    assertThat(decision.reason()).isNull();
  }

  @Test
  @DisplayName(
      "1º menos de dos — gana aunque además falte la descripción, esté inactivo y retirado")
  void menosDeDosPrimero() {
    PackageOfferability decision =
        PackageOfferability.decidir(
            PackageStatus.INACTIVO, true, false, VIGENTE, List.of(INACTIVO));
    assertThat(decision.offerable()).isFalse();
    assertThat(decision.reason()).contains("menos de dos");
  }

  @Test
  @DisplayName("2º sin descripción — gana sobre inactivo, retirado y producto no ofrecible")
  void sinDescripcionSegundo() {
    PackageOfferability decision =
        PackageOfferability.decidir(
            PackageStatus.INACTIVO, true, false, VIGENTE, List.of(ACTIVO, INACTIVO));
    assertThat(decision.reason()).contains("no tiene descripción");
  }

  @Test
  @DisplayName("3º inactivo — gana sobre retirado y producto no ofrecible")
  void inactivoTercero() {
    PackageOfferability decision =
        PackageOfferability.decidir(
            PackageStatus.INACTIVO, true, true, VIGENTE, List.of(ACTIVO, RETIRADO));
    assertThat(decision.reason()).contains("está inactivo");
  }

  @Test
  @DisplayName("4º retirado — gana sobre el producto no ofrecible")
  void retiradoCuarto() {
    PackageOfferability decision =
        PackageOfferability.decidir(
            PackageStatus.ACTIVO, true, true, VIGENTE, List.of(ACTIVO, RETIRADO));
    assertThat(decision.reason()).contains("El paquete está retirado");
  }

  @Test
  @DisplayName("5º un producto no ofrecible, NOMBRADO por su código, y el primero que lo sea")
  void productoNoOfrecibleUltimo() {
    PackageOfferability inactivo =
        PackageOfferability.decidir(
            PackageStatus.ACTIVO, false, true, VIGENTE, List.of(ACTIVO, INACTIVO, RETIRADO));
    assertThat(inactivo.offerable()).isFalse();
    assertThat(inactivo.reason()).contains("UPGRADE_ORO").contains("inactivo");

    PackageOfferability retirado =
        PackageOfferability.decidir(
            PackageStatus.ACTIVO, false, true, VIGENTE, List.of(RETIRADO, ACTIVO));
    assertThat(retirado.reason()).contains("BOT_VIEJO").contains("retirado");
  }

  @Test
  @DisplayName("el paquete vacío no es ofrecible por «menos de dos», aunque esté activo")
  void vacio() {
    PackageOfferability decision =
        PackageOfferability.decidir(PackageStatus.ACTIVO, false, true, VIGENTE, List.of());
    assertThat(decision.offerable()).isFalse();
    assertThat(decision.reason()).contains("menos de dos");
  }

  @Test
  @DisplayName(
      "RN-PM-047 — 5º la vigencia, DESPUÉS del retiro y ANTES del producto: empieza mañana y"
          + " terminó ayer llevan la fecha; termina hoy, empieza hoy e indefinido se ofrecen")
  void vigenciaEnSuSitio() {
    // Retirado y vencido: gana «retirado», que es lo que hay que arreglar primero.
    assertThat(
            PackageOfferability.decidir(
                    PackageStatus.ACTIVO, true, true, VENCIDA, List.of(ACTIVO, OTRO_ACTIVO))
                .reason())
        .contains("retirado");
    // Vencido y con un producto inactivo: gana la vigencia, que es del paquete.
    PackageOfferability vencido =
        PackageOfferability.decidir(
            PackageStatus.ACTIVO, false, true, VENCIDA, List.of(ACTIVO, INACTIVO));
    assertThat(vencido.offerable()).isFalse();
    assertThat(vencido.reason()).isEqualTo("La vigencia del paquete terminó el 2026-09-15.");
    PackageOfferability futuro =
        PackageOfferability.decidir(
            PackageStatus.ACTIVO, false, true, FUTURA, List.of(ACTIVO, OTRO_ACTIVO));
    assertThat(futuro.offerable()).isFalse();
    assertThat(futuro.reason())
        .isEqualTo("El paquete todavía no está vigente: empieza el 2026-09-17.");

    // El día de fin cuenta entero, y el de inicio también; sin fin, siempre.
    for (Vigencia vigente :
        List.of(
            new Vigencia(HOY, HOY.minusDays(10), HOY),
            new Vigencia(HOY, HOY, HOY),
            new Vigencia(HOY, HOY, null),
            new Vigencia(HOY, HOY.minusYears(1), null))) {
      assertThat(
              PackageOfferability.decidir(
                      PackageStatus.ACTIVO, false, true, vigente, List.of(ACTIVO, OTRO_ACTIVO))
                  .offerable())
          .as("%s", vigente)
          .isTrue();
    }
  }
}
