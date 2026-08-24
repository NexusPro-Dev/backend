package com.factech.nexus.modules.system.auth.application;

/**
 * Cuerpo de {@code POST /api/v1/auth/password} (`RF-SP-037`).
 *
 * <p><b>No lleva identificador de usuario, y esa ausencia es la implementación de la regla.</b> El
 * sujeto es siempre quien porta el token: no hay campo por el que dirigir la operación a un
 * tercero, del mismo modo que el cierre de sesión no puede cerrar la de otro. Restablecer la
 * contraseña de otra persona es una operación distinta, con su propio permiso.
 *
 * <p><b>Ninguna de las dos se recorta.</b> Un espacio al principio o al final es parte de la
 * contraseña; recortarla cambiaría en silencio lo que la persona escribió — y en el campo vigente
 * convertiría un acierto en un fallo que consume intento.
 */
public record ChangePasswordRequest(String currentPassword, String newPassword) {}
