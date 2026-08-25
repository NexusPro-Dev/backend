package com.factech.nexus.shared.security;

import java.util.UUID;

/**
 * Cortar el acceso vigente de una persona cuando cambia lo que puede hacer.
 *
 * <p>Vive en {@code shared} y no en el módulo de personas por la razón de siempre: quien lo
 * <b>necesita</b> —`RF-SP-031`, y después `RF-SP-028`, `RF-SP-029` y `RF-SP-037`— no puede depender
 * de quien lo <b>implementa</b>, que es el módulo de sesión. El puerto solo habla de
 * identificadores y de una intención, de modo que no arrastra nada de un módulo al otro.
 *
 * <p><b>Por qué hace falta.</b> El token de acceso <b>no es revocable</b>: solo expira, y vive
 * quince minutos. Retirar un rol tiene efecto inmediato sobre los permisos —se resuelven contra la
 * base en cada petición—, pero el <b>refresh token</b> sobrevive hasta siete días y permite seguir
 * emitiendo tokens de acceso. Sin esta llamada, a quien se le retira el acceso lo conserva por otra
 * puerta.
 *
 * <p><b>Se invoca dentro de la transacción del cambio</b>, antes del commit. Si la revocación
 * falla, el cambio entero se revierte: es preferible que el retiro no ocurra a que ocurra y deje
 * vivo el acceso que decía haber cortado.
 */
public interface SessionRevoker {

  /**
   * Revoca todas las sesiones vigentes de la persona por <b>retiro de acceso</b>.
   *
   * <p>El motivo importa y no es intercambiable: `RF-SP-035` solo trata como <b>robo</b> la
   * reutilización de un token revocado <b>por rotación</b>. Registrar este caso con cualquier otro
   * motivo llenaría el registro de seguridad de incidentes falsos.
   *
   * @return cuántas sesiones se revocaron; cero es un resultado normal
   */
  int revokeAllForAccessChange(UUID userId);
}
