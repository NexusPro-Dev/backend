# PLAN — `RF-SP-060` Un permiso por operación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-060` |
| Especificación | [`spec.md`](spec.md), aprobada el 19-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 19-09-2026 |

---

## 1. Enfoque

**Una migración de datos, veintiún anotaciones y una prueba que lo vigila.** No hay dominio nuevo ni servicio nuevo: el reparto de `spec.md` §6.2 es (a) cincuenta y una filas en `permissions`, (b) las filas de `role_permissions` que llevan cada hijo a quien portaba el padre, (c) el cambio de la cadena dentro de `@PreAuthorize` en cada operación que cambia de permiso, y (d) una afirmación nueva en `EndpointPermissionsIT`: **la función operación → permiso es inyectiva**.

**Lo que carga el plan no es el código sino el orden.** Sesenta y ocho requerimientos de cuatro módulos nombran el permiso en su spec y en su plan, y por el Art. I.7 esas tripletas se corrigen **antes** de tocar el controlador. Y `AC` se reparte **después** de que su bloque 4 esté construido con `courses:update` (spec §14, pregunta 4), de modo que el trabajo va en tres tramos:

| Tramo | Qué | Cuándo |
|---|---|---|
| 1 | Los documentos transversales y la tripleta | 19-09-2026, este mismo pase |
| 2 | Las enmiendas a `pm.md`, `cm.md` y a las sesenta y ocho tripletas; después `V28`, los controladores de `SP`, `PM` y `CM`, sus pruebas y la prueba de inyectividad | Cuando el responsable del proyecto apruebe este plan |
| 3 | `ac.md` §7 y los controladores de `AC` | Después del bloque 4 de `AC`; `V28` ya siembra sus dieciocho |

**`V28` siembra los ciento once desde el tramo 2, `AC` incluido**, aunque sus controladores sigan con `courses:update` hasta el tramo 3: sembrar un permiso sin ruta que lo exija no rompe nada —`products:hotlink` y `movements:` nacieron así— y evita partir la migración. Entre el tramo 2 y el 3, `EndpointPermissionsIT` conoce esa situación y **la declara con fecha** en lugar de fallar: el reparto de `AC` es un hecho pendiente, no una excepción.

## 2. Cambios de esquema

**Ninguno.** `V28__sp_un_permiso_por_operacion.sql` es una migración de **datos**: `permissions` y `role_permissions` no cambian de forma.

### 2.1 Los identificadores

Literales, como todos (Art. V.11; `V8`, cabecera). Forma: `01a0b6f6-7400-7NNN-9c4f-<serie><secuencia>`:

- `01a0b6f67400` es la marca de tiempo v7 del 19-09-2026, como `01a0b3c71000` lo fue del 18-09-2026 en `V22`.
- `7NNN` numera los cincuenta y uno del `7001` al `7033`, en el orden de `spec.md` §6.2.
- La serie es la del módulo, y **continúa donde cada una quedó**: `SP` `5e7ad0` desde `000019` (`users:assign-supervisor` es `000018`), `broker-accounts` `5e7ada` desde `000003`, `PM` `5e7ad5` desde `000012`, `CM` `5e7ad6` desde `000006` —la comparte con `document-types:read`, que fue `000005`—, `AC` `5e7adc` desde `000011`.

`PermissionsSeedIT` comprueba que los ciento once identificadores son distintos y que ningún literal de `V8`, `V19` y `V22` cambió.

### 2.2 El reparto en `role_permissions`

Una sola sentencia sobre una tabla de parejas (padre, hijo) escrita en la migración:

```sql
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, hijo.id
  FROM (VALUES ('roles:read', 'roles:list'),
               ('roles:update', 'roles:change-status'),
               -- … las cincuenta y una parejas de spec.md §6.2
       ) AS reparto (padre, hijo)
  JOIN permissions p_padre ON p_padre.code = reparto.padre
  JOIN permissions p_hijo  ON p_hijo.code  = reparto.hijo
  JOIN role_permissions rp ON rp.permission_id = p_padre.id
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;
```

**Por pareja (rol, padre) y no por rol de sistema**: así `SUPERADMIN` y `ADMIN` reciben lo suyo por la misma vía que cualquier rol creado a mano, y no hay una lista de roles que mantener. `ON CONFLICT … DO NOTHING` es lo que permite reejecutar el reparto (spec §13) sin que la clave primaria lo impida.

