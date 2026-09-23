package com.factech.nexus.modules.system.teams.domain.service;

import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.repository.RoleCatalog;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * El catálogo de roles con memoria, <b>del alcance de una sola operación</b>.
 *
 * <p><b>Existe por un `N+1` que no está donde se busca.</b> {@link
 * com.factech.nexus.modules.system.users.domain.security.CommercialStructure#esCuspide} pregunta
 * por el <b>rol padre</b> para decidir si un rol es la cúspide, y esa pregunta es una consulta.
 * Resolver a las personas y a sus roles en bloque —que es lo que hace {@code
 * AssignTeamMembersService}— no evita nada si después se llama a la regla cien veces: son cien
 * lecturas del mismo puñado de roles. La prueba del lote de cien lo vio: ciento siete consultas en
 * lugar de ocho.
 *
 * <p><b>Se memoriza en lugar de duplicar la regla</b>, que es la alternativa que parece más simple
 * y no lo es: comparar el rol con el código {@code MANAGER} evitaría la consulta y ataría
 * `RN-SP-051` al catálogo de hoy, de modo que un rango nuevo por encima exigiría tocar código en
 * dos sitios. Aquí la regla sigue siendo la de siempre y lo único que cambia es cuántas veces se
 * lee lo mismo.
 *
 * <p><b>Del alcance de la operación y no un caché de verdad</b>: se crea por petición y se tira con
 * ella. Un caché con vida propia tendría que decidir cuándo invalidarse —qué pasa si a mitad de la
 * transacción alguien reordena la jerarquía de roles—, y esa pregunta no tiene respuesta buena.
 * Dentro de una transacción la jerarquía no cambia, y eso es todo lo que esta clase asume.
 */
final class MemoizingRoleCatalog implements RoleCatalog {

  private final RoleCatalog delegado;
  private final Map<UUID, Optional<AssignableRole>> porId = new HashMap<>();

  MemoizingRoleCatalog(RoleCatalog delegado) {
    this.delegado = delegado;
  }

  /** La única lectura que se repite, y la única que se memoriza. */
  @Override
  public Optional<AssignableRole> findById(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return porId.computeIfAbsent(id, delegado::findById);
  }

  @Override
  public List<AssignableRole> findAllById(Set<UUID> ids) {
    List<AssignableRole> encontrados = delegado.findAllById(ids);
    // Lo que ya se trajo en bloque no se vuelve a pedir de uno en uno.
    encontrados.forEach(rol -> porId.put(rol.id(), Optional.of(rol)));
    return encontrados;
  }

  @Override
  public Optional<AssignableRole> findByCode(String code) {
    return delegado.findByCode(code);
  }

  @Override
  public Set<UUID> roleIdsOf(UUID userId) {
    return delegado.roleIdsOf(userId);
  }

  @Override
  public Map<UUID, Set<UUID>> roleIdsOfAll(Set<UUID> userIds) {
    return delegado.roleIdsOfAll(userIds);
  }
}
