package com.factech.nexus.modules.system.auth.application;

/**
 * Cuerpo de {@code POST /api/v1/auth/password-recovery} (`RF-SP-040`).
 *
 * <p>Nombre de usuario <b>o</b> correo, indistintamente y sin declarar cuál: la prohibición del
 * {@code @} en el nombre de usuario hace que ningún valor sea ambiguo. Es el mismo campo del inicio
 * de sesión y por el mismo motivo.
 *
 * <p><b>Un solo campo, y no hay más.</b> Cualquier otro dato que la petición aceptara sería un dato
 * que alguien puede probar contra la cuenta de otro.
 */
public record PasswordRecoveryRequest(String identifier) {}
