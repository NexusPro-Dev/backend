# SPEC — `RF-AC-039` Reportar el avance de un video

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-039` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Cómo se construye está en [`plan.md`](plan.md).

---

## 1. Objetivo

Que el sistema sepa **cuánto ha visto** cada alumno de una lección `VIDEO`, y **cuándo la completó**.

## 2. Contexto

El responsable del proyecto pidió el 09-10-2026 «saber por usuario qué tanto ha visto las lecciones» ([`requirements/ac.md`](../../../requirements/ac.md) §5.2.14). El video lo reproduce YouTube o Vimeo **en el navegador**: el backend no lo ve, y **solo sabe lo que el frontend le cuenta**. Este requerimiento es la ruta por la que se lo cuenta.

**Lo que se cuenta es la posición del reproductor**, en segundos enteros, cada pocos segundos mientras el alumno mira y al pausar o salir. El sistema guarda **la mayor alcanzada** (`RN-AC-021`): volver al principio a repasar no borra lo visto, y **un reporte que llega tarde** —la red no garantiza el orden— no puede bajar lo que ya subió uno anterior.

**La lección se completa al 90 %** de su duración, y **completada se queda**. El umbral no es 100 % porque los reproductores no siempre informan el último segundo y los créditos finales no son la lección.

### 2.1 Por qué la posición más lejana y no los segundos reproducidos

**Adelantar el video hasta el final lo completa**, y se acepta a conciencia (`ac.md` §5.2.14). Contar los segundos realmente reproducidos exige que el frontend reporte **tramos** y que el backend los una, a cambio de cerrar una trampa que **solo engaña a quien la hace**: el progreso **no abre nada** (`ac.md` §1.3) — ni otro curso, ni un certificado. El día que abra algo, esto se reabre.

### 2.2 Por qué las mismas puertas que el contenido

El reporte dice «he visto hasta aquí» de algo que solo se puede ver con el contenido en la mano (`RF-AC-035`). **Quien no puede abrir la lección no puede reportar que la vio**: el reporte pasa por las mismas comprobaciones —primero si se ofrece, después si se abre— y responde lo mismo. Así el progreso nunca dice que alguien vio lo que no le correspondía.

### 2.3 Por qué no se audita cada reporte

Es **una escritura de la persona sobre sí misma**, que llega cada pocos segundos mientras mira. Auditar cada una llenaría `audit_log` de ruido sin dar a nadie una pregunta que responder. **La fila guarda cuándo se abrió por primera vez, cuándo por última y cuándo se completó** (`ac.md` §8.7.1), que es lo que una auditoría diría. Es la excepción al Art. V.7 que el responsable del proyecto aprobó el 09-10-2026 (§5.2.14).

## 3. Actores

| Actor | Papel |
|---|---|
| Alumno | Mira el video; **su frontend** reporta la posición |

## 4. Alcance

### 4.1 Incluye

- Guardar la mayor posición alcanzada, acotada a la duración de la lección.
- Completar la lección al llegar al 90 % y no descompletarla nunca.
- Abrir la lección si el reporte llega sin apertura previa (`RN-AC-022`).

### 4.2 No incluye

- **Las lecciones `TEXTO`**: se completan al abrirlas (`RF-AC-035`, `RN-AC-022`).
- **Marcar a mano** una lección como vista o no vista, ni **reiniciar** el progreso.
- **Reportar por otra persona**: el alumno sale del token.

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-013`, `RN-AC-014`, `RN-AC-015` | Las puertas de `RF-AC-035`: se ofrece, y se abre |
| `RN-AC-021` | La posición más lejana, el 90 % y la completitud para siempre |
| `RN-AC-022` | Un reporte sin apertura previa abre la lección |
| `RN-AC-023` | Lo guardado no lo toca nada que haga administración |

## 6. Datos

### 6.1 Entrada

| Dato | Dónde | Obligatorio | Regla |
|---|---|---|---|
| `courseId` | Ruta | Sí | `uuid` |
| `lessonId` | Ruta | Sí | `uuid` |
| `positionSeconds` | Cuerpo | Sí | Entero mayor o igual que cero |

