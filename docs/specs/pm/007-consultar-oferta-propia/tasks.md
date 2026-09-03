# TASKS — `RF-PM-007` Consultar la oferta disponible para uno mismo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-007` |
| Enmendadas | 02-09-2026 — `pm.md` §5.2.1 declara que la oferta pasará a coincidir por **origen**; el código de esta consulta **sigue sin reescribirse** (`T-20`) |
| Plan | [`plan.md`](plan.md), aprobado el 26-08-2026 |
| Estado | **En curso** — `T-01` a `T-19` `Hecha`; `T-20` (coincidencia por origen) queda `Pendiente` |
| Autor | Responsable técnico |
| Aprobadas por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **En `SP`**: `CurrentMembershipLookup` con su adaptador, en `modules/system/users/application`. Devuelve la membresía **vigente** ya evaluada, o vacío | `RF-PM-001 · T-07` | Integración: una membresía con fecha de fin **pasada** devuelve vacío, y una con fecha **igual al instante consultado** también — ese borde ya está fijado en `SP` y aquí se hereda, no se reimplementa | **Hecha** |
| `T-02` | `application/OfferResponse`: `currentMembership` más **dos colecciones envueltas** | — | La respuesta tiene `upgrades.content` y `services.content`, **no arreglos en la raíz** (`CA-PM-091`) | **Hecha** |
| `T-03` | `ProductQueryRepository.findOffer(Integer nivel)`: una sentencia, solo activos, upgrades con `level` **estrictamente menor** que el del actor | `RF-PM-002 · T-05` | Integración: **una** sentencia. La comparación es la mitad del requerimiento y se prueba en `T-06` | **Hecha** |
| `T-04` | Orden: upgrades por nivel destino, bots por fecha de alta | `T-03` | `CA-PM-078`, `CA-PM-079` | **Hecha** |
| `T-05` | `domain/service/GetOwnOfferService`: el actor sale del token, nunca de un parámetro | `T-01`, `T-03` | Enviar `userId` no cambia la respuesta (`CA-PM-066`) | **Hecha** |
| `T-06` | **Prueba de los tres casos de nivel**: destino inferior, igual y superior al del actor | `T-05` | Es la que detecta la comparación escrita al revés, que **pasaría todas las pruebas de camino feliz** ofreciendo exactamente lo contrario | **Hecha** |
| `T-07` | `interfaces`: `GET /api/v1/products/available`. **Sin permiso hasta el 02-09-2026**; desde entonces, `@PreAuthorize("hasAuthority('products:sale')")` (`T-16`) | `T-05` | Responde a un actor autenticado que porta `products:sale` (`CA-PM-065`) | **Hecha** |
| `T-08` | Prueba de que la ruta literal **no se confunde** con `/products/{id}` | `T-07` | `available` responde `200` y no `400` por identificador inválido. Spring resuelve antes el segmento literal, y esta prueba es lo que impide que un renombrado lo rompa en silencio | **Hecha** |
| `T-09` | Pruebas de API del resto de criterios de `spec.md` §12 | `T-07` | Cubre `CA-PM-058` a `CA-PM-067`, `CA-PM-078`, `CA-PM-079`, `CA-PM-088` a `CA-PM-091` | **Hecha** |
| `T-10` | Prueba de quien **no tiene membresía** y de quien la tiene **vencida** | `T-09` | Cero upgrades y todos los bots, en los dos casos (`FA-001`, `FA-003`) | **Hecha** |
| `T-11` | Prueba de quien está **en la cima**: lista de upgrades vacía, sin error | `T-09` | No es un mensaje especial: es una lista vacía | **Hecha** |
| `T-12` | Documentación OpenAPI del endpoint, **declarando que no admite parámetros** | `T-09` | El contrato no lista ninguno | **Hecha** |
| `T-13` | Actualizar la matriz de trazabilidad | `T-09` | La fila refleja el estado | **Hecha** |
| `T-14` | La **vigencia** viaja en cada producto ofrecido | `T-02`, `T-03` | Vacía en los que no caducan (`CA-PM-095`) | **Hecha** |
| `T-20` | **Reescribir `T-03`/`T-06` a coincidencia por origen**: `findOffer` deja de comparar `level` y pasa a filtrar por `source_membership_id = :membresia`, como `pm.md` §5.2.1 declara decidido desde el 02-09-2026 | `RF-PM-001` (el alta que declara el origen, ya construida) | `CA-PM-106` a `CA-PM-108`, ya escritos en `spec.md` §12 y sin prueba todavía | **Pendiente** |

**Este 03-09-2026 se descubrió que `T-20` nunca se ejecutó, al fusionar la rama que trae `RF-PM-007`.** `8d2bb3e` (02-09-2026) amplió `ProductRow` con las columnas de origen para `RF-PM-002` y `RF-PM-003`, y su prosa de `pm.md` §5.2.1 da por hecho que `RF-PM-007` recibió el mismo tratamiento — pero `RF-PM-007` **no existía todavía en esa rama**: llegó después, desde `develop`, con `findOffer` sin tocar. El resultado es un requerimiento que la documentación del módulo describe como coincidencia por origen y cuyo código **sigue comparando niveles**, verificado y probado así. Se declara aquí en lugar de forzar `T-03`/`T-06` a `Hecha` con una descripción que el código no cumple: una tarea `Hecha` que describe una versión que no existe es peor que una `Pendiente` visible.

