package com.factech.nexus.shared.notification;

import java.util.Map;

/**
 * Un envío saliente, visto desde quien lo pide (`architecture.md` §15.1).
 *
 * <p><b>No dice por qué canal viaja.</b> Hoy es correo; el día que haya mensajería o notificación
 * de aplicación, quien pide el envío no debería enterarse. Por eso lleva un destinatario y un
 * texto, y no una dirección de correo y un cuerpo HTML.
 *
 * <p><b>El tipo y las variables no rompen esa regla.</b> Son el mensaje descrito por sus partes
 * —«esto es una recuperación de contraseña, y el código es este»— y no la plantilla ni el formato:
 * el identificador de la plantilla alojada en el proveedor vive en la configuración del adaptador,
 * que es el único que sabe que existe Resend. Un canal que no tenga plantillas puede ignorarlos y
 * enviar el cuerpo.
 *
 * <p><b>Y por eso el cuerpo en texto plano sigue siendo obligatorio.</b> No es un resto del diseño
 * anterior: es lo que sale cuando no hay plantilla configurada. Sin él, un despliegue al que le
 * falte una variable no enviaría un correo feo, no enviaría <b>ninguno</b>.
 *
 * @param destinatario a quién va. Hoy una dirección de correo
 * @param asunto de qué trata. Nunca lleva el secreto: los asuntos se registran en más sitios de los
 *     que uno controla
 * @param cuerpo el mensaje, en texto plano. Siempre presente, y siempre completo por sí solo
 * @param tipo qué clase de mensaje es, o {@code null} si no le corresponde ninguna plantilla
 * @param variables lo que la plantilla necesita, por nombre. Vacío cuando no hay plantilla
 */
public record Notification(
    String destinatario,
    String asunto,
    String cuerpo,
    NotificationKind tipo,
    Map<String, String> variables) {

  public Notification {
    variables = variables == null ? Map.of() : Map.copyOf(variables);
  }

  /** Un mensaje que solo es texto: sin tipo y sin variables. */
  public Notification(String destinatario, String asunto, String cuerpo) {
    this(destinatario, asunto, cuerpo, null, Map.of());
  }
}
