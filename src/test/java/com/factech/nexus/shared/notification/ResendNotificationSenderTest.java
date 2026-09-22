package com.factech.nexus.shared.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Qué se le manda a Resend (`architecture.md` §15.1).
 *
 * <p><b>Lo que se verifica es la petición y no el correo.</b> El adaptador no dibuja nada: elige
 * entre la plantilla alojada y el texto plano, y esa elección es la única lógica que tiene. Se
 * comprueba contra un doble del API porque llamar al proveedor de verdad ataría la suite a la red,
 * a una cuenta y a una cuota.
 *
 * <p><b>Las dos ramas son excluyentes por exigencia del proveedor</b>, que rechaza una petición con
 * plantilla y texto a la vez. Por eso cada prueba comprueba también la <b>ausencia</b> del otro
 * campo: mandar los dos no daría un correo con estilos, daría un `422`.
 */
class ResendNotificationSenderTest {

  private static final String REMITENTE = "NEXUS <no-responder@ejemplo.test>";
  private static final String DESTINO = "alguien@ejemplo.test";

  @Test
  @DisplayName("con plantilla configurada manda la plantilla y sus variables, y ningún texto")
  void mandaLaPlantillaCuandoEstaConfigurada() {
    RestClient.Builder constructor = RestClient.builder();
    MockRestServiceServer proveedor = MockRestServiceServer.bindTo(constructor).build();

    proveedor
        .expect(requestTo("https://api.resend.com/emails"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer re_de_prueba"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.from").value(REMITENTE))
        .andExpect(jsonPath("$.to[0]").value(DESTINO))
        .andExpect(jsonPath("$.subject").value("Restablecer su contraseña"))
        .andExpect(jsonPath("$.template.id").value("nexus-password-recovery"))
        .andExpect(jsonPath("$.template.variables.CODIGO").value("un-permiso-cualquiera"))
        .andExpect(jsonPath("$.template.variables.MINUTOS").value("30"))
        // El proveedor rechaza la petición que lleve las dos cosas.
        .andExpect(jsonPath("$.text").doesNotExist())
        .andRespond(withSuccess("{\"id\":\"env-1\"}", MediaType.APPLICATION_JSON));

    new ResendNotificationSender(
            ajustes(Map.of("password-recovery", "nexus-password-recovery")), constructor)
        .send(recuperacion());

    proveedor.verify();
  }

  @Test
  @DisplayName("sin plantilla configurada cae al texto plano en lugar de no enviar")
  void caeAlTextoPlanoCuandoNoHayPlantilla() {
    RestClient.Builder constructor = RestClient.builder();
    MockRestServiceServer proveedor = MockRestServiceServer.bindTo(constructor).build();

    proveedor
        .expect(requestTo("https://api.resend.com/emails"))
        .andExpect(jsonPath("$.text").value(Matchers.containsString("un-permiso-cualquiera")))
        .andExpect(jsonPath("$.template").doesNotExist())
        .andRespond(withSuccess("{\"id\":\"env-2\"}", MediaType.APPLICATION_JSON));

    new ResendNotificationSender(ajustes(Map.of()), constructor).send(recuperacion());

    proveedor.verify();
  }

  /**
   * La trampa del valor por omisión vacío.
   *
   * <p>Cada plantilla se declara en `application.yml` como {@code ${VARIABLE:}}, de modo que en un
   * entorno que no la configure la clave <b>existe</b> y vale la cadena vacía. Tomarla por presente
   * mandaría una plantilla llamada «» y el correo se perdería con un rechazo que nadie mira.
   */
  @Test
  @DisplayName("una plantilla en blanco cuenta como ausente y no como plantilla vacía")
  void laPlantillaEnBlancoCuentaComoAusente() {
    RestClient.Builder constructor = RestClient.builder();
    MockRestServiceServer proveedor = MockRestServiceServer.bindTo(constructor).build();

    proveedor
        .expect(requestTo("https://api.resend.com/emails"))
        .andExpect(jsonPath("$.text").exists())
        .andExpect(jsonPath("$.template").doesNotExist())
        .andRespond(withSuccess("{\"id\":\"env-3\"}", MediaType.APPLICATION_JSON));

    new ResendNotificationSender(ajustes(Map.of("password-recovery", "   ")), constructor)
        .send(recuperacion());

    proveedor.verify();
  }

  /**
   * El puerto declara que no lanza, y quien lo llama ya respondió.
   *
   * <p>Para cuando esto corre, el permiso está emitido y la respuesta viajó: propagar el fallo no
   * desharía nada y solo rompería el hilo que lo intentó.
   */
  @Test
  @DisplayName("un rechazo del proveedor no se propaga")
  void noPropagaElFalloDelProveedor() {
    RestClient.Builder constructor = RestClient.builder();
    MockRestServiceServer proveedor = MockRestServiceServer.bindTo(constructor).build();

    proveedor.expect(requestTo("https://api.resend.com/emails")).andRespond(withServerError());

    ResendNotificationSender canal =
        new ResendNotificationSender(
            ajustes(Map.of("password-recovery", "nexus-password-recovery")), constructor);

    assertThatCode(() -> canal.send(recuperacion())).doesNotThrowAnyException();
    proveedor.verify();
  }

  @Test
  @DisplayName("sin credencial no se llama al proveedor")
  void noLlamaAlProveedorSinCredencial() {
    RestClient.Builder constructor = RestClient.builder();
    MockRestServiceServer proveedor = MockRestServiceServer.bindTo(constructor).build();

    NotificationSettings apagado = new NotificationSettings(false, "", "", null, null, Map.of());

    new ResendNotificationSender(apagado, constructor).send(recuperacion());

    // Ninguna expectativa declarada: cualquier llamada habría fallado la prueba.
    proveedor.verify();
  }

  // ---------------------------------------------------------------------------

  private static NotificationSettings ajustes(Map<String, String> plantillas) {
    return new NotificationSettings(true, "re_de_prueba", REMITENTE, null, null, plantillas);
  }

  /** El mismo mensaje que arma `RequestPasswordRecoveryService`, con las dos versiones. */
  private static Notification recuperacion() {
    return new Notification(
        DESTINO,
        "Restablecer su contraseña",
        "Su código es: un-permiso-cualquiera\n\nCaduca en 30 minutos.",
        NotificationKind.PASSWORD_RECOVERY,
        Map.of("CODIGO", "un-permiso-cualquiera", "MINUTOS", "30"));
  }
}
