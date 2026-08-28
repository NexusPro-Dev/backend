package com.factech.nexus.modules.system.roles.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.roles.application.RoleHierarchyLock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * El bloqueo que serializa la jerarquía de roles (`RF-SP-008` · `T-04`).
 *
 * <p><b>Por qué esta clase existe aparte de la prueba concurrente.</b> `CA-SP-161` comprueba el
 * <b>efecto</b> —que no queda ciclo— y lo hace ejercitando una carrera, de modo que su capacidad de
 * detectar un fallo depende de que el solapamiento ocurra. Esto comprueba el <b>mecanismo</b>, y es
 * determinista: no hay ventana que acertar ni resultado que dependa de la temporización.
 *
 * <p><b>Las dos propiedades que se verifican son las dos que el plan eligió</b>, y ninguna es
 * gratuita: que el intento <b>no espere</b> —si esperara, una reubicación lenta encadenaría
 * peticiones colgadas ocupando cada una su conexión del pool— y que se <b>libere ante un fallo</b>
 * —si no, una excepción no prevista dejaría la jerarquía inmovilizada hasta reiniciar el proceso—.
 *
 * <p><b>Cómo se prueba «no espera» sin medir el reloj.</b> Una aserción de tiempo sería frágil en
 * una máquina cargada. En su lugar, el segundo hilo intenta el bloqueo <b>mientras el primero lo
 * tiene tomado con certeza</b>, y el primero no suelta su transacción hasta que el segundo avisa de
 * que ya lo intentó. Si el intento se encolara, ese aviso no llegaría nunca y la prueba fallaría
 * por tiempo agotado en vez de dar un falso verde.
 */
class RoleHierarchyLockIT extends IntegrationTestBase {

  /** Generoso: lo que se detecta con él es un encolamiento, no una lentitud. */
  private static final long LIMITE_SEGUNDOS = 20;

  @Autowired private RoleHierarchyLock cerrojo;
  @Autowired private PlatformTransactionManager transacciones;

  @Test
  @DisplayName("una segunda transacción recibe la negativa DE INMEDIATO, no se encola")
  void noEspera() throws Exception {
    CountDownLatch tomado = new CountDownLatch(1);
    CountDownLatch intentado = new CountDownLatch(1);

    ExecutorService hilos = Executors.newFixedThreadPool(2);
    try {
      Future<Boolean> primero =
          hilos.submit(
              () ->
                  enTransaccion(
                      () -> {
                        boolean obtenido = cerrojo.tryAcquire();
                        tomado.countDown();
                        // No suelta la transacción hasta que el segundo haya
                        // intentado: así el intento ocurre con el bloqueo
                        // tomado con certeza, y no por casualidad.
                        esperar(intentado);
                        return obtenido;
                      }));

      Future<Boolean> segundo =
          hilos.submit(
              () -> {
                esperar(tomado);
                try {
                  return enTransaccion(cerrojo::tryAcquire);
                } finally {
                  intentado.countDown();
                }
              });

      assertThat(primero.get(LIMITE_SEGUNDOS, TimeUnit.SECONDS))
          .as("el primero debe obtener el bloqueo")
          .isTrue();
      assertThat(segundo.get(LIMITE_SEGUNDOS, TimeUnit.SECONDS))
          .as("el segundo debe recibir la negativa mientras el primero lo tiene")
          .isFalse();
    } finally {
      // Que el primero salga aunque la aserción haya reventado antes.
      intentado.countDown();
      hilos.shutdownNow();
    }
  }

  @Test
  @DisplayName("se libera también cuando la transacción FALLA")
  void seLiberaAlFallar() {
    // Es la razón de usar `_xact_` y no un bloqueo de sesión: quien lo toma no
    // tiene nada que liberar, y por tanto no hay forma de olvidarse.
    try {
      enTransaccion(
          () -> {
            assertThat(cerrojo.tryAcquire()).isTrue();
            throw new IllegalStateException("algo revienta después de tomar el bloqueo");
          });
      fail("La transacción debía propagar el fallo");
    } catch (IllegalStateException esperado) {
      // Es el desenlace que la prueba provoca.
    }

    assertThat(enTransaccion(cerrojo::tryAcquire))
        .as("tras el fallo, el bloqueo debe estar libre")
        .isTrue();
  }

  @Test
  @DisplayName("dentro de la MISMA transacción se puede volver a tomar")
  void esReentranteEnSuPropiaTransaccion() {
    // PostgreSQL cuenta los bloqueos consultivos por sesión, de modo que quien
    // ya lo tiene vuelve a obtenerlo. Se deja escrito porque lo contrario
    // —que un servicio se bloqueara a sí mismo al tomarlo dos veces— sería un
    // fallo silencioso y muy difícil de leer.
    assertThat(
            enTransaccion(
                () -> {
                  assertThat(cerrojo.tryAcquire()).isTrue();
                  return cerrojo.tryAcquire();
                }))
        .isTrue();
  }

  private <T> T enTransaccion(java.util.concurrent.Callable<T> trabajo) {
    return new TransactionTemplate(transacciones)
        .execute(
            estado -> {
              try {
                return trabajo.call();
              } catch (RuntimeException fallo) {
                throw fallo;
              } catch (Exception fallo) {
                throw new IllegalStateException(fallo);
              }
            });
  }

  private static void esperar(CountDownLatch senal) {
    try {
      if (!senal.await(LIMITE_SEGUNDOS, TimeUnit.SECONDS)) {
        fail("La señal no llegó en " + LIMITE_SEGUNDOS + " s: el intento se encoló.");
      }
    } catch (InterruptedException interrumpido) {
      Thread.currentThread().interrupt();
      fail("La prueba fue interrumpida", interrumpido);
    }
  }
}
