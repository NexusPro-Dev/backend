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
 * <p><b>Qué se programa hoy:</b> solo la purga de sesiones caducadas. Cada tarea que se sume debe
 * poder <b>apagarse por configuración</b> y no debe suponer que es la única instancia: el
 * despliegue con réplicas es cuestión de tiempo (<b>D-09</b>), y una tarea que corre tres veces
 * sobre las mismas filas es un problema difícil de ver desde los síntomas.
 *
 * <p><b>El planificador por defecto tiene un solo hilo.</b> Con una tarea es exactamente lo que
 * hace falta; en cuanto haya dos que puedan solaparse, una tarda y la otra se retrasa sin que nadie
 * lo note. Ese es el momento de declarar un {@code TaskScheduler} con tamaño propio, y no antes.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
