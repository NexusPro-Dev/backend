package com.factech.nexus.modules.system.auth.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.shared.notification.Notification;
import com.factech.nexus.shared.notification.NotificationSender;
import com.factech.nexus.shared.security.PasswordHasher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
 * `CA-SP-473` — el tiempo de respuesta no distingue identidad existente de inexistente.
 *
 * <p><b>Qué prueba esta clase, exactamente.</b> Que el <b>envío saliente no está en el camino de la
 * respuesta</b>. Es la mitad de la defensa del requerimiento y la que se implementa mal con más
 * facilidad: igualar el mensaje es trivial y se ve en el diff; igualar el tiempo no se ve en
 * ninguna parte, y una implementación que espere al envío responde igual de bien y filtra igual de
 * mal — porque emitir y enviar cuesta cientos de milisegundos y eso se mide desde fuera con un
 * cronómetro.
 *
 * <p><b>El envío del doble tarda a propósito.</b> Sin esa espera la prueba pasaría también con una
 * implementación acoplada, y no diría nada. Con ella, esperar al envío es exactamente lo que la
 * hace fallar.
 *
 * <p><b>Qué NO prueba, y conviene que esté escrito.</b> No prueba que los dos caminos ejecuten el
 * mismo trabajo: el que encuentra la identidad escribe además el permiso, y eso son unos
 * milisegundos de más que ninguna decisión de diseño elimina. Lo que la defensa quita es la
 * diferencia <b>dominante</b> —el envío—, y el margen declarado abajo es lo que separa una de otra.
 *
 * <p><b>Se comparan medianas y no promedios</b>, y sobre repeticiones: un promedio lo desplaza una
 * sola pausa del recolector de basura, y la prueba pasaría a fallar por motivos que no tienen nada
 * que ver con lo que comprueba.
 */
@AutoConfigureMockMvc
@Import(PasswordRecoveryTimingIT.EnvioLento.class)
class PasswordRecoveryTimingIT extends IntegrationTestBase {

  private static final String CLAVE = "ClaveLargaYSegura2026";

  /** Cuánto tarda el envío simulado. Es el coste que el desacople saca de la respuesta. */
  private static final Duration ENVIO = Duration.ofMillis(300);

  /** Suficientes para que la mediana signifique algo, pocas para no alargar la suite. */
  private static final int REPETICIONES = 11;

  /**
   * Margen declarado, y el número tiene que justificarse (Art. VII.3).
   *
   * <p>Cubre lo que el camino con identidad hace de más —sustituir el permiso anterior, insertar el
   * nuevo, leer el correo— más el ruido de una máquina compartida. Es <b>menos de la mitad</b> de
   * lo que tarda el envío simulado, que es lo que hace que la prueba distinga: una implementación
   * que esperase al envío se pasaría del margen por un factor de seis, no por un pelo.
   */
  private static final long MARGEN_MS = 50;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @Autowired private PasswordHasher hasher;

  @BeforeEach
  void preparar() {
    limpiar();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status)
        VALUES (?, 'JPerez', 'juan@factech.co', 'Juan', 'Pérez', ?, false, 'ACTIVO')
        """,
        UUID.randomUUID(),
        hasher.hash(CLAVE));
  }

  @AfterEach
  void limpiarDespues() {
    limpiar();
  }

  private void limpiar() {
    jdbc.update("DELETE FROM password_reset_permits");
    jdbc.update("DELETE FROM audit_security_log");
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
  }

  @Test
  @DisplayName("`CA-SP-473` — la respuesta no espera al envío, y por eso los tiempos no delatan")
  void elTiempoNoDistingue() throws Exception {
    // Una vuelta en vacío: la primera petición paga el calentamiento del
    // despachador y de las sentencias, y compararla con el resto mediría eso.
    mvc.perform(solicitar("JPerez"));
    mvc.perform(solicitar("nadie-con-este-nombre"));

    long existente = medianaDe("JPerez");
    long inexistente = medianaDe("nadie-con-este-nombre");
    long diferencia = Math.abs(existente - inexistente);

    assertThat(diferencia)
        .as(
            "la mediana con identidad existente fue %d ms y sin ella %d ms: %d ms de diferencia,"
                + " sobre un envío de %d ms. Una diferencia de ese orden significa que la"
                + " respuesta está esperando al envío, y con eso el endpoint dice qué cuentas"
                + " existen",
            existente, inexistente, diferencia, ENVIO.toMillis())
        .isLessThan(MARGEN_MS);
  }

  private long medianaDe(String identificador) throws Exception {
    List<Long> muestras = new ArrayList<>();
    for (int vuelta = 0; vuelta < REPETICIONES; vuelta++) {
      long inicio = System.nanoTime();
      mvc.perform(solicitar(identificador));
      muestras.add((System.nanoTime() - inicio) / 1_000_000);
    }
    muestras.sort(Long::compare);
    return muestras.get(muestras.size() / 2);
  }

  private MockHttpServletRequestBuilder solicitar(String identificador) {
    return post("/api/v1/auth/password-recovery")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"identifier\":\"%s\"}".formatted(identificador));
  }

  // ---------------------------------------------------------------------------

  /**
   * Canal de envío que <b>tarda</b>.
   *
   * <p>Es lo que da sentido a la prueba: con un doble instantáneo, una implementación que esperase
   * al envío pasaría igual. Un proveedor real tarda esto y más.
   */
  static final class Lento implements NotificationSender {

    @Override
    public void send(Notification notificacion) {
      try {
        Thread.sleep(ENVIO.toMillis());
      } catch (InterruptedException interrumpido) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @TestConfiguration
  static class EnvioLento {

    @Bean
    @Primary
    NotificationSender envioLento() {
      return new Lento();
    }
  }
}
