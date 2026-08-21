# SPEC — `RF-SP-001` Registrar rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-001` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 20-08-2026 |

---

## 1. Objetivo

Permitir que la organización defina un rol nuevo y su alcance de permisos sin necesidad de desplegar código.

## 2. Contexto

El control de acceso se basa en roles con permisos declarados de forma explícita. Cada rol se acota por su rol padre: no puede declarar permisos que el padre no tenga.

Sin esta funcionalidad, todo rol nuevo exigiría una migración y un despliegue, lo que convierte una decisión administrativa —«contabilidad necesita ver los balances»— en un ciclo de desarrollo.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Registra roles sin más límite que el catálogo de permisos |
| Administrador | Registra roles acotados por sus propios permisos efectivos |

## 4. Alcance

### 4.1 Incluye

- Alta de un rol con código, nombre, descripción, clasificación, rol padre y conjunto inicial de permisos.
- Validación de la contención de privilegios respecto del rol padre y del actor.

El rol **nace siempre activo**: el alta no recibe el estado. Desactivarlo es una operación distinta, con sus propias reglas (`RF-SP-007`), lo que deja un único camino hacia el estado inactivo y un solo lugar donde auditarlo.

### 4.2 No incluye

- Modificar los permisos de un rol ya creado → `RF-SP-005` y `RF-SP-006`.
- Asignar el rol a usuarios → `RF-SP-030`.
- Crear permisos: el catálogo solo se modifica por migración (`RN-SP-004`).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SEG-001` | Código y nombre únicos entre los no eliminados | `security.md` §4.3 |
| `RN-SEG-003` | Los permisos son subconjunto de los del rol padre | `security.md` §4.3 |
| `RN-SEG-004` | La validación se hace contra el padre inmediato | `security.md` §4.3 |
| `RN-SEG-007` | Existe exactamente un rol raíz sin padre | `security.md` §4.3 |
| `RN-SEG-010` | Nadie otorga permisos que no posee | `security.md` §4.3 |
| `RN-SP-002` | Rol padre obligatorio salvo en `SUPERADMIN` | `requirements/sp.md` §5.1 |
| `RN-SP-003` | Todo rol se clasifica | `requirements/sp.md` §5.1 |
| `RN-SP-011` | El orden comercial se expresa con el rol padre | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Código | Sí | Identificador corto y estable del rol | Lo escribe el actor. Mayúsculas, dígitos y guion bajo, empezando por letra, hasta 50 caracteres. Único entre los roles no eliminados. No se modifica después |
| Nombre | Sí | Nombre legible | Único entre los roles no eliminados |
| Descripción | No | Para qué existe el rol | — |
| Clasificación | Sí | Funcionario, vendedor o consumidor | Uno de los tres valores definidos. **Independiente** de la clasificación del rol padre |
| Rol padre | Sí | Rol que acota sus privilegios | Debe existir y estar activo |
| Permisos | No | Permisos que el rol declara | Cada uno debe existir, estar contenido en el padre y en el actor |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Identificador | Identificador del rol creado |
| Rol | Rol registrado, con sus permisos y su rol padre |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de creación de roles.
- El catálogo de permisos está poblado.
- Existe el rol raíz del sistema.

**Postcondiciones**

- El rol queda registrado en estado activo y disponible para asignarse a usuarios.
- Sus permisos quedan contenidos en los de su rol padre.
- Queda constancia del alta en la auditoría de cambios y en la de seguridad.

## 8. Flujo principal

1. El actor solicita registrar un rol y proporciona sus datos.
2. El sistema valida el formato y la obligatoriedad de los datos.
3. El sistema verifica que el código y el nombre no estén en uso.
4. El sistema verifica que el rol padre exista y esté activo.
5. El sistema verifica que los permisos declarados estén contenidos en los del rol padre.
6. El sistema verifica que los permisos declarados estén contenidos en los permisos efectivos del actor.
7. El sistema registra el rol con sus permisos.
8. El sistema registra el evento en la auditoría de cambios y en la de seguridad.
9. El sistema informa el rol creado.

## 9. Flujos alternativos

### FA-001 — Alta sin permisos iniciales

**Cuándo ocurre:** el actor no declara ningún permiso.

1. El sistema omite las verificaciones de contención.
2. El rol queda registrado sin permisos, a la espera de que se le asignen con `RF-SP-005`.

## 10. Excepciones

### EX-001 — Código o nombre ya en uso

**Condición:** existe un rol no eliminado con el mismo código o el mismo nombre.
**Respuesta del sistema:** rechaza el alta e informa cuál de los dos está duplicado.

### EX-002 — Rol padre inexistente o inactivo

**Condición:** el rol padre no existe, está eliminado o está inactivo.
**Respuesta del sistema:** rechaza el alta e informa que el rol padre no es válido.

### EX-003 — Permiso fuera del rol padre

**Condición:** algún permiso declarado no está entre los del rol padre.
**Respuesta del sistema:** rechaza el alta, cita `RN-SEG-003` e informa **qué permisos** lo incumplen.

### EX-004 — Permiso fuera del alcance del actor

**Condición:** algún permiso declarado no está entre los permisos efectivos del actor.
**Respuesta del sistema:** rechaza el alta, cita `RN-SEG-010` e informa qué permisos lo incumplen.

### EX-005 — Permiso inexistente

**Condición:** algún permiso declarado no está en el catálogo.
**Respuesta del sistema:** rechaza el alta e informa qué permisos no existen.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Código obligatorio | El código del rol es obligatorio. |
| `VAL-002` | Nombre obligatorio | El nombre del rol es obligatorio. |
| `VAL-003` | Clasificación obligatoria y dentro del dominio | La clasificación del rol no es válida. |
| `VAL-004` | Rol padre obligatorio | El rol padre es obligatorio. |
| `VAL-005` | Código único entre no eliminados | Ya existe un rol con ese código. |
| `VAL-006` | Nombre único entre no eliminados | Ya existe un rol con ese nombre. |
| `VAL-007` | Longitud máxima de código, nombre y descripción | El campo excede la longitud permitida. |
| `VAL-008` | Formato del código | El código solo admite letras mayúsculas, dígitos y guion bajo, y debe empezar por letra. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-001` | El sistema registra un rol con datos válidos y permisos contenidos en su rol padre |
| `CA-SP-002` | El sistema rechaza el alta cuando el código o el nombre ya están en uso |
| `CA-SP-003` | El sistema rechaza el alta cuando algún permiso no está contenido en el rol padre, e indica cuáles |
| `CA-SP-004` | El sistema rechaza el alta cuando algún permiso excede los permisos efectivos del actor |
| `CA-SP-005` | El sistema permite registrar un rol sin permisos iniciales |
| `CA-SP-006` | El sistema permite reutilizar el código de un rol eliminado lógicamente |
| `CA-SP-007` | El sistema registra el alta en la auditoría de cambios y en la de seguridad |
| `CA-SP-008` | El sistema rechaza el alta a un actor sin el permiso de creación de roles |
| `CA-SP-144` | El sistema rechaza un código que no cumpla el formato de mayúsculas, dígitos y guion bajo |
| `CA-SP-145` | El sistema registra un rol cuya clasificación difiere de la de su rol padre |
| `CA-SP-146` | El rol registrado queda siempre en estado activo, sin que el alta lo reciba como dato |

