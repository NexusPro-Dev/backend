package com.factech.nexus.modules.movements.application;

import com.factech.nexus.modules.movements.domain.repository.MovementRepository.ExcludedCountryView;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.PaymentMethodCatalogView;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Un método de pago con **dónde no vale** (`RN-MV-019`).
 *
 * <p><b>{@code excludedCountries} viaja siempre, y vacío significa «vale en todas partes»</b>. Es
 * la misma decisión que `RF-MV-001` toma con el descuento: un cliente que tenga que distinguir «sin
 * exclusiones» de «no vino el campo» acabará tratándolo como opcional para siempre. Por eso
 * {@code @JsonInclude(ALWAYS)} y no la omisión por defecto.
 *
 * <p><b>Esta lista NO impide nada.</b> El servidor la publica y el cliente decide qué ofrecer; una
 * venta registrada con un método excluido entra con normalidad. Ver `spec.md` §2 de `RF-MV-009`.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PaymentMethodResponse(
    UUID id, String code, String name, List<ExcludedCountryResponse> excludedCountries) {

  static PaymentMethodResponse de(PaymentMethodCatalogView metodo) {
    return new PaymentMethodResponse(
        metodo.id(),
        metodo.code(),
        metodo.name(),
        metodo.excludedCountries().stream().map(ExcludedCountryResponse::de).toList());
  }

  /**
   * Un país en el que el método no vale.
   *
   * <p><b>Sin el nombre</b>: quien pinta países ya tiene su catálogo (`RF-SP-021`), y repetirlo
   * aquí lo dejaría desincronizado el día que se corrija una tilde.
   *
   * <p>El nombre publicado se declara a mano por lo mismo que en `RF-MV-001`: {@code
   * ExcludedCountryResponse} anidado se publicaría como un esquema genérico en un espacio de
   * nombres plano y compartido por todos los módulos.
   */
  @Schema(name = "ExcludedCountry")
  public record ExcludedCountryResponse(UUID id, String code) {

    static ExcludedCountryResponse de(ExcludedCountryView pais) {
      return new ExcludedCountryResponse(pais.id(), pais.code());
    }
  }
}
