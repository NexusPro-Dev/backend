# PLAN — `RF-SP-021` Consultar países

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-021` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento es el de [`spec.md`](spec.md) y no se repite aquí. Este documento decide dos cosas que la especificación pide sin decir cómo: **qué significa exactamente «orden alfabético» cuando hay acentos**, y **por qué este catálogo sí recibe un índice de búsqueda cuando el de permisos y el de membresías no lo recibieron**.

---

## 1. Enfoque

Una sentencia de lectura sobre una proyección, sin `JOIN`, sin paginar y sin reglas de negocio. Es el mismo molde de `RF-SP-010` y `RF-SP-017`: catálogo completo envuelto en `content`, búsqueda opcional insensible a mayúsculas y acentos, y ninguna participación de `domain`.

Dos decisiones lo distinguen de sus dos hermanos, y ambas vienen de que este catálogo **crece por API sin techo declarado** (`RF-SP-020`), mientras que el de permisos crece por migración y el de membresías crece cuando el negocio define un nivel:

1. **Sí recibe índice de trigramas.** `requirements/sp.md` §10.7 ya lo declara como `ix_countries_busqueda`, y este plan lo crea y explica por qué aquí el criterio de `RF-SP-010` §2 —«no mantener una estructura para acelerar lo que ya es inmediato»— no aplica.
2. **El orden se declara con una intercalación explícita.** El cuarto caso límite de `spec.md` §13 dice que «el orden alfabético debe seguir la configuración regional del idioma, no el orden de bytes», y eso no se cumple solo poniendo `ORDER BY name`.

## 2. Cambios de esquema

**Migración:** `V17__create_country_search_index.sql`

La tabla `countries`, sus dos índices únicos y sus dos `CHECK` los crea `V16__create_countries.sql` (`RF-SP-020`). Esta migración **no cambia columnas ni restricciones**: añade la estructura de acceso que la búsqueda exige.

| Tabla | Cambio | Detalle |
|---|---|---|
| `countries` | Altera (índice) | `ix_countries_busqueda`, GIN de trigramas sobre `f_unaccent(lower(code))` y `f_unaccent(lower(name))` |

```sql
CREATE INDEX ix_countries_busqueda ON countries USING gin (
    f_unaccent(lower(code)) gin_trgm_ops,
    f_unaccent(lower(name)) gin_trgm_ops
);
```

Las extensiones y `f_unaccent` las crea `V1__create_shared_functions.sql` (`RF-SP-010`), y allí vive la justificación de por qué `unaccent` no es indexable directamente y hay que envolverla. No se repite. Este es su **quinto** consumidor, después del catálogo de permisos, `ix_roles_busqueda` de `RF-SP-002`, `ix_audit_deletion_log_reason_busqueda` de `RF-SP-012` y `uq_countries_name` de `RF-SP-020`.

**Por qué GIN de trigramas y no un B-tree**, en una línea: un `LIKE '%termino%'` no puede usar un B-tree en ningún caso, porque el B-tree ordena por prefijo y una coincidencia que puede empezar en cualquier posición no acota el rango. El argumento completo está en `RF-SP-002` §2 y aquí solo se hereda; lo mismo vale para el índice multicolumna, que el planificador combina con `BitmapOr` sobre las dos ramas del `OR`.

**Por qué aquí sí y en `permissions` y `memberships` no.** `RF-SP-010` §2 rechazó el índice sobre `permissions` con un argumento correcto: veintitrés filas se recorren más rápido de lo que cuesta consultar un índice. `RF-SP-017` §2 rechazó el de `memberships` por lo mismo. La diferencia no es el tamaño de hoy sino **quién decide el tamaño de mañana**:

| Catálogo | Cómo crece | Techo |
|---|---|---|
| `permissions` | Solo por migración (`RN-SP-004`) | Lo fija el equipo, requerimiento a requerimiento |
| `memberships` | Por API, pero solo cuando el negocio define un nivel | Unos pocos, por naturaleza del negocio |
| `countries` | **Por API, a demanda** (`RF-SP-020`) | Menos de doscientos, y sin nada que lo imponga |

`requirements/sp.md` §10.7 ya lo declara, de modo que la decisión estaba tomada y este plan la ejecuta en lugar de reabrirla. Lo que sí conviene dejar escrito, para que no se lea como una contradicción: **con pocas decenas de filas el planificador probablemente prefiera el recorrido secuencial, y eso no es un defecto del índice**. El índice existe para el caso en que el catálogo se pueble de verdad, y su coste de escritura es irrelevante porque las escrituras son altas manuales.

**El índice no es parcial.** Excluir los inactivos con `WHERE is_active` lo haría marginalmente más pequeño y dejaría sin cobertura la consulta con `includeInactive=true` (`CA-SP-172`). Es el mismo criterio con el que `ix_roles_busqueda` no excluye los eliminados (`RF-SP-002` §2): la distinción se deja al predicado, no al índice.

**No se crea índice para el ordenamiento.** El `ORDER BY` va sobre una expresión con intercalación explícita (§4), y un índice que lo sostuviera tendría que declararse con esa misma intercalación. Sobre un catálogo de este tamaño el ordenamiento es despreciable, y crear un índice para él sería mantener una estructura por si acaso.

**Recordatorios de la plantilla que no aplican:** esta migración no crea tablas, así que no hay clave primaria UUID v7, ni `created_at`/`updated_at`, ni columnas de actor que omitir, ni integridad declarativa que añadir. El nombre `ix_countries_busqueda` viene fijado en español por `requirements/sp.md` §10.7 y se conserva tal cual, al amparo de la excepción que `development-guide.md` §4.1 recoge desde el 21-08-2026.

## 3. Componentes afectados

Paquete raíz: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | — | — | Sin participación: `RN-SP-009` gobierna la escritura, y este requerimiento no escribe |
| `application` | `ListCountriesService` | Nuevo | Caso de uso. `@Transactional(readOnly = true)` |
| `application` | `ListCountriesQuery` | Nuevo | Dos criterios ya normalizados: término recortado e indicador de inactivos |
| `application` | `CountryItem` | Nuevo | Modelo de lectura |
| `application` | `CountryQueryRepository` | Nuevo | Puerto de consulta |
| `infrastructure` | `JpaCountryQueryRepository` | Nuevo | Adaptador. Predicado y proyección con la API de criterios; el ordenamiento es un `ORDER BY name, id` corriente, porque la intercalación del español es de la columna (§4) |
| `infrastructure` | `CountryEntity` | Sin cambios | Mapeo JPA de `RF-SP-020`. Se usa como metamodelo; la consulta no lo instancia |
| `api` | `CountryController` | Modificado | Añade `GET /api/v1/countries` |
| `api` | `ListCountriesRequest` | Nuevo | Dos parámetros de consulta. Sin Bean Validation: `spec.md` §11 no declara ninguna validación |
| `api` | `CountryResponse` | Sin cambios | DTO definido en `RF-SP-020`. Se reutiliza tal cual (§4) |
| `shared/api` | `PageResponse<T>` | Sin cambios | **No se usa**: este catálogo no se pagina (§4) |

**`CountryQueryRepository` es un puerto distinto de `CountryRepository`.** El segundo lo creó `RF-SP-020` para guardar el agregado; este devuelve modelos de lectura. Es el criterio con el que `RF-SP-002` separó `RoleQueryRepository` de `RoleRepository` y `RF-SP-017` §3 hizo lo propio con las membresías.

**El detalle de un país no existe como endpoint**, y conviene decirlo aquí porque es la asimetría con `permissions` y `memberships`, que sí lo tienen (`RF-SP-015`, `RF-SP-018`). Ningún requerimiento lo declara: el catálogo se devuelve entero y cada elemento trae ya todo lo que hay que saber de un país —código, nombre y estado—, de modo que un `GET /api/v1/countries/{id}` devolvería un elemento de una lista que el cliente ya tiene. Esa asimetría tenía una consecuencia que se cerró el 21-08-2026 al aprobar `RF-SP-020`: aquel requerimiento devolvía `Location: /api/v1/countries/{id}` en su `201`, una URL que **no resuelve** —y que devuelve `404`, no `405`, porque la ruta no está mapeada para ningún método—. La cabecera se retiró; el `id` viaja en el cuerpo, que es de donde el cliente lo toma. Si algún día existiera el detalle, añadirla es aditivo.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/countries` | Catálogo completo de países, ordenado alfabéticamente por nombre |

