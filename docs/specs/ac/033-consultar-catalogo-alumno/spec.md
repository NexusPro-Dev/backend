# SPEC — `RF-AC-033` Consultar el catálogo de cursos como alumno

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-033` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que el alumno vea **qué se enseña** y **qué le abre su nivel**: todos los cursos que se ofrecen, en su orden, cada uno marcado con si su membresía vigente lo abre.

## 2. Contexto

Es la primera de las tres vistas del aula (`ac.md` §2) y la que aplica entera la decisión de §1.4: **el catálogo enseña todos los cursos que se ofrecen, tenga o no el alumno la membresía que los abre** (`RN-AC-013`). La lista de visibilidad no esconde el curso: lo marca. Un alumno de `BRONCE` ve el curso de `ORO` con `accessible: false`, y es esa marca —y no la ausencia— lo que le dice que hay algo a lo que subir.

Es `RF-PM-007` para cursos, y hereda de la oferta de `PM` lo que la define: **el actor sale del token** y no hay forma de preguntar por otra persona; **exige un permiso de vista** —`courses:learn`— y no el de administración, que abre lo inactivo, lo retirado y lo que no se ofrece; **solo lo ofrecido**, con la ofrecibilidad decidida por **el mismo objeto** que administración ve en el detalle (`CourseOfferability`, `RF-AC-008`) y no por una segunda definición escrita en la consulta del aula. Lo que no hereda es el «sin parámetros»: el catálogo se **filtra por categoría y por dificultad**, porque son decenas de cursos y el alumno elige por cajón (`ac.md` §6.2, ficha). **Sin paginar**, como la oferta.

**Trae además las categorías vivas**, con su color, icono, portada y orden, para que el frontend pinte los cajones sin otra petición — incluidas las **vacías**, porque una categoría sin cursos ofrecidos «simplemente sale vacía» (`RN-AC-008`) y es el frontend quien decide si la enseña. Y trae **la membresía vigente** de quien pregunta, presente y nula, para que la pantalla diga desde qué nivel mira.

Es el requerimiento que **hace real `CA-AC-032`** de `RF-AC-005` —una categoría retirada no se enseña en el aula y sus cursos siguen ofreciéndose— y **la parte del aula de `CA-AC-138`** de `RF-AC-020`: con una membresía, el curso aparece; sin ninguna, no.

## 3. Actores

| Actor | Papel |
|---|---|
| Alumno | Consulta el catálogo que se le ofrece |

## 4. Alcance

### 4.1 Incluye

- Devolver **todos los cursos que se ofrecen** (`RN-AC-015`, con sus cinco motivos), en su orden global (`RN-AC-002`), sin paginar, filtrables por **categoría** y por **dificultad**.
- Por curso: portada, título, instructor resuelto, dificultad, descripción corta, orden, categorías vivas, **duración total** y **cuántas lecciones** —contando solo lo que el alumno va a ver—, y **`accessible`**.
- **Las categorías vivas**, con color, icono, portada y orden, incluidas las vacías.
- **La membresía vigente** de quien pregunta, presente y nula.

### 4.2 No incluye

- **Lo que no se ofrece.** Ni lo inactivo, ni lo retirado, ni lo que está activo y le falta algo. Para el alumno, no existe.
- **Descripción larga, video de introducción, recomendaciones, membresías que lo abren y el árbol.** Son del detalle (`RF-AC-034`); aquí viajaría todo por cada curso de la lista.
- **Cuántos cursos tiene cada categoría.** El frontend lo cuenta de la lista que ya tiene, y con el filtro de categoría activo la cuenta sería la de otra pregunta.
- **Paginación y búsqueda por texto.** Es una lista corta que se lee entera, como la oferta de `PM`; buscar es del frontend sobre lo que ya tiene.
- **Un curso «suyo» que no se ofrece.** Un alumno de `ORO` no ve un curso de `ORO` inactivo: `accessible` es una marca sobre lo ofrecido, no un segundo filtro.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-002` | Los cursos salen en su orden global, con desempate por identificador; las categorías, en el suyo | `requirements/ac.md` §5.1 |
| `RN-AC-004` | `coverImageUrl` presente y nula, en el curso y en la categoría | `requirements/ac.md` §5.1 |
| `RN-AC-010` | Un curso sin categoría se ofrece igual; las categorías retiradas no se enseñan | `requirements/ac.md` §5.1 |
| `RN-AC-012` | La lista es explícita: `accessible` es «la vigente está en la lista», sin mirar niveles | `requirements/ac.md` §5.1 |
| `RN-AC-013` | Todo curso ofrecido aparece para todo alumno; la lista marca, no esconde | `requirements/ac.md` §5.1 |
| `RN-AC-015` | Solo lo ofrecido, con los cinco motivos y el mismo objeto que administración | `requirements/ac.md` §5.1 |
| `RN-AC-017` | La duración es la suma de las lecciones que el alumno verá | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| `categoryId` | No | Solo los clasificados en esa categoría | UUID. Una categoría **retirada o inexistente** devuelve la lista **vacía**, no `404`: es un filtro, no una ruta |
| `difficulty` | No | `PRINCIPIANTE`, `INTERMEDIO` o `AVANZADO` | Fuera del dominio, `400` |

