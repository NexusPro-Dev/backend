# Despliegue — Backend NEXUS

| Campo | Valor |
|---|---|
| Proyecto | NEXUS — Renovación de plataforma |
| Empresa | FACTECH GROUP SAS |
| Documento | `deployment.md` |
| Versión | 0.1.0 |
| Estado | Borrador |
| Responsable técnico | Bonilla Diaz William Steven |
| Fecha de creación | 27-08-2026 |
| Última actualización | 27-08-2026 |
| Documento superior | `constitution.md` v0.7.0 |
| Documentos relacionados | `architecture.md` v0.20.0 · `security.md` v0.35.0 · [`ADR-002`](architecture/ADR-002-plataforma-de-despliegue-railway.md) |

---

## 1. Propósito y alcance

Este documento responde una pregunta concreta: **cómo se lleva este backend a un entorno en ejecución que no es la máquina de nadie**. Es el procedimiento operativo del despliegue, no la justificación de la plataforma: por qué Railway y no otra cosa está en [`ADR-002`](architecture/ADR-002-plataforma-de-despliegue-railway.md), que cierra la decisión **D-09**.

**Dentro del alcance:** los dos entornos desplegados que exige el Art. IX.4 —`testing` y `production`—, sobre Railway; el mapa completo de variables de entorno con el valor exacto que va en cada una; los tres detalles de la plataforma que rompen el arranque si nadie los mira; la verificación posterior; y la operación del día a día —logs, redespliegue, reversión, acceso a la base—.

