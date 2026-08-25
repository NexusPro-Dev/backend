package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.modules.system.auth.application.ChangePasswordRequest;
import com.factech.nexus.modules.system.auth.domain.models.LockoutPolicy;
import com.factech.nexus.modules.system.auth.domain.models.RevokedReason;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUser;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUserRepository;
import com.factech.nexus.modules.system.auth.domain.repository.RefreshTokenRepository;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BlockedAccountException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.security.CurrentActor;
import com.factech.nexus.shared.security.PasswordHasher;
import com.factech.nexus.shared.security.PasswordPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambiar la propia contraseña (`RF-SP-037`).
 *
 * <p><b>No hay identificador de usuario en ninguna parte</b>, y esa ausencia es la implementación
 * de la regla: el sujeto es siempre quien porta el token. No existe campo por el que dirigir la
 * operación a un tercero, del mismo modo que el cierre de sesión no puede cerrar la de otro.
 *
 * <p>Orden de verificación (`plan.md` §4), y el último paso es el que importa:
 *
 * <ol>
 *   <li>Formato y obligatoriedad — {@code 400}.
 *   <li>La nueva difiere de la vigente presentada — {@code 400}, porque <b>se decide comparando los
 *       dos campos del cuerpo</b>, sin leer nada.
 *   <li>La nueva cumple la política mínima — {@code 400}.
 *   <li>La vigente coincide con la almacenada — {@code 422}.
 * </ol>
 *
 * <p><b>El paso 4 va el último a propósito</b>: es el único que consume el intento del contador de
 * bloqueo. Ponerlo antes haría que una petición con la contraseña nueva mal formada gastara un
 * intento sin necesidad, y <b>bastarían cinco peticiones descuidadas de un cliente propio para
 * bloquear la cuenta de su titular</b>.
 *
 * <p><b>La contraseña vigente incorrecta es {@code 422} y no {@code 401}</b>, y la diferencia se
 * nota en el cliente: un {@code 401} le dice a cualquier cliente bien escrito que su sesión ya no
 * vale, y reaccionaría descartándola y mandando a la persona a iniciar sesión — cuando lo único que
 * ocurrió es que escribió mal su contraseña actual. La sesión sigue siendo válida; lo que no se
 * puede procesar es la petición.
 *
 * <p><b>Aquí sí se dice qué falló</b>, al revés que al iniciar sesión: quien hace esta petición ya
 * está autenticado, y no se le revela nada que no supiera.
 */
@Service
public class ChangeOwnPasswordService {

  private final AuthUserRepository cuentas;
  private final RefreshTokenRepository sesiones;
  private final PasswordPolicy politica;
  private final PasswordHasher hasher;
  private final CurrentActor actor;
  private final AuditWriter auditoria;
  private final LockoutPolicy bloqueo;
  private final Clock reloj;

  @Autowired
  public ChangeOwnPasswordService(
      AuthUserRepository cuentas,
      RefreshTokenRepository sesiones,
      PasswordPolicy politica,
      PasswordHasher hasher,
      CurrentActor actor,
      AuditWriter auditoria,
      @Value("${nexus.security.lockout.max-attempts:5}") int intentosParaBloquear,
      @Value("${nexus.security.lockout.base-delay:PT1M}") Duration bloqueoBase,
      @Value("${nexus.security.lockout.max-delay:PT1H}") Duration bloqueoMaximo) {
    this(
        cuentas,
        sesiones,
        politica,
        hasher,
        actor,
        auditoria,
        new LockoutPolicy(intentosParaBloquear, bloqueoBase, bloqueoMaximo),
        Clock.systemUTC());
  }

  ChangeOwnPasswordService(
      AuthUserRepository cuentas,
      RefreshTokenRepository sesiones,
      PasswordPolicy politica,
      PasswordHasher hasher,
      CurrentActor actor,
      AuditWriter auditoria,
      LockoutPolicy bloqueo,
      Clock reloj) {
    this.cuentas = cuentas;
    this.sesiones = sesiones;
    this.politica = politica;
    this.hasher = hasher;
    this.actor = actor;
    this.auditoria = auditoria;
    this.bloqueo = bloqueo;
    this.reloj = reloj;
  }

