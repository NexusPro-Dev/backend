package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.domain.repository.GatewayEventRepository;
import com.factech.nexus.modules.movements.domain.service.CardGateway.GatewayEvent;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ServiceUnavailableException;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Recibir</b> una notificación de la pasarela (`RF-MV-041` · `plan.md` §1, primer tiempo).
 *
 * <p>Verifica la firma <b>antes de guardar nada</b>, la guarda entera si no la tenía y, si es
 * nueva, avisa para procesarla <b>después del {@code COMMIT}</b>. La pasarela recibe su respuesta
 * en cuanto la notificación está guardada: procesar no es asunto de esta petición (`RN-MV-059`).
 */
@Service
public class GatewayEventIntake {

  static final String CODIGO = "RN-MV-059";

  private final CardGateway pasarela;
  private final GatewayEventRepository eventos;
  private final ApplicationEventPublisher avisos;

  public GatewayEventIntake(
      CardGateway pasarela, GatewayEventRepository eventos, ApplicationEventPublisher avisos) {
    this.pasarela = pasarela;
    this.eventos = eventos;
    this.avisos = avisos;
  }

  /** El aviso de que hay una notificación nueva, guardada y sin procesar. */
  public record Received(UUID id) {}

  @Transactional
  public void receive(byte[] cuerpo, String firma) {
    if (!pasarela.enabled()) {
      throw new ServiceUnavailableException(
          CODIGO, "La pasarela de pago no está configurada en este entorno.");
    }
    GatewayEvent evento;
    try {
      evento = pasarela.verify(cuerpo, firma);
    } catch (CardGateway.InvalidSignature fallo) {
      String mensaje = "La notificación no está firmada por la pasarela: " + fallo.getMessage();
      throw new ValidationException(
          CODIGO, mensaje, List.of(new FieldError("Stripe-Signature", CODIGO, mensaje)));
    }
    if (evento.externalId() == null || evento.type() == null) {
      String mensaje = "La notificación no trae identificador o tipo.";
      throw new ValidationException(
          CODIGO, mensaje, List.of(new FieldError("body", CODIGO, mensaje)));
    }
    UUID id = UUID.randomUUID();
    if (eventos.insertIfNew(
        id, pasarela.name(), evento.externalId(), evento.type(), evento.payload())) {
      avisos.publishEvent(new Received(id));
    }
  }
}
