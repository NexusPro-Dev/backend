# ADR-004 — Quién mira las métricas y a quién se le avisa

| Campo | Valor |
|---|---|
| Estado | **Propuesta — pendiente de decisión** |
| Fecha | 27-08-2026 |
| Decide | Responsable del proyecto |
| Redacta | Bonilla Diaz William Steven |
| Issue | #43 (desprendido de #31) |
| Documentos afectados | `architecture.md` §9.1 · `security.md` §6 · `deployment.md` §13 |

---

## Contexto

**Desde el 25-08-2026 el sistema produce señal y nadie la consume.** `/actuator/metrics` publica `http.server.requests`, `request_log` guarda la duración de cada petición, y hay veintiún tipos de evento de seguridad.

[`ADR-002`](ADR-002-plataforma-de-despliegue-railway.md) **cierra D-09 sin resolver esto**, y lo deja dicho: Railway no aporta ni permiso propio ni red de administración. Ya no hay decisión pendiente tras la que esperar — es este ADR o nada.

Cuatro huecos, y el tercero no es como los otros:

| Hueco | Consecuencia |
|---|---|
| **Nadie raspa las métricas** | Están detrás de la autenticación, como exige el Art. XV.10, y un raspador no porta un JWT |
| **Nadie mira los umbrales** | El Art. XV.9 fija p95 < 500 ms en lectura y < 1 s en escritura. Ahora se pueden **medir**; nadie comprueba que se cumplen. El requisito no está incumplido: está **sin comprobar**, que es peor, porque una degradación no avisa |
| **Nadie vigila la ausencia de eventos de auditoría** | `RF-SP-001` §10 acepta **a conciencia** que `recordSecurityAfterCommit` deje una operación sin evento si la escritura falla después del `commit`, **a cambio de que esa ausencia se vigile**. Es un trato que hoy cumple una sola de las dos partes |
| **Nadie nota que la purga dejó de correr** | `SESSION_TOKENS_PURGED` es informativo a propósito: lo que merece atención no es que ocurra, sino que **deje** de ocurrir. Eso es ausencia, y la ausencia no la detecta un evento |

## Decisión 1 — Cómo se accede a las métricas sin JWT

### A · Un permiso propio, `metrics:read`

Un permiso más del catálogo, portado por una identidad de servicio.

**Coste:** una migración de permiso, y sobre todo **una identidad para procesos automáticos**, que es la decisión **D-19** y sigue abierta. Arrastra otro pendiente en lugar de cerrar este.

### B · Una credencial estática solo para el actuator

Autenticación básica en `/actuator/**`, con su propia credencial, fuera del modelo de permisos.

**Coste:** un segundo mecanismo de autenticación en el sistema. Barato de construir y caro de recordar: quien audite la seguridad tendrá que descubrir que existe.

### C · Puerto de administración separado (recomendada)

`management.server.port` distinto del de la aplicación, sin exponer al exterior. En Railway, la red privada del proyecto.

**Coste:** cero código. Es configuración, y es lo que Spring Boot ya ofrece para esto.

**Lo que se paga:** depende de que el borde no publique ese puerto, que es una propiedad de la plataforma y no del código. Hay que verificarla, y verificarlo es una tarea con nombre, no un supuesto.

**Por qué esta:** no toca el modelo de permisos, no estrena un segundo mecanismo de autenticación y no espera a D-19. Y el Art. XV.10 se sigue cumpliendo: las métricas no son públicas.

## Decisión 2 — Qué se alerta

Cuatro señales, y el orden no es alfabético:

| # | Señal | Por qué está aquí |
|---|---|---|
| 1 | **Ausencia de eventos de auditoría** en una ventana con tráfico | Es la mitad del trato de `RF-SP-001` §10, y la única que hoy no se cumple. Debería construirse **primero** aunque sea la más rara |
| 2 | **La purga no corrió** en su ventana | Detecta ausencia, que ningún evento puede detectar |
| 3 | **p95 por encima del umbral** del Art. XV.9 | Convierte un requisito medible en un requisito comprobado |
| 4 | **Tasa de `5xx`** por encima de un suelo | La red de seguridad de todo lo demás |

**Las dos primeras son detectores de ausencia y las dos últimas de exceso**, y esa diferencia decide cómo se construyen: un umbral se consulta, una ausencia hay que ir a buscarla.

## Decisión 3 — Dónde

Aquí no hay recomendación, y conviene decirlo en lugar de fingirla: depende de si el proyecto quiere pagar un servicio gestionado o mantener el suyo, y eso no se deduce de nada escrito hasta ahora.

| Opción | Coste real |
|---|---|
| Servicio gestionado (Grafana Cloud, Better Stack o equivalente) | Cuota mensual y un secreto más. Cero operación |
| Autogestionado en la misma plataforma | Sin cuota y **con** operación: es un segundo sistema que mantener, y el que vigila no puede caerse con el vigilado |
| **Nada, y revisión manual con calendario** | Honesto mientras no haya usuarios. Deja de serlo el día que los haya, y ese día no avisa |

## Recomendación

1. **Decisión 1: opción C**, y verificar que el puerto no queda expuesto.
2. **Decisión 2: construir la señal 1 primero**, aunque sea la menos vistosa. Es la única que el sistema **prometió por escrito** y no cumple.
3. **Decisión 3: aplazarla explícitamente** hasta que haya un entorno con uso real, y **anotar la fecha de revisión**. Un aplazamiento sin fecha es un olvido con mejor nombre.

## Qué hace falta para cerrarla

- Confirmar la opción de acceso, y con ella si D-19 sigue pudiendo esperar.
- Decidir a **quién** se avisa. Una alerta sin destinatario es un registro más.
- Fijar la fecha de revisión de la decisión 3.
