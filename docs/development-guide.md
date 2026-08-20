# Guía de Desarrollo — Backend NEXUS

| Campo | Valor |
|---|---|
| Proyecto | NEXUS — Renovación de plataforma |
| Empresa | FACTECH GROUP SAS |
| Documento | `development-guide.md` |
| Versión | 0.5.0 |
| Estado | Borrador |
| Responsable técnico | Bonilla Diaz William Steven |
| Fecha de creación | 19-08-2026 |
| Última actualización | 19-08-2026 |
| Documento superior | `constitution.md` v0.5.0 |
| Documentos relacionados | `architecture.md` v0.4.0, `security.md` v0.3.0 |

---

## 1. Propósito y alcance

Esta guía responde una pregunta concreta: **cómo se escribe código en NEXUS, día a día**. Traduce las reglas de la constitución y las decisiones de arquitectura en convenciones aplicables mientras se programa.

Es el documento de consulta frecuente. Si algo aquí contradice a `constitution.md` o `architecture.md`, prevalecen esos y esta guía debe corregirse (§0.1 de la constitución).

**Fuera de alcance:** la estructura de capas (`architecture.md` §5), el modelo de autorización (`security.md`), y los niveles y la estrategia de prueba (`testing-strategy.md`).

---

## 2. Puesta en marcha del entorno local

### 2.1 Prerrequisitos

| Herramienta | Versión | Verificación |
|---|---|---|
| JDK Temurin | 21 | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Docker Desktop | Reciente | `docker version` |
| Git | Reciente | `git --version` |

### 2.2 Primer arranque

```bash
git clone https://github.com/NexusPro-Dev/backend.git
cd backend
cp .env.example .env          # completar los valores locales
docker compose up -d db       # levanta PostgreSQL
mvn clean verify              # compila, formatea y ejecuta pruebas
mvn spring-boot:run           # arranca la aplicación
```

Flyway aplica las migraciones automáticamente al arrancar (`architecture.md` §4).

### 2.3 Comprobación

| Recurso | URL |
|---|---|
| Estado de salud | `http://localhost:8080/actuator/health` |
| Documentación de API | `http://localhost:8080/swagger-ui.html` |
| Especificación OpenAPI | `http://localhost:8080/v3/api-docs` |

### 2.4 Comandos frecuentes

```bash
mvn spotless:apply            # formatea el código
mvn test                      # pruebas unitarias
mvn verify                    # todo, incluidas pruebas de integración
mvn flyway:info               # estado de las migraciones
docker compose down -v        # reinicia la base de datos desde cero
mkdocs serve                  # sitio de documentación en local (ver 2.5)
```

Si `mvn verify` falla en tu máquina, **no** abras el Pull Request esperando que CI lo resuelva.

### 2.5 La documentación como sitio

La documentación de `docs/` se publica como sitio navegable con MkDocs y el tema Material. Los archivos Markdown siguen siendo la **fuente de verdad**; el sitio es solo una vista de ellos.

**Con Docker, sin instalar nada** (recomendado, y coherente con el Art. X.1):

```bash
docker run --rm -it -p 8000:8000 -v "$PWD":/docs squidfunk/mkdocs-material
```

**Con Python**, si prefieres tenerlo local:

```bash
pip install -r requirements-docs.txt
mkdocs serve            # recarga en caliente en http://localhost:8000
mkdocs build --strict   # construye en site/ y falla ante cualquier advertencia
```

Reglas al tocar la documentación:

