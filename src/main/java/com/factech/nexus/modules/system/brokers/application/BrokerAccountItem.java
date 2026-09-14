package com.factech.nexus.modules.system.brokers.application;

import com.factech.nexus.modules.system.brokers.domain.models.UserBrokerStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una cuenta de una persona en un broker (`RF-SP-055`).
 *
 * <p><b>{@code accountId} se llama igual que en la entrada de `RF-SP-045`</b>, donde el titular lo
 * declara. Publicarlo como {@code externalId} —el nombre de la columna— obligaría al frontend a
 * saber que son el mismo dato con dos nombres, y esa clase de sinónimo es la que acaba produciendo
 * dos campos en un formulario.
 *
 * <p><b>{@code brokerUsername} se emite AUNQUE SEA NULO</b> ({@code Include.ALWAYS}), y por eso el
 * anotado está aquí y no se deja al ajuste global. Su nulo <b>significa algo</b> (`RN-SP-040`): «el
 * broker todavía no lo ha confirmado». Omitir el campo confundiría eso con «confirmado sin nombre»,
 * y una cadena vacía lo confundiría con «confirmado con el nombre vacío».
 *
 * <p><b>{@code declaredAt} es {@code created_at}</b>, y se publica con ese nombre porque lo que
 * significa es <b>cuándo la declaró la persona</b>. {@code updatedAt} no se publica: hoy nadie
 * actualiza la fila, y el día que el webhook lo haga, publicarlo será una decisión de `RF-SP-054`
 * con su propio significado.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record BrokerAccountItem(
    UUID id,
    BrokerRef broker,
    String accountId,
    String brokerUsername,
    UserBrokerStatus status,
    OffsetDateTime declaredAt) {

  /**
   * El broker al que pertenece la cuenta, con lo justo para nombrarlo.
   *
   * <p><b>Anidado y no aplanado en {@code brokerId} y {@code brokerName}</b>: es la forma que ya
   * tiene el catálogo de `RF-SP-052`, y deja crecer el broker sin renombrar campos aquí.
   */
  public record BrokerRef(UUID id, String name) {}
}
