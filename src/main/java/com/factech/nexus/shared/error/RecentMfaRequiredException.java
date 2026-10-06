package com.factech.nexus.shared.error;

import java.util.List;
import java.util.Map;

/**
 * La operación es sensible y la prueba del segundo factor no es reciente (`RN-SP-063`).
 *
 * <p>Responde {@code 403} con {@link ProblemKind#REVERIFICACION_REQUERIDA} y la ruta donde
 * reverificar. <b>No se audita</b>: no es un intento de saltarse un permiso, igual que la retención
 * por contraseña provisional.
 */
public class RecentMfaRequiredException extends DomainException {

  private static final long serialVersionUID = 1L;

  public static final String RUTA = "/api/v1/auth/mfa/verification";

  public RecentMfaRequiredException(String mensaje) {
    super("AUTH-003", mensaje, List.of(), Map.of("verificationPath", RUTA));
  }
}
