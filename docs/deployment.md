# Despliegue — Backend NEXUS

| Campo | Valor |
|---|---|
| Proyecto | NEXUS — Renovación de plataforma |
| Empresa | FACTECH GROUP SAS |
| Documento | `deployment.md` |
| Versión | 0.26.0 |
| Estado | Borrador |
| Responsable técnico | Bonilla Diaz William Steven |
| Fecha de creación | 27-08-2026 |
| Última actualización | 14-10-2026 |
| Documento superior | `constitution.md` v0.7.0 |
| Documentos relacionados | `architecture.md` v0.51.0 · `security.md` v0.138.0 · [`ADR-007`](architecture/ADR-007-plataforma-de-despliegue-dokploy.md) |
| Documento derivado | [`manual-de-despliegue.md`](manual-de-despliegue.md) v0.3.0 — el paso a paso |

---

## 1. Propósito y alcance

Este documento responde una pregunta concreta: **cómo se lleva este backend a un entorno en ejecución que no es la máquina de nadie**. Es el procedimiento operativo del despliegue, no la justificación de la plataforma: por qué un VPS propio con Dokploy está en [`ADR-007`](architecture/ADR-007-plataforma-de-despliegue-dokploy.md), que sustituye el 14-10-2026 a [`ADR-002`](architecture/ADR-002-plataforma-de-despliegue-railway.md) —Railway—.

**Dentro del alcance:** los dos entornos desplegados que exige el Art. IX.4 —`testing` y `production`—, sobre Dokploy; el servidor y lo que hay que endurecer en él; la configuración del servicio que Dokploy guarda fuera del repositorio; el mapa completo de variables de entorno con el valor exacto que va en cada una; los detalles de la plataforma que rompen el arranque o el relevo si nadie los mira; la publicación de la imagen desde CI; la verificación posterior; y la operación del día a día —logs, redespliegue, reversión, copias, acceso a la base—.

