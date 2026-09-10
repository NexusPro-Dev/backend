package com.factech.nexus.modules.system.brokers.domain.service;

import com.factech.nexus.modules.system.brokers.application.BrokerAccountItem.BrokerRef;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountsPage;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountsPage.ByBroker;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountsPage.Summary;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountsPage.Totals;
import com.factech.nexus.modules.system.brokers.application.ListBrokerAccountsRequest;
import com.factech.nexus.modules.system.brokers.application.TeamBrokerAccountItem;
import com.factech.nexus.modules.system.brokers.domain.models.UserBrokerStatus;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountQueryRepository;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountQueryRepository.BrokerAccountFilters;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountQueryRepository.BrokerStatusCount;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Todas las cuentas de broker del sistema, filtradas (`RF-SP-057`).
 *
 * <p><b>Nace de un hueco que `RF-SP-056` cerró a propósito y duró un día.</b> Aquel resolvió «¿cómo
 * va <b>mi</b> equipo?» y dejó escrito que un administrador no podía pedir el de otro — «un {@code
 * ?supervisorId=} es otro requerimiento y nadie lo ha pedido». Se pidió al día siguiente.
 *
 * <h2>Aquí la autorización es corriente, y de eso sale todo lo demás</h2>
 *
 * <p>Lo gobierna {@code broker-accounts:read} con un {@code @PreAuthorize} normal, al revés que
 * {@code GetBrokerAccountsService}, que resuelve el par (actor, consultado) a mano. Y esa
 * normalidad decide las dos diferencias que más llaman la atención al comparar los tres servicios:
 *
 * <ul>
 *   <li><b>No hay {@code 404} uniforme.</b> No hay recurso ajeno que pedir — el actor ya puede
 *       verlo todo—, de modo que no hay existencia que ocultar.
 *   <li><b>No hay cota de un nivel.</b> `RF-SP-042`, `RF-SP-055` y `RF-SP-056` la tienen porque a
 *       aquellas <b>las autoriza la estructura</b>, y devolver la rama entera publicaría la empresa
 *       a quien solo lleva un equipo. Aquí la autoriza el permiso: <b>la profundidad no concede
 *       nada que el actor no tuviera</b>, le ahorra recorrer el árbol (`RN-SP-047`).
 * </ul>
 *
 * <p><b>Y no amplía la excepción a D-22</b>: este listado <b>filtra</b> por estructura, no se
 * <b>autoriza</b> por ella. Queda escrito en `security.md` §5 para que nadie lea esta ruta como un
 * precedente.
 */
@Service
public class ListBrokerAccountsService {

  private final BrokerAccountQueryRepository cuentas;
  private final Pagination paginacion;

  public ListBrokerAccountsService(BrokerAccountQueryRepository cuentas, Pagination paginacion) {
    this.cuentas = cuentas;
    this.paginacion = paginacion;
  }

  /** El resumen y la página, <b>en la misma transacción de solo lectura</b>. */
  @Transactional(readOnly = true)
  public BrokerAccountsPage list(ListBrokerAccountsRequest peticion) {
    Pagination.Slice trozo = paginacion.resolver(peticion.page(), peticion.size());

    BrokerAccountFilters filtros =
        new BrokerAccountFilters(
            peticion.supervisorId(),
            peticion.userId(),
            estado(peticion.status()),
            peticion.brokerId(),
            peticion.search(),
            peticion.from(),
            rangoVerificado(peticion));

    List<BrokerStatusCount> conteos = cuentas.summarize(filtros);
    List<TeamBrokerAccountItem> pagina = cuentas.findAll(filtros, trozo.offset(), trozo.size());

    Summary resumen = resumen(conteos);

    // `totalElements` sale DEL RESUMEN y no de un conteo aparte: son el mismo
    // número, y calcularlos por separado daría dos fuentes de verdad sobre lo
    // mismo — el día que una divergiera, nadie sabría cuál creer.
    PageResponse<TeamBrokerAccountItem> pagina0 =
        PageResponse.de(pagina, resumen.accounts().total(), trozo.page(), trozo.size());

    return new BrokerAccountsPage(
        pagina0.content(),
        pagina0.totalElements(),
        pagina0.totalPages(),
        pagina0.page(),
        pagina0.size(),
        pagina0.totalIsExact(),
        resumen);
  }

  /**
   * Los dos totales y sus desgloses, armados de <b>una sola</b> consulta agrupada.
   *
   * <p><b>El de {@code FIRST_DEPOSIT} sale del mismo conjunto que el otro</b>, y por tanto respeta
   * también el filtro {@code status} (decisión del 10-09-2026). De ahí que con {@code
   * ?status=REGISTER} valga cero: no es que nadie haya depositado, es que no se pidió ninguno.
   *
   * <p><b>El desglose conserva el orden de la consulta</b> —por nombre de broker— porque se recorre
   * en orden y se acumula en un mapa que lo preserva. Reordenar aquí sería una segunda copia del
   * criterio que ya fijó el {@code ORDER BY}.
   */
  private static Summary resumen(List<BrokerStatusCount> conteos) {
    Map<UUID, ByBroker> todas = new LinkedHashMap<>();
    Map<UUID, ByBroker> depositadas = new LinkedHashMap<>();
    long total = 0;
    long conDeposito = 0;

    for (BrokerStatusCount fila : conteos) {
      BrokerRef broker = new BrokerRef(fila.brokerId(), fila.brokerName());
      acumular(todas, broker, fila.total());
      total += fila.total();

      if (fila.status() == UserBrokerStatus.FIRST_DEPOSIT) {
        acumular(depositadas, broker, fila.total());
        conDeposito += fila.total();
      }
    }

    return new Summary(
        new Totals(total, List.copyOf(todas.values())),
        new Totals(conDeposito, List.copyOf(depositadas.values())));
  }

  private static void acumular(Map<UUID, ByBroker> destino, BrokerRef broker, long cuantas) {
    destino.merge(
        broker.id(),
        new ByBroker(broker, cuantas),
        (antes, ahora) -> new ByBroker(broker, antes.total() + ahora.total()));
  }

  /**
   * Un estado inválido es {@code 400}, y un identificador inexistente es la página vacía.
   *
   * <p>La asimetría es la misma que en `RF-SP-056` y por el mismo motivo: un identificador que no
   * designa nada es <b>una pregunta legítima con respuesta vacía</b>, mientras que {@code
   * ?status=depositado} es <b>una pregunta mal escrita</b> — devolver vacío haría creer que nadie
   * está en ese estado.
   */
  private static UserBrokerStatus estado(String valor) {
    if (valor == null) {
      return null;
    }
    return UserBrokerStatus.de(valor)
        .orElseThrow(() -> invalido("status", "El estado debe ser REGISTER o FIRST_DEPOSIT."));
  }

  /**
   * {@code from} posterior a {@code to} es {@code 400}, y no un rango vacío.
   *
   * <p>Un rango imposible <b>no es un rango sin resultados</b>: devolverlo como vacío haría creer
   * que no hubo altas en ese periodo. Es el mismo criterio que aplica {@code AuditQueryService}.
   */
  private static java.time.OffsetDateTime rangoVerificado(ListBrokerAccountsRequest p) {
    if (p.from() != null && p.to() != null && p.from().isAfter(p.to())) {
      throw invalido("to", "El fin del rango no puede ser anterior a su inicio.");
    }
    return p.to();
  }

  private static ValidationException invalido(String campo, String mensaje) {
    return new ValidationException(
        "VAL-001", mensaje, List.of(new FieldError(campo, "VAL-001", mensaje)));
  }
}
