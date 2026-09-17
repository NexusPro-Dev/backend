package com.factech.nexus.modules.system.brokers.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Parámetros de {@code GET /api/v1/broker-accounts} (`RF-SP-057`).
 *
 * <p><b>Los nombres son los del sistema y no unos nuevos</b>: {@code search} es como se llama en
 * {@code GET /api/v1/users}, y {@code from}/{@code to} como en los cuatro listados de auditoría,
 * con su <b>misma semántica semiabierta</b> —incluye {@code from}, excluye {@code to}—. Llamarlos
 * {@code q}, {@code declaredFrom} o {@code declaredTo} habría dado dos vocabularios para una idea.
 *
 * <p><b>{@code supervisorId} es LA RED ENTERA y no un nivel</b> (`RN-SP-047`), y <b>no incluye a la
 * propia persona</b>: su red son los suyos. Quien quiera además las cuentas de ella las pide con
 * {@code userId}, y <b>los dos filtros se combinan</b> — juntos responden «de la red de este
 * vendedor, las de esta persona», que es la comprobación natural al revisar un caso.
 *
 * <p><b>Ni {@code supervisorId}, ni {@code userId}, ni {@code brokerId} se validan contra su
 * tabla.</b> Uno inexistente devuelve la página vacía y <b>no es un error</b>: es el criterio de
 * `RF-SP-025` y de `RF-SP-056`, y validarlo costaría una consulta por petición para producir un
 * fallo que la especificación no quiere. Lo único que se les exige es la forma canónica.
 *
 * <p><b>{@code status} sí es un error si no es uno de los dos</b>, y la asimetría es deliberada: un
 * identificador que no designa nada es una pregunta legítima con respuesta vacía, mientras que
 * {@code ?status=depositado} es una pregunta mal escrita — devolver vacío haría creer que nadie
 * está en ese estado.
 *
 * <p>{@code page} y {@code size} son {@code Integer} y no primitivos, como en el resto del sistema:
 * un primitivo en un {@code @ModelAttribute} hace que la petición <b>sin el parámetro</b> falle con
 * {@code 400}, porque Spring intenta convertir la ausencia.
 */
public record ListBrokerAccountsRequest(
    Integer page,
    Integer size,
    UUID supervisorId,
    UUID userId,
    String status,
    UUID brokerId,
    String search,
    OffsetDateTime from,
    OffsetDateTime to) {

  public ListBrokerAccountsRequest {
    // Recortado, y en blanco equivale a ausente: buscar por espacios es no
    // buscar, y añadir el predicado devolvería la lista entera igualmente
    // pagando el recorrido. Es el mismo constructor compacto de
    // `ListUsersRequest`.
    search = search == null || search.isBlank() ? null : search.trim();
    status = status == null || status.isBlank() ? null : status.trim();
  }
}
