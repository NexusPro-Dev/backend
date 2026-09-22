package com.factech.nexus.modules.system.users.application;

import java.util.Set;
import java.util.UUID;

/**
 * Lo que `SP` publica sobre <b>hasta dónde llega una persona</b>: su alcance comercial (**D-22**,
 * `RF-MV-015` · `T-02`, `RN-MV-031`).
 *
 * <p>Es el resolvedor que `ADR-005` recomienda (opción B) con <b>un</b> tipo de alcance, y vive
 * aquí y no en `MV` por la regla 2 de `architecture.md` §15.2: quién manda a quién ({@code
 * user_supervisors}) y de qué tipo es cada rol ({@code user_roles.role_type}) son de `SP`, y «mi
 * red» ya se recorre una vez para las cuentas de broker (`RF-SP-057`). Una segunda copia del
 * recorrido en `MV` divergiría sin fallar. `MV` recibe el alcance y lo aplica como predicado; no lo
 * calcula.
 *
 * <h2>La precedencia es la de `RN-MV-031`, y está escrita una vez</h2>
 *
 * <ol>
 *   <li>Quien porta un rol de tipo {@code FUNCIONARIO} alcanza <b>todo</b>: no vende ni compra, y
 *       administración necesita verlo todo por la misma ruta que los demás.
 *   <li>Si no, quien porta uno de tipo {@code VENDEDOR} alcanza <b>su red</b>: quienes cuelgan de
 *       él en la estructura de mando <b>vigente</b>, en toda la profundidad, <b>y él mismo</b>.
 *   <li>Si no —{@code CONSUMIDOR}, o ningún rol vivo— alcanza <b>solo a sí mismo</b>.
 * </ol>
 *
 * <p>Solo cuentan los roles <b>vivos</b>: no retirados, activos, de una persona no eliminada — el
 * mismo predicado con el que se resuelven los permisos efectivos.
 *
 * <p><b>Los clientes no se recorren.</b> La red es la estructura de mando; la cartera ({@code
 * client_sellers}) no interviene, porque lo que se atribuye a un vendedor llega por el {@code
 * seller_id} de la línea y no por quién es cliente de quién.
 */
public interface CommercialReach {

  /**
   * Hasta dónde llega esa persona. Nunca nulo; un identificador nulo o desconocido alcanza {@link
   * Kind#OWN}, que es el alcance más pequeño y el único que no puede enseñar de más.
   */
  Reach reachOf(UUID actorId);

  /** Qué clase de alcance es. */
  enum Kind {
    /** Todo: un rol de tipo {@code FUNCIONARIO}. */
    EVERYTHING,
    /** Su red en profundidad, con él dentro: un rol de tipo {@code VENDEDOR}. */
    NETWORK,
    /** Solo él: {@code CONSUMIDOR}, o nadie. */
    OWN
  }

  /**
   * El alcance resuelto.
   *
   * @param kind qué clase es
   * @param sellers en {@link Kind#NETWORK}, el actor y todos los que cuelgan de él, vigentes, en
   *     toda la profundidad; en los otros dos, vacío. Nunca nulo
   */
  record Reach(Kind kind, Set<UUID> sellers) {
    public Reach {
      sellers = sellers == null ? Set.of() : Set.copyOf(sellers);
    }

    public static Reach everything() {
      return new Reach(Kind.EVERYTHING, Set.of());
    }

    public static Reach own() {
      return new Reach(Kind.OWN, Set.of());
    }

    public static Reach network(Set<UUID> sellers) {
      return new Reach(Kind.NETWORK, sellers);
    }
  }
}
