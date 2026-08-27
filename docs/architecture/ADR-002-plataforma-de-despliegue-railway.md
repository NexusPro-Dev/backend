# ADR-002 — Plataforma de despliegue: Railway

| Campo | Valor |
|---|---|
| Estado | **Aceptada** |
| Fecha | 27-08-2026 |
| Decide | Responsable del proyecto |
| Redacta | Bonilla Diaz William Steven |
| Cierra | **D-09** — Infraestructura de despliegue para `testing` y `production` |
| Documentos afectados | `architecture.md` §12 y §16 · `security.md` §12 · nuevo `deployment.md` |

---

## Contexto

**D-09 llevaba abierta desde el 19-08-2026 y ya no bloqueaba solo un pipeline.** Se había convertido en la respuesta por defecto de todo lo que no tenía dónde ir:

| Quién la esperaba | Qué queda sin hacer mientras tanto |
|---|---|
| `security.md` §4.5 | El corte de tokens ya emitidos se propaga por un registro **en memoria**: con dos instancias solo corta en una. Impide desplegar una segunda |
| `security.md` §5.5 | El límite de tasa cuenta **por proceso**: con réplicas, el techo real se multiplica |
| `architecture.md` §9.1 | **Nadie raspa las métricas**: un raspador no porta un JWT, y hace falta un permiso propio o una red que las aísle |
| `security.md` §12 (**D-21**) | La lista de proxies confiables «se fija por entorno», y no había entorno |
| `security.md` §6.1 | Los orígenes autorizados del navegador «son configuración de despliegue», ídem |

Y por debajo de todo eso, lo más simple: **cuarenta y dos requerimientos con endpoint funcionando y ninguna instancia en pie**. El frontend consume un contrato versionado ([`ADR-001`](ADR-001-publicacion-del-contrato-openapi.md)) contra un backend que no existe en ninguna dirección.

Lo que el proyecto necesita **hoy** es modesto y conviene decirlo antes de comparar nada: **una instancia, una base de datos, HTTPS y despliegue automático desde la rama**. No hay tráfico, no hay usuarios, no hay requisito de disponibilidad, y el sistema es explícitamente de una sola réplica por decisiones ya tomadas y documentadas. Elegir infraestructura para la carga que no hay es la forma más común de no desplegar nunca.

## Decisión

**El backend se despliega en Railway, un servicio por entorno de los dos que exige el Art. IX.4, construido desde el `Dockerfile` del repositorio y con PostgreSQL 17 gestionado por la misma plataforma.**

Cinco piezas:

1. **Un proyecto de Railway con dos entornos**, `testing` y `production`, cada uno con su servicio `backend` y su servicio `Postgres` propios. No se comparte base, ni secreto de firma, ni credencial de proveedor.
2. **La imagen es la del repositorio.** Railway construye con el `Dockerfile` que ya existe —dos etapas, JRE 21, usuario sin privilegios— y no con un constructor propio. El artefacto que corre desplegado es el que se prueba en local y en CI.
3. **Desplegar es integrar.** Cada entorno vigila una rama —`develop` y `main`— y despliega al recibir un commit. No hay acción manual de despliegue que alguien pueda olvidar o disparar por su cuenta, y el Art. XI.2 —`main` siempre desplegable— deja de ser una convención.
4. **La configuración del servicio se versiona** en `railway.json`: constructor, sonda de salud, política de reinicio y **`numReplicas: 1`**. Los secretos, no (Art. IV.3).
5. **El procedimiento vive en [`deployment.md`](../deployment.md)**, no en la interfaz de la plataforma ni en la cabeza de quien lo hizo la primera vez (Art. X.4).

**Lo que esta decisión NO es:** no es la arquitectura definitiva de producción del producto. Es la infraestructura correcta para el estado actual —un sistema en construcción, sin usuarios, de una sola réplica— y está pensada para poder abandonarse (ver §Consecuencias).

## Alternativas consideradas

| Alternativa | Por qué se descarta |
|---|---|
| **Render** | Equivalente en casi todo y perfectamente defendible. Se descarta por un motivo pequeño y real: su capa gratuita **duerme el servicio por inactividad**, y este backend aplica migraciones al arrancar — un despertar frío tras un despliegue nuevo tarda minutos, y el primer visitante se encuentra un `502`. En un entorno que existe para que alguien lo pruebe, eso es peor que el coste que ahorra |
| **Fly.io** | Más control —regiones, escalado, red privada de verdad— a cambio de administrar la máquina, los volúmenes y la base de datos, que en Fly no es un servicio gestionado sino un Postgres que **se administra uno mismo**. Ese trabajo lo hace hoy una persona, y es exactamente el trabajo que este proyecto no necesita todavía |
| **AWS (ECS/Fargate + RDS)** | Es la salida cuando haya carga, cumplimiento o red que aislar. Hoy cuesta semanas de infraestructura —VPC, IAM, ALB, RDS, secretos, un pipeline propio— para desplegar un proceso y una base. **Se pospone, no se descarta**: la condición de revisión está en §Consecuencias |
| **VPS con `docker-compose.yml`** | Barato y tentador, y es la peor de las opciones aquí: convierte el archivo de desarrollo en infraestructura de producción, con sus credenciales versionadas, y traslada a una persona el sistema operativo, los certificados, las copias de seguridad y las actualizaciones. El Art. X.2 declara Compose para el **entorno local**, y ampliarlo sería exactamente el «en mi máquina funciona» que ese artículo existe para prohibir |
| **Kubernetes gestionado** | Resuelve el escalado horizontal, que es un problema que este sistema **no puede tener todavía**: tres de sus componentes guardan estado en memoria del proceso. Desplegar en Kubernetes con una sola réplica es pagar toda la complejidad y no cobrar ninguna ventaja |
| **No desplegar aún**, esperar a resolver las réplicas | Es lo que se ha hecho durante ocho días y lo que convirtió a D-09 en el aparcadero de cinco pendientes distintos. Las tres piezas en memoria están **detrás de puertos** y sustituirlas no toca ningún caso de uso: se pueden arreglar con el sistema ya desplegado |

