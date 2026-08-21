# SPEC — `RF-SP-014` Consultar auditoría de seguridad

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-014` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Revisar la actividad sobre el control de acceso: quién entró, a quién se le negó, y quién cambió los privilegios de quién.

## 2. Contexto

Es el registro más sensible de los cuatro, y por eso su permiso se concede **aparte** de los demás: quien diagnostica fallos no necesita ver los intentos de acceso, y quien audita cambios de negocio tampoco.

Reúne dos clases de evento que conviene no separar: los de **autenticación** —entradas, fallos, bloqueos— y los de **privilegio** —creación de roles, cambios de permisos, asignación de roles a personas—. Juntos permiten responder si un acceso indebido vino de una credencial comprometida o de un privilegio concedido de más.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Responsable de seguridad | Revisa la actividad de control de acceso |

## 4. Alcance

### 4.1 Incluye

- Listado paginado de eventos de autenticación, autorización y cambio de privilegios.
- Filtro por tipo de evento, severidad, resultado, actor, usuario afectado y rango de fechas.
- Registro de la propia consulta como evento de seguridad: aquí, mirar también deja rastro.

### 4.2 No incluye

- Los demás tipos de evento → `RF-SP-011`, `RF-SP-012` y `RF-SP-013`.
- Las credenciales, en ninguna forma: ni en claro ni cifradas.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RNF-SEG-006` | Los eventos de seguridad quedan registrados | `security.md` §11 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Página y tamaño | No | Paginación | Por defecto 20, máximo 100 (`architecture.md` §7.4) |
| Tipo de evento | No | Filtro por evento del catálogo cerrado | Uno de los definidos |
| Severidad | No | Informativa, media o alta | Uno de los valores definidos |
| Resultado | No | Éxito o fallo | Uno de los dos valores |
| Actor | No | Quien ejecutó la acción | — |
| Usuario afectado | No | Sobre quién recayó la acción | — |
| Desde y hasta | No | Rango de fechas | La fecha inicial no puede ser posterior a la final |
| Dirección de red | No | Filtro por origen | — |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Eventos | Momento, tipo de evento, severidad, resultado, actor y usuario afectado |
| Origen | Dirección de red y cliente desde el que se originó |
| Correlación | Identificador que enlaza con la petición |
| Paginación | Total de elementos, total de páginas y página actual |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de auditoría de seguridad.

**Postcondiciones**

- Ninguna sobre los datos consultados.
- La consulta queda registrada como un evento de seguridad más: quién la hizo, cuándo y con qué filtros.

## 8. Flujo principal

1. El actor solicita el registro de seguridad, con o sin filtros.
2. El sistema valida la paginación, el rango de fechas y los filtros.
3. El sistema recupera los eventos que cumplen los filtros, del más reciente al más antiguo.
4. El sistema registra la consulta como evento de seguridad.
5. El sistema devuelve la página solicitada con su información de paginación.

## 9. Flujos alternativos

### FA-001 — Investigación de una cuenta

**Cuándo ocurre:** se sospecha del uso indebido de una cuenta.

1. El actor filtra por el usuario afectado y un rango de fechas.
2. El sistema devuelve toda la actividad de control de acceso relativa a esa cuenta: entradas, fallos, bloqueos y cambios de sus privilegios.
3. Es el flujo para el que existe esta consulta.

### FA-002 — Sin resultados

**Cuándo ocurre:** ningún evento cumple los filtros.

1. El sistema devuelve una colección vacía; no es un error.

## 10. Excepciones

### EX-001 — Rango de fechas inválido

**Condición:** la fecha inicial es posterior a la final.
**Respuesta del sistema:** rechaza la consulta e informa el problema del rango.

### EX-002 — Parámetro de paginación inválido

**Condición:** la página es negativa o el tamaño excede el máximo configurado.
**Respuesta del sistema:** rechaza la consulta e informa el límite aplicable.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Rango de fechas coherente | La fecha inicial no puede ser posterior a la final. |
| `VAL-002` | Tamaño dentro del máximo configurado | El tamaño de página excede el máximo permitido. |
| `VAL-003` | Tipo, severidad y resultado dentro de su dominio | El valor del filtro no es válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-103` | El sistema devuelve los eventos de seguridad paginados, del más reciente al más antiguo |
| `CA-SP-104` | El sistema devuelve toda la actividad relativa a un usuario afectado en un rango de fechas |
| `CA-SP-105` | El sistema filtra por tipo de evento, severidad, resultado, actor y dirección de red |
| `CA-SP-106` | Ningún evento contiene contraseñas ni tokens, en ninguna forma |
| `CA-SP-107` | Los intentos de acceso fallidos aparecen con resultado de fallo y su severidad |
| `CA-SP-108` | Las denegaciones de autorización aparecen aquí y no en la auditoría de error |
| `CA-SP-109` | El intento de acceso con un usuario inexistente aparece en el registro sin revelar si la cuenta existía |
| `CA-SP-110` | Un actor con permiso sobre los otros registros no puede consultar este |
| `CA-SP-167` | Cada consulta de este registro genera a su vez un evento de seguridad con el actor, el momento y los filtros usados |

## 13. Casos límite

- **Intento de acceso con un usuario inexistente:** debe registrarse, pero sin revelar si el usuario existía. El evento no puede convertirse en un medio de enumeración de cuentas.
- **El evento que genera la propia consulta:** aparece en el registro que se está consultando. Es correcto y deliberado, pero conviene que no se confunda con actividad de acceso: se distingue por su tipo de evento.
- **Reutilización de una credencial de refresco revocada:** es el evento de mayor severidad y debe destacarse; indica robo de credenciales.
- **Ráfaga de intentos fallidos:** cada intento es un evento; el bloqueo genera uno propio.
- **Actor sin autenticar:** un fallo de acceso no tiene actor resuelto; el campo queda vacío, y la dirección de red pasa a ser el único identificador disponible.
- **Dirección de red detrás de un proxy:** debe resolverse contra la lista de proxies confiables. Una dirección falsificable no sirve como evidencia.

## 14. Preguntas abiertas

Ninguna. Las cuatro se resolvieron el 21-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Alerta automática ante severidad alta? | **No en este alcance: la revisión es manual.** Alertar exige decidir a quién se avisa, por qué canal y con qué umbral antes de que el ruido lo vuelva inútil, y eso es un requerimiento de observabilidad con reglas propias. Este registro debe primero existir y consultarse bien |
| 2 | ¿La consulta de este registro se audita a su vez? | **Sí, y es el único de los cuatro que lo hace.** En una auditoría de acceso, quién revisó los accesos ajenos es en sí mismo información de seguridad. Se añade como postcondición y como `CA-SP-167`. Los otros tres registros se conforman con el rastro del registro de peticiones (`RF-SP-011` §14) |
| 3 | ¿La lista de proxies confiables? (D-21) | **No bloquea esta especificación.** El sistema registra la dirección resuelta contra la lista configurada por entorno; cuál es esa lista en `testing` y en `production` es una decisión de despliegue, y D-21 queda abierta en `security.md`. El caso límite ya advierte que una dirección falsificable no sirve como evidencia |
| 4 | ¿Se conserva más tiempo que los demás? | **No pertenece a esta especificación**, igual que en `RF-SP-013`. Que este registro sea el que más valor tiene a largo plazo es un argumento para D-10, no un comportamiento de la consulta, que se comporta igual con cualquier retención |