  /**
   * <b>{@code noRollbackFor}</b>, por lo mismo que el inicio de sesión: el incremento del contador
   * de intentos ocurre dentro de esta transacción y el rechazo se expresa lanzando una excepción,
   * de modo que sin él la excepción revertiría el incremento y el bloqueo <b>no llegaría nunca</b>.
   */
  @Transactional(
      noRollbackFor = {
        UnprocessableEntityException.class,
        BlockedAccountException.class,
        ValidationException.class
      })
  public void change(ChangePasswordRequest peticion) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));

    AuthUser cuenta =
        cuentas
            .findById(quien)
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "La sesión ya no es válida."));

    // 1. Formato.
    exigir(peticion.currentPassword(), "currentPassword", "VAL-001");
    exigir(peticion.newPassword(), "newPassword", "VAL-002");

    // 2. La nueva difiere de la vigente PRESENTADA. Se compara contra el cuerpo
    //    y no contra el resumen almacenado: así el rechazo no necesita leer nada
    //    y no consume intento.
    if (peticion.newPassword().equals(peticion.currentPassword())) {
      String mensaje = "La contraseña nueva debe ser distinta de la actual.";
      throw new ValidationException(
          "VAL-005", mensaje, List.of(new FieldError("newPassword", "VAL-005", mensaje)));
    }

    // 3. La política. Sin nombre de usuario ni correo aquí: la comprobación de
    //    que no los contenga la hace `PasswordPolicy` con lo que se le pase, y
    //    esta proyección no los trae.
    politica.verificar(peticion.newPassword(), null, null);

    // 4. Y solo ahora, la vigente. Si la cuenta ya estaba bloqueada, se rechaza
    //    antes de gastar el resumen, igual que al iniciar sesión.
    if (cuenta.bloqueadaAMano() || cuenta.bloqueadaPorIntentos(ahora)) {
      throw new BlockedAccountException("La cuenta está bloqueada.");
    }

    if (!hasher.matches(peticion.currentPassword(), cuenta.passwordHash())) {
      anotarFallo(cuenta, ahora);
      auditarFallo(quien);

      if (bloqueo.bloqueoTras(cuenta.failedAttempts() + 1, ahora).isPresent()) {
        throw new BlockedAccountException(
            "La cuenta está bloqueada temporalmente por intentos fallidos.");
      }
      String mensaje = "La contraseña actual no es correcta.";
      throw new UnprocessableEntityException(
          "VAL-003", mensaje, List.of(new FieldError("currentPassword", "VAL-003", mensaje)));
    }

    cuentas.cambiarContrasena(quien, hasher.hash(peticion.newPassword()), ahora);

    // TODAS las sesiones, incluida la que ejecutó el cambio. Es deliberado: si
    // alguien cambia su contraseña porque sospecha que se la robaron, dejar viva
    // la sesión desde la que lo hizo tendría sentido, pero dejar viva cualquier
    // otra no — y no hay forma de distinguirlas sin conocer el token presentado,
    // que esta operación no recibe.
    sesiones.revokeAllActive(quien, RevokedReason.CAMBIO_CONTRASENA, ahora);

    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.PASSWORD_CHANGED,
            Severity.ALTA,
            Outcome.SUCCESS,
            quien,
            Map.of("self", true)));
  }

  private void anotarFallo(AuthUser cuenta, OffsetDateTime ahora) {
    int intentos = cuenta.failedAttempts() + 1;
    cuentas.registrarFallo(
        cuenta.id(), intentos, bloqueo.bloqueoTras(intentos, ahora).orElse(null));
  }

  /**
   * El fallo se registra como el <b>mismo</b> evento con {@code outcome = 'FAILURE'}.
   *
   * <p>Un literal aparte habría obligado a alterar el dominio cerrado de {@code
   * ck_audit_security_log_event_type} para separar lo que una columna ya separa.
   */
  private void auditarFallo(UUID quien) {
    auditoria.recordSecurity(
        new SecurityEvent(
            SecurityEventType.PASSWORD_CHANGED,
            Severity.ALTA,
            Outcome.FAILURE,
            quien,
            Map.of("reason", "contraseña actual incorrecta")));
  }

  private static void exigir(String valor, String campo, String codigo) {
    if (valor == null || valor.isEmpty()) {
      String mensaje = "El campo '" + campo + "' es obligatorio.";
      throw new ValidationException(
          codigo, mensaje, List.of(new FieldError(campo, codigo, mensaje)));
    }
  }
}
