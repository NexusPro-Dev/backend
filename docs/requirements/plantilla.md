# Requerimientos del Módulo — `COD` [Nombre del módulo]

| Campo | Valor |
|---|---|
| Módulo | `COD` — [Nombre] |
| Versión | 0.1.0 |
| Estado | Borrador · En revisión · **Aprobado** |
| Responsable | [Nombre] |
| Fecha de creación | [DD-MM-AAAA] |
| Última actualización | [DD-MM-AAAA] |

!!! info "Qué va en este documento"

    **El catálogo de requerimientos del módulo:** qué debe hacer, bajo qué reglas de negocio y con qué permisos.

    **El detalle de cada requerimiento no va aquí.** Flujos, validaciones, criterios de aceptación y casos límite viven en la tripleta de ese requerimiento (`docs/specs/<módulo>/<NNN>-<nombre>/`). Repetirlos aquí crearía dos fuentes que se contradicen.

    Este documento responde *«qué requerimientos tiene el módulo»*; la tripleta responde *«cómo se comporta cada uno»*.

---

## 1. Información del módulo

### 1.1 Descripción

[Qué es el módulo, en dos o tres frases.]

### 1.2 Objetivo

[Qué necesidad del negocio resuelve.]

### 1.3 Alcance

**Incluye**

- [Funcionalidad]

**No incluye**

- [Funcionalidad fuera de alcance, y a qué módulo pertenece]

## 2. Submódulos

Según [`modules.md` §5](../modules.md).

| Submódulo | Responsabilidad | Requerimientos |
|---|---|---|
| [Nombre] | [Qué hace] | `RF-COD-001`, … |

## 3. Dependencias

| Módulo | Tipo | Para qué |
|---|---|---|
| [Módulo] | Consume | [Qué necesita de él] |

## 4. Actores

| Actor | Rol en el módulo | Permisos típicos |
|---|---|---|
| [Actor] | [Qué hace] | `recurso:acción` |

## 5. Reglas de negocio

| ID | Regla | Detalle en |
|---|---|---|
| `RN-COD-001` | [Enunciado] | [Documento] |

## 6. Requerimientos funcionales

### 6.1 Resumen

| ID | Requerimiento | Prioridad | Permiso | Estado |
|---|---|---|---|---|
| `RF-COD-001` | [Nombre] | Crítica | `recurso:acción` | Pendiente |

**Prioridades:** Crítica · Alta · Media · Baja.
**Estados:** los de [`requirements.md` §4](../requirements.md).

### 6.2 Fichas

#### `RF-COD-001` — [Nombre]

| Campo | Valor |
|---|---|
| Objetivo | [Qué necesidad satisface] |
| Actor | [Quién lo ejecuta] |
| Permiso requerido | `recurso:acción` |
| Prioridad | Crítica / Alta / Media / Baja |
| Reglas aplicables | `RN-COD-001` |
| Depende de | `RF-COD-NNN` o — |
| Tripleta | `docs/specs/<mod>/NNN-<nombre>/` |
| Estado | Pendiente |

[Descripción del requerimiento: qué debe permitir hacer el sistema. Dos o tres frases. El comportamiento detallado va en `spec.md`.]

## 7. Requerimientos no funcionales

| ID | Requerimiento | Detalle en |
|---|---|---|
| `RNF-CAT-001` | [Enunciado] | [Documento] |

## 8. Integraciones

| Sistema o módulo | Tipo | Dirección | Descripción |
|---|---|---|---|
| [Sistema] | REST | Entrada / Salida | [Para qué] |

## 9. API

| Método | Ruta | Requerimiento | Permiso |
|---|---|---|---|
| `GET` | `/api/v1/[recurso]` | `RF-COD-001` | `recurso:read` |

El contrato detallado de cada endpoint se define en el `plan.md` de su tripleta.

## 10. Persistencia

| Entidad | Descripción | Dueño |
|---|---|---|
| `tabla` | [Qué guarda] | Este módulo |

El esquema exacto vive en las migraciones Flyway, que son su fuente de verdad (Art. V.3).

## 11. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | [DD-MM-AAAA] | Creación inicial. | [Nombre] |