## 13. Casos límite

- **Código de un rol eliminado lógicamente:** debe poder reutilizarse (`RN-SEG-001`).
- **Rol padre eliminado lógicamente:** se trata como inexistente.
- **Permisos duplicados en la petición:** se normalizan a una sola ocurrencia, sin error.
- **Rol padre igual al rol que se crea:** imposible, el rol aún no existe; queda cubierto en `RF-SP-008`.
- **Alta concurrente con el mismo código:** la restricción única del esquema debe resolver el empate; el segundo intento recibe el error de duplicado, no un error interno.
- **Rol raíz:** no se crea por esta funcionalidad. Se puebla por migración, porque `RN-SP-002` exige rol padre y `RN-SEG-007` admite uno solo sin él.
- **Código en minúsculas:** se rechaza por formato. No se normaliza en silencio, para que el actor vea exactamente qué código quedó registrado.
- **Rol consumidor bajo un padre funcionario:** válido. Es el caso de `ESTUDIANTE` bajo `ADMIN` en el catálogo aprobado.

## 14. Preguntas abiertas

Ninguna. Las cuatro se resolvieron el 20-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El código lo escribe el actor o lo deriva el sistema? | Lo escribe el actor. El código es inmutable y el nombre no: derivarlo ataría un dato estable a uno que `RF-SP-004` permite cambiar |
| 2 | ¿Qué formato admite el código? | Mayúsculas, dígitos y guion bajo, empezando por letra, hasta 50 caracteres. Es el formato del catálogo ya aprobado |
| 3 | ¿La clasificación debe coincidir con la del padre? | No, es independiente. El catálogo aprobado ya lo exige: `ESTUDIANTE` es consumidor y cuelga de `ADMIN`, que es funcionario |
| 4 | ¿Puede crearse un rol inactivo? | No, nace siempre activo. Deja un único camino hacia inactivo, `RF-SP-007`, y un solo lugar donde auditarlo |