## Consecuencias

### Lo que se gana

- **Hay una dirección.** El frontend deja de integrar contra un contrato sin implementación, y `testing` deja de ser una palabra en la constitución.
- **El despliegue es reproducible y está escrito** (Art. X.4): un entorno nuevo se levanta siguiendo `deployment.md`, sin pasos no documentados.
- **La construcción es la del repositorio.** No hay un segundo Dockerfile, ni un constructor de la plataforma que interprete el proyecto a su manera.
- **La configuración del servicio se revisa en el diff**, como cualquier otro cambio, porque vive en `railway.json`.

### Lo que cuesta

- **Dependencia de un proveedor para la base de datos.** La copia de seguridad, la versión del motor y la restauración las gobierna Railway. Se acepta con una obligación explícita en `deployment.md` §12: **verificar que una restauración funciona antes de que haya datos reales**. Una copia que nadie ha restaurado nunca no es una copia.
- **La red privada de Railway es solo IPv6**, y eso tiene consecuencias para una JVM que hay que conocer antes de que fallen (`deployment.md` §7.2).
- **La plataforma elige el puerto.** Se resolvió el mismo día haciendo que la aplicación lo obedezca (`server.port: ${PORT:8080}`) en lugar de fijar la variable a mano, que era un acoplamiento frágil. El coste que queda es el de siempre: el puerto ya no está escrito en ninguna parte del repositorio, y hay que ir a `deployment.md` §7.1 para saber de dónde sale.
- **`main` en producción en minutos.** Con auto-despliegue, un merge equivocado es un despliegue equivocado. Es la contrapartida honesta de no tener un botón que alguien olvide pulsar.

### La consecuencia que hay que mirar de frente

**D-21 no se cierra con esta decisión; cambia de forma, y a peor.**

Se creía que era «qué IPs poner en `TRUSTED_PROXIES` en cada entorno». Al aterrizar sobre Railway resulta que **no hay ninguna que poner**: `ClientIpResolver` compara la IP del par inmediato contra un conjunto de **coincidencia exacta**, sin rangos ni CIDR, y la dirección con la que el borde de Railway habla con el contenedor no es fija ni publicada.

Con la lista vacía, el resolvedor hace lo correcto —ignora `X-Forwarded-For` y registra la IP del socket—, de modo que **la auditoría apunta al proxy**: un dato incompleto pero cierto, en lugar de uno que el atacante elige escribiendo una cabecera. Esa es la propiedad que se conserva y no es poca.

Lo que se pierde, y no debe darse por resuelto: **el Art. V.15 —desde dónde se hizo cada operación— no se cumple en un despliegue en Railway**. La salida no es una lista de IPs sino **un cambio en `ClientIpResolver` para que admita rangos**, y con él la posibilidad de confiar en la red del proveedor. Queda registrado en `security.md` §12 y en `deployment.md` §13.

**Lo que esta decisión NO autoriza:**

- **Subir `numReplicas`.** Las tres piezas en memoria de `deployment.md` §2.1 lo impiden, y ninguna falla de forma visible al hacerlo.
- **Abrir `EXPOSE_API_DOCS` en un entorno desplegado.** Sigue en `false`, por lo que ya argumenta [`ADR-001`](ADR-001-publicacion-del-contrato-openapi.md).
- **Copiar valores del `docker-compose.yml`.** Son credenciales de desarrollo en un archivo versionado.

**Condición de revisión.** Esta decisión se reabre cuando ocurra cualquiera de estas cuatro cosas, y conviene que estén escritas para no discutirlas en caliente:

1. El sistema necesita **más de una instancia** y las tres piezas en memoria ya se resolvieron.
2. Aparece un requisito de **cumplimiento, residencia de datos o aislamiento de red** que Railway no cubra.
3. La **auditoría de IP** pasa a ser exigible de verdad y el proveedor no permite identificar su borde.
4. El coste operativo deja de ser marginal frente al de administrar la alternativa.

La salida prevista en los cuatro casos es la de §Alternativas: **AWS con ECS/Fargate y RDS**. La portabilidad está preservada por construcción —el artefacto es una imagen Docker y toda la configuración entra por variables de entorno (Art. IX.1)—, de modo que migrar es rehacer infraestructura, no reescribir la aplicación.

## Seguimiento

| # | Pendiente | Dónde |
|---|---|---|
| ~~1~~ | ~~**`application.yml` debe leer `PORT`**~~ · **Hecho el 27-08-2026.** `server.port: ${PORT:8080}`: en un entorno desplegado manda la plataforma y en local siguen valiendo los 8080 del `Dockerfile` | `deployment.md` §7.1 |
| 2 | **Canal compartido detrás de `AccessRevocationPublisher`**, condición previa a cualquier segunda instancia | `security.md` §4.5 |
| 3 | **`ClientIpResolver` debe admitir rangos**, sin lo cual **D-21** no puede cerrarse en esta plataforma | `security.md` §12 |
| ~~4~~ | ~~**Apagado ordenado**~~ · **Hecho el 27-08-2026.** `server.shutdown: graceful` con treinta segundos de plazo. El relevo deja de cortar peticiones en curso, que para quien estaba escribiendo era una conexión caída y no un error | `deployment.md` §7.3 |
| 5 | **Raspado de métricas y alertas.** D-09 se cierra sin resolverlo: la plataforma no aporta permiso ni red de administración, y sigue haciendo falta uno de los dos | `architecture.md` §9.1 |
