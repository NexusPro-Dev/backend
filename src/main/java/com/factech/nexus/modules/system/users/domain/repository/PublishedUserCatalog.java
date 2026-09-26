package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.modules.system.users.application.ClientCatalog;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup;
import com.factech.nexus.modules.system.users.application.PublicSellerLookup;
import com.factech.nexus.modules.system.users.application.SellerRoleCatalog;
import com.factech.nexus.modules.system.users.application.UserCatalog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    implements UserCatalog,
        SellerRoleCatalog,
        CurrentMembershipLookup,
        ClientCatalog,
        PublicSellerLookup {

  private final EntityManager em;
  private final UserRepository usuarios;
  private final ClientSellerRepository vinculos;
  private final Clock reloj;

  @Autowired
  public PublishedUserCatalog(
      EntityManager em, UserRepository usuarios, ClientSellerRepository vinculos) {
    this(em, usuarios, vinculos, Clock.systemUTC());
  }

  PublishedUserCatalog(
      EntityManager em, UserRepository usuarios, ClientSellerRepository vinculos, Clock reloj) {
    this.em = em;
    this.usuarios = usuarios;
    this.vinculos = vinculos;
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
   * El lote, con la misma proyección que {@link #find} y en una sola consulta (`RF-SP-069`).
   *
   * <p>Las que no existen <b>no salen</b>, y las eliminadas sí, con su marca: quien pregunta por un
   * lote decide qué hacer con cada caso, y aquí se le da el dato en lugar de la decisión.
   */
  @Override
  @Transactional(readOnly = true)
  public List<UserView> findAll(Set<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT u.id AS id, u.username AS username, u.first_name AS nombre,
                       u.last_name AS apellido, u.deleted_at AS deleted_at
                  FROM users u
                 WHERE u.id IN (:ids)
                """,
                Tuple.class)
            .setParameter("ids", ids)
            .getResultList();

    return filas.stream()
        .map(
            fila ->
                new UserView(
                    (UUID) fila.get("id"),
                    (String) fila.get("username"),
                    nombreCompleto((String) fila.get("nombre"), (String) fila.get("apellido")),
                    fila.get("deleted_at") != null))
        .toList();
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
                    membresia.level(),
                    membresia.color()));
  }

  // ---------------------------------------------------------------------------
  // PublicSellerLookup (`RF-PM-008` · `T-01`)
  // ---------------------------------------------------------------------------

  /**
   * El nombre de quien reparte un enlace, <b>solo si es fuerza comercial</b>.
   *
   * <p><b>Los tres rechazos son el mismo vacío</b> —no existe, no está activa, no es vendedora— y
   * eso es deliberado: quien consume no puede distinguirlos, de modo que el `404` que `PM` devuelve
   * no puede filtrarse por accidente en tres respuestas distintas.
   *
   * <p><b>La comparación del nombre de usuario ignora la caja</b>, como el correo en `RF-SP-024`:
   * un enlace se teclea y se comparte por mensajería.
   *
   * <p><b>La consulta selecciona DOS columnas</b>, y no la fila entera. Lo que no cruza la frontera
   * no se puede publicar por descuido al otro lado — es la misma disciplina con la que {@code
   * currentMembershipOf} descarta la fecha de fin.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<PublicSellerView> findSellerByUsername(String username) {
    if (username == null || username.isBlank()) {
      return Optional.empty();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT u.id AS id, u.first_name AS first_name, u.last_name AS last_name
                  FROM users u
                 WHERE lower(u.username) = lower(:usuario)
                   AND u.status = 'ACTIVO'
                   AND u.deleted_at IS NULL
                   AND EXISTS (
                         SELECT 1
                           FROM user_roles ur
                          WHERE ur.user_id = u.id
                            AND ur.role_type = 'VENDEDOR')
                 LIMIT 1
                """,
                Tuple.class)
            .setParameter("usuario", username)
            .getResultList();

    return filas.stream()
        .findFirst()
        .map(
            fila ->
                new PublicSellerView(
                    (UUID) fila.get("id"),
                    (String) fila.get("first_name"),
                    (String) fila.get("last_name")));
  }

  /**
   * El identificador del mismo vendedor que {@link #findSellerByUsername} resuelve.
   *
   * <p><b>El predicado se repite y no se comparte</b>, y conviene decir por qué: extraerlo a una
   * constante de texto ahorraría cinco líneas y dejaría dos consultas atadas a la misma cadena, que
   * es peor de leer que dos consultas explícitas. Lo que <b>no</b> se repite es la regla — vive en
   * este adaptador y solo en él.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> sellerIdByUsername(String username) {
    if (username == null || username.isBlank()) {
      return Optional.empty();
    }
    List<UUID> filas =
        em.createNativeQuery(
                """
                SELECT u.id
                  FROM users u
                 WHERE lower(u.username) = lower(:usuario)
                   AND u.status = 'ACTIVO'
                   AND u.deleted_at IS NULL
                   AND EXISTS (
                         SELECT 1
                           FROM user_roles ur
                          WHERE ur.user_id = u.id
                            AND ur.role_type = 'VENDEDOR')
                """,
                UUID.class)
            .setParameter("usuario", username.trim())
            .getResultList();

    return filas.stream().findFirst();
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
   * El vendedor de quien compra (`RN-MV-003`).
   *
   * <p><b>Primero {@code client_sellers}, después {@code user_supervisors}</b>, y el orden es la
   * regla. Desde el 18-09-2026 (`RN-SP-028` revertida, `RF-SP-059`) el cliente <b>no cuelga</b> de
   * la estructura de mando: su vendedor es su <b>principal</b>, la fila {@code REGISTRO} de {@code
   * client_sellers}. La tabla de mando sigue valiendo para <b>un vendedor que compra</b>, cuyo
   * vendedor es su superior vigente. Y quien no tiene ni lo uno ni lo otro se vende a sí mismo
   * (`CA-MV-017`), que se decide en `MV` y no aquí.
   *
   * <p>Si una persona tuviera las dos —un cliente ascendido a agente— <b>manda el {@code
   * REGISTRO}</b>: le vendieron antes de que vendiera (`RF-SP-059` spec §13).
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
    Optional<SellerView> principal =
        vinculos
            .findPrincipalOf(id)
            .map(
                fila ->
                    new SellerView(
                        fila.sellerId(), fila.username(), fila.firstName(), fila.lastName()));
    if (principal.isPresent()) {
      return principal;
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

  /**
   * Todos los vínculos del cliente, en el orden de {@link ClientSellerRepository#findSellersOf}:
   * principal primero. `RN-MV-034` solo los cuenta, y `RN-MV-035` elige entre ellos.
   */
  @Override
  @Transactional(readOnly = true)
  public List<SellerView> sellersOf(UUID id) {
    if (id == null) {
      return List.of();
    }
    return vinculos.findSellersOf(id).stream()
        .map(
            fila ->
                new SellerView(fila.sellerId(), fila.username(), fila.firstName(), fila.lastName()))
        .toList();
  }

  /** Nombre y apellido, o nulo si no hay ninguno de los dos. */
  private static String nombreCompleto(String nombre, String apellido) {
    String completo =
        ((nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido)).trim();
    return completo.isEmpty() ? null : completo;
  }
}
