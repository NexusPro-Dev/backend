package com.factech.nexus.modules.system.users.domain.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * La membresía de una persona, tal como está en `user_memberships`.
 *
 * <p><b>Aquí vive la única definición de «vigente» del sistema</b> (`RF-SP-032` · `T-01`). Tres
 * requerimientos la necesitan —`RF-SP-026` para mostrarla, `RF-SP-031` para decidir la cascada y
 * `RF-SP-032` para distinguir una renovación de una asignación sin cambio—, y escrita tres veces
 * como un {@code WHERE} distinto acabaría dando tres respuestas en el borde.
 *
 * <p>El borde importa y es el que se implementa mal: <b>una fecha exactamente igual al instante
 * consultado ya NO está vigente</b>. {@code ends_at} es el momento en que la membresía deja de
 * valer, no el último en que vale; con {@code >=} habría un instante —el de la propia frontera— en
 * que la membresía seguiría concediendo lo que ya expiró.
 *
 * <p>{@code endsAt} nulo significa <b>indefinida</b>, no «sin fecha conocida». Nadie retira la
 * vencida: la vigencia se evalúa al consultarla (`V20`).
 */
public record UserMembership(
    UUID membershipId, String code, String name, short level, OffsetDateTime endsAt) {

  /** ¿Sigue valiendo en ese instante? */
  public boolean isCurrentAt(OffsetDateTime instante) {
    return endsAt == null || endsAt.isAfter(instante);
  }

  /** ¿Describe lo mismo que la asignación que se pide? Es lo que separa `FA-002` de `FA-003`. */
  public boolean coincideCon(UUID otraMembresia, OffsetDateTime otraVigencia) {
    if (!membershipId.equals(otraMembresia)) {
      return false;
    }
    if (endsAt == null || otraVigencia == null) {
      return endsAt == null && otraVigencia == null;
    }
    // Se compara el INSTANTE y no el objeto: la misma fecha con otro desplazamiento
    // horario es la misma fecha, y tratarla como un cambio dejaría una fila de
    // auditoría que no describe ningún cambio.
    return endsAt.isEqual(otraVigencia);
  }
}
