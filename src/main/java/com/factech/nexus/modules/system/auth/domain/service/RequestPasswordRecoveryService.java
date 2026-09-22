package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.modules.system.auth.application.PasswordRecoveryRequest;
import com.factech.nexus.modules.system.auth.domain.models.OpaqueToken;
import com.factech.nexus.modules.system.auth.domain.models.PasswordResetPermit;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUser;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUserRepository;
import com.factech.nexus.modules.system.auth.domain.repository.PasswordResetPermitRepository;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.notification.Notification;
import com.factech.nexus.shared.notification.NotificationKind;
import com.factech.nexus.shared.notification.NotificationSender;
import com.factech.nexus.shared.observability.RequestContext;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Solicitar el permiso para recuperar la propia contraseña (`RF-SP-040`).
 *
 * <p><b>La respuesta es idéntica exista o no la identidad</b>, y eso decide el diseño entero de
 * esta clase. Es una operación de <b>escritura pública</b>: cualquiera puede invocarla contra la
 * cuenta de otro, de modo que todo lo que revele —en el cuerpo, en el estado o <b>en el tiempo</b>—
 * es información que se está regalando.
 *
 * <p><b>Igualar solo el mensaje deja la defensa declarada y no real.</b> Emitir el permiso y enviar
 * el correo cuesta cientos de milisegundos más que no hacer nada, y eso se mide desde fuera con un
 * cronómetro. Por eso el envío se dispara <b>después del commit y fuera de la respuesta</b>: no es
 * una optimización, es la mitad de la defensa.
 *
 * <p><b>Y por eso tampoco se rechaza nada.</b> Ni la cuenta bloqueada, ni la inactiva, ni la que
 * tiene un cambio obligatorio pendiente: cualquiera de esos rechazos diría algo. El único error de
 * esta operación es la identidad ausente, que es un fallo de forma y no dice nada de nadie.
 */
@Service
public class RequestPasswordRecoveryService {

  private static final Logger LOG = LoggerFactory.getLogger(RequestPasswordRecoveryService.class);

  /** Lo que se responde siempre, y la razón de ser de esta clase. */
  private static final String ACUSE =
      "Si esa identidad corresponde a una cuenta, le hemos enviado instrucciones para"
          + " restablecer su contraseña.";

  private final AuthUserRepository cuentas;
  private final PasswordResetPermitRepository permisos;
  private final NotificationSender correo;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final TaskExecutor hilos;
  private final Duration vigencia;
  private final Clock reloj;

  @Autowired
  public RequestPasswordRecoveryService(
      AuthUserRepository cuentas,
      PasswordResetPermitRepository permisos,
      NotificationSender correo,
      AuditWriter auditoria,
      UuidV7Generator ids,
      @Qualifier("applicationTaskExecutor") TaskExecutor hilos,
      @Value("${nexus.security.password.recovery-ttl:PT30M}") Duration vigencia) {
    this(cuentas, permisos, correo, auditoria, ids, hilos, vigencia, Clock.systemUTC());
  }

  RequestPasswordRecoveryService(
      AuthUserRepository cuentas,
      PasswordResetPermitRepository permisos,
      NotificationSender correo,
      AuditWriter auditoria,
      UuidV7Generator ids,
      TaskExecutor hilos,
      Duration vigencia,
      Clock reloj) {
    this.cuentas = cuentas;
    this.permisos = permisos;
    this.correo = correo;
    this.auditoria = auditoria;
    this.ids = ids;
    this.hilos = hilos;
    this.vigencia = vigencia;
    this.reloj = reloj;
  }

  /** Siempre termina igual. Lo que cambia por dentro no debe notarse por fuera. */
  @Transactional
  public String solicitar(PasswordRecoveryRequest peticion) {
    String identificador = peticion == null ? null : peticion.identifier();
    if (identificador == null || identificador.isBlank()) {
      String mensaje = "Debe indicar su usuario o su correo.";
      throw new ValidationException(
          "VAL-001", mensaje, List.of(new FieldError("identifier", "VAL-001", mensaje)));
    }

    Optional<AuthUser> cuenta = cuentas.findByIdentifier(identificador);

    if (cuenta.isEmpty() || cuenta.get().deleted()) {
      // La identidad probada NO se registra, y esa omisión es deliberada:
      // registrarla convertiría `audit_security_log` en la lista de usuarios y
      // correos que alguien está sondeando, legible por quien tenga
      // `audit:read-security`. Lo que hace investigable una ráfaga es el ORIGEN
      // —que viaja en el núcleo común del evento— y su VOLUMEN.
      auditar(Outcome.FAILURE, null, "SOLICITUD");
      return ACUSE;
    }

    emitirYEnviar(cuenta.get());
    return ACUSE;
  }

  /**
   * Emite el permiso, sustituye el anterior y encola el envío.
   *
   * <p><b>Sustituir va antes de emitir y en la misma transacción.</b> El índice único parcial del
   * esquema solo admite un permiso vivo por persona: sin esto, la segunda solicitud choca; y sin el
   * índice, dos solicitudes concurrentes dejarían <b>dos vías de entrada abiertas</b>.
   */
  private void emitirYEnviar(AuthUser cuenta) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    String enClaro = OpaqueToken.generar();

