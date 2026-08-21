# SPEC — `RF-SP-020` Registrar país

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-020` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Incorporar un país al catálogo del sistema.

## 2. Contexto

El catálogo de países se usa para ubicar personas y operaciones. A diferencia de las monedas, sí puede ampliarse desde la API: la plataforma incorpora países a medida que crece, y exigir un despliegue para cada uno sería desproporcionado.

Ahora bien, **un país registrado no puede editarse ni eliminarse** (`RN-SP-009`). Eso convierte al alta en la única oportunidad de que el dato sea correcto: un nombre mal escrito queda para siempre y aparecerá en cada formulario. La validación en este punto es casi toda la defensa que hay.

La otra defensa es el **estado**: un país registrado por error puede desactivarse (`RF-SP-022`), con lo que deja de ofrecerse para seleccionar sin que su registro desaparezca ni cambie. Desactivar no es corregir —el nombre mal escrito sigue ahí, y quien ya lo tenía asignado lo conserva—, pero evita que el error se propague.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Registra países |
| Administrador | Registra países |

## 4. Alcance

### 4.1 Incluye

- Alta de un país con su código y su nombre.

### 4.2 No incluye

- Editar o eliminar países (`RN-SP-009`).
- Cambiar el estado de un país → `RF-SP-022`.
- Divisiones internas: departamentos, estados o ciudades.
- Asociar una moneda al país.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-009` | Los países no se editan ni eliminan; lo único que puede cambiarse es su estado | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Código | Sí | Código internacional del país | Dos letras, único en el catálogo |
| Nombre | Sí | Nombre del país | Único en el catálogo |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| País | País registrado, con su código, su nombre y su estado |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de creación de países.

**Postcondiciones**

- El país queda registrado, activo y disponible de inmediato.
- El código y el nombre son definitivos: no podrán modificarse. Lo único reversible es el estado (`RF-SP-022`).
- Queda constancia en la auditoría de cambios.

## 8. Flujo principal

1. El actor solicita registrar un país y proporciona su código y su nombre.
2. El sistema valida el formato del código y la obligatoriedad de los datos.
3. El sistema verifica que ni el código ni el nombre estén en uso.
4. El sistema registra el país.
5. El sistema registra el evento en la auditoría de cambios.
6. El sistema informa el país creado.

## 9. Flujos alternativos

Ninguno.

## 10. Excepciones

### EX-001 — Código o nombre ya en uso

**Condición:** el catálogo ya contiene ese código o ese nombre.
**Respuesta del sistema:** rechaza el alta e informa cuál está duplicado. Al no existir edición ni borrado, el duplicado sería permanente.

### EX-002 — Código con formato inválido

**Condición:** el código no tiene el formato del estándar internacional de dos letras.
**Respuesta del sistema:** rechaza el alta e informa el formato esperado.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Código obligatorio | El código del país es obligatorio. |
| `VAL-002` | Código de dos letras | El código del país debe tener dos letras. |
| `VAL-003` | Nombre obligatorio | El nombre del país es obligatorio. |
| `VAL-004` | Código único | Ya existe un país con ese código. |
| `VAL-005` | Nombre único | Ya existe un país con ese nombre. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-134` | El sistema registra un país con código y nombre válidos |
| `CA-SP-135` | El sistema rechaza el alta con un código que no tenga el formato de dos letras |
| `CA-SP-136` | El sistema rechaza el alta con código o nombre ya presentes en el catálogo |
| `CA-SP-137` | El sistema no expone operación de edición ni de eliminación sobre el catálogo; el único cambio admitido es el estado, por `RF-SP-022` |
| `CA-SP-171` | El país queda activo al registrarse |
| `CA-SP-138` | El sistema registra el alta en la auditoría de cambios |
| `CA-SP-139` | El sistema rechaza el alta a un actor sin el permiso de creación de países |

## 13. Casos límite

- **Código en minúsculas:** se normaliza a mayúsculas antes de validar la unicidad, para que no entren duplicados que solo difieren en el caso.
- **Nombre con espacios sobrantes:** se recortan los extremos antes de validar.
- **Nombre con acentos o caracteres no latinos:** debe admitirse; el catálogo es internacional.
- **Alta concurrente del mismo código:** la restricción única del esquema resuelve el empate; el segundo intento recibe el error de duplicado.
- **País registrado por error:** el código y el nombre no se corrigen, pero el país puede desactivarse con `RF-SP-022` para que deje de ofrecerse. El registro permanece, porque puede haber datos que ya lo referencien.

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 21-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Indicador de país activo? | **Sí.** Un catálogo que no se edita ni se borra necesita alguna salida para el alta equivocada, y exigir un despliegue para cada error de escritura es desproporcionado. El país se desactiva y deja de ofrecerse, sin desaparecer: puede haber datos que ya lo referencien. Cambiarlo es `RF-SP-022`, con su propio permiso, y `RN-SP-009` se enmienda para admitir esa única modificación |
| 2 | ¿Sembrar por migración la lista internacional completa? | **No: el alta sigue siendo manual por la API.** La plataforma llega a los países de uno en uno, y sembrar los casi doscientos llenaría cada selector de opciones a las que no se opera. El coste es el error de escritura, y para eso están la validación del alta y el estado de la pregunta 1 |
| 3 | ¿Prefijo telefónico o moneda del país? | **No ahora.** Los pedirán los requerimientos de usuarios y el módulo de cobros cuando existan, y entonces se sabrá qué forma deben tener —un país puede tener más de un prefijo, y su moneda de curso no siempre es la de cobro—. Añadir el dato después es una migración sobre un catálogo pequeño, no una revisión de cálculos, a diferencia de los decimales de `RF-SP-019` |
