package com.factech.nexus.modules.system.roles.domain.service;

import com.factech.nexus.modules.system.roles.application.ListRolesRequest;
import com.factech.nexus.modules.system.roles.application.RoleListItem;
import com.factech.nexus.modules.system.roles.application.RoleSummaryResponse;
import com.factech.nexus.modules.system.roles.domain.models.RoleSortField;
import com.factech.nexus.modules.system.roles.domain.models.RoleStatus;
import com.factech.nexus.modules.system.roles.domain.models.RoleType;
import com.factech.nexus.modules.system.roles.domain.repository.RoleQueryRepository;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listado de roles (`RF-SP-002`).
 *
 * <p><b>Dos sentencias como máximo, y ninguna depende del número de filas</b>: la página con su rol
 * padre resuelto en la misma pasada, y el conteo — que además se omite cuando el resultado lo hace
 * deducible. La alternativa, cargar la entidad y navegar a su padre, produce veintiuna consultas
 * para una página de veinte.
 *
 * <p><b>No hay {@code 404} ni {@code 422}.</b> Un filtro sin coincidencias devuelve {@code 200} con
 * la colección vacía (`CA-SP-013`) y una página más allá de la última hace lo mismo: obligar al
 * cliente a distinguir «no hay» de «falló» sería pedirle que trate como error la respuesta útil.
 *
 * <p><b>Sin alcance por actor.</b> `spec.md` §5 lo dice de forma explícita: quien tiene el permiso
 * ve todos los roles. La consulta no recibe al actor ni lo usa en el predicado — y por eso este
 * endpoint es de los primeros que habrá que revisar el día que se resuelva <b>D-22</b>, porque hoy
 * no tiene ningún punto donde insertar esa restricción.
 */
@Service
public class ListRolesService {

  private final RoleQueryRepository consultas;
  private final Pagination paginacion;

  public ListRolesService(RoleQueryRepository consultas, Pagination paginacion) {
    this.consultas = consultas;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public PageResponse<RoleListItem> list(ListRolesRequest filtros) {
    Verificacion verificada = verificar(filtros);

    List<RoleQueryRepository.RoleRow> filas =
        consultas.search(
            filtros, verificada.orden(), verificada.trozo().offset(), verificada.trozo().size());

    long total = totalDe(filtros, filas, verificada.trozo());

    return PageResponse.de(
        filas.stream().map(ListRolesService::item).toList(),
        total,
        verificada.trozo().page(),
        verificada.trozo().size());
  }

  /**
   * Los cuatro {@code 400} se evalúan <b>juntos</b> y se devuelven juntos.
   *
   * <p>Son independientes entre sí, y devolverlos de a uno obliga a corregir la URL parámetro por
   * parámetro: cuatro vueltas para un cliente que escribió mal cuatro cosas. Por eso cada
   * comprobación <b>acumula</b> en lugar de interrumpir, incluida la de paginación —que vive en el
   * componente compartido y lanza por su cuenta, de modo que aquí se captura su detalle en vez de
   * dejarlo salir.
   */
  private Verificacion verificar(ListRolesRequest filtros) {
    List<FieldError> problemas = new ArrayList<>();

    Pagination.Slice trozo = null;
    try {
      trozo = paginacion.resolver(filtros.page(), filtros.size());
    } catch (ValidationException paginacionInvalida) {
      problemas.addAll(paginacionInvalida.errors());
    }

    String orden = null;
    try {
      orden = RoleSortField.resolver(filtros.sort());
    } catch (ValidationException ordenInvalido) {
      problemas.addAll(ordenInvalido.errors());
    }

    verificarDominio(
            "status", filtros.status(), Arrays.stream(RoleStatus.values()).map(Enum::name).toList())
        .ifPresent(problemas::add);
    verificarDominio(
            "roleType",
            filtros.roleType(),
            Arrays.stream(RoleType.values()).map(Enum::name).toList())
        .ifPresent(problemas::add);

    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(), "La consulta solicitada no es válida.", problemas);
    }
    return new Verificacion(trozo, orden);
  }

  /**
   * {@code VAL-004}. El valor se compara <b>sin distinguir caja</b> y el mensaje enumera los
   * admitidos: un filtro rechazado sin decir qué se admite obliga a buscar el dominio en la
   * documentación.
   */
  private static java.util.Optional<FieldError> verificarDominio(
      String campo, String valor, List<String> admitidos) {

    if (valor == null || admitidos.stream().anyMatch(valido -> valido.equalsIgnoreCase(valor))) {
      return java.util.Optional.empty();
    }
    String mensaje = "El valor '" + valor + "' no es válido. Valores admitidos: " + admitidos + ".";
    return java.util.Optional.of(new FieldError(campo, "VAL-004", mensaje));
  }

  /**
   * El conteo se <b>omite cuando la página no se llena</b>, y el total sigue siendo exacto: si una
   * página devuelve menos filas de las que caben, no hay más detrás, de modo que el total es el
   * desplazamiento más lo devuelto. Es un {@code COUNT(*)} ahorrado en el caso más frecuente —el
   * catálogo de roles sin filtros— sin perder ninguna garantía.
   *
   * <p><b>Esta estrategia no debe heredarse sin revisarla</b> (`plan.md` §4 y §8): sobre {@code
   * roles}, que se mide en decenas, un conteo exacto es un recorrido de milisegundos. Sobre los
   * registros de auditoría de `RF-SP-011` a `RF-SP-014`, que crecen sin límite, sería un recorrido
   * completo de la tabla en cada petición.
   */
  private long totalDe(
      ListRolesRequest filtros, List<RoleQueryRepository.RoleRow> filas, Pagination.Slice trozo) {

    // La primera página que no se llena ES el total: no hay nada antes ni
    // detrás de ella.
    if (trozo.offset() == 0 && filas.size() < trozo.size()) {
      return filas.size();
    }
    // Una página intermedia que no se llena es la última: lo que hay delante
    // son páginas completas, de modo que el total es el desplazamiento más lo
    // devuelto.
    //
    // LA PÁGINA VACÍA NO ENTRA AQUÍ, y esa es la distinción que importa: pedir
    // la página 99 de un catálogo de doce roles devuelve cero filas, y aplicarle
    // el atajo daría un `totalElements` de 1980 —el desplazamiento— en lugar de
    // 12. El cliente vería un total inventado, con la colección vacía y sin
    // ningún error que lo delate. Cuando no viene nada, hay que contar.
    if (!filas.isEmpty() && filas.size() < trozo.size()) {
      return (long) trozo.offset() + filas.size();
    }
    return consultas.count(filtros);
  }

  private static RoleListItem item(RoleQueryRepository.RoleRow fila) {
    return new RoleListItem(
        fila.id(),
        fila.code(),
        fila.name(),
        fila.description(),
        fila.roleType(),
        fila.status(),
        fila.isSystem(),
        fila.tienePadre()
            ? new RoleSummaryResponse(fila.parentId(), fila.parentCode(), fila.parentName())
            : null,
        fila.deletedAt());
  }

  /** Parámetros ya validados: el trozo de página y la cláusula de ordenamiento. */
  private record Verificacion(Pagination.Slice trozo, String orden) {}
}
