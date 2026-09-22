package com.factech.nexus.modules.system.users.domain.repository;

import java.util.UUID;

/**
 * Una persona del equipo directo (`RF-SP-042`).
 *
 * <p>Lleva <b>lo justo para nombrarla y ver si sigue operando</b>, y nada más. No es un perfil: no
 * hay correo, ni fechas, ni membresía. La restricción es deliberada y es lo que impide que este
 * endpoint se convierta en un listado de usuarios con otro nombre y otro permiso — `RF-SP-025` ya
 * existe para eso.
 *
 * <p><b>Ya no lleva el rol</b> (10-09-2026). Llevaba uno solo, resuelto en la propia consulta del
 * equipo y limitado a la clasificación {@code VENDEDOR}: la cartera de clientes —que cuelga de esta
 * misma estructura desde `RF-SP-045`— llegaba con el rol en nulo. Los roles se resuelven ahora
 * <b>completos y por lote</b> con {@code UserQueryRepository.rolesOf}, fuera de esta proyección,
 * que es lo que evita una consulta por fila.
 */
public record TeamMember(
    UUID id, String username, String firstName, String lastName, String status) {}
