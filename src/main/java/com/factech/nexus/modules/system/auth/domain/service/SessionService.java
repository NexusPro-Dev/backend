package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.modules.system.auth.application.SessionResponse;
import com.factech.nexus.modules.system.auth.domain.models.OpaqueToken;
import com.factech.nexus.modules.system.auth.domain.models.RefreshToken;
import com.factech.nexus.modules.system.auth.domain.models.RevokedReason;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUser;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUserRepository;
import com.factech.nexus.modules.system.auth.domain.repository.RefreshTokenRepository;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.factech.nexus.shared.security.AccessTokenIssuer;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresco y cierre de sesión (`RF-SP-035`, `RF-SP-036`).
 *
 * <p><b>Cinco situaciones distintas devuelven al cliente la misma respuesta y tienen efectos
 * internos OPUESTOS.</b> Esa es la característica que define el refresco, y es donde se implementa
 * mal: la tentación es tratar «revocado» como un solo caso.
 *
 * <ul>
 *   <li>Token inexistente o expirado → {@code 401} <b>sin revocar nada</b>: un token que no existe
 *       no identifica ninguna sesión, y revocar «lo que sea» ante un valor inventado permitiría a
 *       cualquiera cerrar sesiones ajenas a ciegas.
 *   <li>Revocado <b>por rotación</b> → es la señal de <b>robo</b>: alguien presenta un token que su
 *       titular ya sustituyó, luego existe una copia. Se revoca la <b>familia entera</b> y se emite
 *       la alarma.
 *   <li>Revocado por cierre, retiro de acceso o cambio de contraseña → {@code 401} y nada más: son
 *       revocaciones deliberadas, y volver a presentar ese token es torpeza del cliente.
 *   <li>Familia agotada → se revoca la familia y {@code 401}.
 *   <li>Persona inactiva, bloqueada o eliminada → {@code 401}.
 * </ul>
 */
@Service
public class SessionService {

  private final RefreshTokenRepository sesiones;
  private final AuthUserRepository cuentas;
  private final AccessTokenIssuer tokens;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Duration vidaDelRefresh;
  private final Duration duracionMaximaDeSesion;
  private final Clock reloj;

  @Autowired
  public SessionService(
      RefreshTokenRepository sesiones,
      AuthUserRepository cuentas,
      AccessTokenIssuer tokens,
      AuditWriter auditoria,
      UuidV7Generator ids,
      @Value("${nexus.security.jwt.refresh-token-ttl:P7D}") Duration vidaDelRefresh,
      @Value("${nexus.security.jwt.session-max-duration:P30D}") Duration duracionMaxima) {
    this(
        sesiones,
        cuentas,
        tokens,
        auditoria,
        ids,
        vidaDelRefresh,
        duracionMaxima,
        Clock.systemUTC());
  }

  SessionService(
      RefreshTokenRepository sesiones,
      AuthUserRepository cuentas,
      AccessTokenIssuer tokens,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Duration vidaDelRefresh,
      Duration duracionMaxima,
      Clock reloj) {
    this.sesiones = sesiones;
    this.cuentas = cuentas;
    this.tokens = tokens;
    this.auditoria = auditoria;
    this.ids = ids;
    this.vidaDelRefresh = vidaDelRefresh;
    this.duracionMaximaDeSesion = duracionMaxima;
    this.reloj = reloj;
  }

  /**
   * El rechazo NO deshace las revocaciones que lo acompañan.
   *
   * <p>Tres de las cinco condiciones de rechazo <b>escriben</b> antes de rechazar: la reutilización
   * revoca la familia, la sesión agotada la revoca, y la cuenta deshabilitada revoca todas las de
   * su titular. Sin {@code noRollbackFor}, la excepción que expresa el rechazo revierte esas
   * escrituras y <b>la detección de robo no revoca nada</b>: la alarma quedaba en la auditoría —que
   * escribe en transacción propia y sí sobrevivía— mientras los tokens de la familia seguían vivos.
   * Es el peor desenlace posible, porque el registro afirma que se actuó.
   *
   * <p>Aquí no cabe la alternativa de {@code REQUIRES_NEW}: {@code findByHashForUpdate} mantiene un
   * bloqueo pesimista sobre la fila del token, y esa fila pertenece a la familia que habría que
   * revocar. La segunda transacción esperaría a que la primera soltara el bloqueo, y la primera
   * esperaría a que la segunda terminara — un interbloqueo garantizado, justo en el camino de la
   * detección de robo.
   */
  @Transactional(noRollbackFor = UnauthorizedException.class)
  public SessionResponse refresh(String valor) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    // Con bloqueo de fila: es lo que hace ATÓMICA la detección de reutilización.
    // Dos refrescos simultáneos con el mismo token se serializan, y el segundo
    // encuentra la fila ya revocada por rotación — que es la señal de robo.
    RefreshToken token =
        sesiones
            .findByHashForUpdate(OpaqueToken.resumen(valor))
            .orElseThrow(SessionService::sesionInvalida);

