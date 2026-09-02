package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;

/**
 * Cuerpo de {@code PATCH /api/v1/commission-rates/{id}} (`RF-CM-003`).
 *
 * <p>{@link Patchable} distingue los <b>tres</b> estados: campo ausente, campo presente con nulo
 * explícito, y campo con valor. Aquí el nulo explícito <b>se rechaza</b>: una tasa sin porcentaje
 * no significa nada.
 *
 * <h2>Un solo campo corregible, y el que queda ya no es reversible</h2>
 *
 * <p>Hasta el 01-09-2026 esta petición corregía también el fin de vigencia, y por eso había una
 * diferencia entre <b>corregir</b> —arreglar un error, reescribiendo lo que la tarifa dijo que
 * rigió— y <b>cambiar</b> la comisión a partir de una fecha, que era cerrar la vigente y abrir
 * otra.
 *
 * <p><b>Sin vigencia, esa diferencia desaparece y solo queda reescribir.</b> Pasar un `AGENTE` de
 * 10 a 12 <b>borra el 10</b>: no hay dos filas contando cada una su parte, hay una que ahora dice
 * otra cosa. Lo único que puede preservar el pasado es que la liquidación haya copiado el
 * porcentaje que aplicó (`RN-CM-008`) — y esa liquidación todavía no existe.
 *
 * <h2>El rol está declarado A PROPÓSITO</h2>
 *
 * <p>{@code roleId} <b>no se corrige</b>: cambiarlo no corrige la tasa, la convierte en otra, y
 * arrastraría consigo todas sus asociaciones a un rol que nadie eligió. Se declara igualmente para
 * poder rechazarlo con <b>su</b> mensaje: sin él, quien intentara cambiarlo leería «propiedad
 * desconocida» y creería que se equivocó de nombre.
 */
public record UpdateCommissionRateRequest(
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<BigDecimal> percentage,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> roleId) {

  public UpdateCommissionRateRequest {
    percentage = percentage == null ? Patchable.ausente() : percentage;
    roleId = roleId == null ? Patchable.ausente() : roleId;
  }

  /** ¿Trae el que no se puede corregir? (`VAL-009`) */
  public boolean traeInmutables() {
    return roleId.presente();
  }

  /** ¿Se envió algún campo corregible, con el valor que sea? */
  public boolean informaAlgo() {
    return percentage.presente();
  }
}