**El actor sale del token.** Ni paginación ni orden: la lista es corta y el orden es el de `RN-AC-002`.

### 6.2 Salida

`200` con:

- `currentMembership { id, code, name, color }`, **presente y nula** cuando quien pregunta no tiene membresía vigente.
- `categories [{ id, name, color, icon, displayOrder, coverImageUrl }]`: **todas las vivas**, en su orden, **sin aplicar el filtro** — son los cajones, y el filtro es lo que el alumno eligió dentro de ellos.
- `courses [{ id, title, instructor { id, username, fullName }, difficulty, shortDescription, displayOrder, coverImageUrl, categories [{ id, name, color, icon }], totalDurationMinutes, lessonCount, accessible }]`: los ofrecidos que pasan el filtro, en su orden.

**`accessible` es una sola pregunta**: ¿la membresía vigente de quien pregunta está en la lista del curso? Sin vigente, falso en todos. La decide **un solo objeto** para las tres vistas del aula (`StudentAccess`, `plan.md` §3), y en la lección añade «o está abierta». **`totalDurationMinutes` y `lessonCount` cuentan lo que el alumno verá**: las lecciones ofrecibles —activas, vivas, con contenido— de los módulos ofrecibles. Una lección inactiva o un módulo inactivo no suman, aunque administración los cuente en su detalle sobre lo vivo (`RF-AC-010`).

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:learn`. **Postcondiciones:** ninguna.

## 8. Flujo principal

1. Llega la petición, con los filtros si los hay.
2. El sistema valida la forma de los filtros (`VAL-001`, `VAL-002`).
3. El sistema pregunta a `SP` la membresía vigente de quien llama (`CurrentMembershipLookup`).
4. El sistema lee los cursos vivos y `ACTIVOS` que pasan el filtro con lo que `CourseOfferability` necesita, y **descarta los que no se ofrecen**.
5. El sistema resuelve las categorías vivas y las membresías de los cursos que quedan, decide `accessible` por curso con la vigente, y lee la lista de categorías vivas.
6. Devuelve `200`.

## 9. Flujos alternativos

### FA-001 — Sin membresía vigente

**Comportamiento:** `currentMembership` nula y **`accessible: false` en todos**; el catálogo es el mismo. Toda persona tiene una membresía (`RN-SP-018`), pero puede estar **vencida**, y el puerto devuelve vacío en ese caso a propósito (`CurrentMembershipLookup`, Javadoc): el aula lo trata como «hoy no tiene nivel», y lo que le queda son las demostraciones (`RF-AC-035`).

### FA-002 — Filtro por una categoría retirada o inexistente

**Comportamiento:** `200` con `courses` **vacío** y `categories` completo. El filtro no es una ruta y la categoría no aparece en los cajones; un `404` diría que la pantalla se rompió.

### FA-003 — Nada que ofrecer

**Comportamiento:** `200` con `courses` vacío y las categorías que haya, vacías. No es un error: es un catálogo recién creado.

## 10. Excepciones

Ninguna propia.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | `categoryId` con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | `difficulty` dentro del dominio | La dificultad indicada no existe. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-186` | **Solo lo ofrecido**: el sistema deja fuera del catálogo un curso por **cada uno** de los cinco motivos de `RN-AC-015` —retirado, inactivo, sin una de las dos descripciones, sin membresías, sin módulo activo con lección activa con contenido—, y **un módulo activo cuya única lección activa está vacía no ofrece el curso** |
| `CA-AC-187` | Lo ofrecido aparece **para todo alumno con `courses:learn`**: el de una membresía de la lista lo ve con `accessible: true` y `currentMembership` con su código; el de otra, con `false`; **sin vigente o con una vencida**, `false` y `currentMembership` nula |
| `CA-AC-188` | **La lista es exacta** (`RN-AC-012`): un curso abierto a `ORO` responde `accessible: false` a `PLATINO` |
| `CA-AC-189` | `categories` trae **todas las vivas**, en su orden, con color, icono, portada y orden, **incluidas las vacías** y **sin aplicar el filtro**; una categoría **retirada** no aparece ni en los cajones ni en `categories` de ningún curso, y sus cursos se siguen ofreciendo (**hace real `CA-AC-032`**) |
| `CA-AC-190` | `categoryId` reduce la lista a los clasificados en ella; una categoría retirada o inexistente devuelve `courses` vacío con `200`; `difficulty` reduce la lista; una dificultad fuera del dominio o un identificador mal formado responden `400` |
| `CA-AC-191` | Los cursos salen por `displayOrder` y después por identificador; `totalDurationMinutes` y `lessonCount` cuentan **solo las lecciones ofrecibles de los módulos ofrecibles**: una lección inactiva, vacía o retirada y un módulo inactivo **no suman** |
| `CA-AC-192` | La lectura cuesta **cuatro sentencias fijas** —cursos con instructor y cuentas, categorías de esos cursos, membresías de esos cursos, categorías vivas— **más la del puerto**, con cero cursos y con muchos; con cero cursos la segunda y la tercera **no se ejecutan** |
| `CA-AC-193` | Sin `courses:learn` responde `403` **aunque el actor porte `courses:read`**; y `GET /courses/available` **no cae en `GET /courses/{id}`** |
| `CA-AC-194` | **Concordancia**: para todo curso y toda combinación de estado, descripciones, membresías, módulos y lecciones, **está en el catálogo si y solo si el detalle de administración (`RF-AC-010`) dice `offerable: true`** |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Un curso de la lista se retira entre dos peticiones | Desaparece en la siguiente: la ofrecibilidad se calcula en cada lectura |
| Instructor retirado de `SP` | Se enseña con su nombre actual, como en administración (`RN-AC-006`) |
| Un alumno cuyo rol perdió `courses:learn` | `403` desde la siguiente petición; el aula no guarda nada suyo |
| Un curso ofrecido con **cien** lecciones | Suma y cuenta en la sentencia, no en Java: la lista no lee lecciones |
| Dos alumnos de la misma membresía | El mismo catálogo con las mismas marcas: no hay nada por persona salvo la vigente |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El aula calcula la ofrecibilidad en SQL o con `CourseOfferability`? | **Con `CourseOfferability`**, sobre las mismas cuentas que el listado de administración trae por fila (`RF-AC-009`, `RF-AC-016` §3). Escribir la regla otra vez en un `WHERE` daría **dos definiciones** de lo mismo, y la que se olvidaría de enmendar sería la del aula. Se lee lo vivo y activo con sus cuentas y se filtra en Java; son decenas de filas. `CA-AC-194` lo prueba desde fuera |
| 2 | ¿Se pagina? | **No.** Como la oferta de `PM`: es una lista que se lee entera y se filtra por cajón. El día que sean cientos, se pagina con la envoltura del sistema |
| 3 | ¿Las categorías traen cuántos cursos tienen? | **No** (§4.2): con el filtro activo la cuenta respondería otra pregunta; el frontend cuenta de la lista |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. `RF-PM-007` para cursos: actor del token, permiso de vista, solo lo ofrecido — **con `CourseOfferability` y no con una segunda definición en SQL** (§14.1), probado por concordancia con el detalle de administración. Todos los ofrecidos para todo alumno, con `accessible` como única marca y `currentMembership` presente y nula; las categorías vivas como cajones, sin filtro y con las vacías; filtros por categoría y dificultad sin paginar. **Hace real `CA-AC-032`** de `RF-AC-005`. Nace con los **cinco motivos** de `RN-AC-015` del mismo día. | Responsable técnico |
