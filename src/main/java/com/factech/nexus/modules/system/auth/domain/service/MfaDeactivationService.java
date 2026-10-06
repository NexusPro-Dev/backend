package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.modules.system.auth.application.MfaDeactivationRequest;
import com.factech.nexus.modules.system.auth.application.SessionResponse;
import com.factech.nexus.modules.system.auth.domain.models.MfaFactor;
import com.factech.nexus.modules.system.auth.domain.models.OpaqueToken;
import com.factech.nexus.modules.system.auth.domain.models.RefreshToken;
import com.factech.nexus.modules.system.auth.domain.models.RevokedReason;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUser;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUserRepository;
import com.factech.nexus.modules.system.auth.domain.repository.MfaFactorRepository;
import com.factech.nexus.modules.system.auth.domain.repository.RefreshTokenRepository;
import com.factech.nexus.modules.system.auth.domain.service.CredentialFailures.FalloDeCodigo;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BlockedAccountException;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.factech.nexus.shared.security.AccessRevocationPublisher;
import com.factech.nexus.shared.security.AccessTokenIssuer;
import com.factech.nexus.shared.security.CurrentActor;
import com.factech.nexus.shared.security.PasswordHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Desactivar el propio segundo factor (`RF-SP-075`).
 *
 * <p><b>Pide los dos factores a la vez</b>: la verificación reciente —que exige {@code
 * RecentMfaInterceptor}, porque {@code users:disable-own-mfa} está marcado— y la contraseña
 * vigente, que se comprueba aquí. <b>El orden</b>: factor activo → rol que lo exige → contraseña,
 * la última, para que los dos conflictos —que no dependen de ella— no consuman intentos (`plan.md`
 * §1).
 *
 * <p><b>Las demás sesiones se cierran y la actual sigue, con credenciales nuevas</b> (`CA-SP-861`):
 * el token de acceso no dice de qué sesión viene —es la misma limitación que llevó a `RF-SP-037` a
 * cerrarlas todas—, de modo que se cierran todas y la respuesta trae <b>una sesión nueva</b> para
 * quien acaba de probar los dos factores. El efecto es el pedido: este dispositivo sigue dentro y
 * los demás quedan fuera.
 */
@Service
public class MfaDeactivationService {

  private final AuthUserRepository cuentas;
  private final MfaFactorRepository factores;
  private final CredentialFailures fallos;
  private final RefreshTokenRepository sesiones;
  private final AccessRevocationPublisher cortes;
  private final AccessTokenIssuer tokens;
  private final PasswordHasher hasher;
  private final CurrentActor actor;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Duration vidaDelRefresh;
  private final Clock reloj;

  @Autowired
  public MfaDeactivationService(
      AuthUserRepository cuentas,
      MfaFactorRepository factores,
      CredentialFailures fallos,
      RefreshTokenRepository sesiones,
      AccessRevocationPublisher cortes,
      AccessTokenIssuer tokens,
      PasswordHasher hasher,
      CurrentActor actor,
      AuditWriter auditoria,
      UuidV7Generator ids,
      @Value("${nexus.security.jwt.refresh-token-ttl:P7D}") Duration vidaDelRefresh) {
    this(
        cuentas,
        factores,
        fallos,
        sesiones,
        cortes,
        tokens,
        hasher,
        actor,
        auditoria,
        ids,
        vidaDelRefresh,
        Clock.systemUTC());
  }

  MfaDeactivationService(
      AuthUserRepository cuentas,
      MfaFactorRepository factores,
      CredentialFailures fallos,
      RefreshTokenRepository sesiones,
      AccessRevocationPublisher cortes,
      AccessTokenIssuer tokens,
      PasswordHasher hasher,
      CurrentActor actor,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Duration vidaDelRefresh,
      Clock reloj) {
    this.cuentas = cuentas;
    this.factores = factores;
    this.fallos = fallos;
    this.sesiones = sesiones;
    this.cortes = cortes;
    this.tokens = tokens;
    this.hasher = hasher;
    this.actor = actor;
    this.auditoria = auditoria;
    this.ids = ids;
    this.vidaDelRefresh = vidaDelRefresh;
    this.reloj = reloj;
  }

