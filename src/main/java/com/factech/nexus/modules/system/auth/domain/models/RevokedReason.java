package com.factech.nexus.modules.system.auth.domain.models;

/**
 * Por qué se revocó una sesión ({@code ck_refresh_tokens_motivo}).
 *
 * <p><b>El motivo es obligatorio en toda fila revocada</b>, y no por prolijidad: es el dato del que
 * depende que el refresco distinga un <b>robo</b> de un cierre de sesión.
 *
 * <p><b>{@link #ROTACION} es la única cuya reutilización significa robo.</b> Un token revocado por
 * rotación fue sustituido por otro y su titular legítimo ya no lo tiene; que alguien lo presente
 * significa que existe una copia. Las demás son revocaciones <b>deliberadas</b> —alguien cerró
 * sesión, alguien cambió la contraseña— y volver a presentar ese token es torpeza del cliente, no
 * un incidente.
 */
public enum RevokedReason {
  /** Sustituido por otro al refrescar. Su reutilización es la señal de robo. */
  ROTACION,

  /** Cierre de sesión explícito. */
  CIERRE,

  /** El acceso de la persona se retiró: desactivación, bloqueo o eliminación. */
  ACCESO_RETIRADO,

  /** La contraseña cambió, de modo que las sesiones abiertas con la anterior caen. */
  CAMBIO_CONTRASENA,

  /** La familia agotó su duración máxima de sesión. */
  SESION_AGOTADA,

  /** Revocada en cascada al detectarse la reutilización de otra de su familia. */
  REUTILIZACION
}
