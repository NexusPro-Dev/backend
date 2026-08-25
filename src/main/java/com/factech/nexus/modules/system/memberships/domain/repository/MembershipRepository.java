package com.factech.nexus.modules.system.memberships.domain.repository;

import com.factech.nexus.modules.system.memberships.domain.models.ChainLink;
import com.factech.nexus.modules.system.memberships.domain.models.Membership;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de escritura de la cadena de membresías (`RF-SP-016`).
 *
 * <p>El puerto expone las cuatro escrituras que el reordenamiento necesita y ninguna más: no hay
 * {@code update} ni {@code delete} generales porque `RN-SP-008` los prohíbe, y no tenerlos es la
 * forma más barata de que nadie los llame por descuido.
 */
public interface MembershipRepository {

  /**
   * Lee la cadena entera <b>y la bloquea</b> hasta el final de la transacción.
   *
   * <p><b>Va antes de las verificaciones, no después</b> (`plan.md` §4): verificar sobre una cadena
   * que otra transacción está reordenando produce decisiones tomadas sobre un estado que ya no
   * existe.
   *
   * <p>Es asumible porque son unos pocos elementos y porque las altas de membresía son raras. Se
   * serializan las inserciones concurrentes, y la segunda encuentra la cadena ya reordenada y
   * calcula su posición sobre el estado real. <b>El listado de `RF-SP-017` no se bloquea</b>:
   * {@code FOR UPDATE} no estorba a los lectores.
   */
  List<ChainLink> loadChainForUpdate();

  /** ¿Hay ya una membresía con ese código? (`EX-001`) */
  boolean existsCode(String code);

  /** ¿Hay ya una membresía con ese nombre, sin distinguir mayúsculas ni acentos? (`VAL-004`) */
  boolean existsName(String name);

  /**
   * Baja un nivel a todas las membresías que estén en {@code nivel} o por debajo.
   *
   * <p>Una sola sentencia y no una entidad por fila: el dominio ya decidió qué nivel corresponde a
   * cada una, y no hay ninguna regla que evaluar por fila.
   *
   * @return cuántas filas se desplazaron, para contrastarlo con lo que el dominio calculó
   */
  int shiftLevelsFrom(int nivel);

  /** Persiste la membresía nueva. */
  Membership save(Membership membresia);

  /** Reencadena: la hija indicada pasa a colgar de la membresía nueva. */
  void reparent(UUID hija, UUID nuevaSuperior);
}
