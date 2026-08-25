package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.modules.system.auth.application.SessionResponse;
import com.factech.nexus.modules.system.auth.domain.models.LockoutPolicy;
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
import com.factech.nexus.shared.error.BlockedAccountException;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.factech.nexus.shared.security.AccessTokenIssuer;
import com.factech.nexus.shared.security.PasswordHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicio de sesión (`RF-SP-034`).
 *
 * <p><b>El orden de verificación está diseñado contra la enumeración de cuentas</b>, y cada paso
 * está donde está por un motivo que no es obvio:
 *
 * <ol>
 *   <li>Localizar la cuenta por nombre de usuario <b>o</b> correo.
 *   <li>Si el bloqueo sigue vigente → {@code 423}, <b>sin comprobar la contraseña</b>.
 *   <li>Verificar la contraseña <b>siempre</b>, aunque la cuenta no exista, contra un resumen de
 *       descarte de coste equivalente.
 *   <li>Comprobar que la cuenta esté activa y no eliminada — <b>después</b> de la contraseña.
 * </ol>
 *
 * <p>El paso 3 es lo que hace verificable que no se pueda enumerar cuentas: sin él, la respuesta
 * ante un usuario inexistente vuelve en milisegundos y la de una contraseña incorrecta en las
 * decenas que cuesta Argon2id, y el atacante deduce cuáles existen <b>cronometrando</b>. El paso 4
 * va después por lo mismo: cortar en el estado ahorraría el resumen y volvería a abrir ese canal.
 *
 * <p>El paso 2 es la excepción consciente al mensaje genérico: quien provocó un bloqueo por fuerza
 * bruta ya sabe que la cuenta existe —fue él quien la bloqueó—, de modo que callarlo solo perjudica
 * a su titular legítimo. Y la contraseña no se comprueba antes de rechazar, para no filtrar por
 * tiempo de respuesta lo que el mensaje sí dice.
 */
@Service
public class LoginService {

  /**
   * Resumen de descarte contra el que se compara cuando la cuenta no existe.
   *
   * <p>Su valor no importa —ninguna contraseña coincidirá— pero su <b>coste</b> sí: tiene que ser
   * el mismo que el de un resumen real, o la defensa contra la enumeración por temporización no
   * funciona. Se calcula una vez al arrancar con los parámetros vigentes.
   */
  private final String resumenDeDescarte;

  private final AuthUserRepository cuentas;
  private final RefreshTokenRepository sesiones;
  private final PasswordHasher hasher;
  private final AccessTokenIssuer tokens;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  private final int intentosParaBloquear;
  private final Duration bloqueoBase;
  private final Duration bloqueoMaximo;
  private final Duration vidaDelRefresh;

  @Autowired
  public LoginService(
      AuthUserRepository cuentas,
      RefreshTokenRepository sesiones,
      PasswordHasher hasher,
      AccessTokenIssuer tokens,
      AuditWriter auditoria,
      UuidV7Generator ids,
      @Value("${nexus.security.lockout.max-attempts:5}") int intentosParaBloquear,
      @Value("${nexus.security.lockout.base-delay:PT1M}") Duration bloqueoBase,
      @Value("${nexus.security.lockout.max-delay:PT1H}") Duration bloqueoMaximo,
      @Value("${nexus.security.jwt.refresh-token-ttl:P7D}") Duration vidaDelRefresh) {
    this(
        cuentas,
        sesiones,
        hasher,
        tokens,
        auditoria,
        ids,
        intentosParaBloquear,
        bloqueoBase,
        bloqueoMaximo,
        vidaDelRefresh,
        Clock.systemUTC());
  }

  LoginService(
      AuthUserRepository cuentas,
      RefreshTokenRepository sesiones,
      PasswordHasher hasher,
      AccessTokenIssuer tokens,
      AuditWriter auditoria,
      UuidV7Generator ids,
      int intentosParaBloquear,
      Duration bloqueoBase,
      Duration bloqueoMaximo,
      Duration vidaDelRefresh,
      Clock reloj) {
    this.cuentas = cuentas;
    this.sesiones = sesiones;
    this.hasher = hasher;
    this.tokens = tokens;
    this.auditoria = auditoria;
    this.ids = ids;
    this.intentosParaBloquear = intentosParaBloquear;
    this.bloqueoBase = bloqueoBase;
    this.bloqueoMaximo = bloqueoMaximo;
    this.vidaDelRefresh = vidaDelRefresh;
    this.reloj = reloj;
    this.resumenDeDescarte = hasher.hash("contrasena-de-descarte-que-nadie-usa");
  }

