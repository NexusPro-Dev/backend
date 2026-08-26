package com.factech.nexus.modules.system.auth.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de lectura y control de acceso sobre las cuentas. */
@Repository
public class JpaAuthUserRepository implements AuthUserRepository {

  private static final String PROYECCION =
      """
      SELECT u.id                   AS id,
             u.password_hash        AS password_hash,
             u.status               AS status,
             (u.deleted_at IS NOT NULL) AS deleted,
             u.failed_attempts      AS failed_attempts,
             u.locked_until         AS locked_until,
             u.provisional_password_expires_at AS provisional_expires_at,
             COALESCE(
               (SELECT string_agg(r.code, ',' ORDER BY r.code)
                  FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                 WHERE ur.user_id = u.id AND r.deleted_at IS NULL AND r.status = 'ACTIVO'),
               '')                  AS roles
        FROM users u
      """;

  private final EntityManager em;

  public JpaAuthUserRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AuthUser> findByIdentifier(String identificador) {
    if (identificador == null || identificador.isBlank()) {
      return Optional.empty();
    }
    // Se busca también entre las eliminadas: quien pregunta no debe poder
    // distinguir «no existe» de «fue eliminada» por la forma de la respuesta, y
    // eso se decide después, no filtrando aquí.
    return primero(
        PROYECCION
            + " WHERE lower(u.username) = lower(CAST(:valor AS text)) OR u.email = lower(btrim(CAST(:valor AS text)))",
        "valor",
        identificador.trim());
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AuthUser> findById(UUID id) {
    return id == null
        ? Optional.empty()
        : primero(PROYECCION + " WHERE u.id = :valor", "valor", id);
  }

  @Override
  public void registrarFallo(UUID userId, int intentos, OffsetDateTime bloquearHasta) {
    em.createNativeQuery(
            "UPDATE users SET failed_attempts = :intentos, locked_until = :hasta WHERE id = :id")
        .setParameter("intentos", intentos)
        .setParameter("hasta", bloquearHasta)
        .setParameter("id", userId)
        .executeUpdate();
  }

  /**
   * Limpia el contador y el bloqueo automático, y anota la entrada.
   *
   * <p><b>No toca {@code status}</b>: el bloqueo manual no se levanta entrando, porque no se
   * desbloquea solo — solo `RF-SP-028` lo retira.
   */
  @Override
  public void registrarEntrada(UUID userId, OffsetDateTime ahora) {
    em.createNativeQuery(
            """
            UPDATE users
               SET failed_attempts = 0, locked_until = NULL, last_login_at = :ahora
             WHERE id = :id
            """)
        .setParameter("ahora", ahora)
        .setParameter("id", userId)
        .executeUpdate();
  }

  @Override
  public void cambiarContrasena(UUID userId, String passwordHash, OffsetDateTime ahora) {
    em.createNativeQuery(
            """
            UPDATE users
               SET password_hash = :resumen,
                   must_change_password = false,
                   provisional_password_expires_at = NULL,
                   failed_attempts = 0,
                   locked_until = NULL,
                   updated_at = :ahora
             WHERE id = :id
            """)
        .setParameter("resumen", passwordHash)
        .setParameter("ahora", ahora)
        .setParameter("id", userId)
        .executeUpdate();
  }

  /**
   * Como {@code cambiarContrasena} <b>menos {@code locked_until}</b>, y esa omisión es la regla.
   *
   * <p>Ver {@link AuthUserRepository#recuperarContrasena}: recuperar prueba que se tiene el correo,
   * no que alguien decidiera devolver el acceso. {@code status} tampoco se toca.
   */
  @Override
  public void recuperarContrasena(UUID userId, String passwordHash, OffsetDateTime ahora) {
    em.createNativeQuery(
            """
            UPDATE users
               SET password_hash = :resumen,
                   must_change_password = false,
                   provisional_password_expires_at = NULL,
                   failed_attempts = 0,
                   updated_at = :ahora
             WHERE id = :id
            """)
        .setParameter("resumen", passwordHash)
        .setParameter("ahora", ahora)
        .setParameter("id", userId)
        .executeUpdate();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<String> correoDe(UUID userId) {
    if (userId == null) {
      return Optional.empty();
    }
    // Las eliminadas no tienen a quién escribirle: la cuenta ya no existe para
    // nadie, y su correo pudo haberlo tomado otra persona (`RF-SP-027`).
    List<?> filas =
        em.createNativeQuery("SELECT email FROM users WHERE id = :id AND deleted_at IS NULL")
            .setParameter("id", userId)
            .setMaxResults(1)
            .getResultList();

    return filas.stream().findFirst().map(String.class::cast);
  }

  private Optional<AuthUser> primero(String sql, String parametro, Object valor) {
    List<Tuple> filas =
        em.createNativeQuery(sql, Tuple.class)
            .setParameter(parametro, valor)
            .setMaxResults(1)
            .getResultList();

    return filas.stream()
        .map(
            fila ->
                new AuthUser(
                    (UUID) fila.get("id"),
                    (String) fila.get("password_hash"),
                    (String) fila.get("status"),
                    Boolean.TRUE.equals(fila.get("deleted")),
                    ((Number) fila.get("failed_attempts")).intValue(),
                    momento(fila.get("locked_until")),
                    momento(fila.get("provisional_expires_at")),
                    codigos((String) fila.get("roles"))))
        .findFirst();
  }

  /**
   * Convierte el instante que devuelve una consulta nativa.
   *
   * <p>El molde directo a {@link OffsetDateTime} <b>no vale</b>: para una columna {@code
   * timestamptz} leída por consulta nativa, Hibernate entrega un {@link Instant}, y el molde
   * revienta con {@code ClassCastException}. El defecto era invisible mientras {@code locked_until}
   * valiera siempre {@code null} —el molde de un nulo no falla—, de modo que solo aparecía <b>una
   * vez la cuenta estaba bloqueada</b>: justo en el camino que existe para protegerla, y con un
   * {@code 500} en lugar del {@code 423} que corresponde.
   *
   * <p>Se normaliza a UTC porque es lo que la columna almacena y lo que el resto del sistema
   * compara.
   */
  private static OffsetDateTime momento(Object valor) {
    return switch (valor) {
      case null -> null;
      case OffsetDateTime instante -> instante;
      case Instant instante -> instante.atOffset(ZoneOffset.UTC);
      case Timestamp marca -> marca.toInstant().atOffset(ZoneOffset.UTC);
      default ->
          throw new IllegalStateException(
              "Tipo temporal inesperado en la proyección: " + valor.getClass());
    };
  }

  private static List<String> codigos(String agregado) {
    if (agregado == null || agregado.isBlank()) {
      return List.of();
    }
    return List.of(agregado.split(","));
  }
}
