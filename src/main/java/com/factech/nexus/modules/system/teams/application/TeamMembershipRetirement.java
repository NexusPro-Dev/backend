package com.factech.nexus.modules.system.teams.application;

import java.util.UUID;

/**
 * Lo que `teams` publica para que la pertenencia <b>siga al rol</b> (`RN-SP-055`, `RF-SP-070`).
 *
 * <p><b>Existe porque un equipo no puede contener a quien ya no es manager.</b> Si al retirarle el
 * rol comercial de mayor rango (`RF-SP-031`) o al eliminar a la persona (`RF-SP-029`) su
 * pertenencia siguiera abierta, `RN-SP-051` se cumpliría al asignar y dejaría de cumplirse después,
 * sin que nadie lo notara: el equipo seguiría contando a alguien que ya no es de la cúspide, o que
 * ya no existe.
 *
 * <p><b>El sentido del cruce es el admisible</b>: lo publica `teams` y lo consume `users`, que es
 * quien sabe cuándo alguien deja de ser manager. Al revés —`teams` vigilando los roles— habría que
 * preguntar por el rol de cada miembro en cada lectura, y la regla se aplicaría tarde.
 *
 * <p><b>No falla si no hay nada que cerrar</b>, y esa es la parte que importa del contrato: quien
 * retira un rol o elimina a alguien <b>no sabe</b> si esa persona estaba en un equipo, y obligarle
 * a preguntarlo antes convertiría una invariante del sistema en una condición del llamador. Aquí es
 * idempotente por diseño.
 *
 * <p><b>Sin identificador de correlación en la firma</b>, al contrario de lo que el plan §3
 * apuntaba: lo pone {@code JpaAuditWriter} desde el contexto de la petición, de modo que todas las
 * filas de la misma comparten el suyo <b>sin que nadie lo pase</b>. Recibirlo como parámetro
 * sugeriría que el llamador puede elegirlo, y dos valores distintos dentro de la misma petición
 * serían un defecto que la firma habría permitido escribir.
 */
public interface TeamMembershipRetirement {

  /**
   * Cierra la pertenencia vigente de la persona, si la tiene.
   *
   * @param userId la persona que deja de ser manager o se elimina; un valor nulo no hace nada
   * @param reason el motivo, que viaja a la auditoría; es el mismo con el que se retiró el rol o se
   *     eliminó a la persona, para que las dos filas cuenten la misma historia
   * @return {@code true} si había una pertenencia y quedó cerrada
   */
  boolean retire(UUID userId, String reason);
}
