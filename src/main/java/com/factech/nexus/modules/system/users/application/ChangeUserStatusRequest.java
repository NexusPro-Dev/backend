package com.factech.nexus.modules.system.users.application;

/**
 * Cuerpo de {@code PATCH /api/v1/users/{id}/status} (`RF-SP-028`).
 *
 * <p><b>Se envía el estado destino y no una acción</b> —«desactivar», «bloquear»—, y eso hace la
 * operación <b>idempotente por construcción</b>: pedir el estado que ya se tiene no cambia nada,
 * sin que haga falta una regla que lo diga.
 *
 * <p><b>El motivo es condicional en los DOS sentidos.</b> Obligatorio al retirar el acceso y
 * <b>rechazado</b> al devolverlo. Aceptarlo en silencio al reactivar dejaría un texto que nadie
 * sabría si interpretar como justificación de la reactivación o como resto de una petición
 * anterior.
 *
 * <p><b>{@code lockedUntil} no está aquí.</b> El momento de expiración del bloqueo lo calcula el
 * sistema, y el bloqueo manual no tiene ninguno — es precisamente lo que lo distingue del
 * automático.
 */
public record ChangeUserStatusRequest(String status, String reason) {}