**Fuera del alcance:** el entorno local, que vive en [`development-guide.md` §2](development-guide.md#2-puesta-en-marcha-del-entorno-local) y en el `docker-compose.yml`; el despliegue del frontend, que es otro repositorio; y la publicación del sitio de documentación, que la hace GitHub Pages desde `.github/workflows/docs.yml` y no tiene nada que ver con esto.

!!! warning "El artefacto es el mismo; lo único que cambia es la configuración"

    El Art. IX.1 no admite excepciones aquí: **no hay perfil de Spring por entorno, ni `application-production.yml`, ni una rama con valores distintos**. La imagen que corre en `production` es la misma que corre en `testing` y la que se construye en local. Todo lo que las distingue son las variables de §6.

    Si alguna vez hace falta un cambio de comportamiento que no quepa en una variable, eso es una enmienda a la arquitectura, no un archivo nuevo.

---

## 2. Qué se despliega

Dos servicios dentro de un mismo proyecto de Railway, y nada más:

```mermaid
graph LR
    N["Navegador / Frontend"] -->|HTTPS| E["Borde de Railway<br/>TLS y dominio"]
    E -->|"HTTP :8080"| A["Servicio <b>backend</b><br/>imagen del Dockerfile<br/><b>1 réplica</b>"]
    A -->|"JDBC · red privada<br/>(IPv6)"| P[("Servicio <b>Postgres</b><br/>PostgreSQL 17<br/>volumen gestionado")]
    G["GitHub<br/>NexusPro-Dev/backend"] -.->|"push a la rama vigilada"| A
```

| Pieza | Qué es | De dónde sale |
|---|---|---|
| Servicio **backend** | La aplicación. Railway construye la imagen con el `Dockerfile` del raíz —construcción en dos etapas, JRE 21 y usuario sin privilegios— y la ejecuta | `Dockerfile` + `railway.json` |
| Servicio **Postgres** | PostgreSQL 17 gestionado por Railway, con su propio volumen y sus copias de seguridad | Plantilla oficial de Railway |
| Borde | TLS, certificado y dominio. No hay nada que configurar en la aplicación para esto | Railway |

**No hay un tercer servicio.** Adminer es exclusivamente local; el `docker-compose.yml` **no interviene en ningún despliegue** y sus valores no deben copiarse a Railway: son credenciales de desarrollo escritas en un archivo versionado, que es justo lo que el Art. IV.3 prohíbe fuera de local.

### 2.1 Una sola réplica, y no es un ajuste de coste

El servicio backend corre con **`numReplicas: 1`**, declarado en `railway.json`. Subirlo rompe tres comportamientos que hoy son correctos, y ninguno de los tres falla de forma visible:

| Componente | Qué guarda en memoria del proceso | Qué pasa con dos réplicas |
|---|---|---|
| `AccessRevocationRegistry` | El corte de los tokens de acceso ya emitidos | Desactivar o eliminar a alguien **solo corta en la réplica que atendió la petición**. En la otra, su token sigue abriendo puertas hasta quince minutos (`security.md` §4.5) |
| `RateLimitLedger` | La cuenta de peticiones por origen y por identidad | El techo real **se multiplica por el número de réplicas**: con tres, diez intentos por minuto son treinta (`security.md` §5.5) |
| `FailedAttemptLedger` | Los fallos de identificadores sin cuenta | La respuesta vuelve a distinguir por tiempo qué identificadores existen, que es lo que ese componente existe para impedir |

La purga de sesiones **sí** está preparada para varias réplicas —toma un cerrojo de aviso en el motor— y Flyway también toma el suyo. Los tres de la tabla, no.

Escalar exige, antes, sustituir el registro en memoria por un canal compartido detrás del puerto `AccessRevocationPublisher`. Está registrado como pendiente en §13 y **no toca ningún caso de uso**.

---

## 3. Precondiciones

Antes de crear nada en Railway:

- [ ] La rama que se va a desplegar tiene **CI en verde** (`.github/workflows/ci.yml`). El Art. XI.2 exige que `main` esté siempre desplegable; desplegar una rama roja lo contradice sin discusión.
- [ ] `mvn verify` pasa en local. Si falla aquí, falla en Railway igual y se descubre diez minutos más tarde y pagando la construcción.
- [ ] Existe una cuenta de Railway con acceso al repositorio `NexusPro-Dev/backend`.
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

### 4.2 `SUPERADMIN_PASSWORD_HASH`

La contraseña del superadministrador inicial, **ya cifrada con Argon2id**. La siembra `V22__seed_superadmin.sql` por marcador de posición de Flyway: la migración recibe el resumen, nunca la contraseña.

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

!!! danger "En Railway los `$` van tal cual — NO se duplican"

    El `.env.example` avisa de que en un archivo `.env` los `$` del hash deben ir **duplicados**, porque Docker Compose interpola el archivo y convierte el resumen en un resto irreconocible. **Ese problema es exclusivo de Docker Compose.**

    En una variable de Railway el valor llega al proceso sin interpolar nada: el hash va **exactamente como lo imprimió `jshell`**, empezando por `$argon2id$v=19$m=16384,t=2,p=1$`.

    Duplicarlos aquí produce el fallo simétrico y silencioso: la migración termina con éxito y el superadministrador **no puede entrar nunca**, con un mensaje genérico de credenciales inválidas. La guarda de `V22` detecta el caso contrario —los `$` comidos—, no este.

**Comprobación inmediata:** el valor debe empezar por `$argon2id`. Si no, `V22` aborta la migración con un mensaje explícito y el despliegue no arranca — que es lo que debe pasar.

---

## 5. Crear el proyecto en Railway

Se hace una vez por entorno. El orden importa: la base de datos primero, porque el backend referencia sus variables.

### 5.1 El servicio de base de datos

1. **New Project** → *Deploy PostgreSQL*.
2. Renombrar el servicio a **`Postgres`**. El nombre es literal: las referencias `${{Postgres.PGUSER}}` de §6 lo usan, y si el servicio se llama de otro modo hay que cambiarlas todas.
3. Comprobar en *Settings* que la versión es **PostgreSQL 17**. El Art. V.1 y V.2 admiten un solo motor y el SQL usa características propias del motor; desplegar sobre una línea distinta de la que se prueba en local y en CI con Testcontainers no es una diferencia de detalle.

No hay que crear la base ni las tablas: la base la crea Railway y el esquema lo aplica **Flyway al arrancar la aplicación** (§8).

### 5.2 El servicio de aplicación

1. **New** → *GitHub Repo* → `NexusPro-Dev/backend`.
2. Renombrar el servicio a **`backend`**.
3. *Settings → Source* → fijar la **rama** que se despliega (§10).
4. Railway detecta el `Dockerfile` del raíz y lo usa como constructor. **No hay `startCommand`**: el `ENTRYPOINT` de la imagen ya es `java -jar /app/app.jar`.
5. Cargar las variables de §6 **antes del primer despliegue**. Si arranca sin ellas fallará —correctamente— y habrá que redesplegar.

### 5.3 `railway.json`

El repositorio versiona la configuración del servicio, para que no viva solo en la interfaz de nadie (Art. X.3, X.4):

```json
{
  "$schema": "https://railway.com/railway.schema.json",
  "build": { "builder": "DOCKERFILE", "dockerfilePath": "Dockerfile" },
  "deploy": {
    "numReplicas": 1,
    "healthcheckPath": "/actuator/health/readiness",
    "healthcheckTimeout": 300,
    "restartPolicyType": "ON_FAILURE",
    "restartPolicyMaxRetries": 10
  }
}
```

Cada valor tiene su motivo, y están en §2.1 —la réplica única— y §7 —la sonda y el plazo—.

**Lo que este archivo no lleva son las variables**, y no es un olvido: son secretos, y el Art. IV.3 los mantiene fuera del repositorio en toda forma.

---

## 6. Variables de entorno

Se cargan en el servicio **`backend`**. La columna «Valor en Railway» es literal: lo de `${{…}}` es la sintaxis de referencia entre servicios de la plataforma y se escribe tal cual, no se sustituye a mano.

### 6.1 Base de datos

| Variable | Valor en Railway |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://${{Postgres.RAILWAY_PRIVATE_DOMAIN}}:5432/${{Postgres.PGDATABASE}}` |
| `DATABASE_USER` | `${{Postgres.PGUSER}}` |
| `DATABASE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |

!!! danger "`DATABASE_URL` significa dos cosas distintas, y ahí es donde falla todo el mundo"

    El servicio Postgres de Railway **publica una variable llamada `DATABASE_URL`** con la forma `postgresql://usuario:clave@host:puerto/base`. Es una URI de conexión, no una URL de JDBC.

    Esta aplicación tiene **su propia variable, también llamada `DATABASE_URL`**, y espera la forma **`jdbc:postgresql://host:puerto/base`, sin credenciales dentro**, porque el usuario y la contraseña van por separado (`application.yml`).

    Referenciar `${{Postgres.DATABASE_URL}}` directamente **no funciona**: Spring no reconoce el esquema `postgresql://` y el arranque muere con un error de driver que no menciona nada de esto. Hay que construir la cadena como muestra la tabla.

### 6.2 Red, puerto y entorno

| Variable | Valor en Railway | Por qué |
|---|---|---|
| `PORT` | `8080` | Ver §7.1. **Fijarla explícitamente**, no dejar que la plataforma la elija |
| `ENVIRONMENT` | `production` o `testing` | Art. IX.4. Ver la nota de §6.6 |
| `API_URL` | La URL pública del servicio, sin barra final | Art. IX.1. Ver la nota de §6.6 |

### 6.3 Seguridad

| Variable | Valor en Railway | Por qué |
|---|---|---|
| `JWT_SECRET` | El secreto de §4.1 | Obligatoria y sin valor por defecto |
| `SUPERADMIN_EMAIL` | El correo real del superadministrador | Solo actúa en el primer arranque (§8.2) |
| `SUPERADMIN_PASSWORD_HASH` | El resumen de §4.2, con los `$` **sin duplicar** | Ídem |
| `CORS_ALLOWED_ORIGINS` | El dominio del frontend, o **vacío** | Ver §9. El comodín `*` tumba el arranque |
| `EXPOSE_API_DOCS` | **`false`** en los dos entornos | Ver el aviso de abajo |
| `TRUSTED_PROXIES` | **Vacío** | Ver §11.2. Hoy no hay un valor correcto que poner |
| `RATE_LIMIT_ENABLED` | `true`, o no declararla | Su valor por defecto ya es `true`. **Solo la suite la apaga** |

!!! warning "`EXPOSE_API_DOCS` va en `false`, y el contrato publicado no es razón para cambiarlo"

    Desde [`ADR-001`](architecture/ADR-001-publicacion-del-contrato-openapi.md) el contrato se versiona en `docs/api/openapi.json`, de modo que el frontend no necesita que ningún entorno tenga Swagger abierto. Ese ADR lo deja escrito: que el contrato sea legible en el repositorio **no autoriza** dejar Swagger abierto en un entorno en ejecución, donde además invita a probar contra datos reales.

### 6.4 Operación

| Variable | Valor en Railway | Por qué |
|---|---|---|
| `LOG_LEVEL` | `INFO` | Subirlo a `DEBUG` en un entorno desplegado emite contenido que el enmascaramiento de `security.md` §7.3 no cubre |
| `TOKEN_PURGE_ENABLED` | `true`, o no declararla | Sin ella `refresh_tokens` crece de forma monótona |
| `TOKEN_PURGE_CRON` | No declararla | El valor por defecto —`0 30 3 * * *`, **en UTC**— sirve |
| `TOKEN_PURGE_RETENTION` | No declararla | `P30D`. Acortarlo apaga la detección de robo por reutilización de `RF-SP-035` |
| `REQUEST_LOG_RETENTION_DAYS` | **Vacía** | **Hoy no la lee nadie** (§13). Ponerle un número no purga nada y hace creer que sí |

### 6.5 Envío saliente

| Variable | Valor en Railway | Por qué |
|---|---|---|
| `NOTIFICATION_ENABLED` | `true` en los entornos donde deba funcionar la recuperación de contraseña | Con `false`, `RF-SP-040` emite permisos **que nadie recibe** |
| `RESEND_API_KEY` | La clave `re_…` de Resend | Sin ella el envío queda apagado y se avisa **al arrancar** |
| `NOTIFICATION_FROM` | `NEXUS <no-responder@dominio-verificado>` | El remitente **debe pertenecer a un dominio verificado en Resend** o el proveedor responde `403` |

!!! tip "Verificar el dominio en Resend antes de desplegar, no después"

    El `403` por dominio no verificado **no se ve al arrancar**: la aplicación levanta con normalidad y el fallo aparece la primera vez que alguien olvida su contraseña — que es exactamente cuando nadie está mirando. Y no hay reintento propio: ese mensaje se pierde y solo queda su registro.

### 6.6 Dos variables que la documentación exige y el código todavía no lee

`ENVIRONMENT` y `API_URL` figuran en `architecture.md` §11 y en `.env.example` como obligatorias, y **ninguna clase del backend las consulta hoy**. Se declaran igual, por dos motivos: el Art. IX.4 las exige como parte del contrato de configuración, y el día que algo las lea —un banner de entorno, un enlace absoluto en un correo— no se descubrirá que faltaban en producción.

Queda escrito aquí para que nadie las dé por operativas: **cambiar `ENVIRONMENT` a `production` no cambia hoy ningún comportamiento**. Lo que separa un entorno de otro son las demás variables de esta sección, una por una.

---

## 7. Los tres detalles de la plataforma que rompen el arranque

Ninguno de los tres es un error de la aplicación, y los tres producen síntomas que apuntan al sitio equivocado.

### 7.1 El puerto

`application.yml` fija `server.port: 8080` como literal, y el `Dockerfile` declara `EXPOSE 8080`. Railway, por su parte, inyecta una variable `PORT` y encamina el tráfico al puerto que cree que la aplicación escucha.

La aplicación **no lee `PORT`**. Si la plataforma elige otro número, el borde encamina a un puerto donde no hay nadie: la sonda de salud falla, el despliegue se marca como caído y los logs de la aplicación muestran un arranque perfectamente correcto.

**La solución es declarar `PORT=8080` de forma explícita** (§6.2). Con eso los dos lados coinciden y no hay ambigüedad.

La corrección de fondo —que `application.yml` diga `server.port: ${PORT:8080}` y la aplicación obedezca a la plataforma— está registrada como pendiente en §13. No se ha aplicado aquí porque este documento no toca código.

### 7.2 La red privada es solo IPv6

Los servicios de un proyecto de Railway se ven entre sí por `*.railway.internal`, un nombre que **solo resuelve a AAAA**. No hay dirección IPv4.

Dos consecuencias:

- **La JVM debe estar dispuesta a usarla.** Si la conexión a la base falla con un error de red y el nombre resuelve bien, añadir la variable `JAVA_TOOL_OPTIONS` con valor `-Djava.net.preferIPv6Addresses=true` y volver a desplegar.
- **La red privada tarda unos segundos en estar lista** tras arrancar el contenedor. La aplicación abre la conexión enseguida, porque Flyway corre al inicio; si el primer intento cae en esa ventana, el proceso muere. La política `ON_FAILURE` con diez reintentos de `railway.json` cubre exactamente ese caso: el segundo intento encuentra la red en pie.

**Salida de emergencia**, no la vía normal: el servicio Postgres publica además un acceso público (`RAILWAY_TCP_PROXY_DOMAIN` y `RAILWAY_TCP_PROXY_PORT`). Sirve para desatascar, cuesta tráfico de salida y expone la base fuera de la red del proyecto. Volver a la red privada en cuanto se pueda.

### 7.3 Durante un redespliegue conviven dos instancias

Railway levanta la nueva versión y espera a que su sonda responda antes de retirar la anterior. Durante ese relevo hay **dos procesos vivos**, aunque `numReplicas` sea 1.

Qué implica, dicho sin adornos:

- **El esquema está a salvo.** Flyway toma un cerrojo en el motor: si las dos instancias intentan migrar, una espera y la otra aplica.
- **El estado en memoria se pierde.** Los cortes de acceso, las cuentas de límite de tasa y los fallos por identificador **empiezan de cero** en la instancia nueva. Los cortes sí se resiembran al arrancar; las otras dos cuentas, no. Es aceptable —la ventana es de segundos— y conviene saberlo antes de investigar por qué un contador se reinició solo.
- **Una migración incompatible hacia atrás rompe la instancia vieja** mientras siga sirviendo. Toda migración debe poder convivir con la versión anterior del código durante el relevo: añadir columnas anulables antes de usarlas, y separar en dos despliegues el «dejar de escribir» del «eliminar».

El **apagado ordenado** de Spring Boot no está habilitado, de modo que el relevo puede cortar una petición en curso. Está registrado en §13.

---

## 8. El primer arranque

### 8.1 Migraciones

Flyway corre **dentro del proceso, al arrancar**, contra `classpath:db/migration`, con `validate-on-migrate` y **sin `baseline-on-migrate`**. No hay paso de despliegue que aplique migraciones aparte, y no debe haberlo: el esquema es la fuente de verdad (Art. V.3) y llega con el artefacto.

De ahí se siguen tres cosas:

- Una base **vacía** es el estado correcto de partida. Railway la entrega así.
- Una base **con tablas y sin historial de Flyway** falla a propósito. No se «arregla» activando `baseline-on-migrate`: eso asume que lo que hay coincide con lo esperado, que es justo lo que nadie ha comprobado.
- Hibernate corre con `ddl-auto: validate`. Si el esquema no coincide con el mapeo, el arranque muere con el detalle exacto de la discrepancia — y eso es una funcionalidad, no un obstáculo.

El primer arranque **tarda**: aplica todas las migraciones antes de que el puerto responda. Por eso `healthcheckTimeout` está en 300 segundos, y por eso la sonda es `/readiness` y no `/liveness` (`architecture.md` §9.1).

### 8.2 El superadministrador

`V22__seed_superadmin.sql` siembra la única fila de `users` con identificador conocido, con el correo y el resumen de §4. Después de eso:

- [ ] Iniciar sesión con esa credencial **una vez**, y **cambiar la contraseña de inmediato**. La de §4.2 pasó por una terminal y por el portapapeles.
- [ ] Crear las cuentas reales desde la API. La del superadministrador es la llave del sistema, no una cuenta de trabajo.

`SUPERADMIN_EMAIL` y `SUPERADMIN_PASSWORD_HASH` **solo actúan en el primer arranque**: la migración ya está aplicada y cambiar su valor después no cambia nada. Se dejan declaradas de todos modos — retirarlas rompería una reconstrucción desde cero.

---

## 9. Dominio, HTTPS y CORS

**HTTPS lo pone la plataforma.** Railway asigna un dominio `*.up.railway.app` con certificado, y admite dominio propio por CNAME. La aplicación no termina TLS ni lo sabe: recibe HTTP en su puerto, detrás del borde. Con eso se satisface el Art. IV.6 y `security.md` §7.2.

**CORS lo pone la aplicación**, y es el paso que se olvida:

- `CORS_ALLOWED_ORIGINS` lleva los orígenes del **navegador**, separados por coma, con la forma `esquema://host[:puerto]`, **sin barra final y sin ruta**.
- **Vacío es ningún origen autorizado.** Es lo seguro, y no rompe a quien consume la API de servidor a servidor: eso no pasa por CORS.
- **`*` tumba el arranque**, a propósito (`security.md` §6.1). También lo tumba un origen sin esquema o con barra final, que nunca casaría y cuyo fallo aparecería semanas después como un error de CORS en el navegador de otra persona.
- Los orígenes de `docker-compose.yml` —los `localhost` de Vite, Next y Angular— **no van aquí jamás**. Autorizarlos en un entorno desplegado abre la API al navegador de cualquiera que corra un frontend en su máquina.

Un `production` cuyo frontend viva en `https://app.nexus.co` lleva exactamente eso, y nada más.

---

## 10. Ramas, entornos y promoción

| Entorno de Railway | `ENVIRONMENT` | Rama vigilada | Para qué |
|---|---|---|---|
| `testing` | `testing` | `develop` | Verificar lo integrado antes de que llegue a `main` |
| `production` | `production` | `main` | El sistema real |

Cada entorno tiene **su propio servicio Postgres y su propio juego completo de variables**. No se comparte la base, ni el `JWT_SECRET`, ni la clave de Resend.

El flujo es el del Art. XI.1 y `development-guide.md` §12, sin nada añadido: `feature/*` → `develop` → `main`. **Desplegar es integrar**; no hay una acción de despliegue separada que alguien pueda olvidar o disparar por su cuenta.

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

### 11.2 Lo que la verificación NO puede comprobar hoy

**La IP que queda en la auditoría es la del borde de Railway, no la de quien llamó.**

`ClientIpResolver` solo confía en `X-Forwarded-For` si la IP del par inmediato figura en `TRUSTED_PROXIES`, y esa lista es de **coincidencia exacta**: no admite rangos ni CIDR. La IP con la que el borde de Railway habla con el contenedor no es un valor fijo ni publicado, de modo que **hoy no existe un valor correcto que poner en esa variable**.

La consecuencia está acotada y hay que conocerla: los cinco registros que viven en PostgreSQL apuntan a la dirección del proxy —un dato incompleto pero **cierto**— en lugar de a una que el atacante elige escribiendo una cabecera. Es exactamente el comportamiento que ese componente busca cuando no hay lista, y por eso `TRUSTED_PROXIES` va **vacía** (§6.3).

Lo que no está resuelto es el Art. V.15 en su totalidad: **desde dónde se hizo cada operación no se responde en Railway hoy**. Eso reabre **D-21** con una forma nueva —no es «qué IPs poner» sino «el resolvedor necesita admitir rangos»— y queda registrado en §13 y en `security.md` §12.

---

## 12. Operación

| Necesidad | Cómo |
|---|---|
| **Ver los logs** | Pestaña *Deployments* del servicio, o `railway logs`. Es la salida estándar del proceso: el log de aplicación de `architecture.md` §9, ya enmascarado |
| **Redesplegar sin cambios** | *Deployments* → *Redeploy*. Reconstruye desde el mismo commit |
| **Revertir** | *Deployments* → seleccionar un despliegue anterior → *Redeploy*. **Revierte el código, nunca el esquema**: lo que Flyway aplicó, aplicado queda |
| **Cambiar una variable** | *Variables* → editar. Railway **reinicia el servicio**: es un corte breve, y el estado en memoria de §7.3 se pierde |
| **Entrar a la base** | *Postgres → Data*, o `psql` por el proxy TCP. Toda consulta contra datos reales tiene delante el Art. IV y `security.md`: se lee lo que hace falta y no se escribe a mano |
| **Copias de seguridad** | Las gestiona Railway en el servicio Postgres. **Verificar que están activas y que una restauración funciona** antes de que haya datos reales. Una copia que nadie ha restaurado nunca no es una copia |
| **Métricas** | `/actuator/metrics`, autenticado y **a mano** (§13) |

!!! warning "Revertir un despliegue no revierte una migración"

    Volver al despliegue anterior deja corriendo un código antiguo contra un esquema nuevo. Si la migración era compatible hacia atrás —§7.3—, funciona. Si no lo era, la reversión **no arregla nada y puede empeorarlo**: `ddl-auto: validate` abortará el arranque, que es el mejor de los desenlaces posibles.

    La salida de una migración mala es **hacia adelante**: otra migración que corrija. No hay botón para lo otro.

---

## 13. Lo que este despliegue no resuelve

Ninguno de estos puntos impide desplegar. Todos están declarados para que no se confundan con lo hecho.

| # | Pendiente | Consecuencia hoy | Dónde se corrige |
|---|---|---|---|
| 1 | **La aplicación no lee `PORT`** | El puerto se hace coincidir a mano con una variable (§7.1). Un cambio de la plataforma lo rompería sin avisar | `application.yml`, una línea |
| 2 | **Una sola réplica** | No hay escalado horizontal ni tolerancia a la caída del único proceso | Canal compartido detrás de `AccessRevocationPublisher` (§2.1) |
| 3 | **La IP de auditoría es la del proxy** | El Art. V.15 no se cumple en Railway | `ClientIpResolver` debe admitir rangos. **D-21** |
| 4 | **Nadie raspa las métricas** | `/actuator/metrics` exige un JWT y un raspador no lo porta. Se consultan a mano | Permiso propio o red de administración |
| 5 | **Nadie alerta** | En particular, sigue sin vigilarse la **ausencia de eventos de auditoría**, que `RF-SP-001` §10 declara que debería. Una métrica que nadie mira es una métrica que no existe | — |
| 6 | **`request_log` crece sin techo** | La tabla existe desde `V35` y **ningún proceso la purga**, aunque la variable exista | **D-10** |
| 7 | **Sin apagado ordenado** | Un relevo puede cortar una petición en curso (§7.3) | `server.shutdown: graceful` más el drenaje de la plataforma |
| 8 | **El contrato no llega solo al frontend** | Sigue siendo un archivo que alguien copia | Pendiente n.º 1 de [`ADR-001`](architecture/ADR-001-publicacion-del-contrato-openapi.md) |

---

## 14. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 27-08-2026 | Creación inicial. Recoge el procedimiento de despliegue sobre Railway que [`ADR-002`](architecture/ADR-002-plataforma-de-despliegue-railway.md) decide al cerrar **D-09**: topología de dos servicios, mapa completo de variables con su valor literal, los tres detalles de plataforma que rompen el arranque —el puerto, la red privada IPv6 y el relevo con dos instancias vivas—, la verificación posterior y la operación. Declara **una sola réplica** como restricción de diseño y no de coste, con los tres componentes en memoria que la imponen; declara que **`TRUSTED_PROXIES` no tiene hoy valor correcto** en Railway, lo que reabre **D-21** con otra forma; y separa lo desplegado de lo pendiente en §13. | Responsable técnico |
