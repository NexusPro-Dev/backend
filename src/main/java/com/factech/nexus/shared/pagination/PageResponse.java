package com.factech.nexus.shared.pagination;

import java.util.List;

/**
 * Una página de resultados, uniforme para todo el sistema (`architecture.md` §7.4).
 *
 * <p>Uniforme <b>y no por endpoint</b>: dos formas distintas de paginar obligan a la interfaz a
 * escribir dos lectores, y el segundo acaba asumiendo lo que el primero hacía.
 *
 * <p>{@code totalElements} <b>no depende de la página</b>. Es el número que `RF-SP-042` ·
 * `CA-SP-447` exige que coincida con el que informan los rechazos de `RN-SP-022`: si esta cuenta
 * cambiara al pasar de página, las dos operaciones dirían cosas distintas sobre el mismo equipo.
 *
 * <p><b>{@code totalIsExact} existe para el día en que deje de serlo.</b> Hoy vale siempre
 * verdadero: cuando la página no se llena, el total se deriva sin contar —y sigue siendo exacto—, y
 * cuando se llena se cuenta de verdad. Pero `RF-SP-025` §10 declara el {@code COUNT(*)} sobre
 * `users` como disparador de revisión: con cientos de miles de filas y un filtro poco selectivo es
 * un recorrido secuencial por petición, y el día que se sustituya por una estimación el contrato ya
 * tiene dónde decirlo. Añadirlo entonces habría sido un cambio de forma para todos los clientes.
 */
public record PageResponse<T>(
    List<T> content, long totalElements, int totalPages, int page, int size, boolean totalIsExact) {

  public static <T> PageResponse<T> de(List<T> contenido, long total, int pagina, int tamano) {
    int paginas = tamano <= 0 ? 0 : (int) Math.ceil((double) total / tamano);
    return new PageResponse<>(contenido, total, paginas, pagina, tamano, true);
  }
}
