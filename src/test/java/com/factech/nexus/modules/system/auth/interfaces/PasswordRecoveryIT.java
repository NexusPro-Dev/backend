package com.factech.nexus.modules.system.auth.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.shared.notification.Notification;
import com.factech.nexus.shared.notification.NotificationSender;
import com.factech.nexus.shared.security.PasswordHasher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Recuperar la propia contraseña olvidada (`RF-SP-040`).
 *
 * <p><b>El envío se sustituye por un doble que captura</b>, y no porque el adaptador real no exista
 * —existe, es Resend desde el 26-08-2026— sino porque lo que aquí se verifica es el permiso y no el
 * correo. Una prueba que llamara al proveedor de verdad dependería de la red, de una cuenta y de
 * una cuota, y fallaría por motivos que no tienen nada que ver con lo que comprueba.
 *
 * <p>El doble es además lo único que da acceso al permiso en claro: el servidor solo guarda su
 * hash, de modo que la única forma de completar el flujo es leerlo del mensaje — igual que hace la
 * persona que lo recibe.
 */
@AutoConfigureMockMvc
@Import(PasswordRecoveryIT.CorreoCapturado.class)
class PasswordRecoveryIT extends IntegrationTestBase {

  private static final String ADMIN_ROL = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final String CLAVE = "ClaveLargaYSegura2026";
  private static final String NUEVA = "OtraClaveLargaDistinta2026";

  /** Extrae el permiso del cuerpo del mensaje, que es lo que hace quien lo recibe. */
  private static final Pattern CODIGO = Pattern.compile("Su código es: (\\S+)");

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private PasswordHasher hasher;
  @Autowired private Buzon buzon;

  private UUID persona;