- La navegación **no** se declara en `mkdocs.yml`: la genera `mkdocs-awesome-pages-plugin` a partir de los archivos `.pages` de cada carpeta. Una tripleta nueva aparece sola en el sitio, sin tocar configuración ni provocar conflictos de merge.
- Para fijar el orden o el título de una sección, se edita el `.pages` de esa carpeta. Lo que no se lista queda recogido por `...` al final, de modo que ningún documento se vuelve invisible por olvido.
- Con `--strict`, un enlace roto **detiene el pipeline**.
- Los enlaces entre documentos se escriben como rutas relativas al archivo `.md` (`[Seguridad](security.md)`), no como URLs del sitio publicado. Así funcionan igual en GitHub, en el editor y en el sitio.
- Los diagramas se escriben en bloques ` ```mermaid `, que se renderizan tanto en GitHub como en el sitio.
- El directorio `site/` es un artefacto de construcción y no se versiona (Art. XI.5).

El sitio se publica automáticamente en GitHub Pages al integrar en `main`. En Pull Request solo se construye, como verificación de que la documentación sigue siendo válida.

---

## 3. Flujo de trabajo

El ciclo completo, en el orden en que ocurre (Art. I, III):

```
 1. Requerimiento aprobado      docs/requirements/<modulo>.md
 2. spec.md escrita             docs/specs/<modulo>/<NNN>-<nombre>/spec.md
 3. COMPUERTA 1                 spec aprobada — Pull Request propio
 4. plan.md escrito             .../plan.md
 5. COMPUERTA 2                 plan aprobado — Pull Request propio
 6. tasks.md escrito            .../tasks.md
 7. COMPUERTA 3                 tasks aprobadas — Pull Request propio
 8. Issue creado en GitHub      referencia el RF y enlaza a tasks.md
 9. Rama de trabajo             feature/<descripcion-corta>
10. Implementación + pruebas    en el orden que fija tasks.md
11. mvn verify en verde         local, antes de publicar
12. Pull Request                con la plantilla de §12.3
13. Revisión y aprobación       al menos una persona distinta del autor
14. Integración                 a develop
15. Trazabilidad actualizada    matriz en docs/requirements.md
```

**No empieces por el paso 10.** Las tres compuertas del Art. I.6 existen para que el negocio apruebe el *qué* sin discutir el *cómo*, y para no planear sobre una especificación que todavía va a cambiar. Son tres Pull Requests pequeños y rápidos de revisar, no tres semanas de espera.

**Los cambios de la tripleta que se descubran implementando** vuelven a su compuerta (Art. I.7): no se resuelven en el código ni se anotan «para después».

---

## 4. Convenciones de nomenclatura

### 4.1 Idioma

| Elemento | Idioma | Motivo |
|---|---|---|
| Identificadores de código (clases, métodos, variables) | Inglés | Convención de Java y de las librerías del stack |
| Tablas, columnas, códigos de permiso | Inglés | Coherencia con el código |
| Documentación, comentarios | Español | Idioma del equipo y de los requerimientos |
| Mensajes dirigidos al usuario | Español | Idioma del producto |
| Nombres de métodos de prueba | Español | Describen criterios de aceptación redactados en español |

Los términos del dominio tienen equivalencia directa (rol → `Role`, permiso → `Permission`, usuario → `User`), por lo que esta separación no genera ambigüedad. Si algún día aparece un término del negocio **sin** equivalente natural en inglés, se conserva en español y se documenta en el glosario del módulo.

### 4.2 Java

| Elemento | Convención | Ejemplo |
|---|---|---|
| Paquete | minúsculas, sin guiones | `com.factech.nexus.modules.security` |
| Clase | `PascalCase`, sustantivo | `RoleService` |
| Interfaz | `PascalCase`, sin prefijo `I` | `RoleRepository` |
| Método | `camelCase`, verbo | `createRole()` |
| Variable | `camelCase`, descriptiva | `effectivePermissions` |
| Constante | `UPPER_SNAKE_CASE` | `MAX_PAGE_SIZE` |
| Enum | `PascalCase`; valores en `UPPER_SNAKE_CASE` | `RoleStatus.ACTIVE` |
| Prueba unitaria | `<Clase>Test` | `RoleServiceTest` |
| Prueba de integración | `<Clase>IT` | `RoleControllerIT` |

**Sufijos por rol dentro del módulo:**

| Capa | Sufijo | Ejemplo |
|---|---|---|
| `api` | `Controller`, `Request`, `Response` | `RoleController`, `CreateRoleRequest` |
| `application` | `Service` o `UseCase` | `CreateRoleService` |
| `domain` | Sin sufijo técnico | `Role`, `PermissionCode` |
| `infrastructure` | `Repository`, `Adapter`, `Entity` | `JpaRoleRepository` |

**Prohibido:** nombres genéricos sin significado (`data`, `info`, `manager`, `util`, `helper`, `process`, `handle`). Si una clase solo puede llamarse `RoleManager`, probablemente tiene más de una responsabilidad.

### 4.3 Base de datos

Definidas en `architecture.md` §6.2. Recordatorio del nombre de las migraciones:

```
V<n>__<descripcion_en_snake_case>.sql

