package com.factech.nexus.modules.system.users.application;

/**
 * Cuerpo de {@code POST /api/v1/users/{id}/password-reset} (`RF-SP-038`).
 *
 * <p>`POST` sobre un subrecurso y no `PATCH` sobre el usuario: cada petición <b>crea</b> un
 * restablecimiento, que es un hecho con fecha y con caducidad propia, no la edición de un campo.
 *
 * <p><b>La contraseña asignada no se devuelve en la respuesta</b>: la conoce quien la escribió, y
 * repetirla la expondría a cualquier registro de la operación.
 *
 * <p>No se recorta, por lo mismo que en el alta: un espacio al principio o al final es parte de la
 * contraseña, y recortarla haría fallar el primer inicio de sesión de su titular.
 */
public record ResetPasswordRequest(String newPassword) {}
