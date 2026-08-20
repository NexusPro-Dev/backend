# PLAN — `RF-XXX-NNN` [Nombre de la funcionalidad]

| Campo | Valor |
|---|---|
| Requerimiento | `RF-XXX-NNN` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | [DD-MM-AAAA] |
| Estado | Borrador · En revisión · **Aprobado** |
| Autor | [Nombre] |
| Aprobado por | [Responsable técnico] |
| Fecha de aprobación | [DD-MM-AAAA] |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

    **No se empieza a escribir hasta que `spec.md` esté aprobada** (Art. I.6). Planear sobre una especificación que aún va a cambiar es donde más trabajo se pierde.

---

## 1. Enfoque

[Dos o tres frases: cómo se va a resolver. Suficiente para que alguien entienda la forma de la solución antes de entrar al detalle.]

## 2. Cambios de esquema

**Migración:** `V<n>__<descripcion>.sql`

| Tabla | Cambio | Detalle |
|---|---|---|
| [tabla] | Crea / Altera | [Columnas, tipos, restricciones] |

Recordatorios que aplican siempre:

- Clave primaria `uuid` v7 generada en la aplicación (Art. V.11).
- `created_at` y `updated_at`; **sin** columnas de actor (Art. V.7).
- Integridad declarada en el esquema: claves foráneas, `NOT NULL`, únicas y `CHECK` (Art. V.6).
- Convenciones de nombre de `architecture.md` §6.2.

Si no hay cambios de esquema, decirlo explícitamente.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | | | |
| `application` | | | |
| `infrastructure` | | | |
| `api` | | | |

Las reglas `RN-…` van en `domain` y deben poder probarse sin Spring ni base de datos (Art. VI.3).

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/[recurso]` | [Qué hace] |

**Petición**

```json
{ }
```

**Respuesta `2xx`**

```json
{ }
```

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | [Condición] | `VAL-001` |
| `409` | [Condición] | `RN-XXX-NNN` |

El formato de error es el de `architecture.md` §7.3. Toda colección se pagina (§7.4).

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| [Método y ruta] | `recurso:acción` |

Si el permiso no existe todavía en el catálogo, indicar en qué migración se crea. Un endpoint sin permiso declarado queda inaccesible (Art. IV.1).

## 6. Auditoría

Qué evento emite esta funcionalidad y a cuál de los cuatro registros del Art. V.8:

| Operación | Registro | Contenido relevante |
|---|---|---|
| [Operación] | `audit_change_log` | Diff de los campos modificados |
| [Eliminación] | `audit_deletion_log` | Motivo obligatorio y `snapshot` |
| [Fallo] | `audit_error_log` | Código, tipo y severidad |
| [Evento de acceso] | `audit_security_log` | Tipo de evento y `outcome` |

Sin evento de auditoría, la autoría del cambio es irrecuperable (Art. V.7).

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Cambio de negocio y su auditoría de cambio/eliminación | **La misma** (Art. V.14) |
| Auditoría de error y de seguridad | **Independiente**, `REQUIRES_NEW` (Art. V.14) |

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| [Módulo] | [Qué cambia para él] |

Ninguno accede a las tablas de otro: la comunicación es por interfaz publicada (`architecture.md` §5.3).

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| [Opción] | [Motivo] |

Esta sección es la que da valor al documento dentro de un año. Una alternativa descartada sin motivo escrito se vuelve a proponer.

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| [Riesgo] | Alto / Medio / Bajo | [Qué se hace] |

## 11. Estrategia de prueba

Qué nivel cubre cada criterio de aceptación de `spec.md` §12:

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-XXX-001` | Unitaria / Integración / API | [Qué] |

Toda regla `RN-…` se prueba de forma unitaria y aislada.
