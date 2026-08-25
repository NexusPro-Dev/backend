package com.factech.nexus.modules.system.countries.application;

import com.factech.nexus.modules.system.countries.domain.models.Country;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Un país en el contrato de la API.
 *
 * <p><b>Un solo tipo para los tres endpoints.</b> El alta, el listado y el cambio de estado
 * devuelven exactamente lo mismo; un tipo propio por endpoint sería una segunda representación sin
 * un solo campo de diferencia.
 *
 * <p><b>{@code isActive} se devuelve siempre</b>, también cuando no se pidieron los inactivos y por
 * tanto vale {@code true} en todos los elementos. Omitirlo en ese caso haría que la forma de la
 * respuesta dependiera de los parámetros, y obligaría al cliente a tratar dos formas del mismo
 * recurso.
 *
 * <p><b>{@code id} se devuelve aunque el selector muestre el nombre.</b> Es lo que se guarda al
 * referenciar un país: un código ISO puede reasignarse, un identificador no.
 *
 * <p><b>No existe {@code createdBy}</b>: el actor no vive en la tabla de negocio (Art. V.7). Quién
 * registró el país se responde con `RF-SP-011`.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CountryResponse(UUID id, String code, String name, boolean isActive) {

  public static CountryResponse from(Country pais) {
    return new CountryResponse(
        pais.getId(),
        pais.getCode() == null ? null : pais.getCode().trim(),
        pais.getName(),
        pais.isActive());
  }

  public static CountryResponse from(CountryItem item) {
    return new CountryResponse(item.id(), item.code(), item.name(), item.isActive());
  }
}