**Sin auditoría**, como `V8`, `V19` y `V22`: el catálogo es datos, y las filas de `role_permissions` que nacen aquí no las concedió nadie — las tenía todo el mundo bajo otro nombre. Auditarlas como asignaciones de `RF-SP-005` diría que alguien las asignó.

### 2.3 Los veintiún que se estrechan

`UPDATE permissions SET name = …, description = … WHERE code = …`, uno por código, con el texto de lo que queda. `roles:read` pasa de «Consultar roles» a «Consultar el detalle de un rol»; `roles:update` deja de decir «asignar o retirar permisos». `CA-SP-695` lo comprueba leyendo las descripciones: **ninguna nombra lo que perdió**.

### 2.4 Las guardas

Un bloque `DO $$` al final, que aborta la migración si algo no cuadra:

| Guarda | Valor esperado | Qué detecta |
|---|---|---|
| `count(*)` de `permissions` | 111 | Un `INSERT` de menos, o un literal repetido que hizo fallar uno |
| Permisos de `SUPERADMIN` | 111 | `RN-SEG-007`: la raíz acotada por el catálogo completo |
| Permisos de `ADMIN` | 105 | La reserva sigue siendo de seis, ni uno más ni uno menos |
| Parejas (rol, padre) sin alguno de sus hijos | 0 | El reparto dejó a alguien a medias. Es la guarda que vale para los roles creados a mano |

## 3. Componentes afectados

| Capa | Elemento | Cambio |
|---|---|---|
| `db/migration` | `V28__sp_un_permiso_por_operacion.sql` | Nace |
| `system/roles/interfaces` | `RoleController` | Cinco operaciones cambian de permiso |
| `system/permissions/interfaces` | `PermissionController` | Una |
| `system/memberships/interfaces` | `MembershipController` | Una |
| `system/users/interfaces` | `UserController` | Cinco |
| `system/brokers/interfaces` | `BrokerAccountController` | Una |
| `products/interfaces` | `ProductController`, `ProductCommentController`, `PackageController` | Cuatro, tres y siete |
| `commissions/interfaces` | `UserCommissionRateController`, `ProductCommissionRateController`, `CommissionResolutionController` | Cuatro, una y una |
| `academy/interfaces` | `CourseCategoryController`, `CourseController`, `CourseModuleController`, `LessonController` y los del bloque 4 | Una, dos, cuatro, cinco y seis — **tramo 3** |
| `shared/security` (pruebas) | `EndpointPermissionsIT` | Gana la inyectividad (`CA-SP-689`) y la tabla operación → permiso (`CA-SP-690`) |
| `shared/persistence` (pruebas) | `PermissionSplitMigrationIT` | Nace: el reparto sobre roles creados **antes** de `V28` (`CA-SP-692`, `CA-SP-693`) |
| `system/permissions` (pruebas) | `PermissionsSeedIT`, `JpaPermissionQueryRepositoryIT`, `ListPermissionsServiceIT`, `PermissionIT` | Cuentan ciento once |
| Pruebas de cada módulo | Las que conceden un código para ejercer una operación | Conceden el de `spec.md` §6.2 |
| `RequiredPermissionCustomizer` | — | **No cambia**: lee la anotación, y publica lo que haya |

**Ningún servicio ni repositorio cambia.** La autorización vive en la anotación (`security.md` §6) y es lo único que se toca.

## 4. La prueba de inyectividad

`EndpointPermissionsIT` ya recorre todos los `HandlerMethod` con su `@PreAuthorize`. Se le añade una afirmación: agrupar por permiso y **ningún grupo tiene más de una operación**. El mensaje de fallo lista el permiso y sus operaciones, para que quien lo provoque sepa qué separar.

**En el tramo 2, entre `V28` y el reparto de `AC`, la afirmación conoce a `courses:update`, `courses:read` y `course-categories:read` como pendientes con fecha** —la misma técnica que la lista blanca `SIN_PERMISO_A_PROPOSITO`—, y el tramo 3 la vacía. Una excepción con fecha y motivo es la diferencia entre un pendiente y un olvido.

**Se comprueba en la prueba y no al arrancar.** `RequiredPermissionCustomizer` detiene el arranque cuando una expresión no se puede leer, porque decir a medias en el contrato es peor que fallar; un permiso repetido no es un contrato a medias sino un **diseño** equivocado, y el sitio de un diseño equivocado es `mvn verify`, que corre antes de cualquier arranque que importe.

