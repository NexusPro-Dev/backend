package com.factech.nexus.modules.system.teams.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * La pertenencia de un manager a un equipo (`RN-SP-051`, `RN-SP-052`).
 *
 * <p><b>Tiene la forma exacta de `user_supervisors`</b>, y por los mismos motivos: clave sustituta
 * porque el mismo par puede repetirse en el tiempo —un manager vuelve a un equipo en el que ya
 * estuvo— y lo que distingue una fila de otra es el <b>periodo</b>; unicidad parcial sobre {@code
 * user_id} mientras {@code ended_at} es nulo, porque una restricción única corriente haría
 * imposible el historial. Lo que cambia es qué hay al otro lado: allí una persona, aquí un equipo.
 *
 * <p><b>Solo la cúspide tiene fila.</b> Un director o un agente pertenece al equipo de su manager
 * <i>por recorrido</i> de `user_supervisors`, no por dato: si tuviera fila propia, alguien podría
 * ponerlo en un equipo distinto del de quien lo manda y no habría regla que lo impidiera sin volver
 * a recorrer la cadena.
 *
 * <p><b>Cerrar no es borrar.</b> Una fila con fecha de fin es historial y se conserva: decide a qué
 * equipo se atribuía lo que esa red producía, y las comisiones lo necesitarán.
 *
 * <p>Nace con `V33` (`RF-SP-063`) y la escriben `RF-SP-069` y `RF-SP-070`.
 */
@Entity
@Table(name = "team_members")
public class TeamMember {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "team_id", nullable = false, updatable = false)
  private UUID teamId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "started_at", nullable = false, updatable = false)
  private OffsetDateTime startedAt;

  @Column(name = "ended_at")
  private OffsetDateTime endedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** Exigido por JPA. */
  protected TeamMember() {}

  /**
   * Abre una pertenencia vigente. Rige desde el momento de ejecutarse; no admite fecha declarada.
   */
  public static TeamMember open(UUID id, UUID teamId, UUID userId, OffsetDateTime ahora) {
    TeamMember pertenencia = new TeamMember();
    pertenencia.id = id;
    pertenencia.teamId = teamId;
    pertenencia.userId = userId;
    pertenencia.startedAt = ahora;
    pertenencia.createdAt = ahora;
    pertenencia.updatedAt = ahora;
    return pertenencia;
  }

  /**
   * Cierra la pertenencia y devuelve si hubo cambio. Cerrar una ya cerrada no es un error y no
   * mueve nada: el puerto de `RN-SP-055` lo invoca sin saber si la persona estaba en un equipo.
   *
   * <p>El cierre nunca es anterior al comienzo —lo impide {@code ck_team_members_periodo}—, y por
   * eso un cierre en el mismo instante en que se abrió se desplaza al menos un microsegundo: puede
   * ocurrir cuando se asigna y se retira dentro de la misma prueba.
   */
  public boolean close(OffsetDateTime ahora) {
    if (endedAt != null) {
      return false;
    }
    endedAt = ahora.isAfter(startedAt) ? ahora : startedAt.plusNanos(1_000);
    updatedAt = ahora;
    return true;
  }

  public boolean estaVigente() {
    return endedAt == null;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTeamId() {
    return teamId;
  }

  public UUID getUserId() {
    return userId;
  }

  public OffsetDateTime getStartedAt() {
    return startedAt;
  }

  public OffsetDateTime getEndedAt() {
    return endedAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
