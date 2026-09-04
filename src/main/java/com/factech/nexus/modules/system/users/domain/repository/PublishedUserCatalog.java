package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.modules.system.users.application.ClientCatalog;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup;
import com.factech.nexus.modules.system.users.application.SellerRoleCatalog;
import com.factech.nexus.modules.system.users.application.UserCatalog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de las tres interfaces que este submódulo <b>publica</b> hacia otros módulos
 * (**D-25**).
 *
 * <p><b>Cuatro interfaces y un solo adaptador</b>, y eso no contradice a «una interfaz por
 * lectura»: lo que §15.2 separa son los <b>contratos</b>, para que añadir un método a uno no cambie
 * el del otro. Quién los implementa es una decisión interna de `SP`, y las cuatro lecturas salen de
 * las mismas tablas.
 *
 * <p>Proyecta a los modelos de lectura aquí dentro: si {@code User} saliera de estos métodos, el
 * otro módulo tendría una entidad JPA viva en las manos.
 */
@Repository
public class PublishedUserCatalog
    implements UserCatalog, SellerRoleCatalog, CurrentMembershipLookup, ClientCatalog {

  private final EntityManager em;
  private final UserRepository usuarios;
  private final Clock reloj;

  @Autowired
  public PublishedUserCatalog(EntityManager em, UserRepository usuarios) {
    this(em, usuarios, Clock.systemUTC());
  }

  PublishedUserCatalog(EntityManager em, UserRepository usuarios, Clock reloj) {
    this.em = em;
    this.usuarios = usuarios;
    this.reloj = reloj;
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

  /**
   * La membresía vigente de la persona (`RF-PM-007` · `T-01`).
   *
   * <p><b>No escribe ni un {@code WHERE} de vigencia</b>, y eso es lo único importante de este
   * método. Reutiliza {@link UserRepository#findMembership} —la misma lectura que usan `RF-SP-026`,
   * `RF-SP-031` y `RF-SP-032`— y decide con {@link UserMembership#isCurrentAt}, que es donde vive
   * la <b>única</b> definición de «vigente» del sistema y donde está fijado su borde por prueba:
   * una fecha igual al instante consultado ya <b>no</b> está vigente.
   *
   * <p>Una consulta propia aquí sería una cuarta copia de esa comparación. Copiada bien, no aporta
   * nada; copiada mal, devuelve un nivel que ya expiró y `PM` ofrece upgrades desde un peldaño en
   * el que la persona ya no está — sin que nada falle.
   *
   * <p><b>La proyección descarta la fecha de fin</b> al cruzar la frontera: es el dato con el que
   * `PM` podría rehacer la decisión que aquí se toma.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<CurrentMembershipView> currentMembershipOf(UUID userId) {
    if (userId == null) {
      return Optional.empty();
    }
    return usuarios
        .findMembership(userId)
        .filter(membresia -> membresia.isCurrentAt(OffsetDateTime.now(reloj)))
        .map(
            membresia ->
                new CurrentMembershipView(
                    membresia.membershipId(),
                    membresia.code(),
                    membresia.name(),
                    membresia.level()));
  }

  // ---------------------------------------------------------------------------
  // ClientCatalog (`RF-MV-001` · `T-07`)
  // ---------------------------------------------------------------------------

  /**
   * El cliente a nombre de quien se vende.
   *
   * <p><b>Reutiliza {@code findNotDeletedById} y no escribe un {@code SELECT} propio</b>, que es
   * exactamente lo que hace {@link #currentMembershipOf} con {@code findMembership}: la definición
   * de «existe» —no eliminada, sin exigir estado— vive en el repositorio y no se reimplementa por
   * cada consumidor.
   *
   * <p><b>El estado se proyecta a cadena aquí</b>, y es lo único que este método traduce. Devolver
   * el enumerado dejaría a `MV` recompilando cada vez que `SP` estrene un valor.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<ClientView> findClient(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return usuarios
        .findNotDeletedById(id)
        .map(
            persona ->
                new ClientView(
                    persona.getId(),
                    persona.getUsername(),
                    persona.getFirstName(),
                    persona.getLastName(),
                    persona.getStatus().name()));
  }

  /**
   * El vendedor del que cuelga el cliente.
   *
   * <p><b>Se descarta {@code roleCode} y {@code since} al proyectar</b>, y no por omisión: el
   * primero solo dice si el superior porta un rol `VENDEDOR` —lo comprueba `RN-SP-020` al colgarlo,
   * y volver a exigirlo al vender convertiría una estructura mal formada en una venta rechazada— y
   * el segundo responde «desde cuándo reporta», que a una venta no le concierne. Lo que cruza la
   * frontera es lo que se va a congelar y lo que se va a devolver resuelto, y nada más.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<SellerView> sellerOf(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return usuarios
        .findActiveSupervisor(id)
        .map(
            superior ->
                new SellerView(
                    superior.supervisorId(),
                    superior.username(),
                    superior.firstName(),
                    superior.lastName()));
  }

  /** Nombre y apellido, o nulo si no hay ninguno de los dos. */
  private static String nombreCompleto(String nombre, String apellido) {
    String completo =
        ((nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido)).trim();
    return completo.isEmpty() ? null : completo;
  }
}
