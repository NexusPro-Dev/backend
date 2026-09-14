package com.factech.nexus.modules.system.users.domain.models;

/**
 * Estado de una cuenta (`security.md` §9, {@code ck_users_status}).
 *
 * <p><b>{@code INACTIVO} y {@code BLOQUEADO} tienen significados separados</b> —organizativo frente
 * a seguridad— y no son sinónimos: quien deja la empresa queda inactivo; quien tropieza cinco veces
 * con su contraseña queda bloqueado. Fijado al aprobarse `RF-SP-028`.
 *
 * <p><b>{@code FTD_PENDIENTE} lo estrena `RF-SP-045` el 09-09-2026</b>, y hasta entonces se llamó
 * {@code PENDIENTE} y no lo usaba nadie. El nombre cambió porque el viejo no decía pendiente <b>de
 * qué</b>: este estado significa una cosa muy concreta —la cuenta existe, <b>autentica</b> y <b>no
 * opera</b> hasta que haya un primer depósito confirmado—, y un nombre que no lo diga acaba
 * usándose para «pendiente de revisión» o «pendiente de correo». <b>FTD</b> es el primer depósito.
 *
 * <p><b>Es el primer estado distinto de {@code ACTIVO} que puede autenticarse</b>, y esa es la
 * consecuencia que hay que tener presente al leer {@code AuthUser.puedeEntrar()}: quien se registra
 * por enlace entra al sistema desde el primer momento, y lo que no puede es operar.
 */
public enum UserStatus {
  ACTIVO,
  INACTIVO,
  BLOQUEADO,
  FTD_PENDIENTE
}
