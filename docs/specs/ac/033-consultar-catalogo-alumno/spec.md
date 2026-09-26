# SPEC — `RF-AC-033` Consultar el catálogo de cursos como alumno

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-033` |
| Módulo | `AC` — Academia |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 26-09-2026 |

---

## 1. Objetivo

Que el alumno vea **qué se enseña** y **qué se le abre**: todos los cursos que se ofrecen, en su orden, cada uno marcado con si su membresía vigente, uno de sus servicios vigentes o la gratuidad del curso lo abren, y con cuántas lecciones abiertas tiene; **o solo los que le abren algo**, si lo pide.

## 2. Contexto

Es la primera de las tres vistas del aula (`ac.md` §2) y la que aplica entera la decisión de §1.4: **el catálogo enseña todos los cursos que se ofrecen, tenga o no el alumno la llave que los abre** (`RN-AC-013`). La lista de visibilidad no esconde el curso: lo marca. Un alumno de `BRONCE` ve el curso de `ORO` con `accessible: false`, y es esa marca —y no la ausencia— lo que le dice que hay algo a lo que subir.

**Las llaves son dos desde el 25-09-2026** (`RN-AC-020`): la membresía vigente **o** uno de los servicios `BOT` que el alumno tenga vigentes. **Y un curso sin ninguna llave es de todos** (`ac.md` §5.2.12): `accessible: true` para todo alumno con sesión.

**El 26-09-2026 el responsable del proyecto pidió además «los cursos que puedo ver»** (`ac.md` §5.2.13): los que se le abren enteros y los que tienen **al menos una lección abierta**. Se resuelve **sin reescribir la vitrina**: el filtro **`onlyAccessible`** deja solo esos, y cada curso publica **`openLessonCount`** para que el frontend sepa por qué un curso cerrado pasó el filtro y pueda pintar «2 lecciones gratis».

Es `RF-PM-007` para cursos, y hereda de la oferta de `PM` lo que la define: **el actor sale del token** y no hay forma de preguntar por otra persona; **exige un permiso de vista** —`courses:learn`— y no el de administración, que abre lo inactivo, lo retirado y lo que no se ofrece; **solo lo ofrecido**, con la ofrecibilidad decidida por **el mismo objeto** que administración ve en el detalle (`CourseOfferability`) y no por una segunda definición escrita en la consulta del aula. **Sin paginar**, como la oferta; filtrable por categoría y dificultad, porque el alumno elige por cajón.

**Trae además las categorías vivas**, con su color, icono, portada y orden, para que el frontend pinte los cajones sin otra petición — incluidas las **vacías** (`RN-AC-008`). Y trae **la membresía vigente** de quien pregunta, presente y nula, para que la pantalla diga desde qué nivel mira.

Es el requerimiento que **hace real `CA-AC-032`** de `RF-AC-005` —una categoría retirada no se enseña en el aula y sus cursos siguen ofreciéndose—.

## 3. Actores

| Actor | Papel |
|---|---|
| Alumno | Consulta el catálogo que se le ofrece |

## 4. Alcance

### 4.1 Incluye

- Devolver **todos los cursos que se ofrecen** (`RN-AC-015`, con sus cuatro motivos), en su orden global (`RN-AC-002`), sin paginar, filtrables por **categoría**, por **dificultad** y por **`onlyAccessible`**.
- Por curso: portada, título, instructor resuelto, dificultad, descripción corta, orden, categorías vivas, **duración total**, **cuántas lecciones** y **cuántas abiertas** —contando solo lo que el alumno va a ver—, y **`accessible`**.
- **Las categorías vivas**, con color, icono, portada y orden, incluidas las vacías.
- **La membresía vigente** de quien pregunta, presente y nula.

### 4.2 No incluye

- **Lo que no se ofrece.** Ni lo inactivo, ni lo retirado, ni lo que está activo y le falta algo. Para el alumno, no existe.
- **Descripción larga, video de introducción, recomendaciones, llaves que lo abren y el árbol.** Son del detalle (`RF-AC-034`).
- **Los servicios vigentes del alumno.** Deciden `accessible` y no se publican: el alumno los ve en «mis productos» (`RF-MV-014`).
- **Cuántos cursos tiene cada categoría.** El frontend lo cuenta de la lista; con un filtro activo la cuenta respondería otra pregunta.
- **Paginación y búsqueda por texto.** Es una lista corta que se lee entera.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-002` | Los cursos salen en su orden global, con desempate por identificador; las categorías, en el suyo | `requirements/ac.md` §5.1 |
| `RN-AC-004` | `coverImageUrl` presente y nula, en el curso y en la categoría | `requirements/ac.md` §5.1 |
| `RN-AC-010` | Un curso sin categoría se ofrece igual; las categorías retiradas no se enseñan | `requirements/ac.md` §5.1 |
| `RN-AC-012` | La lista es explícita: se comprueba pertenencia, sin mirar niveles; sin llaves, de todos | `requirements/ac.md` §5.1 |
| `RN-AC-013` | Todo curso ofrecido aparece para todo alumno, salvo que pida `onlyAccessible` | `requirements/ac.md` §5.1 |
| `RN-AC-014` | La lección abierta se abre a cualquiera con sesión: cuenta en `openLessonCount` | `requirements/ac.md` §5.1 |
| `RN-AC-015` | Solo lo ofrecido, con los cuatro motivos y el mismo objeto que administración | `requirements/ac.md` §5.1 |
| `RN-AC-017` | La duración es la suma, en segundos, de las lecciones que el alumno verá | `requirements/ac.md` §5.1 |
| `RN-AC-020` | Un servicio vigente también abre el curso | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| `categoryId` | No | Solo los clasificados en esa categoría | UUID. Una categoría **retirada o inexistente** devuelve la lista **vacía**, no `404`: es un filtro, no una ruta |
| `difficulty` | No | `PRINCIPIANTE`, `INTERMEDIO` o `AVANZADO` | Fuera del dominio, `400` |
| `onlyAccessible` | No | `true` deja solo los cursos que abren algo a quien pregunta: `accessible` **o** `openLessonCount > 0` | Booleano; ausente es `false` |

