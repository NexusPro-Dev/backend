# SPEC — `RF-CM-029` Elegir si el pago del próximo cierre es automático o manual

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-029` |
| Módulo | `CM` — Comisiones |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 08-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que administración decida, **en las 48 horas anteriores al cierre**, si lo que ese cierre pase a pendiente **se paga solo en el mismo momento** o **se queda para Finanzas**.

---

## 2. Contexto

Es el botón de `requirements/cm.md` v0.43.0 §5.12: «2 días antes aparecerá un botón para indicar si el pago se realizará manual o automático». **Sin elección, el pago es automático** (`RN-CM-053`). `RF-CM-028` dice si la ventana está abierta; este requerimiento es lo que pasa al pulsar.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió | Lo que se descartó |
|---|---|---|
| **Para qué cierre** | **El próximo**, y solo ese | *Un interruptor permanente* — el responsable eligió decidir cierre a cierre |
| **Cuántas veces** | **Las que se quiera** dentro de la ventana; **gana la última** | *Una sola* — un error de clic no tendría arreglo |
| **Volver a automático** | Se elige automático, como cualquier otra elección | *Borrar la elección* — es la misma cosa con otra ruta |
| **Fuera de la ventana** | **Conflicto**, y no cambia nada | *Guardarla para el siguiente* — el responsable eligió que cada cierre se decida en su ventana |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| `SUPERADMIN` o `ADMIN` | Elige (`commission-closings:set-payment-mode`) |

---

## 4. Alcance

### 4.1 Incluye

- Elegir automático o manual para el próximo cierre, dentro de la ventana.
- Cambiar la elección mientras la ventana siga abierta.

### 4.2 No incluye

- **Pagar**: lo hace el cierre (`RF-CM-009`) o Finanzas (`RF-CM-011`, `RF-CM-025`).
- **Elegir para un cierre que no sea el próximo**, ni para el cierre a mano, que no paga.
- **Pagar solo algunos lotes**: el automático paga todos los de ese cierre.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-054` | La ventana, quién elige, que gana la última y que vale para un cierre |
| `RN-CM-053` | Qué hace el cierre con lo elegido |

---

## 6. Datos

### 6.1 Entrada

| Campo | Obligatorio | Descripción |
|---|---|---|
| Modo de pago | Sí | Automático o manual |

### 6.2 Salida

**Lo mismo que `RF-CM-028`**, con la elección ya hecha.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El cierre programado está encendido y la ventana del próximo está abierta |
| Postcondición | El próximo cierre se pagará como se eligió; queda auditado quién eligió qué y cuándo |

---

## 8. Flujo principal

1. Administración elige el modo.
2. Se calcula el próximo cierre y su ventana.
3. Si la ventana está abierta y el cierre de ese turno no ha empezado, se guarda la elección —reemplazando la anterior, si la había— y se audita.
4. Se responde con el próximo cierre y su elección.

---

## 9. Flujos alternativos

### FA-001 — Se elige lo mismo que ya estaba

Se guarda igual, con la persona y la hora nuevas, y se audita. **No es un error**.

### FA-002 — Se elige justo cuando empieza el cierre

O la elección llega antes y el cierre la usa, o el cierre llega antes y la elección responde conflicto. **Nunca se acepta una elección que el cierre ya no va a leer.**

---

## 10. Excepciones

### EX-001 — El cierre programado está apagado

**Conflicto**, como `RF-CM-028`.

### EX-002 — La ventana está cerrada

Antes de abrirse, o con el cierre del turno ya empezado: **conflicto**, diciendo cuándo se abre la del próximo. Nada cambia.

---

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-001` | El modo es obligatorio, y es automático o manual | El modo de pago debe ser AUTOMATICO o MANUAL |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-386` | **Dentro de la ventana**, elegir **manual** lo deja elegido **con quién y cuándo**; la respuesta es la de `RF-CM-028` con esa elección |
| `CA-CM-387` | Elegir **otra vez** dentro de la ventana cambia la elección: **gana la última**, y elegir **automático** vuelve al pago automático |
| `CA-CM-388` | **Fuera de la ventana** —antes de abrirse— responde **conflicto** y no cambia nada |
| `CA-CM-389` | Con el cierre **de ese turno ya empezado**, elegir responde **conflicto**; **elegir y empezar el cierre a la vez**: o el cierre usa la elección, o la elección responde conflicto —nunca se acepta y se ignora— |
| `CA-CM-390` | Un modo **ausente** o que no es automático ni manual se rechaza con `VAL-001` |
| `CA-CM-391` | Cada elección queda **auditada**, con el turno, el modo anterior y el nuevo |
| `CA-CM-392` | Sin `commission-closings:set-payment-mode`, se rechaza —**también con `commission-closings:read-next`**—; con el cierre programado **apagado**, conflicto |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Dos administradores eligen a la vez | Gana la que se guarda última; las dos quedan auditadas |
| Quien eligió es eliminado antes del cierre | La elección vale igual |
| Se cambia la frecuencia del cierre después de elegir | La elección es del turno que había; si el próximo cambia de instante, no le aplica |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 08-10-2026 | Primera versión ([`requirements/cm.md`](../../../requirements/cm.md) v0.43.0 §5.12, `RN-CM-053`, `RN-CM-054`), por decisión del responsable del proyecto: el botón que elige si el pago del próximo cierre es automático o manual, en las 48 horas anteriores. Criterios `CA-CM-386` a `CA-CM-392`. | Responsable del proyecto |
