package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.ProductScope;
import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDate;

/**
 * Cuerpo de la corrección de un paquete (`RF-PM-020` §11): nombre, descripción, alcance y —desde el
 * 16-09-2026— las dos fechas de vigencia, cada uno <b>ausente</b>, <b>presente con valor</b> o
 * <b>presente y nulo</b>. El nulo <b>vacía</b> la descripción y el fin de vigencia; en el nombre,
 * el alcance y el inicio de vigencia <b>se rechaza</b>.
 *
 * <p>{@code code} y {@code currencyId} se declaran <b>para rechazarlos</b> (`EX-003`), no para
 * ignorarlos: ignorarlos haría creer que el cambio se aplicó. Sin ellos, {@code
 * FAIL_ON_UNKNOWN_PROPERTIES} ya devolvería {@code 400}, pero con el texto genérico de «campo no
 * admitido», y quien lo envió merece saber que lo que intenta es cambiar un inmutable.
 */
public record UpdatePackageRequest(
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> name,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> description,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<ProductScope> scope,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<LocalDate> validFrom,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<LocalDate> validTo,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> code,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> currencyId) {

  public UpdatePackageRequest {
    name = name == null ? Patchable.ausente() : name;
    description = description == null ? Patchable.ausente() : description;
    scope = scope == null ? Patchable.ausente() : scope;
    validFrom = validFrom == null ? Patchable.ausente() : validFrom;
    validTo = validTo == null ? Patchable.ausente() : validTo;
    code = code == null ? Patchable.ausente() : code;
    currencyId = currencyId == null ? Patchable.ausente() : currencyId;
  }

  public boolean traeInmutables() {
    return code.presente() || currencyId.presente();
  }

  public boolean informaAlgo() {
    return name.presente()
        || description.presente()
        || scope.presente()
        || validFrom.presente()
        || validTo.presente();
  }
}
