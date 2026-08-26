# PLAN — `RF-PM-003` Consultar el detalle de un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-003` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| `spec.md` aprobada el | 26-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |

---

## 1. Enfoque

Una lectura por identificador que devuelve el producto con su destino y su moneda **resueltos**, y —cuando está retirado— **el motivo del retiro**. Todo lo demás es rutina; el motivo no lo es, y es lo que este plan tiene que resolver.

## 2. Cambios de esquema

**Ninguno.** La clave primaria basta.

## 3. El motivo del retiro: de dónde sale

`spec.md` §14 resolución 1 decidió que el detalle lo devuelve. Pero el motivo **no está en `products`**: `requirements/pm.md` §10.1 mantiene la tabla sin columna de motivo, porque el Art. V.13 lo manda al **registro de eliminación** junto con la instantánea de lo retirado.

Ese registro es `audit_deletion_log`, y de ahí sale el problema: leerlo desde `PM` con un `JOIN` sería entrar en un almacén que este módulo no gobierna.

**Salida elegida: `shared/audit` publica una lectura estrecha.** Un puerto que responde *«el motivo registrado al eliminar esta entidad»*, dado el módulo, la entidad y su identificador.

**Por qué ahí y no en `SP`.** La auditoría es **infraestructura compartida**, no una funcionalidad de `SP`: cada módulo **escribe** en ella a través de `shared/audit` —`PM` lo hará en `RF-PM-001` y `RF-PM-006`—, y lo que `SP` posee es **consultarla como producto** (`RF-SP-011` a `RF-SP-014`), que es otra cosa. Leer el motivo de la eliminación de **una entidad propia** es simétrico de escribirlo, y por eso vive junto al escritor y no detrás de un puerto de `SP`.

**Qué NO abre esta lectura**, y conviene que esté escrito:

- **Solo el motivo, y solo de una entidad concreta.** No devuelve el actor, ni la instantánea, ni permite recorrer el registro. Quien quiera eso usa `RF-SP-012`, que exige `audit:read-deletions`.
- **No sustituye a la consulta de auditoría** ni la duplica: aquí no hay filtros, ni paginación, ni rango de fechas.
- **Cada módulo solo alcanza lo suyo.** El puerto recibe el módulo y la entidad, y `PM` pide `PM`/`products`. Que no pueda pedir lo ajeno se ancla con la regla de ArchUnit de D-25 y con una prueba.

Si esta salida no se aprueba, la alternativa es **añadir `deletion_reason` a `products`**, que duplica un dato que ya está en el registro de eliminación y crea dos verdades que pueden divergir. Se descarta por eso, no por coste.

## 4. Componentes afectados

| Capa | Componente | Responsabilidad |
|---|---|---|
| `shared/audit` | `DeletionReasonReader` + adaptador | La lectura estrecha de §3 |
| `application` | `ProductDetailResponse` | Producto, destino resuelto, moneda y marca de retiro con motivo |
| `domain/repository` | `ProductQueryRepository.findDetail(UUID)` | Una sentencia con dos uniones externas |
| `domain/service` | `GetProductService` | `@Transactional(readOnly = true)` |
| `interfaces` | `ProductController` | `GET /api/v1/products/{id}` |

## 5. Contrato de API

`GET /api/v1/products/{id}` → `200` con el detalle; `404` si no existe; `400` si el identificador no es canónico.

- **El destino y la moneda llegan resueltos** con `LEFT JOIN`, en la misma sentencia. Mismo criterio y misma justificación que `RF-PM-002` §8.
- **`targetMembership` viaja como `null` presente** en los servicios, no ausente.
- **El precio va como número, con los decimales de su moneda** y no con la escala de la columna (`CA-PM-082`): `49.99`, no `49.9900`. La escala se aplica al serializar, leyendo `decimalPlaces` de la moneda que ya viene en la misma fila.
- **`deletedAt` y `deletionReason` solo aparecen si el producto está retirado**, y `deletionReason` se pide al puerto **solo entonces**: en un producto vivo esa consulta no se ejecuta.
- **No devuelve autoría** (`CA-PM-081`), ni siquiera resuelta desde la auditoría.

!!! important "El identificador no canónico devuelve `400`, no `404`"

    Lo resuelve `CanonicalUuidConverter`, que ya existe en `shared/error` desde que `RF-SP-018` cerró ese hueco. No hay que escribir nada: hay que **probarlo**, porque el defecto original era que `UUID.fromString` del JDK acepta formas laxas y el rechazo se convertía en «no existe» ante algo que nunca pudo existir.

## 6. Autorización

`products:read`. El motivo del retiro llega **con ese mismo permiso** (`spec.md` §14, resolución 1), y esa es la consecuencia asumida: `products:read` alcanza a un dato que en la auditoría acota `audit:read-deletions`. Acotada a la consulta individual — el listado no lo lleva.

## 7. Auditoría

Ninguna.

## 8. Transaccionalidad

`@Transactional(readOnly = true)`. Una sentencia para el producto; una segunda, **solo si está retirado**, para el motivo.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Columna `deletion_reason` en `products` | Duplica lo que ya está en el registro de eliminación: dos verdades que divergen en cuanto una se corrija |
| `JOIN` desde `PM` a `audit_deletion_log` | Entra en un almacén que `PM` no gobierna, y ata este módulo a su esquema |
| Un puerto en `SP` para el motivo | La auditoría no es de `SP`: es infraestructura compartida donde cada módulo escribe. `SP` posee **consultarla**, que es otra cosa |
| Devolver también el actor | `CA-PM-081` lo prohíbe: el Art. V.7 mantiene la autoría en la auditoría |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El puerto de lectura del motivo puede convertirse en la puerta trasera de la auditoría** | Nace estrecho —módulo, entidad, identificador, y devuelve un texto— y se prueba que no da acceso a nada más. Ampliarlo exige decidirlo, no basta con añadir un método |
| 2 | La escala del precio se aplica al serializar y podría olvidarse en otras respuestas | Se resuelve en un solo componente de serialización compartido por `RF-PM-001` a `RF-PM-004`, con prueba sobre una moneda de cero decimales |

## 11. Estrategia de prueba

| Qué se prueba | Nivel | Cómo |
|---|---|---|
| Los siete criterios de `spec.md` §12 | API | Producto vivo, retirado, upgrade y servicio |
| El motivo del retiro | API | Se retira con un motivo conocido y el detalle lo devuelve **literal** |
| El producto vivo **no** consulta el registro de eliminación | Integración | Número de sentencias: una, no dos |
| Sin autoría en la respuesta | API | Ni `createdBy` ni nada equivalente, ni resuelto |
| Precio con los decimales de su moneda | API | Con una moneda de dos decimales y otra de cero |
| Identificador no canónico | API | `400` con `VAL-001`, no `404` |
| El puerto no alcanza lo ajeno | Integración | Pedir el motivo de una entidad de otro módulo no devuelve nada |