### 1.1 `products:sale` — 02-09-2026

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-15` | `V48__seed_products_sale_permission.sql`: sembrar `products:sale` y asociarlo a `SUPERADMIN` y `ADMIN` **en la misma migración**, sin tocar `CLIENTE` | — | El catálogo de permisos cuenta veintinueve, y `ADMIN` lo tiene | **Hecha** |
| `T-16` | `ProductController`: `@PreAuthorize("hasAuthority('products:sale')")` sobre `GET /available` | `T-15` | Un actor sin el permiso recibe `403` | **Hecha** |
| `T-17` | `EndpointPermissionsIT`: **retirar** `GET /api/v1/products/available` de `SIN_PERMISO_A_PROPOSITO` | `T-16` | La ruta ya no figura en la lista blanca, y `declaraPermiso` la reconoce | **Hecha** |
| `T-18` | `ProductOfferIT`: el actor de las pruebas existentes gana `products:sale`, y nace la prueba del `403` sin él | `T-16` | `CA-PM-065` (revisado) y `CA-PM-101` | **Hecha** |
| `T-19` | OpenAPI: el endpoint declara el permiso que exige | `T-16` | El contrato publicado lo dice | **Hecha** |

## 2. Orden de ejecución

`T-01` primero: es la tercera y última lectura de D-25, y la única que este requerimiento estrena. `T-06` dejó de ser la prueba que decide si el requerimiento hace lo contrario de lo que dice: **desde el 02-09-2026 aquí no se comparan niveles**, y esa comparación se hace una vez al registrar el producto (`RN-PM-017`).

**`T-15` va antes que `T-16`, y no al revés.** Sembrar el permiso sin exigirlo todavía no rompe nada; exigirlo sin haberlo sembrado deja a todo el mundo —incluido `SUPERADMIN`— fuera de una ruta que hasta ayer era pública. `V48` sigue la misma forma que `V40`: los identificadores se enumeran por código y no por `SELECT` sin filtro, para no asociar de paso ningún otro permiso que otra migración hubiera sembrado y que alguien hubiera decidido no conceder.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-058` | `T-03` |
| `CA-PM-059` a `CA-PM-061` | `T-03`, `T-06` |
| `CA-PM-062` | `T-11` |
| `CA-PM-063` | `T-01`, `T-10` |
| `CA-PM-064` | `T-02`, `T-05` |
| `CA-PM-065` | `T-07`, `T-15`, `T-16`, `T-18` |
| `CA-PM-066` | `T-05` |
| `CA-PM-067` | `T-09` |
| `CA-PM-078`, `CA-PM-079` | `T-04` |
| `CA-PM-088` | `T-10` |
| `CA-PM-089` | `T-06` |
| `CA-PM-090` | `T-09` |
| `CA-PM-091` | `T-02` |
| `CA-PM-101` | `T-16`, `T-18` |
| `CA-PM-106` a `CA-PM-108` | `T-20`, sin cubrir |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | `T-01` escribe en paquetes de `SP`, sobre `user_memberships`. Una regresión ahí alcanza a `RF-SP-032` y `RF-SP-033` | 26-08-2026 | Responsable técnico | **Cerrado el 01-09-2026.** El adaptador NO consulta `user_memberships`: reutiliza `UserRepository.findMembership` y decide con `UserMembership.isCurrentAt`, de modo que no hay una segunda lectura que pueda divergir. La suite de `SP` sigue en verde sin un solo cambio |
| 2 | `T-06` y `T-10` necesitan una cadena de al menos **cuatro** niveles y personas en varios de ellos: la preparación de datos es la mitad del trabajo de estas pruebas | 26-08-2026 | Responsable técnico | **Cerrado el 01-09-2026.** `ProductOfferIT` siembra `ORO(1) > PLATINO(2) > VIP(3) > FREE(4)` y cinco personas: una por peldaño, una sin membresía y una con la suya vencida |
| 3 | `T-20` necesita upgrades declarados desde **varios orígenes** hacia el mismo destino, que `ProductOfferIT` no siembra todavía | 03-09-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

El requerimiento no está terminado hasta cumplir **todas** las condiciones de la constitución §16:

- [ ] Todas las tareas en estado `Hecha`. `T-01` a `T-19`, sí; `T-20` (coincidencia por origen), pendiente.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde. `CA-PM-106` a `CA-PM-108` no tienen prueba: el código que verificarían no existe.
- [x] `mvn verify` en verde en local.
- [x] Toda escritura emite su evento de auditoría, en la transacción que corresponde. **No aplica: este requerimiento no escribe nada** (`plan.md` §7). `V48` tampoco: sembrar un permiso no tiene línea de tiempo que reconstruir, igual que `V40`.
- [x] Los endpoints nuevos declaran su permiso. **Dejó de declarar que no lleva ninguno**: desde `T-16` exige `products:sale`, y `T-17` retiró la entrada de `SIN_PERMISO_A_PROPOSITO` en `EndpointPermissionsIT` — la excepción que ya no aplicaba se borró, no se dejó como fósil.
- [x] El contrato OpenAPI coincide con el comportamiento real.
- [x] Documentación afectada actualizada en el mismo Pull Request.
- [x] Matriz de trazabilidad actualizada.
- [x] Pull Request aprobado e integrado (PR #56, 03-09-2026, y las correcciones de origen/destino de `RF-PM-002` en su propio pase).
