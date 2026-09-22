package com.factech.nexus.modules.system.teams.domain.service;

import com.factech.nexus.modules.system.teams.application.ListTeamsRequest;
import com.factech.nexus.modules.system.teams.application.TeamItem;
import com.factech.nexus.modules.system.teams.application.TeamSortField;
import com.factech.nexus.modules.system.teams.domain.models.TeamStatus;
import com.factech.nexus.modules.system.teams.domain.repository.TeamQueryRepository;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.BoundedCount;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-SP-064`: el catálogo de administración de equipos.
 *
 * <p><b>Dos sentencias fijas</b> (`CA-SP-746`): la página —con el recuento de miembros vigentes
 * como columna más, resuelto por subconsulta correlacionada— y el total. Ni una consulta por fila:
 * ese es el `N+1` que el criterio existe para impedir, y por eso una página de uno y una de veinte
 * cuestan lo mismo.
 *
 * <p><b>Los cuatro `400` se devuelven juntos</b> (`EX-001`): paginación, estado, orden y la forma
 * de {@code includeDeleted}. Quien envía tres parámetros mal tiene que poder corregirlos de una
 * vez, y por eso ninguno de los cuatro interrumpe la evaluación de los demás.
 *
 * <p><b>El total es exacto</b> ({@link BoundedCount#exacto}): los equipos de una empresa se cuentan
 * en decenas, y el techo de `RF-SP-011` existe para tablas que crecen sin cota.
 *
 * <p><b>No pregunta quién es el actor.</b> Quien porta {@code teams:list} ve todos los equipos; un
 * manager no ve «el suyo» por esta vía, y si algún día debe verlo será un requerimiento propio con
 * su permiso de alcance (`RN-SEG-015`), no un filtro implícito aquí.
 */
@Service
public class ListTeamsService {

  private final TeamQueryRepository consultas;
  private final Pagination paginacion;

  public ListTeamsService(TeamQueryRepository consultas, Pagination paginacion) {
    this.consultas = consultas;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public PageResponse<TeamItem> list(ListTeamsRequest filtros) {
    List<FieldError> problemas = new ArrayList<>();
    Pagination.Slice trozo = resolverPaginacion(filtros, problemas);
    TeamSortField.Orden orden = resolverOrden(filtros, problemas);
    verificarEstado(filtros, problemas);
    verificarIncluirEliminados(filtros, problemas);
    if (!problemas.isEmpty()) {
      throw new ValidationException(problemas.get(0).code(), resumen(problemas), problemas);
    }

    List<TeamItem> pagina =
        consultas.search(filtros, orden.sql(), trozo.offset(), trozo.size()).stream()
            .map(TeamItem::from)
            .toList();

    return PageResponse.de(
        pagina, BoundedCount.exacto(consultas.count(filtros)), trozo.page(), trozo.size());
  }

  private Pagination.Slice resolverPaginacion(
      ListTeamsRequest filtros, List<FieldError> problemas) {
    try {
      return paginacion.resolver(filtros.page(), filtros.size());
    } catch (ValidationException fallo) {
      fallo.errors().stream()
          .map(error -> new FieldError(error.field(), "VAL-001", error.message()))
          .forEach(problemas::add);
      return new Pagination.Slice(0, 1);
    }
  }

  private static TeamSortField.Orden resolverOrden(
      ListTeamsRequest filtros, List<FieldError> problemas) {
    try {
      return TeamSortField.resolver(filtros.sort());
    } catch (ValidationException fallo) {
      problemas.addAll(fallo.errors());
      return TeamSortField.resolver(null);
    }
  }

  /**
   * `VAL-002`: el estado se admite en cualquier caja y solo puede ser uno de los dos declarados.
   */
  private static void verificarEstado(ListTeamsRequest filtros, List<FieldError> problemas) {
    String estado = filtros.estadoNormalizado();
    if (estado == null) {
      return;
    }
    boolean valido =
        Arrays.stream(TeamStatus.values()).anyMatch(valor -> valor.name().equals(estado));
    if (!valido) {
      String mensaje =
          "El estado indicado no es válido: '"
              + filtros.status()
              + "'. Valores admitidos: "
              + String.join(", ", Arrays.stream(TeamStatus.values()).map(Enum::name).toList())
              + ".";
      problemas.add(new FieldError("status", "VAL-002", mensaje));
    }
  }

  /**
   * `VAL-004`: la forma de {@code includeDeleted} se comprueba <b>aquí</b> y no en el convertidor
   * de Spring, para que viaje junto a los otros tres problemas de la misma petición.
   */
  private static void verificarIncluirEliminados(
      ListTeamsRequest filtros, List<FieldError> problemas) {
    if (filtros.incluirEliminadosMalEscrito()) {
      String mensaje = "El parámetro includeDeleted debe ser true o false.";
      problemas.add(new FieldError("includeDeleted", "VAL-004", mensaje));
    }
  }

  private static String resumen(List<FieldError> problemas) {
    return problemas.size() == 1
        ? problemas.get(0).message()
        : "La consulta trae " + problemas.size() + " parámetros inválidos.";
  }
}
