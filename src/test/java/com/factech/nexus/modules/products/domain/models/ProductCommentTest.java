package com.factech.nexus.modules.products.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `RN-PM-025`, `RN-PM-027` y la corrección sin cambio (`RF-PM-009` · `T-03`, `RF-PM-010` · `T-02`).
 */
class ProductCommentTest {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 9, 14, 12, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime DESPUES = AHORA.plusHours(1);

  private static final UUID PRODUCTO = UUID.randomUUID();
  private static final UUID ANA = UUID.randomUUID();
  private static final UUID LUIS = UUID.randomUUID();

  @Test
  @DisplayName("el alta recorta el texto y deja las dos fechas iguales")
  void altaRecorta() {
    ProductComment resena = crear(5, "   Muy bueno   ");
    assertThat(resena.getComment()).isEqualTo("Muy bueno");
    assertThat(resena.getRating()).isEqualTo(5);
    assertThat(resena.getUpdatedAt()).isEqualTo(resena.getCreatedAt()).isEqualTo(AHORA);
    assertThat(resena.esDe(ANA)).isTrue();
    assertThat(resena.esDe(LUIS)).isFalse();
  }

  @Test
  @DisplayName("puntuación fuera de rango o ausente: VAL-003 / VAL-002")
  void puntuacionInvalida() {
    assertThatThrownBy(() -> crear(0, "Texto"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("entre 1 y 5");
    assertThatThrownBy(() -> crear(6, "Texto")).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> crear(null, "Texto"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("obligatoria");
  }

  @Test
  @DisplayName(
      "texto vacío, de solo espacios o de mil y uno: VAL-004 / VAL-005; mil exactos se admite")
  void textoInvalido() {
    assertThatThrownBy(() -> crear(4, null)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> crear(4, "     ")).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> crear(4, "x".repeat(1001)))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("1000");
    assertThat(crear(4, "  " + "x".repeat(1000) + "  ").getComment()).hasSize(1000);
  }

  @Test
  @DisplayName("los dos errores viajan juntos: quien se equivocó en dos corrige una vez")
  void losDosJuntos() {
    assertThatThrownBy(() -> crear(9, ""))
        .isInstanceOf(ValidationException.class)
        .satisfies(fallo -> assertThat(((ValidationException) fallo).errors()).hasSize(2));
  }

  @Test
  @DisplayName("corregir: lo presente cambia, updatedAt avanza, y el mapa dice antes y después")
  void corregir() {
    ProductComment resena = crear(3, "Original");

    Map<String, Object> cambios = resena.corregir(Patchable.de(5), Patchable.ausente(), DESPUES);

    assertThat(cambios).containsOnlyKeys("rating");
    assertThat(cambios.get("rating")).isEqualTo(Map.of("before", 3, "after", 5));
    assertThat(resena.getRating()).isEqualTo(5);
    assertThat(resena.getComment()).isEqualTo("Original");
    assertThat(resena.getUpdatedAt()).isEqualTo(DESPUES);
    assertThat(resena.getCreatedAt()).isEqualTo(AHORA);
  }

  @Test
  @DisplayName("corregir sin cambio de valor: mapa vacío y updatedAt intacto")
  void corregirSinCambio() {
    ProductComment resena = crear(3, "Original");

    Map<String, Object> cambios =
        resena.corregir(Patchable.de(3), Patchable.de("  Original  "), DESPUES);

    assertThat(cambios).isEmpty();
    assertThat(resena.getUpdatedAt()).isEqualTo(AHORA);
  }

  @Test
  @DisplayName("corregir con nulo explícito se rechaza: ningún campo admite vaciarse")
  void corregirConNulo() {
    ProductComment resena = crear(3, "Original");
    assertThatThrownBy(() -> resena.corregir(Patchable.de(null), Patchable.ausente(), DESPUES))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> resena.corregir(Patchable.ausente(), Patchable.de(null), DESPUES))
        .isInstanceOf(ValidationException.class);
    assertThat(resena.getRating()).isEqualTo(3);
  }

  @Test
  @DisplayName("retirar marca y no toca nada más; la instantánea tomada antes dice que estaba viva")
  void retirar() {
    ProductComment resena = crear(4, "Se va");
    Map<String, Object> antes = resena.instantanea();

    resena.retirar(DESPUES);

    assertThat(antes.get("deleted_at")).isNull();
    assertThat(antes.get("rating")).isEqualTo(4);
    assertThat(resena.estaRetirada()).isTrue();
    assertThat(resena.getRating()).isEqualTo(4);
    assertThat(resena.getComment()).isEqualTo("Se va");
    assertThat(resena.instantanea().get("deleted_at")).isEqualTo(DESPUES.toString());
  }

  private static ProductComment crear(Integer puntuacion, String texto) {
    return ProductComment.create(UUID.randomUUID(), PRODUCTO, ANA, puntuacion, texto, AHORA);
  }
}
