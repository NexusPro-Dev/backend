package com.factech.nexus.modules.system.memberships.domain.repository;

import com.factech.nexus.modules.system.memberships.application.MembershipCatalog;
import com.factech.nexus.modules.system.memberships.domain.models.Membership;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de la interfaz que este submódulo <b>publica</b> hacia otros módulos (**D-25**).
 *
 * <p><b>Se llama {@code Published…} y no {@code Jpa…} a propósito.</b> El submódulo de usuarios de
 * `SP` ya tiene su propio {@code MembershipCatalog} interno, con su adaptador {@code
 * JpaMembershipCatalog}, para lo que él necesita saber de una membresía al asignarla. Este es
 * <b>otro</b> contrato con otro alcance: el que cruza la frontera del módulo. El nombre lo dice
 * para que nadie los confunda al leer, y de paso evita el choque de nombre de bean que los dos
 * tendrían.
 *
 * <p><b>Que existan los dos está anotado como deuda</b>, no como diseño: son la misma lectura
 * declarada dos veces, y consolidarlos toca código de `SP` que este requerimiento no debía tocar.
 *
 * <p>Vive junto a la tabla que lee. El consumidor solo conoce la interfaz de la capa {@code
 * application}: esta clase no aparece en ninguno de sus {@code import}, y la regla de ArchUnit de
 * D-25 lo comprueba.
 *
 * <p><b>Proyecta a {@code MembershipView} aquí dentro</b> y no devuelve la entidad gestionada: si
 * {@code Membership} saliera de este método, el otro módulo tendría una entidad JPA viva en las
 * manos y la frontera sería una convención.
 */
@Repository
public class PublishedMembershipCatalog implements MembershipCatalog {

  private final EntityManager em;

  public PublishedMembershipCatalog(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<MembershipView> find(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery("SELECT m FROM Membership m WHERE m.id = :id", Membership.class)
        .setParameter("id", id)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst()
        .map(
            membresia ->
                new MembershipView(
                    membresia.getId(),
                    membresia.getCode(),
                    membresia.getName(),
                    membresia.getLevel()));
  }
}