## 5. El reparto sobre roles creados antes

`CA-SP-692` y `CA-SP-693` afirman algo sobre **el pasado**: un rol que existía con `roles:update` porta después sus hijos. Las pruebas de integración migran al arrancar, y cuando corren ya no hay pasado.

`PermissionSplitMigrationIT` lo fabrica: con la API de Flyway sobre **un esquema propio** del mismo contenedor —`defaultSchema` distinto, migraciones sin calificar—, migra hasta `V27`, inserta un rol bajo `ADMIN` con `roles:update` y `users:read` y otro con `roles:create`, migra `V28` y afirma: el primero porta los cuatro hijos de `roles:update` y los dos de `users:read`; el segundo, nada nuevo; y `ADMIN` ciento cinco. Es la única forma de probar la sentencia de §2.2 sobre datos que **`V8` no siembra**, y sin ella la guarda de «parejas a medias» estaría probada solo contra los dos roles de sistema.

## 6. Las pruebas de cada módulo

Unas sesenta clases conceden un código para ejercer una operación, con `authorities(…)`. Donde el código cambie, la prueba pasa a conceder el nuevo; **donde una prueba conceda el padre para una operación que ya no gobierna, recibirá `403`**, que es exactamente `CA-SP-690` ocurriendo sin querer. Se hace **módulo a módulo** con la suite del módulo en verde antes de pasar al siguiente, y no todo junto: un `403` inesperado en ciento veinte pruebas a la vez no dice cuál de los cincuenta y un permisos está mal.

**Y a cada operación que cambia de permiso se le añade el caso negativo del padre**: con `roles:update` y sin `roles:assign-permissions`, `POST /roles/{id}/permissions` responde `403`. Es la mitad de `CA-SP-690` que ninguna prueba existente cubre, porque hasta hoy el padre bastaba.

## 7. Contrato de API

Ninguna ruta, cuerpo ni respuesta cambia. Cambia **`x-required-permission`** y la primera línea de la descripción —«Permiso requerido: …»— en cincuenta y una operaciones, y las escribe el `RequiredPermissionCustomizer` a partir de la anotación: el contrato se regenera y se compara (`CA-SP-696`). `api/index.md` gana una fila.

## 8. Enmiendas que este plan declara

| Documento | Enmienda | Tramo |
|---|---|---|
| `security.md` | §4.3: `RN-SEG-014`. §4.4: el catálogo de ciento once, la regla, las convenciones de `spec.md` §6.3 y la fila de control 0.63.0 | 1 |
| `requirements/sp.md` | §6.1 y §9 con el permiso de cada operación; ficha de `RF-SP-060`; ficha de `RF-SP-059` con `users:read-sellers`; 1.62.0 | 1 |
| `requirements.md` | Fila de `RF-SP-060`; §5 con 145 registrados y `SP` en cincuenta y ocho; 0.178.0 | 1 |
| `requirements/pm.md` | §6.1 y §9 con el permiso de cada operación; la prosa de §5.2.10 sobre `packages:update` deja de valer y se anota | 2 |
| `requirements/cm.md` | §6: los diez permisos y el reverso de «asociar reutilizaba `commissions:update`» | 2 |
| `requirements/ac.md` | §6.1 y §7: los veintiocho de `AC`, con el reverso de «módulos y lecciones bajo `courses:update`» en fila propia; bloques 5 y 6 diseñados con un permiso por operación | 3 |
| Las sesenta y ocho tripletas de `spec.md` §6.2 | En cada `spec.md`, la línea de permiso (§3, §7 o §10 según la forma) y en cada `plan.md` su §de autorización, con una nota de Art. I.7 que cite `RF-SP-060` | 2 (`AC`: 3) |
| `docs/api/index.md` | Fila nueva: cincuenta y una operaciones cambian de permiso, ninguna de forma | 2 |
| `docs/testing/…` | Donde se describa `EndpointPermissionsIT`, la afirmación nueva | 2 |

