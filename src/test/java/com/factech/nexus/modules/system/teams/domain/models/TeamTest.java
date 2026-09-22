package com.factech.nexus.modules.system.teams.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.shared.error.ValidationException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo único que el agregado decide solo (`RF-SP-063` · `T-03`): la normalización del nombre y de la
 * descripción y el estado inicial. Sin Spring y sin base de datos (Art. VI.3).
 */
class TeamTest {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 9, 22, 14, 0, 0, 0, ZoneOffset.UTC);

  @Test
  @DisplayName("nace ACTIVO, con el nombre recortado y las dos fechas iguales")
  void naceActivoYRecortado() {
    Team equipo = Team.create(UUID.randomUUID(), "  Equipo Norte  ", " Los del norte ", AHORA);

    assertThat(equipo.getName()).isEqualTo("Equipo Norte");
    assertThat(equipo.getDescription()).isEqualTo("Los del norte");
    assertThat(equipo.getStatus()).isEqualTo(TeamStatus.ACTIVO);
    assertThat(equipo.estaActivo()).isTrue();
    assertThat(equipo.estaEliminado()).isFalse();
    assertThat(equipo.getCreatedAt()).isEqualTo(AHORA);
    assertThat(equipo.getUpdatedAt()).isEqualTo(AHORA);
  }

  @Test
  @DisplayName("una descripción de solo espacios se guarda NULA: es una errata, no un valor")
  void descripcionDeSoloEspacios() {
    Team equipo = Team.create(UUID.randomUUID(), "Equipo Norte", "   ", AHORA);

    assertThat(equipo.getDescription()).isNull();
  }

  @Test
  @DisplayName("el nombre ausente, vacío o de más de 100 es VAL-001")
  void nombreObligatorio() {
    assertThatThrownBy(() -> Team.create(UUID.randomUUID(), null, null, AHORA))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("nombre");
    assertThatThrownBy(() -> Team.create(UUID.randomUUID(), "   ", null, AHORA))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Team.create(UUID.randomUUID(), "x".repeat(101), null, AHORA))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  @DisplayName("la descripción de más de 500 es VAL-002, y la de 500 exactos se admite")
  void descripcionAcotada() {
    assertThatThrownBy(() -> Team.create(UUID.randomUUID(), "Equipo", "x".repeat(501), AHORA))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("descripción");

    assertThat(Team.create(UUID.randomUUID(), "Equipo", "x".repeat(500), AHORA).getDescription())
        .hasSize(500);
  }

  @Test
  @DisplayName("la instantánea lleva nombre, descripción y estado, y nada más")
  void instantanea() {
    Team equipo = Team.create(UUID.randomUUID(), "Equipo Norte", null, AHORA);

    assertThat(equipo.instantanea())
        .containsOnlyKeys("name", "description", "status")
        .containsEntry("name", "Equipo Norte")
        .containsEntry("status", "ACTIVO");
  }

  @Test
  @DisplayName("la pertenencia se abre vigente y se cierra una sola vez")
  void pertenencia() {
    UUID equipo = UUID.randomUUID();
    UUID persona = UUID.randomUUID();
    TeamMember pertenencia = TeamMember.open(UUID.randomUUID(), equipo, persona, AHORA);

    assertThat(pertenencia.estaVigente()).isTrue();
    assertThat(pertenencia.getTeamId()).isEqualTo(equipo);
    assertThat(pertenencia.getUserId()).isEqualTo(persona);

    assertThat(pertenencia.close(AHORA.plusDays(1))).isTrue();
    assertThat(pertenencia.estaVigente()).isFalse();
    assertThat(pertenencia.getEndedAt()).isEqualTo(AHORA.plusDays(1));

    // Cerrar una ya cerrada no es un error y no mueve nada: el puerto de
    // `RN-SP-055` la invoca sin saber si la persona estaba en un equipo.
    assertThat(pertenencia.close(AHORA.plusDays(2))).isFalse();
    assertThat(pertenencia.getEndedAt()).isEqualTo(AHORA.plusDays(1));
  }

  @Test
  @DisplayName("cerrar en el mismo instante en que se abrió respeta ck_team_members_periodo")
  void cierreEnElMismoInstante() {
    TeamMember pertenencia =
        TeamMember.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AHORA);

    assertThat(pertenencia.close(AHORA)).isTrue();
    assertThat(pertenencia.getEndedAt()).isAfter(pertenencia.getStartedAt());
  }
}
