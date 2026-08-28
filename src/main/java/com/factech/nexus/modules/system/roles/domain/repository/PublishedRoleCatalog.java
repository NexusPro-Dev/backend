package com.factech.nexus.modules.system.roles.domain.repository;

import com.factech.nexus.modules.system.roles.application.RoleCatalog;
import com.factech.nexus.modules.system.roles.domain.models.Role;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de la interfaz que este submódulo <b>publica</b> hacia otros módulos (**D-25**).
 *
 * <p>Se llama {@code Published…} y no {@code Jpa…} por el mismo motivo que {@code
 * PublishedMembershipCatalog}: `SP` tiene ya repositorios internos de roles con otro alcance, y
 * este es el contrato que <b>cruza la frontera del módulo</b>.
 *
 * <p><b>Proyecta a {@code RoleView} aquí dentro</b> y no devuelve la entidad gestionada: si {@code
 * Role} saliera de este método, el otro módulo tendría una entidad JPA viva en las manos y la
 * frontera sería una convención.
 *
 * <p><b>No filtra por eliminado.</b> Devuelve el rol con su marca y deja que el consumidor decida:
 * `CM` rechaza declarar una tarifa sobre un rol que no existe, y necesita distinguir eso de uno que
 * fue eliminado.
 */
@Repository
public class PublishedRoleCatalog implements RoleCatalog {

  private final EntityManager em;

  public PublishedRoleCatalog(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RoleView> find(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery("SELECT r FROM Role r WHERE r.id = :id", Role.class)
        .setParameter("id", id)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst()
        .map(
            rol ->
                new RoleView(
                    rol.getId(),
                    rol.getCode().value(),
                    rol.getName(),
                    rol.getRoleType().name(),
                    rol.getDeletedAt() != null));
  }
}
