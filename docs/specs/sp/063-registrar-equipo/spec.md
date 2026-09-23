# SPEC — `RF-SP-063` Registrar equipo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-063` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 22-09-2026 |

---

## 1. Objetivo

Crear el **cajón en el que se organiza la cúspide de la fuerza comercial**: un equipo con nombre y descripción al que después se asignan los managers, para que la empresa pueda decir «esta parte de la red es este equipo» sin que eso cambie quién manda sobre quién.

## 2. Contexto

**Es el primer requerimiento del submódulo Equipos**, y por eso carga con lo que todo submódulo nuevo paga una vez: crea las dos tablas —`teams` y `team_members`— y siembra los ocho permisos `teams:`, con su asociación a `SUPERADMIN` y `ADMIN` en la misma migración. Es la misma forma con la que `RF-AC-001` estrenó Academia, y se sigue a propósito: el submódulo entero entra en una migración de esquema y una de catálogo, y ningún requerimiento posterior toca el esquema.

**Lo pidió el responsable del proyecto el 21-09-2026**: «un CRUD de equipos, sirve para organizar el máximo rango de vendedores». Ese mismo día quedó fijado qué es un equipo y qué no, y está escrito en [`requirements/sp.md` §2 y §10.20](../../../requirements/sp.md): reúne **managers** —quienes portan el rol vendedor de mayor rango, los que `RN-SP-019` exime de tener superior— y con cada uno entra, por su cadena de mando en `user_supervisors`, toda la red que cuelga de él. **Un equipo agrupa; no manda.** Debajo de un manager la organización ya está dicha, y lo que faltaba era cómo se agrupan quienes no tienen a nadie encima.

**No es una tercera jerarquía.** `roles.parent_role_id` acota privilegios, `user_supervisors` dice quién está a cargo de quién, y esta tabla **particiona las raíces** de ese bosque. No se anida —no hay equipos dentro de equipos— porque encima de la cúspide no hay nada que ordenar, y **no concede acceso a ningún dato**: D-22 sigue abierta y el alcance de las ventas que `RF-MV-015` resolvió recorre `user_supervisors`, no los equipos.

**El alta no toca a nadie.** Un equipo nace **vacío y activo**; los miembros se asignan después (`RF-SP-069`), porque asignar exige comprobar el rol de cada persona contra `user_roles` y cerrar la pertenencia anterior, y mezclarlo con el alta haría que un nombre repetido y un manager inválido salieran por el mismo `409` sin que el administrador supiera cuál de las dos cosas falló.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador con `teams:create` | Registra el equipo |

## 4. Alcance

### 4.1 Incluye

- Registrar un equipo con **nombre** obligatorio y **descripción** opcional; nace `ACTIVO` y vacío.
- Crear `teams` y `team_members` con sus restricciones e índices (`requirements/sp.md` §10.8, §10.20 y §10.21).
- Sembrar los ocho permisos `teams:` y asociarlos a `SUPERADMIN` y `ADMIN`.
- Devolver el equipo recién creado **en la forma del detalle** (`RF-SP-065`): sin miembros y con el recuento en cero.

### 4.2 No incluye

