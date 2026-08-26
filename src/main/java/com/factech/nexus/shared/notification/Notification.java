package com.factech.nexus.shared.notification;

/**
 * Un envío saliente, visto desde quien lo pide (`architecture.md` §15.1).
 *
 * <p><b>No dice por qué canal viaja.</b> Hoy es correo; el día que haya mensajería o notificación
 * de aplicación, quien pide el envío no debería enterarse. Por eso lleva un destinatario y un
 * texto, y no una dirección de correo y un cuerpo HTML.
 *
 * @param destinatario a quién va. Hoy una dirección de correo
 * @param asunto de qué trata. Nunca lleva el secreto: los asuntos se registran en más sitios de los
 *     que uno controla
 * @param cuerpo el mensaje, en texto plano
 */
public record Notification(String destinatario, String asunto, String cuerpo) {}
