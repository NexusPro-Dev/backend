package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.ProductImplementation;
import com.factech.nexus.modules.products.domain.models.ProductScope;
import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de {@code PATCH /api/v1/products/{id}} (`RF-PM-004`).
 *
 * <p>{@link Patchable} distingue los <b>tres</b> estados que un `PATCH` necesita: campo ausente,
 * campo presente con nulo explícito, y campo con valor. Aquí la distinción no es teórica: decide
 * dos comportamientos <b>opuestos</b> — la descripción, el icono y la vigencia <b>admiten
 * vaciarse</b>, y el nombre <b>no</b>.
 *
 * <p><b>Y los dos precios se separan justo en esa distinción</b> (`RN-PM-023`): {@code
 * purchasePrice} —lo que NEXUS pagó por el producto— <b>admite vaciarse</b> —su nulo es un estado
 * legítimo de la columna, «no se conoce el costo»—, y {@code price} <b>no</b>, porque es {@code NOT
 * NULL} y «bórralo» no tiene ningún estado al que llevar el producto. <b>Vaciar el de compra no es
 * ponerlo a cero</b>: uno dice «no sé cuánto costó» y el otro «no costó nada». Esta operación es
 * hoy donde se registra lo que costó.
 *
 * <p><b>El icono se corrige aunque el tipo no</b>: es el aspecto del producto y no lo que otorga,
 * de modo que cambiarlo no reescribe lo comprado. En un producto de tipo bot, en cambio, cualquier
 * valor distinto de nulo se rechaza con `VAL-013` — `RN-PM-016` no admite excepción por venir en un
 * `PATCH`.
 *
 * <p><b>Los enlaces se corrigen EN BLOQUE</b> (`RN-PM-048`, 22-09-2026), y ahí se apartan de todo
 * lo demás de este cuerpo: la colección que llega <b>es la que queda</b>, de modo que un tipo que
 * no viene se borra. Vivieron como el campo `videoUrl` entre el 14-09-2026 y esa fecha, cuando el
 * producto tenía UN enlace y no enlaces con tipo. La forma de cada uno se comprueba en el dominio,
 * con los códigos de esta operación (`VAL-009` y `VAL-016` a `VAL-018`).
 *
 * <p><b>No se vuelve a intentar con {@code Optional}</b>: falló en `RF-SP-027` y falló en silencio,
 * porque Jackson entrega {@code Optional.empty()} tanto para el campo ausente como para el nulo
 * explícito.
 *
 * <h2>Los tres inmutables están declarados A PROPÓSITO</h2>
 *
 * <p>{@code type}, {@code code} y LAS DOS MEMBRESIAS. {@code targetMembershipId} <b>no se pueden
 * corregir</b>: definen qué derecho otorga el producto, y cambiarlos convertiría lo comprado en
 * otra cosa. Se declaran igualmente —como {@code Patchable<Object>}, porque su valor no importa—
 * para poder rechazarlos con <b>su</b> mensaje.
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
    /**
     * Los enlaces del producto, <b>en bloque</b> (`RN-PM-048`).
     *
     * <p>Es el primer campo de este módulo que se corrige entero y no uno a uno, y conserva los
     * tres estados de {@link Patchable} con un significado que aquí vale <b>para el conjunto</b>:
     * <b>ausente</b> no toca ningún enlace; <b>nula o vacía</b> los quita todos —la vacía va con la
     * nula, como {@code ""} iba con el nulo en el campo {@code videoUrl} que esto sustituye—; y
     * <b>con entradas</b>, la colección que llega <b>es la que queda</b>.
     *
     * <p>El coste está escrito y aceptado: quien mande un solo enlace <b>borra el otro sin haberlo
     * nombrado</b> (`CA-PM-390`).
     */
    @JsonDeserialize(using = PatchableDeserializer.class) @Valid
        Patchable<List<ProductLinkRequest>> links,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<BigDecimal> price,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<BigDecimal> purchasePrice,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<UUID> currencyId,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Integer> validityDays,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<ProductScope> scope,
    @JsonDeserialize(using = PatchableDeserializer.class)
        Patchable<ProductImplementation> implementation,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> type,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> code,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> targetMembershipId,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> sourceMembershipId) {

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
    links = links == null ? Patchable.ausente() : links;
    price = price == null ? Patchable.ausente() : price;
    purchasePrice = purchasePrice == null ? Patchable.ausente() : purchasePrice;
    currencyId = currencyId == null ? Patchable.ausente() : currencyId;
    validityDays = validityDays == null ? Patchable.ausente() : validityDays;
    scope = scope == null ? Patchable.ausente() : scope;
    implementation = implementation == null ? Patchable.ausente() : implementation;
    type = type == null ? Patchable.ausente() : type;
    code = code == null ? Patchable.ausente() : code;
    targetMembershipId = targetMembershipId == null ? Patchable.ausente() : targetMembershipId;
    sourceMembershipId = sourceMembershipId == null ? Patchable.ausente() : sourceMembershipId;
  }

  /** ¿Trae alguno de los CUATRO campos que no se pueden corregir? (`VAL-006`) */
  public boolean traeInmutables() {
    return type.presente()
        || code.presente()
        || targetMembershipId.presente()
        || sourceMembershipId.presente();
  }

  /** ¿Se envió algún campo corregible, con el valor que sea? */
  public boolean informaAlgo() {
    return name.presente()
        || description.presente()
        || icon.presente()
        || links.presente()
        || price.presente()
        || purchasePrice.presente()
        || currencyId.presente()
        || validityDays.presente()
        || scope.presente()
        || implementation.presente();
  }
}
