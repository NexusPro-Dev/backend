# SPEC — `RF-PM-011` Retirar la reseña propia

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-011` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que el autor de una reseña **la quite sin explicárselo a nadie**, y que el sistema conserve igualmente qué decía y quién la quitó.

## 2. Contexto

**Es la primera eliminación de una entidad de negocio del sistema que no pide motivo.** El Art. V.13 lo exigía en toda baja salvo en las asociaciones, y para esta operación **se enmendó** el 14-09-2026 ([`constitution.md`](../../../constitution.md) v0.8.0): nace la tercera excepción, el **contenido propio** — lo que una persona escribió en nombre propio y retira ella misma. El motivo no se pide porque el único posible ya está en el evento: quien retira y quien escribió son la misma persona.

**Y es el primer `DELETE` del módulo.** El retiro del producto (`RF-PM-006`) es un `POST /deletion` porque su cuerpo lleva el motivo y la RFC 9110 no garantiza que el cuerpo de un `DELETE` llegue. Aquí **no hay cuerpo que proteger**, y el verbo correcto es el que dice lo que hace — como `DELETE /users/{id}/membership` hizo con una asociación.

## 3. Actores

| Actor | Papel |
|---|---|
| El **autor** de la reseña, con `products:comment` | Retira la suya |

**Nadie más, y eso incluye a la administración.** `RN-PM-027` no tiene excepción; lo que eso cuesta está escrito en [`requirements/pm.md` §5.2.7](../../../requirements/pm.md) y la salida —una moderación con su propio permiso, que retiraría **con motivo** porque quien la ejerce no es el autor— es otro requerimiento.

## 4. Alcance

### 4.1 Incluye

- Retirar **lógicamente** la reseña viva del actor, sin motivo declarado.
- Registrar la baja en la auditoría de eliminación **con la instantánea** y un motivo fijo.
- Responder `403` a quien no es el autor, aunque porte el permiso.
- Admitir el retiro **aunque el producto ya no se venda**.

### 4.2 No incluye

- **Borrar físicamente.** La fila permanece con `deleted_at`, como todo lo que este módulo retira.
- **Revivir** una reseña retirada. Quien cambie de opinión escribe otra (`RF-PM-009`, `FA-001`).
- **Retirar en bloque** —todas las de un producto, todas las de una persona—: no hay actor legítimo para eso hoy.
- **Retirar la de otro.** Ni con permiso, ni con motivo: la moderación no existe.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-027` | **Solo el autor corrige y retira su reseña — nadie más, ni administración** | `requirements/pm.md` §5.1 |
| `RN-PM-029` | **La reseña se retira sin motivo declarado, y no desaparece** | `requirements/pm.md` §5.1 |
| `RN-PM-026` | **Una reseña por persona y producto, entre las vivas** — retirada la suya, puede escribir otra | `requirements/pm.md` §5.1 |
| `RN-PM-031` | **El producto publica el promedio de sus reseñas vivas** — la retirada sale de la cuenta en el acto | `requirements/pm.md` §5.1 |
| Art. V.13 | Toda eliminación registra motivo — **con la tercera excepción, el contenido propio** | `constitution.md` |
| Art. V.7 | El actor de la baja vive en la auditoría, no en la tabla | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador del producto | Sí | De qué producto es la reseña | Va en la ruta. La reseña **debe ser de ese producto** |
| Identificador de la reseña | Sí | Cuál se retira | Va en la ruta |

**Sin cuerpo.** Un `DELETE` con cuerpo se ignora, no se rechaza: no hay nada que leer en él, y rechazarlo obligaría a documentar una validación sobre algo que no existe.

### 6.2 Salida

`204`, sin cuerpo. No hay nada que devolver: la reseña ya no está, y lo que era vive en la auditoría.

## 7. Precondiciones y postcondiciones

**Precondiciones:**

- El actor está autenticado y porta `products:comment`.
- La reseña existe, está **viva**, es **de ese producto** y **su autor es el actor**.

**Postcondiciones:**

