package com.factech.nexus.modules.system.users.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Detalle de una persona (`RF-SP-026`).
 *
 * <p><b>{@code roles} lleva el estado de cada uno, y esa es la mitad de la respuesta que
 * importa.</b> La otra mitad es que {@code effectivePermissions} pueda llegar vacía: las dos juntas
 * son lo único que explica por qué una persona <b>con roles</b> no puede hacer nada — porque todos
 * están inactivos—. Con una sola de las dos, esa pantalla no responde la pregunta para la que
 * existe.
 *
 * <p>{@code effectivePermissions} son <b>códigos, ordenados y sin duplicados</b>. No se devuelven
 * identificadores ni descripciones: la pregunta es «qué puede hacer», y qué significa cada permiso
 * lo responde su propio catálogo. El orden alfabético hace la respuesta estable entre llamadas y
 * comparable entre personas.
 *
 * <p><b>No se pagina</b>, y es una excepción consciente a `architecture.md` §7.4: los permisos
 * efectivos de una persona son decenas, no constituyen un recurso navegable, y paginarlos obligaría
 * a dos peticiones para responder la única pregunta del requerimiento.
 *
 * <p>Lo que <b>no</b> lleva:
 *
 * <ul>
 *   <li><b>{@code failedAttempts}</b> — diría a cualquiera con permiso de lectura cuántos intentos
 *       le quedan a una cuenta antes de bloquearse. La columna existe y <b>la proyección no la
 *       selecciona</b>, que es lo único que hace verificable el criterio.
 *   <li><b>Ningún dato de la credencial</b>, ni la marca de cambio obligatorio: esa se le devuelve
 *       a su titular, que es quien tiene que actuar, y no aquí.
 *   <li><b>{@code deletedAt}</b> — una persona eliminada devuelve {@code 404}, de modo que el campo
 *       sería siempre nulo.
 *   <li><b>El superior comercial y el equipo</b> — devolverlos aquí habría sido barato y habría
 *       creado una segunda fuente del mismo dato.
 * </ul>
 *
 * <p><b>{@code lockedUntil} nulo significa dos cosas distintas, y eso es información:</b> la cuenta
 * no está bloqueada, o lo está <b>por decisión de un actor</b> y por tanto sin expiración. El
 * estado desambigua — {@code BLOQUEADO} con {@code lockedUntil} nulo es un bloqueo manual, que no
 * se levanta solo.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserDetailResponse(
    UUID id,
    String username,
    String email,
    String firstName,
    String lastName,
    String status,
    List<RoleRef> roles,
    List<String> effectivePermissions,
    MembershipRef membership,
    OffsetDateTime lastLoginAt,
    OffsetDateTime lockedUntil,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  /** Con su estado: es lo que explica que un rol asignado no conceda nada. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record RoleRef(UUID id, String code, String name, String status) {}

  /**
   * Lleva {@code level}, que el listado no devuelve: es el dato con el que se decide qué ofrecer.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record MembershipRef(
      UUID id, String code, String name, short level, OffsetDateTime endsAt, boolean current) {}
}
