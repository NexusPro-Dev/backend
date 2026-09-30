# SPEC — `RF-MV-031` Consultar mis compras de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-031` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que quien compró puntos vea **qué compras ha hecho y en qué quedó cada una**: si todavía espera, si ya tiene los puntos o por qué se rechazó.

---

## 2. Contexto

**Sin esto, una compra pendiente es invisible para quien la hizo** ([`requirements/mv.md`](../../../requirements/mv.md) v0.54.0 §4.4). «Mis compras» (`RF-MV-008`) trae solo ventas (`RN-MV-047`), y el historial de saldos (`RF-MV-022`) solo lo que movió un saldo: una compra pendiente o rechazada no movió nada. Es la tercera lectura propia del módulo, con los mismos filtros que las otras.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Solo las propias** | La persona ve las suyas y nada más; no hay filtro por persona |
| **Sin detalle aparte** | Cada fila ya lo trae todo —tasa, puntos, pago, motivo—, y un detalle repetiría la fila |
| **Las más recientes primero** | Como los demás listados |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:list-own-points-purchases` | Consulta sus compras de puntos. Lo porta todo rol por su tipo |

---

## 4. Alcance

### 4.1 Incluye

- Las compras de puntos de quien pregunta, paginadas, con estado, moneda, importe, tasa, puntos, pago y, si se rechazó, el motivo.
- Filtrar por estado, moneda, comprobante y periodo.

### 4.2 No incluye

- **Las compras de otras personas**: administración las ve en `RF-MV-006` filtrando por el tipo.
- **Lo gastado en puntos**: está en el historial de saldos (`RF-MV-022`).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-026` | «Propias» son aquellas cuyo sujeto es quien pregunta |
| `RN-MV-037` | El comprobante se busca por fragmento |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Página y tamaño | No | Los de siempre |
| Estado | No | Pendiente, confirmada o rechazada |
| Moneda | No | |
| Comprobante | No | Un fragmento |
| Desde / hasta | No | Cuándo se compró |

### 6.2 Salida

**Una página de compras**, cada una como la devuelve `RF-MV-027`.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:list-own-points-purchases` |
| Postcondición | Ninguna: es una lectura |

---

## 8. Flujo principal

1. El actor pide sus compras, con los filtros que quiera.
2. El sistema devuelve la página, las más recientes primero.

---

## 9. Flujos alternativos

### FA-001 — No ha comprado nunca

Página vacía.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Estado desconocido o periodo invertido | Rechazo, **los dos errores juntos** si son los dos |
| `EX-002` | Quien pide no tiene `movements:list-own-points-purchases` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Estado, si viene, uno de los tres |
| `VAL-002` | Desde no posterior a hasta |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-344` | Devuelve **las compras de puntos del actor** —pendientes, confirmadas y rechazadas—, las más recientes primero, cada una con su tasa, sus puntos, su pago y, si se rechazó, su motivo |
| `CA-MV-345` | **No trae** las de otra persona, ni sus ventas, retiros o bonos |
| `CA-MV-346` | Filtra por **estado**, por **moneda**, por **fragmento de comprobante** y por **periodo** |
| `CA-MV-347` | Un estado desconocido y un periodo invertido responden rechazo, **los dos juntos** |
| `CA-MV-348` | Sin compras, página **vacía** |
| `CA-MV-349` | Un cliente —rol de tipo `CONSUMIDOR`— la consulta; sin `movements:list-own-points-purchases` responde prohibido y sin autenticar, `401` |
| `CA-MV-350` | La página se resuelve en **un número fijo de consultas**, cualquiera que sea el número de filas |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Una compra rechazada | Aparece, con su motivo |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 30-09-2026 | Primera versión, con la etapa 3 de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.54.0 §4.4). **Solo las propias**, sin detalle aparte, con los filtros de los demás listados. Criterios `CA-MV-344` a `CA-MV-350`. | Responsable del proyecto |
