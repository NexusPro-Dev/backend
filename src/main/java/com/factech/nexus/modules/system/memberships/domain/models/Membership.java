package com.factech.nexus.modules.system.memberships.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un eslabón de la cadena de membresías (`RF-SP-016`).
 *
 * <p><b>Es a la vez agregado y modelo persistente</b>, por lo mismo que {@code Role}: `plan.md` §3
 * pedía separarlos con un {@code MembershipJpaMapper}, y `architecture.md` §5.1 —reescrita el
 * 22-08-2026— sitúa el modelo persistente en {@code domain/models}. No hay dos representaciones que
 * unir, de modo que el mapeador no tiene nada que mapear.
 *
 * <p><b>Es inmutable salvo por el reordenamiento</b> (`RN-SP-008`). No existe edición ni
 * eliminación: lo único que cambia en una fila ya escrita es su {@code level} y, en la hija
 * reencadenada, su superior. Por eso los únicos mutadores que expone son los dos que el
 * reordenamiento necesita, y no un {@code setName} que nadie debe poder llamar.
 *
 * <p><b>Sin {@code deleted_at}</b>: una membresía no se elimina nunca. Se estudió darle un
 * indicador de activo como el de países y monedas y se descartó, porque desactivar un eslabón deja
 * un hueco en un orden lineal y obliga a decidir qué pasa con quien lo tenía asignado (`spec.md`
 * §14, pregunta 1).
 */
@Entity
@Table(name = "memberships")
public class Membership {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "code", nullable = false, length = 50, updatable = false)
  private String code;

  @Column(name = "name", nullable = false, length = 100, updatable = false)
  private String name;

  @Column(name = "description", updatable = false)
  private String description;

  /**
   * Identificador y no una asociación {@code @ManyToOne}: la cadena se recorre entera de una vez
   * con una sola consulta, y una asociación perezosa produciría una consulta por eslabón al
   * recorrerla.
   */
  @Column(name = "parent_membership_id")
  private UUID parentMembershipId;

  /** Distancia hasta la cima. {@code 1} es la superior y el número crece hacia abajo. */
  @Column(name = "level", nullable = false)
  private short level;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** Exigido por JPA. */
  protected Membership() {}

  /**
   * Registra una membresía en la posición que el dominio ya calculó.
   *
   * <p><b>El nivel y la superior no se reciben del actor</b>, se reciben de {@link
   * MembershipChain}: el DTO de entrada no tiene ni {@code level} ni {@code parentMembershipId}, y
   * es lo que hace verificable que la posición no se pueda forzar desde fuera (`CA-SP-115`).
   *
   * @param posicion resultado de {@link MembershipChain#insertAbove(UUID)}
   * @param ahora instante del alta, inyectado para que la prueba pueda fijarlo
   */
  public static Membership create(
      UUID id,
      String code,
      String name,
      String description,
      MembershipInsertion posicion,
      OffsetDateTime ahora) {

    Membership membresia = new Membership();
    membresia.id = id;
    // El código NO se recorta: se persiste tal como llegó, para que el actor
    // vea exactamente qué código quedó registrado (`plan.md` §4).
    membresia.code = code;
    membresia.name = recortar(name);
    membresia.description = recortar(description);
    membresia.parentMembershipId = posicion.parentId();
    membresia.level = (short) posicion.level();
    membresia.createdAt = ahora;
    membresia.updatedAt = ahora;
    return membresia;
  }

  /**
   * Recorta espacios al inicio y al final.
   *
   * <p>Sin este recorte, {@code "Plata "} y {@code "Plata"} serían dos nombres distintos para
   * {@code uq_memberships_name} y la unicidad se burlaría con un espacio — y `RN-SP-008` haría el
   * duplicado permanente.
   */
  private static String recortar(String valor) {
    if (valor == null) {
      return null;
    }
    String recortado = valor.trim();
    return recortado.isEmpty() ? null : recortado;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public UUID getParentMembershipId() {
    return parentMembershipId;
  }

  public short getLevel() {
    return level;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