  /**
   * El rechazo NO deshace su propia contabilidad.
   *
   * <p>{@code noRollbackFor} no es un adorno: sin él, esta operación <b>no bloquea nunca</b>. El
   * contador de intentos se incrementa dentro de la transacción y el rechazo se expresa lanzando
   * una excepción, de modo que la propia excepción revierte el incremento y {@code failed_attempts}
   * se queda en cero para siempre. El bloqueo existía, tenía prueba y no servía para nada; lo
   * destapó la prueba de integración que agota los cinco intentos y vuelve a entrar.
   *
   * <p>El razonamiento que lo corrige es que <b>unas credenciales inválidas no son un fallo de la
   * operación, sino su resultado</b>: la transacción hizo exactamente lo que debía —anotar el
   * intento— y tiene que confirmarse. La excepción viaja después, y solo para elegir el código
   * HTTP.
   *
   * <p>La alternativa era escribir el contador en una transacción propia ({@code REQUIRES_NEW}),
   * como hace la auditoría. Se descarta: aquí no hay ninguna fila bloqueada de por medio, y abrir
   * una segunda transacción para una escritura que la primera puede confirmar es coste sin
   * contrapartida. En {@code SessionService} esa alternativa además <b>se traba</b>.
   */
  @Transactional(noRollbackFor = {UnauthorizedException.class, BlockedAccountException.class})
  public SessionResponse login(String identificador, String contrasena) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    Optional<AuthUser> encontrada = cuentas.findByIdentifier(identificador);

    // Paso 2: el bloqueo se comprueba ANTES que la contraseña y responde
    // distinto. Es la excepción consciente al mensaje genérico.
    if (encontrada.isPresent() && bloqueada(encontrada.get(), ahora)) {
      auditarFallo(encontrada.get(), identificador, "cuenta bloqueada", ahora);
      throw new BlockedAccountException(mensajeDeBloqueo(encontrada.get()));
    }

    // Paso 3: SIEMPRE se calcula un resumen, exista la cuenta o no.
    String contra = encontrada.map(AuthUser::passwordHash).orElse(resumenDeDescarte);
    boolean coincide = hasher.matches(contrasena, contra);

    if (encontrada.isEmpty() || !coincide) {
      encontrada.ifPresent(cuenta -> anotarFallo(cuenta, ahora));
      auditarFallo(encontrada.orElse(null), identificador, "credenciales inválidas", ahora);
      throw credencialesInvalidas();
    }

    AuthUser cuenta = encontrada.get();

    // Paso 4: el estado se comprueba DESPUÉS del resumen, y su rechazo es
    // indistinguible del de una contraseña incorrecta.
    if (!cuenta.puedeEntrar()) {
      auditarFallo(cuenta, identificador, "cuenta no habilitada", ahora);
      throw credencialesInvalidas();
    }

    // La credencial que OTRA PERSONA fijó caduca (`RF-SP-038`, `security.md`
    // §3.2). Sin esta comprobación, una cuenta restablecida y nunca usada
    // conserva indefinidamente una contraseña que alguien más conoce, y nadie se
    // entera porque no falla nada.
    //
    // El rechazo es el genérico: decir «su credencial provisional caducó»
    // confirmaría que la cuenta existe y que alguien la restableció.
    if (cuenta.credencialProvisionalCaducada(ahora)) {
      auditarFallo(cuenta, identificador, "credencial provisional caducada", ahora);
      throw credencialesInvalidas();
    }

    cuentas.registrarEntrada(cuenta.id(), ahora);

