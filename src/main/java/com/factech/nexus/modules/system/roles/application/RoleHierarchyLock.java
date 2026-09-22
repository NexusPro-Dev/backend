package com.factech.nexus.modules.system.roles.application;

/**
 * Serializa <b>toda</b> mutación de la jerarquía de roles (`RF-SP-008` · `T-03`).
 *
 * <p><b>Por qué un bloqueo global y no uno por rol.</b> El bloqueo pesimista de fila que toma
 * {@code RoleWriteAccess} sirve para dos ediciones del <b>mismo</b> rol, y no sirve para nada aquí:
 * dos reubicaciones en ramas distintas no comparten ninguna fila y aun así pueden cerrar un ciclo
 * entre ambas. Con cuatro roles, {@code A → B} y {@code C → D}:
 *
 * <pre>
 *   Mover B bajo D    (válido: D no desciende de B)
 *   Mover D bajo B    (válido: B no desciende de D)
 * </pre>
 *
 * <p>Cada operación es correcta contra la jerarquía que ve, y aplicadas a la vez {@code B} y {@code
 * D} quedan colgando el uno del otro. Como no se sabe de antemano qué ramas se tocarán, el único
 * bloqueo que cierra el hueco es el que abarca la jerarquía entera.
 *
 * <p><b>Se intenta sin esperar, y eso es parte del contrato.</b> {@link #tryAcquire()} devuelve
 * {@code false} de inmediato si otra transacción lo tiene tomado, en lugar de encolarse. Dos
 * razones (`plan.md` §5): una espera indefinida encadena peticiones colgadas, cada una ocupando una
 * conexión del pool; y con espera, `CA-SP-161` dependería de la temporización de dos transacciones
 * y sería intermitente.
 *
 * <p><b>Es un puerto en {@code application}</b> —la capa sin dependencias— para que el caso de uso
 * pueda probarse con un doble y sin base de datos, igual que {@link AuthenticatedActor}. El
 * adaptador es {@code AdvisoryRoleHierarchyLock}.
 *
 * <p><b>El coste es que las reubicaciones no se solapan.</b> Es aceptable porque reubicar un rol es
 * una operación excepcional; no lo sería si el bloqueo alcanzara también a las lecturas, que no es
 * el caso — ninguna consulta lo toma.
 */
public interface RoleHierarchyLock {

  /**
   * Intenta tomar el bloqueo <b>sin esperar</b>.
   *
   * <p>Se libera solo al terminar la transacción, también si esta falla: quien lo toma no tiene
   * nada que liberar y no hay forma de dejar la jerarquía inmovilizada por una excepción no
   * prevista.
   *
   * @return {@code true} si lo obtuvo; {@code false} si otra transacción lo tiene tomado
   */
  boolean tryAcquire();
}
