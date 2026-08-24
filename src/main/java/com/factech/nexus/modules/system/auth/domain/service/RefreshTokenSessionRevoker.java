package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.modules.system.auth.domain.models.RevokedReason;
import com.factech.nexus.modules.system.auth.domain.repository.RefreshTokenRepository;
import com.factech.nexus.shared.security.SessionRevoker;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación del puerto que `RF-SP-031` consume y `RF-SP-034` provee.
 *
 * <p>{@code MANDATORY} y no {@code REQUIRED}: <b>esta operación no tiene sentido por su cuenta</b>.
 * Se invoca siempre desde la transacción del cambio que la motiva, y exigir que ya exista una
 * convierte en un fallo inmediato lo que de otro modo sería un acierto silencioso — una revocación
 * que se confirma sola mientras el retiro que la justificaba se revierte.
 */
@Service
public class RefreshTokenSessionRevoker implements SessionRevoker {

  private final RefreshTokenRepository sesiones;
  private final Clock reloj;

  @org.springframework.beans.factory.annotation.Autowired
  public RefreshTokenSessionRevoker(RefreshTokenRepository sesiones) {
    this(sesiones, Clock.systemUTC());
  }

  RefreshTokenSessionRevoker(RefreshTokenRepository sesiones, Clock reloj) {
    this.sesiones = sesiones;
    this.reloj = reloj;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public int revokeAllForAccessChange(UUID userId) {
    return sesiones.revokeAllActive(
        userId, RevokedReason.ACCESO_RETIRADO, OffsetDateTime.now(reloj));
  }
}