### 6.2 Salida

El avance de la lección para quien reporta: `lessonId`, `watchedSeconds` —lo guardado, que puede ser **mayor** que la posición recién reportada—, `durationSeconds`, `percent` —entero, hacia abajo, tope 100; 100 si está completada—, `completed`, `completedAt`, `firstOpenedAt` y `lastOpenedAt`.

## 7. Precondiciones y postcondiciones

| | |
|---|---|
| Precondición | Sesión con `lessons:track-progress`; la lección se ofrece en ese curso y se le abre |
| Postcondición | Existe una fila de `lesson_progress` para esa persona y esa lección, con `watched_seconds` igual al máximo de lo que había y de la posición acotada, y `last_opened_at` = ahora |

## 8. Flujo principal

1. El alumno mira el video; el frontend envía `PUT …/progress` con la posición.
2. El sistema comprueba que la lección se ofrece en ese curso y que se le abre.
3. Acota la posición a la duración.
4. Guarda el máximo entre lo que había y lo nuevo; si llega al 90 % y no estaba completada, la completa ahora.
5. Responde `200` con el avance.

## 9. Flujos alternativos

### FA-001 — No había abierto la lección

El reporte la abre: primera y última apertura = ahora. Pasa si el frontend guardó la URL del video y no pidió el contenido en esta sesión.

### FA-002 — Reporta una posición menor que la guardada

No cambia `watched_seconds` ni la completitud; mueve `lastOpenedAt`. Responde `200` con lo guardado.

## 10. Excepciones

| Código | Cuándo | Respuesta |
|---|---|---|
| `EX-001` | La lección no existe en ese curso o no se ofrece — **también si es abierta** | `404`, el mensaje de `RF-AC-035` |
| `EX-002` | Se ofrece y ninguna llave la abre | `403` con `memberships` y `products`, como `RF-AC-035` |
| `EX-003` | La lección es `TEXTO` | `422`: «Una lección de texto se completa al abrirla; no reporta avance.» |

## 11. Validaciones

| Código | Campo | Regla |
|---|---|---|
| `VAL-001` | `courseId`, `lessonId`, cuerpo | Identificador mal formado o cuerpo ilegible: `400` |
| `VAL-002` | `positionSeconds` | Ausente o negativo: `400` |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-243` | Un reporte guarda la posición; uno **menor** después **no la baja**; uno **mayor**, sí. La respuesta trae siempre lo guardado |
| `CA-AC-244` | La lección se completa **en el reporte que llega al 90 %** —`completedAt` presente— y no antes (89 % no completa); un reporte posterior menor **no la descompleta** ni mueve `completedAt` |
| `CA-AC-245` | Una posición **mayor que la duración** se guarda como la duración y completa |
| `CA-AC-246` | Una lección `TEXTO` responde `422` con `EX-003` y no escribe nada |
| `CA-AC-247` | Las puertas de `RF-AC-035`: lección de otro curso, inactiva o retirada, o curso que no se ofrece, `404`; cerrada sin llave, `403` con las listas; **ninguno deja fila** |
| `CA-AC-248` | Sin `lessons:track-progress` responde `403` aunque porte `lessons:learn`; `positionSeconds` ausente o negativo, `400` |
| `CA-AC-249` | Un reporte **sin apertura previa** crea la fila con primera y última apertura; el reporte **no escribe en `audit_log`** |

## 13. Casos límite

- **Dos reportes simultáneos**: el máximo lo calcula el motor en la misma sentencia; ninguno pisa al otro.
- **La duración se corrigió a la baja** y lo guardado ya pasa de ella: lo guardado no se recorta (`RN-AC-023`); el porcentaje se acota a 100.
- **La duración se corrigió al alza** después de completar: sigue completada.
- **Posición 0** en la primera llamada: abre la lección con cero segundos.

## 14. Preguntas abiertas

Ninguna. Las cuatro decisiones del responsable están en `ac.md` §5.2.14.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 09-10-2026 | Nace con el progreso del alumno (`ac.md` v0.21.0 §5.2.14). | Responsable técnico |
