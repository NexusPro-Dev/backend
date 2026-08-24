package com.factech.nexus.testing;

import static com.factech.nexus.testing.ConcurrencyHarness.exitos;
import static com.factech.nexus.testing.ConcurrencyHarness.fallos;
import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El arnés se prueba a sí mismo.
 *
 * <p><b>No es ceremonia.</b> Una prueba de concurrencia que no llega a solapar nada pasa siempre
 * —incluso con la garantía que dice verificar completamente rota— y no hay forma de notarlo mirando
 * su resultado: sale verde igual. Si el arnés serializa, las seis pruebas de concurrencia de las
 * tres familias de catálogo dejan de significar nada de golpe y en silencio.
 *
 * <p>Por eso lo que se comprueba aquí es lo único que no puede comprobarse desde ellas: que las
 * tareas <b>se estaban ejecutando a la vez</b>.
 */
class ConcurrencyHarnessTest {

  @Test
  @DisplayName("las tareas se solapan de verdad: todas están vivas en el mismo instante")
  void seSolapan() {
    int tareas = 4;

    List<Outcome<long[]>> resultados =
        runTogether(
            tareas,
            indice -> {
              long inicio = System.nanoTime();
              // Suficiente para que el solapamiento sea inequívoco y lo bastante
              // corto para no ralentizar la suite.
              Thread.sleep(120);
              return new long[] {inicio, System.nanoTime()};
            });

    assertThat(exitos(resultados)).isEqualTo(tareas);

    long ultimoInicio = resultados.stream().mapToLong(r -> r.value()[0]).max().orElseThrow();
    long primerFin = resultados.stream().mapToLong(r -> r.value()[1]).min().orElseThrow();

    // Si la última en arrancar lo hizo ANTES de que la primera terminara,
    // hubo un instante en que las cuatro estaban dentro a la vez. Con ejecución
    // secuencial esto es imposible por construcción.
    assertThat(ultimoInicio)
        .as("las tareas se ejecutaron una después de otra: el arnés no está solapando")
        .isLessThan(primerFin);
  }

  @Test
  @DisplayName("un fallo se captura y se devuelve; no tumba a las demás ni sube hacia fuera")
  void losFallosSeCapturan() {
    // En estas pruebas que una tarea falle suele ser el resultado ESPERADO —un
    // alta concurrente produce un éxito y un rechazo—, de modo que el arnés no
    // puede propagar la excepción ni cancelar al resto.
    List<Callable<String>> tareas =
        List.of(
            () -> "bien",
            () -> {
              throw new IllegalStateException("fallo deliberado");
            },
            () -> "también bien");

    List<Outcome<String>> resultados = runTogether(tareas);

    assertThat(exitos(resultados)).isEqualTo(2);
    assertThat(fallos(resultados)).isEqualTo(1);
    assertThat(resultados.get(0).value()).isEqualTo("bien");
    assertThat(resultados.get(2).value()).isEqualTo("también bien");
  }

  @Test
  @DisplayName("la causa raíz atraviesa las capas de envoltura")
  void causaRaiz() {
    // Lo que interesa de una violación de integridad está varias capas por
    // debajo de lo que Spring acaba lanzando.
    List<Outcome<Void>> resultados =
        runTogether(
            1,
            indice -> {
              throw new RuntimeException(
                  "capa externa", new IllegalArgumentException("capa interna"));
            });

    assertThat(resultados.get(0).rootCause())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("capa interna");
  }

  @Test
  @DisplayName("los resultados llegan en el mismo orden en que se enviaron las tareas")
  void ordenEstable() {
    // Sin esta garantía, una prueba que distinga «la primera» de «la segunda»
    // —activar frente a desactivar, por ejemplo— no podría escribirse.
    List<Outcome<Integer>> resultados = runTogether(5, indice -> indice);

    assertThat(resultados).extracting(Outcome::value).containsExactly(0, 1, 2, 3, 4);
  }
}
