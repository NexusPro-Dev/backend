# SPEC — `RF-MV-053` Consultar los ajustes de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-053` |
| Módulo | `MV` — Movimientos |
| Versión | 0.2.0 |
| Estado | **Retirada** — 06-10-2026, la sustituye `RF-MV-056` |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 05-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que administración vea **los ajustes de puntos ya hechos** (`RF-MV-052`): a quién, cuántos, por qué, con qué comprobante y **quién los hizo**. Es la tabla de la pantalla de ajustes, junto al formulario.

---

## 2. Contexto

El ajuste se veía hasta hoy en el historial de saldos de cada persona (`RF-MV-022`) y en el libro entero (`RF-MV-006`), pero ninguno de los dos responde «¿qué ajustes se hicieron esta semana, y quién los hizo?». Lo pidió el frontend en nombre del responsable del proyecto el 05-10-2026 ([`requirements/mv.md`](../../../requirements/mv.md) v0.84.0 §4.11).

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Quién lo hizo va en la fila** | El ajuste guarda desde ahora quién lo registró. Los anteriores no lo tienen y salen sin él |
| **Sin documento de identidad** | Ni en la fila ni en la búsqueda, con el criterio del listado de personas de `SP` |
| **Solo ajustes** | Ni bonos ni compras de puntos: tienen sus propias consultas |
| **Más recientes primero** | Y se puede ordenar también por puntos y por comprobante |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:list-points-adjustments` | Consulta los ajustes de cualquier persona |

---

## 4. Alcance

### 4.1 Incluye

- Los ajustes de puntos de todas las personas, paginados.
- Filtros por persona, moneda, periodo y sentido, y una búsqueda libre.
- Ordenar por fecha, puntos o comprobante.

### 4.2 No incluye

- El detalle de un ajuste: es el de cualquier movimiento (`RF-MV-007`).
- Exportar.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-076` | Qué es un ajuste y qué guarda |
| `RN-SEG-014` | Un permiso propio |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Página y tamaño | No | Como en los demás listados |
| Orden | No | Fecha (por omisión, descendente), puntos o comprobante, ascendente o descendente |
| Persona | No | Los de una persona |
| Moneda | No | Los de una moneda |
| Desde, hasta | No | Sobre cuándo ocurrió; desde inclusive, hasta exclusive |
| Sentido | No | `SUMA` o `RESTA` |
| Búsqueda | No | Fragmento del comprobante, el motivo, la referencia, o el nombre, usuario o correo de la persona; sin distinguir acentos ni mayúsculas |

### 6.2 Salida

Una página de ajustes. Cada uno: identificador, comprobante, estado, **la persona** —identificador, nombre completo, usuario y correo—, la moneda, **los puntos con su signo**, motivo, referencia, cuándo ocurrió y se confirmó, y **quién lo hizo** —identificador y nombre completo—, nulo si es anterior a que se guardara.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:list-points-adjustments` |
| Postcondición | Nada cambia |

---

## 8. Flujo principal

1. El actor pide la página, con los filtros y el orden que quiera.
2. El sistema valida todo junto.
3. Devuelve la página.

---

## 9. Flujos alternativos

Ninguno.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Página, tamaño, orden, sentido o periodo inválidos | Rechazo, con todos los problemas juntos |
| `EX-002` | Sin `movements:list-points-adjustments` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Página y tamaño dentro de los límites de los listados |
| `VAL-002` | Orden por un campo admitido y en un sentido válido |
| `VAL-003` | Sentido `SUMA` o `RESTA` |
| `VAL-004` | Desde no posterior a hasta |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-647` | Devuelve los ajustes de todas las personas, **los más recientes primero**, con la persona, la moneda, los puntos con signo, motivo, referencia y **quién lo hizo** |
| `CA-MV-648` | Persona, moneda y periodo filtran, y se combinan |
| `CA-MV-649` | `SUMA` devuelve solo los positivos y `RESTA` solo los negativos |
| `CA-MV-650` | La búsqueda encuentra por comprobante, motivo, referencia, nombre, usuario y correo, sin distinguir acentos ni mayúsculas |
| `CA-MV-651` | Se ordena por fecha, puntos o comprobante, en los dos sentidos |
| `CA-MV-652` | Un orden, un sentido o un periodo inválidos, o una página fuera de límites, responden `400` con todos los problemas juntos |
| `CA-MV-653` | **Solo ajustes**: un bono o una compra de puntos no aparecen |
| `CA-MV-654` | La fila **no publica el documento** de la persona |
| `CA-MV-655` | Sin `movements:list-points-adjustments` responde prohibido; sin autenticar, `401` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Un ajuste anterior a que se guardara quién lo hizo | Sale con quién lo hizo nulo |
| Un filtro sin coincidencias | Página vacía, no un error |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 05-10-2026 | Primera versión ([`requirements/mv.md`](../../../requirements/mv.md) v0.84.0 §4.11), pedida por el frontend para la pantalla de ajustes. Criterios `CA-MV-647` a `CA-MV-655`. | Responsable técnico |
| 0.2.0 | 06-10-2026 | **Retirada** ([`requirements/mv.md`](../../../requirements/mv.md) v0.88.0 §4.12): la sustituye [`RF-MV-056`](../056-consultar-movimientos-de-puntos/spec.md), que trae también las compras de puntos y hereda lo que aquí se decidió. `movements:list-points-adjustments` se renombra a `movements:list-points-movements`. | Responsable del proyecto |
