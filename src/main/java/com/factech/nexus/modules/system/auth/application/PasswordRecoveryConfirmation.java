package com.factech.nexus.modules.system.auth.application;

/**
 * Cuerpo de {@code POST /api/v1/auth/password-recovery/confirmation} (`RF-SP-040`).
 *
 * <p><b>No lleva identificador de usuario</b>, y esa ausencia es deliberada: a quién pertenece la
 * cuenta lo dice el permiso, no quien lo presenta. Aceptar un identificador permitiría probar un
 * permiso válido contra la cuenta de otro.
 *
 * <p><b>La contraseña no se recorta</b>: un espacio al principio o al final es parte de ella.
 */
public record PasswordRecoveryConfirmation(String permit, String newPassword) {}