**Petición**

```
GET /api/v1/countries?search=panama&includeInactive=false
```

| Parámetro | Tipo | Por defecto | Notas |
|---|---|---|---|
| `search` | texto | — | Sobre código y nombre. Recortado; en blanco equivale a ausente |
| `includeInactive` | booleano | `false` | Incorpora los países desactivados (`CA-SP-172`) |

- **No hay `page`, `size` ni `sort`.** `spec.md` §6.1 y §14 lo deciden de forma explícita, y no aceptarlos siquiera es lo que lo hace verificable: los parámetros desconocidos se ignoran en silencio por defecto en Spring. El DTO declara **dos** campos y la respuesta **no** se envuelve en `PageResponse`. Mismo mecanismo de `RF-SP-010` §4.
- **`includeInactive` añade, no sustituye.** Con `true` se devuelven activos e inactivos; `spec.md` §4.1 dice «los inactivos se piden explícitamente», y un filtro que ocultara los activos respondería una pregunta que nadie hace. Igual que en `RF-SP-019` §4.
- **La búsqueda y el estado son independientes**: buscar «pan» con `includeInactive=false` devuelve solo los activos que coinciden.
- **Un valor no booleano en `includeInactive` produce `400`** por conversión, aunque `spec.md` §11 no declare validaciones: es un fallo de forma que el conversor de Spring resuelve antes del caso de uso.