    String refresco = OpaqueToken.generar();
    sesiones.save(
        RefreshToken.abrirSesion(
            ids.next(),
            cuenta.id(),
            OpaqueToken.resumen(refresco),
            ahora,
            ahora.plus(vidaDelRefresh)));

    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.LOGIN_SUCCESS,
            Severity.INFORMATIVA,
            Outcome.SUCCESS,
            cuenta.id(),
            Map.of("roles", cuenta.roleCodes())));

    return SessionResponse.de(
        tokens.emitir(
            cuenta.id(), cuenta.roleCodes(), cuenta.mustChangePassword(), ahora.toInstant()),
        refresco,
        tokens.vidaEnSegundos(),
        // AUTENTICA Y ADVIERTE: no rechaza, porque la persona necesita una
        // sesión para poder cambiar la contraseña.
        cuenta.mustChangePassword());
  }

  private boolean bloqueada(AuthUser cuenta, OffsetDateTime ahora) {
    return cuenta.bloqueadaAMano() || cuenta.bloqueadaPorIntentos(ahora);
  }

  /**
   * El mensaje distingue el bloqueo manual del automático (`CA-SP-378`).
   *
   * <p>En el manual el argumento es más fuerte todavía: esa cuenta <b>no se desbloquea sola</b>, de
   * modo que decir «espere» sería falso y dejaría a su titular esperando indefinidamente.
   */
  private static String mensajeDeBloqueo(AuthUser cuenta) {
    return cuenta.bloqueadaAMano()
        ? "La cuenta está bloqueada. Contacte con quien administra el sistema."
        : "La cuenta está bloqueada temporalmente por intentos fallidos. Vuelva a intentarlo más tarde.";
  }

  /**
   * Bloqueo con progresión y <b>techo</b>.
   *
   * <p>El techo no es opcional: sin él, alguien puede mantener la cuenta de otra persona bloqueada
   * indefinidamente provocando fallos a propósito, que es una denegación de servicio contra su
   * titular.
   */
  private void anotarFallo(AuthUser cuenta, OffsetDateTime ahora) {
    int intentos = cuenta.failedAttempts() + 1;
    // La progresión y su techo viven en `LockoutPolicy`, compartida con el
    // cambio de la propia contraseña: escrita dos veces, la segunda copia
    // acabaría con otro techo — o sin ninguno.
    OffsetDateTime hasta = politicaDeBloqueo().bloqueoTras(intentos, ahora).orElse(null);
    cuentas.registrarFallo(cuenta.id(), intentos, hasta);
  }

  private LockoutPolicy politicaDeBloqueo() {
    return new LockoutPolicy(intentosParaBloquear, bloqueoBase, bloqueoMaximo);
  }

  /**
   * Todo intento fallido se audita (`security.md` §3.2).
   *
   * <p><b>El actor es nulo</b> cuando no hay identidad probada, que es siempre en este evento: el
   * identificador intentado va en el detalle, y la dirección de red —que el escritor toma del
   * contexto de la petición— es el único identificador disponible para reconocer un ataque por
   * fuerza bruta.
   */
  private void auditarFallo(
      AuthUser cuenta, String identificador, String motivo, OffsetDateTime ahora) {
    Map<String, Object> detalle = new HashMap<>();
    detalle.put("identifier", identificador);
    detalle.put("reason", motivo);

    auditoria.recordSecurity(
        new SecurityEvent(
            cuenta != null && bloqueada(cuenta, ahora)
                ? SecurityEventType.ACCOUNT_LOCKED
                : SecurityEventType.LOGIN_FAILURE,
            cuenta != null && bloqueada(cuenta, ahora) ? Severity.ALTA : Severity.MEDIA,
            Outcome.FAILURE,
            cuenta == null ? null : cuenta.id(),
            detalle));
  }

  /**
   * Un solo mensaje para los cuatro casos de `EX-001`, sin una diferencia observable.
   *
   * <p>No lleva detalle por campo: un {@code errors} que señalara el identificador frente a la
   * contraseña reintroduciría por la puerta de atrás justo lo que el mensaje único evita.
   */
  private static UnauthorizedException credencialesInvalidas() {
    return new UnauthorizedException("VAL-003", "Las credenciales no son válidas.");
  }
}
