# SPEC — `RF-SP-018` Consultar detalle de una membresía

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-018` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Conocer una membresía concreta y su posición exacta en la cadena: qué nivel tiene por encima y cuál por debajo.

## 2. Contexto

Antes de insertar una membresía nueva hay que saber entre qué dos niveles va a quedar. Este detalle responde esa pregunta sobre un punto concreto de la cadena, sin recorrerla entera.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Consulta cualquier membresía |
| Administrador | Consulta cualquier membresía |

## 4. Alcance

### 4.1 Incluye

- Datos de la membresía: código, nombre, descripción y nivel.
- Su membresía superior y su membresía hija.

### 4.2 No incluye

- Modificarla: las membresías son inmutables (`RN-SP-008`).
- Las personas que la tienen → módulo `USR`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-006` | Toda membresía está sujeta a una de mayor nivel, salvo la superior | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador | Sí | Membresía que se consulta | Debe existir |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Membresía | Código, nombre, descripción y nivel |
| Membresía superior | La de mayor nivel a la que está sujeta, vacía en la superior |
| Membresía hija | La que queda inmediatamente por debajo, vacía en la inferior |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de membresías.
- La membresía existe.

**Postcondiciones**

- Ninguna: la consulta no altera el estado del sistema.

## 8. Flujo principal

1. El actor solicita el detalle de una membresía.
2. El sistema recupera la membresía, su superior y su hija.
3. El sistema devuelve el detalle.

## 9. Flujos alternativos

### FA-001 — Membresía superior de la cadena

**Cuándo ocurre:** la membresía no está sujeta a ninguna otra.

1. El sistema devuelve la membresía superior vacía.

### FA-002 — Membresía inferior de la cadena

**Cuándo ocurre:** ninguna membresía cuelga de ella.

1. El sistema devuelve la membresía hija vacía.

## 10. Excepciones

### EX-001 — Membresía inexistente

**Condición:** el identificador no corresponde a ninguna membresía.
**Respuesta del sistema:** informa que la membresía no existe.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador de la membresía no es válido. |
| `VAL-002` | Membresía existente | La membresía solicitada no existe. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-125` | El sistema devuelve la membresía con su nivel, su superior y su hija |
| `CA-SP-126` | El sistema devuelve la superior vacía al consultar la membresía superior de la cadena |
| `CA-SP-127` | El sistema devuelve la hija vacía al consultar la membresía inferior |
| `CA-SP-128` | El sistema informa que la membresía no existe cuando el identificador no corresponde a ninguna |
| `CA-SP-129` | El sistema rechaza la consulta a un actor sin el permiso de lectura de membresías |

## 13. Casos límite

- **Única membresía del sistema:** superior e hija vacías a la vez. Es válido.
- **Membresía recién insertada:** su superior y su hija deben reflejar ya el reordenamiento.
- **Identificador con formato incorrecto:** se rechaza por validación, no como membresía inexistente.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | ¿Debe devolver la cadena completa por encima y por debajo, o solo el vecino inmediato? Lo segundo basta para insertar; lo primero evita varias consultas para pintar la jerarquía | Responsable técnico | Abierta |
| 2 | ¿Se accede por identificador o también por código? | Responsable técnico | Abierta |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
