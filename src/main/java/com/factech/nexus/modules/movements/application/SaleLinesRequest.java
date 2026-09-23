package com.factech.nexus.modules.movements.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Los filtros del listado de líneas de venta (`RF-MV-017` §6.1). Todos opcionales y
 * <b>combinables</b>.
 *
 * <p><b>Los tres estados llegan como texto y no como enumerados</b>, igual que en {@link
 * ListMovementsRequest}: con el enumerado, Spring rechaza el valor mal escrito <b>antes</b> de
 * entrar al caso de uso y el cliente recibe ese error <b>solo</b>, mientras que `VAL-001` a
 * `VAL-005` exigen que los seis problemas de la misma petición viajen <b>juntos</b>. Recibiéndolos
 * como texto, la forma la comprueba el servicio y se suma a los demás.
 *
 * <p><b>{@code typeStatus} filtra y no se publica</b> (0.2.0, 23-09-2026): se acota por el estado
 * del tipo de la venta —«¿qué falta por validar?»— y la fila no lo trae. La asimetría es deliberada
 * y está escrita en `spec.md` §14.7, porque leída en el código sola parece un olvido.
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
    String typeStatus,
    String code,
    OffsetDateTime from,
    OffsetDateTime to) {

  public SaleLinesRequest {
    status = enBlancoEsAusente(status);
    deliveryStatus = enBlancoEsAusente(deliveryStatus);
    typeStatus = enBlancoEsAusente(typeStatus);
    code = enBlancoEsAusente(code);
  }

  /**
   * Filtrar por espacios es no filtrar, y añadir el predicado devolvería lo mismo pagando el paso.
   */
  private static String enBlancoEsAusente(String valor) {
    return valor == null || valor.isBlank() ? null : valor.trim();
  }
}
