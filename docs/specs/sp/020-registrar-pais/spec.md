# SPEC — `RF-SP-020` Registrar país

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-020` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Incorporar un país al catálogo del sistema.

## 2. Contexto

El catálogo de países se usa para ubicar personas y operaciones. A diferencia de las monedas, sí puede ampliarse desde la API: la plataforma incorpora países a medida que crece, y exigir un despliegue para cada uno sería desproporcionado.

Ahora bien, **un país registrado no puede editarse ni eliminarse** (`RN-SP-009`). Eso convierte al alta en la única oportunidad de que el dato sea correcto: un nombre mal escrito queda para siempre y aparecerá en cada formulario. La validación en este punto es toda la defensa que hay.

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
- Divisiones internas: departamentos, estados o ciudades.
- Asociar una moneda al país.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-009` | Los países no se editan ni eliminan | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Código | Sí | Código internacional del país | Dos letras, único en el catálogo |
| Nombre | Sí | Nombre del país | Único en el catálogo |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| País | País registrado, con su código y su nombre |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de creación de países.

**Postcondiciones**

- El país queda registrado y disponible de inmediato.
- El registro es definitivo: no podrá modificarse ni retirarse.
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
| `CA-SP-137` | El sistema no expone operación de edición ni de eliminación sobre el catálogo |
| `CA-SP-138` | El sistema registra el alta en la auditoría de cambios |
| `CA-SP-139` | El sistema rechaza el alta a un actor sin el permiso de creación de países |

## 13. Casos límite

- **Código en minúsculas:** se normaliza a mayúsculas antes de validar la unicidad, para que no entren duplicados que solo difieren en el caso.
- **Nombre con espacios sobrantes:** se recortan los extremos antes de validar.
- **Nombre con acentos o caracteres no latinos:** debe admitirse; el catálogo es internacional.
- **Alta concurrente del mismo código:** la restricción única del esquema resuelve el empate; el segundo intento recibe el error de duplicado.
- **País registrado por error:** no hay marcha atrás por la API. Ver pregunta abierta 1.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | Sin edición ni borrado, un alta equivocada solo se corrige por migración. ¿Es aceptable, o conviene un indicador de país activo que permita ocultarlo sin borrarlo? | Responsable técnico | Abierta |
| 2 | ¿El catálogo debería sembrarse por migración con la lista internacional completa, en lugar de darlo de alta a mano? Evitaría errores de escritura y haría innecesario este requerimiento | Responsable técnico | Abierta |
| 3 | ¿Se registra el prefijo telefónico o la moneda del país? Serán necesarios en cuanto haya usuarios y cobros | Responsable técnico | Abierta |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
