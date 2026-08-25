package com.factech.nexus.modules.system.users.domain.models;

/**
 * Estado de una cuenta (`security.md` §9, {@code ck_users_status}).
 *
 * <p><b>{@code INACTIVO} y {@code BLOQUEADO} tienen significados separados</b> —organizativo frente
 * a seguridad— y no son sinónimos: quien deja la empresa queda inactivo; quien tropieza cinco veces
 * con su contraseña queda bloqueado. Fijado al aprobarse `RF-SP-028`.
 *
 * <p><b>{@code PENDIENTE} está declarado y sin usar a propósito</b> (`RF-SP-024` spec.md §14): el
 * alta deja la cuenta {@code ACTIVO} y marcada para cambio obligatorio. Existe en el dominio
 * cerrado para que el día que un requerimiento lo estrene no haga falta alterar el {@code CHECK} de
 * una tabla en uso.
 */
public enum UserStatus {
  ACTIVO,
  INACTIVO,
  BLOQUEADO,
  PENDIENTE
}
