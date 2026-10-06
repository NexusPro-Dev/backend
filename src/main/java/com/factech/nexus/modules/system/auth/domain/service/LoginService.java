package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.modules.system.auth.application.LoginResponse;
import com.factech.nexus.modules.system.auth.application.SessionResponse;
import com.factech.nexus.modules.system.auth.domain.models.MfaChallenge;
import com.factech.nexus.modules.system.auth.domain.models.OpaqueToken;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUser;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUserRepository;
import com.factech.nexus.modules.system.auth.domain.repository.MfaChallengeRepository;
import com.factech.nexus.modules.system.auth.domain.service.FailedAttemptLedger.Fallos;
import com.factech.nexus.modules.system.auth.infrastructure.MfaSettings;
import com.factech.nexus.shared.error.BlockedAccountException;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.factech.nexus.shared.security.PasswordHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicio de sesión (`RF-SP-034`; enmendado por `RF-SP-072` el 06-10-2026).
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
 *   <li>Releer la cuenta con su fila bloqueada, por la carrera de `RF-SP-029` · `T-12`.
 * </ol>
 *
 * <p>El paso 3 es lo que hace verificable que no se pueda enumerar cuentas: sin él, la respuesta
 * ante un usuario inexistente vuelve en milisegundos y la de una contraseña incorrecta en las
 * decenas que cuesta Argon2id. El paso 4 va después por lo mismo.
 *
 * <p><b>Lo que cambia con el segundo factor es solo lo que se entrega al final</b> (`RF-SP-072`):
 * con un factor activo, tras el paso 5 se emite un <b>desafío</b> en lugar de la sesión, y nada se
 * registra todavía —ni el acceso, ni el contador a cero, ni el evento de éxito—, porque el intento
 * no ha terminado. Que el desafío exista no permite enumerar: solo aparece tras una contraseña
 * correcta. La contabilidad del fallo vive en {@link CredentialFailures} desde ese día, compartida
 * con el segundo paso (`RN-SP-059`).
 */
@Service
public class LoginService {

  /**
   * Resumen de descarte contra el que se compara cuando la cuenta no existe. Su valor no importa;
   * su <b>coste</b> sí: tiene que ser el de un resumen real.
   */
  private final String resumenDeDescarte;

  private final AuthUserRepository cuentas;
  private final FailedAttemptLedger sinCuenta;
  private final CredentialFailures fallos;
  private final PasswordHasher hasher;
  private final SessionOpener aperturas;
  private final MfaChallengeRepository desafios;
  private final MfaSettings ajustes;
  private final UuidV7Generator ids;
  private final Clock reloj;

  @Autowired
  public LoginService(
      AuthUserRepository cuentas,
      FailedAttemptLedger sinCuenta,
      CredentialFailures fallos,
      PasswordHasher hasher,
      SessionOpener aperturas,
      MfaChallengeRepository desafios,
      MfaSettings ajustes,
      UuidV7Generator ids) {
    this(cuentas, sinCuenta, fallos, hasher, aperturas, desafios, ajustes, ids, Clock.systemUTC());
  }

  LoginService(
      AuthUserRepository cuentas,
      FailedAttemptLedger sinCuenta,
      CredentialFailures fallos,
      PasswordHasher hasher,
      SessionOpener aperturas,
      MfaChallengeRepository desafios,
      MfaSettings ajustes,
      UuidV7Generator ids,
      Clock reloj) {
    this.cuentas = cuentas;
    this.sinCuenta = sinCuenta;
    this.fallos = fallos;
    this.hasher = hasher;
    this.aperturas = aperturas;
    this.desafios = desafios;
    this.ajustes = ajustes;
    this.ids = ids;
    this.reloj = reloj;
    this.resumenDeDescarte = hasher.hash("contrasena-de-descarte-que-nadie-usa");
  }

  /**
   * El rechazo NO deshace su propia contabilidad: {@code noRollbackFor} es lo que hace que el
   * contador de intentos se confirme aunque el rechazo viaje como excepción (`RF-SP-034`).
   */
  @Transactional(noRollbackFor = {UnauthorizedException.class, BlockedAccountException.class})
  public LoginResponse login(String identificador, String contrasena) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    Optional<AuthUser> encontrada = cuentas.findByIdentifier(identificador);

    // El contador del identificador SIN cuenta solo se consulta cuando no hay
    // cuenta: cuando la hay, la verdad está en su fila.
    Fallos anonimos =
        encontrada.isPresent() ? Fallos.NINGUNO : sinCuenta.consultar(identificador, ahora);

    // Paso 2: el bloqueo se comprueba ANTES que la contraseña y responde distinto.
    boolean aMano = encontrada.map(AuthUser::bloqueadaAMano).orElse(false);
    OffsetDateTime desbloqueoEn = desbloqueoEn(encontrada, anonimos, ahora);

    if (aMano || desbloqueoEn != null) {
      fallos.auditarFallo(encontrada.orElse(null), identificador, "cuenta bloqueada", true);
      throw bloqueada(aMano, desbloqueoEn, ahora);
    }

    // Paso 3: SIEMPRE se calcula un resumen, exista la cuenta o no.
    String contra = encontrada.map(AuthUser::passwordHash).orElse(resumenDeDescarte);
    boolean coincide = hasher.matches(contrasena, contra);

    if (encontrada.isEmpty() || !coincide) {
      throw fallos.rechazar(encontrada, anonimos, identificador, "credenciales inválidas", ahora);
    }

    AuthUser cuenta = encontrada.get();

    // Paso 4: el estado DESPUÉS del resumen, con el mismo rechazo.
    if (!cuenta.puedeEntrar()) {
      throw fallos.rechazar(encontrada, anonimos, identificador, "cuenta no habilitada", ahora);
    }

    // Paso 5: releer CON LA FILA BLOQUEADA, y solo aquí (`RF-SP-029` · `T-12`).
    AuthUser confirmada =
        cuentas
            .findByIdForUpdate(cuenta.id())
            .filter(AuthUser::puedeEntrar)
            .orElseThrow(
                () ->
                    fallos.rechazar(
                        encontrada, anonimos, identificador, "cuenta no habilitada", ahora));

    // `RF-SP-072`: con el factor activo, la contraseña no abre sesión.
    if (confirmada.tieneSegundoFactor()) {
      return emitirDesafio(confirmada, ahora);
    }

    SessionResponse sesion = aperturas.abrir(confirmada, null, null, ahora);
    return LoginResponse.sesion(sesion);
  }

  /**
   * El desafío: un valor opaco del que solo se guarda el resumen, cinco minutos y cinco intentos.
   * <b>No</b> se registra el acceso ni se pone a cero el contador: el intento no ha terminado.
   */
  private LoginResponse emitirDesafio(AuthUser cuenta, OffsetDateTime ahora) {
    String valor = OpaqueToken.generar();
    Duration vida = ajustes.challengeTtl();
    desafios.guardar(
        MfaChallenge.emitir(
            ids.next(),
            cuenta.id(),
            OpaqueToken.resumen(valor),
            ahora.plus(vida),
            SessionOpener.origen(),
            ahora));
    return LoginResponse.desafio(valor, vida.toSeconds());
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
   * El mensaje distingue el bloqueo manual del automático (`CA-SP-378`). El manual no lleva
   * expiración porque no la tiene; el automático la lleva como dato y no escrita en el texto.
   */
  static BlockedAccountException bloqueada(
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
