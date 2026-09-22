package com.factech.nexus.modules.system.memberships.domain.repository;

import com.factech.nexus.modules.system.memberships.domain.models.ChainLink;
import com.factech.nexus.modules.system.memberships.domain.models.Membership;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Adaptador JPA del puerto de escritura (`RF-SP-016`).
 *
 * <p><b>Escribe lo que el dominio decidió; no decide.</b> El {@code UPDATE} masivo de niveles es
 * una sola sentencia porque no hay ninguna regla que evaluar por fila: cargar cada entidad afectada
 * para modificarla produciría tantas sentencias como membresías haya sin aportar nada.
 */
@Repository
public class JpaMembershipRepository implements MembershipRepository {

  /**
   * Clave del bloqueo consultivo que serializa el registro de membresías.
   *
   * <p>Arbitraria y fija: identifica «la cadena de membresías» y nada más. Cualquier otro recurso
   * que necesite un bloqueo consultivo debe usar una clave distinta, y por eso vive declarada con
   * nombre en lugar de escrita en la sentencia.
   */
  private static final long CANDADO_DE_LA_CADENA = 716_016L;

  private static final String UQ_CODIGO = "uq_memberships_code";
  private static final String UQ_NOMBRE = "uq_memberships_name";
  private static final String UQ_COLOR = "uq_memberships_color";

  private final EntityManager em;

  public JpaMembershipRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public List<ChainLink> loadChainForUpdate() {
    // EL BLOQUEO CONSULTIVO VA ANTES, y no es redundante con el `FOR UPDATE` de
    // abajo: `FOR UPDATE` bloquea LAS FILAS QUE ENCUENTRA, y sobre una cadena
    // VACÍA no encuentra ninguna. Dos registros simultáneos de la primera
    // membresía no se serializaban en absoluto, y el desenlace dependía de qué
    // restricción mordiera antes — a veces un 409 correcto, a veces un
    // interbloqueo que sale como 500. La prueba concurrente falló así dos veces
    // en la suite completa y nunca en aislamiento, que es la firma de este
    // defecto.
    //
    // La clave es fija y arbitraria: identifica «la cadena de membresías» y nada
    // más. El bloqueo se suelta solo al terminar la transacción —`_xact_`— de
    // modo que no hay forma de olvidarse de liberarlo, y no cuesta nada cuando
    // no hay contención.
    em.createNativeQuery("SELECT pg_advisory_xact_lock(:clave)")
        .setParameter("clave", CANDADO_DE_LA_CADENA)
        .getSingleResult();

    // Nativa y no JPQL porque `FOR UPDATE` sobre una proyección no tiene
    // equivalente limpio en JPQL, y porque proyectar evita traer entidades
    // gestionadas que el UPDATE masivo posterior dejaría obsoletas en el
    // contexto de persistencia.
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT id, parent_membership_id, level
                  FROM memberships
                 ORDER BY level
                   FOR UPDATE
                """,
                Tuple.class)
            .getResultList();

    return filas.stream()
        .map(
            fila ->
                new ChainLink(
                    (UUID) fila.get("id"),
                    (UUID) fila.get("parent_membership_id"),
                    ((Number) fila.get("level")).intValue()))
        .toList();
  }

  @Override
  public boolean existsCode(String code) {
    return !em.createQuery("SELECT 1 FROM Membership m WHERE m.code = :code", Integer.class)
        .setParameter("code", code)
        .setMaxResults(1)
        .getResultList()
        .isEmpty();
  }

  /**
   * Compara sobre la forma normalizada, igual que {@code uq_memberships_name}.
   *
   * <p>Si esta consulta comparara el texto literal mientras el índice compara la forma normalizada,
   * el `409` legible de `EX-001` se convertiría en un fallo de integridad para el caso que más
   * importa: el nombre que solo difiere en mayúsculas o acentos (`CA-SP-348`).
   */
  @Override
  public boolean existsName(String name) {
    return !em.createNativeQuery(
            """
            SELECT 1 FROM memberships
             WHERE f_unaccent(lower(name)) = f_unaccent(lower(CAST(:name AS text)))
             LIMIT 1
            """)
        .setParameter("name", name)
        .getResultList()
        .isEmpty();
  }

  @Override
  public boolean existsColor(String color) {
    return !em.createQuery("SELECT 1 FROM Membership m WHERE m.color = :color", Integer.class)
        .setParameter("color", color)
        .setMaxResults(1)
        .getResultList()
        .isEmpty();
  }

  @Override
  public int shiftLevelsFrom(int nivel) {
    // `updated_at` no es decorativo aquí: el reordenamiento es lo único que
    // puede cambiar en una fila ya escrita (`RN-SP-008`).
    return em.createNativeQuery(
            """
            UPDATE memberships
               SET level = level + 1, updated_at = now()
             WHERE level >= :nivel
            """)
        .setParameter("nivel", nivel)
        .executeUpdate();
  }

  /**
   * Persiste y fuerza el volcado.
   *
   * <p>El {@code flush} explícito es lo que permite traducir el duplicado de código o de nombre a
   * un {@code 409} legible: sin él, la violación saltaría al confirmar, fuera de este método, y
   * llegaría al manejador global como un fallo no controlado.
   *
   * <p><b>Lo que este {@code flush} no puede atrapar</b> son las restricciones <b>diferidas</b>
   * —{@code uq_memberships_parent} y {@code uq_memberships_level}—, que por definición se evalúan
   * en el {@code COMMIT}. Esas las traduce {@code GlobalExceptionHandler} a `EX-003`.
   */
  @Override
  public Membership save(Membership membresia) {
    try {
      em.persist(membresia);
      em.flush();
      return membresia;
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public void reparent(UUID hija, UUID nuevaSuperior) {
    em.createNativeQuery(
            """
            UPDATE memberships
               SET parent_membership_id = :superior, updated_at = now()
             WHERE id = :hija
            """)
        .setParameter("superior", nuevaSuperior)
        .setParameter("hija", hija)
        .executeUpdate();
  }

  /**
   * Traduce el duplicado por <b>nombre de restricción</b>, nunca por el texto del mensaje del
   * driver, que cambia entre versiones.
   *
   * <p>`EX-001` no produce un {@code error_code} de regla, a diferencia de `RF-SP-001`: aquí no hay
   * ninguna regla {@code RN-…} de unicidad de membresía, de modo que el código es el de la
   * excepción de la especificación (`architecture.md` §7.3).
   */
  private static RuntimeException traducir(PersistenceException fallo) {
    String restriccion = nombreDeRestriccion(fallo);
    if (UQ_CODIGO.equals(restriccion)) {
      return new BusinessRuleException(
          "EX-001",
          "Ya existe una membresía con ese código.",
          List.of(new FieldError("code", "EX-001", "Ya existe una membresía con ese código.")));
    }
    if (UQ_NOMBRE.equals(restriccion)) {
      return new BusinessRuleException(
          "EX-001",
          "Ya existe una membresía con ese nombre.",
          List.of(new FieldError("name", "EX-001", "Ya existe una membresía con ese nombre.")));
    }
    if (UQ_COLOR.equals(restriccion)) {
      return new BusinessRuleException(
          "EX-001",
          "Ya existe una membresía con ese color.",
          List.of(new FieldError("color", "EX-001", "Ya existe una membresía con ese color.")));
    }
    return fallo;
  }

  private static String nombreDeRestriccion(Throwable fallo) {
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
      if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion) {
        return violacion.getConstraintName();
      }
    }
    return null;
  }
}
