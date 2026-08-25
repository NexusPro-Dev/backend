package com.factech.nexus.modules.system.audit.domain.service;

import com.factech.nexus.modules.system.audit.application.AuditFilters;
import com.factech.nexus.modules.system.audit.application.ChangeAuditItem;
import com.factech.nexus.modules.system.audit.application.DeletionAuditItem;
import com.factech.nexus.modules.system.audit.application.ErrorAuditItem;
import com.factech.nexus.modules.system.audit.application.ListChangeAuditRequest;
import com.factech.nexus.modules.system.audit.application.ListDeletionAuditRequest;
import com.factech.nexus.modules.system.audit.application.ListErrorAuditRequest;
import com.factech.nexus.modules.system.audit.application.ListSecurityAuditRequest;
import com.factech.nexus.modules.system.audit.application.SecurityAuditItem;
import com.factech.nexus.modules.system.audit.domain.repository.AuditQueryRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEnums.ErrorType;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los cuatro listados de auditoría (`RF-SP-011` a `RF-SP-014`).
 *
 * <p><b>Un caso de uso para los cuatro, y no cuatro.</b> Comparten la validación entera
 * —paginación, rango de fechas y dominios cerrados—, el conteo acotado y la forma de la respuesta;
 * lo único propio de cada uno son sus filtros y su permiso, que se declara en el controlador.
 * Cuatro clases con la misma estructura habrían sido cuatro copias de {@link #verificar}, y la
 * copia que se quedara atrás no fallaría: aceptaría un filtro que las otras rechazan.
 *
 * <p><b>Ninguna regla de negocio gobierna estas consultas</b> (`spec.md` §5 de las cuatro). Lo que
 * hay aquí es orquestación: validar, leer, contar y componer.
 *
 * <p><b>Solo la consulta de seguridad se audita a sí misma</b> (`CA-SP-167`). Las otras tres dejan
 * rastro en el registro de peticiones y con eso basta para saber quién miró; en el registro de
 * seguridad, en cambio, <b>el acto de mirar es en sí mismo información de seguridad</b>: quién
 * revisó los intentos de acceso de una cuenta ajena, y con qué filtros, es exactamente la clase de
 * hecho que ese registro existe para conservar.
 */
@Service
public class AuditQueryService {

  private final AuditQueryRepository consultas;
  private final Pagination paginacion;
  private final AuditWriter auditoria;

  public AuditQueryService(
      AuditQueryRepository consultas, Pagination paginacion, AuditWriter auditoria) {
    this.consultas = consultas;
    this.paginacion = paginacion;
    this.auditoria = auditoria;
  }

  @Transactional(readOnly = true)
  public PageResponse<ChangeAuditItem> changes(ListChangeAuditRequest filtros) {
    Pagination.Slice trozo =
        verificar(
            filtros,
            problemas -> dominio("action", filtros.action(), ChangeAction.class, problemas));

    return PageResponse.de(
        consultas.changes(filtros, trozo.offset(), trozo.size()),
        consultas.countChanges(filtros, paginacion.techoDelConteo()),
        trozo.page(),
        trozo.size());
  }

  @Transactional(readOnly = true)
  public PageResponse<DeletionAuditItem> deletions(ListDeletionAuditRequest filtros) {
    Pagination.Slice trozo =
        verificar(
            filtros,
            problemas ->
                dominio("deletionType", filtros.deletionType(), DeletionType.class, problemas));

    return PageResponse.de(
        consultas.deletions(filtros, trozo.offset(), trozo.size()),
        consultas.countDeletions(filtros, paginacion.techoDelConteo()),
        trozo.page(),
        trozo.size());
  }

  @Transactional(readOnly = true)
  public PageResponse<ErrorAuditItem> errors(ListErrorAuditRequest filtros) {
    Pagination.Slice trozo =
        verificar(
            filtros,
            problemas -> {
              dominio("errorType", filtros.errorType(), ErrorType.class, problemas);
              dominio("severity", filtros.severity(), Severity.class, problemas);
            });

    return PageResponse.de(
        consultas.errors(filtros, trozo.offset(), trozo.size()),
        consultas.countErrors(filtros, paginacion.techoDelConteo()),
        trozo.page(),
        trozo.size());
  }

  /**
   * El registro de seguridad, que <b>deja constancia de haber sido consultado</b>.
   *
   * <p>El evento se emite <b>después</b> de resolver la consulta y con transacción propia: lo que
   * interesa conservar es que alguien miró y con qué filtros, no que la sentencia terminara. Y por
   * eso mismo el detalle guarda los filtros usados: «alguien consultó el registro de seguridad» no
   * dice nada; «alguien consultó los intentos de acceso de esta cuenta en estas fechas» sí.
   */
  @Transactional(readOnly = true)
  public PageResponse<SecurityAuditItem> security(ListSecurityAuditRequest filtros) {
    Pagination.Slice trozo =
        verificar(
            filtros,
            problemas -> {
              dominio("eventType", filtros.eventType(), SecurityEventType.class, problemas);
              dominio("severity", filtros.severity(), Severity.class, problemas);
              dominio("outcome", filtros.outcome(), Outcome.class, problemas);
            });

    PageResponse<SecurityAuditItem> pagina =
        PageResponse.de(
            consultas.security(filtros, trozo.offset(), trozo.size()),
            consultas.countSecurity(filtros, paginacion.techoDelConteo()),
            trozo.page(),
            trozo.size());

    auditoria.recordSecurity(
        new SecurityEvent(
            SecurityEventType.SECURITY_AUDIT_READ,
            Severity.INFORMATIVA,
            Outcome.SUCCESS,
            filtros.targetUserId(),
            filtrosUsados(filtros)));

    return pagina;
  }

  // ---------------------------------------------------------------------------
  // Validación, común a los cuatro
  // ---------------------------------------------------------------------------

  /**
   * Paginación, rango de fechas y dominios cerrados, <b>todo junto</b>.
   *
   * <p>Los rechazos se acumulan en lugar de interrumpir: son independientes entre sí, y devolverlos
   * de a uno obliga a corregir la URL parámetro por parámetro.
   *
   * @param extra las comprobaciones propias del registro, que añaden sus problemas a la lista
   */
  private Pagination.Slice verificar(
      AuditFilters filtros, java.util.function.Consumer<List<FieldError>> extra) {

    List<FieldError> problemas = new ArrayList<>();

    Pagination.Slice trozo = null;
    try {
      trozo = paginacion.resolver(filtros.page(), filtros.size());
    } catch (ValidationException paginacionInvalida) {
      problemas.addAll(paginacionInvalida.errors());
    }

    if (filtros.from() != null && filtros.to() != null && filtros.from().isAfter(filtros.to())) {
      String mensaje = "La fecha inicial no puede ser posterior a la final.";
      problemas.add(new FieldError("from", "VAL-001", mensaje));
    }

    extra.accept(problemas);

    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(), "La consulta solicitada no es válida.", problemas);
    }
    return trozo;
  }

  /**
   * Un filtro cuyo valor debe pertenecer a un dominio cerrado.
   *
   * <p>Se compara <b>sin distinguir caja</b> y el mensaje enumera los admitidos: un filtro
   * rechazado sin decir qué se admite obliga a buscar el dominio en la documentación — o a
   * adivinarlo probando.
   */
  private static <E extends Enum<E>> void dominio(
      String campo, String valor, Class<E> dominio, List<FieldError> problemas) {

    if (valor == null) {
      return;
    }
    boolean valido =
        Arrays.stream(dominio.getEnumConstants())
            .anyMatch(constante -> constante.name().equalsIgnoreCase(valor));

    if (!valido) {
      String mensaje =
          "El valor '"
              + valor
              + "' no es válido. Valores admitidos: "
              + Arrays.stream(dominio.getEnumConstants()).map(Enum::name).toList()
              + ".";
      problemas.add(new FieldError(campo, "VAL-003", mensaje));
    }
  }

  /**
   * Los filtros de la consulta, para el evento que la registra.
   *
   * <p>Solo los informados: un mapa con diez claves nulas no dice qué se buscó. Y solo los filtros
   * —nunca los resultados—, porque el evento debe registrar <b>qué se preguntó</b>, no volcar una
   * segunda copia de lo que el registro ya contiene.
   */
  private static Map<String, Object> filtrosUsados(ListSecurityAuditRequest filtros) {
    Map<String, Object> usados = new LinkedHashMap<>();
    Optional.ofNullable(filtros.eventType()).ifPresent(valor -> usados.put("eventType", valor));
    Optional.ofNullable(filtros.severity()).ifPresent(valor -> usados.put("severity", valor));
    Optional.ofNullable(filtros.outcome()).ifPresent(valor -> usados.put("outcome", valor));
    Optional.ofNullable(filtros.actorId())
        .ifPresent(valor -> usados.put("actorId", valor.toString()));
    Optional.ofNullable(filtros.targetUserId())
        .ifPresent(valor -> usados.put("targetUserId", valor.toString()));
    Optional.ofNullable(filtros.ipAddress()).ifPresent(valor -> usados.put("ipAddress", valor));
    Optional.ofNullable(filtros.from()).ifPresent(valor -> usados.put("from", valor.toString()));
    Optional.ofNullable(filtros.to()).ifPresent(valor -> usados.put("to", valor.toString()));
    return usados;
  }
}
