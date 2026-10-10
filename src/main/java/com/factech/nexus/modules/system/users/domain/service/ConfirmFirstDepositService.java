package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.application.FirstDepositActivation;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.models.UserStatus;
import com.factech.nexus.modules.system.users.domain.repository.ClientSellerRepository;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * El primer depósito confirmado <b>por el broker</b> saca a la persona de {@code FTD_PENDIENTE}
 * (`RN-SP-073`, `RN-SP-057`, 10-10-2026).
 *
 * <p>Es la misma transición que `RF-SP-028` hace a mano —{@code FTD_PENDIENTE} a {@code ACTIVO}, y
 * en la misma transacción la entrega de lo comprado al registrarse—, sin actor: la dispara el aviso
 * del broker, o la asociación de una cuenta que ya tenía el depósito. <b>Solo desde {@code
 * FTD_PENDIENTE}</b>: a quien está activo, inactivo o bloqueado un depósito no le cambia nada, y
 * levantar un bloqueo porque alguien depositó sería saltarse a quien lo puso.
 *
 * <p>{@code MANDATORY}: la llama quien mueve la cuenta del broker, y si la entrega falla no se
 * mueve nada.
 */
@Service
public class ConfirmFirstDepositService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "users";
  private static final String MOTIVO = "FIRST_DEPOSIT";

  private final UserRepository usuarios;
  private final ClientSellerRepository vinculos;
  private final FirstDepositActivation activacion;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public ConfirmFirstDepositService(
      UserRepository usuarios,
      ClientSellerRepository vinculos,
      FirstDepositActivation activacion,
      AuditWriter auditoria) {
    this(usuarios, vinculos, activacion, auditoria, Clock.systemUTC());
  }

  ConfirmFirstDepositService(
      UserRepository usuarios,
      ClientSellerRepository vinculos,
      FirstDepositActivation activacion,
      AuditWriter auditoria,
      Clock reloj) {
    this.usuarios = usuarios;
    this.vinculos = vinculos;
    this.activacion = activacion;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  /**
   * Activa a {@code userId} si está en {@code FTD_PENDIENTE}. Devuelve si lo activó.
   *
   * <p>Si la persona no tiene todavía su venta del alta —se está registrando y la venta se anota
   * después—, <b>no la activa</b>: quien la registra vuelve a llamar con la venta ya escrita.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean confirm(UUID userId) {
    User usuario = usuarios.findNotDeletedByIdForUpdate(userId).orElse(null);
    if (usuario == null || usuario.getStatus() != UserStatus.FTD_PENDIENTE) {
      return false;
    }
    UUID venta = vinculos.findRegistrationMovementOf(userId).orElse(null);
    if (venta == null) {
      return false;
    }

    usuarios.applyStatus(userId, UserStatus.ACTIVO.name(), true, OffsetDateTime.now(reloj));
    activacion.activate(userId, venta);

    Map<String, Object> cambios = new HashMap<>();
    cambios.put(
        "status",
        Map.of("before", UserStatus.FTD_PENDIENTE.name(), "after", UserStatus.ACTIVO.name()));
    cambios.put("reason", MOTIVO);
    auditoria.recordChange(new ChangeEvent(MODULO, ENTIDAD, userId, ChangeAction.UPDATE, cambios));

    Map<String, Object> detalle = new HashMap<>();
    detalle.put("from", UserStatus.FTD_PENDIENTE.name());
    detalle.put("to", UserStatus.ACTIVO.name());
    detalle.put("reason", MOTIVO);
    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.USER_STATUS_CHANGED,
            Severity.ALTA,
            Outcome.SUCCESS,
            userId,
            detalle));
    return true;
  }
}