V1__create_permissions.sql
V2__create_roles.sql
V3__create_users.sql
```

El número es estrictamente creciente y **nunca** se reutiliza. Si dos ramas toman el mismo número, la última en integrarse renumera su migración antes del merge.

---

## 5. Anatomía de una funcionalidad

Archivos que se tocan al implementar `RF-SP-001 — Registrar rol`. Sirve como plantilla mental de lo que un Pull Request debe contener:

```
docs/specs/sp/001-registrar-rol/                Tripleta (aprobada antes del código)
  spec.md                                       Qué debe pasar y por qué
  plan.md                                       Cómo se construye
  tasks.md                                      En qué pasos

src/main/resources/db/migration/
  V2__create_roles.sql                          Esquema

src/main/java/com/factech/nexus/modules/security/
  domain/Role.java                              Entidad de dominio y reglas RN-SEG-…
  domain/RoleStatus.java
  domain/RoleRepository.java                    Puerto (interfaz)
  application/CreateRoleService.java            Caso de uso, transaccional
  infrastructure/JpaRoleRepository.java         Adaptador
  api/RoleController.java                       Endpoint
  api/dto/CreateRoleRequest.java                DTO de entrada, con validaciones
  api/dto/RoleResponse.java                     DTO de salida

src/test/java/.../security/
  domain/RoleTest.java                          Reglas de negocio, sin Spring
  application/CreateRoleServiceTest.java        Caso de uso, con dobles
  api/RoleControllerIT.java                     Extremo a extremo con Testcontainers