**Respuesta `200`**

```json
{
  "content": [
    { "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d50", "code": "CO", "name": "Colombia", "isActive": true },
    { "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d51", "code": "PA", "name": "Panamá",   "isActive": true }
  ]
}
```

- **La colección va envuelta en `content`, no como arreglo desnudo**, y **no se reutiliza `PageResponse<T>` con valores de adorno**, por lo dicho en `RF-SP-010` §4. `CA-SP-140` exige que no haya paginación, y rellenar `totalPages: 1` diría lo contrario.
- **Se devuelve el mismo `CountryResponse` que el alta.** El catálogo trae exactamente lo que `RF-SP-020` devuelve al registrar: `id`, `code`, `name` e `isActive`. Un tipo propio sería una segunda representación sin un solo campo de diferencia, que es el criterio de `RF-SP-015` §4.
- **`isActive` se devuelve siempre**, también cuando no se pidieron los inactivos y por tanto vale `true` en todos los elementos. Omitirlo en ese caso haría que la forma de la respuesta dependiera de los parámetros, y obligaría al cliente a tratar dos formas del mismo recurso.
- **`id` se devuelve aunque el selector muestre el nombre.** Es lo que se guarda al referenciar un país (`RF-SP-020` §8), no el código: un código ISO puede reasignarse, un identificador no.
- **No se devuelven `createdAt` ni `updatedAt`**, ni prefijo telefónico ni moneda (`spec.md` §14, pregunta 3): no existen esos campos.
- **Un solo idioma** (`spec.md` §14, pregunta 3 de aquella tabla): el nombre se devuelve tal como se registró. Traducir un catálogo es parte de una decisión de internacionalización que alcanza a toda la interfaz.

### El orden alfabético

`spec.md` §13 lo exige explícitamente: «el orden alfabético debe seguir la configuración regional del idioma, no el orden de bytes». No es un matiz teórico. Con la intercalación `C` —la que una base de datos puede tener por defecto si se creó sin configuración regional— el orden es por bytes UTF-8, y entonces «Panamá» se coloca **después** de «Perú», porque la `á` tiene un valor mayor que cualquier letra sin acento. Un selector ordenado así parece roto.

