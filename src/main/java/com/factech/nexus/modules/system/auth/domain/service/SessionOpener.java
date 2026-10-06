package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.modules.system.auth.application.SessionResponse;
import com.factech.nexus.modules.system.auth.domain.models.OpaqueToken;
import com.factech.nexus.modules.system.auth.domain.models.RefreshToken;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUser;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUserRepository;
import com.factech.nexus.modules.system.auth.domain.repository.RefreshTokenRepository;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.observability.RequestContext;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.factech.nexus.shared.security.AccessTokenIssuer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Abre una sesión: lo que `RF-SP-034` hacía al final y que desde `RF-SP-072` hacen dos caminos —la
 * contraseña sola, y la contraseña con el segundo paso—.
 *
 * <p>Registra el acceso —contador a cero, último inicio—, persiste el resumen del refresh token,
 * audita {@code LOGIN_SUCCESS} y emite el token de acceso con sus tres marcas: {@code mcp} (cambio
 * de contraseña pendiente), {@code mer} (activación obligatoria pendiente, `RN-SP-062`) y {@code
 * mfa} (cuándo se verificó el segundo factor, si se verificó).
 */
@Component
public class SessionOpener {

  /** Solo literales de IP: {@code InetAddress.getByName} resolvería un nombre por DNS. */
  private static final Pattern LITERAL_IP = Pattern.compile("[0-9A-Fa-f.:%]+");

  private final AuthUserRepository cuentas;
  private final RefreshTokenRepository sesiones;
  private final AccessTokenIssuer tokens;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Duration vidaDelRefresh;

  public SessionOpener(
      AuthUserRepository cuentas,
      RefreshTokenRepository sesiones,
      AccessTokenIssuer tokens,
      AuditWriter auditoria,
      UuidV7Generator ids,
      @Value("${nexus.security.jwt.refresh-token-ttl:P7D}") Duration vidaDelRefresh) {
    this.cuentas = cuentas;
    this.sesiones = sesiones;
    this.tokens = tokens;
    this.auditoria = auditoria;
    this.ids = ids;
    this.vidaDelRefresh = vidaDelRefresh;
  }

  /**
   * @param cuenta releída con su fila bloqueada por quien llama
   * @param factorVerificadoEn el instante del segundo factor, o nulo si la sesión nace sin él
   * @param codigosRestantes cuántos códigos de recuperación quedan, si se entró con uno; o nulo
   */
  public SessionResponse abrir(
      AuthUser cuenta,
      OffsetDateTime factorVerificadoEn,
      Integer codigosRestantes,
      OffsetDateTime ahora) {

    cuentas.registrarEntrada(cuenta.id(), ahora);

    String refresco = OpaqueToken.generar();
    sesiones.save(
        RefreshToken.abrirSesion(
            ids.next(),
            cuenta.id(),
            OpaqueToken.resumen(refresco),
            ahora,
            ahora.plus(vidaDelRefresh),
            factorVerificadoEn));

    Map<String, Object> detalle = new HashMap<>();
    detalle.put("roles", cuenta.roleCodes());
    detalle.put("mfa", factorVerificadoEn != null);
    if (codigosRestantes != null) {
      detalle.put("recoveryCode", true);
    }
    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.LOGIN_SUCCESS,
            Severity.INFORMATIVA,
            Outcome.SUCCESS,
            cuenta.id(),
            detalle));

    // Lo decide la CADUCIDAD y no la marca: nula, navega; con fecha, la cambia.
    boolean debeCambiarla = cuenta.credencialAjena();
    boolean debeActivarlo = cuenta.activacionObligatoriaPendiente();

    return SessionResponse.de(
        tokens.emitir(
            cuenta.id(),
            cuenta.roleCodes(),
            debeCambiarla,
            debeActivarlo,
            factorVerificadoEn == null ? null : factorVerificadoEn.toInstant(),
            ahora.toInstant()),
        refresco,
        tokens.vidaEnSegundos(),
        debeCambiarla,
        debeActivarlo,
        codigosRestantes);
  }

  /** El origen de la petición, o nulo si no es un literal legible. Nunca resuelve nombres. */
  static InetAddress origen() {
    return RequestContext.current()
        .map(RequestContext::ipAddress)
        .filter(texto -> texto != null && LITERAL_IP.matcher(texto).matches())
        .map(
            texto -> {
              try {
                return InetAddress.getByName(texto);
              } catch (UnknownHostException ilegible) {
                return null;
              }
            })
        .orElse(null);
  }
}
