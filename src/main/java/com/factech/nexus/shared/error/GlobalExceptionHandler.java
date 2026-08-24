package com.factech.nexus.shared.error;

import com.factech.nexus.shared.audit.AuditEnums.ErrorType;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.ErrorEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.observability.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traducción de excepciones al formato RFC 9457 (`architecture.md` §7.3, `RF-SP-001` · `T-06`).
 *
 * <p><b>Es el único lugar del código que decide códigos de estado</b> (`development-guide.md`
 * §7.1). Ni el dominio ni los controladores mencionan uno: el dominio dice qué regla se incumplió y
 * aquí se decide cómo se ve eso desde fuera. Sin esa concentración, el mismo rechazo acabaría
 * devolviendo {@code 400} en un endpoint y {@code 422} en otro.
 *
 * <p><b>Y es también donde se decide qué se audita</b>, porque es el único punto que ve a la vez la
 * excepción, el estado resultante y el contexto de la petición. Las reglas son las de {@code
 * architecture.md} §6.6.4 y no se repiten en cada caso de uso:
 *
 * <ul>
 *   <li>Violación de regla de negocio ({@code 409}) y referencia que no resuelve ({@code 422}) →
 *       {@code audit_error_log} con {@code error_type = BUSINESS_RULE}.
 *   <li>Fallo no controlado ({@code 5xx}) → {@code audit_error_log} con {@code UNHANDLED}.
 *   <li>Denegación de autorización ({@code 403}) → <b>{@code audit_security_log}</b>, nunca el
 *       registro de errores: una denegación no es un fallo del sistema, es el sistema funcionando.
 *       El esquema lo impone con {@code ck_audit_error_log_status}.
 *   <li>Validación de formato ({@code 400}), {@code 401} y {@code 404} → <b>no se auditan</b>. Son
 *       ruido de formulario y {@code request_log} ya los cubre.
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /**
   * Reglas cuya violación es un <b>intento de escalada de privilegios</b> y no un error de
   * operación.
   *
   * <p>Declarar permisos por encima del rol padre (`RN-SEG-003`) o por encima de los del propio
   * actor (`RN-SEG-010`) debe poder encontrarse buscando por severidad, de ahí {@code ALTA}. El
   * resto de los rechazos de negocio —duplicado, padre inválido, permiso inexistente— son {@code
   * MEDIA} (`plan.md` §6).
   */
  private static final Set<String> REGLAS_DE_ESCALADA = Set.of("RN-SEG-003", "RN-SEG-010");

  /**
   * Restricciones declaradas {@code DEFERRABLE INITIALLY DEFERRED} cuya violación es un <b>empate
   * concurrente</b> y no un dato inválido.
   *
   * <p>Se enumeran a propósito en lugar de tratar toda confirmación fallida como un empate:
   * cualquier otro fallo al confirmar es un defecto, y darle esta respuesta lo escondería. Cada
   * requerimiento que declare una restricción diferida con semántica de reintento añade aquí su
   * nombre.
   */
  private static final Set<String> RESTRICCIONES_DIFERIDAS =
      Set.of("uq_memberships_parent", "uq_memberships_level");

  /**
   * Separador del código dentro del mensaje de una restricción de Bean Validation.
   *
   * <p>Las anotaciones estándar no admiten un atributo propio para el código, de modo que el DTO lo
   * escribe como prefijo —{@code "VAL-001: El código del rol es obligatorio."}— y aquí se separa.
   * Es una convención y no una limitación del formato: la alternativa era un catálogo paralelo
   * campo-a-código que habría que mantener sincronizado con las anotaciones.
   */
  private static final String SEPARADOR_CODIGO = ": ";

  private final AuditWriter auditoria;

  public GlobalExceptionHandler(AuditWriter auditoria) {
    this.auditoria = auditoria;
  }

  // ---------------------------------------------------------------------------
  // Excepciones del dominio
  // ---------------------------------------------------------------------------

  /** {@code 400}. No se audita: ruido de formulario (`architecture.md` §6.6.4). */
  @ExceptionHandler(ValidationException.class)
  public ProblemDetail deValidacion(ValidationException fallo, HttpServletRequest peticion) {
    return problema(ProblemKind.VALIDACION, fallo, peticion);
  }

  /** {@code 409}. Se audita como {@code BUSINESS_RULE}. */
  @ExceptionHandler(BusinessRuleException.class)
  public ProblemDetail deReglaDeNegocio(BusinessRuleException fallo, HttpServletRequest peticion) {
    ProblemDetail detalle = problema(ProblemKind.REGLA_DE_NEGOCIO, fallo, peticion);
    auditarRechazo(fallo, ProblemKind.REGLA_DE_NEGOCIO, peticion);
    return detalle;
  }

  /** {@code 422}. Se audita como {@code BUSINESS_RULE}. */
  @ExceptionHandler(UnprocessableEntityException.class)
  public ProblemDetail deEntidadNoProcesable(
      UnprocessableEntityException fallo, HttpServletRequest peticion) {
    ProblemDetail detalle = problema(ProblemKind.ENTIDAD_NO_PROCESABLE, fallo, peticion);
    auditarRechazo(fallo, ProblemKind.ENTIDAD_NO_PROCESABLE, peticion);
    return detalle;
  }

  /** {@code 404}. No se audita: {@code request_log} ya lo cubre. */
  @ExceptionHandler(ResourceNotFoundException.class)
  public ProblemDetail deNoEncontrado(
      ResourceNotFoundException fallo, HttpServletRequest peticion) {
    return problema(ProblemKind.NO_ENCONTRADO, fallo, peticion);
  }

  /** {@code 401}. No se audita aquí; la autenticación tiene sus propios eventos (`RF-SP-034`). */
  @ExceptionHandler(UnauthorizedException.class)
  public ProblemDetail deNoAutenticado(UnauthorizedException fallo, HttpServletRequest peticion) {
    return problema(ProblemKind.NO_AUTENTICADO, fallo, peticion);
  }

  /**
   * {@code 403} lanzado por un <b>caso de uso</b>.
   *
   * <p>Se audita con severidad {@code ALTA} y no {@code MEDIA}: cuando quien deniega es el caso de
   * uso, la causa es `RN-SEG-011` —alguien operando sobre su propio rol—, que no puede verificarse
   * antes de leer el rol y que es la prohibición que este módulo más necesita poder buscar
   * (`RF-SP-014` §2). La denegación por permiso ausente, que resuelve la capa de seguridad, es
   * {@code MEDIA} y se atiende más abajo.
   */
  @ExceptionHandler(ForbiddenException.class)
  public ProblemDetail deSinPermiso(ForbiddenException fallo, HttpServletRequest peticion) {
    ProblemDetail detalle = problema(ProblemKind.SIN_PERMISO, fallo, peticion);
    auditarDenegacion(Severity.ALTA, fallo.errorCode(), peticion);
    return detalle;
  }

  // ---------------------------------------------------------------------------
  // Excepciones del framework
  // ---------------------------------------------------------------------------

  /**
   * Bean Validation: {@code 400} con <b>todas</b> las violaciones juntas.
   *
   * <p>Se devuelven todas y no la primera porque son independientes entre sí, y devolverlas de a
   * una obliga a corregir el formulario campo por campo (`plan.md` §4).
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail deBeanValidation(
      MethodArgumentNotValidException fallo, HttpServletRequest peticion) {

    List<FieldError> errores =
        fallo.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    separarCodigo(
                        error.getField(),
                        error.getDefaultMessage() == null ? "" : error.getDefaultMessage()))
            .toList();

    ProblemDetail detalle =
        base(
            ProblemKind.VALIDACION,
            errores.isEmpty()
                ? "La solicitud contiene campos inválidos."
                : errores.get(0).message(),
            peticion);
    detalle.setProperty("errors", errores);
    return detalle;
  }

  /**
   * Cuerpo ilegible o con un campo desconocido: {@code 400}.
   *
   * <p>El campo desconocido llega aquí porque {@code application.yml} deja activo {@code
   * FAIL_ON_UNKNOWN_PROPERTIES}. Es lo que hace verificable a `CA-SP-146`: enviar {@code "status":
   * "INACTIVO"} en el alta de un rol no se ignora en silencio, se rechaza.
   *
   * <p>El mensaje no incluye la excepción original: el detalle de Jackson menciona clases y
   * paquetes (Art. VI.5). Va al log, no a la respuesta.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail deCuerpoIlegible(
      HttpMessageNotReadableException fallo, HttpServletRequest peticion) {
    LOG.debug("Cuerpo de la petición ilegible", fallo);
    return base(
        ProblemKind.VALIDACION,
        "El cuerpo de la solicitud no es válido o contiene campos que este endpoint no admite.",
        peticion);
  }

  /**
   * {@code 403} de la <b>capa de seguridad</b>: el actor está autenticado pero no declara el
   * permiso que el método exige.
   *
   * <p>La comprobación de {@code @PreAuthorize} ocurre <b>antes</b> de entrar al cuerpo del método,
   * de modo que `CA-SP-008` se satisface aquí y no en el caso de uso. El evento va a {@code
   * audit_security_log} con {@code MEDIA} y {@code FAILURE}, nunca al registro de errores.
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail deAccesoDenegado(AccessDeniedException fallo, HttpServletRequest peticion) {
    LOG.debug("Acceso denegado", fallo);
    ProblemDetail detalle =
        base(ProblemKind.SIN_PERMISO, "No tiene permiso para ejecutar esta operación.", peticion);
    detalle.setProperty("errors", List.of());
    auditarDenegacion(Severity.MEDIA, "AUTH-002", peticion);
    return detalle;
  }

  /**
   * El recurso existe pero no admite ese método: {@code 405}.
   *
   * <p><b>Sin este manejador el caso salía como {@code 500}</b>, y es el mismo defecto de forma que
   * el del argumento no convertible: Spring lanza la excepción antes de que exista un controlador
   * que la atienda, de modo que caía en el {@code catch} genérico.
   *
   * <p>Importa más de lo que parece, porque <b>varios requerimientos cumplen una regla no
   * escribiendo código</b>: `RN-SP-004` hace el catálogo de permisos inmutable por API y
   * `RN-SP-010` el de monedas, y ninguno de los dos se implementa con una validación que rechace —
   * se implementan porque no hay manejador que llamar. La verificación de esa ausencia es
   * precisamente un {@code 405}, y con un {@code 500} el criterio decía lo contrario de lo que
   * quería decir.
   *
   * <p>No se audita: no es un fallo del sistema ni una regla de negocio violada, sino una petición
   * mal dirigida. {@code request_log} ya la cubre.
   */
  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  public ProblemDetail deMetodoNoPermitido(
      org.springframework.web.HttpRequestMethodNotSupportedException fallo,
      HttpServletRequest peticion) {

    ProblemDetail detalle =
        base(
            ProblemKind.METODO_NO_PERMITIDO,
            "El recurso no admite el método " + fallo.getMethod() + ".",
            peticion);
    detalle.setProperty("errors", List.of());
    return detalle;
  }

  /**
   * Ninguna ruta atiende esa dirección: {@code 404}.
   *
   * <p>Mismo motivo que el anterior — se lanza antes de llegar a un controlador— y misma
   * consecuencia si falta: un {@code 500} donde corresponde un {@code 404}. Es lo que responde, por
   * ejemplo, un {@code PATCH} sobre el recurso completo de una moneda, cuya ruta no está mapeada
   * para ningún método porque el estado se cambia sobre el subrecurso.
   */
  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  public ProblemDetail deRutaInexistente(
      org.springframework.web.servlet.resource.NoResourceFoundException fallo,
      HttpServletRequest peticion) {

    LOG.debug("Ruta no mapeada: {} {}", peticion.getMethod(), peticion.getRequestURI(), fallo);
    ProblemDetail detalle =
        base(ProblemKind.NO_ENCONTRADO, "La dirección solicitada no existe.", peticion);
    detalle.setProperty("errors", List.of());
    return detalle;
  }

  /**
   * Un valor de la ruta o de la consulta que no se puede convertir al tipo declarado: {@code 400}.
   *
   * <p><b>Sin este manejador el caso salía como {@code 500}</b>, y no es un matiz: {@code GET
   * /api/v1/memberships/abc} es una petición mal formada, no un fallo del sistema. Spring lanza la
   * excepción al convertir el argumento —antes de entrar al controlador—, de modo que ningún caso
   * de uso puede atraparla y sin esto caía en el {@code catch} genérico. El síntoma era doble: el
   * cliente recibía un {@code 500} por un dedazo, y {@code audit_error_log} acumulaba fallos {@code
   * UNHANDLED} de severidad {@code ALTA} que no eran fallos de nada.
   *
   * <p>Se detectó al implementar `RF-SP-018` · `T-08`, pero <b>alcanza a todo endpoint con una
   * variable de ruta tipada</b>, presentes y futuros.
   *
   * <p>No se audita, por lo mismo que el resto de las validaciones de formato: es ruido de
   * formulario y {@code request_log} ya lo cubre. El esquema lo respalda —{@code
   * ck_audit_error_log_status} rechaza el {@code 400}—.
   */
  @ExceptionHandler(
      org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
  public ProblemDetail deTipoIncorrecto(
      org.springframework.web.method.annotation.MethodArgumentTypeMismatchException fallo,
      HttpServletRequest peticion) {

    // El mensaje NO menciona el tipo esperado ni la excepción de conversión:
    // ambos nombran clases y paquetes internos (Art. VI.5).
    String mensaje = "El valor de '" + fallo.getName() + "' no tiene el formato esperado.";

    ProblemDetail detalle = base(ProblemKind.VALIDACION, mensaje, peticion);
    detalle.setProperty("errors", List.of(new FieldError(fallo.getName(), "VAL-001", mensaje)));
    return detalle;
  }

  /**
   * Violación de una restricción <b>diferida</b>, que salta al confirmar la transacción.
   *
   * <p><b>Por qué necesita un manejador propio.</b> Una restricción {@code DEFERRABLE INITIALLY
   * DEFERRED} se evalúa en el {@code COMMIT}, es decir, cuando el interceptor transaccional cierra
   * — <b>fuera</b> del caso de uso y fuera del adaptador. Ningún {@code try} del repositorio puede
   * capturarla, de modo que sin esto llegaría como fallo no controlado y el cliente recibiría un
   * {@code 500} por un empate que tiene respuesta de negocio.
   *
   * <p>Hoy la usan las dos restricciones de la cadena de membresías. Que dos altas simultáneas
   * pretendan ser la superior de la misma hija no es un dato inválido: es que la cadena cambió
   * mientras esta operación se resolvía, y <b>la misma petición repetida es correcta</b>. De ahí
   * que `EX-003` diga que se reintente y no que algo esté mal.
   *
   * <p>Se distingue por el <b>nombre de la restricción</b>, nunca por el texto del mensaje del
   * driver, que cambia entre versiones. Una violación diferida que no reconozcamos se relanza:
   * darle a todo fallo de confirmación la respuesta de un empate escondería defectos reales.
   */
  @ExceptionHandler(org.springframework.transaction.TransactionSystemException.class)
  public ProblemDetail deConfirmacionFallida(
      org.springframework.transaction.TransactionSystemException fallo,
      HttpServletRequest peticion) {

    String restriccion = nombreDeRestriccion(fallo);

    // El nulo se comprueba aparte: `Set.of(...)` es un conjunto inmutable del
    // JDK y su `contains(null)` lanza NullPointerException en lugar de devolver
    // false. Sin esta guarda, un fallo al confirmar que no venga de una
    // restricción —el caso más común de todos— reventaría DENTRO del manejador
    // de excepciones, que es el peor sitio posible para que algo falle.
    if (restriccion == null || !RESTRICCIONES_DIFERIDAS.contains(restriccion)) {
      return deFalloNoControlado(fallo, peticion);
    }

    LOG.info(
        "Empate concurrente sobre la restricción diferida {}. correlationId={}",
        restriccion,
        correlacion());

    ProblemDetail detalle =
        base(
            ProblemKind.REGLA_DE_NEGOCIO,
            "La cadena cambió durante la operación. Vuelva a intentarlo.",
            peticion);
    detalle.setProperty("errors", List.of());

    auditar(
        new ErrorEvent(
            recurso(peticion),
            null,
            operacion(peticion),
            "EX-003",
            ErrorType.BUSINESS_RULE,
            ProblemKind.REGLA_DE_NEGOCIO.status().value(),
            Severity.MEDIA,
            "Empate concurrente al reordenar la cadena."));
    return detalle;
  }

  private static String nombreDeRestriccion(Throwable fallo) {
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
      if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion) {
        return violacion.getConstraintName();
      }
    }
    return null;
  }

  /**
   * Cualquier otra cosa: {@code 500}, auditado como {@code UNHANDLED} con severidad {@code ALTA}.
   *
   * <p>Es el único {@code catch} genérico admitido (`development-guide.md` §7.2). La traza va al
   * log de aplicación, alcanzable por correlación; la respuesta no la lleva (Art. VI.5) y el
   * registro de auditoría tampoco: esa tabla responde «a quién le falló qué», no «en qué línea».
   */
  @ExceptionHandler(Exception.class)
  public ProblemDetail deFalloNoControlado(Exception fallo, HttpServletRequest peticion) {
    LOG.error(
        "Fallo no controlado en {} {}", peticion.getMethod(), peticion.getRequestURI(), fallo);

    ProblemDetail detalle =
        base(
            ProblemKind.INTERNO,
            "Ocurrió un error inesperado. Reporte el identificador de correlación.",
            peticion);
    detalle.setProperty("errors", List.of());

    auditar(
        new ErrorEvent(
            recurso(peticion),
            null,
            operacion(peticion),
            "ERR-500",
            ErrorType.UNHANDLED,
            ProblemKind.INTERNO.status().value(),
            Severity.ALTA,
            "Fallo no controlado."));
    return detalle;
  }

  // ---------------------------------------------------------------------------
  // Construcción de la respuesta
  // ---------------------------------------------------------------------------

  private ProblemDetail problema(
      ProblemKind forma, DomainException fallo, HttpServletRequest peticion) {
    ProblemDetail detalle = base(forma, fallo.getMessage(), peticion);
    detalle.setProperty("errors", fallo.errors());
    return detalle;
  }

  private static ProblemDetail base(
      ProblemKind forma, String detalleTexto, HttpServletRequest peticion) {
    ProblemDetail problema = ProblemDetail.forStatus(forma.status());
    problema.setType(URI.create(forma.type()));
    problema.setTitle(forma.title());
    problema.setDetail(detalleTexto);
    problema.setInstance(URI.create(peticion.getRequestURI()));
    // Siempre presente, para que quien reporte un error pueda citarlo y el
    // equipo localizarlo en request_log (Art. XV.1).
    problema.setProperty("correlationId", correlacion());
    return problema;
  }

  private static UUID correlacion() {
    return RequestContext.current().map(RequestContext::correlationId).orElse(null);
  }

  /**
   * Separa {@code "VAL-001: El código es obligatorio."} en su código y su mensaje.
   *
   * <p>Un mensaje sin prefijo reconocible se devuelve entero con código {@code VAL-000}: es
   * preferible una respuesta con un código genérico a una que se coma el mensaje intentando
   * partirlo.
   */
  private static FieldError separarCodigo(String campo, String mensaje) {
    int corte = mensaje.indexOf(SEPARADOR_CODIGO);
    if (corte > 0) {
      String posibleCodigo = mensaje.substring(0, corte);
      if (posibleCodigo.matches("(VAL|EX|AUTH|INT|ERR)-\\d+|RN-[A-Z]+-\\d+")) {
        return new FieldError(campo, posibleCodigo, mensaje.substring(corte + 2));
      }
    }
    return new FieldError(campo, "VAL-000", mensaje);
  }

  // ---------------------------------------------------------------------------
  // Auditoría
  // ---------------------------------------------------------------------------

  private void auditarRechazo(
      DomainException fallo, ProblemKind forma, HttpServletRequest peticion) {
    auditar(
        new ErrorEvent(
            recurso(peticion),
            null,
            operacion(peticion),
            fallo.errorCode(),
            ErrorType.BUSINESS_RULE,
            forma.status().value(),
            REGLAS_DE_ESCALADA.contains(fallo.errorCode()) ? Severity.ALTA : Severity.MEDIA,
            fallo.getMessage()));
  }

  private void auditarDenegacion(Severity severidad, String codigo, HttpServletRequest peticion) {
    intentar(
        () ->
            auditoria.recordSecurity(
                new SecurityEvent(
                    SecurityEventType.AUTHORIZATION_DENIED,
                    severidad,
                    Outcome.FAILURE,
                    null,
                    Map.of("operation", operacion(peticion), "errorCode", codigo))));
  }

  private void auditar(ErrorEvent evento) {
    intentar(() -> auditoria.recordError(evento));
  }

  /**
   * Un fallo al auditar no debe convertir un {@code 409} legible en un {@code 500} opaco.
   *
   * <p>Es la única excepción admisible a «nunca captures una excepción para ignorarla»
   * (`development-guide.md` §7.2), y no se ignora: se registra como {@code ERROR} con su
   * correlación, que es lo que permite detectar que la auditoría dejó de escribir.
   */
  private static void intentar(Runnable escritura) {
    try {
      escritura.run();
    } catch (RuntimeException fallo) {
      LOG.error("No se pudo escribir la auditoría. correlationId={}", correlacion(), fallo);
    }
  }

  private static String recurso(HttpServletRequest peticion) {
    String ruta = peticion.getRequestURI();
    return ruta.length() > 100 ? ruta.substring(0, 100) : ruta;
  }

  private static String operacion(HttpServletRequest peticion) {
    String operacion = peticion.getMethod() + " " + peticion.getRequestURI();
    return operacion.length() > 100 ? operacion.substring(0, 100) : operacion;
  }
}
