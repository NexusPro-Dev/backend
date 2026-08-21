# Flujos del Módulo — `SP` Sistema Principal

| Campo | Valor |
|---|---|
| Módulo | `SP` — Sistema Principal |
| Versión | 0.1.0 |
| Estado | **Borrador** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 21-08-2026 |
| Última actualización | 21-08-2026 |

!!! info "Qué va en este documento"

    La vista de conjunto de los requerimientos de `SP`: cómo se encadenan entre sí, qué debe existir antes de qué, y qué deja cada operación en la auditoría.

    Se dibujan los **21 que tienen spec aprobada**. `RF-SP-022` y `RF-SP-023` —cambiar el estado de un país y de una moneda— están registrados sin tripleta y aparecen marcados como pendientes donde corresponde.

    No define comportamiento. Todo lo que aquí se dibuja está declarado en las precondiciones y postcondiciones de las tripletas de `docs/specs/sp/`; este documento solo lo hace visible. Ante cualquier discrepancia, **manda la spec**.

    El flujo *técnico* de una petición —controlador, servicio, repositorio— está en [`architecture.md` §8](../architecture.md), y no se repite aquí.

---

## 1. Ciclo de vida del rol

Siete de los veintiún requerimientos escriben sobre la misma entidad. El rol nace activo (`RF-SP-001`) y desde ahí solo hay dos caminos: cambiar de estado o desaparecer.

```mermaid
stateDiagram-v2
    direction LR

    [*] --> Vigente : RF-SP-001 · registrar

    state Vigente {
        direction LR
        [*] --> Activo
        Activo --> Inactivo : RF-SP-007 · desactivar
        Inactivo --> Activo : RF-SP-007 · activar
    }

    Vigente --> Vigente : RF-SP-004 · editar<br/>RF-SP-005 · asignar permisos<br/>RF-SP-006 · revocar permisos<br/>RF-SP-008 · cambiar rol padre
    Vigente --> Eliminado : RF-SP-009 · eliminar<br/>motivo obligatorio
    Eliminado --> [*] : código y nombre quedan libres
```

**Lo que el diagrama no puede dibujar y hay que leer en las specs:**

- Las cuatro mutaciones del bucle exigen las mismas tres condiciones: el rol **no está eliminado**, **no es de sistema** y **el actor no lo porta**. Ninguna distingue entre activo e inactivo: un rol inactivo se sigue editando.
- `RF-SP-007` desactivar **no** revoca asignaciones. El rol deja de conceder permisos de inmediato, pero el vínculo con los usuarios se conserva; reactivarlo lo restituye entero.
- `RF-SP-009` exige además **sin hijos vigentes ni usuarios asignados**.
- El **rol raíz** queda fuera de tres operaciones: no cambia de estado (`RF-SP-007` `EX-001`), no cambia de padre (`RF-SP-008` `EX-003`) y no se elimina (`RF-SP-009` `EX-004`). Un rol raíz inactivo dejaría al sistema sin su última vía de administración.
- Un **rol de sistema** no tiene ninguna transición saliente: no se edita, no cambia de estado y no se elimina. Nace por migración y permanece.

---

## 2. Qué debe existir antes de qué

Las precondiciones de las 21 specs forman un orden de dependencias. Este es el mapa de lo que hay que tener poblado antes de poder ejecutar cada operación.

```mermaid
flowchart LR
    MIG(["Migración<br/>RN-SP-004"])

    PERM[("Catálogo de<br/>permisos")]
    MON[("Catálogo de<br/>monedas")]
    RAIZ["Rol raíz<br/>único, sin padre<br/>RN-SEG-007"]

    MIG --> PERM
    MIG --> MON
    MIG --> RAIZ

    F001["RF-SP-001<br/>registrar rol"]
    F005["RF-SP-005<br/>asignar permisos"]
    F008["RF-SP-008<br/>cambiar rol padre"]
    F009["RF-SP-009<br/>eliminar rol"]
    F016["RF-SP-016<br/>registrar membresía"]
    F020["RF-SP-020<br/>registrar país"]

    ROL[("Roles")]
    MEM[("Membresías<br/>cadena lineal")]
    PAIS[("Países")]

    PERM -->|"el permiso existe<br/>y está en el padre"| F001
    PERM -->|"el permiso existe"| F005
    RAIZ -->|"todo rol cuelga<br/>de uno activo"| F001

    F001 --> ROL
    ROL -->|"rol padre destino activo"| F008
    F008 -->|"reubicar los hijos<br/>antes de poder borrar"| F009
    ROL --> F009

    F016 -->|"la hija indicada existe"| MEM
    F020 --> PAIS

    USR{{"Módulo USR<br/>sin usuarios asignados"}} -->|"condición de borrado"| F009

    classDef cat fill:#e7eef0,stroke:#2d5a6b,stroke-width:1px,color:#151b1e
    classDef ext fill:#f6e6e2,stroke:#a33b2a,stroke-dasharray:4 3,color:#151b1e
    class PERM,MON,RAIZ,ROL,MEM,PAIS cat
    class USR ext
```

