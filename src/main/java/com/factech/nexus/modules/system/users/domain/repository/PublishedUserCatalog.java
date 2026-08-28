package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.modules.system.users.application.SellerRoleCatalog;
import com.factech.nexus.modules.system.users.application.UserCatalog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de las dos interfaces que este submódulo <b>publica</b> hacia otros módulos (**D-25**).
 *
 * <p><b>Dos interfaces y un solo adaptador</b>, y eso no contradice a «una interfaz por lectura»:
 * lo que §15.2 separa son los <b>contratos</b>, para que añadir un método a uno no cambie el del
 * otro. Quién los implementa es una decisión interna de `SP`, y las dos lecturas salen de las
 * mismas dos tablas.
 *
 * <p>Proyecta a los modelos de lectura aquí dentro: si {@code User} saliera de estos métodos, el
 * otro módulo tendría una entidad JPA viva en las manos.
 */
@Repository
public class PublishedUserCatalog implements UserCatalog, SellerRoleCatalog {

  private final EntityManager em;

  public PublishedUserCatalog(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserView> find(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT u.username AS username, u.first_name AS nombre,
                       u.last_name AS apellido, u.deleted_at AS deleted_at
                  FROM users u
                 WHERE u.id = :id
                """,
                Tuple.class)
            .setParameter("id", id)
            .getResultList();

    return filas.stream()
        .findFirst()
        .map(
            fila ->
                new UserView(
                    id,
                    (String) fila.get("username"),
                    nombreCompleto((String) fila.get("nombre"), (String) fila.get("apellido")),
                    fila.get("deleted_at") != null));
  }

  /**
   * El rol vendedor de la persona.
   *
   * <p><b>Se consultan TODOS los que porta y no se pide uno con {@code LIMIT 1}</b>, que sería más
   * corto y escondería el caso que importa: con dos, este método tiene que enterarse para poder
   * fallar. Un {@code LIMIT 1} elegiría uno en silencio, que es exactamente lo que {@link
   * SellerRoleCatalog} declara que no debe ocurrir.
   *
   * <p>No filtra por rol eliminado ni por persona eliminada: quien pregunta por la comisión de
   * alguien dado de baja está reconstruyendo el pasado, y ahí el rol que portaba sigue siendo el
   * dato correcto.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> sellerRoleOf(UUID userId) {
    if (userId == null) {
      return Optional.empty();
    }
    @SuppressWarnings("unchecked")
    List<UUID> vendedores =
        em.createNativeQuery(
                """
                SELECT r.id
                  FROM user_roles ur
                  JOIN roles r ON r.id = ur.role_id
                 WHERE ur.user_id = :id
                   AND r.role_type = 'VENDEDOR'
                """)
            .setParameter("id", userId)
            .getResultList();

    if (vendedores.size() > 1) {
      throw new AmbiguousSellerRoleException(userId);
    }
    return vendedores.stream().findFirst();
  }

  /** Nombre y apellido, o nulo si no hay ninguno de los dos. */
  private static String nombreCompleto(String nombre, String apellido) {
    String completo =
        ((nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido)).trim();
    return completo.isEmpty() ? null : completo;
  }
}
