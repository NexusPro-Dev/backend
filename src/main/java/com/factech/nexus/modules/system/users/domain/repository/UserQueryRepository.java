package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.modules.system.users.application.ListUsersRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lecturas del listado y del detalle de personas (`RF-SP-025`, `RF-SP-026`).
 *
 * <p>Puerto <b>separado</b> del de escritura, y no un método más de {@code UserRepository}: lo que
 * devuelve no son agregados sino proyecciones, y mezclarlos invitaría a cargar la entidad para
 * responder una consulta — que es justamente el camino al {@code N+1} que estos dos requerimientos
 * existen para evitar.
 */
public interface UserQueryRepository {

  /** Una página de personas, con su membresía resuelta en la misma sentencia. */
  List<UserRow> search(ListUsersRequest filtros, String ordenamiento, int offset, int limit);

  /** El conteo, con <b>el mismo predicado</b> que la página: se generan desde el mismo sitio. */
  long count(ListUsersRequest filtros);

  /**
   * Los roles de <b>toda la página</b>, en una sola pasada.
   *
   * <p>Recibe los identificadores ya leídos —veinte como mucho— y devuelve el mapa agrupado. Es la
   * forma de traer una colección por fila sin {@code N+1}: la alternativa —cargar la entidad y
   * navegar a sus roles— produce veintiuna consultas.
   *
   * <p>Incluye los roles <b>inactivos</b> y excluye los <b>eliminados</b>: un rol inactivo sigue
   * asignado y sigue siendo lo que explica por qué esa persona aparece al filtrar por él; uno
   * eliminado no existe.
   */
  Map<UUID, List<RoleRow>> rolesOf(List<UUID> userIds);

  /** La persona, su membresía y su contexto de acceso. Vacío si no existe o está eliminada. */
  Optional<UserRow> findDetail(UUID id);

  /** Proyección de una persona. {@code membershipCurrent} lo calcula la <b>base de datos</b>. */
  record UserRow(
      UUID id,
      String username,
      String email,
      String firstName,
      String lastName,
      String status,
      OffsetDateTime deletedAt,
      OffsetDateTime lastLoginAt,
      OffsetDateTime lockedUntil,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      UUID membershipId,
      String membershipCode,
      String membershipName,
      Short membershipLevel,
      OffsetDateTime membershipEndsAt,
      Boolean membershipCurrent) {

    public boolean tieneMembresia() {
      return membershipId != null;
    }
  }

  /** Proyección de un rol asignado, con su estado. */
  record RoleRow(UUID id, String code, String name, String status) {}
}
