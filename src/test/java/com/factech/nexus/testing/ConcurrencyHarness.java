package com.factech.nexus.testing;

import static org.assertj.core.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Ejecuta varias operaciones <b>a la vez</b> y devuelve lo que le pasó a cada una.
 *
 * <p><b>Por qué hace falta un arnés y no basta con lanzar hilos.</b> Un {@code
 * executor.submit(...)} por tarea no las hace simultáneas: el primer hilo suele arrancar, ejecutar
 * y terminar antes de que el último exista, de modo que la prueba «de concurrencia» acaba
 * comprobando dos operaciones secuenciales y pasa siempre — incluso con la garantía rota. Aquí los
 * hilos se crean, se aparcan en una barrera y se sueltan <b>todos a la vez</b>, que es lo único que
 * hace que el solapamiento ocurra de verdad.
 *
 * <p><b>Ninguna excepción se propaga hacia fuera.</b> Cada tarea devuelve su {@link Outcome}, con
 * el valor o con el fallo, porque en estas pruebas <b>que una falle suele ser el resultado
 * esperado</b>: un alta concurrente debe producir un éxito y un rechazo. Dejar que la excepción
 * subiera obligaría a envolver cada llamada en un {@code try} y perdería cuál de las dos falló.
 *
 * <p><b>Hay tiempo límite y es deliberado.</b> Estas pruebas ejercitan bloqueos de fila y
 * restricciones diferidas: si algo se traba, sin límite la suite se queda colgada sin decir nada.
 * Con él, un interbloqueo se convierte en un fallo con mensaje.
 *
 * <p><b>Un hilo por tarea, sin reutilización.</b> Un grupo más pequeño que el número de tareas las
 * serializaría en silencio, que es exactamente lo que se quiere evitar.
 */
public final class ConcurrencyHarness {

  /** Suficiente para cualquier operación de estas pruebas; el interbloqueo se detecta muy antes. */
  private static final long LIMITE_SEGUNDOS = 30;

  private ConcurrencyHarness() {}

  /**
   * Resultado de una tarea: o el valor, o el fallo. Nunca los dos, nunca ninguno.
   *
   * @param value lo que devolvió, o {@code null} si falló
   * @param failure lo que lanzó, o {@code null} si tuvo éxito
   */
  public record Outcome<T>(T value, Throwable failure) {

    public boolean succeeded() {
      return failure == null;
    }

    public boolean failed() {
      return failure != null;
    }

    /**
     * La causa más profunda del fallo.
     *
     * <p>Lo que interesa de una violación de integridad está varias capas por debajo de lo que
     * Spring acaba lanzando, y comparar contra la excepción externa haría la prueba dependiente de
     * cuántas capas la envuelven.
     */
    public Throwable rootCause() {
      Throwable causa = failure;
      while (causa != null && causa.getCause() != null && causa.getCause() != causa) {
        causa = causa.getCause();
      }
      return causa;
    }
  }

  /**
   * Ejecuta {@code veces} copias de la misma tarea a la vez.
   *
   * <p>Cada copia recibe su índice, para que pueda construir datos distintos cuando lo necesite
   * —dos altas con el mismo código pero distinto nombre, por ejemplo—.
   */
  public static <T> List<Outcome<T>> runTogether(int veces, IndexedTask<T> tarea) {
    List<Callable<T>> tareas = new ArrayList<>(veces);
    for (int i = 0; i < veces; i++) {
      int indice = i;
      tareas.add(() -> tarea.run(indice));
    }
    return runTogether(tareas);
  }

  /** Ejecuta todas las tareas a la vez y devuelve sus resultados en el mismo orden. */
  public static <T> List<Outcome<T>> runTogether(List<Callable<T>> tareas) {
    int total = tareas.size();
    ExecutorService hilos = Executors.newFixedThreadPool(total);

    // `enPosicion` se abre cuando TODOS los hilos han arrancado; `salida` los
    // libera de golpe. Sin las dos, el primero termina antes de que el último
    // exista y no hay concurrencia que probar.
    CountDownLatch enPosicion = new CountDownLatch(total);
    CountDownLatch salida = new CountDownLatch(1);

    try {
      List<Future<Outcome<T>>> futuros = new ArrayList<>(total);
      for (Callable<T> tarea : tareas) {
        futuros.add(
            hilos.submit(
                () -> {
                  enPosicion.countDown();
                  salida.await();
                  try {
                    return new Outcome<>(tarea.call(), null);
                  } catch (Throwable fallo) {
                    return new Outcome<T>(null, fallo);
                  }
                }));
      }

      if (!enPosicion.await(LIMITE_SEGUNDOS, TimeUnit.SECONDS)) {
        return fail("No todos los hilos llegaron a la barrera de salida");
      }
      salida.countDown();

      List<Outcome<T>> resultados = new ArrayList<>(total);
      for (Future<Outcome<T>> futuro : futuros) {
        resultados.add(futuro.get(LIMITE_SEGUNDOS, TimeUnit.SECONDS));
      }
      return Collections.unmodifiableList(resultados);

    } catch (TimeoutException agotado) {
      return fail(
          "Una operación concurrente no terminó en "
              + LIMITE_SEGUNDOS
              + " s. Lo más probable es un interbloqueo entre los bloqueos de fila.",
          agotado);
    } catch (InterruptedException interrumpido) {
      Thread.currentThread().interrupt();
      return fail("La prueba concurrente fue interrumpida", interrumpido);
    } catch (ExecutionException fallo) {
      return fail("Un hilo de la prueba concurrente murió de forma inesperada", fallo);
    } finally {
      hilos.shutdownNow();
    }
  }

  /** Cuenta cuántas tuvieron éxito. */
  public static <T> long exitos(List<Outcome<T>> resultados) {
    return resultados.stream().filter(Outcome::succeeded).count();
  }

  /** Cuenta cuántas fallaron. */
  public static <T> long fallos(List<Outcome<T>> resultados) {
    return resultados.stream().filter(Outcome::failed).count();
  }

  /** Una tarea que sabe cuál de las copias simultáneas es. */
  @FunctionalInterface
  public interface IndexedTask<T> {
    T run(int indice) throws Exception;
  }
}
