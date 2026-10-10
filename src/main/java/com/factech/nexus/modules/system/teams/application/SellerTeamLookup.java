package com.factech.nexus.modules.system.teams.application;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * <b>La oficina de un vendedor</b>: lo que `teams` publica para que `MV` congele en cada línea de
 * venta dónde se vendió (`RN-MV-078`; `RF-MV-001` plan §2.8; D-25).
 *
 * <p>La oficina es el equipo del <b>primero</b> de la cadena de mando del vendedor —él incluido—
 * que tiene una pertenencia vigente: un agente vende en la oficina de su director, un director en
 * la suya, y un manager, que está por encima de las oficinas, en ninguna.
 *
 * <p><b>No se pregunta por el rol ni por el estado del equipo.</b> Quién puede tener fila lo decide
 * `RN-SP-051` al asignar (`RF-SP-069`); aquí se lee lo que hay. Un equipo {@code INACTIVO} con su
 * director vigente sigue siendo su oficina: la regla habla de pertenencia.
 *
 * <p><b>Por qué no {@code SupervisorChain} y un filtro en `MV`</b>: haría falta la cadena entera y
 * después preguntar por la pertenencia de cada eslabón, o leer {@code team_members} desde `MV`, que
 * D-25 prohíbe. La sentencia que para en el primer miembro vive en el módulo dueño de las dos
 * tablas, y es una sola ida a la base.
 */
public interface SellerTeamLookup {

  /**
   * La oficina del vendedor <b>en un instante</b>: el de la venta, no el de hoy.
   *
   * @param sellerId el vendedor; nulo devuelve vacío
   * @param instant el instante en que rigen la cadena y la pertenencia; nulo devuelve vacío
   * @return el equipo, o vacío si nadie de la cadena tenía pertenencia vigente en ese instante
   */
  Optional<UUID> teamAt(UUID sellerId, OffsetDateTime instant);

  /**
   * La oficina de <b>hoy</b> de varios vendedores, en una sola sentencia (`RF-MV-058`).
   *
   * @return vendedor → equipo; <b>sin entrada</b> el que no tiene oficina
   */
  Map<UUID, UUID> currentTeamsOf(Collection<UUID> sellerIds);
}
