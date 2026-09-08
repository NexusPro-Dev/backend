# TASKS — `RF-PM-008` Consultar un hotlink

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-008` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 07-09-2026 |
| Estado | **En curso** — `T-01` a `T-17` **Hecha**, `T-18` retirada. Faltan `T-19` y `T-20`, que nacen el 08-09-2026 al reescribirse `RN-PM-024`: los dos importes se publican |
| Issue | Pendiente de crear |
| Rama | `feature/hotlink-publico` |
| Autor | Responsable técnico |
| Enmendadas | 07-09-2026 — `T-05b` por el **color de la membresía**; 08-09-2026 — `T-17` y `T-18` por el **precio a mostrar** (`RN-PM-024`), y `T-13` y `plan.md` §9 por el **número real de consultas**: son cuatro y no tres, y una sola cuando el vendedor no procede |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **En `SP`**: `PublicSellerLookup` con su adaptador. Devuelve `Optional` con **nombre y apellido**, y **vacío si la persona no porta un rol de tipo `VENDEDOR`** | — | Integración: un vendedor devuelve el nombre; un cliente, un administrador y un usuario inexistente devuelven **vacío** — los tres iguales. **La regla vive aquí y no en `PM`** | **Hecha el 07-09-2026** |
| `T-02` | **En `SP`**: `ExchangeRateLookup` con su adaptador. Devuelve la tasa **vigente hoy** entre dos monedas, ya elegida | `RF-SP-047 · T-01` | Integración: devuelve la que rige y **vacío** cuando ninguna cubre el día. Que sea una sola lo garantiza `RN-SP-032` | **Hecha el 07-09-2026** |
| `T-03` | Regla de **ArchUnit**: la ruta pública no puede importar repositorios ni entidades de `SP` | `T-01`, `T-02` | La regla ya existe para `modules/products`; se comprueba que alcanza al paquete nuevo | **Hecha el 07-09-2026** |
| `T-04` | `ProductQueryRepository.findPublishedByCode(String)`: por código, **sin distinguir mayúsculas**, exigiendo `ACTIVO`, no retirado y **alcance `HOTLINKS`** | `RF-PM-002 · T-05` | Un producto de alcance `TIENDA` **no se encuentra** (`CA-PM-131`), ni uno inactivo ni uno retirado (`CA-PM-132`) | **Hecha el 07-09-2026** |
| `T-05` | `application/HotlinkResponse` con `SellerRef` y `ExchangeRef`. **`rate` como cadena**, `amount` como número redondeado a la moneda de destino | `T-04` | `CA-PM-128`: los ocho decimales llegan intactos en la respuesta | **Hecha el 07-09-2026** |
| `T-05b` | La **membresía destino recortada** en `HotlinkResponse`: `code`, `name` y `color`, **presente y nula** en los bots. Sale del `LEFT JOIN` que la consulta ya hace, sin llamada extra | `T-04` | `CA-PM-138` a `CA-PM-140`. La prueba comprueba además que **no** viajan `id` ni `level` | **Hecha el 07-09-2026** |
| `T-06` | `domain/service/GetHotlinkService`: vendedor, producto, tasa — **y un solo punto de salida para el `404`** | `T-01`, `T-02`, `T-04`, `T-05` | Los seis casos que no proceden lanzan **la misma** excepción, con el mismo código y el mismo mensaje | **Hecha el 07-09-2026** |
| `T-07` | La conversión: se calcula solo si hay tasa **y** la moneda del producto no es la de casa | `T-06` | `CA-PM-129` y `CA-PM-130`: en los dos casos `exchange` llega **presente y nulo**, y el producto se devuelve igual | **Hecha el 07-09-2026** |
| `T-08` | `interfaces/HotlinkController`: `GET /api/v1/hotlinks/{username}/{code}`, **sin `@PreAuthorize`** | `T-06` | Responde sin token (`CA-PM-127`) y **lo mismo con uno** (`CA-PM-136`) | **Hecha el 07-09-2026** |
| `T-09` | La ruta en `RUTAS_PUBLICAS` de `SecurityConfig`, **con su motivo escrito al lado**, y en la lista de rutas sin permiso a propósito de `EndpointPermissionsIT` | `T-08` | Sin lo primero responde `401`; sin lo segundo, `EndpointPermissionsIT` falla — que es lo que impide que una ruta pública se cuele por descuido | **Hecha el 07-09-2026** |
| `T-10` | La política de **límite de tasa por origen** para esta ruta | `T-09` | `CA-PM-137`: el exceso desde un origen recibe `429` | **Hecha el 08-09-2026** |
| `T-11` | **LA PRUEBA DEL ORÁCULO**: los **seis** casos que no proceden, comparando **el cuerpo entero** de las respuestas entre sí | `T-08` | `CA-PM-134`. **Es la prueba que define el requerimiento**: sin ella, el día que alguien mejore un mensaje para depurar nadie se enteraría de que el endpoint pasó a decir quién existe | **Hecha el 07-09-2026** |
| `T-12` | Prueba de que la respuesta **no lleva** correo, identificador, estado ni roles del vendedor | `T-08` | `CA-PM-135`, comprobando el cuerpo completo y no solo los campos esperados | **Hecha el 07-09-2026** |
| `T-13` | Prueba de **número de consultas** | `T-06` | **TRES** —vendedor, producto y moneda de casa—, **cuatro** cuando el producto está en otra moneda y hay tasa que aplicar, y **una sola** cuando el vendedor no procede. El plan dijo «tres y dos», luego «cuatro y una», y la prueba dejó el número en su sitio: es para lo que existe. La conversión no se pide si no hay a quién enseñársela | **Hecha el 08-09-2026** |
| `T-14` | Pruebas de API de los once criterios de `spec.md` §12 | `T-08` | La suite cubre `CA-PM-127` a `CA-PM-137` | **Hecha el 07-09-2026** |
| `T-15` | Documentación OpenAPI. **La prosa dice que es público, que la conversión es informativa y que `rate` es una cadena** | `T-14` | El contrato declara el `200` y el `404`, y **ningún** esquema de seguridad para esta ruta | **Hecha el 07-09-2026** |
| `T-16` | Actualizar la matriz de `docs/requirements.md` | `T-14` | La fila de `RF-PM-008` refleja el estado | **Hecha el 08-09-2026** |
| `T-17` | **El precio a mostrar**: `findPublishedByCode` selecciona `COALESCE(p.public_price, p.price) AS price` y **no selecciona el otro**; la conversión de `T-07` se calcula sobre **ese** importe | `RF-PM-001 · T-33`, `T-04` | `CA-PM-161` y `CA-PM-162`: con precio público se publica y se convierte ese; el importe convertido **dividido por la tasa** devuelve el publicado | **Hecha el 08-09-2026** |
| ~~`T-18`~~ | ~~Prueba de que el precio del sistema **no aparece en el cuerpo**~~ — **retirada el 08-09-2026** con `CA-PM-163`: aparece a propósito | `T-17` | La prueba **se invierte** en `T-20` en vez de borrarse | **Retirada el 08-09-2026** |
| `T-19` | **Los dos importes en la respuesta**: `findPublishedByCode` selecciona `p.price` y `p.public_price` por separado —sin `COALESCE`— y `ProductRef` gana `publicPrice`. La conversión se sigue calculando sobre **el que se muestra** | `T-17` | `CA-PM-161` y `CA-PM-162` reescritos: los dos importes llegan, y el convertido dividido por la tasa devuelve el mostrado | **Hecha el 08-09-2026** |
| `T-20` | **La prueba de la fuga aceptada**: los dos importes viajan **sin token** y la diferencia entre ellos es visible. Es `T-18` del revés | `T-19` | `CA-PM-169`. Se escribe **afirmando** lo que se publica, para que el día que alguien decida volver a ocultarlo la prueba falle y obligue a decidirlo | **Hecha el 08-09-2026** |

## 2. Orden de ejecución

**`T-01` y `T-02` van primero y son las de más riesgo**: escriben código **en paquetes de `SP`**, un módulo con su suite en verde, y llevan dentro una regla de negocio —quién es publicable, cuál es la tasa vigente— que si se implementa en `PM` no falla, **miente**.

`T-04` y `T-05` son independientes. `T-06` las junta.

**`T-11` no es opcional y no se deja para el final por comodidad**: es la que define el requerimiento. Conviene escribirla justo después de `T-08`, cuando todavía se recuerda por qué los seis mensajes son el mismo.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-127` | `T-06`, `T-08` |
| `CA-PM-128` | `T-02`, `T-05` |
| `CA-PM-129`, `CA-PM-130` | `T-07` |
| `CA-PM-131`, `CA-PM-132` | `T-04` |
| `CA-PM-133` | `T-01` |
| `CA-PM-134` | `T-11` |
| `CA-PM-135` | `T-12` |
| `CA-PM-136` | `T-08` |
| `CA-PM-137` | `T-10` |
| `CA-PM-138` a `CA-PM-140` | `T-05b` |
| `CA-PM-161`, `CA-PM-162` | `T-17`, `T-19` |
| ~~`CA-PM-163`~~ | ~~`T-18`~~ — retirados el 08-09-2026 |
| `CA-PM-169` | `T-20` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | ~~**`RF-SP-047` no está construido.** La conversión sale de `exchange_rates`, cuya tripleta está escrita y cuyo código no~~ | 07-09-2026 | Responsable del proyecto | **Cerrado el 08-09-2026** — el alta de tasas existe, y la conversión sale de una tabla real |
| 2 | `T-01` y `T-02` escriben en paquetes de `SP`. Cualquier regresión allí es responsabilidad de este requerimiento | 07-09-2026 | Responsable técnico | **Cerrado el 08-09-2026** — la suite de `SP` sigue entera, y `CurrencyView` ganó `name` sin romper a ninguno de sus cuatro consumidores |
| 3 | **`products:hotlink` se queda sin endpoint que lo exija**, y qué gobierna entonces **no está decidido** (`spec.md` §14, resolución 2). No bloquea construir esto | 07-09-2026 | Responsable del proyecto | **Abierto** |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local, **incluida la suite de `SP` sin cambios**.
- [ ] La ruta está declarada pública **en los dos sitios**: `SecurityConfig` y `EndpointPermissionsIT`.
- [ ] El contrato OpenAPI coincide con el comportamiento real, **prosa incluida**.
- [ ] Documentación afectada actualizada en el mismo Pull Request.
- [ ] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