**La intercalación se declara en la columna, no en la consulta**, y esto se corrigió el 21-08-2026 al aprobar este plan. El borrador escribía `ORDER BY c.name COLLATE "es-x-icu"` en la sentencia y decía a la vez que el adaptador la construye con la API de criterios: **las dos cosas no pueden ser ciertas**, porque esa API no tiene forma de expresar `COLLATE` —tampoco Hibernate 6—, de modo que el orden habría exigido una consulta nativa que el propio plan no contemplaba.

La corrección es declarativa y va en la migración de `RF-SP-020`:

```sql
name varchar(100) COLLATE "es-x-icu" NOT NULL
```

```sql
ORDER BY c.name, c.id   -- ya ordena en español: la intercalación es de la columna
```

- **La intercalación es de la columna y no del entorno.** Depender de `LC_COLLATE` haría que el mismo código produjera órdenes distintos en la máquina de un desarrollador, en `testing` y en `production`, y ese es exactamente el tipo de diferencia que nadie nota hasta que un usuario reporta que su país no aparece donde debería. Declararla en la columna elimina esa dependencia **y** la de que cada consulta futura sobre `countries` se acuerde de escribir el `COLLATE`: el orden correcto pasa a ser el comportamiento por omisión.
- **No afecta a las restricciones que `RF-SP-020` declara.** `uq_countries_name` va sobre `f_unaccent(lower(name))`, una expresión cuya intercalación es la suya, y `ck_countries_name_not_blank` no compara texto. La igualdad que usa la unicidad sigue siendo la misma.
- **Se elige ICU y no una intercalación del sistema operativo.** Las de la biblioteca C dependen de qué configuraciones regionales estén instaladas en la imagen del contenedor —y `postgres:17-alpine` es una imagen mínima—, mientras que ICU viene con el propio PostgreSQL y produce el mismo resultado en cualquier despliegue.
- **`es-x-icu` y no `es-CO-x-icu`**: el orden alfabético del español no cambia entre países hispanohablantes, y atar el catálogo a una configuración regional concreta sugeriría que sí.
- **`c.id` es el desempate**, por la misma razón que en `RF-SP-002`: dos países no deberían compartir nombre —`uq_countries_name` lo impide—, pero sin desempate explícito el orden de filas equivalentes queda a criterio del plan de ejecución y puede cambiar entre llamadas. Cuesta nada.
- **No se ordena por código.** `spec.md` §4.1 dice «ordenado alfabéticamente por nombre», que es lo que un selector necesita: nadie busca «Colombia» bajo la letra C de `CO`.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | `includeInactive` no es un booleano | `VAL-003` |
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | Autenticado sin `countries:read` | `AUTH-002` |
| `500` | Fallo no controlado | `ERR-500` |

**No hay `404` ni `422`.** `spec.md` §10 y §11 no declaran ninguna excepción ni validación propias. Una búsqueda sin coincidencias devuelve `200` con `content` vacío (`FA-001`, `CA-SP-142`), y un catálogo vacío también: es el estado real al arrancar (`spec.md` §13), porque los países no se siembran.

**Cuántas consultas cuesta.** Una:

```sql
SELECT c.id, c.code, c.name, c.is_active
  FROM countries c
 -- Cada bloque se añade solo si su criterio está presente; no se neutralizan con guardas
 WHERE c.is_active                                   -- solo si includeInactive es false
   AND (f_unaccent(lower(c.code)) LIKE f_unaccent(lower(:termino)) ESCAPE '\'
        OR f_unaccent(lower(c.name)) LIKE f_unaccent(lower(:termino)) ESCAPE '\')
 ORDER BY c.name, c.id;                              -- intercalación de la columna
```

**Los predicados se añaden o no se añaden; no se neutralizan con guardas.** El borrador los escribía como `(:incluirInactivos OR c.is_active)` y `(:termino IS NULL OR …)`, que produce una sola sentencia para todas las combinaciones y obliga al planificador a un plan que sirva a todas —desaprovechando `ix_countries_busqueda` justo cuando el catálogo crezca, que es para lo que existe—. Es el patrón que `RF-SP-002` §9 descartó de forma explícita, y la prosa de este mismo plan ya decía lo contrario dos párrafos más abajo.