```

Si un Pull Request no incluye pruebas, está incompleto (Art. VII.1).

---

## 6. Estándares de código

### 6.1 Formato

El formato **no se discute en revisión**: lo aplica la herramienta. `mvn spotless:apply` antes de commitear; `mvn verify` falla si el formato no está aplicado.

El repositorio incluye `.editorconfig` para que los editores respeten indentación y fin de línea.

### 6.2 Reglas generales

- **Inmutabilidad por defecto.** Campos `final`, colecciones no modificables al exponerlas. Usa `record` para DTOs y objetos de valor.
- **Nada de `null` como valor de retorno.** Devuelve `Optional<T>` cuando la ausencia es un resultado legítimo, o lanza una excepción cuando es un error.
- **Constructor por argumentos para inyección de dependencias.** Nunca `@Autowired` sobre campos: impide construir la clase en una prueba sin Spring.
- **Métodos cortos y con un solo nivel de abstracción.** Si necesitas comentarios para separar bloques dentro de un método, esos bloques son métodos.
- **Sin código muerto, comentado ni dependencias sin uso** (Art. VI.8). El historial de Git guarda lo que borres.
- **Sin números ni cadenas mágicas.** Constantes con nombre o configuración.
- Aprovecha Java 21 donde aporte claridad: `record`, `sealed`, pattern matching en `switch`, bloques de texto para SQL literal.

### 6.3 Verificaciones automáticas

| Herramienta | Qué verifica | Cuándo |
|---|---|---|
| Spotless | Formato del código | `mvn verify`, CI |
| ArchUnit | Reglas de dependencia entre capas (`architecture.md` §5.2) | Suite de pruebas |
| JaCoCo | Cobertura, como indicador | `mvn verify` |

**ArchUnit merece atención:** las reglas de capas no se sostienen por disciplina, se sostienen porque una prueba falla. Como mínimo debe verificar que `domain` no importa Spring, JPA ni nada de `jakarta.servlet`, y que `api` no accede a `infrastructure`.

---

## 7. Manejo de errores

### 7.1 Jerarquía

```
DomainException (abstracta)
├── ValidationException          → 400
├── BusinessRuleException        → 409  (viola una RN-…)
├── ResourceNotFoundException    → 404
├── UnauthorizedException        → 401
└── ForbiddenException           → 403
```

- El dominio y la capa `application` lanzan estas excepciones. **No conocen HTTP.**
- Un único `@RestControllerAdvice` las traduce al formato `ProblemDetail` (`architecture.md` §7.3). Es el **único** lugar del código que decide códigos de estado.

### 7.2 Reglas

- **Nunca captures una excepción para ignorarla.** Un `catch` vacío o que solo registra y continúa oculta fallos reales (Art. VI.4).
- **No captures `Exception` de forma genérica** salvo en el manejador global.
- Al lanzar una excepción de regla de negocio, incluye el **identificador de la regla**: `throw new BusinessRuleException("RN-SEG-003", "...")`. Así el error es trazable al requerimiento.
- Los mensajes al cliente son en español, comprensibles y **sin detalles internos** (Art. VI.5). El detalle técnico va al log, no a la respuesta.

### 7.3 Ejemplo

```java
// domain
public void assignPermissions(Set<PermissionCode> permissions, Role parent) {
    if (parent != null && !parent.permissions().containsAll(permissions)) {
        throw new BusinessRuleException(
            "RN-SEG-003",
            "Un rol no puede tener permisos que su rol padre no posee.");
    }
    ...
}
```

---

## 8. Validación

Dos niveles, con responsabilidades distintas y sin superposición:

| Nivel | Dónde | Qué valida | Herramienta |
|---|---|---|---|
| Formato | `api`, sobre el DTO | Obligatoriedad, longitud, tipo, patrón | Bean Validation (`@NotBlank`, `@Size`) |
| Negocio | `domain` / `application` | Reglas `RN-…`, unicidad, coherencia de estados | Código propio |

Una regla de negocio **NO DEBE** implementarse con anotaciones sobre el DTO: quedaría fuera del dominio y sin poder probarse de forma aislada (Art. VI.3).

Toda validación produce un código `VAL-NNN` declarado en la especificación, que viaja en el campo `errors` de la respuesta de error.

---

## 9. Logging

### 9.1 Niveles

| Nivel | Uso | Ejemplo |
|---|---|---|
| `ERROR` | Fallo que exige intervención | Error no controlado, caída de dependencia |
| `WARN` | Situación anómala recuperable | Reintento, configuración degradada |
| `INFO` | Hito de negocio relevante | Arranque, migración aplicada |
| `DEBUG` | Detalle para diagnóstico | Solo en `development` |

### 9.2 Reglas

- **Nunca** registres contraseñas, tokens, cabeceras `Authorization` ni datos personales sensibles (Art. IV.8, `security.md` §7.3).
- Logs estructurados, con el identificador de correlación incorporado (Art. XV.6). Llega solo por el contexto de logging; no lo pases a mano.
- **No uses el log como auditoría.** La auditoría son los cuatro registros de `architecture.md` §6.6 y tiene garantías transaccionales; el log no. Escribir «usuario X eliminó el rol Y» en el log no sustituye la fila de `audit_deletion_log`: el log se purga, no se consulta por entidad y no exige el motivo.
- **No registres una excepción y además la relances.** Produce el mismo error duplicado en el log. Relanza, y que la maneje quien corresponda.
- Nada de `System.out.println`.

---

## 10. Transacciones

- `@Transactional` vive en la capa **`application`**, sobre el caso de uso. No en controladores ni en repositorios.
- Una operación de negocio es **una** transacción. Si necesitas dos, probablemente son dos casos de uso.
- **El evento de `audit_change_log` o `audit_deletion_log` se escribe dentro de la misma transacción** que el cambio (`architecture.md` §8). Es obligatorio: son la única fuente del actor del cambio (Art. V.7, V.8).
- **La auditoría de error y la de seguridad van en transacción propia** (`@Transactional(propagation = REQUIRES_NEW)`). No es una preferencia: se emiten mientras la transacción de negocio se revierte, y escritas dentro de ella el `rollback` se lleva el evento (Art. V.14). Si escribes un evento de error en la transacción que acaba de fallar, no queda constancia de nada.
- El `request_log` se escribe fuera de la transacción de negocio; de eso se encarga la infraestructura y no es responsabilidad del caso de uso.
- Las consultas usan `@Transactional(readOnly = true)`.
- **Nunca** ejecutes llamadas a sistemas externos dentro de una transacción abierta.

---

## 11. Persistencia

- **No expongas entidades JPA en la API.** El controlador recibe y devuelve DTOs. Filtrar una entidad hacia afuera acopla el contrato a la tabla y termina publicando campos que nadie decidió publicar.
- Relaciones **siempre `LAZY`**. `EAGER` parece cómodo y produce consultas que nadie pidió.
- Cuidado con el problema **N+1**: usa `JOIN FETCH` o proyecciones. Activa el conteo de consultas en pruebas de integración cuando la operación sea sensible.
- **Sin `cascade = ALL` indiscriminado.** Declara la cascada que realmente corresponda.
- Toda consulta de colección es **paginada** (`architecture.md` §7.4).
- Los identificadores UUID v7 se generan en la aplicación, no en la base de datos (`architecture.md` §6.3).
- El esquema solo cambia por migración Flyway (Art. V.4). `ddl-auto` permanece en `validate`.
- Una migración ya integrada en `main` **no se edita nunca** (Art. V.5); se corrige con una nueva.

---

## 12. Git

### 12.1 Ramas

```
main                    siempre desplegable, protegida
develop                 integración
feature/<descripcion>   funcionalidad nueva
fix/<descripcion>       corrección
refactor/<descripcion>  refactorización sin cambio funcional
hotfix/<descripcion>    corrección urgente sobre producción
```

Descripción corta, en minúsculas y con guiones: `feature/registrar-rol`, `fix/error-inventario`.

### 12.2 Commits

```
<tipo>(<modulo>): <resumen en imperativo, minúscula, sin punto final>