- La fila tiene `deleted_at` puesto. **Nada más de la fila cambia**: la puntuación y el texto se quedan tal cual, porque la instantánea de la auditoría y la fila tienen que decir lo mismo.
- `audit_deletion_log` tiene una fila `LOGICAL` de `product_comments` con el actor, **la instantánea completa** y `reason = 'Retirada por su autor'`.
- La reseña **no aparece** en `RF-PM-012` ni en `RF-PM-013`, y **no cuenta** en `rating` de ninguna lectura del producto — todo en la misma transacción.
- El actor **puede escribir otra** sobre el mismo producto.

## 8. Flujo principal

1. Llega una petición con los dos identificadores.
2. El sistema toma el actor del token.
3. El sistema resuelve la reseña **viva** por su identificador **y el del producto**, bloqueándola. Si no: `EX-001`.
4. El sistema comprueba que **el autor de la fila es el actor**. Si no: `EX-002`.
5. El sistema toma **la instantánea**, antes de tocar nada.
6. El sistema marca `deleted_at`.
7. El sistema registra la baja en la auditoría de eliminación con la instantánea y el motivo fijo, en la misma transacción (Art. V.14).
8. Devuelve `204`.

**El paso 5 va antes que el 6, y no es un detalle.** Después de marcar, la instantánea diría que la reseña ya estaba retirada. Es el mismo orden que `RF-PM-006` y `RF-SP-029`.

## 9. Flujos alternativos

### FA-001 — El producto ya no se vende

**Condición:** el producto está inactivo o retirado.
**Comportamiento:** **se retira igual.** Lo escrito es del autor aunque el catálogo haya cambiado (`RN-PM-028`), y es precisamente cuando un producto se retira cuando más sentido tiene que alguien quiera quitar lo que dijo de él.

### FA-002 — El actor quiere volver a opinar después

**Comportamiento:** escribe otra con `RF-PM-009`. El índice único es parcial sobre las vivas, de modo que la retirada no estorba. Es **otra fila**: la retirada permanece, retirada, con su historia.

## 10. Excepciones

### EX-001 — La reseña no existe, ya está retirada o no es de ese producto

**Respuesta del sistema:** `404` — *«La reseña no existe.»* **El mismo cuerpo en los tres.**

**Retirar dos veces responde `404`, y ahí esta operación se aparta de `RF-PM-006`.** Aquel distingue «ya está retirado» con un `409` porque el catálogo administrativo **devuelve** los retirados y quien retira dos veces merece saber que la primera funcionó. Aquí la reseña retirada **no la devuelve nadie** —ni la lista, ni la propia—, de modo que para el sistema y para el autor «retirada» y «no existe» son indistinguibles, y un segundo `DELETE` que reciba `404` ha conseguido lo que quería. Distinguirlo le confirmaría a un tercero que existió.

### EX-002 — La reseña no es del actor

**Condición:** existe, está viva, y su autor **no es** quien llama.
**Respuesta del sistema:** `403` — *«Solo el autor puede retirar su reseña.»* Se registra en la **auditoría de seguridad** con severidad alta.

!!! danger "Un `403` a un administrador es lo correcto, y es lo que la prueba tiene que afirmar"

    Es la mitad más incómoda de `RN-PM-027`: la persona con más permisos del sistema recibe `403` sobre una reseña que considera inapropiada, y **no hay otra ruta**. Se decidió así ([`requirements/pm.md` §5.2.7](../../../requirements/pm.md)) y la prueba lo afirma con un superadministrador, para que el día que se decida construir la moderación se construya **como otra operación con motivo** y no aflojando esta comparación.

### EX-003 — Sin permiso

**Respuesta del sistema:** `403`. Sin `products:comment` la ruta no se alcanza.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |

