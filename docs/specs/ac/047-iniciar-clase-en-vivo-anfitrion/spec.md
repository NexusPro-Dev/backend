# SPEC — `RF-AC-047` Iniciar una clase en vivo como anfitrión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-047` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Objetivo

Que quien dicta la clase entre a la reunión como anfitrión.

## 2. Contexto

La reunión es del **usuario anfitrión de la cuenta** (`ZOOM_HOST_USER`, `RN-AC-030`). Zoom da un **enlace de inicio** que abre la reunión como anfitrión **y caduca a las pocas horas**; por eso no se guarda: se pide a Zoom en el momento. **Da el control de la reunión** —admitir, silenciar, expulsar, grabar—, y por eso **cada entrega se audita**.

## 3. Actores

| Actor | Permiso |
|---|---|
| Administrador | `live-sessions:host` |

## 4. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-029` | Solo lo programado que no ha terminado |
| `RN-AC-030` | El enlace de anfitrión |

## 5. Datos

### 5.1 Entrada

| Dato | Obligatorio | Regla |
|---|---|---|
| Ruta: `POST /api/v1/live-sessions/{id}/host-link` | — | Identificadores `uuid` mal formados: `400` |

### 5.2 Salida

`{ startUrl, startsAt, endsAt }`.

## 6. Excepciones

| Código | Cuándo | Respuesta |
|---|---|---|
| `EX-001` | La clase no existe | `404` |
| `EX-003` | Cancelada o terminada | `409` |
| `EX-004` | Zoom falla o no responde | `503` |

## 7. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-285` | Devuelve el enlace de inicio que Zoom da en ese momento, **sin guardarlo**, y audita la entrega con quién lo pidió |
| `CA-AC-286` | Cancelada o terminada, `409`; inexistente, `404`; Zoom falla, `503` |
| `CA-AC-287` | Sin `live-sessions:host`, `403` aunque porte `live-sessions:read` |

## 8. Preguntas abiertas

Ninguna.

## 9. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 09-10-2026 | Nace con las clases en vivo (`ac.md` v0.23.1 §5.2.15). | Responsable técnico |