<cuerpo: por qué se hace el cambio, no qué líneas cambiaron>

Requerimiento: RF-SP-001
```

| Tipo | Cuándo se usa |
|---|---|
| `feat` | Funcionalidad nueva |
| `fix` | Corrección de un defecto |
| `refactor` | Cambio interno sin efecto funcional |
| `perf` | Mejora de rendimiento |
| `test` | Pruebas, sin cambio de producción |
| `docs` | Documentación |
| `build` | Construcción y dependencias: `pom.xml`, `Dockerfile` |
| `ci` | Flujos de integración continua: `.github/workflows/` |
| `chore` | Mantenimiento que no encaja en ninguno de los anteriores |

`build` y `ci` existen porque un cambio de pipeline o de construcción no es documentación ni una tarea de mantenimiento: tiene su propio riesgo y su propia revisión.

```
feat(security): validar la contencion de permisos al crear un rol

Un rol podia declarar permisos que su rol padre no posee, lo que permitia
escalar privilegios creando un rol hijo. La validacion se aplica en el
dominio para que sea independiente del punto de entrada.

Requerimiento: RF-SP-001
```

El commit explica **por qué** (Art. XI.4). El *qué* ya está en el diff.

### 12.3 Pull Requests

Contenido obligatorio (Art. III.4):

```markdown
## Requerimiento
RF-SP-001 — Registrar rol
Tripleta: docs/specs/sp/001-registrar-rol/
Issue: #25

## Descripción del cambio
[Qué se implementó]

## Solución
[Cómo se resolvió y por qué así]

## Evidencia de pruebas
[Pruebas agregadas y resultado de mvn verify]

## Impacto técnico
[Migraciones, cambios de contrato, configuración nueva]

