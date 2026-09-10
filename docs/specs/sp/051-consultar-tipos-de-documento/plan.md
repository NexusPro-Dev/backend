# PLAN — `RF-SP-051` Consultar tipos de documento

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-051` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 08-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 08-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, excepciones, validaciones y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí. Este documento decide tres cosas: **qué filas lleva la siembra y por qué esa lista es la regla**, **por qué no hay endpoint de escritura ni siquiera para el estado**, y **cómo se verifica una regla que consiste en una ausencia**.

---

## 1. Enfoque

Es la consulta más simple del módulo —una tabla, sin filtros, sin paginación— y la que carga la decisión más grande de esta entrega, que además **no está en el código**: está en la lista de `INSERT` de una migración.

Tres decisiones lo gobiernan:

1. **La siembra es la regla, y por eso se revisa como se revisa una regla.** Lo que este catálogo contiene decide quién puede registrarse en el sistema. Añadir una fila no es configurar: es cambiar `RN-SP-035` sin escribirla.
2. **No hay ninguna operación de escritura, ni siquiera de estado.** Es la asimetría deliberada con `RF-SP-022` y `RF-SP-023`, que sí existen para países y monedas (§5).
3. **La regla se verifica por ausencia**, que es lo que ningún otro requerimiento del sistema hace. `CA-SP-587` comprueba que ciertas filas **no están** (§11).

Se hereda entera la forma de [`RF-SP-019`](../019-consultar-monedas/plan.md): proyección en lugar de agregado, colección sin envoltura de página, indicador opcional para incluir lo inactivo. Lo que allí está argumentado no se repite.

## 2. Cambios de esquema

Tres migraciones. `V67__products_precio_publico.sql` es la última comprometida — y las mías se corrieron de `V67` a `V70` el mismo día, porque aquella se escribió primero. El número no se reserva anotándolo: se reserva escribiéndolo.

### 2.1 `V70__create_document_types.sql`

```sql
CREATE TABLE document_types (
    id           uuid         PRIMARY KEY,
    abbreviation varchar(10)  NOT NULL,
    name         varchar(100) COLLATE "es-x-icu" NOT NULL,
    is_active    boolean      NOT NULL DEFAULT true,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_document_types_abbreviation UNIQUE (abbreviation),
    CONSTRAINT ck_document_types_abbreviation_format
        CHECK (abbreviation ~ '^[A-Z][A-Z0-9]{0,9}$'),
    CONSTRAINT ck_document_types_name_not_blank
        CHECK (length(btrim(name)) > 0)
);

CREATE UNIQUE INDEX uq_document_types_name
    ON document_types (f_unaccent(lower(name)));
```

Cinco decisiones:

- **La intercalación va en la columna**, `es-x-icu`, con el mismo argumento que `V16` dejó escrito para `countries`: la API de criterios no puede expresar `COLLATE`, y sin ella un catálogo ordenado por nombre coloca mal cualquier acento. Aquí importa menos que allí —son pocas filas— y se declara igual, porque el coste es cero y el día que se note ya no se puede añadir sin migrar.
- **La unicidad del nombre es funcional** sobre `f_unaccent(lower(name))` y no sobre el literal. Mismo criterio que `uq_countries_name`, y por lo mismo: dos entradas que solo difieran en acentos serían dos opciones indistinguibles en el desplegable del alta.
- **`abbreviation` es el código y no hay una columna `code` además.** Tener las dos daría tres identificadores para el mismo concepto. El `CHECK` exige mayúsculas y prohíbe el espacio porque **el único punto de entrada de esta tabla es una migración** (`RN-SP-036`) — y precisamente por eso hace falta: la API no puede meter basura aquí, pero una migración descuidada sí.
- **`is_active` con `DEFAULT true`, y ninguna operación de la API lo escribe.** Existe porque **sin ella no hay retirada posible**: `fk_users_document_type` bloquea el borrado en cuanto una persona lo referencie, de modo que la alternativa no es «no tener la columna», es «no poder retirar nunca». Y **no es una columna dormida** de las que `RF-SP-024` §2 advierte: la lee esta misma consulta desde el primer día.
- **Sin `deleted_at`.** Mismo criterio que `countries` y `currencies`: un tipo de documento no se elimina, se retira de la circulación.

### 2.2 `V70`, segunda mitad — la siembra, que **es** la regla

```sql
INSERT INTO document_types (id, abbreviation, name) VALUES
  ('…uuid v7 fijo…', 'CC',  'Cédula de ciudadanía'),
  ('…uuid v7 fijo…', 'CE',  'Cédula de extranjería'),
  ('…uuid v7 fijo…', 'PA',  'Pasaporte'),
  ('…uuid v7 fijo…', 'NIT', 'Número de identificación tributaria');
