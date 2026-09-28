package com.factech.nexus.shared.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita las tareas programadas del sistema (issue #25).
 *
 * <p>Sin esta clase, {@code @Scheduled} <b>no hace nada</b> y no avisa: la anotación queda ahí, la
 * aplicación arranca sin una queja y la tarea sencillamente nunca corre. Es el peor modo de fallar
 * —silencioso y con aspecto de estar hecho—, y por eso la habilitación vive en un sitio propio con
 * su motivo escrito, en lugar de como una anotación más en la clase de arranque.
 *
 * <p><b>Qué se programa hoy:</b> la purga de sesiones caducadas y, desde el 28-09-2026, el cierre
 * del periodo de comisiones (`RF-CM-009`). Cada tarea que se sume debe poder <b>apagarse por
 * configuración</b> y no debe suponer que es la única instancia: el despliegue con réplicas es
 * cuestión de tiempo (<b>D-09</b>), y una tarea que corre tres veces sobre las mismas filas es un
 * problema difícil de ver desde los síntomas.
 *
 * <p><b>El planificador tiene dos hilos desde que hay dos tareas</b> ({@code
 * spring.task.scheduling.pool.size}). Con uno, el cierre de comisiones —que barre y cierra, y puede
 * tardar— retrasaría la purga sin que nadie lo notara. Este Javadoc lo pedía para el día en que
 * hubiera dos, y ese día es el 28-09-2026. <b>Va por propiedad y no con un bean</b>: declarar un
 * {@code ThreadPoolTaskScheduler} apaga el {@code applicationTaskExecutor} que Spring configura
 * solo, y la recuperación de contraseña depende de él — se probó y el contexto no arrancaba.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
