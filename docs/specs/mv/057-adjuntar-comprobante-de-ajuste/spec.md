# SPEC — `RF-MV-057` Adjuntar el comprobante de un ajuste de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-057` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que el ajuste de puntos guarde **el soporte mismo** —la foto o el PDF de la consignación— y no solo su número: **al ajustar o después**, y que se pueda **reemplazar** si se subió el equivocado.

---

## 2. Contexto

El ajuste (`RF-MV-052`) nació con una referencia de texto y dejó fuera, a propósito, el archivo. El responsable del proyecto lo pidió el 06-10-2026 ([`requirements/mv.md`](../../../requirements/mv.md) v0.88.0 §4.12): **uno por ajuste, opcional, PDF, PNG o JPG**, al ajustar o después.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Dos caminos** | Con el ajuste, en la misma petición —todo o nada—, o después sobre un ajuste ya hecho |
| **Reemplazar sustituye** | No se acumulan versiones; la auditoría guarda el resumen del anterior |
| **El tipo lo dice el contenido** | Ni el nombre ni lo que declare el cliente: un archivo que no empieza como un PDF, un PNG o un JPG se rechaza |
| **Un permiso para adjuntar después** | Distinto de ajustar (`RN-SEG-014`): corregir un soporte no es mover puntos. Con el ajuste, viaja con el permiso de ajustar |
| **Una petición repetida con archivo** | Es la misma solo si trae el mismo archivo que se guardó; si no, es otra petición con la misma clave |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:adjust-points` | Adjunta el comprobante al ajustar |
| Quien tiene `movements:attach-points-receipt` | Adjunta o reemplaza el comprobante de un ajuste hecho |

---

## 4. Alcance

### 4.1 Incluye

- Ajustar con el comprobante en la misma petición.
- Adjuntar el comprobante a un ajuste que no lo tiene, o reemplazar el que tiene.

### 4.2 No incluye

- Quitar el comprobante sin poner otro.
- Varios archivos por ajuste, o comprobantes en compras de puntos.
- Consultarlo y descargarlo: `RF-MV-055` y `RF-MV-056`.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-076` | El ajuste que lo lleva |
| `RN-MV-077` | Qué archivo se admite, cuántos, y qué audita |
| `RN-SEG-014` | Un permiso propio para adjuntar después |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| El ajuste | Al ajustar | Lo de `RF-MV-052`, con la clave de idempotencia |
| El archivo | Al adjuntar después; opcional al ajustar | PDF, PNG o JPG, hasta 5 MB |
| El movimiento | Al adjuntar después | Qué ajuste |

### 6.2 Salida

**Al ajustar**: la respuesta de `RF-MV-052` y, si se adjuntó, los datos del comprobante. **Al adjuntar después**: los datos del comprobante —nombre, tipo, tamaño, resumen y cuándo se subió—.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene el permiso del camino que usa |
| Postcondición | El ajuste tiene ese archivo como comprobante, y el anterior, si lo había, ya no existe |
| Postcondición | Queda auditado el nombre, tipo, tamaño y resumen del nuevo y, al reemplazar, del anterior |

---

## 8. Flujo principal

1. El actor envía el archivo, con el ajuste o sobre un ajuste hecho.
2. El sistema reconoce el tipo por el contenido y comprueba el tamaño, **antes de escribir nada**.
3. Guarda el comprobante —con el ajuste, en la misma operación— y lo audita.

---

## 9. Flujos alternativos

| ID | Flujo |
|---|---|
| `FA-001` | El ajuste ya tenía comprobante: se reemplaza |
| `FA-002` | La petición de ajuste se repite con la misma clave y el mismo archivo: responde el ajuste ya hecho, sin escribir nada |

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Archivo vacío, mayor de 5 MB o de otro tipo | Rechazo; **nada se escribe, tampoco el ajuste** |
| `EX-002` | Al adjuntar después, el movimiento no existe o no es un ajuste | No encontrado |
| `EX-003` | La clave de un ajuste repetido llega con otro archivo, o con archivo cuando el primero no lo tenía | Conflicto, como cualquier petición distinta con la misma clave |
| `EX-004` | Sin el permiso | Prohibido |

Las demás, las de `RF-MV-052`.

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Al adjuntar después, el archivo es obligatorio |
| `VAL-002` | No vacío |
| `VAL-003` | Empieza como un PDF, un PNG o un JPG |
| `VAL-004` | Hasta 5 MB |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-685` | Ajustar con un PDF, un PNG o un JPG deja el ajuste **y** el comprobante, y la respuesta lo describe |
| `CA-MV-686` | Ajustar sin archivo, por la misma ruta de siempre, se comporta como antes |
| `CA-MV-687` | Ajustar con un archivo inválido **no crea el ajuste** ni mueve puntos |
| `CA-MV-688` | Repetir el ajuste con la misma clave y el mismo archivo responde el ya hecho; con otro archivo, o con archivo donde no lo había, conflicto |
| `CA-MV-689` | Adjuntar a un ajuste sin comprobante lo deja con él |
| `CA-MV-690` | Adjuntar a un ajuste con comprobante **lo reemplaza**: la descarga devuelve el nuevo |
| `CA-MV-691` | El tipo se reconoce **por el contenido**: un texto llamado `.pdf` se rechaza, y un PNG llamado `.jpg` se guarda como PNG |
| `CA-MV-692` | Vacío o mayor de 5 MB se rechaza con su motivo, por cualquiera de los dos caminos |
| `CA-MV-693` | Adjuntar a una compra de puntos o a un movimiento inexistente responde `404` |
| `CA-MV-694` | La auditoría guarda nombre, tipo, tamaño y resumen —del anterior también al reemplazar—, **nunca el contenido** |
| `CA-MV-695` | Sin `movements:attach-points-receipt` no se adjunta después, aunque se tenga `movements:adjust-points`; sin autenticar, `401` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Un archivo de exactamente 5 MB | Se admite |
| Un nombre con ruta o caracteres de control | Se guarda solo el nombre, limpio |
| Sin nombre | Se guarda uno por omisión con la extensión de su tipo |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión ([`requirements/mv.md`](../../../requirements/mv.md) v0.88.0 §4.12, `RN-MV-077`), con las decisiones del responsable del proyecto: un comprobante opcional por ajuste, PDF, PNG o JPG hasta 5 MB, al ajustar o después, reemplazable. Criterios `CA-MV-685` a `CA-MV-695`. | Responsable del proyecto |
