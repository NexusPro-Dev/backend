# PLAN — `RF-SP-052` Consultar el catálogo de brokers

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-052` |
| Especificación | [`spec.md`](spec.md), aprobada el 08-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 08-09-2026 |

---

## 1. Enfoque

**Se copia el catálogo de tipos de documento**, que es el precedente más reciente y el más parecido: una tabla sembrada por migración, un `GET` sin paginación y un permiso de lectura. Copiar el patrón no es pereza — es lo que hace que los cuatro catálogos del módulo se comporten igual, y que quien aprenda uno sepa los otros.

**Lo que este requerimiento trae de nuevo no es el endpoint sino la segunda tabla**, `user_brokers`, que se crea **sin código que la escriba**: su caso de uso (`RF-SP-053`) no está decidido. Se construye ahora porque el responsable del proyecto pidió las dos cosas, y porque la regla que la gobierna —`RN-SP-038`— vive en el esquema y no en un caso de uso, de modo que **se puede declarar y probar sin endpoint**.

## 2. Cambios de esquema

Tres migraciones:

| Migración | Qué hace |
|---|---|
| `V73__create_brokers.sql` | La tabla `brokers` con su índice único funcional sobre el nombre |
| `V74__create_user_brokers.sql` | La tabla `user_brokers`, sus dos claves foráneas y el único de `RN-SP-038` |
| `V75__seed_brokers_permission.sql` | El permiso `brokers:read` y su asociación a `SUPERADMIN` y `ADMIN` |

!!! warning "La siembra del catálogo NO va en estas migraciones"

    `brokers` nace **vacía**, y es lo correcto: los nombres de los brokers son una decisión de negocio que nadie ha tomado todavía. El día que se den, entran en su propia migración — que es exactamente lo que `RN-SP-039` dice que debe ocurrir con cada alta.

    Hasta entonces el endpoint responde `200` con la colección vacía (`FA-002`), y eso **no es un fallo**: es el estado que corresponde a un catálogo sin sembrar.

### 2.1 El único del nombre, y por qué es funcional

`uq_brokers_name` va sobre `f_unaccent(lower(name))` y no sobre `name` literal, igual que en `countries` y `document_types`. Con el nombre como **única** columna de negocio, es él quien identifica: sin normalizar, «Exness» y «exness» son dos filas y `user_brokers` se reparte entre ellas sin que nada falle.

### 2.2 El único de `user_brokers` es la decisión del requerimiento

```sql
ALTER TABLE user_brokers
    ADD CONSTRAINT uq_user_brokers_cuenta UNIQUE (broker_id, external_id);
```

**No es `(user_id, broker_id)`**, que es el que sale solo al escribir la tabla. La diferencia es todo (`RN-SP-038`):

| Único | Varias cuentas de la misma persona en un broker | La misma cuenta declarada por dos personas |
|---|---|---|
| `(user_id, broker_id)` | **Prohibido** — y es lo normal en el ramo | **Permitido** — y es el fraude |
| `(broker_id, external_id)` | **Permitido** | **Prohibido** |

**Y lo sostiene el índice, no una comprobación previa**: dos altas simultáneas de la misma cuenta leen una tabla sin la fila, las dos creen que pueden, y solo chocan en el motor. Es la misma lección que `RF-SP-047 · T-12` dejó escrita el mismo día con el `EXCLUDE` de las tasas.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | `Broker` — entidad de lectura | `SP` |
| `domain/repository` | `BrokerRepository` (Spring Data) | `SP` |
| `application` | `BrokerResponse`, `BrokerCatalog` | `SP` |
| `domain/service` | `ListBrokersService` | `SP` |
| `interfaces` | `BrokerController` — `GET /api/v1/brokers` | `SP` |

**`user_brokers` no gana entidad JPA**, y es deliberado: no hay caso de uso que la lea ni que la escriba, y una entidad sin uso es código que hay que mantener para que nadie lo llame. La tabla existe, sus reglas las sostiene el esquema, y una prueba de esquema las comprueba.

## 4. Contrato de API

`GET /api/v1/brokers?includeInactive=false`

```json
{
  "content": [
    { "id": "01a0…", "name": "Exness", "isActive": true }
  ]
}
```

- **Envuelto en `content`** y no como arreglo desnudo, como los otros tres catálogos: deja sitio a paginar el día que haga falta sin romper a nadie.
- **Sin marcas temporales**: `createdAt` diría cuándo se aplicó la migración de siembra, que es distinto en cada entorno y no significa nada.
- **Sin totales**: no se pagina, de modo que `totalElements` sería el tamaño del arreglo dicho dos veces.

## 5. Autorización

`@PreAuthorize("hasAuthority('brokers:read')")`. El permiso se siembra con identificador **literal** (Art. V.11) y se asocia a `SUPERADMIN` y `ADMIN`, con la guarda al final de la migración que aborta si alguna de las dos filas no se insertó.

## 6. Auditoría

**No audita.** Es una lectura.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Una consulta.**

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Administrar el catálogo por API** | Decisión del responsable del proyecto: son pocos y cambian poco, y cada alta merece quedar en el historial del repositorio |
| **Dar a `brokers` una columna `code`** | «De momento el nombre». Añadirla hoy sería inventar un identificador que nadie usa; el coste de añadirla después es una migración |
| **`(user_id, broker_id)` como único** | Prohíbe lo que sí se admite y permite lo que no. Ver §2.2 |
| **Crear la entidad JPA de `user_brokers`** | No hay caso de uso. Se creará con `RF-SP-053`, y entonces con su forma decidida |

## 9. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El único se escribe sobre `(user_id, broker_id)`** por ser el que sale solo | `T-06` lo prueba **por los dos lados**: dos cuentas de la misma persona en el mismo broker **se admiten**, y la misma cuenta declarada dos veces **se rechaza** |
| 2 | El catálogo se queda **vacío para siempre** porque nadie recuerda sembrarlo | Queda declarado en `tasks.md` §4 como bloqueo abierto: **faltan los nombres de los brokers** |
| 3 | Se numera una migración que otra rama ya tomó | Se comprueba el máximo aplicado antes de escribir. Es lo que pasó con `V62`/`V64` y con `V67`, dos veces en dos días |

## 10. Estrategia de prueba

- **Integración del endpoint**: catálogo con activos e inactivos, `includeInactive`, orden por nombre y `403` sin permiso.
- **Esquema**: el único del nombre y —la que importa— **el único de la cuenta por los dos lados**.
- **Siembra**: el permiso existe, con identificador literal, y lo tienen los dos roles.