  @Transactional(
      noRollbackFor = {
        UnprocessableEntityException.class,
        BlockedAccountException.class,
        ValidationException.class,
        BusinessRuleException.class
      })
  public SessionResponse desactivar(MfaDeactivationRequest peticion) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));

    String contrasena = peticion == null ? null : peticion.currentPassword();
    if (contrasena == null || contrasena.isEmpty()) {
      String mensaje = "Debe indicar su contraseña actual.";
      throw new ValidationException(
          "VAL-001", mensaje, List.of(new FieldError("currentPassword", "VAL-001", mensaje)));
    }

    // Orden de bloqueo: cuenta → factor, el de `RF-SP-073`.
    AuthUser cuenta =
        cuentas
            .findByIdForUpdate(quien)
            .filter(AuthUser::puedeEntrar)
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "La sesión ya no es válida."));
    if (cuenta.bloqueadaAMano() || cuenta.bloqueadaPorIntentos(ahora)) {
      throw LoginService.bloqueada(cuenta.bloqueadaAMano(), cuenta.lockedUntil(), ahora);
    }

    MfaFactor factor =
        factores
            .activoParaActualizar(quien)
            .orElseThrow(
                () -> new BusinessRuleException("VAL-004", "No tiene activado el segundo factor."));

    if (cuenta.requiereSegundoFactor()) {
      throw new BusinessRuleException(
          "VAL-003", "Su rol exige el segundo factor: no puede desactivarlo.");
    }

    if (!hasher.matches(contrasena, cuenta.passwordHash())) {
      FalloDeCodigo fallo =
          fallos.registrarFallo(
              cuenta,
              SecurityEventType.MFA_DISABLED,
              Severity.ALTA,
              Map.of("reason", "contraseña actual incorrecta"),
              ahora);
      if (fallo.bloqueo()) {
        // Como al reverificar (`RF-SP-073` `EX-005`): con el bloqueo, fuera todas.
        sesiones.revokeAllActive(quien, RevokedReason.ACCESO_RETIRADO, ahora);
        cortes.publicarCorte(quien);
        throw LoginService.bloqueada(false, fallo.bloqueadaHasta(), ahora);
      }
      String mensaje = "La contraseña actual no es correcta.";
      throw new UnprocessableEntityException(
          "VAL-002", mensaje, List.of(new FieldError("currentPassword", "VAL-002", mensaje)));
    }

    factor.retirar(MfaFactor.Retiro.DESACTIVADO, ahora);
    factores
        .pendienteParaActualizar(quien)
        .ifPresent(pendiente -> pendiente.retirar(MfaFactor.Retiro.DESACTIVADO, ahora));
    factores.sincronizar();
    // Los códigos de recuperación no se tocan: cuelgan del factor, y un código de
    // un factor retirado no casa (`requirements/sp.md` §10.23).

    cuentas.registrarFallo(quien, 0, null);
    // Se cierran TODAS las sesiones —sus refresh tokens— y NO se publica el corte del
    // token de acceso: el corte se anota tras el commit y apunta al segundo siguiente,
    // de modo que el token de la sesión nueva que se emite abajo nacería ANTES del
    // corte y quedaría cortado él también. Los demás dispositivos conservan su token
    // de acceso hasta que caduque —quince minutos como mucho— y ya no pueden
    // renovarlo: es la garantía de D-08 para todo token de acceso (`security.md` §5.1).
    sesiones.revokeAllActive(quien, RevokedReason.ACCESO_RETIRADO, ahora);

    Map<String, Object> cambios = new HashMap<>();
    cambios.put("status", Map.of("before", MfaFactor.ACTIVO, "after", MfaFactor.RETIRADO));
    cambios.put("retired_reason", Map.of("after", MfaFactor.Retiro.DESACTIVADO.name()));
    auditoria.recordChange(
        new ChangeEvent("SP", "user_mfa_factors", factor.getId(), ChangeAction.UPDATE, cambios));
    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.MFA_DISABLED, Severity.ALTA, Outcome.SUCCESS, quien, Map.of()));

    // La sesión nueva de este dispositivo. Sin `mfa`: ya no hay factor que probar.
    String refresco = OpaqueToken.generar();
    sesiones.save(
        RefreshToken.abrirSesion(
            ids.next(), quien, OpaqueToken.resumen(refresco), ahora, ahora.plus(vidaDelRefresh)));
    boolean debeCambiarla = cuenta.credencialAjena();
    return SessionResponse.de(
        tokens.emitir(quien, cuenta.roleCodes(), debeCambiarla, false, null, ahora.toInstant()),
        refresco,
        tokens.vidaEnSegundos(),
        debeCambiarla,
        false,
        null);
  }
}
