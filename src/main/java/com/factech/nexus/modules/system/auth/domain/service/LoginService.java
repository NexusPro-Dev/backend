package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.modules.system.auth.application.SessionResponse;
import com.factech.nexus.modules.system.auth.domain.models.LockoutPolicy;
import com.factech.nexus.modules.system.auth.domain.models.OpaqueToken;
import com.factech.nexus.modules.system.auth.domain.models.RefreshToken;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUser;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUserRepository;
import com.factech.nexus.modules.system.auth.domain.repository.RefreshTokenRepository;
import com.factech.nexus.modules.system.auth.domain.service.FailedAttemptLedger.Fallos;
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
 *
 * <p><b>El rechazo dice cuántos intentos quedan, y el bloqueo cuánto falta para levantarse.</b> Las
 * dos cosas son deudas con quien se equivoca de buena fe: sin la primera, la cuenta se bloquea sin
 * previo aviso; sin la segunda, «vuelva a intentarlo más tarde» deja a la persona reintentando a
 * ciegas — y `EX-002` con `CA-SP-378` pedían el momento de expiración desde el principio.
 *
 * <p><b>Y ninguna de las dos puede depender de que la cuenta exista</b>, o serían el verificador de
 * cuentas que todo lo anterior evita. Por eso el identificador <b>sin cuenta</b> tiene su propio
 * contador en {@link FailedAttemptLedger}, con la misma política y el mismo umbral: cuenta, agota
 * intentos y acaba respondiendo {@code 423} exactamente igual que uno real. La alternativa
 * —devolver el dato solo cuando hay cuenta— habría bastado para descubrir qué correos están
 * registrados enviando una contraseña cualquiera y mirando si el campo aparece.
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
  private final FailedAttemptLedger sinCuenta;
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
      FailedAttemptLedger sinCuenta,
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
        sinCuenta,
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
      FailedAttemptLedger sinCuenta,
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
    this.sinCuenta = sinCuenta;
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

    // El contador del identificador SIN cuenta solo se consulta cuando no hay
    // cuenta: cuando la hay, la verdad está en su fila, y leer los dos abriría
    // la puerta a que discreparan.
    Fallos anonimos =
        encontrada.isPresent() ? Fallos.NINGUNO : sinCuenta.consultar(identificador, ahora);

    // Paso 2: el bloqueo se comprueba ANTES que la contraseña y responde
    // distinto. Es la excepción consciente al mensaje genérico.
    boolean aMano = encontrada.map(AuthUser::bloqueadaAMano).orElse(false);
    OffsetDateTime desbloqueoEn = desbloqueoEn(encontrada, anonimos, ahora);

    if (aMano || desbloqueoEn != null) {
      auditarFallo(encontrada.orElse(null), identificador, "cuenta bloqueada", true);
      throw bloqueada(aMano, desbloqueoEn, ahora);
    }

    // Paso 3: SIEMPRE se calcula un resumen, exista la cuenta o no.
    String contra = encontrada.map(AuthUser::passwordHash).orElse(resumenDeDescarte);
    boolean coincide = hasher.matches(contrasena, contra);

    if (encontrada.isEmpty() || !coincide) {
      throw rechazar(encontrada, anonimos, identificador, "credenciales inválidas", ahora);
    }

    AuthUser cuenta = encontrada.get();

    // Paso 4: el estado se comprueba DESPUÉS del resumen, y su rechazo es
    // indistinguible del de una contraseña incorrecta.
    if (!cuenta.puedeEntrar()) {
      throw rechazar(encontrada, anonimos, identificador, "cuenta no habilitada", ahora);
    }

    // Aquí estaba el rechazo por credencial provisional CADUCADA, retirado el
    // 25-08-2026 por decisión del responsable del proyecto: la caducidad ya no
    // corta el acceso, solo marca la sesión (ver `AuthUser.credencialAjena`).
    // Se deja escrito porque su ausencia es una decisión y no un olvido, y
    // porque `RF-SP-038` la había pedido de forma expresa.

    // Paso 5: se vuelve a leer la cuenta CON SU FILA BLOQUEADA, y solo aquí.
    //
    // Lo que esto cierra es la carrera de `RF-SP-029` · `T-12`: si mientras se
    // comprobaba la contraseña un actor eliminó o desactivó la cuenta, el paso 4
    // lo decidió sobre una instantánea donde seguía viva. Aquel barrió las
    // sesiones vigentes y confirmó; la que estamos a punto de insertar nacería
    // DESPUÉS de ese barrido y sobreviviría a la eliminación.
    //
    // Va después de comprobar la contraseña y no antes: tomar el bloqueo al
    // principio le añadiría al rechazo un tiempo que depende de si la cuenta
    // existe, que es la fuga que `EX-001` existe para cerrar.
    AuthUser confirmada =
        cuentas
            .findByIdForUpdate(cuenta.id())
            .filter(AuthUser::puedeEntrar)
            .orElseThrow(
                () -> rechazar(encontrada, anonimos, identificador, "cuenta no habilitada", ahora));

    cuentas.registrarEntrada(confirmada.id(), ahora);

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

    // Lo decide la CADUCIDAD y no la marca: nula, navega; con fecha, la cambia.
    boolean debeCambiarla = confirmada.credencialAjena();

    return SessionResponse.de(
        tokens.emitir(cuenta.id(), cuenta.roleCodes(), debeCambiarla, ahora.toInstant()),
        refresco,
        tokens.vidaEnSegundos(),
        // AUTENTICA Y ADVIERTE: no rechaza, porque la persona necesita una
        // sesión para poder cambiar la contraseña.
        debeCambiarla);
  }

  /** Hasta cuándo dura el bloqueo <b>automático</b> vigente, o {@code null} si no hay ninguno. */
  private static OffsetDateTime desbloqueoEn(
      Optional<AuthUser> encontrada, Fallos anonimos, OffsetDateTime ahora) {
    if (encontrada.isPresent()) {
      AuthUser cuenta = encontrada.get();
      return cuenta.bloqueadaPorIntentos(ahora) ? cuenta.lockedUntil() : null;
    }
    return anonimos.bloqueado(ahora) ? anonimos.bloqueadoHasta() : null;
  }

  /**
   * Anota el fallo, lo audita y construye el rechazo con los intentos que quedan.
   *
   * <p><b>Los tres casos vivos de `EX-001` pasan por aquí</b>, incluido el que antes no consumía
   * intento —cuenta no habilitada con la contraseña correcta—. Tenía que empezar a consumirlo: si
   * no lo hiciera, su respuesta llevaría un número de intentos distinto del de una contraseña
   * incorrecta, y el mensaje único de `CA-SP-292` se rompería por el sitio menos visible. El coste
   * —que una cuenta desactivada acumule bloqueos que no le impiden nada, porque tampoco puede
   * entrar— es el menor de los dos.
   *
   * <p>El cuarto caso, la credencial provisional caducada, <b>dejó de rechazarse</b> el 25-08-2026:
   * ahora autentica y obliga a cambiarla, de modo que ya no llega hasta aquí.
   *
   * <p>El bloqueo con severidad {@code ALTA} se registra <b>en el intento que lo provoca</b>, y no
   * en el siguiente: antes se decidía leyendo la proyección de la cuenta, que en ese momento
   * todavía llevaba el estado anterior, de modo que `CA-SP-296` quedaba anotado un intento tarde.
   */
  private UnauthorizedException rechazar(
      Optional<AuthUser> encontrada,
      Fallos anonimos,
      String identificador,
      String motivo,
      OffsetDateTime ahora) {

    int intentos = encontrada.map(AuthUser::failedAttempts).orElse(anonimos.intentos()) + 1;
    // La progresión y su techo viven en `LockoutPolicy`, compartida con el
    // cambio de la propia contraseña: escrita dos veces, la segunda copia
    // acabaría con otro techo — o sin ninguno.
    OffsetDateTime hasta = politicaDeBloqueo().bloqueoTras(intentos, ahora).orElse(null);

    if (encontrada.isPresent()) {
      cuentas.registrarFallo(encontrada.get().id(), intentos, hasta);
    } else {
      sinCuenta.registrarFallo(identificador, intentos, hasta, ahora);
    }

    auditarFallo(encontrada.orElse(null), identificador, motivo, hasta != null);
    return credencialesInvalidas(Math.max(0, intentosParaBloquear - intentos), hasta, ahora);
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
   *
   * <p>El agotamiento de un identificador <b>sin cuenta</b> se anota como un fallo más y no como
   * {@code ACCOUNT_LOCKED}: no hay cuenta que bloquear, y un evento de severidad {@code ALTA} sin
   * objeto convertiría en alarma lo que solo es ruido. Que aquí se distingan no filtra nada — el
   * registro de auditoría no lo ve quien intenta entrar.
   */
  private void auditarFallo(
      AuthUser cuenta, String identificador, String motivo, boolean bloqueada) {
    Map<String, Object> detalle = new HashMap<>();
    detalle.put("identifier", identificador);
    detalle.put("reason", motivo);

    boolean bloqueoDeCuenta = bloqueada && cuenta != null;

    auditoria.recordSecurity(
        new SecurityEvent(
            bloqueoDeCuenta ? SecurityEventType.ACCOUNT_LOCKED : SecurityEventType.LOGIN_FAILURE,
            bloqueoDeCuenta ? Severity.ALTA : Severity.MEDIA,
            Outcome.FAILURE,
            cuenta == null ? null : cuenta.id(),
            detalle));
  }

  /**
   * Un solo mensaje para los cuatro casos de `EX-001`, sin una diferencia observable.
   *
   * <p>No lleva detalle por campo: un {@code errors} que señalara el identificador frente a la
   * contraseña reintroduciría por la puerta de atrás justo lo que el mensaje único evita.
   *
   * <p>Los intentos restantes viajan como <b>campo</b> además de en el texto. El texto es para
   * quien lea la respuesta; el campo, para el cliente que quiera presentar el aviso a su manera sin
   * analizar una frase que mañana puede reescribirse.
   */
  private static UnauthorizedException credencialesInvalidas(
      int restantes, OffsetDateTime hasta, OffsetDateTime ahora) {

    Map<String, Object> extensiones = new HashMap<>();
    extensiones.put("remainingAttempts", restantes);

    if (hasta == null) {
      String aviso =
          restantes == 1
              ? " Le queda 1 intento antes de que la cuenta se bloquee."
              : " Le quedan " + restantes + " intentos antes de que la cuenta se bloquee.";
      return new UnauthorizedException(
          "VAL-003", "Las credenciales no son válidas." + aviso, extensiones);
    }

    // Sin intentos restantes no hay nada que anunciar para «la próxima vez»: la
    // próxima vez es un `423`. Que el bloqueo ya existe se dice aquí, para que
    // la persona no tenga que descubrirlo reintentando.
    Duration espera = Duration.between(ahora, hasta);
    extensiones.put("unlockAt", hasta);
    extensiones.put("retryAfterSeconds", Math.max(0, espera.toSeconds()));

    return new UnauthorizedException(
        "VAL-003",
        "Las credenciales no son válidas. La cuenta ha quedado bloqueada temporalmente.",
        extensiones);
  }

  /**
   * El mensaje distingue el bloqueo manual del automático (`CA-SP-378`).
   *
   * <p>En el manual el argumento es más fuerte todavía: esa cuenta <b>no se desbloquea sola</b>, de
   * modo que decir «espere» sería falso y dejaría a su titular esperando indefinidamente. Por eso
   * es también el único que se construye <b>sin</b> momento de expiración.
   *
   * <p><b>Y el automático tampoco lleva la duración en el texto</b>, aunque la sepa. «Vuelva a
   * intentarlo en dos minutos» es cierto en el instante en que se serializa y deja de serlo
   * enseguida: la respuesta viaja, el cliente la guarda, la persona lee el aviso un minuto después
   * y el número le miente. La duración viaja como {@code retryAfterSeconds} justamente para que
   * quien la presente pueda <b>descontarla</b> —una cuenta regresiva no envejece— en lugar de
   * repetir un número congelado.
   */
  private static BlockedAccountException bloqueada(
      boolean aMano, OffsetDateTime desbloqueoEn, OffsetDateTime ahora) {

    if (aMano) {
      return new BlockedAccountException(
          "La cuenta está bloqueada. Contacte con quien administra el sistema.");
    }

    Duration espera = Duration.between(ahora, desbloqueoEn);
    return new BlockedAccountException(
        "La cuenta está bloqueada temporalmente por intentos fallidos.",
        desbloqueoEn,
        Math.max(0, espera.toSeconds()));
  }
}