**Fuera del alcance:** el entorno local, que vive en [`development-guide.md` §2](development-guide.md#2-puesta-en-marcha-del-entorno-local) y en el `docker-compose.yml`; el despliegue del frontend, que es otro repositorio; y la publicación del sitio de documentación, que la hace GitHub Pages desde `.github/workflows/docs.yml` y no tiene nada que ver con esto.

!!! tip "¿Vas a desplegar ahora mismo? Usa el [manual](manual-de-despliegue.md)"

    Este documento es la **referencia**: qué es cada cosa y por qué. Para hacerlo, con el panel de Dokploy abierto delante, el [**manual de despliegue**](manual-de-despliegue.md) lleva los pasos en orden, con los comandos exactos y el bloque de variables listo para pegar.

    Si los dos se contradicen, manda este.

!!! warning "El artefacto es el mismo; lo único que cambia es la configuración"

    El Art. IX.1 no admite excepciones aquí: **no hay perfil de Spring por entorno, ni `application-production.yml`, ni una rama con valores distintos**. La imagen que corre en `production` es la misma que corre en `testing` y la que se construye en local. Todo lo que las distingue son las variables de §6.

    Si alguna vez hace falta un cambio de comportamiento que no quepa en una variable, eso es una enmienda a la arquitectura, no un archivo nuevo.

---

## 2. Qué se despliega

Un servidor con Dokploy, y en él un proyecto, `nexus`, con un entorno de Dokploy por cada entorno del sistema. En cada uno, dos servicios de este repositorio —la aplicación y su base— y el frontend, que es del otro:

```mermaid
graph LR
    N["Navegador / Frontend"] -->|HTTPS| T["<b>Traefik</b><br/>TLS de Let's Encrypt<br/>contenedor de Dokploy"]
    T -->|"HTTP :8080<br/>red dokploy-network"| A["Servicio <b>backend</b><br/>imagen de GHCR<br/><b>1 réplica</b>"]
    A -->|"JDBC · red interna"| P[("Base <b>postgres</b><br/>PostgreSQL 17<br/>volumen del servidor")]
    P -.->|"copia programada"| S[("Almacenamiento S3<br/>fuera del servidor")]
    G["GitHub Actions<br/>CI en verde"] -->|"publica la imagen"| R["GHCR<br/>ghcr.io/nexuspro-dev/backend"]
    G -.->|"API de Dokploy:<br/>desplegar"| A
    R -.->|"descarga"| A
```

| Pieza | Qué es | De dónde sale |
|---|---|---|
| Servicio **backend** | La aplicación: una *Application* de Dokploy con proveedor **Docker**, que ejecuta la imagen ya construida como servicio de Swarm. **Dokploy no compila nada** | La imagen la construye y publica el CI (§10) con el `Dockerfile` del raíz —dos etapas, JRE 21, usuario sin privilegios, sonda de salud incluida— |
| Base **postgres** | PostgreSQL 17 como servicio de base de datos de Dokploy, con su volumen en el servidor y copias programadas a un almacenamiento S3 (§12) | El catálogo de bases de datos de Dokploy, con la imagen `postgres:17` |
| **Traefik** | El borde: TLS, certificado y dominio. No hay nada que configurar en la aplicación para esto | Lo instala Dokploy |
| **GHCR** | El registro de las imágenes, con una etiqueta por rama y otra por commit | GitHub, en la organización `NexusPro-Dev` |

**No hay un tercer servicio del backend.** Adminer es exclusivamente local; el `docker-compose.yml` **no interviene en ningún despliegue** y sus valores no deben copiarse a Dokploy: son credenciales de desarrollo escritas en un archivo versionado, que es justo lo que el Art. IV.3 prohíbe fuera de local.

### 2.1 Una sola réplica, y no es un ajuste de coste

El servicio backend corre con **una réplica**, fijada en la configuración del servicio en Dokploy (§5.3). Swarm deja subirla con cambiar un número en el panel, y por eso mismo hay que tenerlo presente: subirla rompe tres comportamientos que hoy son correctos, y ninguno de los tres falla de forma visible:

| Componente | Qué guarda en memoria del proceso | Qué pasa con dos réplicas |
|---|---|---|
| `AccessRevocationRegistry` | El corte de los tokens de acceso ya emitidos | Desactivar o eliminar a alguien **solo corta en la réplica que atendió la petición**. En la otra, su token sigue abriendo puertas hasta quince minutos (`security.md` §4.5) |
| `RateLimitLedger` | La cuenta de peticiones por origen y por identidad | El techo real **se multiplica por el número de réplicas**: con tres, diez intentos por minuto son treinta (`security.md` §5.5) |
| `FailedAttemptLedger` | Los fallos de identificadores sin cuenta | La respuesta vuelve a distinguir por tiempo qué identificadores existen, que es lo que ese componente existe para impedir |

La purga de sesiones **sí** está preparada para varias réplicas —toma un cerrojo de aviso en el motor— y Flyway también toma el suyo. Los tres de la tabla, no.

Escalar exige, antes, sustituir el registro en memoria por un canal compartido detrás del puerto `AccessRevocationPublisher`. Está registrado como pendiente en §13 y **no toca ningún caso de uso**.

---

## 3. Precondiciones

Antes de crear nada en Dokploy:

- [ ] La rama que se va a desplegar tiene **CI en verde** (`.github/workflows/ci.yml`). Con esta plataforma ya no es solo una regla: **sin CI en verde no se publica la imagen** (§10), de modo que no hay nada que desplegar.
- [ ] `mvn verify` pasa en local. Si falla aquí, falla en el CI igual y se descubre diez minutos más tarde.
- [ ] Existe el **servidor**, con Dokploy instalado y endurecido (§5.1).
- [ ] Existe un **almacenamiento compatible con S3** para las copias de la base —Cloudflare R2, Backblaze B2, el del propio proveedor del VPS—, **fuera del servidor**.
- [ ] Hay un **dominio** con acceso a su DNS: uno para el panel de Dokploy y uno por entorno para la API.
- [ ] Están generados los **dos secretos** de §4. Sin ellos el despliegue no arranca, y es intencionado (Art. IX.5).
- [ ] Está decidido **qué dominio** consumirá la API desde el navegador, porque de él depende `CORS_ALLOWED_ORIGINS` (§9). Si todavía no hay frontend desplegado, la variable va **vacía**: eso deja la API perfectamente utilizable de servidor a servidor y cerrada al navegador, que es el valor seguro.

---

## 4. Los dos secretos que hay que generar antes

Ninguno de los dos tiene valor por defecto. Un despliegue que no los declare **falla al arrancar**, en lugar de arrancar con algo conocido (Art. IX.5).

### 4.1 `JWT_SECRET`

Secreto de firma de los tokens de acceso. **Distinto en cada entorno**: compartirlo entre `testing` y `production` significa que un token emitido en pruebas es válido en producción.

```bash
openssl rand -base64 48
```

Se pega tal cual en la variable. No se guarda en ningún archivo del repositorio, ni en un comentario, ni en un mensaje de chat (`security.md` §7.1). Si alguna vez se expone, **se rota**: borrarlo de donde estuviera no lo vuelve seguro.

Rotarlo invalida todos los tokens de acceso vivos —quince minutos como mucho— y obliga a volver a autenticarse. Es una operación aceptable y debe poder hacerse sin miedo.

#### 4.1.1 `MFA_ENCRYPTION_KEY`

La llave que cifra el **secreto del segundo factor** de cada persona ([`security.md` §3.3](security.md)). Desde el 06-10-2026, con `V75`.

```bash
openssl rand -base64 32
```

Treinta y dos bytes en Base64, **distinta en cada entorno** y obligatoria: **sin ella el servicio no arranca**, porque no hay forma segura de guardar el factor de nadie. Al revés que `JWT_SECRET`, **no se rota a la ligera**: los secretos guardados solo se descifran con la llave que los cifró, de modo que cambiarla —o perderla— deja a todo el mundo sin poder usar su authenticator, y habría que restablecerlos uno a uno (`RF-SP-076`). Se guarda y se respalda **junto con `JWT_SECRET`**.

!!! warning "Desde `RF-SP-072`, el superadministrador activa su segundo factor en el primer inicio de sesión"

    `SUPERADMIN` y `ADMIN` exigen el segundo factor desde `V75` (`RN-SP-062`). Tras desplegar, **quien entre con esos roles y no lo tenga activo queda retenido**: el sistema solo le deja activarlo (`POST /api/v1/users/me/mfa/totp` y su confirmación), ver su perfil y cerrar sesión. Hace falta **el teléfono con una app autenticadora a mano** en ese primer inicio, y **guardar los diez códigos de recuperación** que entrega la confirmación: si se pierden el teléfono y los códigos del último superadministrador, la recuperación ya no es por la API (`RF-SP-076`).

### 4.2 `SUPERADMIN_PASSWORD_HASH`

La contraseña del superadministrador inicial, **ya cifrada con Argon2id**. La siembra `V9__semilla_catalogos_y_superadmin.sql` por marcador de posición de Flyway: la migración recibe el resumen, nunca la contraseña.

`RN-SP-001` convierte al superadministrador en obligación permanente, y el primero **no puede crearse por la API** —haría falta un actor con `users:create`, que es exactamente lo que aún no existe—, de modo que este valor es el único camino de entrada a un sistema recién desplegado.

**Cómo se genera**, con los mismos parámetros que usa la aplicación y sin instalar nada que no esté ya:

```bash
# 1. Volcar el classpath del proyecto (trae Spring Security y BouncyCastle)
mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt

# 2. Abrir jshell con ese classpath
jshell --class-path "$(cat cp.txt)"
```

Y dentro de `jshell`:

```java
var encoder = new org.springframework.security.crypto.argon2.Argon2PasswordEncoder(16, 32, 1, 16384, 2);
System.out.println(encoder.encode("LA-CONTRASENA-REAL-AQUI"));
```

Los cinco números son los de `application.yml` —`salt-length`, `hash-length`, `parallelism`, `memory-kb`, `iterations`—. Aun así, **el resumen lleva sus propios parámetros dentro**: si mañana se endurecen, este hash se sigue verificando y solo se recifra cuando su titular cambie la contraseña.

Borrar `cp.txt` al terminar, y **no dejar la contraseña en el historial de la terminal**.

!!! danger "En Dokploy los `$` van tal cual — NO se duplican"

    El `.env.example` avisa de que en un archivo `.env` los `$` del hash deben ir **duplicados**, porque Docker Compose interpola el archivo y convierte el resumen en un resto irreconocible. **Ese problema es exclusivo de Docker Compose.**

    Dokploy lee sus variables con el analizador de `dotenv`, que **no expande `$`**, y solo sustituye su propia sintaxis de referencias, `${{…}}`, que un hash Argon2 no contiene. El valor llega al servicio tal cual: el hash va **exactamente como lo imprimió `jshell`**, empezando por `$argon2id$v=19$m=16384,t=2,p=1$`. Comprobado en el código de Dokploy (`prepareEnvironmentVariables`), no supuesto.

    Duplicarlos aquí produce el fallo simétrico y silencioso: la migración termina con éxito y el superadministrador **no puede entrar nunca**, con un mensaje genérico de credenciales inválidas. La guarda de `V9` detecta el caso contrario —los `$` comidos—, no este.

**Comprobación inmediata:** el valor debe empezar por `$argon2id`. Si no, `V9` aborta la migración con un mensaje explícito y el despliegue no arranca — que es lo que debe pasar.

---

## 5. El servidor y los servicios en Dokploy

El servidor se prepara una vez. Los servicios se crean una vez **por entorno**, y el orden importa: la base primero, porque el backend necesita su dirección interna.

### 5.1 El servidor

| Qué | Valor | Por qué |
|---|---|---|
| Sistema | **Ubuntu 24.04 LTS** | Es la primera de la lista de sistemas probados por Dokploy y tiene soporte hasta 2029 |
| Tamaño mínimo | **4 vCPU · 8 GB de RAM · 80 GB NVMe** | Dos entornos con sus límites de §5.4 y el propio Dokploy suman cerca de 6 GB. Dokploy pide 2 GB y 30 GB para sí mismo |
| Región | La más cercana a los usuarios que ofrezca el proveedor | La latencia hasta la API la paga cada petición del navegador |
| Instalación | `curl -sSL https://dokploy.com/install.sh \| sh`, como `root` | Instala Docker si falta, inicia Swarm, crea la red `dokploy-network` y levanta el panel, su base y Traefik |

**Endurecerlo no es opcional**, porque ahora el sistema operativo es nuestro ([`ADR-007`](architecture/ADR-007-plataforma-de-despliegue-dokploy.md)):

- **Firewall del proveedor** delante del servidor, abriendo solo `22`, `80` y `443` —y `3000` mientras el panel no tenga dominio—. **Tiene que ser el del proveedor y no UFW**: Docker publica puertos escribiendo sus propias reglas de `iptables`, que pasan por delante de las de UFW, de modo que un `ufw deny` no cierra lo que Docker abre.
- **SSH solo con llave**, sin contraseña y sin `root` por contraseña.
- **Actualizaciones de seguridad automáticas**: `unattended-upgrades`.
- **El panel con dominio propio y HTTPS** (*Settings → Server* en Dokploy), **doble factor** en la cuenta de administración, y el puerto `3000` cerrado en el firewall en cuanto el dominio responda. El panel guarda todos los secretos de los dos entornos.

### 5.2 Los servicios de cada entorno

Un proyecto, **`nexus`**, con dos entornos de Dokploy, **`testing`** y **`production`**. En cada uno:

| Servicio | Tipo en Dokploy | Origen |
|---|---|---|
| **`postgres`** | *Database* → PostgreSQL | Imagen **`postgres:17`**. Dokploy genera usuario y contraseña; el nombre de la base es `nexus` |
| **`backend`** | *Application*, proveedor **Docker** | Imagen `ghcr.io/nexuspro-dev/backend:develop` en `testing`, `…:main` en `production`. Registro `ghcr.io` con un token de GitHub de solo lectura de paquetes (`read:packages`), porque el paquete es privado |
| **`frontend`** | *Application*, proveedor **Docker** | Imagen `ghcr.io/nexuspro-dev/frontend:<rama>`. Se configura según el `deployment.md` del frontend |

**PostgreSQL 17 exactamente**, no la última: el Art. V.1 y V.2 admiten un solo motor y el SQL usa características propias de él; desplegar sobre una línea distinta de la que se prueba en local y en CI con Testcontainers no es una diferencia de detalle.

No hay que crear tablas: el esquema lo aplica **Flyway al arrancar la aplicación** (§8). **No hay `Command`** en el servicio: el `ENTRYPOINT` de la imagen ya es `java -jar /app/app.jar`.

### 5.3 La configuración del servicio `backend`

Railway la leía de `railway.json`. **Dokploy la guarda en su propia base y no la lee de ningún archivo del repositorio**, de modo que este apartado es la única copia versionada (Art. X.3, X.4). Un valor cambiado en el panel y no aquí es un defecto de este documento.

| Dónde, en Dokploy | Qué | Valor | Por qué |
|---|---|---|---|
| *Advanced → Cluster Settings* | Réplicas | **`1`** | §2.1. Swarm no lo impide; lo impide el diseño |
| *Swarm Settings → Update Config* | `Order` | **`start-first`** | Levanta la versión nueva y espera a que su sonda responda **antes** de parar la vieja. Es el relevo que §7.3 describe. Con `stop-first`, que es el valor de Swarm por defecto, cada despliegue deja la API caída durante todo el arranque |
| | `FailureAction` | **`rollback`** | Si la versión nueva no llega a estar sana, Swarm vuelve a la anterior por su cuenta |
| | `Parallelism` · `Monitor` | `1` · `30s` | Una tarea cada vez; y treinta segundos vigilando la nueva antes de dar el relevo por bueno |
| *Swarm Settings* | **Stop Grace Period** | **`35s`** | **Swarm manda `SIGKILL` a los diez segundos por defecto**, y el apagado ordenado de la aplicación tiene treinta (§7.3). Sin esto, el relevo corta las peticiones en curso, que es justo lo que `server.shutdown: graceful` existe para impedir |
| *Swarm Settings → Health Check* | — | **Vacío** | La sonda viaja en el `HEALTHCHECK` del `Dockerfile` —`/actuator/health/readiness`, cada diez segundos, con trescientos de gracia para el primer arranque— y Swarm la obedece sin más. Declararla aquí la sustituiría por una copia que nadie revisa en un diff |
| *Advanced → Resources* | Límite de memoria | **2 GB** en `production` · **1 GB** en `testing` | Lo que separa un entorno del otro en el mismo servidor (§5.4) |
| *Domains* | Dominio · puerto · HTTPS | El de la API del entorno · **`8080`** · Let's Encrypt | §9 |
| *Advanced → Ports* | — | **Ninguno** | El tráfico entra por Traefik. Publicar el `8080` en el anfitrión saltaría el TLS |

Los formularios de *Swarm Settings* escriben la estructura del API de Docker (`UpdateConfig`, `HealthCheck`…), y los plazos que se teclean como JSON van **en nanosegundos**: treinta y cinco segundos son `35000000000`.

**Lo que esta configuración no lleva son las variables**, y no es un olvido: son secretos, y el Art. IV.3 los mantiene fuera del repositorio en toda forma. Van en *Environment* (§6).

### 5.4 La memoria, en un servidor compartido

Los dos entornos comparten servidor, de modo que **los límites son lo único que impide que `testing` deje a `production` sin memoria**.

| Servicio | `production` | `testing` |
|---|---|---|
| `backend` | 2 GB | 1 GB |
| `postgres` | 1 GB | 512 MB |
| `frontend` | 128 MB | 128 MB |

Con los límites puestos, **la JVM tiene que saber cuánto le toca**. Java 21 lee el límite del contenedor, pero por defecto solo dedica al montón **una cuarta parte**: con 2 GB, medio gigabyte. Por eso `JAVA_TOOL_OPTIONS` lleva `-XX:MaxRAMPercentage=60` (§6.4), que deja el resto para el metaespacio, los hilos y la memoria nativa.

Lo que más memoria pide en un pico es **el inicio de sesión**: Argon2id con `memory-kb` 16384 reserva **16 MB por cada verificación en curso**. Cincuenta inicios de sesión simultáneos son unos 800 MB de montón durante un instante. Si la carga real se acerca a eso, el límite de 2 GB es lo primero que se revisa.

---

## 6. Variables de entorno

Se cargan en *Environment* del servicio **`backend`**, en el formato de un archivo `.env`: una `CLAVE=valor` por línea. Al guardarlas, Dokploy no reinicia nada; **hay que desplegar** para que el servicio las tome.

### 6.1 Base de datos

| Variable | Valor |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://<Internal Host>:5432/nexus` |
| `DATABASE_USER` | El *User* del servicio `postgres` |
| `DATABASE_PASSWORD` | Su *Password* |

El *Internal Host* es el nombre con el que el servicio `postgres` se alcanza dentro de la red de Swarm, y Dokploy lo muestra en *Internal Credentials* del propio servicio —un nombre del estilo `nexus-postgres-a1b2c3`—. **Es distinto en cada entorno**, igual que el usuario y la contraseña.

!!! danger "Dokploy enseña dos direcciones de la base, y la que va aquí es la de JDBC construida a mano"

    *Internal Credentials* ofrece una **Internal Connection URL** con la forma `postgresql://usuario:clave@host:5432/base`. Es una URI de conexión, no una URL de JDBC.

    Esta aplicación espera en `DATABASE_URL` la forma **`jdbc:postgresql://host:puerto/base`, sin credenciales dentro**, porque el usuario y la contraseña van por separado (`application.yml`).

    Pegar la URI de Dokploy **no funciona**: Spring no reconoce el esquema `postgresql://` y el arranque muere con un error de driver que no menciona nada de esto. Hay que construir la cadena como muestra la tabla, con el *Internal Host*.

    Y **nunca la *External* URL**: exige publicar el puerto de la base en el anfitrión, que este despliegue no hace (§12).

### 6.2 Red, puerto y entorno

| Variable | Valor | Por qué |
|---|---|---|
| `PORT` | **No declararla** | Sin ella la aplicación escucha en `8080`, que es el puerto que el dominio de §5.3 apunta (§7.1) |
| `ENVIRONMENT` | `production` o `testing` | **Obligatoria de verdad desde el 31-08-2026: sin ella el servicio NO ARRANCA.** Decide si se siembran datos de prueba. Ver §6.6 |
| `DEV_SEED_ENABLED` | **No declararla** | La semilla no se aplica en `production` lo diga esta lo que diga. En `testing`, ponerla en `false` es lo que la desactiva |
| `API_URL` | La URL pública del servicio, sin barra final | Art. IX.1. Declarada y todavía sin lector, ver §6.6 |

### 6.3 Seguridad

| Variable | Valor | Por qué |
|---|---|---|
| `JWT_SECRET` | El secreto de §4.1 | Obligatoria y sin valor por defecto |
| `MFA_ENCRYPTION_KEY` | La llave de §4.1.1 | Obligatoria y sin valor por defecto. **No se cambia**: cambiarla deja ilegibles los segundos factores |
| `SUPERADMIN_EMAIL` | El correo real del superadministrador | Solo actúa en el primer arranque (§8.2) |
| `SUPERADMIN_PASSWORD_HASH` | El resumen de §4.2, con los `$` **sin duplicar** | Ídem |
| `CORS_ALLOWED_ORIGINS` | El dominio del frontend, o **vacío** | Ver §9. El comodín `*` tumba el arranque |
| `EXPOSE_API_DOCS` | **`false`** en los dos entornos | Ver el aviso de abajo |
| `TRUSTED_PROXIES` | La subred de `dokploy-network`, p. ej. `10.0.1.0/24` | La que dé `docker network inspect` en el servidor, **no una más ancha**. Ver §11.2 (D-21) |
| `RATE_LIMIT_ENABLED` | `true`, o no declararla | Su valor por defecto ya es `true`. **Solo la suite la apaga** |

!!! warning "`EXPOSE_API_DOCS` va en `false`, y el contrato publicado no es razón para cambiarlo"

    Desde [`ADR-001`](architecture/ADR-001-publicacion-del-contrato-openapi.md) el contrato se versiona en `docs/api/openapi.json`, de modo que el frontend no necesita que ningún entorno tenga Swagger abierto. Ese ADR lo deja escrito: que el contrato sea legible en el repositorio **no autoriza** dejar Swagger abierto en un entorno en ejecución, donde además invita a probar contra datos reales.

### 6.4 Operación

| Variable | Valor | Por qué |
|---|---|---|
| `LOG_LEVEL` | `INFO` | Subirlo a `DEBUG` en un entorno desplegado emite contenido que el enmascaramiento de `security.md` §7.3 no cubre |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=60` | Sin ella la JVM solo usa para el montón una cuarta parte del límite de memoria del servicio (§5.4). La JVM imprime al arrancar `Picked up JAVA_TOOL_OPTIONS…`: es la confirmación de que la leyó, no un error |
| `TOKEN_PURGE_ENABLED` | `true`, o no declararla | Sin ella `refresh_tokens` crece de forma monótona |
| `TOKEN_PURGE_CRON` | No declararla | El valor por defecto —`0 30 3 * * *`, **en UTC**— sirve |
| `TOKEN_PURGE_RETENTION` | No declararla | `P30D`. Acortarlo apaga la detección de robo por reutilización de `RF-SP-035` |
| `REQUEST_LOG_RETENTION_DAYS` | **Vacía** | **Hoy no la lee nadie** (§13). Ponerle un número no purga nada y hace creer que sí |

### 6.5 Envío saliente

| Variable | Valor | Por qué |
|---|---|---|
| `NOTIFICATION_ENABLED` | `true` en los entornos donde deba funcionar la recuperación de contraseña | Con `false`, `RF-SP-040` emite permisos **que nadie recibe** |
| `RESEND_API_KEY` | La clave `re_…` de Resend | Sin ella el envío queda apagado y se avisa **al arrancar** |
| `NOTIFICATION_FROM` | `NEXUS <no-responder@dominio-verificado>` | El remitente **debe pertenecer a un dominio verificado en Resend** o el proveedor responde `403` |
| `NOTIFICATION_TEMPLATE_PASSWORD_RECOVERY` | `nexus-password-recovery` | Plantilla con estilos del correo de `RF-SP-040`. **Vacía** manda el mensaje en texto plano; **con un valor que no exista** el proveedor rechaza el envío y el correo se pierde |

!!! tip "Verificar el dominio en Resend antes de desplegar, no después"

    El `403` por dominio no verificado **no se ve al arrancar**: la aplicación levanta con normalidad y el fallo aparece la primera vez que alguien olvida su contraseña — que es exactamente cuando nadie está mirando. Y no hay reintento propio: ese mensaje se pierde y solo queda su registro.

### 6.5.1 La pasarela de la tarjeta (Stripe)

| Variable | Valor | Por qué |
|---|---|---|
| `STRIPE_SECRET_KEY` | Una **clave restringida** de Stripe (`rk_test_…` en pruebas, `rk_live_…` en producción), con permiso de escritura solo sobre los cobros (*PaymentIntents*) | Sin ella la pasarela queda **apagada** y se avisa al arrancar: la tarjeta nace pendiente y la confirma una persona. **Restringida y no la secreta completa**: si se filtra, no abre la cuenta entera |
| `STRIPE_WEBHOOK_SECRET` | El `whsec_…` del endpoint registrado en el panel de Stripe —**uno por entorno**— | Verifica la firma de cada notificación. Sin él la ruta responde `503` y no procesa nada |

**El endpoint se registra en el panel de Stripe** apuntando a `https://<dominio del backend>/api/v1/movements/gateway-notifications/stripe`, suscrito como mínimo a `payment_intent.succeeded`, `payment_intent.payment_failed`, `payment_intent.canceled`, `charge.refunded`, `charge.dispute.created` y `charge.dispute.closed`. **La clave publicable (`pk_…`) no es del backend**: la usa el frontend para pintar el formulario de la tarjeta.

**En local no hay endpoint registrado**, y sin él las notificaciones no llegan: el cobro sale `succeeded` en Stripe y la venta se queda `PENDIENTE` aquí. **Desde el 02-10-2026 las reenvía el servicio `stripe` del `docker-compose.yml`** (`nexus-stripe`, la Stripe CLI fijada a `v1.53.0`), que arranca con `docker compose up`, usa la misma `STRIPE_SECRET_KEY` que la aplicación y reenvía a `http://app:8080/api/v1/movements/gateway-notifications/stripe` los seis eventos de arriba. **El `whsec_…` que va en `STRIPE_WEBHOOK_SECRET` del `.env` es el que imprime `stripe listen --print-secret` con esa clave**, y es estable: no cambia de un arranque a otro. Sin `STRIPE_SECRET_KEY` el servicio se queda dormido y lo dice en su log, sin tumbar el resto. Lo que reenvía se ve en `docker logs nexus-stripe`, y un aviso perdido se recupera con `stripe events resend evt_…`. Con la aplicación fuera de Docker (`mvn spring-boot:run`) sigue valiendo correr a mano `stripe listen --forward-to localhost:8080/api/v1/movements/gateway-notifications/stripe`.

### 6.5.2 La pasarela local (PayRetailers)

| Variable | Valor | Por qué |
|---|---|---|
| `PAYRETAILERS_SUBSCRIPTION_KEY` | La *Subscription Key* de la API (`Ocp-Apim-Subscription-Key`), de la cuenta | Va en cada petición. **Sin ella, o sin una llave de cifrado válida, la pasarela queda apagada** y se avisa al arrancar |
| `PAYRETAILERS_ENCRYPTION_KEY` | 32 bytes en Base64, generados una vez por entorno: `openssl rand -base64 32` | Cifra las claves secretas de las tiendas en la base. Secreto: nunca en el repositorio ni en el chat. **No se cambia**: cambiarla deja ilegibles las claves guardadas y hay que volver a fijarlas |
| `PAYRETAILERS_BASE_URL` | `https://api-sandbox.payretailers.com/payments/v2` en pruebas; la de producción la da PayRetailers | **Por omisión, la del sandbox**: un entorno mal configurado prueba, no cobra |
| `PAYRETAILERS_NOTIFICATION_URL` | `https://<dominio del backend>/api/v1/movements/gateway-notifications/payretailers` | La dirección **pública** a la que PayRetailers envía los avisos; viaja en cada cobro |
| `PAYRETAILERS_RETURN_URL` | La página del frontend a la que vuelve el cliente al terminar, por ejemplo `https://<dominio del frontend>/pagos/resultado` | Solo devuelve al cliente: **no confirma nada** |
| `PAYRETAILERS_TEST_MODE` | `false` | Con `true` los cobros se marcan de prueba y PayRetailers no los procesa |
| `PAYRETAILERS_RECONCILE_CRON` | Por omisión `0 */5 * * * *`, cada cinco minutos | El barrido de los cobros pendientes (`RF-MV-050`) |
| `PAYRETAILERS_RECONCILE_AFTER` | Por omisión `PT10M` | Cuánto espera un cobro sin noticias antes de que el barrido pregunte por él |

**Las tiendas no van en el entorno.** PayRetailers da **una por país** —un *Shop ID* con su clave secreta, del menú *Shops* de su backoffice—, y **viven en la conversión del país** (`RN-MV-063`): un ADMIN las fija con `POST /api/v1/movements/conversion-rates`, mandando `shopId` y `secretKey` junto a los precios. La clave se guarda cifrada y **no se devuelve nunca**; la consulta solo dice si la hay (`shopSecretKeySet`). Las conversiones siguientes heredan la tienda si no se manda otra. **Un país cuya conversión no tenga tienda no puede pagar con `PSE`** (`409`).

**En local los avisos no llegan solos**: PayRetailers necesita una dirección pública. Para probarlos hace falta un túnel —ngrok o Cloudflare Tunnel— que publique el puerto `8080`, y poner su dirección en `PAYRETAILERS_NOTIFICATION_URL`. **Sin túnel el sistema sigue funcionando**: el barrido pregunta por los cobros pendientes y los confirma igual, solo que unos minutos más tarde.

### 6.5.3 Los avisos de los brokers

Lo que avisa cada broker se guarda tal como llega y **no se interpreta todavía** (`RF-SP-078`, `RN-SP-066`): esto es para ver qué mandan antes de escribir `RF-SP-054`.

**La dirección es UNA para todos los brokers** (`RN-SP-069`), sin el nombre del broker, y con **un solo secreto**. La dirección por nombre y sus tres secretos (`BROKER_TOKEN_*`) **se retiraron el 13-10-2026**: si quedan en las variables de algún entorno, se pueden borrar.

| Variable | Valor | Por qué |
|---|---|---|
| `BROKER_NOTIFICATION_TOKEN` | Un secreto aleatorio, **distinto por entorno**: `openssl rand -hex 24` | Autentica la dirección común. **Sin él, responde `503`** y no guarda nada |

La dirección que va en el panel de afiliados de **cualquier** broker:

```
https://<dominio del backend>/api/v1/brokers/notifications?token=<BROKER_NOTIFICATION_TOKEN>&advertiser={advertiser}&…
```

**El broker lo dice el `advertiser` del aviso** —IQ Option lo manda: `advertiser=iq_option`—, comparado con `brokers.advertiser`. Hoy solo está el de `IQOPTION`. **Un aviso con el secreto correcto se guarda siempre** (`RN-SP-076`): si su `advertiser` no se reconoce —el de `EXNOVA` o `EXOPTION`, por ahora—, **se guarda sin broker y no se interpreta**. Para reconocerlo: mira el `advertiser` de esos avisos (consulta de abajo), cárgalo con una migración y despliega; **al arrancar**, la aplicación les asocia el broker y los interpreta en el orden en que llegaron.

**El aviso de registro crea la cuenta sin titular** (`RN-SP-072`, 09-10-2026). Qué aviso es el de registro lo dice el entorno:

| Variable | Valor | Por qué |
|---|---|---|
| `BROKER_REGISTRATION_EVENT` | El valor que el panel pone en `postback_name` para el registro (`BROKER_REGISTRATION_NEXUS_V2_EVENT` en IQ Option) | **Vacío = ningún aviso crea cuentas**: solo se guardan |
| `BROKER_DEPOSIT_EVENT` | El del depósito (`BROKER_DEPOSIT_NEXUS_V2_EVENT`) | **Vacío = ningún aviso confirma un FTD** (`RN-SP-073`) |
| `BROKER_OPERATION_EVENT` | El de la operación (`BROKER_OPERATION_NEXUS_V2_EVENT`) | **Vacío = no se cuenta ninguna** (`RN-SP-074`) |

De cada aviso se leen `trader_id` —el número de la cuenta—, `afftrack` —el del vendedor, solo en el registro— y `event_id` —el del aviso, para no aplicar dos veces una reentrega—. Si el panel los llama de otra forma, se cambian con `BROKER_FIELD_EVENT`, `BROKER_FIELD_ACCOUNT`, `BROKER_FIELD_AFFTRACK` y `BROKER_FIELD_EVENT_ID`. **El depósito confirma el FTD entero**: la cuenta pasa a `FIRST_DEPOSIT` y, si su titular espera en `FTD_PENDIENTE`, pasa a `ACTIVO` con lo que compró — configúralo cuando el panel ya mande depósitos reales.

**El secreto, en hexadecimal**, y no en Base64: va dentro de una dirección, y un `+` o un `/` habría que escaparlos en el panel. Va en el panel de afiliados de cada broker, en la sección de *postback* o S2S.

**Detrás del `token` se añaden los marcadores que ofrezca el panel**, con los nombres que el panel diga —por ejemplo `&event={event}&trader_id={trader_id}`—: todo lo que llegue se guarda. Vale `GET` y `POST`, con cualquier tipo de contenido.

**Ver lo que llegó**, desde la base (`psql`, como dice §12):

```sql
SELECT b.name, n.advertiser, n.received_at, n.method, n.query_params, n.headers, n.body,
       n.content_type
  FROM broker_notifications n
  LEFT JOIN brokers b ON b.id = n.broker_id
 ORDER BY n.received_at DESC
 LIMIT 50;
```

**Los que llegaron sin broker, por `advertiser`** —de aquí sale el valor que hay que cargar—:

```sql
SELECT advertiser, count(*), min(received_at), max(received_at)
  FROM broker_notifications
 WHERE broker_id IS NULL
 GROUP BY advertiser;
```

**Cambiar el secreto** es cambiar la variable y la dirección en los paneles; desde que la aplicación toma la nueva, los avisos con el viejo responden `401`. **En local no llegan solos**: como los de PayRetailers (§6.5.2), hace falta un túnel que publique el puerto `8080`.

### 6.5.4 Las clases en vivo (Zoom)

Las clases en vivo de la academia se programan en Zoom con una **app Server-to-Server OAuth** de la cuenta de la empresa (Workplace Business o superior: el registro de asistentes exige anfitrión con licencia).

**Crear la app**: en [marketplace.zoom.us](https://marketplace.zoom.us), con un usuario **Owner o Admin** (o con el permiso *Server-to-Server OAuth app* en su rol), *Develop › Build App › Server-to-Server OAuth App*. En *Scopes*: `meeting:write:meeting:admin`, `meeting:update:meeting:admin`, `meeting:delete:meeting:admin`, `meeting:read:meeting:admin` y `meeting:write:registrant:admin`. **Sin pulsar *Activate your app*, el token se rechaza.**

| Variable | Valor | Por qué |
|---|---|---|
| `ZOOM_ACCOUNT_ID` | El *Account ID* de *App Credentials* | Pide el token (`grant_type=account_credentials`) |
| `ZOOM_CLIENT_ID` | El *Client ID* | Igual |
| `ZOOM_CLIENT_SECRET` | El *Client Secret* | Igual. **Sin los tres, las clases en vivo no se pueden programar** |
| `ZOOM_HOST_USER` | `me` —quien creó la app— o el correo de un usuario **con licencia** de la cuenta | A su nombre se crean las reuniones; sin licencia, el registro de asistentes falla |

Una sola app sirve para todos los entornos; si se quiere separar, otra app con su par de credenciales.

### 6.6 `ENVIRONMENT` ya se lee; `API_URL` todavía no

**Desde el 31-08-2026 `ENVIRONMENT` decide algo, y por eso un valor equivocado tumba el arranque.**

Hasta esa fecha esta sección decía que ninguna clase la consultaba y que cambiarla a `production` no cambiaba ningún comportamiento. Ya no es cierto:

| Valor | Qué ocurre al arrancar |
|---|---|
| `production` | No se siembra nada. En el log queda la línea que lo dice |
| `testing` · `development` | Se aplican las **tres** semillas de `db/dev-seed/`, en orden y en una sola transacción: `semilla-desarrollo.sql` —**diecinueve personas de prueba** con sus roles, documento `CC`, teléfono, su estructura comercial —cada director con tres a cargo— y sus membresías; desde el 21-09-2026 **completa el documento a quien ya existía sin él**, porque la semilla solo crea a quien falta y una base sembrada antes conservaba a las diecinueve sin `CC`— y, desde el 14-09-2026, `semilla-productos.sql` —**un catálogo de dieciséis productos**: once upgrades (un salto desde cada membresía, las cuatro renovaciones con la de Beca a precio cero, y uno inactivo) y cinco bots (uno gratuito, uno con precio de compra declarado y uno retirado)—. Los activos nacen ya activos, con descripción, en USD y con alcances e implementaciones mezclados, para que la oferta, el hotlink, el catálogo administrativo y la entrega manual tengan algo que enseñar sin sembrar nada a mano. Y desde el 01-10-2026, `semilla-tasas-comision.sql` —**las tasas de comisión de rol** de esos productos: AGENTE, DIRECTOR y MANAGER en cada producto vivo, por porcentaje (10 / 7 / 5 % en los upgrades con precio y en `BOT_PRO_ANUAL`) o por importe fijo (dos bots y los dos gratuitos), y ninguna en el retirado— |
| Cualquier otra cosa, o ausente | **La aplicación NO ARRANCA**, nombrando el valor recibido y los tres admitidos |

!!! danger "Un despliegue sin `ENVIRONMENT` declarada deja de arrancar"

    Es un cambio de comportamiento, no un matiz. Antes la variable se ignoraba y el servicio levantaba igual; ahora su ausencia es un fallo de arranque (Art. IX.5). **Compruebe que está declarada en cada entorno antes de desplegar esto.**

    La alternativa —asumir un valor por defecto— es lo que ese artículo prohíbe, y aquí el precio de acertar mal es concreto: `Production`, `prod` o el vacío contarían todos como «no es producción» y sembrarían diecinueve cuentas en el sistema real.

**Qué son esas diecinueve personas, y por qué esto se mira dos veces.** Comparten el **hash de contraseña del superadministrador** y nacen **sin marca de cambio obligatorio**, al revés que cualquier alta por la API. En `development` es justo lo que se quiere. En `testing` —que es un entorno **desplegado y alcanzable**— hay que saber que quedan nombres de usuario adivinables (`admin1`, `cliente1`) sobre un host público; lo que **no** añade es una credencial nueva, porque quien pueda entrar con ellas ya podía entrar como SUPERADMIN.

**El guion viaja dentro del artefacto de producción**, y tiene que hacerlo: el classpath es el mismo para todos los entornos. Lo que lo separa de esas cuentas **no es la ausencia del archivo, es el guardia** — y por eso hay una prueba dedicada (`ProductionSeedIT`) que enciende el interruptor a propósito para comprobar que en producción no siembra igual.

`DEV_SEED_ENABLED` apaga la semilla sin tocar el entorno. Existe porque **la suite la apaga** —sus pruebas cuentan personas y roles—, y **no puede reabrir producción**: el entorno se comprueba primero y por separado.

**`API_URL` sigue declarada y sin lector.** Se mantiene por el Art. IX.4 y para que el día que algo la lea no se descubra que faltaba en producción.

---

## 7. Los tres detalles de la plataforma que rompen el arranque o el relevo

Ninguno de los tres es un error de la aplicación, y los tres producen síntomas que apuntan al sitio equivocado.

### 7.1 El puerto

**Dokploy no inyecta `PORT`.** El puerto lo decide el **dominio**: al declararlo en el servicio se indica a qué puerto del contenedor manda Traefik el tráfico, y ese puerto tiene que ser el que la aplicación escucha. `application.yml` declara `server.port: ${PORT:8080}`; sin la variable, escucha en `8080`, que es lo que el dominio de §5.3 apunta y lo que la sonda del `Dockerfile` consulta.

**No hay que declarar `PORT`** (§6.2). Si alguna vez se declara, hay que cambiar también el puerto del dominio: la sonda lo sigue sola —usa la misma variable—, Traefik no.

El síntoma de que no coincidan es el de siempre: **un `502` o `404` de Traefik sobre un arranque impecable en los logs**, con el servicio sano para Swarm — es decir, el puerto es lo último que uno mira.

### 7.2 La base puede no estar lista cuando arranca la aplicación

Cuando los dos servicios arrancan a la vez —la primera vez, o tras reiniciar el servidor—, la aplicación abre la conexión enseguida, porque Flyway corre al inicio, y puede encontrarse a PostgreSQL todavía levantando. El proceso muere.

**Es lo previsto, y lo resuelve Swarm**: su política de reinicio por defecto vuelve a arrancar la tarea, y el siguiente intento encuentra la base en pie. Uno o dos reinicios en el primer arranque no son un fallo; un bucle sí, y entonces el error de conexión del log dice cuál de los tres valores de §6.1 está mal.

La base se alcanza por su *Internal Host* en la red interna de Swarm, **en IPv4**. La advertencia que había aquí sobre la red solo IPv6 de Railway —y su `-Djava.net.preferIPv6Addresses=true`— **no aplica a esta plataforma**.

### 7.3 Durante un redespliegue conviven dos instancias

Con `Order: start-first` (§5.3), Swarm levanta la versión nueva, espera a que su sonda responda y solo entonces para la anterior. Durante ese relevo hay **dos procesos vivos**, aunque las réplicas sean una.

Qué implica, dicho sin adornos:

- **El esquema está a salvo.** Flyway toma un cerrojo en el motor: si las dos instancias intentan migrar, una espera y la otra aplica.
- **El estado en memoria se pierde.** Los cortes de acceso, las cuentas de límite de tasa y los fallos por identificador **empiezan de cero** en la instancia nueva. Los cortes sí se resiembran al arrancar; las otras dos cuentas, no. Es aceptable —la ventana es de segundos— y conviene saberlo antes de investigar por qué un contador se reinició solo.
- **Una migración incompatible hacia atrás rompe la instancia vieja** mientras siga sirviendo. Toda migración debe poder convivir con la versión anterior del código durante el relevo: añadir columnas anulables antes de usarlas, y separar en dos despliegues el «dejar de escribir» del «eliminar».
- **Si la versión nueva no llega a estar sana**, la vieja nunca se para: con `FailureAction: rollback`, Swarm retira la nueva y deja la anterior sirviendo. Un despliegue roto no tumba el entorno; se queda en el historial de Dokploy como fallido.

**El apagado ordenado sí está habilitado**, y es lo que hace tolerable el relevo: `server.shutdown: graceful` con treinta segundos de plazo (`spring.lifecycle.timeout-per-shutdown-phase`). Al recibir la orden de parar, la instancia vieja deja de aceptar peticiones nuevas y termina las que tiene, en lugar de cortarlas en seco — que para quien estaba escribiendo es una conexión caída, indistinguible de que el sistema esté roto.

**Pero solo si Swarm le da esos treinta segundos, y por defecto le da diez.** Pasado ese plazo manda `SIGKILL`, y el apagado ordenado queda a medias sin que ningún log lo diga. Por eso el *Stop Grace Period* del servicio es **35 segundos** (§5.3): los treinta de la aplicación y un margen. Treinta segundos sobran para cualquier operación de este sistema —los umbrales del Art. XV.9 son de menos de un segundo—.

---

## 8. El primer arranque

### 8.1 Migraciones

Flyway corre **dentro del proceso, al arrancar**, contra `classpath:db/migration`, con `validate-on-migrate` y **sin `baseline-on-migrate`**. No hay paso de despliegue que aplique migraciones aparte, y no debe haberlo: el esquema es la fuente de verdad (Art. V.3) y llega con el artefacto.

De ahí se siguen tres cosas:

- Una base **vacía** es el estado correcto de partida. Dokploy la entrega así.
- Una base **con tablas y sin historial de Flyway** falla a propósito. No se «arregla» activando `baseline-on-migrate`: eso asume que lo que hay coincide con lo esperado, que es justo lo que nadie ha comprobado.
- Hibernate corre con `ddl-auto: validate`. Si el esquema no coincide con el mapeo, el arranque muere con el detalle exacto de la discrepancia — y eso es una funcionalidad, no un obstáculo.

El primer arranque **tarda**: aplica todas las migraciones antes de que el puerto responda. Por eso `healthcheckTimeout` está en 300 segundos, y por eso la sonda es `/readiness` y no `/liveness` (`architecture.md` §9.1).

### 8.2 El superadministrador

`V9__semilla_catalogos_y_superadmin.sql` siembra la única fila de `users` con identificador conocido, con el correo y el resumen de §4. Después de eso:

- [ ] Iniciar sesión con esa credencial **una vez**, y **cambiar la contraseña de inmediato**. La de §4.2 pasó por una terminal y por el portapapeles.
- [ ] Crear las cuentas reales desde la API. La del superadministrador es la llave del sistema, no una cuenta de trabajo.

`SUPERADMIN_EMAIL` y `SUPERADMIN_PASSWORD_HASH` **solo actúan en el primer arranque**: la migración ya está aplicada y cambiar su valor después no cambia nada. Se dejan declaradas de todos modos — retirarlas rompería una reconstrucción desde cero.

---

## 9. Dominio, HTTPS y CORS

**HTTPS lo pone Traefik.** Al declarar el dominio del servicio en Dokploy (§5.3) con el certificado de **Let's Encrypt**, Traefik lo pide y lo renueva solo. Para eso el DNS tiene que llevar, **antes**, un registro `A` del dominio hacia la IP del servidor: Let's Encrypt comprueba el dominio contra el puerto `80`, y si el registro no existe todavía, el certificado no se emite y Traefik sirve uno autofirmado. La aplicación no termina TLS ni lo sabe: recibe HTTP en su puerto, detrás del borde. Con eso se satisface el Art. IV.6 y `security.md` §7.2.

**Sin un proxy de CDN delante**, o con el de Cloudflare en modo *DNS only*. Con el proxy activado, Traefik ve las IP de Cloudflare y no las de quien llama, y lo que §11.2 consigue se pierde sin ningún síntoma.

**CORS lo pone la aplicación**, y es el paso que se olvida:

- `CORS_ALLOWED_ORIGINS` lleva los orígenes del **navegador**, separados por coma, con la forma `esquema://host[:puerto]`, **sin barra final y sin ruta**.
- **Vacío es ningún origen autorizado.** Es lo seguro, y no rompe a quien consume la API de servidor a servidor: eso no pasa por CORS.
- **`*` tumba el arranque**, a propósito (`security.md` §6.1). También lo tumba un origen sin esquema o con barra final, que nunca casaría y cuyo fallo aparecería semanas después como un error de CORS en el navegador de otra persona.
- Los orígenes de `docker-compose.yml` —los `localhost` de Vite, Next y Angular— **no van aquí jamás**. Autorizarlos en un entorno desplegado abre la API al navegador de cualquiera que corra un frontend en su máquina.

Un `production` cuyo frontend viva en `https://app.nexus.co` lleva exactamente eso, y nada más.

---

## 10. Ramas, entornos y promoción

| Entorno de Dokploy | `ENVIRONMENT` | Rama | Imagen | Entorno de GitHub |
|---|---|---|---|---|
| `testing` | `testing` | `develop` | `ghcr.io/nexuspro-dev/backend:develop` | `testing` |
| `production` | `production` | `main` | `ghcr.io/nexuspro-dev/backend:main` | `production` |

Cada entorno tiene **su propia base y su propio juego completo de variables**. No se comparte la base, ni el `JWT_SECRET`, ni la clave de Resend.

El flujo es el del Art. XI.1 y `development-guide.md` §12, sin nada añadido: `feature/*` → `develop` → `main`. **Desplegar es integrar**; no hay una acción de despliegue separada que alguien pueda olvidar o disparar por su cuenta.

### 10.1 Cómo llega un commit a un entorno

Lo hace el job **`publicar-y-desplegar`** de `.github/workflows/ci.yml`, y solo en un `push` a `develop` o a `main`:

1. **Espera a `verificar`.** Si la compilación, el formato, las pruebas o el contrato están en rojo, no se publica nada. En Railway no era así: la plataforma construía al recibir el commit, con el pipeline como estuviera.
2. **Construye la imagen una sola vez** con el `Dockerfile` y la publica en GHCR con dos etiquetas: **la de la rama**, que es la que el servicio de Dokploy despliega, y **`sha-<commit>`**, que es la que permite volver a un despliegue concreto (§12).
3. **Llama a la API de Dokploy** —`POST /api/application.deploy`, con la clave en `x-api-key`— y Dokploy descarga la imagen y hace el relevo de §7.3.

Los commits que **solo** tocan documentación no disparan el CI (`paths-ignore`) y, por tanto, **no despliegan**: no hay nada nuevo en la imagen.

**El job está apagado hasta que se enciende a propósito.** Corre solo si la **variable de repositorio** `DOKPLOY_DEPLOY` vale `true`; sin ella, se salta y el pipeline queda en verde. Así un repositorio sin servidor configurado no está en rojo. Lo que necesita, en cada **entorno de GitHub** (*Settings → Environments*):

| Secreto | Valor |
|---|---|
| `DOKPLOY_URL` | La dirección del panel, `https://…`, **sin barra final** |
| `DOKPLOY_API_KEY` | Una clave generada en Dokploy, *Settings → Profile → API/CLI* |
| `DOKPLOY_APPLICATION_ID` | El identificador del servicio `backend` **de ese entorno**. Aparece en la dirección del servicio en el panel |

Separarlos por entorno de GitHub y no ponerlos en el repositorio es lo que permite, el día que haga falta, **exigir una aprobación** antes de desplegar `production` (*Required reviewers* del entorno) sin tocar el workflow.

`main` debe mantenerse siempre desplegable (Art. XI.2), y con auto-despliegue esa regla deja de ser una convención: lo que entra en `main` está en producción en minutos.

---

## 11. Verificación posterior al despliegue

### 11.1 Lista de comprobación

Contra el dominio del entorno recién desplegado:

```bash
BASE=https://<dominio-del-entorno>

# 1. ¿Arrancó?  -> {"status":"UP"}
curl -s $BASE/actuator/health/liveness

# 2. ¿Puede atender?  -> {"status":"UP"}
curl -s $BASE/actuator/health/readiness

# 3. La documentación NO debe estar abierta  -> 401
curl -s -o /dev/null -w '%{http_code}\n' $BASE/v3/api-docs

# 4. Las métricas NO deben estar abiertas  -> 401
curl -s -o /dev/null -w '%{http_code}\n' $BASE/actuator/metrics

# 5. Una ruta protegida cualquiera  -> 401, nunca 200 ni 500
curl -s -o /dev/null -w '%{http_code}\n' $BASE/api/v1/roles
```

- [ ] Los cinco responden lo esperado. **Un `200` en la 3 o la 4 es un despliegue mal configurado**, no un detalle.
- [ ] El superadministrador entra, y su contraseña se cambia acto seguido (§8.2).
- [ ] En los logs del arranque figuran las migraciones aplicadas y **ninguna advertencia de envío apagado** si `NOTIFICATION_ENABLED` es `true`.
- [ ] Un error provocado a propósito devuelve el formato RFC 9457 **sin traza ni mensaje interno** (Art. VI.5).
- [ ] La respuesta de salud **no lleva detalle**: `{"status":"UP"}` y nada más. Si aparecen componentes o versiones, `show-details` está mal.

**Y la configuración del servicio, que vive fuera del repositorio** (§5.3). En el servidor:

```bash
docker service inspect <nombre-del-servicio-backend> --format \
  'réplicas={{.Spec.Mode.Replicated.Replicas}} orden={{.Spec.UpdateConfig.Order}} fallo={{.Spec.UpdateConfig.FailureAction}} gracia={{.Spec.TaskTemplate.ContainerSpec.StopGracePeriod}}'
```

- [ ] Responde `réplicas=1 orden=start-first fallo=rollback gracia=35000000000`. Cualquier otra cosa es un valor cambiado en el panel y no en §5.3, y uno de los dos está mal.
- [ ] **Ningún servicio de los entornos publica puertos**: en `docker service ls`, la columna `PORTS` va vacía en el backend, en el frontend y, sobre todo, en `postgres`. El único que publica es el panel de Dokploy (`3000`), y Traefik no aparece en esa lista porque no es un servicio de Swarm.

### 11.2 La IP de la auditoría

**En Dokploy, `TRUSTED_PROXIES` lleva la subred de la red `dokploy-network`**, y con ella la auditoría registra la IP de quien llamó. El porqué está en [`ADR-007`](architecture/ADR-007-plataforma-de-despliegue-dokploy.md), y se resume en tres hechos que hay que poder volver a comprobar:

1. Traefik corre como **contenedor normal**, no como servicio de Swarm, con los puertos `80` y `443` publicados en el anfitrión: la conexión le llega con su origen intacto.
2. Traefik **no confía en el `X-Forwarded-For` que llega de fuera** —Dokploy no le declara IP de confianza— y escribe el suyo.
3. El backend ve a Traefik con una dirección de `dokploy-network`, que cambia si el contenedor se recrea. **La subred no cambia.**

La subred se lee en el servidor:

```bash
docker network inspect dokploy-network --format '{{(index .IPAM.Config 0).Subnet}}'
```

Y es ese valor —un bloque del estilo `10.0.1.0/24`— el que va en `TRUSTED_PROXIES` de los dos entornos (§6.3). **No un rango más ancho por si acaso**: confiar en un bloque es confiar en todo lo que salga de él, y quien pudiera emitir peticiones desde dentro escribiría en la auditoría la IP que quisiera. En esa red están Traefik, Dokploy y los servicios de los dos entornos; es la declaración más estrecha que esta plataforma permite.

**Lo que hay que comprobar sobre el despliegue real**, porque los tres hechos de arriba son de la plataforma y no de este código:

- [ ] Una petición desde una IP conocida —la de quien despliega, por ejemplo— queda **con esa IP** en `request_log`.
- [ ] Una petición con `X-Forwarded-For: 1.2.3.4` inventado **no** queda con `1.2.3.4`.

Si la primera falla —en `request_log` aparece una dirección de `dokploy-network` o la puerta de enlace de Docker—, la IP se está perdiendo antes de Traefik: un proxy de CDN delante (§9), o clientes que llegan por IPv6 a un Docker sin IPv6 activado, en cuyo caso el reenvío lo hace el proxy de espacio de usuario de Docker y el origen se sustituye. Mientras se resuelve, **dejar `TRUSTED_PROXIES` vacía** devuelve el comportamiento conocido: la auditoría apunta al proxy, un dato incompleto pero **cierto**.

---

## 12. Operación

| Necesidad | Cómo |
|---|---|
| **Ver los logs** | Pestaña *Logs* del servicio en Dokploy, o `docker service logs -f <servicio>` en el servidor. Es la salida estándar del proceso: el log de aplicación de `architecture.md` §9, ya enmascarado. *Deployments* guarda en cambio el log de **cada despliegue** —la descarga de la imagen y el relevo—, que es donde se mira un despliegue fallido |
| **Ver el consumo** | Pestaña *Monitoring* del servicio: CPU y memoria contra su límite (§5.4). Es lo primero que se mira si un entorno va lento |
| **Redesplegar sin cambios** | *Deploy* en el servicio. Vuelve a descargar la imagen de la etiqueta configurada y hace el relevo |
| **Revertir** | En *General*, cambiar la imagen de `…:main` a **`…:sha-<commit>`** del despliegue bueno y pulsar *Deploy*. Las etiquetas de cada commit están en el paquete de GHCR. **Devolverla a `…:main` en cuanto el arreglo esté integrado**: mientras apunte a un `sha-`, los despliegues del CI siguen ocurriendo y **no cambian nada**. Revierte el código, **nunca el esquema**: lo que Flyway aplicó, aplicado queda |
| **Cambiar una variable** | *Environment* → editar → guardar → **Deploy**. Sin desplegar, el servicio sigue con los valores viejos. El relevo es el de §7.3: el estado en memoria se pierde |
| **Entrar a la base** | **Desde el servidor, por SSH; nunca publicando el puerto.** Allí, `docker ps` da el contenedor de `postgres` del entorno y `docker exec -it <contenedor> psql -U <usuario> -d nexus` abre la consola. Toda consulta contra datos reales tiene delante el Art. IV y `security.md`: se lee lo que hace falta y no se escribe a mano |
| **Copias de seguridad** | *Backups* del servicio `postgres`: destino S3 (*Settings → Destinations*), programación diaria —`0 4 * * *`— y un prefijo por entorno. El botón *Test* comprueba el destino, **no la restauración**. Restaurar una copia en un servicio de pruebas **antes de que haya datos reales** es una obligación con nombre ([`ADR-007`](architecture/ADR-007-plataforma-de-despliegue-dokploy.md) §Seguimiento): una copia que nadie ha restaurado nunca no es una copia |
| **Los secretos de §4** | `JWT_SECRET`, `MFA_ENCRYPTION_KEY` y `PAYRETAILERS_ENCRYPTION_KEY` **no están en las copias de la base** y sin las dos últimas los datos cifrados de la copia son ilegibles. Van en el gestor de contraseñas, junto a la credencial del destino S3 |
| **Actualizar Dokploy** | Desde su panel, *Settings*. Leer antes las notas de la versión: Dokploy actualiza Traefik por su cuenta, y Traefik es el borde de los dos entornos |
| **Métricas** | `/actuator/metrics`, autenticado y **a mano** (§13) |

!!! warning "Revertir un despliegue no revierte una migración"

    Volver al despliegue anterior deja corriendo un código antiguo contra un esquema nuevo. Si la migración era compatible hacia atrás —§7.3—, funciona. Si no lo era, la reversión **no arregla nada y puede empeorarlo**: `ddl-auto: validate` abortará el arranque, que es el mejor de los desenlaces posibles.

    La salida de una migración mala es **hacia adelante**: otra migración que corrija. No hay botón para lo otro.

### 12.1 El último superadministrador perdió el teléfono y los códigos

`RN-SP-065` impide que un administrador restablezca el segundo factor de quien tiene más privilegios (`RF-SP-076`), y **el superadministrador los tiene todos**: solo otro superadministrador puede restablecer el suyo. **Si no hay otro, no hay salida por la API**, y es deliberado: la alternativa —que `ADMIN` pudiera— es la toma de cuenta que esa regla cierra. La salida es este procedimiento, que exige **acceso a la base**, que es justo lo que un administrador de la aplicación no tiene.

**Antes de ejecutarlo**: comprobar por un canal ajeno al sistema que quien lo pide es de verdad el titular, y anotar quién lo autoriza. El sistema no puede hacer esa comprobación (`RF-SP-076` · `spec.md` §2.1).

```sql
BEGIN;

-- 1. Retirar el factor activo y el pendiente, como lo haría RF-SP-076.
UPDATE user_mfa_factors
   SET status = 'RETIRADO', retired_at = now(), retired_reason = 'RESTABLECIDO',
       pending_expires_at = NULL, updated_at = now()
 WHERE user_id = (SELECT id FROM users WHERE username = 'superadmin')
   AND status IN ('ACTIVO', 'PENDIENTE');

-- 2. Cerrar todas sus sesiones: una abierta en el teléfono perdido la tiene otro.
UPDATE refresh_tokens
   SET revoked_at = now(), revoked_reason = 'ACCESO_RETIRADO'
 WHERE user_id = (SELECT id FROM users WHERE username = 'superadmin')
   AND revoked_at IS NULL;

-- 3. Dejar constancia, sin actor: no lo hizo nadie desde la aplicación.
INSERT INTO audit_security_log (id, occurred_at, actor_id, event_type, severity, outcome,
                                target_user_id, detail)
SELECT gen_random_uuid(), now(), NULL, 'MFA_RESET', 'ALTA', 'SUCCESS', id,
       jsonb_build_object('procedure', 'deployment.md §12.1',
                          'reason', '<por qué, y quién lo autorizó>')
  FROM users WHERE username = 'superadmin';

COMMIT;
```

**Después**: el superadministrador entra con su contraseña y, como su rol exige el segundo factor, **queda retenido hasta activar uno nuevo** (`RF-SP-072` `FA-002`), con su teléfono nuevo y sus diez códigos nuevos. Si además olvidó la contraseña, se recupera aparte por correo (`RF-SP-040`). **No hace falta reiniciar el servicio**: el token de acceso que tuviera vivo caduca en quince minutos y ya no puede renovarse.

Probado el 06-10-2026 contra la base local dentro de una transacción deshecha: retira el factor, revoca las sesiones y escribe el evento, y los tres `CHECK` del esquema lo admiten.

---

## 13. Lo que este despliegue no resuelve

Ninguno de estos puntos impide desplegar. Todos están declarados para que no se confundan con lo hecho.

| # | Pendiente | Consecuencia hoy | Dónde se corrige |
|---|---|---|---|
| 1 | **Una sola réplica** | No hay escalado horizontal ni tolerancia a la caída del único proceso | Canal compartido detrás de `AccessRevocationPublisher` (§2.1) |
| 2 | **Un solo servidor** | Si el VPS cae, caen los dos entornos y los dos repositorios a la vez. No hay alta disponibilidad | Condiciones de revisión de [`ADR-007`](architecture/ADR-007-plataforma-de-despliegue-dokploy.md) |
| 3 | **La IP de auditoría depende de la plataforma** | Con la subred de §11.2 se registra la de quien llamó; está **sin comprobar** sobre el despliegue real | La comprobación de §11.2 |
| 4 | **Nadie raspa las métricas** | `/actuator/metrics` exige un JWT y un raspador no lo porta. Se consultan a mano | Permiso propio o red de administración. La salida C de [`ADR-004`](architecture/ADR-004-raspado-de-metricas-y-alertas.md) pasa a ser comprobable: en Swarm, un puerto que no se publica ni se enruta solo se alcanza desde la red interna |
| 5 | **Nadie alerta** | En particular, sigue sin vigilarse la **ausencia de eventos de auditoría**, que `RF-SP-001` §10 declara que debería. Una métrica que nadie mira es una métrica que no existe | — |
| 6 | **`request_log` crece sin techo** | La tabla existe desde `V35` y **ningún proceso la purga**, aunque la variable exista | **D-10** |
| 7 | **El contrato no llega solo al frontend** | Sigue siendo un archivo que alguien copia | Pendiente n.º 1 de [`ADR-001`](architecture/ADR-001-publicacion-del-contrato-openapi.md) |

---

## 14. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.5.0 | 27-08-2026 | §11.2 y §13 se corrigen: `ClientIpResolver` **ya admite rangos CIDR**, de modo que el obstáculo deja de ser el mecanismo y pasa a ser el valor. Lo que falta de **D-21** es elegir qué rango declarar, con el precio dicho: confiar en un bloque es confiar en todo lo que salga de él. Mientras la variable siga vacía el comportamiento no cambia — la auditoría apunta al proxy, que es un dato incompleto pero cierto. | Responsable técnico |
| 0.1.0 | 27-08-2026 | Creación inicial. Recoge el procedimiento de despliegue sobre Railway que [`ADR-002`](architecture/ADR-002-plataforma-de-despliegue-railway.md) decide al cerrar **D-09**: topología de dos servicios, mapa completo de variables con su valor literal, los tres detalles de plataforma que rompen el arranque —el puerto, la red privada IPv6 y el relevo con dos instancias vivas—, la verificación posterior y la operación. Declara **una sola réplica** como restricción de diseño y no de coste, con los tres componentes en memoria que la imponen; declara que **`TRUSTED_PROXIES` no tiene hoy valor correcto** en Railway, lo que reabre **D-21** con otra forma; y separa lo desplegado de lo pendiente en §13. | Responsable técnico |
| 0.2.0 | 27-08-2026 | **Dos de los pendientes de §13 dejan de serlo, y con código y no con prosa.** El **puerto** deja de fijarse a mano: `application.yml` declara `server.port: ${PORT:8080}`, de modo que en un entorno desplegado manda la plataforma y en local siguen valiendo los 8080 del `Dockerfile`. El literal anterior obligaba a declarar `PORT=8080` en Railway para que los dos lados coincidieran, y el día que dejaran de hacerlo el síntoma era **una sonda en rojo sobre un arranque impecable en los logs** — el puerto es lo último que uno mira. Y el **apagado ordenado** pasa a existir: `server.shutdown: graceful` con treinta segundos, que es lo que hace tolerable el relevo de §7.3 —hay dos procesos vivos y al viejo se le manda parar con peticiones en curso; sin esto las corta en seco, y una conexión caída es indistinguible de un sistema roto para quien estaba escribiendo—. El plazo sobra para cualquier operación de este sistema —los umbrales del Art. XV.9 son de menos de un segundo— y queda por debajo del que usa la plataforma para matar el proceso a la fuerza. §6.2 pasa a decir que `PORT` **no se declara**, §7.1 y §7.3 se reescriben, y la tabla de §13 baja de ocho filas a seis. | Responsable técnico |
| 0.3.0 | 27-08-2026 | Este documento gana un **complemento y una frontera**: nace [`manual-de-despliegue.md`](manual-de-despliegue.md), que es **qué se teclea y en qué orden**, y esta pasa a ser la **referencia** —qué es cada cosa y por qué—. La separación no es de gusto: quien despliega por primera vez tenía que saltar entre §4, §5, §6, §7, §8 y §11 para reunir una secuencia que ninguna sección contenía entera, y quien viene a entender una decisión tropezaba con instrucciones. §1 declara cuál manda cuando se contradigan: **este**. | Responsable técnico |
| 0.4.0 | 27-08-2026 | **Corrige un defecto de este documento que costó un despliegue caído.** §10 daba `develop` y `main` como las ramas de cada entorno sin decir que **hoy ninguna de las dos es desplegable**: todo el trabajo vive en `feature/esqueleto-del-proyecto` sin fusionar, y a `develop` y `main` les faltan **veintitrés commits**, entre ellos `railway.json` y el `server.port: ${PORT:8080}`. Desplegar una de ellas da «Application failed to respond» **con un arranque impecable en los logs** — el fallo que §7.1 describe y que este documento mandaba a reproducir. La portada de la documentación ya avisaba de que el trabajo estaba sin fusionar; lo que faltaba era leerlo desde el despliegue. §10 y el paso 4 del manual ganan el aviso y una comprobación de una línea: **una rama es desplegable si contiene `railway.json`**. | Responsable técnico |
| 0.6.0 | 31-08-2026 | **`ENVIRONMENT` deja de ser decorativa, y §6.6 pasa de decir que nadie la lee a decir qué decide.** Fuera de `production` se aplica al arrancar la semilla de `db/dev-seed/`: diecinueve personas de prueba con sus roles y tres membresías, por decisión del responsable del proyecto. **Lo que cambia para quien despliega es que un servicio sin la variable declarada DEJA DE ARRANCAR** (Art. IX.5), y §6.2 lo dice en la propia tabla en lugar de remitir a una nota. No es celo: la condición «el entorno no es producción» sobre una cadena suelta **falla abierta justo del lado que importa** —`Production`, `prod`, el vacío y la variable ausente son todos «distintos de producción»—, y lo que se sembraría en el sistema real son diecinueve cuentas que **comparten el hash del superadministrador** y **no están obligadas a cambiar la contraseña**. El valor se traduce por eso a un dominio cerrado de tres y cualquier otra cosa tumba el arranque, con lo que no queda un cuarto estado. Queda escrito además que **el guion viaja dentro del artefacto de producción** —el classpath es el mismo— y que lo que lo separa de esas cuentas **no es la ausencia del archivo sino el guardia**, verificado por una prueba que enciende el interruptor a propósito. Se documenta `DEV_SEED_ENABLED`, que apaga la semilla sin tocar el entorno y **no puede reabrir producción**. `API_URL` sigue declarada y sin lector. | Responsable del proyecto |
| 0.7.0 | 14-09-2026 | **La semilla de desarrollo gana un segundo guion: el catálogo de productos.** `semilla-productos.sql` siembra dieciséis productos —once upgrades y cinco bots— pensados para que cada regla de `PM` tenga algo que ejercitar sin tocar la API: un upgrade declarado **desde cada membresía** (`RN-PM-011`, los tres clientes escalonados ven ofertas distintas), el salto `BECA → ORO` junto al de un peldaño (`RN-PM-018`), las cuatro renovaciones con **`RENOVAR_BECA` a precio cero** (`RN-PM-017`, `RN-PM-006`, y el caso de `RN-CM-020`), productos `TIENDA` que el hotlink no resuelve (`RN-PM-021`), bots `MANUAL` (`RN-PM-020`), un inactivo sobre el mismo par que un activo y un retirado para el catálogo administrativo, y un bot con precio de compra declarado (`RN-PM-023`). `DevelopmentDataSeeder` corre los dos guiones **en una sola transacción** y el aviso del arranque dice cuántos productos hay. Es repetible por código (`RN-PM-013`), resuelve las membresías **por código** y no por identificador, y **deja fuera con un `NOTICE`** los upgrades cuya membresía no exista en lugar de tumbar el arranque. `DevelopmentSeedIT` lo cubre. | Responsable técnico |
| 0.8.0 | 15-09-2026 | **Las migraciones se consolidaron en nueve** ([`modelo-datos.md`](modelo-datos.md) §5.4): la semilla del superadministrador y sus marcadores de posición viven ahora en `V9__semilla_catalogos_y_superadmin.sql`, con las mismas variables (`SUPERADMIN_EMAIL`, `SUPERADMIN_PASSWORD_HASH`) y las mismas guardas. **Toda base levantada antes del 15-09-2026 debe borrarse**: su historial de Flyway no coincide con el nuevo y `validate-on-migrate` la rechazará a propósito. | Responsable técnico |
| 0.9.0 | 21-09-2026 | **La semilla de desarrollo completa el documento a quien ya existía sin él.** Declara `CC` y el número de documento desde el 10-09-2026 (`RN-SP-035`), pero solo inserta a quien no existe: el entorno de desarrollo compartido, sembrado antes, seguía mostrando a las diecinueve personas de prueba sin documento, y ninguna migración las rellena porque no son datos del sistema. Un bloque repetible les pone **los mismos valores que recibe quien nace hoy** —`CC`, el número derivado del nombre de usuario, y teléfono, dirección y ciudad solo si faltan—, reconociéndolas por el nombre de usuario de la semilla, sin tocar a quien se registró por la API ni al superadministrador (fila de `V9`, se decide en migración) y sin pisar un número que otro ya tenga. `DevelopmentSeedIT` lo cubre. Por petición del responsable del proyecto. | Responsable técnico |
| 0.10.0 | 01-10-2026 | **La semilla de desarrollo gana un tercer guion: las tasas de comisión de rol** de los productos de prueba, a petición del responsable del proyecto. `semilla-tasas-comision.sql` da a cada producto vivo una tasa para AGENTE, DIRECTOR y MANAGER: **porcentaje** de 10 / 7 / 5 % en los upgrades con precio y en `BOT_PRO_ANUAL`; **importe fijo** en `BOT_SENALES` (4 / 2,50 / 1,50) y `BOT_COPY_TRADING` (8 / 5 / 3); y fijo de 2 / 1 / 0,50 en los dos gratuitos, donde solo comisiona el fijo (`RN-CM-020`). Ninguna cadena pasa del precio (`RN-CM-026`). Es repetible —no toca una tasa viva, tampoco la corregida por la API—, resuelve productos y roles por código y avisa con un `NOTICE` si algún producto queda sin sus tres. `DevelopmentSeedIT` lo cubre. | Responsable técnico |
| 0.11.0 | 01-10-2026 | **La semilla de productos gana los enlaces de entrega de los bots**, a petición del responsable del proyecto: `CUPON_BOT` con la dirección real de activación de bots.com.co y una `DESCARGA` de prueba (`https://www.bots.com.co/downloads/`), en los cuatro bots vivos. Se añaden también a una base sembrada antes —no dependen de que el producto se acabe de crear— y no pisan un enlace corregido a mano. Como manda `RN-PM-050`, solo los publica «mis productos» con la venta pagada. | Responsable técnico |
| 0.12.0 | 01-10-2026 | **Nueva §6.5.1: la pasarela de la tarjeta.** `STRIPE_SECRET_KEY` —una clave **restringida** a los cobros, no la secreta completa— y `STRIPE_WEBHOOK_SECRET` —el del endpoint, uno por entorno—; sin ellas la pasarela queda apagada y la tarjeta vuelve a confirmarla una persona. Se documenta la URL del endpoint que se registra en Stripe, los seis eventos a los que se suscribe y cómo recibir las notificaciones en local con la Stripe CLI. | Responsable técnico |
| 0.13.0 | 02-10-2026 | **§6.5.1: el servicio `stripe` del `docker-compose.yml` reenvía las notificaciones en local**, a petición del responsable del proyecto: una compra con tarjeta se cobraba en Stripe y se quedaba pendiente porque `stripe listen` dependía de una terminal abierta. Usa la misma clave que la aplicación; sin ella se queda dormido. Solo local. | Responsable técnico |
| 0.14.0 | 05-10-2026 | **Nueva §6.5.2: la pasarela local, PayRetailers** ([`requirements/mv.md`](requirements/mv.md) v0.78.0 §4.10): tres credenciales —`shopId`, clave secreta y *Subscription Key*—, la URL base con el sandbox por omisión, la dirección pública de los avisos, la de retorno al frontend, el modo de prueba y el barrido. En local, un túnel para los avisos o esperar al barrido | Responsable del proyecto |
| 0.15.0 | 05-10-2026 | **§6.5.2: la tienda de PayRetailers sale del entorno y va en la conversión de cada país** ([`requirements/mv.md`](requirements/mv.md) v0.82.0, `RN-MV-063`): se retiran `PAYRETAILERS_SHOP_ID` y `PAYRETAILERS_SECRET_KEY`, y entra `PAYRETAILERS_ENCRYPTION_KEY`, la llave que cifra las claves de las tiendas en la base | Responsable del proyecto |
| 0.16.0 | 06-10-2026 | **Nueva §4.1.1: `MFA_ENCRYPTION_KEY`**, la llave que cifra el secreto del segundo factor ([`security.md`](security.md) §3.3, `RF-SP-071`, `V75`). Obligatoria y sin valor por defecto, como `JWT_SECRET`, y con la diferencia que importa: **no se rota**, porque cambiarla deja ilegibles los factores guardados. Una fila más en §6.3. | Responsable técnico |
| 0.17.0 | 06-10-2026 | Aviso en §4.1.1: **desde `RF-SP-072`, `SUPERADMIN` y `ADMIN` activan el segundo factor en su primer inicio de sesión tras desplegar** —teléfono con una app autenticadora a mano, y los diez códigos de recuperación guardados—. | Responsable técnico |
| 0.18.0 | 06-10-2026 | **Nueva §12.1: recuperar al último superadministrador** que perdió el teléfono y los códigos (`RF-SP-076` `T-05`). `RN-SP-065` impide hacerlo por la API —es la toma de cuenta que la regla cierra—, de modo que se hace por la base, con tres sentencias en una transacción: retirar el factor, cerrar las sesiones y dejar el evento. Probado contra la base local en una transacción deshecha. | Responsable técnico |
| 0.19.0 | 08-10-2026 | **Nueva §6.5.3: los avisos de los brokers** (`RF-SP-078`). Tres variables —`BROKER_TOKEN_IQOPTION`, `BROKER_TOKEN_EXNOVA`, `BROKER_TOKEN_EXOPTION`—, la dirección que va en el panel de afiliados de cada broker y la consulta para ver lo que llegó. | Responsable técnico |
| 0.20.0 | 08-10-2026 | §6.5.3: **las direcciones de los avisos llevan el nombre del broker** (`/brokers/iqoption/notifications`) y no su identificador (`RF-SP-078`). Renombrar un broker obliga a cambiar la dirección en su panel. | Responsable técnico |
| 0.21.0 | 09-10-2026 | §6.5.3: **la dirección común de los avisos**, `/api/v1/brokers/notifications`, con `BROKER_NOTIFICATION_TOKEN` y el broker según `advertiser` (`RN-SP-069`). La dirección por nombre y sus tres secretos siguen, obsoletos. | Responsable técnico |
| 0.22.0 | 09-10-2026 | §6.5.3: **`BROKER_REGISTRATION_EVENT`** y los tres nombres de campo: el aviso de registro crea la cuenta sin titular (`RN-SP-072`). | Responsable técnico |
| 0.23.0 | 10-10-2026 | §6.5.3: **`BROKER_DEPOSIT_EVENT`, `BROKER_OPERATION_EVENT`** y `BROKER_FIELD_EVENT_ID`: el depósito confirma el FTD (`RN-SP-073`) y la operación se cuenta (`RN-SP-074`). | Responsable técnico |
| 0.24.0 | 13-10-2026 | **Nueva §6.5.4: las clases en vivo (Zoom)**: la app Server-to-Server OAuth, sus scopes y las cuatro variables `ZOOM_*`. | Responsable técnico |
| 0.25.0 | 13-10-2026 | §6.5.3: **la dirección común es la única** y **guarda todo aviso con el secreto**: el que no se reconoce queda sin broker y se asocia al arrancar cuando una migración cargue su `advertiser` (`RN-SP-076`). Se retiran la dirección por nombre y `BROKER_TOKEN_IQOPTION`, `BROKER_TOKEN_EXNOVA` y `BROKER_TOKEN_EXOPTION`; la consulta de lo que llegó gana `advertiser` y la de los avisos sin broker. | Responsable técnico |
| 0.26.0 | 14-10-2026 | **La plataforma pasa de Railway a un VPS propio con Dokploy** ([`ADR-007`](architecture/ADR-007-plataforma-de-despliegue-dokploy.md), que sustituye a [`ADR-002`](architecture/ADR-002-plataforma-de-despliegue-railway.md)), por decisión del responsable del proyecto. §2 dibuja la topología nueva: Traefik como borde, el backend como servicio de Swarm que ejecuta una imagen de GHCR y PostgreSQL 17 con copias a S3. §5 se reescribe entera: **el servidor y su endurecimiento** —con el aviso de que **Docker se salta UFW** y el firewall tiene que ser el del proveedor—, los servicios de cada entorno, y **la configuración del servicio transcrita valor por valor**, porque Dokploy no la lee de ningún archivo del repositorio y `railway.json` deja de valer. Ahí aparece el único defecto que esta plataforma traía de serie: **Swarm mata el proceso a los diez segundos** y el apagado ordenado tiene treinta, de modo que el *Stop Grace Period* va a 35. Nueva §5.4 con los **límites de memoria** que separan los dos entornos en el mismo servidor y `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60`, sin la cual la JVM solo usa para el montón una cuarta parte del límite. §6.1 construye `DATABASE_URL` con el *Internal Host* y avisa de la URI de Dokploy, que no es de JDBC. §7 cambia sus tres detalles: el puerto lo decide el dominio y no una variable inyectada, desaparece la red solo IPv6 de Railway, y el relevo es `start-first` con vuelta atrás automática. Nueva §10.1: **la imagen la construye y publica el CI después de `verificar`** y despliega por la API de Dokploy, de modo que una rama en rojo ya no llega a ningún entorno; el job está apagado hasta declarar `DOKPLOY_DEPLOY=true`. **§11.2 deja de decir que la auditoría no puede registrar la IP real**: con Traefik como contenedor fuera de la malla de Swarm, `TRUSTED_PROXIES` lleva la subred de `dokploy-network` (**D-21**), con dos comprobaciones sobre el despliegue real. §11.1 gana la verificación de la configuración del servicio, y §12 la reversión por etiqueta `sha-`, el acceso a la base por SSH, las copias y los secretos que no viajan en ellas. Se elimina el aviso de §10 sobre `feature/esqueleto-del-proyecto`, que llevaba semanas sin ser cierto. | Responsable técnico |
