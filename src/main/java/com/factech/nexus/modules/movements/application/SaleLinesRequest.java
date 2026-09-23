package com.factech.nexus.modules.movements.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Los filtros del listado de líneas de venta (`RF-MV-017` §6.1). Todos opcionales y
 * <b>combinables</b>.
 *
 * <p><b>Los dos estados llegan como texto y no como enumerados</b>, igual que en {@link
 * ListMovementsRequest}: con el enumerado, Spring rechaza el valor mal escrito <b>antes</b> de
 * entrar al caso de uso y el cliente recibe ese error <b>solo</b>, mientras que `VAL-001` a
 * `VAL-005` exigen que los cinco problemas de la misma petición viajen <b>juntos</b>. Recibiéndolos
 * como texto, la forma la comprueba el servicio y se suma a los demás.
 *
 * <p><b>Los identificadores sí son {@code UUID}</b>, y la asimetría es la que el módulo ya tiene:
 * un identificador mal formado no es un valor fuera de un dominio cerrado sino un dato ilegible, y
 * el convertidor canónico lo traduce al mismo `VAL-001` de todo el sistema.
 *
 * <p><b>Ningún campo acota por quién pregunta</b>, y su ausencia es la implementación: esta
 * consulta es de administración y con el permiso se ve todo el libro (`spec.md` §2). El alcance por
 * estructura vive en `movements:list-sales`.
 */
public record SaleLinesRequest(
    Integer page,
    Integer size,
    UUID movementId,
    UUID userId,
    UUID sellerId,
    UUID productId,
    String status,
    String deliveryStatus,
    String code,
    OffsetDateTime from,
    OffsetDateTime to) {

  public SaleLinesRequest {
    status = enBlancoEsAusente(status);
    deliveryStatus = enBlancoEsAusente(deliveryStatus);
    code = enBlancoEsAusente(code);
  }

  /**
   * Filtrar por espacios es no filtrar, y añadir el predicado devolvería lo mismo pagando el paso.
   */
  private static String enBlancoEsAusente(String valor) {
    return valor == null || valor.isBlank() ? null : valor.trim();
  }
}
