# SPEC — `RF-PM-029` Quitar la portada de un paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-029` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que un paquete **vuelva a pintarse con el icono de promoción y el color por omisión** que el frontend le pone, sin dejar la imagen huérfana en la base ni una dirección que siga sirviéndola.

## 2. Contexto

**Es `RF-PM-015` sin la regla.** Aquella es la única de las tres operaciones de la portada del producto que tiene algo que rechazar: un upgrade sin icono no puede quedarse sin portada (`RN-PM-034`). **El paquete no declara icono ni color** (`RN-PM-045`, [`requirements/pm.md` §5.2.12](../../../requirements/pm.md)): sin portada, el frontend le pone **su icono de promoción y el color por omisión del sistema**, los mismos para todos, de modo que **nunca se queda sin nada que pintar** y esta operación **nunca dice que no**. Es la mitad del bot de `RN-PM-034` —«se le quita siempre»— para toda la entidad.

**Todo lo demás es `RF-PM-015` tal cual**: un `DELETE` que devuelve `200` con el paquete y no `204`, porque no se retira una entidad sino que se vacía un campo; sin portada responde igual y no escribe nada; y la imagen quitada **se borra**.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador, con `packages:update` | Quita la portada de un paquete |

El mismo permiso y por lo mismo que en `RF-PM-028`: la portada es el valor de un campo del paquete.

## 4. Alcance

### 4.1 Incluye

- Vaciar `product_packages.cover_image_id` y **borrar** la fila de la imagen, en la misma transacción.
- Registrar el cambio en la auditoría de cambios de `product_packages`.
- **No escribir nada** cuando el paquete no tiene portada: responder igual.
- Responder con el paquete, con `coverImageUrl` nulo.

### 4.2 No incluye

