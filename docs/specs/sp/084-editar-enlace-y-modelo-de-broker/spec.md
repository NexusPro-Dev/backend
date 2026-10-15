# SPEC — `RF-SP-084` Editar el enlace y el modelo de un broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-084` |
| Módulo | `SP` — Sistema Principal |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 15-10-2026 |

---

## 1. Objetivo

Que administración cambie el enlace de registro y el modelo de un broker sin esperar una migración.

## 2. Contexto

El catálogo de brokers se pobló siempre por migración (`RN-SP-039`). El enlace (`url`, `V93`) y el modelo (`model`, `V111`) son datos que cambian con la operación —un enlace de afiliado nuevo, otro modelo de comisión— y pedir una migración por cada uno es lento. El responsable del proyecto lo pidió el 15-10-2026: «que se pueda editar, pero solo el enlace y el modelo».

### 2.1 Lo que este requerimiento decide

- **Solo `url` y `model`.** El nombre es la clave de negocio; el `advertiser` clasifica los avisos (`RN-SP-076`) y cambiarlo reasignaría los que ya llegaron; el estado decide qué ve el registro. Los tres siguen siendo de migración.
- **Ausente o nulo no se toca; cadena vacía vacía.** Es el patrón de las ediciones del sistema (`RF-MV-034`: nulo = sin cambio), con una forma explícita de quitar el valor.
- **Se edita también un broker inactivo**: el catálogo público no lo muestra, pero sus cuentas siguen existiendo.

## 3. Actores

Administración, con `brokers:update`.

## 4. Alcance

### 4.1 Incluye

`PATCH /api/v1/brokers/{id}` con `url` y `model`.

### 4.2 No incluye

Crear, eliminar, activar o desactivar brokers; cambiar su nombre o su `advertiser`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-039` | El catálogo de brokers no se administra por API, salvo su enlace y su modelo (enmendada el 15-10-2026) | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| `id` | Sí | El broker, en la ruta | `uuid` |
| `url` | No | Enlace de registro. `""` lo vacía | Empieza por `http://` o `https://`; hasta 500 caracteres |
| `model` | No | Modelo, texto libre. `""` lo vacía | Hasta 120 caracteres |

Al menos uno de los dos. Cualquier otro campo del cuerpo es `400`. Los valores se recortan.

### 6.2 Salida

El broker con la forma de una fila de `RF-SP-052`: `id`, `name`, `url`, `model`.

## 7. Precondiciones y postcondiciones

**Precondición:** el broker existe.

**Postcondición:** el enlace y el modelo son los pedidos, y queda **auditado** quién los cambió, con el antes y el después.

## 8. Flujo principal

1. Administración envía el enlace, el modelo o los dos.
2. El sistema valida, bloquea el broker, aplica lo que cambia y lo audita.
3. Devuelve el broker.

## 9. Flujos alternativos

| # | Caso | Resultado |
|---|---|---|
| 1 | Los valores pedidos son los que ya tiene | `200` con el broker, **sin escribir ni auditar** |

## 10. Excepciones

| Código | Caso | Respuesta |
|---|---|---|
| `VAL-001` | Sin `url` ni `model`; enlace sin `http(s)://` o de más de 500; modelo de más de 120; campo desconocido | `400` |
| `VAL-002` | El broker no existe | `404` |
| `AUTH-001` / `AUTH-002` | Sin token / sin el permiso | `401` / `403` |

## 11. Validaciones

Las de §10. **El enlace se valida antes del motor**: `ck_brokers_url` lo rechazaría igual, pero como `500`.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-1020` | Con el permiso, cambiar `url` y `model` devuelve `200` con el broker editado, `GET /brokers` lo publica, y queda **auditado** con el antes y el después |
| `CA-SP-1021` | Un campo ausente o nulo no cambia; `""` lo vacía; si nada cambia, no se audita |
| `CA-SP-1022` | Sin campos, enlace sin `http(s)://`, valores demasiado largos o un campo como `name` dan `400`; un broker inexistente, `404`; sin el permiso, `403`. Nada cambia |
| `CA-SP-1023` | `V112` siembra `brokers:update` a `SUPERADMIN` y `ADMIN`, y a nadie más |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Broker inactivo | Se edita igual |
| Dos administradores editan a la vez | El bloqueo los ordena; gana el último |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Qué campos? | Solo enlace y modelo (responsable del proyecto, 15-10-2026) |
| 2 | ¿Cómo se vacía un campo? | Con cadena vacía; nulo o ausente no lo toca |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 15-10-2026 | Redacción inicial, a petición del responsable del proyecto. Criterios `CA-SP-1020` a `CA-SP-1023`. | Responsable del proyecto |