Sin `JOIN`, sin conteo —no se pagina— y sin colecciones perezosas: no se carga `CountryEntity`, se materializa `CountryItem` con `cb.construct`.

**Cómo se aplica la búsqueda.** El término se recorta; si queda vacío, no se añade predicado. Si no, se escapan `\`, `%` y `_`, y se envía como parámetro enlazado, envuelto en comodines de contención, con `ESCAPE` explícito. La normalización la hace **la base de datos con `f_unaccent`**, nunca `java.text.Normalizer`, cuyo resultado es parecido y no idéntico al del diccionario `unaccent`. El escape es lo que impide que un `%` en el término convierta la búsqueda en «devuélvemelo todo» (`spec.md` §13, búsqueda con caracteres especiales). Todo eso es heredado de `RF-SP-002` §4 y `RF-SP-010` §4 y no se vuelve a argumentar.

**Aquí no hace falta `coalesce`**, a diferencia de `RF-SP-010` §4: se busca sobre `code` y `name`, ambos `NOT NULL`. El envoltorio sería inerte y sugeriría un problema que no existe.

**Buscar y ordenar usan criterios distintos a propósito.** La búsqueda compara sin acentos, con `f_unaccent`, de modo que «panama» encuentra «Panamá» (`spec.md` §13). El orden **sí** los tiene en cuenta, con la intercalación del español, porque en un listado el usuario quiere ver «Panamá» escrito como se escribe y colocado donde corresponde. Son dos preguntas distintas —«¿coincide?» y «¿dónde va?»— y no tienen por qué responderse con la misma función.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/countries` | `countries:read` |

- El permiso **ya existe**: lo crea `V3__seed_permissions.sql` (`RF-SP-010`).
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **El actor es «cualquier rol autenticado con el permiso»** (`spec.md` §3), no un administrador: este catálogo alimenta los selectores del alta de personas, y se concederá con holgura. Es distinto de `countries:create`, que `RF-SP-020` exige para un alta irreversible, y de `countries:update`, que `RF-SP-022` exigirá para el estado.
- **No hay filtrado por alcance de datos.** Un país no pertenece a nadie.
- **El estado no es un filtro de autorización.** Cualquiera con el permiso puede pedir los inactivos: no son información reservada, son opciones retiradas de la circulación.
- La resolución del permiso puede usar la caché de `security.md` §4.5: aquí solo se decide acceso.
- El `403` lo produce la capa de seguridad antes de entrar al caso de uso, y es ella quien emite el evento de seguridad (§6). `CA-SP-143` se satisface ahí.

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Consulta exitosa | — | **No se audita** |
| Denegación `403` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad |
| Fallo no controlado `5xx` | `audit_error_log` | `resource = 'countries'`, `operation = 'GET /api/v1/countries'`, `error_code = 'ERR-500'`, `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_change_log` | No aplica: la consulta no altera el estado (`spec.md` §7) |
| — | `audit_deletion_log` | No aplica |

Una consulta exitosa no produce evento de seguridad: el catálogo de `security.md` §8.1 es cerrado y no incluye la lectura de catálogos, y el rastro de quién consultó qué lo aporta `request_log`. Misma conclusión de `RF-SP-010` §6, `RF-SP-017` §6 y `RF-SP-019` §6.

