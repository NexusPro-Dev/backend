package com.factech.nexus.shared.pagination;

/**
 * Un total <b>acotado</b>: exacto hasta un techo, y aproximado por encima (`RF-SP-011`).
 *
 * <p><b>El problema que resuelve.</b> El {@code COUNT(*)} exacto sobre el conjunto filtrado es
 * correcto sobre decenas de filas e insostenible sobre millones: obliga a recorrer todas las que
 * cumplen el predicado aunque solo se devuelvan veinte, y lo hace <b>en cada página</b>. Con las
 * tablas vacías no se nota; con dos años de operación, el listado de auditoría tarda segundos.
 *
 * <p><b>Cómo.</b> La sentencia de conteo cuenta sobre una subconsulta con {@code LIMIT techo + 1},
 * de modo que nunca examina más de esas filas, tenga la tabla mil o cien millones:
 *
 * <pre>{@code
 * SELECT count(*) FROM (SELECT 1 FROM tabla WHERE <predicado> LIMIT 10001) t
 * }</pre>
 *
 * <p>Si el resultado supera el techo, el total devuelto <b>es</b> el techo y {@link #exact} vale
 * falso: el cliente sabe que «hay más de diez mil» y que debe seguir filtrando o paginando. Por
 * debajo, el número es el real y la marca vale verdadero — que es lo que ocurre en la inmensa
 * mayoría de las consultas, porque quien audita llega con un filtro y ese conjunto rara vez pasa
 * del techo. El techo lo toca sobre todo el listado sin filtros, que es precisamente donde el total
 * exacto menos informa.
 *
 * <p><b>Vive en {@code shared} y no en el adaptador de auditoría</b>: los cuatro registros lo
 * necesitan igual, y `RF-SP-002` podría adoptarlo el día que {@code roles} crezca. Dejarlo en un
 * adaptador garantizaría cuatro copias con cuatro techos distintos.
 *
 * @param total el número real, o el techo cuando se superó
 * @param exact si el total es el número real
 */
public record BoundedCount(long total, boolean exact) {

  /**
   * Interpreta el resultado de la sentencia acotada.
   *
   * @param contado lo que devolvió el {@code count} sobre la subconsulta limitada
   * @param techo el límite aplicado, sin el {@code + 1}
   */
  public static BoundedCount de(long contado, int techo) {
    return contado > techo ? new BoundedCount(techo, false) : new BoundedCount(contado, true);
  }

  /** Un total exacto, para los listados que sí pueden contarlo entero. */
  public static BoundedCount exacto(long total) {
    return new BoundedCount(total, true);
  }
}