    permisos.sustituirVigente(cuenta.id(), ahora);
    permisos.guardar(
        PasswordResetPermit.emitir(
            ids.next(),
            cuenta.id(),
            OpaqueToken.resumen(enClaro),
            ahora.plus(vigencia),
            origen(),
            ahora));

    auditar(Outcome.SUCCESS, cuenta.id(), "SOLICITUD");

    // DESPUÉS del commit, y no antes: un permiso enviado sobre una transacción
    // que se revierte sería una vía de entrada que la base de datos no conoce.
    // El correo se resuelve AQUÍ y no en la devolución de llamada, porque para
    // entonces ya no hay transacción con la que consultarlo.
    String destino = cuentas.correoDe(cuenta.id()).orElse(null);
    if (destino != null) {
      trasElCommit(() -> correo.send(mensaje(destino, enClaro)));
    }
  }

  /**
   * El mensaje. <b>Sin nombre, sin cuenta, sin nada que confirme quién es quien lo recibe.</b>
   *
   * <p>Puede llegarle a alguien que no pidió nada —porque otro tecleó mal su correo, o porque
   * alguien lo hizo a propósito—, y en ese caso el mensaje no debe contarle nada de la cuenta a la
   * que apunta.
   *
   * <p><b>El texto y las variables dicen lo mismo, y las dos versiones tienen que seguir
   * diciéndolo.</b> Si hay plantilla configurada sale la maquetada; si no, sale este texto. Quien
   * cambie una y no la otra deja al sistema mandando dos correos distintos según el entorno, que es
   * la clase de diferencia que solo se descubre en producción.
   */
  private Notification mensaje(String destino, String permiso) {
    String minutos = String.valueOf(vigencia.toMinutes());
    String cuerpo =
        """
        Alguien solicitó restablecer la contraseña de la cuenta asociada a este correo.

        Su código es: %s

        Caduca en %s minutos y solo puede usarse una vez.

        Si no fue usted, no hace falta que haga nada: sin este código no se puede
        cambiar ninguna contraseña.
        """
            .formatted(permiso, minutos);

    return new Notification(
        destino,
        "Restablecer su contraseña",
        cuerpo,
        NotificationKind.PASSWORD_RECOVERY,
        Map.of("CODIGO", permiso, "MINUTOS", minutos));
  }

  private void auditar(Outcome resultado, java.util.UUID objetivo, String etapa) {
    SecurityEvent evento =
        new SecurityEvent(
            SecurityEventType.PASSWORD_RESET,
            Severity.ALTA,
            resultado,
            objetivo,
            Map.of("stage", etapa, "self", true));

    // El de éxito espera al commit; el de fallo no tiene commit que esperar
    // —no se escribió nada— y debe sobrevivir de todos modos: una ráfaga de
    // solicitudes sobre identidades inexistentes es lo que hay que poder ver.
    if (resultado == Outcome.SUCCESS) {
      auditoria.recordSecurityAfterCommit(evento);
    } else {
      auditoria.recordSecurity(evento);
    }
  }

  /**
   * El origen de la solicitud, para que una ráfaga sea investigable por red.
   *
   * <p>Una dirección ilegible <b>no rompe la operación</b>: se guarda en nulo. Perder el origen de
   * un permiso es una molestia para quien investigue; negarle a alguien recuperar su cuenta porque
   * un proxy escribió mal una cabecera es otra cosa.
   */
  private InetAddress origen() {
    return RequestContext.current()
        .map(RequestContext::ipAddress)
        .flatMap(
            texto -> {
              try {
                return Optional.of(InetAddress.getByName(texto));
              } catch (UnknownHostException ilegible) {
                return Optional.empty();
              }
            })
        .orElse(null);
  }

  /**
   * Despacha el envío <b>después del commit y en otro hilo</b>.
   *
   * <p><b>Las dos mitades hacen falta y ninguna cubre a la otra.</b> Esperar al commit evita enviar
   * un permiso que la base de datos acabará no teniendo, si la transacción se revierte. Salir del
   * hilo evita que la respuesta espere al envío — y eso <b>no lo da {@code afterCommit} por sí
   * solo</b>: esa devolución de llamada corre en el hilo de la petición, justo antes de que el
   * controlador devuelva. Con el envío ahí dentro, la respuesta con identidad existente tarda
   * cientos de milisegundos más que la otra, y el endpoint pasa a decir <b>qué cuentas existen</b>
   * sin que su cuerpo cambie una coma. Es la clase de fuga que no se ve leyendo el código y sí con
   * un cronómetro, y por eso `CA-SP-473` la mide.
   *
   * <p><b>Y se traga lo que falle.</b> El puerto declara que no lanza, y aun así se protege aquí:
   * depender de que el adaptador se porte bien es depender de algo que puede cambiar sin que nadie
   * mire este archivo.
   */
  private void trasElCommit(Runnable accion) {
    Runnable protegida =
        () -> {
          try {
            accion.run();
          } catch (RuntimeException fallo) {
            LOG.error("No se pudo despachar el correo de recuperación tras el commit", fallo);
          }
        };

    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      hilos.execute(protegida);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            hilos.execute(protegida);
          }
        });
  }
}