**Este endpoint se llamará con mucha frecuencia** —alimenta un selector— y esa es una razón más para no auditarlo: un evento por cada apertura de un formulario ahogaría el registro sin decir nada que `request_log` no diga.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| La consulta | **Una sola**, `@Transactional(readOnly = true)` sobre `ListCountriesService` (`development-guide.md` §10) |
| `audit_error_log` de un fallo no controlado | **Independiente**, `REQUIRES_NEW` (Art. V.14) |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`readOnly = true` marca la transacción como de solo lectura en PostgreSQL, de modo que ningún defecto pueda escribir desde un camino de consulta —lo que aquí equivale a una garantía sobre `RN-SP-009`—. Una sola sentencia, una sola instantánea.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `RF-SP-020` | Comparte `CountryController` y `CountryResponse`. **Su `uq_countries_name` y este `ix_countries_busqueda` usan la misma función `f_unaccent`**: buscar «panama» encuentra «Panamá» por la misma razón por la que registrar los dos es imposible. **Su `V16` gana además la intercalación `COLLATE "es-x-icu"` en `countries.name`**, decidida el 21-08-2026 al aprobar este plan (§4): es lo que sostiene el orden alfabético sin exigir una consulta nativa, y debe ir en la migración que crea la tabla, no en una alteración posterior. Ningún cambio en su contrato de API |
| **`RF-SP-022`** | Cambiar el estado de un país es lo que da sentido a `includeInactive`. Cuando exista, un país desactivado desaparece de la respuesta por defecto **sin que este endpoint cambie**, y `CA-SP-172` ya lo verifica hoy sembrando un inactivo en la prueba |
| `RF-SP-010` | `f_unaccent` gana su quinto consumidor. La consecuencia operativa se extiende: modificar el diccionario `unaccent` obliga a `REINDEX` también de `ix_countries_busqueda` |
| `RF-SP-024` y siguientes | Este es el endpoint que alimenta el selector de país en el alta de personas. **Obligación declarada**: se guarda el `id`, no el código, y al **resolver** un país ya guardado no se filtra por `is_active` —solo al **ofrecer** opciones—, porque un país inactivo sigue resolviéndose para quien ya lo tenía (`spec.md` §13) |
| `architecture.md` | §7.4 exige paginar «las colecciones», y este endpoint se aparta de forma consciente, como ya hicieron `RF-SP-003` §4, `RF-SP-010` §4, `RF-SP-017` §4 y `RF-SP-019` §4. No se propone enmendar el documento: la regla general sigue siendo correcta, y son las excepciones las que se justifican una a una |
| Despliegue | La intercalación `es-x-icu` exige un PostgreSQL compilado con ICU. La línea vigente es la 17 y la imagen es `postgres:17-alpine` (`architecture.md` §3), que lo incluye; **debe comprobarse antes del primer despliegue** en cualquier PostgreSQL administrado, y se anota en §10 |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| `ORDER BY name` sin intercalación explícita | Depende de la configuración regional del servidor: el mismo código produce órdenes distintos en desarrollo, en `testing` y en `production`. Con la intercalación `C`, «Panamá» va después de «Perú», que es lo que el cuarto caso límite de `spec.md` §13 prohíbe |
| Una intercalación del sistema operativo (`es_CO.utf8`) en lugar de ICU | Depende de qué configuraciones regionales estén instaladas en la imagen del contenedor, y `postgres:17-alpine` es una imagen mínima. ICU viene con PostgreSQL y produce el mismo resultado en cualquier despliegue |
| `es-CO-x-icu` en lugar de `es-x-icu` | El orden alfabético del español no cambia entre países hispanohablantes, y atarlo a uno sugeriría que sí |
| Ordenar por `f_unaccent(lower(name))`, el mismo criterio con el que se busca | Buscar y ordenar responden preguntas distintas. Ordenar sin acentos colocaría bien a «Panamá», pero también mezclaría mayúsculas y minúsculas de forma arbitraria en un catálogo internacional, y perdería las reglas de la ñ |
| Declarar la intercalación en la columna, al crear la tabla | Funciona, pero deja la decisión de presentación escondida en `V16`, donde nadie la busca; y la haría cara de cambiar, porque alterar la intercalación de una columna indexada obliga a reconstruir sus índices |
| Ordenar por código | Nadie busca «Colombia» bajo la letra C de `CO`. `spec.md` §4.1 dice «alfabéticamente por nombre» |
| No crear `ix_countries_busqueda`, por el criterio de `RF-SP-010` §2 | Ese criterio vale para un catálogo cuyo tamaño fija el equipo por migración. Este crece por API a demanda, `requirements/sp.md` §10.7 ya declara el índice, y su coste de escritura es irrelevante porque las escrituras son altas manuales |
| Índice parcial, solo sobre los activos | Dejaría sin cobertura la consulta con `includeInactive=true` (`CA-SP-172`), y a este volumen el ahorro no existe. Mismo criterio de `RF-SP-002` §2 |
| Paginar el catálogo | `spec.md` §6.1 y §14 lo resolvieron: un selector necesita todas sus opciones, y menos de doscientos elementos con código y nombre es una respuesta pequeña y cacheable. La búsqueda es lo que de verdad acota |
| Devolver la colección como arreglo desnudo | Impide añadir después cualquier metadato sin romper a todos los clientes. Mismo criterio de `RF-SP-010` §4 |
| Omitir `isActive` cuando no se piden los inactivos | Haría que la forma de la respuesta dependiera de los parámetros, obligando al cliente a tratar dos formas del mismo recurso |
| Exigir un permiso distinto para ver los inactivos | Un país inactivo no es información reservada: es una opción retirada de la circulación, y su existencia ya consta en cualquier dato que lo referencie |
| Un endpoint de detalle, `GET /api/v1/countries/{id}` | Ningún requerimiento lo declara, y devolvería un elemento de una lista que el cliente ya tiene entera. La consecuencia sobre la cabecera `Location` de `RF-SP-020` se anota en §10 |
| Traducir los nombres | `spec.md` §14, pregunta 3: es parte de una decisión de internacionalización que alcanza a toda la interfaz y no puede resolverse dentro de un catálogo suelto |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| El PostgreSQL de destino no soporta ICU y `es-x-icu` falla en tiempo de ejecución | Medio | La línea vigente es la 17 y la imagen `postgres:17-alpine` lo incluye (`architecture.md` §3). **Debe comprobarse antes del primer despliegue** en un PostgreSQL administrado, junto con la comprobación de `CREATE EXTENSION` que `RF-SP-010` §10 ya exige. El síntoma sería inmediato y visible: la consulta falla, no devuelve un orden equivocado |
| Se modifica el diccionario `unaccent` y la búsqueda deja de encontrar resultados **sin error** | Medio | Heredado de `RF-SP-010` §10: `f_unaccent` se declara `IMMUTABLE` sin serlo del todo. Cualquier cambio en el diccionario obliga a `REINDEX` de todo lo que dependa de la función, y ahora también de `ix_countries_busqueda` y de `uq_countries_name` |
| ~~La cabecera `Location` que devuelve `RF-SP-020` apunta a una ruta inexistente~~ | — | **Resuelto el 21-08-2026:** la cabecera se retiró de `RF-SP-020` §4. Apuntaba a `/api/v1/countries/{id}`, que devuelve `404` porque no está mapeada para ningún método. El `id` viaja en el cuerpo del `201` |
| La intercalación de la columna se pierde en una migración futura que recree `countries.name` | Bajo | El orden dejaría de ser el del español **sin error alguno**, que es el modo de fallo que este plan existe para evitar. Lo cubre la prueba de ordenamiento con acentos de §11, que compara contra un orden concreto y falla si la intercalación cambia |
| El catálogo crece y devolverlo entero deja de ser razonable | Bajo | El techo real son los países del mundo, menos de doscientos, y la respuesta con cuatro campos por elemento es de pocas decenas de kilobytes. Si se superara, la decisión de no paginar habría que revisarla en la especificación, no aquí |
| El frontend cachea el catálogo y no ve un país recién registrado | Bajo | `spec.md` §6.1 dice que el resultado es cacheable, y lo es; la caducidad es decisión del cliente. Un país nuevo aparece en la siguiente carga, y el alta es una operación excepcional |
| Un selector filtra por `is_active` al resolver un país ya guardado y lo muestra vacío | Medio | Obligación declarada en §8: se filtra al **ofrecer** opciones, nunca al **resolver** un dato guardado. Es la consecuencia directa del tercer caso límite de `spec.md` §13, y este requerimiento no puede impedirlo por su cuenta |