El encadenamiento `RF-SP-008 → RF-SP-009` es el único no evidente: como no se elimina un rol con hijos vigentes, borrar un nodo intermedio de la jerarquía obliga a **reubicar antes** cada hijo con `RF-SP-008`. Ninguna de las dos specs lo dice; se deduce cruzándolas.

---

## 3. Las 21 operaciones, por naturaleza

**Nueve escriben y doce solo leen** —estas últimas con la misma postcondición literal: «ninguna, la consulta no altera el estado del sistema»—.

```mermaid
flowchart TB
    subgraph ESC["Escriben estado · 9"]
        direction LR
        E1["RF-SP-001<br/>registrar rol"]
        E2["RF-SP-004<br/>editar rol"]
        E3["RF-SP-005<br/>asignar permisos"]
        E4["RF-SP-006<br/>revocar permisos"]
        E5["RF-SP-007<br/>cambiar estado"]
        E6["RF-SP-008<br/>cambiar rol padre"]
        E7["RF-SP-009<br/>eliminar rol"]
        E8["RF-SP-016<br/>registrar membresía"]
        E9["RF-SP-020<br/>registrar país"]
    end

    subgraph LEC["Solo leen · 12"]
        direction TB
        subgraph L1["Roles y permisos · 4"]
            direction LR
            C2["RF-SP-002<br/>roles"]
            C3["RF-SP-003<br/>detalle rol"]
            C10["RF-SP-010<br/>permisos"]
            C15["RF-SP-015<br/>detalle permiso"]
        end
        subgraph L2["Auditoría · 4"]
            direction LR
            C11["RF-SP-011<br/>cambios"]
            C12["RF-SP-012<br/>eliminación"]
            C13["RF-SP-013<br/>error"]
            C14["RF-SP-014<br/>seguridad"]
        end
        subgraph L3["Catálogos · 4"]
            direction LR
            C17["RF-SP-017<br/>membresías"]
            C18["RF-SP-018<br/>detalle membresía"]
            C19["RF-SP-019<br/>monedas"]
            C21["RF-SP-021<br/>países"]
        end
    end

    ESC ==>|"dejan rastro"| L2
```

La proporción no es casual: `SP` es un módulo de administración, y la mitad de su superficie existe para poder responder qué pasó. El bloque de auditoría es el único que se alimenta de todos los demás.

---

## 4. Auditoría transversal

Las cuatro tablas de [`architecture.md` §6.6](../architecture.md) se escriben por caminos distintos y se consultan por requerimientos distintos. Este es el cruce completo.

```mermaid
flowchart LR
    subgraph W["Quién escribe"]
        direction TB
        WR["RF-SP-001 · 004 · 005<br/>007 · 008<br/><i>altas y ediciones de rol</i>"]
        WC["RF-SP-016 · 020<br/><i>membresías y países</i>"]
        WD["RF-SP-006 · 009<br/><i>revocación y eliminación</i>"]
        WE["Capa de manejo de errores<br/><i>architecture.md §8</i>"]
    end

    CH[("audit_change_log")]
    DEL[("audit_deletion_log")]
    SEC[("audit_security_log")]
    ERR[("audit_error_log")]

    WR --> CH
    WR --> SEC
    WC --> CH
    WD --> DEL
    WD --> SEC
    WE --> ERR

    CH --> Q11["RF-SP-011<br/>consultar cambios"]
    DEL --> Q12["RF-SP-012<br/>consultar eliminación"]
    ERR --> Q13["RF-SP-013<br/>consultar error"]
    SEC --> Q14["RF-SP-014<br/>consultar seguridad"]

    CH -.-> V{{"Vista transversal<br/>solo lectura<br/>exige los 4 permisos"}}
    DEL -.-> V
    ERR -.-> V
    SEC -.-> V

    classDef tabla fill:#e7eef0,stroke:#2d5a6b,stroke-width:1px,color:#151b1e
    class CH,DEL,SEC,ERR tabla
```

Tres asimetrías que el cruce deja a la vista:

