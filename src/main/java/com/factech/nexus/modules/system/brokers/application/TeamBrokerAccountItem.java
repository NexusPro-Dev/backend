package com.factech.nexus.modules.system.brokers.application;

import com.factech.nexus.modules.system.brokers.domain.models.UserBrokerStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una cuenta del equipo, con su titular (`RF-SP-056`).
 *
 * <p><b>Lo único que añade a {@link BrokerAccountItem} es {@code user}</b>, y esa es la diferencia
 * entera entre los dos requerimientos: en `RF-SP-055` el titular es la ruta, y aquí cada fila es de
 * una persona distinta.
 *
 * <p><b>Los demás campos se serializan exactamente igual que en {@link BrokerAccountItem}</b>, a
 * propósito: que las dos respuestas coincidan campo por campo es lo que permite al frontend tener
 * <b>un solo componente</b> para pintar una cuenta.
 *
 * <p><b>{@code user} es un objeto y no cuatro campos con prefijo</b>, como {@code
 * CommercialStructureResponse.Person}: deja crecer el titular sin renombrar nada.
 *
 * <p><b>El listado es de CUENTAS y no de personas</b>, y de ahí sale lo que más sorprende al
 * leerlo: quien está en el equipo y no declaró ninguna cuenta <b>no aparece</b>, y quien declaró
 * dos aparece dos veces. Quién hay en el equipo lo responde `RF-SP-042`.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TeamBrokerAccountItem(
    UUID id,
    Holder user,
    BrokerAccountItem.BrokerRef broker,
    String accountId,
    String brokerUsername,
    UserBrokerStatus status,
    OffsetDateTime declaredAt) {

  /**
   * El titular de la cuenta, con lo justo para identificarlo en una lista.
   *
   * <p><b>Se publica con nombre declarado a mano</b> y no como {@code Holder}, que es lo que
   * springdoc emitiría: los esquemas viven en un espacio de nombres <b>plano y compartido por todos
   * los módulos</b> (`api/index.md` §6), y «Holder» es genérico — el día que otro módulo publique
   * un titular saldrían {@code Holder} y {@code Holder_1} <b>sin garantizar cuál es cuál</b>, y el
   * cliente generado cambiaría de tipo sin que nada fallara. Mismo cuidado que {@code
   * RegistrationBrokerAccount} y {@code SaleParty}.
   */
  @Schema(name = "BrokerAccountHolder")
  public record Holder(UUID id, String username, String firstName, String lastName) {}
}
