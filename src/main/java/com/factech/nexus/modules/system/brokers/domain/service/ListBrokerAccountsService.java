package com.factech.nexus.modules.system.brokers.domain.service;

import com.factech.nexus.modules.system.brokers.application.BrokerAccountItem.BrokerRef;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountsPage;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountsPage.ByBroker;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountsPage.Summary;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountsPage.Totals;
import com.factech.nexus.modules.system.brokers.application.BrokerItem;
import com.factech.nexus.modules.system.brokers.application.ListBrokerAccountsRequest;
import com.factech.nexus.modules.system.brokers.application.TeamBrokerAccountItem;
import com.factech.nexus.modules.system.brokers.domain.models.UserBrokerStatus;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountQueryRepository;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountQueryRepository.BrokerAccountFilters;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountQueryRepository.BrokerStatusCount;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerQueryRepository;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.ArrayList;
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

  /**
   * El catálogo de `RF-SP-052`, para completar el desglose con los brokers <b>sin</b> cuentas.
   *
   * <p>Se reutiliza en lugar de escribir un {@code LEFT JOIN} contra la consulta filtrada: aquello
   * obligaría a meter el predicado —y la recursiva de la red— dentro de una unión externa, para
   * completar tres filas.
   */
  private final BrokerQueryRepository brokers;

  private final Pagination paginacion;

  public ListBrokerAccountsService(
      BrokerAccountQueryRepository cuentas, BrokerQueryRepository brokers, Pagination paginacion) {
    this.cuentas = cuentas;
    this.brokers = brokers;
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
   * Los tres totales y sus desgloses, armados de <b>una sola</b> consulta agrupada.
   *
   * <p><b>Los tres salen del mismo conjunto</b>, y por tanto respetan también el filtro {@code
   * status} (decisión del 10-09-2026). De ahí que con {@code ?status=REGISTER} el de {@code
   * FIRST_DEPOSIT} valga cero: no es que nadie haya depositado, es que no se pidió ninguno.
   *
   * <p><b>El desglose se arma sobre EL CATÁLOGO ENTERO</b> y no sobre las filas que devolvió la
   * consulta: se parte de todos los brokers en cero y se van sumando los conteos encima. Es lo que
   * hace que <b>la longitud del arreglo no dependa del filtro</b> — y que una columna con cero se
   * distinga de una columna que desapareció.
   *
   * <p><b>El orden es el del catálogo</b>, que ya viene por nombre con la intercalación de la
   * columna. Reordenar aquí sería una segunda copia de ese criterio.
   */
  private Summary resumen(List<BrokerStatusCount> conteos) {
    // Reutiliza el catálogo de `RF-SP-052` en vez de un `LEFT JOIN` contra la
    // consulta filtrada: aquello obligaría a meter el predicado y la recursiva
    // dentro de una unión externa, y son tres brokers.
    List<BrokerItem> catalogo = brokers.findAll(true);

    Map<UUID, Long> todas = enCero(catalogo);
    Map<UUID, Long> pendientes = enCero(catalogo);
    Map<UUID, Long> depositadas = enCero(catalogo);

    for (BrokerStatusCount fila : conteos) {
      todas.merge(fila.brokerId(), fila.total(), Long::sum);
      Map<UUID, Long> delEstado =
          fila.status() == UserBrokerStatus.FIRST_DEPOSIT ? depositadas : pendientes;
      delEstado.merge(fila.brokerId(), fila.total(), Long::sum);
    }

    return new Summary(
        totales(catalogo, todas), totales(catalogo, pendientes), totales(catalogo, depositadas));
  }

  /**
   * Todos los brokers del catálogo, cada uno a cero.
   *
   * <p>Partir de aquí —y no de lo que devolvió la consulta— es lo único que garantiza que estén
   * todos: sumar encima nunca añade un broker que no estuviera.
   */
  private static Map<UUID, Long> enCero(List<BrokerItem> catalogo) {
    Map<UUID, Long> vacio = new LinkedHashMap<>();
    catalogo.forEach(broker -> vacio.put(broker.id(), 0L));
    return vacio;
  }

  private static Totals totales(List<BrokerItem> catalogo, Map<UUID, Long> conteos) {
    List<ByBroker> desglose = new ArrayList<>(catalogo.size());
    long total = 0;
    for (BrokerItem broker : catalogo) {
      long cuantas = conteos.getOrDefault(broker.id(), 0L);
      desglose.add(new ByBroker(new BrokerRef(broker.id(), broker.name()), cuantas));
      total += cuantas;
    }
    return new Totals(total, List.copyOf(desglose));
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
