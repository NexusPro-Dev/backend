# SPEC — `RF-AC-045` Corregir una clase en vivo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-045` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Objetivo

Cambiar título, descripción, curso, inicio, fin o quién puede entrar.

## 2. Contexto

**Solo lo que viene**, y **las listas, si vienen, se reemplazan enteras**: es la forma de decir «ahora la abren estas». **Lo que Zoom conoce se corrige allí primero** —título, inicio y fin, como inicio y duración—; si Zoom falla, `503` y nada cambia. **Quitar a alguien de la lista no lo des-registra en Zoom**: no recibirá un enlace nuevo (`RN-AC-027`), pero el que ya tenía sigue valiendo hasta que la clase termine. Se acepta: des-registrar en Zoom exigiría saber a quién quitó el cambio, y una corrección de listas suele abrir, no cerrar.

## 3. Actores

| Actor | Permiso |
|---|---|
| Administrador | `live-sessions:update` |

## 4. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-025` | Las listas y el curso |
| `RN-AC-026` | Zoom primero |
| `RN-AC-029` | Solo lo programado que no ha terminado; inicio y fin |

## 5. Datos

### 5.1 Entrada

| Dato | Obligatorio | Regla |
|---|---|---|
| Ruta: `PATCH /api/v1/live-sessions/{id}` | — | Identificadores `uuid` mal formados: `400` |

### 5.2 Salida

El detalle de `RF-AC-043`.

## 6. Excepciones

| Código | Cuándo | Respuesta |
|---|---|---|
| `EX-001` | La clase no existe | `404` |
| `EX-002` | Curso, membresía o producto que no valen | `422` |
| `EX-003` | Está cancelada o ya terminó | `409` |
| `EX-004` | Zoom falla o no responde | `503`, nada cambia |

## 7. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-288` | Corrige solo lo que viene; un título, un inicio o un fin nuevos **llegan a Zoom** con la duración que resulta; una descripción nueva no llama a Zoom |
| `CA-AC-289` | Las listas que vienen **reemplazan** a las anteriores; una lista vacía deja la clase de todos; la que no viene no se toca |
| `CA-AC-290` | Cancelada o terminada, `409`; inicio y fin nuevos con las mismas validaciones que al programar —contra el inicio o el fin guardados si solo viene uno— |
| `CA-AC-291` | Zoom falla: `503` y la fila sin cambios; se audita como `UPDATE` con el antes y el después |
| `CA-AC-292` | Sin `live-sessions:update`, `403` aunque porte `live-sessions:update-own` |

## 8. Preguntas abiertas

Ninguna.

## 9. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 09-10-2026 | Nace con las clases en vivo (`ac.md` v0.23.1 §5.2.15). | Responsable técnico |
