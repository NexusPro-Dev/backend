# SPEC — `RF-AC-004` Editar categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-004` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Corregir lo que se declaró de una categoría —nombre, descripción, color, icono y orden— sin tocar lo que no se pidió, y **reordenarla** cambiando su número.

## 2. Contexto

Es `RF-PM-004` para categorías y hereda entera su mecánica: corrección **parcial** —solo lo que viene cambia—, **el nulo explícito es una orden** donde el vacío es un estado legítimo, la unicidad del nombre frente a los otros vivos, la auditoría solo de lo que cambió, y la respuesta en la forma del detalle. **Lo que no tiene es inmutables**: la categoría no lleva código ni moneda, de modo que los cinco campos se corrigen y no hay nada que rechazar «porque no se puede cambiar». Y **no tiene la regla del icono**: color e icono son obligatorios siempre (`RN-AC-003`), de modo que ninguno de los dos admite vaciarse, con portada o sin ella.

**Reordenar es corregir el número.** `RN-AC-002` dice que el orden es una posición y no una identidad: cambiar el de una categoría no mueve a las demás, y «ponerla tercera» son tantas correcciones como categorías haya que desplazar. Se acepta a conciencia (§14.1).

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Corrige la categoría |

## 4. Alcance

### 4.1 Incluye

- Corregir **nombre, descripción, color, icono y orden**, por separado o juntos.
- **Vaciar la descripción** con nulo explícito.
- Devolver la categoría corregida en la forma del detalle (`RF-AC-003`).

### 4.2 No incluye

- **La portada.** Es un archivo, con sus endpoints (`RF-AC-006`, `RF-AC-007`); `coverImageUrl` en el cuerpo se rechaza como campo desconocido.
- **Los cursos.** Se clasifican y desclasifican desde el curso (`RF-AC-016`, `RF-AC-017`).
- **Reordenar en bloque.** Cada categoría se corrige por separado (§14.1).
- **Corregir una retirada.** Lo retirado no se corrige (`RN-AC-018`).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-001` | El nombre nuevo no lo usa otra categoría viva | `requirements/ac.md` §5.1 |
| `RN-AC-002` | El orden es un entero ≥ 0; corregirlo no reordena a las demás | `requirements/ac.md` §5.1 |
| `RN-AC-003` | Color e icono se corrigen y **no se vacían**; el color se normaliza | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Lo retirado no se corrige | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador | Sí | Cuál se corrige | Ruta. Categoría **viva** |
| `name` | No | Nombre nuevo | Hasta 150 tras recortar; único entre los vivos; **no admite nulo** |
| `description` | No | Descripción nueva | Texto; **nulo explícito la vacía**; de solo espacios queda nula |
| `color` | No | Color nuevo | Seis hexadecimales sin `#`, normalizado a mayúsculas; **no admite nulo** |
| `icon` | No | Icono nuevo | La forma del alta; **no admite nulo** |
| `displayOrder` | No | Orden nuevo | Entero ≥ 0; **no admite nulo** |

**Ausente y nulo no significan lo mismo**: ausente es «no lo toques», nulo es «vacíalo» — y solo la descripción admite vaciarse. **Al menos uno de los cinco** tiene que venir.

### 6.2 Salida

`200` con la categoría en la forma del detalle (`RF-AC-003`), con `updatedAt` avanzado **solo si algo cambió**.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `course-categories:update`; categoría viva; si viene nombre, libre entre los otros vivos.

**Postcondiciones:** la fila refleja los campos corregidos y solo esos; `audit_change_log` tiene una fila `UPDATE` con el antes y el después de cada campo que cambió — **ninguna fila si nada cambió de valor**.

## 8. Flujo principal

1. Llega la petición con uno o más campos.
2. El sistema valida la forma de lo que viene, **juntos**, y que venga al menos uno (§11).
3. El sistema resuelve la categoría **viva**, bloqueándola: si no existe o está retirada, `EX-001`.
4. Si viene el nombre, comprueba que no lo usa **otra** categoría viva (`EX-002`).
5. El sistema aplica los cambios, y si alguno cambió de valor, escribe y registra el diff en la misma transacción.
6. Devuelve `200` con el detalle.

## 9. Flujos alternativos

### FA-001 — El cuerpo trae los mismos valores que ya había

