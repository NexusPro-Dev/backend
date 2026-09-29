package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.DeleteAfftrackRateRequest;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;

/**
 * El motivo de un retiro de escalón afftrack, de rol o de persona (Art. V.13): obligatorio, no en
 * blanco y de 500 caracteres como mucho — la longitud de {@code audit_deletion_log.reason}.
 */
final class AfftrackReasons {

  private static final int MAX_MOTIVO = 500;

  private AfftrackReasons() {}

  /**
   * @param codigo el `VAL` de la operación: cada tripleta numera el suyo
   * @return el motivo, sin espacios alrededor
   */
  static String motivo(DeleteAfftrackRateRequest peticion, String codigo) {
    String motivo = peticion == null || peticion.reason() == null ? null : peticion.reason().trim();
    if (motivo == null || motivo.isEmpty()) {
      throw invalido(codigo, "El motivo del retiro es obligatorio.");
    }
    if (motivo.length() > MAX_MOTIVO) {
      throw invalido(codigo, "El motivo no puede exceder %d caracteres.".formatted(MAX_MOTIVO));
    }
    return motivo;
  }

  private static ValidationException invalido(String codigo, String mensaje) {
    return new ValidationException(
        codigo, mensaje, List.of(new FieldError("reason", codigo, mensaje)));
  }
}