**Las de tramo 1 se aplican en este mismo pase.** Las de tramo 2 se aplican **antes** del código de ese tramo, por bloques y con revisión, y las de tramo 3 las aplica quien cierre `AC` con este plan delante.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Renombrar todo a un esquema limpio** (`roles:list`/`roles:read-one`/…) | Cambia sesenta códigos que el frontend y las pruebas ya nombran, sin ganar granularidad. Conservar cada código con una operación cuesta veintiuna descripciones reescritas |
| **Retirar los padres y crear todos nuevos** | Retirar un permiso es otra operación: tiene filas en `role_permissions` y aparece en la auditoría de `RF-SP-005`. Y no hace falta: cada padre se queda con una operación |
| **Dar los hijos solo a `SUPERADMIN` y `ADMIN`** y que los demás roles se reconstruyan | Convierte el reparto en un recorte silencioso de todo rol creado a mano. `RN-SEG-003` además lo prohíbe al revés: un hijo en un rol y no en su padre |
| **Una migración por módulo**, cada uno la suya | La regla es transversal y la guarda de ciento once solo vale entera. Cuatro migraciones son cuatro momentos en que el catálogo está a medias |
| **Fallar al arrancar** si un permiso gobierna dos operaciones, en `RequiredPermissionCustomizer` | Es un error de diseño y su sitio es `verify`. Y el customizer existe para el contrato, no para la seguridad: cargarlo con la regla mezcla dos cosas que hoy están separadas a propósito |
| **Repartir `AC` bloque a bloque**, cambiando las tripletas del 4 antes de construirlas | Dejaría `AC` en dos estados a medio construir. Lo pidió así el responsable técnico de `AC` (spec §14) |
| **Mantener listado y detalle bajo un solo `read`** | Decisión del responsable del proyecto: estricto. La excepción es la puerta que se cierra |
| **`add`/`remove` para todas las relaciones** | `users:assign-roles` y `RF-SP-031` «retirar roles» fijaron `assign`/`revoke` el 21-08-2026. `add`/`remove` queda para elementos con datos propios |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El frontend deja de enseñar pantallas** porque decide por código y el código de la operación cambió | Se avisa a las sesiones de frontend con la tabla de `spec.md` §6.2 antes del tramo 2 (`tasks.md` §4). Nadie pierde acceso en el backend: lo que puede pasar es que el frontend esconda lo que sí se puede |
| 2 | Una prueba concede el padre para una operación que ya no gobierna y **la suite se llena de `403`** | Módulo a módulo, con la suite del módulo en verde antes del siguiente (§6) |
| 3 | El reparto **deja a medias** un rol creado a mano | La cuarta guarda de §2.4 aborta la migración; `PermissionSplitMigrationIT` lo prueba sobre roles que `V8` no siembra |
| 4 | `AC` queda entre el tramo 2 y el 3 con **permisos sembrados sin ruta** y rutas con el padre | Es el estado que `products:hotlink` tuvo ocho días. `EndpointPermissionsIT` lo declara con fecha y el tramo 3 lo vacía |
| 5 | Dos sesiones sobre el mismo árbol (`AC` construye el bloque 4 mientras esto avanza) | `V28` reservada con `backend-02` y `backend-ff` el 19-09-2026; `AC` no se toca hasta el tramo 3; commits por ruta |
| 6 | Un literal de `V28` **choca** con uno futuro de otro módulo | Las series continúan donde cada una quedó (§2.1), y `PermissionsSeedIT` cuenta y compara |

## 11. Estrategia de prueba

- **La regla**: `EndpointPermissionsIT`, inyectividad sobre todos los `HandlerMethod` (`CA-SP-689`) y la tabla operación → permiso completa de `spec.md` §6.2 (`CA-SP-690`, la mitad positiva).
- **La mitad negativa de `CA-SP-690`**, en cada módulo: con el padre y sin el hijo, `403`.
- **El catálogo**: ciento once, identificadores distintos, literales de `V8`/`V19`/`V22` intactos (`CA-SP-688`); `SUPERADMIN` y `ADMIN` (`CA-SP-691`); descripciones que no nombran lo perdido (`CA-SP-695`).
- **El reparto sobre el pasado**: `PermissionSplitMigrationIT` (`CA-SP-692`, `CA-SP-693`).
- **Lo que se pidió poder hacer**: un rol con `users:list` y sin `users:read` (`CA-SP-694`).
- **Lo que no cambia**: `RN-PM-027` con los códigos nuevos de reseña (`CA-SP-697`); la reserva de `movements:` para `ADMIN`.
- **El contrato**: regenerado y comparado (`CA-SP-696`).
