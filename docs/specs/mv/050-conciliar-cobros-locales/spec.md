# SPEC — `RF-MV-050` Conciliar los cobros pendientes con la pasarela local

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-050` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 05-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

---

## 1. Objetivo

Que ningún cobro de la pasarela local se quede pendiente para siempre **porque su aviso no llegó**.

---

## 2. Contexto

([`requirements/mv.md`](../../../requirements/mv.md) v0.78.0 §4.10, `RN-MV-064`.) PayRetailers **no reenvía** un aviso que no se recibió —un despliegue, una red caída, un aviso que nunca salió—. Sin otra vía, el cliente habría pagado y su compra seguiría pendiente. **El barrido pregunta por cada cobro pendiente que lleva un rato sin noticias**, y aplica lo que la pasarela conteste **exactamente como un aviso** (`RF-MV-049`).

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Cada cuánto** | Configurable; por omisión, **cada cinco minutos** |
| **Por qué cobros pregunta** | Los pendientes de la pasarela local con cobro abierto y **más de diez minutos** sin noticias —configurable—: los recientes esperan a su aviso |
| **Cuántos por pasada** | Un lote acotado; el resto, en la siguiente |
| **Una instancia a la vez** | Si el sistema corre en varias, solo una barre en cada pasada |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| El sistema | Barre en cada pasada |

---

## 4. Alcance

### 4.1 Incluye

- Preguntar por los cobros pendientes y aplicar la respuesta, como un aviso.

### 4.2 No incluye

- **Ninguna ruta**: no lo invoca nadie.
- **Los cobros de la tarjeta**, que tienen su notificación firmada y reentregada.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-064` | El barrido pregunta por los que no llegan |

---

## 6. Datos

Ninguno de entrada ni de salida: su efecto son los pagos conciliados.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | La pasarela local está configurada |
| Postcondición | Cada cobro preguntado queda en el estado que dijo la pasarela |

---

## 8. Flujo principal

1. En cada pasada, el sistema toma los cobros pendientes de la pasarela local con más de diez minutos sin noticias, hasta un lote.
2. Por cada uno, pregunta a la pasarela y aplica la respuesta como `RF-MV-049`.

---

## 9. Flujos alternativos

### FA-001 — La pasarela no responde por un cobro

Se sigue con el siguiente; ese cobro se volverá a preguntar en la próxima pasada.

---

## 10. Excepciones

Ninguna visible: el barrido no responde a nadie. Los fallos se registran en el log.

---

## 11. Validaciones

Ninguna.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-620` | Un cobro pendiente **sin aviso** y con más de diez minutos, que la pasarela da por **aprobado**, queda confirmado tras una pasada, con su venta |
| `CA-MV-621` | Un cobro de **menos de diez minutos** no se pregunta |
| `CA-MV-622` | Si la pasarela **no responde** por un cobro, los demás del lote se concilian igual |
| `CA-MV-623` | Apagada la pasarela, el barrido **no hace nada** |
| `CA-MV-624` | El barrido y un aviso del mismo cobro **no confirman dos veces** |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Un Efecty que el cliente paga días después | Se pregunta en cada pasada hasta que la pasarela lo da por final |

---

## 14. Preguntas abiertas

**Cuándo dejar de preguntar** por un cobro que lleva días pendiente. Hoy se pregunta hasta que termine; si el volumen lo pide, se espacian las preguntas.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 05-10-2026 | Primera versión, con la pasarela local ([`requirements/mv.md`](../../../requirements/mv.md) v0.78.0 §4.10, `RN-MV-064`). Barrido configurable, por omisión cada cinco minutos, por los pendientes con más de diez minutos sin noticias. Criterios `CA-MV-620` a `CA-MV-624`. | Responsable del proyecto |