**Comportamiento:** `200` con el detalle, **sin avanzar `updatedAt` ni auditar**. Un cambio que no cambia nada no es un cambio.

### FA-002 — Solo cambia la caja o los acentos del nombre

**Comportamiento:** se admite. La unicidad excluye a la propia categoría: «trading» → «Trading» no choca consigo misma.

### FA-003 — El orden nuevo ya lo usa otra

**Comportamiento:** se admite, y las dos se desempatan por identificador (`RN-AC-002`).

## 10. Excepciones

### EX-001 — La categoría no existe o está retirada

**Respuesta del sistema:** `404` — *«No existe una categoría viva con ese identificador.»*

### EX-002 — El nombre ya lo usa otra categoría viva

**Respuesta del sistema:** `409` — *«Ya existe una categoría con ese nombre.»*

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | El nombre no admite vaciarse y cabe en 150 | El nombre de la categoría no puede quedar vacío ni superar los 150 caracteres. |
| `VAL-003` | El color no admite vaciarse y tiene la forma admitida | El color de la categoría no puede quedar vacío y debe ser seis dígitos hexadecimales sin el símbolo #. |
| `VAL-004` | El icono no admite vaciarse y tiene la forma admitida | El icono de la categoría no puede quedar vacío, solo admite minúsculas, dígitos y guion medio, debe empezar por letra y no puede exceder 50 caracteres. |
| `VAL-005` | El orden no admite vaciarse y es ≥ 0 | El orden de la categoría no puede quedar vacío y debe ser un entero mayor o igual que cero. |
| `VAL-006` | Al menos un campo corregible | Debe informar al menos uno de los campos corregibles. |
| `VAL-007` | Ningún campo desconocido — en particular `coverImageUrl`, `courses`, `status` | El cuerpo de la petición contiene campos no admitidos. |

Todas se devuelven **juntas**.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-021` | El sistema corrige nombre, descripción, color, icono y orden por separado y juntos, y devuelve el detalle con `updatedAt` avanzado |
| `CA-AC-022` | El nulo explícito **vacía** la descripción y **se rechaza** en nombre, color, icono y orden, con `400` y los errores juntos |
| `CA-AC-023` | El sistema rechaza con `400` un cuerpo vacío y uno que traiga `coverImageUrl`, `courses` o `status` |
| `CA-AC-024` | El sistema rechaza con `409` un nombre que ya usa **otra** viva, admite el de una retirada y **admite cambiar solo la caja** del propio; y con `404` una categoría retirada o inexistente |
| `CA-AC-025` | El color llega en minúsculas y **se guarda y se devuelve en mayúsculas**; y un color o un orden que ya usa otra categoría **se admiten** |
| `CA-AC-026` | Un cuerpo sin cambios de valor responde `200` sin avanzar `updatedAt` ni auditar; uno con cambios deja la fila `UPDATE` con antes y después de **solo** los campos que cambiaron |
| `CA-AC-027` | Corregir el orden de una categoría **no cambia** el de ninguna otra, y el listado (`RF-AC-002`) la enseña en su sitio nuevo |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El nombre nuevo coincide con el de una retirada | Se admite: la retirada liberó el nombre |
| `color: "1e88e5"` cuando ya está `1E88E5` | **Sin cambio**: se normaliza antes de comparar, y no se audita |
| Dos correcciones simultáneas del mismo nombre hacia el mismo valor desde dos categorías | El índice parcial las ordena: una queda y la otra recibe `409` |
| Corregir la descripción de una categoría con portada | Se admite; la portada no interviene en nada de esta operación |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Un endpoint de reordenación en bloque —«esta lista, en este orden»—? | **No hoy.** `RN-AC-002` hace del orden un número declarado precisamente para que no haya que renumerar a las demás en cada cambio; una reordenación en bloque es una operación con su propia concurrencia y su propia auditoría —¿un `UPDATE` por fila?— que nadie ha pedido. Queda anotada la condición para reabrirlo: que administración reordene lo bastante como para que corregir uno a uno estorbe |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 17-09-2026 | Redacción inicial. Hereda `RF-PM-004` —parcial, nulo explícito como orden, unicidad frente a otros, auditoría de lo que cambió— **sin inmutables y sin la regla del icono**: los cinco campos se corrigen y ninguno salvo la descripción se vacía. Reordenar es corregir el número, y no mueve a las demás. | Responsable técnico |
