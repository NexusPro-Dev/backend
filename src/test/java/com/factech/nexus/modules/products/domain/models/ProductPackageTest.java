package com.factech.nexus.modules.products.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.images.CambioDePortada;
import com.factech.nexus.shared.patch.Patchable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El agregado del paquete (`RF-PM-017` · `T-03`): lo que normaliza y lo que no tiene. */
class ProductPackageTest {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 9, 15, 10, 0, 0, 0, ZoneOffset.UTC);
  private static final UUID USD = UUID.randomUUID();
  private static final LocalDate DESDE = LocalDate.of(2026, 9, 15);

  @Test
  @DisplayName("nace INACTIVO, con el código en mayúsculas, el nombre recortado y sin precio")
  void naceInactivoYNormalizado() {
    ProductPackage paquete =
        ProductPackage.create(
            UUID.randomUUID(),
            "  combo_oro ",
            "  Combo Oro  ",
            "   ",
            USD,
            ProductScope.AMBOS,
            DESDE,
            null,
            AHORA);

    assertThat(paquete.getStatus()).isEqualTo(PackageStatus.INACTIVO);
    assertThat(paquete.getCode()).isEqualTo("COMBO_ORO");
    assertThat(paquete.getName()).isEqualTo("Combo Oro");
    // La descripción de espacios sale NULA: es lo que `RF-PM-021` mira para
    // publicar, y una cadena vacía la engañaría.
    assertThat(paquete.getDescription()).isNull();
    assertThat(paquete.tieneDescripcion()).isFalse();
    assertThat(paquete.estaRetirado()).isFalse();
    // La instantánea NO lleva precio: no existe (`RN-PM-036`).
    assertThat(paquete.instantanea())
        .doesNotContainKey("price")
        .containsEntry("status", "INACTIVO")
        .containsEntry("valid_from", "2026-09-15")
        .containsEntry("valid_to", null);
    assertThat(paquete.getValidFrom()).isEqualTo(DESDE);
    assertThat(paquete.getValidTo()).isNull();
  }

  @Test
  @DisplayName(
      "RN-PM-047 — sin inicio VAL-006, fin anterior VAL-007; mismo día, sin fin y pasado se admiten")
  void vigencia() {
    assertThatThrownBy(() -> ProductPackage.verificarVigencia(null, null))
        .isInstanceOfSatisfying(
            ValidationException.class, e -> assertThat(e.errorCode()).isEqualTo("VAL-006"));
    assertThatThrownBy(() -> ProductPackage.verificarVigencia(DESDE, DESDE.minusDays(1)))
        .isInstanceOfSatisfying(
            ValidationException.class, e -> assertThat(e.errorCode()).isEqualTo("VAL-007"));
    // Un solo día, indefinido y ya pasado: los tres son legítimos — cerrar un
    // paquete es poner el fin en ayer, y el alta no distingue cerrar de errar.
    ProductPackage.verificarVigencia(DESDE, DESDE);
    ProductPackage.verificarVigencia(DESDE, null);
    ProductPackage.verificarVigencia(DESDE.minusYears(1), DESDE.minusMonths(6));

    ProductPackage paquete =
        ProductPackage.create(
            UUID.randomUUID(),
            "COMBO",
            "Combo",
            null,
            USD,
            ProductScope.TIENDA,
            DESDE,
            DESDE.plusDays(30),
            AHORA);
    assertThat(paquete.getValidTo()).isEqualTo(DESDE.plusDays(30));
    assertThat(paquete.instantanea()).containsEntry("valid_to", "2026-10-15");
  }

  @Test
  @DisplayName(
      "update corrige las dos fechas, el nulo vacía el fin y se rechaza en el inicio, y la pareja"
          + " resultante se comprueba ANTES de aplicar nada")
  void updateDeLaVigencia() {
    ProductPackage paquete =
        ProductPackage.create(
            UUID.randomUUID(),
            "COMBO",
            "Combo",
            null,
            USD,
            ProductScope.TIENDA,
            DESDE,
            DESDE.plusDays(30),
            AHORA);
    OffsetDateTime despues = AHORA.plusMinutes(5);

    // Solo el inicio, a una fecha posterior al fin que ya había: VAL-007 y NADA
    // cambia — ni el nombre que venía en la misma petición.
    assertThatThrownBy(
            () ->
                paquete.update(
                    Patchable.de("Otro"),
                    Patchable.ausente(),
                    Patchable.ausente(),
                    Patchable.de(DESDE.plusDays(31)),
                    Patchable.ausente(),
                    despues))
        .isInstanceOfSatisfying(
            ValidationException.class, e -> assertThat(e.errorCode()).isEqualTo("VAL-007"));
    assertThat(paquete.getName()).isEqualTo("Combo");
    assertThat(paquete.getUpdatedAt()).isEqualTo(AHORA);

    // El inicio no admite vaciarse.
    assertThatThrownBy(
            () ->
                paquete.update(
                    Patchable.ausente(),
                    Patchable.ausente(),
                    Patchable.ausente(),
                    Patchable.de(null),
                    Patchable.ausente(),
                    despues))
        .isInstanceOfSatisfying(
            ValidationException.class, e -> assertThat(e.errorCode()).isEqualTo("VAL-006"));

    // Las dos juntas, y el fin a nulo: vuelve a indefinido, con su antes y su después.
    Map<String, Object> cambios =
        paquete.update(
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.de(DESDE.plusDays(1)),
            Patchable.de(null),
            despues);
    assertThat(cambios).containsOnlyKeys("valid_from", "valid_to");
    assertThat(cambios.get("valid_from"))
        .isEqualTo(Map.of("before", "2026-09-15", "after", "2026-09-16"));
    assertThat(cambios.get("valid_to")).isEqualTo(Map.of("before", "2026-10-15", "after", ""));
    assertThat(paquete.getValidTo()).isNull();
    assertThat(paquete.getUpdatedAt()).isEqualTo(despues);

    // El mismo valor no es un cambio.
    assertThat(
            paquete.update(
                Patchable.ausente(),
                Patchable.ausente(),
                Patchable.ausente(),
                Patchable.de(DESDE.plusDays(1)),
                Patchable.de(null),
                despues.plusMinutes(1)))
        .isEmpty();
  }

  @Test
  @DisplayName("el código con forma inválida se rechaza con VAL-001, también el vacío")
  void codigoInvalido() {
    assertThatThrownBy(
            () ->
                ProductPackage.create(
                    UUID.randomUUID(),
                    "combo-oro",
                    "Combo",
                    null,
                    USD,
                    ProductScope.TIENDA,
                    DESDE,
                    null,
                    AHORA))
        .isInstanceOfSatisfying(
            ValidationException.class, e -> assertThat(e.errorCode()).isEqualTo("VAL-001"));
    assertThatThrownBy(
            () ->
                ProductPackage.create(
                    UUID.randomUUID(),
                    null,
                    "Combo",
                    null,
                    USD,
                    ProductScope.TIENDA,
                    DESDE,
                    null,
                    AHORA))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  @DisplayName("update devuelve solo lo que cambió de verdad y mueve updatedAt solo entonces")
  void updateDevuelveElDiff() {
    ProductPackage paquete =
        ProductPackage.create(
            UUID.randomUUID(),
            "COMBO",
            "Combo",
            "Una descripción",
            USD,
            ProductScope.TIENDA,
            DESDE,
            null,
            AHORA);
    OffsetDateTime despues = AHORA.plusMinutes(5);

    Map<String, Object> sinCambio =
        paquete.update(
            Patchable.de("Combo"),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            despues);
    assertThat(sinCambio).isEmpty();
    assertThat(paquete.getUpdatedAt()).isEqualTo(AHORA);

    Map<String, Object> cambios =
        paquete.update(
            Patchable.de("Combo Plus"),
            Patchable.de(null),
            Patchable.de(ProductScope.AMBOS),
            Patchable.ausente(),
            Patchable.ausente(),
            despues);
    assertThat(cambios).containsOnlyKeys("name", "description", "scope");
    assertThat(cambios.get("description"))
        .isEqualTo(Map.of("before", "Una descripción", "after", ""));
    assertThat(paquete.getDescription()).isNull();
    assertThat(paquete.getScope()).isEqualTo(ProductScope.AMBOS);
    assertThat(paquete.getUpdatedAt()).isEqualTo(despues);
  }

  @Test
  @DisplayName("activar, desactivar y retirar devuelven si hubo cambio; retirar dos veces no")
  void transiciones() {
    ProductPackage paquete =
        ProductPackage.create(
            UUID.randomUUID(),
            "COMBO",
            "Combo",
            null,
            USD,
            ProductScope.TIENDA,
            DESDE,
            null,
            AHORA);
    assertThat(paquete.activate(AHORA)).isTrue();
    assertThat(paquete.activate(AHORA)).isFalse();
    assertThat(paquete.deactivate(AHORA)).isTrue();
    assertThat(paquete.delete(AHORA)).isTrue();
    assertThat(paquete.delete(AHORA)).isFalse();
    assertThat(paquete.estaRetirado()).isTrue();
  }

  @Test
  @DisplayName("asignarPortada devuelve la anterior y el diff; la instantánea lleva cover_image_id")
  void asignaLaPortada() {
    ProductPackage paquete =
        ProductPackage.create(
            UUID.randomUUID(),
            "COMBO",
            "Combo",
            null,
            USD,
            ProductScope.TIENDA,
            DESDE,
            null,
            AHORA);
    assertThat(paquete.instantanea()).containsEntry("cover_image_id", null);

    UUID primera = UUID.randomUUID();
    CambioDePortada cambio = paquete.asignarPortada(primera, AHORA.plusDays(1));
    assertThat(cambio.anterior()).isNull();
    assertThat(cambio.huboCambio()).isTrue();
    assertThat(cambio.cambios())
        .containsEntry("cover_image_id", Map.of("before", "", "after", primera.toString()));
    assertThat(paquete.getCoverImageId()).isEqualTo(primera);
    assertThat(paquete.getUpdatedAt()).isEqualTo(AHORA.plusDays(1));
    assertThat(paquete.instantanea()).containsEntry("cover_image_id", primera.toString());

    UUID segunda = UUID.randomUUID();
    CambioDePortada reemplazo = paquete.asignarPortada(segunda, AHORA.plusDays(2));
    assertThat(reemplazo.anterior()).isEqualTo(primera);
    assertThat(reemplazo.cambios())
        .containsEntry(
            "cover_image_id", Map.of("before", primera.toString(), "after", segunda.toString()));
  }

  @Test
  @DisplayName(
      "quitarPortada nunca rechaza (RN-PM-045): con portada devuelve la anterior; sin portada, nada")
  void quitaLaPortadaSiempre() {
    ProductPackage paquete =
        ProductPackage.create(
            UUID.randomUUID(),
            "COMBO",
            "Combo",
            null,
            USD,
            ProductScope.TIENDA,
            DESDE,
            null,
            AHORA);

    // Sin portada: vacío, sin diff, sin excepción y sin mover updatedAt.
    CambioDePortada nada = paquete.quitarPortada(AHORA.plusDays(1));
    assertThat(nada.anterior()).isNull();
    assertThat(nada.huboCambio()).isFalse();
    assertThat(paquete.getUpdatedAt()).isEqualTo(AHORA);

    // Con portada —y sin icono ni color que exigir—: se quita.
    UUID imagen = UUID.randomUUID();
    paquete.asignarPortada(imagen, AHORA.plusDays(1));
    CambioDePortada cambio = paquete.quitarPortada(AHORA.plusDays(2));
    assertThat(cambio.anterior()).isEqualTo(imagen);
    assertThat(cambio.cambios())
        .containsEntry("cover_image_id", Map.of("before", imagen.toString(), "after", ""));
    assertThat(paquete.getCoverImageId()).isNull();
    assertThat(paquete.getUpdatedAt()).isEqualTo(AHORA.plusDays(2));
  }
}