**El actor sale del token.** Ni paginación ni orden: la lista es corta y el orden es el de `RN-AC-002`.

### 6.2 Salida

`200` con:

- `currentMembership { id, code, name, color }`, **presente y nula** cuando quien pregunta no tiene membresía vigente.
- `categories [{ id, name, color, icon, displayOrder, coverImageUrl }]`: **todas las vivas**, en su orden, **sin aplicar ningún filtro** — son los cajones.
- `courses [{ id, title, instructor { id, username, fullName }, difficulty, shortDescription, displayOrder, coverImageUrl, categories [{ id, name, color, icon }], totalDurationSeconds, lessonCount, openLessonCount, accessible }]`: los ofrecidos que pasan los filtros, en su orden.

**`accessible` es una sola pregunta**: ¿el curso **no declara llaves**, o la membresía vigente de quien pregunta está en su lista, o tiene vigente uno de sus servicios? La decide **un solo objeto** para las tres vistas del aula (`StudentAccess`, `plan.md` §3). **`totalDurationSeconds`, `lessonCount` y `openLessonCount` cuentan lo que el alumno verá**: las lecciones ofrecibles —activas, vivas, con contenido— de los módulos ofrecibles; `openLessonCount`, las de esas que están abiertas. Una lección inactiva o un módulo inactivo no suman, aunque administración los cuente en su detalle.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:learn`. **Postcondiciones:** ninguna.

## 8. Flujo principal

1. Llega la petición, con los filtros si los hay.
2. El sistema valida la forma de los filtros (`VAL-001`, `VAL-002`).
3. El sistema pregunta a `SP` la membresía vigente (`CurrentMembershipLookup`) y los productos vigentes (`CurrentProductsLookup`) de quien llama.
4. El sistema lee los cursos vivos y `ACTIVOS` que pasan los filtros de categoría y dificultad, con lo que `CourseOfferability` necesita y las tres cuentas del alumno, y **descarta los que no se ofrecen**.
5. El sistema lee en una sentencia las llaves —membresías y servicios— de los que quedan, decide `accessible` por curso y, **si se pidió `onlyAccessible`, descarta los que no abren nada**.
6. El sistema resuelve las categorías vivas de los que quedan y la lista de categorías vivas.
7. Devuelve `200`.

## 9. Flujos alternativos

### FA-001 — Sin membresía vigente

**Comportamiento:** `currentMembership` nula; `accessible` es verdadero solo en los cursos **sin llaves** y en los que abre un servicio vigente. Toda persona tiene una membresía (`RN-SP-018`), pero puede estar **vencida**, y el puerto devuelve vacío en ese caso a propósito.

### FA-002 — `onlyAccessible=true`

**Comportamiento:** `courses` trae solo los que tienen `accessible: true` o `openLessonCount > 0`; `categories` sigue completo. Un curso cerrado con una demostración aparece, con `accessible: false` y `openLessonCount` mayor que cero.

### FA-003 — Filtro por una categoría retirada o inexistente

**Comportamiento:** `200` con `courses` **vacío** y `categories` completo.

### FA-004 — Nada que ofrecer

**Comportamiento:** `200` con `courses` vacío y las categorías que haya. No es un error.

## 10. Excepciones

Ninguna propia.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | `categoryId` con formato válido y `onlyAccessible` booleano | El parámetro indicado no tiene un formato válido. |
| `VAL-002` | `difficulty` dentro del dominio | La dificultad debe ser PRINCIPIANTE, INTERMEDIO o AVANZADO. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-186` | **Solo lo ofrecido**: el sistema deja fuera del catálogo un curso por **cada uno** de los **cuatro** motivos de `RN-AC-015` —retirado, inactivo, sin una de las dos descripciones, sin módulo ofrecible—; **un curso sin llaves se enseña y es `accessible` para todos** |
| `CA-AC-187` | Lo ofrecido aparece **para todo alumno con `courses:learn`**: el de una membresía de la lista lo ve con `accessible: true` y `currentMembership` con su código; el de otra, con `false`; **sin vigente o con una vencida**, `false` y `currentMembership` nula |
| `CA-AC-188` | **La lista es exacta** (`RN-AC-012`): un curso abierto a `ORO` responde `accessible: false` a `PLATINO` |
| `CA-AC-189` | `categories` trae **todas las vivas**, en su orden, con color, icono, portada y orden, **incluidas las vacías** y **sin aplicar filtros**; una categoría **retirada** no aparece ni en los cajones ni en `categories` de ningún curso, y sus cursos se siguen ofreciendo (**hace real `CA-AC-032`**) |
| `CA-AC-190` | `categoryId` reduce la lista a los clasificados en ella; una categoría retirada o inexistente devuelve `courses` vacío con `200`; `difficulty` reduce la lista; una dificultad fuera del dominio, un identificador mal formado o un `onlyAccessible` no booleano responden `400` |
| `CA-AC-191` | Los cursos salen por `displayOrder` y después por identificador; `totalDurationSeconds`, `lessonCount` y `openLessonCount` cuentan **solo las lecciones ofrecibles de los módulos ofrecibles**: una lección inactiva, vacía o retirada y un módulo inactivo **no suman** |
| `CA-AC-192` | La lectura cuesta **cuatro sentencias fijas** —cursos con instructor y cuentas, llaves de esos cursos, categorías de esos cursos, categorías vivas— **más las de los dos puertos**, con cero cursos y con muchos; con cero cursos la segunda y la tercera **no se ejecutan** |
| `CA-AC-193` | Sin `courses:learn` responde `403` **aunque el actor porte `courses:read`**; y `GET /courses/available` **no cae en `GET /courses/{id}`** |
| `CA-AC-237` | **Un servicio vigente abre** (`RN-AC-020`): un curso con un servicio en su lista es `accessible` para quien lo tiene vigente, y **no** para quien lo tuvo —vencido o cerrado— ni para quien lo tiene con inicio futuro |
| `CA-AC-238` | **`onlyAccessible=true`** deja los cursos `accessible` y los cerrados con alguna lección abierta ofrecible, y quita los cerrados sin ninguna; una lección abierta **inactiva o vacía no cuenta**; sin el filtro salen todos |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Un curso de la lista se retira entre dos peticiones | Desaparece en la siguiente: la ofrecibilidad se calcula en cada lectura |
| Instructor retirado de `SP` | Se enseña con su nombre actual, como en administración (`RN-AC-006`) |
| Un alumno cuyo rol perdió `courses:learn` | `403` desde la siguiente petición |
| Un curso ofrecido con cien lecciones | Suma y cuenta en la sentencia, no en Java |
| El servicio de la lista fue retirado en `PM` | Sigue abriendo a quien lo tiene vigente (`RN-AC-020`) |
| Un curso abierto entero y con lecciones abiertas | `accessible: true` y `openLessonCount` con su cuenta: la cuenta no depende del acceso |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El aula calcula la ofrecibilidad en SQL o con `CourseOfferability`? | **Con `CourseOfferability`**, sobre las mismas cuentas que el listado de administración trae por fila. Escribir la regla otra vez en un `WHERE` daría **dos definiciones** de lo mismo |
| 2 | ¿Se pagina? | **No.** Como la oferta de `PM` |
| 3 | ¿«Los cursos que puedo ver» esconden lo cerrado siempre? | **No** (`ac.md` §5.2.13): por omisión la vitrina; `onlyAccessible` para «mis cursos». Esconder siempre rompía `RN-AC-013` |
| 4 | ¿`onlyAccessible` se filtra en SQL? | **En Java**, después de `StudentAccess`: `accessible` depende de las llaves del alumno, que vienen de dos puertos, y escribirlo en el `WHERE` sería una segunda definición del acceso |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. `RF-PM-007` para cursos: actor del token, permiso de vista, solo lo ofrecido — **con `CourseOfferability` y no con una segunda definición en SQL** (§14.1), probado por concordancia con el detalle de administración. Todos los ofrecidos para todo alumno, con `accessible` como única marca y `currentMembership` presente y nula; las categorías vivas como cajones, sin filtro y con las vacías; filtros por categoría y dificultad sin paginar. **Hace real `CA-AC-032`** de `RF-AC-005`. Nace con los **cinco motivos** de `RN-AC-015` del mismo día. | Responsable técnico |
| 0.2.0 | 25-09-2026 | **Enmienda declarada por `RF-AC-037`** (Art. I.7, `RN-AC-013` y `RN-AC-020`; `ac.md` v0.12.0 §5.2.8): `accessible` es verdadero también si quien pregunta **tiene vigente uno de los servicios del curso**. Necesita de `SP` una interfaz que no existe —los productos vigentes de una persona, en **una** llamada para el catálogo entero—, y la pide este requerimiento al construirse (`ac.md` §3). **El cuerpo de esta spec se reescribe al construirla**; hasta entonces, donde dice «membresía» como llave se lee «membresía o servicio». | Responsable técnico |
| 0.3.0 | 25-09-2026 | **Enmienda por decisión del responsable del proyecto** (`ac.md` §5.2.10, `RN-AC-017`): **la duración de la lección se guarda en segundos**, y las sumas del módulo y del curso también: `durationSeconds` y `totalDurationSeconds` sustituyen a `durationMinutes` y `totalDurationMinutes` en el cuerpo de esta spec. Las filas anteriores de esta tabla conservan el nombre que tenía el campo en su fecha. | Responsable técnico |
| 0.4.0 | 25-09-2026 | **Enmienda por decisión del responsable del proyecto** (`ac.md` §5.2.12, `RN-AC-015` reescrita): **un curso sin membresías ni servicios es de todos** y las llaves dejan de ser motivo de la ofrecibilidad, que queda en **cuatro** —retirado, inactivo, sin descripción, sin módulo ofrecible—. **En el aula, un curso sin llaves es `accessible` para todo alumno con sesión** y sus lecciones cerradas se abren a todos. El cuerpo de esta spec se reescribe al construirla. | Responsable técnico |
| 1.0.0 | 26-09-2026 | **Cuerpo reescrito al construir**, con las tres enmiendas del 25-09-2026 dentro —servicio como llave, curso sin llaves de todos, segundos— y **dos decisiones del responsable del proyecto de hoy** (`ac.md` v0.20.0 §5.2.13): el filtro **`onlyAccessible`** —solo lo que abre algo: el curso entero o una lección abierta— y **`openLessonCount`** por curso (`CA-AC-238`). Nace `CA-AC-237` —el servicio vigente abre—. Las llaves de los cursos se leen **en una sentencia** —membresías y servicios juntos—, de modo que la cuenta sigue en **cuatro sentencias** más los dos puertos (`CA-AC-192`). El permiso se queda en `courses:learn`, que desde hoy gobierna **solo** el catálogo. `CA-AC-194` —la concordancia exhaustiva con el detalle de administración— **se retira**: el aula decide con el mismo `CourseOfferability` sobre las mismas cuentas del listado, y `CA-AC-186` cubre cada motivo. | Responsable técnico |