```

**Va en la misma migración que la tabla, y no en una aparte.** Es el criterio que `V54` aplicó a `movement_types` y `payment_methods`: un catálogo de tipos vacío deja el módulo sin poder registrar nada, y separar la siembra permitiría desplegar exactamente ese estado. Aquí es peor todavía — `users.document_type_id` apunta aquí y `RN-SP-035` lo exige, de modo que una base con la tabla vacía **no admite dar de alta a nadie**.

**Identificadores UUID v7 literales** (Art. V.11), como `V15` con `USD` y `V22` con el superadministrador: las pruebas y las cargas de datos tienen que poder referirlos sin consultarlos.

!!! danger "Lo que esta lista NO lleva es la funcionalidad entera"

    **No están la tarjeta de identidad ni el registro civil**, que son los documentos que identifican a un menor de edad. Esa ausencia **es** la validación de mayoría de edad que pide `RN-SP-035`: no hay identificador que poner que signifique «Tarjeta de Identidad», y `fk_users_document_type` no admite otra cosa.

    **Añadir una fila a esta lista es desactivar la regla para todo el sistema**, y por eso `RN-SP-036` saca el catálogo de la API: si se pudiera administrar por endpoint, cualquiera con el permiso lo haría sin migración, sin revisión y sin que nada fallara.

    **Y el límite queda declarado en lugar de fingirse:** `PA` y `NIT` **no prueban** mayoría de edad —un pasaporte lo tiene un niño—; están porque el negocio los admite como identificación. Lo que la lista compra es que **el camino barato para colar a un menor —declarar su tarjeta de identidad— no existe**. La prueba de verdad exige fecha de nacimiento, y la condición para registrarla está escrita en `spec.md` §14, pregunta 2.

### 2.3 `V71__usuario_con_documento_y_contacto.sql`

Las seis columnas de `users`. Se declara en su propia migración porque **no es de este requerimiento**: es de la enmienda a `RF-SP-024`, y allí está razonada. Se nombra aquí solo para fijar el orden — `V71` depende de `V70`, porque su clave foránea apunta a una tabla que la anterior crea.

### 2.4 `V72__seed_document_types_permission.sql`

El permiso `document-types:read` con su UUID v7 literal, **y su asociación a `SUPERADMIN` y `ADMIN` en la misma migración**, con la guarda que aborta si falta alguna de las filas. Es el patrón de `V40`, `V45`, `V51`, `V60` y `V66`.

**Un solo permiso y ninguno de escritura**, y es el único recurso del catálogo del que se puede decir eso. No es que falten por escribir: `RN-SP-036` los prohíbe.

## 3. Componentes afectados

Paquete raíz: `com.factech.nexus.modules.system.documenttypes`. Reglas de dependencia de `architecture.md` §5.2.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `application` | `DocumentTypeItem` | Nuevo | Modelo de lectura: identificador, abreviación, nombre y estado |
| `application` | `DocumentTypeCatalogResponse` | Nuevo | Envoltura `{ "content": [...] }`, sin metadatos de página |
| `application` | `ListDocumentTypesRequest` | Nuevo | Un solo parámetro, `includeInactive`, y **`Boolean` y no `boolean`** — un primitivo en un `@ModelAttribute` hace que la petición sin el parámetro falle con `400`, que es el defecto que el catálogo de monedas tuvo que corregir |
| `domain` | — | — | **Sin participación.** No hay regla que aplicar: la única que gobierna este catálogo vive en el contenido de la tabla y en una clave foránea |
| `domain/repository` | `DocumentTypeQueryRepository` | Nuevo | Puerto de consulta. **Solo lectura**: no declara ningún método de escritura, y esa ausencia es `RN-SP-036` en el código |
| `domain/repository` | `JpaDocumentTypeQueryRepository` | Nuevo | Adaptador. Una sentencia, ordenada por nombre |
| `domain/service` | `ListDocumentTypesService` | Nuevo | Caso de uso. `@Transactional(readOnly = true)` |
| `interfaces` | `DocumentTypeController` | Nuevo | `GET /api/v1/document-types`. **Un solo método** |

**No hay agregado `DocumentType`.** Es un catálogo que nadie escribe: un agregado con un constructor que ningún caso de uso invoca es código muerto que además sugiere que existe un alta. `countries` sí lo tiene porque `RF-SP-020` lo registra por API; aquí no hay nada que registrar.

**El puerto que `RF-SP-024` y `RF-SP-027` usan para verificar el tipo NO es este.** Es `AssignableDocumentType`, del módulo de usuarios, y va en la enmienda de aquel plan — por el mismo reparto con el que `AssignableCountry` no es el repositorio de `countries`: el alta de una persona no lee el catálogo entero para comprobar una fila.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/document-types` | Catálogo de documentos de identidad admitidos |