## Consideraciones adicionales
[Deuda asumida, alternativas descartadas, seguimiento pendiente]
```

Reglas:

- Un Pull Request, un requerimiento (Art. XIV.2).
- **No mezcles** cambios funcionales con refactorizaciones amplias o reformateos masivos: hacen la revisión imposible (Art. XIV.3).
- Requiere aprobación de alguien distinto del autor y CI en verde (Art. III.5).
- Si tu PR supera unos cientos de líneas de cambio funcional, considera dividirlo.

---

## 13. Antes de abrir un Pull Request

- [ ] Existe la especificación y el PR la referencia.
- [ ] `mvn verify` pasa en local.
- [ ] Hay pruebas para cada criterio de aceptación del requerimiento.
- [ ] Las reglas de negocio están en `domain`, no en el controlador.
- [ ] Toda creación y edición emite su evento en `audit_change_log`, con el diff de lo que cambió.
- [ ] Toda eliminación emite su evento en `audit_deletion_log`, con motivo y `snapshot`; el endpoint rechaza la eliminación sin motivo.
- [ ] Los fallos no controlados y los rechazos por regla de negocio emiten su evento en `audit_error_log`, en transacción independiente.
- [ ] Los eventos de seguridad que toque el cambio están en `audit_security_log` (`security.md` §8).
- [ ] Ningún evento de auditoría quedó sin IP de origen habiendo llegado por HTTP (Art. V.15).
- [ ] Los endpoints nuevos declaran su permiso; ninguno quedó sin declaración.
- [ ] La API nueva está documentada en OpenAPI y coincide con el comportamiento real.
- [ ] No hay secretos, credenciales ni datos reales en el cambio.
- [ ] Los registros no exponen datos sensibles.
- [ ] La documentación afectada se actualizó en este mismo PR (Art. XII.3).
- [ ] La matriz de trazabilidad se actualizó.
- [ ] No quedó código comentado, `TODO` sin issue, ni `System.out.println`.

---

## 14. Uso de asistencia por IA

Aplica el Artículo XIII. En términos prácticos:

- Puedes usar IA para generar, analizar, refactorizar, documentar y proponer pruebas.
- **Lo que firmes en un Pull Request es tuyo.** Si no puedes explicar una línea en la revisión, no la integres (Art. XIII.3).
- Revisa especialmente: consultas generadas (N+1, inyección), manejo de errores silenciosos, dependencias que la herramienta agrega por su cuenta, y pruebas que verifican el *mock* en lugar del comportamiento.
- **Nunca** pegues credenciales, secretos ni datos reales de producción en una herramienta de IA (Art. XIII.5).
- La IA no aprueba Pull Requests ni sustituye la revisión humana.

---

## 15. Antipatrones frecuentes

| Antipatrón | Por qué falla | Qué hacer |
|---|---|---|
| Lógica de negocio en el controlador | No se puede probar sin HTTP y se duplica al agregar otro punto de entrada | Llevarla al dominio (Art. VI.2) |
| Entidad JPA devuelta por la API | El contrato queda atado a la tabla | DTO de salida explícito |
| `catch (Exception e) { log.error(...) }` y continuar | Convierte un fallo en un dato corrupto silencioso | Relanzar o manejar de verdad |
| Regla de negocio como `@NotNull` en el DTO | Queda fuera del dominio y sin prueba unitaria | Validar en `domain` |
| Consultar por otro módulo su repositorio | Rompe el límite del módulo | Usar la interfaz publicada (`architecture.md` §5.3) |
| Agregar `created_by` a una tabla "por comodidad" | Duplica el actor que ya vive en la auditoría y se desincroniza | Proyección sobre `audit_change_log` (Art. V.7) |
| Auditar el error dentro de la transacción que falló | El `rollback` borra el evento: el fallo ocurre y no queda rastro | Transacción independiente (Art. V.14) |
| Rellenar el motivo de eliminación desde el código | El campo cumple el `NOT NULL` y no informa nada; "eliminado por el sistema" no es un motivo | Pedirlo en el contrato y rechazar la eliminación sin él (Art. V.13) |
| Auditar un `UPDATE` con el registro completo | Multiplica el volumen y no responde qué cambió | Guardar solo el diff (`architecture.md` §6.6.2) |
| Tomar la IP de `X-Forwarded-For` sin validar | Es una cabecera del cliente: cualquiera escribe su propia coartada | Resolverla contra la lista de proxies confiables (Art. V.15) |
| Un solo evento para un cambio de rol | El cambio de negocio y el evento de seguridad se consultan con permisos distintos | Emitir ambos (`security.md` §8.1) |
| Migración editada tras integrarse | Los entornos ya aplicados quedan divergentes | Migración nueva (Art. V.5) |
| Prueba que solo verifica el mock | Pasa siempre, no prueba nada | Probar comportamiento observable |
| `TODO` sin issue asociado | Nadie lo va a atender | Crear el issue o resolverlo |

---

## 16. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 19-08-2026 | Creación inicial. | Responsable técnico |
| 0.2.0 | 19-08-2026 | Nueva sección 2.5: publicación de la documentación como sitio con MkDocs. | Responsable técnico |
| 0.3.0 | 20-08-2026 | Se ajustan §9.2, §10, §13 y §15 a la separación de la auditoría en cuatro registros: transaccionalidad diferenciada, motivo de eliminación obligatorio e IP de origen. | Responsable técnico |
| 0.4.0 | 20-08-2026 | Se adopta la tripleta `spec` / `plan` / `tasks`: §3 incorpora las tres compuertas y §5 y §12.3 apuntan a la carpeta de la tripleta. §2.5 pasa a navegación por `.pages`. | Responsable técnico |
| 0.5.0 | 20-08-2026 | §12.2 incorpora los tipos de commit `build` y `ci`, que faltaban, y describe cuándo se usa cada tipo. | Responsable técnico |