| Observación | Estado |
|---|---|
| `audit_error_log` **no tiene escritor declarado en ninguna spec de `SP`**, pero `RF-SP-013` lo consulta. Su única fuente es la capa transversal de errores. | Coherente con `architecture.md` §6.6.3, pero ninguna spec lo enuncia. |
| `RF-SP-016` y `RF-SP-020` escriben en cambios pero **no** en seguridad; las siete operaciones sobre roles escriben en ambas. | Deliberado —membresías y países no son control de acceso—, aunque no está dicho en ningún sitio. |
| `RF-SP-006` escribe en eliminación **sin motivo**; `RF-SP-009` lo escribe **con motivo obligatorio**. | Declarado en ambas specs. Es la excepción a «todo borrado lleva motivo». |

---

## 5. Los submódulos de catálogo

Membresías, monedas y países no comparten forma. Cada uno decidió distinto sobre su ciclo de vida, y esas decisiones solo se ven al ponerlos uno al lado del otro.

```mermaid
flowchart TB
    subgraph MEM["Membresías · cadena lineal"]
        direction LR
        M1["RF-SP-016<br/>registrar"] --> M2["RF-SP-017<br/>consultar"] --> M3["RF-SP-018<br/>detalle"]
        M1 -.->|"RN-SP-008 · ni editar ni eliminar,<br/>y sin indicador de activo:<br/>desactivar un eslabón dejaría<br/>un hueco en un orden lineal"| MX["∅"]
    end

    subgraph MON["Monedas · alta solo por migración"]
        direction LR
        N1(["alta por migración"]) --> N2["RF-SP-019<br/>consultar"]
        N2 --> N3["RF-SP-023<br/>cambiar estado<br/><i>sin tripleta</i>"]
    end

    subgraph PAI["Países · alta por API, definitiva"]
        direction LR
        P1["RF-SP-020<br/>registrar"] --> P2["RF-SP-021<br/>consultar"]
        P2 --> P3["RF-SP-022<br/>cambiar estado<br/><i>sin tripleta</i>"]
        P1 -.->|"código y nombre<br/>definitivos · RN-SP-009"| PX["∅"]
    end

    classDef vacio fill:#f6e6e2,stroke:#a33b2a,stroke-dasharray:3 3,color:#a33b2a
    classDef pend fill:#fbfcfd,stroke:#8a9ba2,stroke-dasharray:4 3,color:#46565c
    class MX,PX vacio
    class N3,P3 pend
```

- **Monedas** no tiene alta por API y es deliberado: `RF-SP-019` §2 lo argumenta —el catálogo existe desde el principio para que sumar una moneda sea insertar una fila, no migrar cada tabla financiera—. Lo único modificable es su estado (`RN-SP-010`), y la moneda por defecto no puede desactivarse.
- **Países** sí se dan de alta por API, a medida que la plataforma llega a ellos, y el catálogo no se siembra con la lista internacional completa. El código y el nombre son definitivos; el estado es la salida para un alta equivocada (`RN-SP-009`).
- **Membresías** es la única de las tres **sin ninguna salida**: ni edición, ni reubicación, ni baja, ni indicador de activo. `RN-SP-008` lo justifica de forma explícita —desactivar un eslabón dejaría un hueco en un orden lineal—, así que la inmutabilidad es deliberada y está escrita.

Los dos catálogos que admiten cambio de estado lo hacen con requerimientos que **todavía no tienen tripleta**: `RF-SP-022` para países y `RF-SP-023` para monedas.

---

## 6. Qué queda por resolver

| # | Punto | Dónde se resuelve |
|---|---|---|
| 1 | `RF-SP-022` y `RF-SP-023` están registrados pero no tienen tripleta: hasta que la tengan, ningún flujo describe cómo se cambia el estado de un país o de una moneda | `specs/sp/022-…` y `specs/sp/023-…` |
| 2 | El encadenamiento `RF-SP-008 → RF-SP-009` para borrar nodos intermedios no está dicho en ninguna de las dos specs | `RF-SP-009` §13 casos límite |
| 3 | `audit_error_log` sin escritor declarado del lado de `SP` | `RF-SP-013` §2 contexto |
| 4 | La excepción de `RF-SP-006` —eliminación sin motivo— no figura junto a la regla general | `architecture.md` §6.6.3 |
| 5 | Los roles de sistema quedan fuera de `RF-SP-004` a `RF-SP-009`: no tienen ninguna operación de mantenimiento | `security.md` §4.3 |

---

## 7. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 21-08-2026 | Creación inicial. Cinco diagramas derivados de las precondiciones y postcondiciones de las 21 tripletas de `SP`, y cinco puntos abiertos que el cruce deja a la vista. | Responsable técnico |