**Petición**

```
GET /api/v1/document-types?includeInactive=false
```

| Parámetro | Tipo | Por defecto | Notas |
|---|---|---|---|
| `includeInactive` | booleano | `false` | `true` incorpora los retirados de la circulación |

**Respuesta `200`**

```json
{
  "content": [
    { "id": "01a081…-7001-…", "abbreviation": "CC",  "name": "Cédula de ciudadanía",  "isActive": true },
    { "id": "01a081…-7002-…", "abbreviation": "CE",  "name": "Cédula de extranjería", "isActive": true },
    { "id": "01a081…-7003-…", "abbreviation": "NIT", "name": "Número de identificación tributaria", "isActive": true },
    { "id": "01a081…-7004-…", "abbreviation": "PA",  "name": "Pasaporte", "isActive": true }
  ]
}
```

- **`content` y no un array desnudo**, con el mismo criterio que el catálogo de monedas y el de países: una respuesta que hoy es una lista y mañana necesita un metadato obliga a cambiar la forma para todos si nació desnuda.
- **Sin `totalElements` ni `totalPages`.** No se pagina, y publicar contadores de una colección que siempre viene completa invita a construir sobre ellos una paginación que no existe.
- **Ordenado por nombre**, no por abreviación: es lo que se muestra en el desplegable. La intercalación la decide la columna (§2.1).
- **`isActive` se devuelve siempre**, incluso cuando solo se piden los activos y por tanto vale `true` en todas las filas. Es el criterio de `RF-SP-025` con `deletedAt`: un campo que aparece y desaparece obliga al cliente a tratar dos formas del mismo recurso.
- **No existe `createdAt` ni `updatedAt`.** Cuándo se sembró una fila de un catálogo no responde ninguna pregunta de negocio; quien lo necesite mira la migración.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | El actor no posee `document-types:read` | `AUTH-002` |
| `500` | Fallo no controlado | `ERR-500` |