- **Los miembros.** Se asignan con `RF-SP-069` y se retiran con `RF-SP-070`.
- **Un código.** El equipo no lo tiene, y §14.1 dice por qué.
- **Un responsable del equipo.** Un equipo no tiene jefe: lo que hay encima de un manager es administración, no otro manager (`RN-SP-020`).
- **Un país ni ningún otro recorte territorial.** §14.2.
- **Anidar equipos.** No hay `parent_team_id` ni lo habrá mientras la cúspide sea la cúspide.
- **Conceder alcance de datos.** Ninguna consulta se autoriza por pertenecer a un equipo (D-22).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-050` | El nombre de un equipo es único entre los no eliminados, sin distinguir mayúsculas ni acentos | `requirements/sp.md` §5.1 |
| `RN-SP-053` | Un equipo `INACTIVO` no recibe miembros; los que tiene los conserva — aquí solo fija que **nace `ACTIVO`** | `requirements/sp.md` §5.1 |
| `RN-SEG-014` | Un permiso gobierna una operación o ninguna, nunca dos | `security.md` §4.3 |
| `RN-SEG-015` | Autenticarse no autoriza nada: toda ruta con token exige permiso | `security.md` §4.3 |
| `RN-SEG-003` | Ningún rol declara un permiso que su padre no tenga | `security.md` §4.2 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Nombre (`name`) | Sí | Cómo se llama el equipo | Hasta **100** caracteres tras recortar; **único entre los no eliminados**, sin distinguir mayúsculas ni acentos (`RN-SP-050`) |
| Descripción (`description`) | No | Qué agrupa, para quien lo lea dentro de un año | Hasta **500** caracteres, el mismo tope que la de un rol; recortada; **de solo espacios se guarda nula** |

**Ni estado, ni miembros, ni código.** Un `status`, un `members` o un `code` en el cuerpo es un campo desconocido y se rechaza como tal (`VAL-003`): el estado nace `ACTIVO` y se cambia con `RF-SP-067`, los miembros entran con `RF-SP-069`, y el código no existe.

### 6.2 Salida

`201` con el equipo en la **misma forma del detalle** (`RF-SP-065`): identificador, nombre, descripción —presente y nula si no vino—, estado `ACTIVO`, `memberCount` en **cero**, `members` vacío y las dos fechas de auditoría iguales.

**Se devuelve la forma completa aunque esté vacía**, para que el frontend trate «acabo de crearlo» y «lo abrí» igual, como hace toda alta del sistema (`RF-AC-001` §6.2).

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado con `teams:create`; el nombre no lo usa otro equipo no eliminado.

**Postcondiciones:** existe la fila en `teams` con `status = 'ACTIVO'` y `deleted_at` nulo; ninguna fila en `team_members`; `audit_change_log` tiene una fila `CREATE` de `teams` en la misma transacción. **Ningún evento de seguridad**: un equipo no concede nada, y por eso su alta no es un hecho de autorización.

## 8. Flujo principal

1. Llega la petición con el nombre y, si viene, la descripción.
2. El sistema valida la forma de ambos, **juntos** (§11).
3. El sistema recorta el nombre y la descripción, y guarda nula una descripción de solo espacios.
4. El sistema comprueba que el nombre no lo usa otro equipo **no eliminado** (`EX-001`).
5. El sistema inserta el equipo y registra la creación en la auditoría, en la misma transacción.
6. Devuelve `201` con el equipo vacío.

El paso 4 tiene su red en el esquema —`uq_teams_name`, funcional y parcial—: la carrera entre dos altas simultáneas la muerde el índice y el repositorio la traduce al **mismo `409`** que la comprobación previa, como en `RF-AC-001` y `RF-PM-001`.

## 9. Flujos alternativos

### FA-001 — Sin descripción

**Comportamiento:** se registra igual, y la respuesta trae `description` **presente y nula**. Un equipo se identifica por su nombre; la descripción es para quien lo herede.

### FA-002 — El nombre difiere de otro solo en mayúsculas o acentos

**Comportamiento:** **se rechaza** (`EX-001`). «Equipo Norte» y «equipo norte» serían dos opciones indistinguibles en cualquier selector, que es el motivo por el que `RN-SP-050` compara sin acentos y sin caja.

### FA-003 — El nombre lo usa un equipo **eliminado**

**Comportamiento:** se admite. La eliminación es lógica y **libera el nombre** (`RN-SP-050`): no hay código que conservar, y el historial de `team_members` sigue apuntando al equipo viejo por identificador, no por nombre.

### FA-004 — El nombre coincide con el de un **rol** (`MANAGER`, por ejemplo)

**Comportamiento:** se admite. Son entidades distintas y la unicidad es por tabla. Que alguien llame «Manager» a un equipo es una mala idea de negocio, no un error del sistema.

## 10. Excepciones

### EX-001 — El nombre ya lo usa un equipo no eliminado

**Condición:** existe un equipo con `deleted_at` nulo cuyo nombre coincide sin distinguir mayúsculas ni acentos.
**Respuesta del sistema:** `409` — *«Ya existe un equipo con ese nombre.»*

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Nombre presente y de hasta 100 caracteres tras recortar | El nombre es obligatorio y no puede superar los 100 caracteres. |
| `VAL-002` | Descripción, si viene, de hasta 500 caracteres | La descripción no puede exceder 500 caracteres. |
| `VAL-003` | Ningún campo desconocido — en particular, ni `status`, ni `members`, ni `code` | El cuerpo de la petición contiene campos no admitidos. |

`VAL-001` y `VAL-002` se devuelven **juntas**: quien se equivocó en dos corrige una vez. Ninguna compara dos campos, de modo que no hay una tanda aparte.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-731` | El sistema registra el equipo con `201`, en la forma del detalle: estado `ACTIVO`, `memberCount` en **cero**, `members` vacío, `description` **presente y nula** si no vino, y `createdAt` igual a `updatedAt` |
| `CA-SP-732` | El sistema rechaza con `409` un nombre que ya usa un equipo no eliminado, **sin distinguir mayúsculas ni acentos**, y **admite** el de uno eliminado |
| `CA-SP-733` | El sistema rechaza con `400` el nombre ausente, vacío o de más de 100 caracteres y la descripción de más de 500, **juntos**; una descripción de solo espacios se guarda **nula** |
| `CA-SP-734` | El sistema rechaza con `400` un cuerpo que traiga `status`, `members` o `code` |
| `CA-SP-735` | El sistema registra una fila `CREATE` en `audit_change_log` con el actor y en la misma transacción, y **ninguna** en `audit_security_log` |
| `CA-SP-736` | Dos altas simultáneas con el mismo nombre dejan **una** fila y un `409`, no un `500` |
| `CA-SP-737` | `V33` crea `teams` y `team_members` con sus diez restricciones e índices, y `uq_team_members_vigente` impide dos pertenencias vigentes de la misma persona aunque se inserten a mano |
| `CA-SP-738` | `V34` siembra los ocho `teams:` con identificador literal, asociados a `SUPERADMIN` y `ADMIN` y **a ningún otro rol**; el catálogo cuenta **ciento treinta y tres**, `SUPERADMIN` porta 133 y `ADMIN` 127 |
| `CA-SP-739` | Sin `teams:create` el alta responde `403` **aunque el actor porte los otros siete `teams:`**, y `EndpointPermissionsIT` recibe `POST /teams` con su código |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Dos altas simultáneas con el mismo nombre | Una queda y la otra recibe `409` por el índice parcial, no `500` (`CA-SP-736`) |
| Nombre de un solo carácter | Se admite: la regla acota el máximo y la obligatoriedad, no un mínimo con criterio estético |
| Nombre con emoji o signos | Se admite: `varchar(100)` y el `CHECK` de no vacío es todo lo que el esquema opina. Qué se escribe en él es del negocio |
| Nombre igual al de un equipo eliminado **y** otro vivo | Imposible: el vivo hace fallar el alta antes de mirar al eliminado (`EX-001`) |
| El primer equipo del sistema | Nada especial: no hay equipo raíz ni equipo por omisión, y el sistema funciona con cero equipos — nadie deja de tener superior por no estar en ninguno |
| Un administrador crea cien equipos vacíos | Se admiten: un equipo vacío no rompe nada y `RF-SP-064` los enseña con `memberCount` en cero para que se vea |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El equipo tiene **código**, como un rol o una membresía? | **No** (21-09-2026, responsable del proyecto). El código de un rol existe porque el código lo referencia —`hasAuthority`, `SellerRoleCatalog`— y el de una membresía porque `PM` la cita desde un producto; **nada del sistema referencia a un equipo por un nombre estable**: se llega a él por su identificador. Un código que nadie usa es una columna más que mantener única, y la unicidad que sí hace falta es la del nombre (`RN-SP-050`) |
| 2 | ¿El equipo pertenece a un **país** o a un territorio? | **No** (21-09-2026, responsable del proyecto). La red comercial no se organiza hoy por territorio, y una clave foránea a `countries` obligaría a decidir qué pasa con el equipo cuyo manager está en otro país. Si algún día hace falta, es una columna nulable y una enmienda |
| 3 | ¿Puede un equipo tener **jefe**? | **No.** Quien está por encima de un manager es administración, no otro manager (`RN-SP-020`): un `leaderId` sería o bien un manager del propio equipo —y entonces manda sobre sus pares, que `user_supervisors` no admite— o bien alguien de fuera, que es exactamente lo que `ADMIN` ya es |
| 4 | ¿Se crea con sus miembros dentro, en una sola petición? | **No.** Asignar tiene reglas propias (`RN-SP-051`, `RN-SP-052`) y sus dos errores —nombre repetido y persona que no es manager— saldrían por el mismo `409` sin decir cuál falló. El alta crea el cajón; `RF-SP-069` lo llena |
| 5 | ¿Nace `ACTIVO` o hace falta activarlo? | **Nace `ACTIVO`.** Un equipo recién creado se usa para asignar, que es justo lo que `RN-SP-053` prohíbe sobre uno `INACTIVO`; nacer apagado obligaría a dos peticiones para el caso normal |
| 6 | ¿La descripción es obligatoria? | **No.** Un equipo se identifica por su nombre; exigir una descripción produce descripciones que repiten el nombre |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 22-09-2026 | Redacción inicial, al día siguiente de que el responsable del proyecto pidiera el CRUD de equipos y fijara sus cuatro decisiones. Hereda de `RF-AC-001` la forma del alta —el primer requerimiento del submódulo crea las tablas y siembra los permisos, y el alta devuelve la forma completa del detalle aunque esté vacía— y de `RF-SP-001` el tope de 500 de la descripción. Decide: sin código, sin país, sin jefe, sin miembros en el alta, nace `ACTIVO`. Nueve criterios, `CA-SP-731` a `CA-SP-739`. | Responsable del proyecto |
