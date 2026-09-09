package com.factech.nexus.modules.system.users.application;

import java.util.UUID;

/**
 * Lo que el registro por enlace devuelve (`RF-SP-045`).
 *
 * <p><b>No lleva credenciales de sesión</b> (`CA-SP-521`): registrarse no es iniciar sesión. Quien
 * acaba de crear su cuenta pasa por `RF-SP-034` como todo el mundo — devolver un token aquí
 * duplicaría la emisión de sesiones en un endpoint público.
 *
 * <p><b>Sí lleva el estado, y con él lo que falta para operar.</b> Una respuesta que solo dijera
 * «creado» dejaría a quien se registra sin saber por qué no puede hacer nada.
 */
public record SelfRegistrationResponse(UUID id, String username, String status, String pending) {

  public static SelfRegistrationResponse de(UUID id, String username) {
    return new SelfRegistrationResponse(
        id,
        username,
        "FTD_PENDIENTE",
        "Su cuenta quedará operativa cuando se confirme su primer depósito.");
  }
}
