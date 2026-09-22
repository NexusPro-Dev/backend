package com.factech.nexus.modules.products.application;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Lista blanca de campos por los que se puede ordenar el catálogo (`RF-PM-002` · `T-02`).
 *
 * <p><b>Es una lista blanca y no una negra</b>, y ahí está toda la defensa: el nombre que llega del
 * cliente se resuelve contra este enumerado <b>antes</b> de construir la sentencia, de modo que su
 * cadena nunca llega al SQL. Con una lista negra, cada columna nueva nacería ordenable y habría que
 * acordarse de prohibirla.
 *
 * <p><b>Un campo fuera de la lista se RECHAZA, no se ignora</b> (`CA-PM-075`). Ignorarlo devolvería
 * un orden distinto del pedido sin decirlo, y quien pagina sobre él creería estar viendo otra cosa.
 *
 * <p>Las exclusiones tienen motivo:
 *
 * <ul>
 *   <li>{@code code} — es único e inmutable, de modo que ordenar por él es ordenar por un
 *       identificador: no responde ninguna pregunta que el actor se haga sobre el catálogo.
 *   <li>{@code status} y {@code type} — son dominios de dos valores. Ordenar por ellos es agrupar,
 *       y para eso están los filtros.
 *   <li>{@code deletedAt} — agruparía a los retirados, que es lo que ya hace {@code
 *       includeDeleted}.
 *   <li>{@code targetMembership} — no es escalar: obligaría a decidir si se ordena por su nombre o
 *       por su nivel, y la respuesta depende de quién pregunta.
 * </ul>
 */
public enum ProductSortField {
  NAME("name", "p.name"),
  PRICE("price", "p.price"),
  CREATED_AT("createdAt", "p.created_at");

  /**
   * El orden por omisión: el de alta, del más reciente al más antiguo.
   *
   * <p>Quien gobierna el catálogo trabaja sobre lo último que entró (`spec.md` §14, resolución 1).
   * <b>El del cliente es otro</b> —agrupado por tipo y por nivel—, y vive en `RF-PM-007`.
   *
   * <p>Coincide con {@code ix_products_listado}, columna por columna y sentido por sentido.
   */
  public static final String POR_OMISION = "p.created_at DESC, p.id DESC";

  private final String publico;
  private final String columna;

  ProductSortField(String publico, String columna) {
    this.publico = publico;
    this.columna = columna;
  }

  /**
   * Resuelve {@code campo,sentido}. Ausente equivale a {@link #POR_OMISION}.
   *
   * <p><b>El desempate por identificador no es cosmético.</b> Sin un orden <b>total</b>, dos
   * productos que compartan el valor ordenado —dos precios iguales, dos altas del mismo instante—
   * pueden repetirse o saltarse entre páginas, y eso se descubre como «faltan productos» sin ningún
   * error de por medio. Sale gratis: el identificador es un UUID v7 y su orden <b>es</b> el
   * cronológico.
   *
   * <p>El desempate va <b>en el mismo sentido</b> que el campo pedido: en descendente, dos empates
   * salen del más reciente al más antiguo, que es lo que el resto de la página está haciendo.
   *
   * @return la cláusula de ordenamiento y su nombre público, ya desempatada
   * @throws ValidationException `VAL-005` si el campo o el sentido no pertenecen a su dominio
   */
  public static Orden resolver(String sort) {
    if (sort == null || sort.isBlank()) {
      return new Orden(POR_OMISION, CREATED_AT.publico + ",desc");
    }

    String[] partes = sort.split(",", 2);
    String campo = partes[0].trim();
    String sentido = partes.length > 1 ? partes[1].trim().toLowerCase(Locale.ROOT) : "asc";

    ProductSortField resuelto =
        Arrays.stream(values())
            .filter(valor -> valor.publico.equalsIgnoreCase(campo))
            .findFirst()
            .orElseThrow(() -> rechazar(campo));

    if (!"asc".equals(sentido) && !"desc".equals(sentido)) {
      throw rechazar(sort);
    }
    boolean descendente = "desc".equals(sentido);
    return new Orden(
        resuelto.columna
            + (descendente ? " DESC" : " ASC")
            + (descendente ? ", p.id DESC" : ", p.id ASC"),
        resuelto.publico + "," + sentido);
  }

  /**
   * El orden resuelto, en sus dos formas.
   *
   * <p>Van <b>juntas y no separadas</b> porque tienen que decir lo mismo: {@code sql} construye la
   * sentencia y {@code publico} es lo que se devuelve al cliente. Resolverlas por caminos distintos
   * permitiría paginar por una columna y anunciar otra.
   *
   * @param sql la cláusula {@code ORDER BY}, con nombres de columna
   * @param publico el nombre del campo tal como lo escribe el cliente, con su sentido
   */
  public record Orden(String sql, String publico) {}

  private static ValidationException rechazar(String valor) {
    String mensaje =
        "No se puede ordenar por '"
            + valor
            + "'. Campos admitidos: "
            + String.join(", ", admitidos())
            + ".";
    return new ValidationException(
        "VAL-005", mensaje, List.of(new FieldError("sort", "VAL-005", mensaje)));
  }

  private static List<String> admitidos() {
    return Arrays.stream(values()).map(valor -> valor.publico).toList();
  }
}
