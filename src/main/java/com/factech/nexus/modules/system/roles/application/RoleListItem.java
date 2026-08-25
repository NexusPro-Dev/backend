package com.factech.nexus.modules.system.roles.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una fila del listado de roles (`RF-SP-002`).
 *
 * <p>Lo que <b>no</b> lleva es tan deliberado como lo que lleva:
 *
 * <ul>
 *   <li><b>Ni {@code permissions}</b> (`spec.md` §4.2). No es una omisión de redacción: la
 *       proyección no toca {@code role_permissions} en ningún momento, que es lo único que hace
 *       verificable el criterio. Con la entidad, bastaría con que un mapeador recorriera la
 *       colección perezosa para que apareciera una consulta por fila. Los permisos los responde
 *       `RF-SP-003`.
 *   <li><b>Ni el número de usuarios asignados</b> (`CA-SP-148`). La pregunta se hace sobre un rol
 *       concreto, no sobre la lista: en el detalle cuesta una consulta y aquí una por fila.
 *   <li><b>Ni {@code createdAt} o {@code updatedAt}</b>: se admiten como criterio de ordenamiento
 *       —`spec.md` §6.1 lo pide— pero el listado no los muestra. Devolverlos engordaría cada fila
 *       con dos marcas que la interfaz del catálogo no usa; el detalle sí las lleva.
 * </ul>
 *
 * <p>{@code parentRole} es <b>nulo en el rol raíz</b>, y por eso la sentencia usa {@code LEFT
 * JOIN}: con un {@code JOIN} interno el rol raíz desaparecería del listado sin error visible y el
 * catálogo de un sistema recién instalado se vería incompleto.
 *
 * <p>{@code deletedAt} está <b>siempre presente</b> y vale nulo en los roles vigentes. La
 * especificación dice que informa «solo cuando se piden los eliminados»; se lee como que es
 * entonces cuando <b>dice algo</b>, no como que el campo aparece y desaparece — sin él, {@code
 * includeDeleted=true} devuelve una mezcla que el cliente no puede distinguir y `CA-SP-011`
 * quedaría satisfecho con una respuesta inútil.
 *
 * <p>{@code isSystem} va en la respuesta porque el listado es la entrada natural a editar, cambiar
 * de estado y eliminar: sin él, la interfaz ofrece acciones que `RN-SEG-012` va a rechazar.
 *
 * <p>{@code @JsonInclude(ALWAYS)} por lo mismo que en {@link RoleResponse}: {@code application.yml}
 * declara {@code non_null} para todo el sistema, y sin la anotación un rol sin descripción llegaría
 * sin la propiedad en lugar de con {@code null}.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RoleListItem(
    UUID id,
    String code,
    String name,
    String description,
    String roleType,
    String status,
    boolean isSystem,
    RoleSummaryResponse parentRole,
    OffsetDateTime deletedAt) {}