**No hay `400`**: el único parámetro es un booleano opcional que Spring convierte o rechaza en el borde. **No hay `404`**: el catálogo siempre existe, y vacío devolvería `200` con `content` vacío — que es un estado que `CA-SP-588` prohíbe que ocurra, no una respuesta que haya que diseñar.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/document-types` | `document-types:read` |

**Y ningún otro, porque no hay ningún otro endpoint.** Conviene decir por qué no se registran `document-types:create` ni `document-types:update` «para el futuro»: un permiso declarado sin operación acaba concediéndose en una revisión de roles, y el día que alguien escriba la operación se encontrará con que ya hay quien la puede usar. `RN-SP-036` dice que esa operación no debe existir.

!!! warning "El formulario público de `RF-SP-045` no puede leer este catálogo, y es el mismo bloqueo que el de países"

    `RF-SP-045` registra clientes **sin autenticación** y `RN-SP-035` le exige declarar tipo de documento. Este endpoint pide `document-types:read`, que quien se está registrando no tiene por definición — exactamente el mismo problema que `RF-SP-021` con los países, anotado como bloqueo 6 en aquella tripleta.

    **Lo que este requerimiento aporta al bloqueo es que ya son dos catálogos y no uno**, y eso inclina la balanza: una excepción puntual se puede discutir; dos catálogos que el registro público necesita y no puede leer piden **una decisión de forma** —un endpoint público de catálogos bajo `/auth`, con su límite de tasa— en lugar de dos parches. Se anota en `tasks.md` §4 y **no se resuelve aquí**: es decisión del responsable del proyecto.

## 6. Auditoría

**Ninguna.** Es una consulta y no altera nada; `architecture.md` §6.6 no audita lecturas. La denegación por falta de permiso la emite la capa de seguridad como en cualquier endpoint.

## 7. Transaccionalidad

`@Transactional(readOnly = true)` sobre el caso de uso, con una sola sentencia. No hay concurrencia que considerar: la tabla solo la escribe una migración, y Flyway no corre a la vez que la aplicación atiende peticiones.

## 8. Impacto sobre otros módulos

**Ninguno hoy, y uno declarado para mañana.** `MV` y `PM` no leen este catálogo. Quien sí lo hará es cualquier flujo de alta de personas que se construya después —el registro por enlace de `RF-SP-045` es el primero—, y la obligación queda escrita: **quien referencie `document_types(id)` declara su clave foránea sin `ON DELETE`**, porque `RN-SP-036` no admite borrar una fila y un `SET NULL` dejaría a una persona sin identificación.

## 9. Alternativas consideradas

| Alternativa | Por qué se descarta |
|---|---|
| **Una columna `acredita_mayoria_de_edad` con todos los tipos en el catálogo** | Es la alternativa natural y la que este diseño existe para no tomar. Haría que registrar a un menor fuera *posible y rechazado*, y bastaría con que un caso de uso futuro olvidara mirar la columna. Además obligaría a que el catálogo publicara **qué documentos son de menor**, que es información que nadie necesita para rellenar un formulario |
| **Un `enum` en el código en lugar de una tabla** | Barato y equivocado: cada tipo nuevo sería un despliegue, y `users.document_type_id` no tendría a qué apuntar — la validación volvería a ser un `if` |
| **Administrar el catálogo por API con `document-types:create`** | Es lo que `RN-SP-036` prohíbe. Dejaría desactivar la validación de mayoría de edad con una llamada |
| **Un `RF-SP-052` para cambiar `is_active`**, por simetría con `RF-SP-022` y `RF-SP-023` | Se descarta **hoy** y no para siempre. Retirar un tipo de documento es raro y grave; que cueste una migración revisada es el precio correcto mientras no haya nadie pidiéndolo. La condición para registrarlo: en cuanto haya que retirar un tipo en un entorno con datos y sin ventana de despliegue |
| **Devolver el catálogo sin envoltura, como array desnudo** | Cierra la puerta a añadir un metadato sin romper a todos los clientes. Mismo criterio que los otros dos catálogos |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Alguien añade un tipo de menor de edad a la siembra**, en esta migración o en una futura, y la validación desaparece sin que nada falle | `CA-SP-587` comprueba que la tarjeta de identidad y el registro civil **no están**, por abreviación y por nombre. Es la única prueba del sistema que verifica una ausencia, y es la que sustituye al `if` |
| 2 | Alguien registra `document-types:create` en una revisión de permisos | El catálogo de `security.md` §4.4 lo declara explícitamente como el único recurso sin escritura, con el motivo |
| 3 | La tabla queda vacía en algún entorno por una siembra que falló a medias | La siembra va en la **misma migración** que la tabla (§2.2), de modo que o están las dos cosas o no está ninguna |
| 4 | El registro público de `RF-SP-045` no puede leer el catálogo | Declarado como bloqueo, no resuelto (§5) |

## 11. Estrategia de prueba

| Nivel | Qué se prueba |
|---|---|
| Integración | El catálogo se devuelve completo y ordenado por nombre; los inactivos solo bajo petición; `403` sin el permiso |
| **Esquema** | **Que la tarjeta de identidad y el registro civil NO están** (`CA-SP-587`), y que el catálogo no está vacío (`CA-SP-588`) |
| Contrato | `OpenApiContractIT` lista la ruta y **no** lista ninguna otra bajo `/api/v1/document-types` |

**La prueba de la ausencia se escribe contra el contenido de la tabla y no contra la respuesta del endpoint**, y la diferencia importa: si se escribiera contra la respuesta, pasar a `includeInactive=false` la haría verde con una tarjeta de identidad inactiva dentro — que sigue siendo una fila que alguien puede reactivar con un `UPDATE`.