## 11. Estrategia de prueba

Niveles: **Integración** (Testcontainers sobre PostgreSQL real, con `V16` y `V17` aplicadas) y **API** (extremo a extremo por HTTP, con autenticación). No hay nivel unitario: este requerimiento no tiene `domain`.

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-140` | Integración + API | Con varios países registrados, la respuesta los trae todos ordenados alfabéticamente por nombre, y el cuerpo **no** contiene `page`, `size`, `totalElements` ni `totalPages` |
| `CA-SP-172` | API | Con un país inactivo: sin el parámetro no aparece; con `includeInactive=true` aparecen ambos, y el inactivo trae `isActive: false` |
| `CA-SP-141` | Integración + API | Buscar por código y por fragmento de nombre devuelve solo las coincidencias; combinar búsqueda e `includeInactive=false` devuelve la intersección |
| `CA-SP-142` | API | Una búsqueda sin coincidencias devuelve `200` con `content` vacío. Nunca `404` ni `204` |
| `CA-SP-143` | API | Un actor autenticado sin `countries:read` recibe `403`, no obtiene dato alguno y queda el evento de denegación en `audit_security_log` |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Catálogo vacío | API | Tras `V16` y `V17`, sin ningún alta, devuelve `200` con `content` vacío. Es el estado real al arrancar y **no** un error |
| Ordenamiento con acentos | Integración | Con «Panamá», «Perú» y «Paraguay» registrados, el orden devuelto es Panamá, Paraguay, Perú. Con la intercalación por bytes sería Paraguay, Perú, Panamá: es la prueba que verifica la decisión de §4 y **exige PostgreSQL real**. Falla también si una migración futura recrea `countries.name` perdiendo su intercalación, que es un fallo silencioso sin esta prueba |
| Ordenamiento con ñ y con mayúsculas | Integración | «España» y «Estonia» se ordenan según el español, no por byte |
| Búsqueda insensible a acentos | Integración | Buscar `panama`, `PANAMÁ` y `Panamà` encuentra «Panamá». Exige PostgreSQL real: `unaccent` no es simulable |
| Búsqueda con `%`, `_` y `\` | Integración | Se tratan como texto literal: un término con `%` no devuelve el catálogo entero |
| Búsqueda vacía o solo de espacios | API | Equivale a no filtrar: mismo resultado que la consulta sin el parámetro |
| Búsqueda por código | API | `search=co` encuentra «Colombia» por su código `CO`, y también cualquier país cuyo nombre contenga «co» |
| País inactivo ya referenciado | Integración | Un país desactivado desaparece del listado por defecto **y sigue existiendo en la tabla** con su identificador intacto: la prueba lo recupera por `id` directamente |
| Parámetros de paginación ignorados | API | `?page=2&size=5` devuelve el catálogo completo, no cinco elementos ni un error |
| Parámetro no booleano | API | `includeInactive=quizas` devuelve `400`, no se interpreta como `false` |
| Presencia de `isActive` | API | El campo viene en cada elemento también cuando no se pidieron los inactivos y todos valen `true` |
| Orden estable | Integración | Dos llamadas consecutivas devuelven los mismos elementos en el mismo orden |
| Uso efectivo del índice de búsqueda | Integración | Con doscientos países sembrados en la prueba, el `EXPLAIN` de una búsqueda muestra el recorrido de `ix_countries_busqueda`. **Con pocas filas el planificador puede preferir el recorrido secuencial y eso no es un fallo**: la prueba siembra volumen a propósito para que la comprobación signifique algo |
| Número de sentencias por petición | Integración | **Una**, con y sin búsqueda |
| Coherencia con el alta | Integración | El elemento que devuelve el listado es **campo por campo idéntico** al que `RF-SP-020` devolvió al registrarlo |
| Ausencia de escritura | API | `PUT`, `PATCH` y `DELETE` sobre `/api/v1/countries` devuelven `405`; `POST` es el alta de `RF-SP-020` y exige otro permiso |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento. No se añade ninguna nueva: no toca `domain` y no introduce dependencias entre módulos.