  @BeforeEach
  void preparar() {
    limpiar();
    buzon.vaciar();
    persona = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status)
        VALUES (?, 'JPerez', 'juan@factech.co', 'Juan', 'Pérez', ?, false, 'ACTIVO')
        """,
        persona,
        hasher.hash(CLAVE));
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", persona, ADMIN_ROL);
  }

  @AfterEach
  void limpiarDespues() {
    limpiar();
  }

  private void limpiar() {
    // La auditoría también: varias pruebas de esta clase CUENTAN eventos, y sin
    // vaciarla heredarían los que dejó la anterior — un fallo que además
    // dependería del orden de ejecución.
    jdbc.update("DELETE FROM audit_security_log");
    jdbc.update("DELETE FROM password_reset_permits");
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_roles WHERE user_id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
  }

  // ---------------------------------------------------------------------------
  // El recorrido completo
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-456` — el flujo entero, sin que intervenga nadie más")
  void elFlujoCompleto() throws Exception {
    mvc.perform(solicitar("JPerez")).andExpect(status().isAccepted());

    String permiso = permisoRecibido();
    mvc.perform(confirmar(permiso, NUEVA)).andExpect(status().isNoContent());

    // Entra con la nueva y no con la anterior.
    mvc.perform(login("JPerez", NUEVA)).andExpect(status().isOk());
    mvc.perform(login("JPerez", CLAVE)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("`CA-SP-457` — la respuesta es IDÉNTICA exista o no la identidad")
  void respuestaIndistinguible() throws Exception {
    String existente = cuerpo(solicitar("JPerez"));
    String inexistente = cuerpo(solicitar("no-existe-nadie-asi"));

    assertThat(sinCorrelacion(inexistente)).isEqualTo(sinCorrelacion(existente));

    // Y una es un envío y la otra no, que es lo que la respuesta no debe delatar.
    esperarMensajes(1);
  }

  @Test
  @DisplayName("se solicita con el correo igual que con el nombre de usuario")
  void tambienConElCorreo() throws Exception {
    mvc.perform(solicitar("juan@factech.co")).andExpect(status().isAccepted());
    esperarMensajes(1);
  }

  @Test
  @DisplayName("`CA-SP-458` — el permiso no sirve dos veces")
  void unSoloUso() throws Exception {
    mvc.perform(solicitar("JPerez"));
    String permiso = permisoRecibido();

    mvc.perform(confirmar(permiso, NUEVA)).andExpect(status().isNoContent());
    mvc.perform(confirmar(permiso, "TerceraClaveDistinta2026"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));
  }

  @Test
  @DisplayName("`CA-SP-459` — el caducado se rechaza igual que uno inventado, byte a byte")
  void elCaducadoNoSeDistingue() throws Exception {
    mvc.perform(solicitar("JPerez"));
    String permiso = permisoRecibido();
    caducar();

    String caducado = cuerpo(confirmar(permiso, NUEVA));
    String inventado = cuerpo(confirmar("no-es-un-permiso-de-verdad", NUEVA));

    assertThat(sinCorrelacion(caducado)).isEqualTo(sinCorrelacion(inventado));
  }

  @Test
  @DisplayName("`CA-SP-460` — emitir uno nuevo invalida el anterior: nunca hay dos puertas")
  void emitirInvalidaElAnterior() throws Exception {
    mvc.perform(solicitar("JPerez"));
    String primero = permisoRecibido();

    mvc.perform(solicitar("JPerez"));
    // El SEGUNDO mensaje, dicho de forma explícita: con el envío en otro hilo,
    // pedir «el último» podría devolver el primero.
    String segundo = permisoRecibido(2);
    assertThat(segundo).isNotEqualTo(primero);

    mvc.perform(confirmar(primero, NUEVA)).andExpect(status().isUnprocessableEntity());
    mvc.perform(confirmar(segundo, NUEVA)).andExpect(status().isNoContent());

    // Sustituido y consumido se distinguen en la base, que es lo que la
    // auditoría necesita: uno es «pidió dos veces» y el otro «completó».
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM password_reset_permits WHERE superseded_at IS NOT NULL",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM password_reset_permits WHERE consumed_at IS NOT NULL",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-SP-461` — la contraseña débil se rechaza SIN consumir el permiso")
  void laPoliticaNoConsume() throws Exception {
    mvc.perform(solicitar("JPerez"));
    String permiso = permisoRecibido();

    mvc.perform(confirmar(permiso, "corta")).andExpect(status().isBadRequest());

    // El permiso sigue sirviendo: el error fue de la persona legítima, y
    // obligarla a pedir otro —y a esperar otro correo— por escribir una
    // contraseña corta sería castigar el intento correcto.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM password_reset_permits WHERE consumed_at IS NULL",
                Integer.class))
        .isEqualTo(1);
    mvc.perform(confirmar(permiso, NUEVA)).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("`CA-SP-462` — se revocan todas las sesiones y el token de acceso deja de valer")
  void cortaLosAccesosVigentes() throws Exception {
    String token = campo(cuerpo(login("JPerez", CLAVE)), "accessToken");
    mvc.perform(get("/api/v1/audit/changes").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    mvc.perform(solicitar("JPerez"));
    mvc.perform(confirmar(permisoRecibido(), NUEVA)).andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/audit/changes").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class))
        .isZero();
  }

  @Test
  @DisplayName("`CA-SP-463` — la cuenta NO queda marcada para cambio obligatorio")
  void noMarcaCambioObligatorio() throws Exception {
    mvc.perform(solicitar("JPerez"));
    mvc.perform(confirmar(permisoRecibido(), NUEVA)).andExpect(status().isNoContent());

    // Es la diferencia deliberada con `RF-SP-038`: esta contraseña la eligió su
    // titular y nadie más la conoce, de modo que no hay ventana que cerrar.
    var fila =
        jdbc.queryForMap(
            "SELECT must_change_password, provisional_password_expires_at FROM users WHERE id = ?",
            persona);
    assertThat(fila.get("must_change_password")).isEqualTo(false);
    assertThat(fila.get("provisional_password_expires_at")).isNull();

    // Y navega: no queda retenida por `MustChangePasswordFilter`.
    String token = campo(cuerpo(login("JPerez", NUEVA)), "accessToken");
    mvc.perform(get("/api/v1/audit/changes").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("`CA-SP-464` y `CA-SP-465` — ni levanta el bloqueo ni cambia el estado")
  void noTocaEstadoNiBloqueo() throws Exception {
    jdbc.update(
        """
        UPDATE users SET status = 'BLOQUEADO', locked_until = now() + interval '1 hour'
         WHERE id = ?
        """,
        persona);

    // Se admite la solicitud: rechazarla revelaría que la cuenta está
    // bloqueada, y esa fuga rompe la defensa central.
    mvc.perform(solicitar("JPerez")).andExpect(status().isAccepted());
    mvc.perform(confirmar(permisoRecibido(), NUEVA)).andExpect(status().isNoContent());

    var fila = jdbc.queryForMap("SELECT status, locked_until FROM users WHERE id = ?", persona);
    assertThat(fila.get("status")).isEqualTo("BLOQUEADO");
    assertThat(fila.get("locked_until")).isNotNull();
  }

  @Test
  @DisplayName("`CA-SP-466` — ni la contraseña ni el permiso quedan en la base ni en la respuesta")
  void nadaEnClaro() throws Exception {
    String respuesta = cuerpo(solicitar("JPerez"));
    String permiso = permisoRecibido();

    assertThat(respuesta).doesNotContain(permiso);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM password_reset_permits WHERE permit_hash = ?",
                Integer.class,
                permiso))
        .isZero();

    mvc.perform(confirmar(permiso, NUEVA));
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, persona))
        .doesNotContain(NUEVA);
  }

  @Test
  @DisplayName(
      "`CA-SP-469` — la solicitud sobre identidad inexistente se registra SIN la identidad")
  void auditaSinDelatarLaIdentidad() throws Exception {
    mvc.perform(solicitar("jperez-que-no-existe@factech.co")).andExpect(status().isAccepted());

    var eventos =
        jdbc.queryForList(
            """
            SELECT target_user_id, outcome, detail::text AS detail
              FROM audit_security_log
             WHERE event_type = 'PASSWORD_RESET' AND outcome = 'FAILURE'
            """);

    assertThat(eventos).hasSize(1);
    assertThat(eventos.get(0).get("target_user_id")).isNull();
    // Registrarla convertiría el registro de seguridad en la lista de
    // identidades que alguien está sondeando.
    assertThat((String) eventos.get(0).get("detail")).doesNotContain("jperez-que-no-existe");
  }

  @Test
  @DisplayName("`CA-SP-468` — solicitud y restablecimiento se registran con severidad alta")
  void auditaConSeveridadAlta() throws Exception {
    mvc.perform(solicitar("JPerez"));
    mvc.perform(confirmar(permisoRecibido(), NUEVA)).andExpect(status().isNoContent());

    var etapas =
        jdbc.queryForList(
            """
            SELECT detail->>'stage' AS stage
              FROM audit_security_log
             WHERE event_type = 'PASSWORD_RESET' AND outcome = 'SUCCESS' AND severity = 'ALTA'
             ORDER BY occurred_at
            """);

    assertThat(etapas).hasSize(2);
    assertThat(etapas.get(0).get("stage")).isEqualTo("SOLICITUD");
    assertThat(etapas.get(1).get("stage")).isEqualTo("CONFIRMACION");
  }

  @Test
  @DisplayName("`CA-SP-475` — un fallo del envío NO altera ninguna de las dos respuestas")
  void elFalloDelEnvioNoSeNota() throws Exception {
    buzon.romper();

    // El permiso se emitió igual y la respuesta es la de siempre: el envío corre
    // fuera de la transacción y fuera de la respuesta.
    mvc.perform(solicitar("JPerez")).andExpect(status().isAccepted());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_permits", Integer.class))
        .isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // Casos límite
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("la solicitud sobre una cuenta ELIMINADA se trata como identidad inexistente")
  void cuentaEliminada() throws Exception {
    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", persona);

    mvc.perform(solicitar("JPerez")).andExpect(status().isAccepted());
    comprobarQueNoSeEnvioNada();
  }

  @Test
  @DisplayName("quien tenía cambio obligatorio pendiente y lo olvidó, sale limpio por esta vía")
  void limpiaLaMarcaDeCambioObligatorio() throws Exception {
    jdbc.update(
        """
        UPDATE users
           SET must_change_password = true,
               provisional_password_expires_at = now() + interval '2 days'
         WHERE id = ?
        """,
        persona);

    mvc.perform(solicitar("JPerez"));
    mvc.perform(confirmar(permisoRecibido(), NUEVA)).andExpect(status().isNoContent());

    assertThat(
            jdbc.queryForObject(
                "SELECT provisional_password_expires_at FROM users WHERE id = ?",
                Object.class,
                persona))
        .isNull();
  }

  @Test
  @DisplayName("el permiso emitido y nunca usado caduca solo: no queda nada abierto")
  void elNoUsadoCaducaSolo() throws Exception {
    mvc.perform(solicitar("JPerez"));
    String permiso = permisoRecibido();

    caducar();

    mvc.perform(confirmar(permiso, NUEVA)).andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName("faltar el permiso o la contraseña es `400`, y con su campo señalado")
  void formato() throws Exception {
    mvc.perform(confirmar(null, NUEVA))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(confirmar("algo", null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    mvc.perform(
            post("/api/v1/auth/password-recovery")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder solicitar(String identificador) {
    return post("/api/v1/auth/password-recovery")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"identifier\":\"%s\"}".formatted(identificador));
  }

  private MockHttpServletRequestBuilder confirmar(String permiso, String contrasena) {
    String cuerpo =
        "{\"permit\":%s,\"newPassword\":%s}".formatted(comilla(permiso), comilla(contrasena));
    return post("/api/v1/auth/password-recovery/confirmation")
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private static String comilla(String valor) {
    return valor == null ? "null" : "\"" + valor + "\"";
  }

  /**
   * Envejece el permiso hasta dejarlo caducado.
   *
   * <p><b>Se mueve también {@code created_at}</b>, y no sobra: {@code
   * ck_password_reset_permits_periodo} exige que la caducidad sea posterior a la creación, de modo
   * que dejar la fecha de creación donde está hace fallar el {@code UPDATE} con una violación de
   * restricción que no tiene nada que ver con lo que la prueba comprueba.
   */
  private void caducar() {
    jdbc.update(
        """
        UPDATE password_reset_permits
           SET created_at = now() - interval '2 hours',
               expires_at = now() - interval '1 hour'
        """);
  }

  private MockHttpServletRequestBuilder login(String identificador, String clave) {
    return post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identificador, clave));
  }

  /**
   * El permiso, leído del mensaje. Es la única forma de obtenerlo: el servidor guarda su hash.
   *
   * <p><b>Espera</b>, porque el envío corre en otro hilo. Ese desacople es la mitad de la defensa
   * del requerimiento —sin él la respuesta tarda distinto según exista la identidad, y el endpoint
   * dice qué cuentas hay—, y su precio es que aquí no basta con mirar el buzón: hay que darle
   * tiempo a llegar.
   */
  private String permisoRecibido() {
    return permisoRecibido(1);
  }

  /**
   * El permiso del mensaje n-ésimo, contando desde uno.
   *
   * <p>El ordinal se declara y no se toma «el último» que haya: con el envío en otro hilo, «el
   * último» puede ser todavía el anterior, y la prueba compararía el permiso consigo mismo — y
   * pasaría.
   */
  private String permisoRecibido(int ordinal) {
    List<Notification> recibidos = esperarMensajes(ordinal);

    Matcher encontrado = CODIGO.matcher(recibidos.get(ordinal - 1).cuerpo());
    assertThat(encontrado.find()).as("el mensaje no lleva el código").isTrue();
    return encontrado.group(1);
  }

  /** Espera a que lleguen al menos tantos mensajes, o falla diciendo cuántos llegaron. */
  private List<Notification> esperarMensajes(int cuantos) {
    long limite = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < limite) {
      List<Notification> recibidos = buzon.mensajes();
      if (recibidos.size() >= cuantos) {
        return recibidos;
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException interrumpido) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    assertThat(buzon.mensajes())
        .as("se esperaban %d mensajes y no llegaron", cuantos)
        .hasSize(cuantos);
    return buzon.mensajes();
  }

  /**
   * Comprueba que <b>no</b> llega ningún mensaje.
   *
   * <p>Una ausencia no se puede esperar hasta verla, de modo que se le da un margen y se comprueba
   * después. Sin ese margen la prueba pasaría siempre —incluso enviando— por llegar antes que el
   * hilo del envío, que es la peor forma de que una prueba de ausencia deje de servir.
   */
  private void comprobarQueNoSeEnvioNada() {
    try {
      Thread.sleep(500);
    } catch (InterruptedException interrumpido) {
      Thread.currentThread().interrupt();
    }
    assertThat(buzon.mensajes()).as("se envió un mensaje y no debía").isEmpty();
  }

  private String cuerpo(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getContentAsString();
  }

  private String campo(String cuerpo, String nombre) throws Exception {
    JsonNode arbol = json.readTree(cuerpo);
    return arbol.get(nombre).asText();
  }

  private static String sinCorrelacion(String cuerpo) {
    return cuerpo.replaceAll("\"correlationId\":\"[^\"]*\"", "\"correlationId\":\"\"");
  }

  // ---------------------------------------------------------------------------

  /** Doble del canal de envío que conserva lo que se le entrega. */
  public static final class Buzon implements NotificationSender {

    private final List<Notification> recibidos = new CopyOnWriteArrayList<>();
    private volatile boolean roto;

    @Override
    public void send(Notification notificacion) {
      if (roto) {
        // El adaptador real NO propaga sus fallos, y esta prueba verifica que
        // quien llama tampoco depende de ello. Aquí se lanza a propósito para
        // comprobar justamente eso.
        throw new IllegalStateException("el proveedor rechazó el envío");
      }
      recibidos.add(notificacion);
    }

    public List<Notification> mensajes() {
      return List.copyOf(recibidos);
    }

    public void vaciar() {
      recibidos.clear();
      roto = false;
    }

    public void romper() {
      roto = true;
    }
  }

  @TestConfiguration
  static class CorreoCapturado {

    @Bean
    @Primary
    Buzon buzon() {
      return new Buzon();
    }
  }
}
