package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.application.ListUsersRequest;
import com.factech.nexus.modules.system.users.application.UserListItem;
import com.factech.nexus.modules.system.users.application.UserResponse;
import com.factech.nexus.modules.system.users.domain.models.UserSortField;
import com.factech.nexus.modules.system.users.domain.models.UserStatus;
import com.factech.nexus.modules.system.users.domain.repository.UserQueryRepository;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listado de personas (`RF-SP-025`).
 *
 * <p><b>Tres sentencias como máximo, y ninguna depende del número de filas</b>: la página con su
 * membresía, los roles de <b>toda</b> la página en una sola pasada, y el conteo. La alternativa
 * —cargar la entidad y navegar a sus roles— produce veintiuna consultas para una página de veinte,
 * que es el patrón que la guía prohíbe por su nombre.
 *
 * <p><b>No hay {@code 404} ni {@code 422}.</b> Un filtro sin coincidencias devuelve {@code 200} con
 * la colección vacía, y una página más allá de la última hace lo mismo: preguntar por algo que no
 * está no es un error, es una respuesta.
 */
@Service
public class ListUsersService {

  private final UserQueryRepository consultas;
  private final Pagination paginacion;

  public ListUsersService(UserQueryRepository consultas, Pagination paginacion) {
    this.consultas = consultas;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public PageResponse<UserListItem> list(ListUsersRequest filtros) {
    // Los `400` se evalúan JUNTOS y se devuelven juntos: quien envía tres
    // parámetros mal tiene que poder corregirlos de una vez.
    Pagination.Slice trozo = paginacion.resolver(filtros.page(), filtros.size());
    verificarEstado(filtros.status());
    String orden = UserSortField.resolver(filtros.sort());

    List<UserQueryRepository.UserRow> filas =
        consultas.search(filtros, orden, trozo.offset(), trozo.size());

    Map<UUID, List<UserQueryRepository.RoleRow>> roles =
        consultas.rolesOf(filas.stream().map(UserQueryRepository.UserRow::id).toList());

    long total = totalDe(filtros, filas, trozo);

    return PageResponse.de(
        filas.stream().map(fila -> item(fila, roles)).toList(), total, trozo.page(), trozo.size());
  }

  /**
   * El conteo se <b>omite cuando la página no se llena</b>.
   *
   * <p>Y el total sigue siendo exacto: si una página devuelve menos filas de las que caben, no hay
   * más detrás, de modo que el total es el desplazamiento más lo devuelto. Es un {@code COUNT(*)}
   * ahorrado en el caso más frecuente —la mayoría de los filtros no llenan una página— sin perder
   * ninguna garantía.
   *
   * <p>`RF-SP-025` §10 deja anotado que {@code users} <b>sí crece sin límite</b>, al revés que
   * {@code roles}: el día que el conteo con un filtro poco selectivo sea un recorrido secuencial
   * por petición, es aquí donde se sustituye por una estimación — y {@code totalIsExact} ya está en
   * el contrato para decirlo.
   */
  private long totalDe(
      ListUsersRequest filtros, List<UserQueryRepository.UserRow> filas, Pagination.Slice trozo) {

    if (filas.size() < trozo.size()) {
      return (long) trozo.offset() + filas.size();
    }
    return consultas.count(filtros);
  }

  /**
   * `PENDIENTE` se admite aunque hoy ninguna fila lo tenga.
   *
   * <p>El estado está declarado en el esquema y sin usar. Excluirlo del dominio del filtro
   * obligaría a ampliarlo el día que exista el flujo de activación, y devolver la colección vacía
   * es la respuesta correcta mientras tanto.
   */
  private static void verificarEstado(String estado) {
    if (estado == null) {
      return;
    }
    boolean valido =
        java.util.Arrays.stream(UserStatus.values())
            .anyMatch(valor -> valor.name().equalsIgnoreCase(estado));

    if (!valido) {
      String mensaje =
          "El estado '"
              + estado
              + "' no existe. Valores admitidos: "
              + java.util.Arrays.stream(UserStatus.values()).map(Enum::name).toList()
              + ".";
      throw new ValidationException(
          "VAL-004", mensaje, List.of(new FieldError("status", "VAL-004", mensaje)));
    }
  }

  private static UserListItem item(
      UserQueryRepository.UserRow fila, Map<UUID, List<UserQueryRepository.RoleRow>> roles) {

    return new UserListItem(
        fila.id(),
        fila.username(),
        fila.email(),
        fila.firstName(),
        fila.lastName(),
        fila.status(),
        roles.getOrDefault(fila.id(), List.of()).stream()
            .map(rol -> new UserResponse.RoleRef(rol.id(), rol.code(), rol.name()))
            .toList(),
        fila.tieneMembresia()
            ? new UserListItem.MembershipRef(
                fila.membershipId(),
                fila.membershipCode(),
                fila.membershipName(),
                fila.membershipEndsAt(),
                Boolean.TRUE.equals(fila.membershipCurrent()))
            : null,
        fila.deletedAt());
  }
}
