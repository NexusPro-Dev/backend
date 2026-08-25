package com.factech.nexus.modules.system.users.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Una fila del listado de personas (`RF-SP-025`).
 *
 * <p>Lo que <b>no</b> lleva es tan deliberado como lo que lleva, y cada ausencia tiene su criterio:
 *
 * <ul>
 *   <li><b>Ningún campo derivado de la credencial</b> —ni la marca de cambio obligatorio, ni la
 *       antigüedad del resumen, ni su longitud—. No es una omisión de redacción: el registro no
 *       tiene esos campos y la proyección no los selecciona, que es lo único que hace verificable
 *       el criterio.
 *   <li><b>Ni permisos efectivos</b>: resolverlos por fila costaría una unión de permisos por
 *       persona. Esa pregunta la responde el detalle.
 *   <li><b>Ni {@code lockedUntil}</b>: es nulo en la inmensa mayoría de las filas. Quien quiera
 *       saber quién no puede entrar filtra por estado, que es la pregunta operativa real.
 * </ul>
 *
 * <p>{@code roles} va <b>siempre presente y vacía</b> cuando la persona no tiene ninguno: una
 * persona sin roles es un estado válido, y distinguirlo con la ausencia del campo obligaría al
 * cliente a tratar dos formas del mismo recurso.
 *
 * <p>{@code deletedAt} está <b>siempre presente</b> y vale nulo en las personas vigentes. La
 * especificación dice que informa «solo cuando se piden los eliminados»; se interpreta como que es
 * entonces cuando <b>dice algo</b>, no como que el campo aparece y desaparece.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserListItem(
    UUID id,
    String username,
    String email,
    String firstName,
    String lastName,
    String status,
    List<UserResponse.RoleRef> roles,
    MembershipRef membership,
    OffsetDateTime deletedAt) {

  /**
   * La membresía de la fila.
   *
   * <p><b>No es nula cuando está vencida.</b> Vencer no es lo mismo que no tener (`RN-SP-014`), y
   * este endpoint es el primero que publica la distinción: {@code current} dice cuál de los dos
   * casos es y {@code endsAt} dice hasta cuándo fue.
   *
   * <p>{@code current} <b>se calcula, no se almacena</b>, y contra el instante de la propia
   * transacción de la base de datos: usar el reloj de la aplicación haría que dos instancias con
   * relojes desalineados dieran respuestas distintas sobre la misma fila.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record MembershipRef(
      UUID id, String code, String name, OffsetDateTime endsAt, boolean current) {}
}
