package com.factech.nexus.modules.products.application;

import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cuerpo de {@code PATCH /api/v1/products/{id}} (`RF-PM-004`).
 *
 * <p>{@link Patchable} distingue los <b>tres</b> estados que un `PATCH` necesita: campo ausente,
 * campo presente con nulo explícito, y campo con valor. Aquí la distinción no es teórica: decide
 * dos comportamientos <b>opuestos</b> — la descripción, el icono y la vigencia <b>admiten
 * vaciarse</b>, y el nombre <b>no</b>.
 *
 * <p><b>El icono se corrige aunque el tipo no</b>: es el aspecto del producto y no lo que otorga,
 * de modo que cambiarlo no reescribe lo comprado. En un producto de tipo bot, en cambio, cualquier
 * valor distinto de nulo se rechaza con `VAL-013` — `RN-PM-016` no admite excepción por venir en un
 * `PATCH`.
 *
 * <p><b>No se vuelve a intentar con {@code Optional}</b>: falló en `RF-SP-027` y falló en silencio,
 * porque Jackson entrega {@code Optional.empty()} tanto para el campo ausente como para el nulo
 * explícito.
 *
 * <h2>Los tres inmutables están declarados A PROPÓSITO</h2>
 *
 * <p>{@code type}, {@code code} y {@code targetMembershipId} <b>no se pueden corregir</b>: definen
 * qué derecho otorga el producto, y cambiarlos convertiría lo comprado en otra cosa. Se declaran
 * igualmente —como {@code Patchable<Object>}, porque su valor no importa— para poder rechazarlos
 * con <b>su</b> mensaje.
 *
 * <p>Sin ellos, {@code FAIL_ON_UNKNOWN_PROPERTIES} ya devolvería {@code 400}, pero con el texto
 * genérico de Jackson: quien intente cambiar el código leería «propiedad desconocida» y creería que
 * se equivocó de nombre, en lugar de enterarse de que el código <b>no se cambia nunca</b>.
 * Rechazarlos y no ignorarlos es `CA-PM-033`: ignorarlos haría creer que el cambio se aplicó.
 */
public record UpdateProductRequest(
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> name,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> description,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> icon,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<BigDecimal> price,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<UUID> currencyId,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Integer> validityDays,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> type,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> code,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> targetMembershipId) {

  /**
   * El campo que Jackson no vio llega como {@code null} al constructor canónico.
   *
   * <p>Convertirlo aquí es lo que permite que el resto del código no tenga que comprobar nulos por
   * ningún lado: a partir de este punto, <b>todo</b> campo es un {@code Patchable} con su estado.
   */
  public UpdateProductRequest {
    name = name == null ? Patchable.ausente() : name;
    description = description == null ? Patchable.ausente() : description;
    icon = icon == null ? Patchable.ausente() : icon;
    price = price == null ? Patchable.ausente() : price;
    currencyId = currencyId == null ? Patchable.ausente() : currencyId;
    validityDays = validityDays == null ? Patchable.ausente() : validityDays;
    type = type == null ? Patchable.ausente() : type;
    code = code == null ? Patchable.ausente() : code;
    targetMembershipId = targetMembershipId == null ? Patchable.ausente() : targetMembershipId;
  }

  /** ¿Trae alguno de los tres campos que no se pueden corregir? (`VAL-006`) */
  public boolean traeInmutables() {
    return type.presente() || code.presente() || targetMembershipId.presente();
  }

  /** ¿Se envió algún campo corregible, con el valor que sea? */
  public boolean informaAlgo() {
    return name.presente()
        || description.presente()
        || icon.presente()
        || price.presente()
        || currencyId.presente()
        || validityDays.presente();
  }
}