**No hay `VAL` de motivo**, y es toda la diferencia con `RF-PM-006`.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-193` | El autor retira su reseña con `204` **sin enviar motivo ni cuerpo**, y la fila queda con `deleted_at` y el resto intacto |
| `CA-PM-194` | El sistema registra en `audit_deletion_log` una fila `LOGICAL` con el actor, **la instantánea completa** de la reseña y `reason` igual a `Retirada por su autor` |
| `CA-PM-195` | El sistema responde `403` a otro cliente **con `products:comment`** sobre una reseña ajena, y la reseña sigue viva |
| `CA-PM-196` | El sistema responde `403` a un **superadministrador** sobre una reseña ajena: no existe moderación |
| `CA-PM-197` | El sistema responde `404` a una reseña inexistente, a una **ya retirada** —también a su autor— y a una que no es del producto de la ruta, con el mismo cuerpo |
| `CA-PM-198` | La reseña retirada **no aparece** en la lista pública ni en la propia, y **sale de `rating`** del producto en las cuatro lecturas: `count` baja y `average` se recalcula sin ella — **nulo** si era la única |
| `CA-PM-199` | Retirada la suya, el autor **escribe otra** sobre el mismo producto con `201`, y es otra fila |
| `CA-PM-200` | El autor retira su reseña sobre un producto **inactivo** y sobre uno **retirado** |
| `CA-PM-201` | Un `DELETE` **con cuerpo** responde igual que sin él: el cuerpo se ignora |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Dos `DELETE` simultáneos del mismo autor | La fila se bloquea en el paso 3; el segundo la encuentra retirada y responde `404`. Una sola fila de auditoría |
| El autor retira y **en la misma pantalla** escribe otra | Dos operaciones, dos filas, dos registros de auditoría. La nueva tiene `count` en cuenta y la vieja no |
| El producto se retira **después** | La reseña retirada no cambia: ya estaba fuera de todo |
| Alguien intenta retirar con el `POST /deletion` del producto | No existe esa ruta para reseñas: `404` de ruta, no de negocio. Queda anotado para que nadie la añada «por simetría» |
| El texto de la instantánea tiene mil caracteres | Cabe: `snapshot` es `jsonb` sin cota |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Por qué el motivo fijo se escribe en `reason` en lugar de dejar la columna nula? | **Porque `ck_deletion_reason` exige contenido en toda baja `LOGICAL`, y no se relaja.** El `CHECK` no puede saber si quien ejecuta es el autor, de modo que relajarlo abriría la puerta a toda baja lógica sin motivo; y un cuarto `deletion_type` diría «cómo» cuando lo que cambia es «por qué se exime». El Art. V.13 enmendado admite suplir el motivo con un valor fijo en el contenido propio, y eso es lo que se hace ([`architecture.md` §6.6.3](../../../architecture.md)) |
| 2 | ¿Qué texto exacto lleva el motivo fijo? | **`Retirada por su autor`**, y se declara aquí porque el Art. V.13 exige que la excepción esté en la especificación. Es una constante del servicio, no configuración: cambiarla es una enmienda de esta spec |
| 3 | ¿Se emite evento de seguridad al retirar? | **No.** Una reseña no concede privilegios, igual que el producto (`RF-PM-006` §14). El `403` de `EX-002` sí se registra, como toda denegación |
| 4 | ¿Debería el autor poder retirar **todas** las suyas de una vez, por ejemplo al darse de baja? | **Hoy no.** No existe la baja voluntaria de cuenta; el día que exista decidirá qué pasa con lo escrito, y ese requerimiento citará esta regla. Añadirlo aquí sería adelantar una decisión de privacidad sin dueño |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 14-09-2026 | Redacción inicial. **Es la primera baja de una entidad de negocio sin motivo declarado**, amparada en la tercera excepción del Art. V.13 —el contenido propio, `constitution.md` v0.8.0—, y **el primer `DELETE` del módulo**, por lo mismo que el retiro del producto es un `POST`: el verbo lo decide si hay cuerpo que proteger. El motivo se suple con **`Retirada por su autor`** y `ck_deletion_reason` no se toca. **Retirar dos veces responde `404` y no `409`**, al revés que en `RF-PM-006`: la reseña retirada no la devuelve nadie, de modo que «retirada» y «no existe» son lo mismo para el autor, y distinguirlo se lo confirmaría a un tercero. **El `403` al superadministrador es lo correcto y se prueba con él**, para que la moderación —si llega— se construya como otra operación con motivo y no aflojando esta comparación. | Responsable técnico |