    if (token.estaRevocado()) {
      if (token.getRevokedReason() == RevokedReason.ROTACION) {
        // ROBO. Solo la rotación lo significa: las demás revocaciones son
        // deliberadas y su token ya no debía usarse.
        sesiones.revokeFamily(token.getFamilyId(), RevokedReason.REUTILIZACION, ahora);
        auditoria.recordSecurity(
            new SecurityEvent(
                SecurityEventType.REFRESH_TOKEN_REUSE,
                Severity.ALTA,
                Outcome.FAILURE,
                token.getUserId(),
                Map.of("familyId", token.getFamilyId().toString())));
      }
      throw sesionInvalida();
    }

    if (token.haExpirado(ahora)) {
      // Sin revocar: el token ya no sirve por sí solo, y revocar la familia
      // castigaría una sesión que simplemente caducó.
      throw sesionInvalida();
    }

    if (!token.getFamilyStartedAt().plus(duracionMaximaDeSesion).isAfter(ahora)) {
      // La duración máxima se mide desde el INICIO DE SESIÓN. Es lo único que
      // impide que una sesión rotada con disciplina no caduque nunca.
      sesiones.revokeFamily(token.getFamilyId(), RevokedReason.SESION_AGOTADA, ahora);
      throw sesionInvalida();
    }

    AuthUser cuenta =
        cuentas
            .findById(token.getUserId())
            .filter(AuthUser::puedeEntrar)
            .orElseThrow(
                () -> {
                  sesiones.revokeAllActive(token.getUserId(), RevokedReason.ACCESO_RETIRADO, ahora);
                  return sesionInvalida();
                });

    String siguiente = OpaqueToken.generar();
    RefreshToken rotado =
        sesiones.save(
            RefreshToken.rotar(
                ids.next(),
                token,
                OpaqueToken.resumen(siguiente),
                ahora,
                ahora.plus(vidaDelRefresh)));

    token.revocar(RevokedReason.ROTACION, ahora);
    token.sustituidoPor(rotado.getId());

    // La misma regla que al entrar, y leída del MISMO sitio: lo decide la
    // caducidad y no la marca. Que el refresco la recalcule en cada rotación es
    // lo que hace que restablecer la contraseña de alguien con la sesión ya
    // abierta lo lleve a cambiarla en cuanto renueve, sin esperar a que vuelva
    // a entrar.
    boolean debeCambiarla = cuenta.credencialAjena();

    return SessionResponse.de(
        tokens.emitir(cuenta.id(), cuenta.roleCodes(), debeCambiarla, ahora.toInstant()),
        siguiente,
        tokens.vidaEnSegundos(),
        debeCambiarla);
  }

  /**
   * Cierre de sesión (`RF-SP-036`).
   *
   * <p><b>Todo token sintácticamente válido devuelve {@code 204}</b>, exista o no, esté vigente o
   * revocado. Es la enmienda que el plan aplicó a `spec.md` §10: distinguir «no reconocido» de «ya
   * revocado» reintroducía un oráculo — bastaban dos peticiones para comprobar si una cadena de
   * texto es un refresh token del sistema.
   *
   * <p>No se pierde nada: el resultado que la operación promete —que ese token no sirva— se cumple
   * en los cuatro casos.
   */
  @Transactional
  public void logout(String valor, boolean todasLasSesiones) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    Optional<RefreshToken> token = sesiones.findByHashForUpdate(OpaqueToken.resumen(valor));

    if (token.isEmpty()) {
      // Silencio deliberado: es lo que cierra el oráculo.
      return;
    }

    RefreshToken sesion = token.get();
    if (todasLasSesiones) {
      sesiones.revokeAllActive(sesion.getUserId(), RevokedReason.CIERRE, ahora);
    } else {
      sesion.revocar(RevokedReason.CIERRE, ahora);
    }

    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.LOGOUT,
            Severity.INFORMATIVA,
            Outcome.SUCCESS,
            sesion.getUserId(),
            Map.of("allSessions", todasLasSesiones)));
  }

  /**
   * Un solo cuerpo para las cinco condiciones.
   *
   * <p>El cliente no debe poder deducir de la respuesta si el token fue robado, si expiró o si la
   * cuenta fue desactivada. Que cinco situaciones tan distintas compartan respuesta y tengan
   * efectos internos opuestos es precisamente el punto.
   */
  private static UnauthorizedException sesionInvalida() {
    return new UnauthorizedException("VAL-002", "La sesión no es válida.");
  }
}
