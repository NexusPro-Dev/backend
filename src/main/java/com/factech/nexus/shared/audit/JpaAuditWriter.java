package com.factech.nexus.shared.audit;

import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditEvents.ErrorEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.observability.RequestContext;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import jakarta.persistence.EntityManager;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Adaptador JPA de {@link AuditWriter} (`RF-SP-001` · `T-07`, `T-08`).
 *
 * <p><b>El núcleo común lo arma este componente, no quien emite el evento.</b> Es lo que impide que
 * un caso de uso invente un actor, se olvide de la IP o escriba una correlación distinta de la de
 * su petición. Quien audita declara <i>qué</i> pasó; <i>quién, cuándo y desde dónde</i> se
 * resuelven aquí, en un solo sitio.
 */
@Component
public class JpaAuditWriter implements AuditWriter {

  private static final Logger LOG = LoggerFactory.getLogger(JpaAuditWriter.class);

  /**
   * Una dirección literal, IPv4 o IPv6, no contiene otra cosa que dígitos hexadecimales, puntos,
   * dos puntos y el separador de zona.
   *
   * <p>Se comprueba <b>antes</b> de construir la {@link InetAddress} porque {@code
   * InetAddress.getByName} resuelve por DNS todo lo que no sea un literal. El valor puede venir de
   * {@code X-Forwarded-For}, que lo escribe un cliente: sin este filtro, una cabecera con un nombre
   * de dominio convertiría cada petición auditada en una consulta DNS saliente elegida por quien la
   * envía.
   */
  private static final java.util.regex.Pattern LITERAL_IP =
      java.util.regex.Pattern.compile("[0-9A-Fa-f.:%]+");

  private final EntityManager em;
  private final UuidV7Generator ids;
  private final AuditActorProvider actores;
  private final TransactionTemplate enTransaccionPropia;
  private final Clock reloj;

  /**
   * Constructor de producción.
   *
   * <p>La anotación es obligatoria y no decorativa: Spring solo infiere el constructor cuando la
   * clase declara exactamente uno, y aquí hay dos. Sin ella busca el constructor sin argumentos, no
   * lo encuentra y el contexto no arranca.
   */
  @Autowired
  public JpaAuditWriter(
      EntityManager em,
      UuidV7Generator ids,
      AuditActorProvider actores,
      PlatformTransactionManager transacciones) {
    this(em, ids, actores, transacciones, Clock.systemUTC());
  }

  JpaAuditWriter(
      EntityManager em,
      UuidV7Generator ids,
      AuditActorProvider actores,
      PlatformTransactionManager transacciones,
      Clock reloj) {
    this.em = em;
    this.ids = ids;
    this.actores = actores;
    this.reloj = reloj;
    this.enTransaccionPropia = new TransactionTemplate(transacciones);
    this.enTransaccionPropia.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * {@code MANDATORY} y no {@code REQUIRED}: el evento de cambio <b>no tiene sentido fuera</b> de
   * la transacción que confirma el alta (Art. V.14). Con {@code REQUIRED}, una llamada desde un
   * punto sin transacción abriría una propia y escribiría el evento de algo que todavía podía
   * revertirse. Así, ese error no llega a ejecutarse: falla al invocarse.
   */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordChange(ChangeEvent evento) {
    em.persist(new AuditChangeLogEntity(nucleo(), evento));
  }

  /** Misma razón que {@link #recordChange}. */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordDeletion(DeletionEvent evento) {
    em.persist(new AuditDeletionLogEntity(nucleo(), evento));
  }

  /**
   * {@code REQUIRES_NEW}: un rechazo se registra mientras la transacción de negocio se revierte.
   * Escrito dentro de ella, el {@code rollback} borraría exactamente el evento que hay que
   * conservar. Este es el caso para el que {@code REQUIRES_NEW} existe.
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordError(ErrorEvent evento) {
    em.persist(new AuditErrorLogEntity(nucleo(), evento));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordSecurity(SecurityEvent evento) {
    em.persist(new AuditSecurityLogEntity(nucleo(), evento));
  }

  @Override
  public void recordSecurityAfterCommit(SecurityEvent evento) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      // Sin transacción que esperar, el evento ya es cierto.
      //
      // Se escribe por plantilla y NO llamando a recordSecurity: una invocación
      // interna no pasa por el proxy de Spring, de modo que su @Transactional
      // no surtiría efecto y el persist moriría con TransactionRequired. Es la
      // misma trampa que en la devolución de llamada de abajo, y aquí llega
      // por el camino de las migraciones y las tareas programadas.
      AuditCore nucleoInmediato = nucleo();
      enTransaccionPropia.executeWithoutResult(
          estado -> em.persist(new AuditSecurityLogEntity(nucleoInmediato, evento)));
      return;
    }

    // El núcleo común se resuelve AHORA y no en la devolución de llamada: para
    // cuando esta se ejecute, el filtro puede haber retirado ya el contexto de
    // la petición y el evento quedaría sin correlación ni IP.
    AuditCore nucleo = nucleo();

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            try {
              // Por plantilla y no llamando a recordSecurity: una invocación
              // interna no pasa por el proxy de Spring y su @Transactional no
              // surtiría efecto, de modo que el evento acabaría en la
              // transacción equivocada — o en ninguna.
              enTransaccionPropia.executeWithoutResult(
                  estado -> em.persist(new AuditSecurityLogEntity(nucleo, evento)));
            } catch (RuntimeException fallo) {
              // No se propaga: la respuesta ya se decidió y el commit ya
              // ocurrió. Se registra con la correlación para que la ausencia
              // del evento sea investigable (`plan.md` §10).
              LOG.error(
                  "No se pudo escribir el evento de seguridad {} tras el commit. correlationId={}",
                  evento.eventType(),
                  nucleo.correlationId(),
                  fallo);
            }
          }
        });
  }

  /**
   * Resuelve actor, instante y origen.
   *
   * <p><b>La correspondencia entre correlación e IP se fuerza aquí, no solo en el esquema.</b> El
   * {@code CHECK} de origen exige que las dos viajen juntas o las dos en nulo, y hay un camino real
   * que las descompareja: una petición siempre tiene correlación, pero su dirección puede quedar
   * sin resolver —el contenedor no la publica, o llega un {@code X-Forwarded-For} que no es un
   * literal—. Si se enviaran tal cual, ese caso sería una violación de integridad dentro de la
   * transacción de auditoría, es decir, un alta correcta que muere al auditarse.
   *
   * <p>Se resuelve degradando a «no vino de la red», que es la lectura honesta: sin IP confiable,
   * la fila no puede afirmar un origen. El agente de usuario sí se conserva — no forma parte de la
   * restricción y sigue diciendo algo.
   */
  private AuditCore nucleo() {
    Optional<RequestContext> peticion = RequestContext.current();
    UUID correlacion = peticion.map(RequestContext::correlationId).orElse(null);
    InetAddress direccion =
        peticion.map(RequestContext::ipAddress).map(JpaAuditWriter::aDireccion).orElse(null);

    if (correlacion == null || direccion == null) {
      correlacion = null;
      direccion = null;
    }

    return new AuditCore(
        ids.next(),
        OffsetDateTime.now(reloj),
        actores.currentActorId().orElse(null),
        correlacion,
        direccion,
        peticion.map(RequestContext::userAgent).orElse(null));
  }

  private static InetAddress aDireccion(String valor) {
    if (valor == null || valor.isBlank() || !LITERAL_IP.matcher(valor).matches()) {
      return null;
    }
    try {
      return InetAddress.getByName(valor);
    } catch (UnknownHostException noEsLiteral) {
      return null;
    }
  }
}