- **Rechazar.** No hay ninguna condición bajo la que un paquete no pueda quedarse sin portada.
- **Motivo.** Se vacía un campo, no se retira una entidad.
- **Conservar la imagen** para volver a ponerla. Quien quiera la misma foto la sube otra vez.
- **Un icono o un color de repuesto.** No existen en el paquete: los pone el frontend.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-045` | **El paquete lleva portada, y sin ella se pinta con lo que el frontend pone por omisión** — quitarla nunca se rechaza | `requirements/pm.md` §5.1 |
| `RN-PM-033` | **La portada es un archivo** — la quitada **se borra**, por extensión | `requirements/pm.md` §5.1 |
| `RN-PM-041` | El paquete hereda la forma del producto — un retirado no se corrige | `requirements/pm.md` §5.1 |
| Art. V.7 | Quién quitó la portada vive en la auditoría | `constitution.md` |
| Art. V.14 | La auditoría se escribe en la misma transacción | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador del paquete | Sí | A qué paquete se le quita la portada | Va en la ruta |

**Sin cuerpo.** Un `DELETE` con cuerpo se ignora, como en `RF-PM-015` y `RF-PM-025`.

### 6.2 Salida

`200` con el paquete (`PackageDetailResponse`), con `coverImageUrl` **presente y nulo**.

## 7. Precondiciones y postcondiciones

**Precondiciones:**

- El actor está autenticado y porta `packages:update`.
- El paquete existe y está **vivo**.

**Postcondiciones:**

- `product_packages.cover_image_id` es nulo y `updated_at` avanza — **solo si había portada**.
- La fila de `product_images` **ya no existe**: su dirección responde `404` en `RF-PM-016`.
- `audit_change_log` tiene una fila `UPDATE` de `product_packages` con `cover_image_id` —antes el identificador, después vacío— **solo si había portada**.
- Las cuatro lecturas del paquete devuelven `coverImageUrl` nulo.

## 8. Flujo principal

1. Llega una petición con el identificador del paquete.
2. El sistema resuelve el paquete **vivo** por su identificador, bloqueándolo. Si no: `EX-001`.
3. Si el paquete **no tiene portada**: devuelve `200` con el paquete, **sin escribir nada** (`FA-001`).
4. El sistema se queda con el identificador de la imagen y deja de señalarla.
5. El sistema **borra** la fila de la imagen.
6. El sistema registra el cambio en la auditoría, en la misma transacción.
7. Devuelve `200` con el paquete.

**No hay paso de regla entre el 3 y el 4**, y esa ausencia es el requerimiento: en `RF-PM-015` ahí va `RN-PM-034`.

## 9. Flujos alternativos

### FA-001 — El paquete no tiene portada

**Comportamiento:** `200` con el paquete tal cual, **sin fila de auditoría ni cambio de `updated_at`**. «Quítala» sobre un paquete sin portada ya ha conseguido lo que quería (`RF-PM-015` `FA-001`).

### FA-002 — Un paquete activo y ofrecible

**Comportamiento:** **se quita igual, y sigue ofreciéndose.** La portada no condiciona ni la activación ni la ofrecibilidad (`RN-PM-040`, `RN-PM-039`): la oferta y el hotlink lo devuelven con `coverImageUrl` nulo, y el frontend lo pinta con el icono de promoción.

### FA-003 — Un paquete inactivo o retirado

**Comportamiento:** inactivo, **se quita igual**. Retirado, `EX-001`: un retirado no se corrige, y su portada se queda (`RF-PM-028` §13).

## 10. Excepciones

### EX-001 — Paquete inexistente o retirado

**Respuesta del sistema:** `404` — *«No existe un paquete vivo con ese identificador.»* El mismo cuerpo que `RF-PM-020` y `RF-PM-028`.

### EX-002 — Sin permiso

**Respuesta del sistema:** `403`.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |

**Y ninguna más.** `RF-PM-015` tiene `VAL-002` —el upgrade sin icono—; aquí no hay estado del paquete que no admita la operación.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-362` | El sistema quita la portada de un paquete con `200`, `coverImageUrl` nulo y presente, `cover_image_id` nulo, y la fila de `product_images` **ya no existe**: su dirección responde `404`. **Nunca responde `400`**: no hay estado del paquete que lo rechace |
| `CA-PM-363` | El sistema registra en `audit_change_log` un `UPDATE` de `product_packages` con `cover_image_id` —antes el identificador, después vacío— y sin ningún otro campo |
| `CA-PM-364` | Sobre un paquete **sin portada** responde `200` con el paquete **sin escribir nada**: `updated_at` no avanza y `audit_change_log` no crece |
| `CA-PM-365` | Quitar la portada de un paquete **activo y ofrecible** lo deja **activo y ofrecible**: la oferta y el hotlink lo siguen devolviendo, con `coverImageUrl` nulo |
| `CA-PM-366` | El sistema responde `404` sobre un paquete inexistente y sobre uno retirado; quita la portada de uno **inactivo**; y sin `packages:update` responde `403` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Quitar la portada y subir otra de inmediato | Dos operaciones, ninguna con condición. Entre las dos, el paquete se pinta con el icono de promoción |
| Dos `DELETE` simultáneos | El bloqueo los serializa; el segundo no encuentra portada y responde `200` sin escribir. Una fila de auditoría |
| Quitar la portada del paquete y la de uno de sus productos | Dos entidades, dos columnas, dos imágenes; cada operación borra la suya y no toca la otra |
| Retirar el paquete con portada | La portada **se queda**, y ya no se puede quitar: un retirado no se corrige (`FA-003`). Es lo mismo que el producto decide en `RF-PM-015` §14.2 |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Por qué no se rechaza nunca, si al producto sí? | **Porque el producto puede quedarse sin nada que pintar y el paquete no.** El upgrade declara icono y puede no tenerlo; el paquete no declara ninguno, y lo que no se declara no puede faltar. Decidido por el responsable del proyecto ([`requirements/pm.md` §5.2.12](../../../requirements/pm.md)) |
| 2 | ¿`200` con el paquete o `204`? | **`200` con el paquete**, por lo mismo que `RF-PM-015` §14.1 y que `RF-PM-025`: se vacía un campo de una entidad que sigue, y el cliente repinta con lo que vuelve |
| 3 | ¿Debería quitarse la portada automáticamente al retirar el paquete? | **No**, como en el producto: el detalle de un retirado la sigue enseñando, y borrarla sería perder la única copia sin que nadie lo pidiera |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 16-09-2026 | Redacción inicial. **Es `RF-PM-015` sin la regla**: el paquete no declara icono ni color (`RN-PM-045`), sin portada el frontend le pone los suyos por omisión, y por eso quitarla **nunca se rechaza** — la ausencia del paso de regla entre «¿hay portada?» y «suéltala» es el requerimiento. `DELETE` que responde `200` con el paquete; sin portada responde igual sin escribir; la imagen se borra. Una sola validación, la del identificador. Cinco criterios, `CA-PM-362` a `CA-PM-366`. | Responsable técnico |
