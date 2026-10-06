# Despliegue — Backend NEXUS

| Campo | Valor |
|---|---|
| Proyecto | NEXUS — Renovación de plataforma |
| Empresa | FACTECH GROUP SAS |
| Documento | `deployment.md` |
| Versión | 0.16.0 |
| Estado | Borrador |
| Responsable técnico | Bonilla Diaz William Steven |
| Fecha de creación | 27-08-2026 |
| Última actualización | 02-10-2026 |
| Documento superior | `constitution.md` v0.7.0 |
| Documentos relacionados | `architecture.md` v0.21.0 · `security.md` v0.35.0 · [`ADR-002`](architecture/ADR-002-plataforma-de-despliegue-railway.md) |
| Documento derivado | [`manual-de-despliegue.md`](manual-de-despliegue.md) v0.2.0 — el paso a paso |

---

## 1. Propósito y alcance

Este documento responde una pregunta concreta: **cómo se lleva este backend a un entorno en ejecución que no es la máquina de nadie**. Es el procedimiento operativo del despliegue, no la justificación de la plataforma: por qué Railway y no otra cosa está en [`ADR-002`](architecture/ADR-002-plataforma-de-despliegue-railway.md), que cierra la decisión **D-09**.

**Dentro del alcance:** los dos entornos desplegados que exige el Art. IX.4 —`testing` y `production`—, sobre Railway; el mapa completo de variables de entorno con el valor exacto que va en cada una; los tres detalles de la plataforma que rompen el arranque si nadie los mira; la verificación posterior; y la operación del día a día —logs, redespliegue, reversión, acceso a la base—.

**Fuera del alcance:** el entorno local, que vive en [`development-guide.md` §2](development-guide.md#2-puesta-en-marcha-del-entorno-local) y en el `docker-compose.yml`; el despliegue del frontend, que es otro repositorio; y la publicación del sitio de documentación, que la hace GitHub Pages desde `.github/workflows/docs.yml` y no tiene nada que ver con esto.

!!! tip "¿Vas a desplegar ahora mismo? Usa el [manual](manual-de-despliegue.md)"

    Este documento es la **referencia**: qué es cada cosa y por qué. Para hacerlo, con Railway abierto delante, el [**manual de despliegue**](manual-de-despliegue.md) lleva los once pasos en orden, con los comandos exactos y el bloque de variables listo para pegar.

    Si los dos se contradicen, manda este.

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

#### 4.1.1 `MFA_ENCRYPTION_KEY`

La llave que cifra el **secreto del segundo factor** de cada persona ([`security.md` §3.3](security.md)). Desde el 06-10-2026, con `V75`.

```bash
openssl rand -base64 32
```

Treinta y dos bytes en Base64, **distinta en cada entorno** y obligatoria: **sin ella el servicio no arranca**, porque no hay forma segura de guardar el factor de nadie. Al revés que `JWT_SECRET`, **no se rota a la ligera**: los secretos guardados solo se descifran con la llave que los cifró, de modo que cambiarla —o perderla— deja a todo el mundo sin poder usar su authenticator, y habría que restablecerlos uno a uno (`RF-SP-076`). Se guarda y se respalda **junto con `JWT_SECRET`**.

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

!!! danger "En Railway los `$` van tal cual — NO se duplican"

    El `.env.example` avisa de que en un archivo `.env` los `$` del hash deben ir **duplicados**, porque Docker Compose interpola el archivo y convierte el resumen en un resto irreconocible. **Ese problema es exclusivo de Docker Compose.**

    En una variable de Railway el valor llega al proceso sin interpolar nada: el hash va **exactamente como lo imprimió `jshell`**, empezando por `$argon2id$v=19$m=16384,t=2,p=1$`.

    Duplicarlos aquí produce el fallo simétrico y silencioso: la migración termina con éxito y el superadministrador **no puede entrar nunca**, con un mensaje genérico de credenciales inválidas. La guarda de `V9` detecta el caso contrario —los `$` comidos—, no este.

**Comprobación inmediata:** el valor debe empezar por `$argon2id`. Si no, `V9` aborta la migración con un mensaje explícito y el despliegue no arranca — que es lo que debe pasar.

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
| `PORT` | **No declararla** | La inyecta Railway y la aplicación la obedece (§7.1). Declararla a mano solo tiene sentido para forzar un puerto concreto |
| `ENVIRONMENT` | `production` o `testing` | **Obligatoria de verdad desde el 31-08-2026: sin ella el servicio NO ARRANCA.** Decide si se siembran datos de prueba. Ver §6.6 |
| `DEV_SEED_ENABLED` | **No declararla** | La semilla no se aplica en `production` lo diga esta lo que diga. En `testing`, ponerla en `false` es lo que la desactiva |
| `API_URL` | La URL pública del servicio, sin barra final | Art. IX.1. Declarada y todavía sin lector, ver §6.6 |

### 6.3 Seguridad

| Variable | Valor en Railway | Por qué |
|---|---|---|
| `JWT_SECRET` | El secreto de §4.1 | Obligatoria y sin valor por defecto |
| `MFA_ENCRYPTION_KEY` | La llave de §4.1.1 | Obligatoria y sin valor por defecto. **No se cambia**: cambiarla deja ilegibles los segundos factores |
| `SUPERADMIN_EMAIL` | El correo real del superadministrador | Solo actúa en el primer arranque (§8.2) |
| `SUPERADMIN_PASSWORD_HASH` | El resumen de §4.2, con los `$` **sin duplicar** | Ídem |
| `CORS_ALLOWED_ORIGINS` | El dominio del frontend, o **vacío** | Ver §9. El comodín `*` tumba el arranque |
| `EXPOSE_API_DOCS` | **`false`** en los dos entornos | Ver el aviso de abajo |
| `TRUSTED_PROXIES` | **Vacío** | Ver §11.2. El resolvedor ya admite rangos; falta **decidir cuál** declarar (D-21) |
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
| `NOTIFICATION_TEMPLATE_PASSWORD_RECOVERY` | `nexus-password-recovery` | Plantilla con estilos del correo de `RF-SP-040`. **Vacía** manda el mensaje en texto plano; **con un valor que no exista** el proveedor rechaza el envío y el correo se pierde |

!!! tip "Verificar el dominio en Resend antes de desplegar, no después"

    El `403` por dominio no verificado **no se ve al arrancar**: la aplicación levanta con normalidad y el fallo aparece la primera vez que alguien olvida su contraseña — que es exactamente cuando nadie está mirando. Y no hay reintento propio: ese mensaje se pierde y solo queda su registro.

### 6.5.1 La pasarela de la tarjeta (Stripe)

| Variable | Valor en Railway | Por qué |
|---|---|---|
| `STRIPE_SECRET_KEY` | Una **clave restringida** de Stripe (`rk_test_…` en pruebas, `rk_live_…` en producción), con permiso de escritura solo sobre los cobros (*PaymentIntents*) | Sin ella la pasarela queda **apagada** y se avisa al arrancar: la tarjeta nace pendiente y la confirma una persona. **Restringida y no la secreta completa**: si se filtra, no abre la cuenta entera |
| `STRIPE_WEBHOOK_SECRET` | El `whsec_…` del endpoint registrado en el panel de Stripe —**uno por entorno**— | Verifica la firma de cada notificación. Sin él la ruta responde `503` y no procesa nada |

**El endpoint se registra en el panel de Stripe** apuntando a `https://<dominio del backend>/api/v1/movements/gateway-notifications/stripe`, suscrito como mínimo a `payment_intent.succeeded`, `payment_intent.payment_failed`, `payment_intent.canceled`, `charge.refunded`, `charge.dispute.created` y `charge.dispute.closed`. **La clave publicable (`pk_…`) no es del backend**: la usa el frontend para pintar el formulario de la tarjeta.

**En local no hay endpoint registrado**, y sin él las notificaciones no llegan: el cobro sale `succeeded` en Stripe y la venta se queda `PENDIENTE` aquí. **Desde el 02-10-2026 las reenvía el servicio `stripe` del `docker-compose.yml`** (`nexus-stripe`, la Stripe CLI fijada a `v1.53.0`), que arranca con `docker compose up`, usa la misma `STRIPE_SECRET_KEY` que la aplicación y reenvía a `http://app:8080/api/v1/movements/gateway-notifications/stripe` los seis eventos de arriba. **El `whsec_…` que va en `STRIPE_WEBHOOK_SECRET` del `.env` es el que imprime `stripe listen --print-secret` con esa clave**, y es estable: no cambia de un arranque a otro. Sin `STRIPE_SECRET_KEY` el servicio se queda dormido y lo dice en su log, sin tumbar el resto. Lo que reenvía se ve en `docker logs nexus-stripe`, y un aviso perdido se recupera con `stripe events resend evt_…`. Con la aplicación fuera de Docker (`mvn spring-boot:run`) sigue valiendo correr a mano `stripe listen --forward-to localhost:8080/api/v1/movements/gateway-notifications/stripe`.

### 6.5.2 La pasarela local (PayRetailers)

| Variable | Valor en Railway | Por qué |
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

### 6.6 `ENVIRONMENT` ya se lee; `API_URL` todavía no

**Desde el 31-08-2026 `ENVIRONMENT` decide algo, y por eso un valor equivocado tumba el arranque.**

Hasta esa fecha esta sección decía que ninguna clase la consultaba y que cambiarla a `production` no cambiaba ningún comportamiento. Ya no es cierto:

| Valor | Qué ocurre al arrancar |
|---|---|
| `production` | No se siembra nada. En el log queda la línea que lo dice |
| `testing` · `development` | Se aplican las **tres** semillas de `db/dev-seed/`, en orden y en una sola transacción: `semilla-desarrollo.sql` —**diecinueve personas de prueba** con sus roles, documento `CC`, teléfono, su estructura comercial —cada director con tres a cargo— y sus membresías; desde el 21-09-2026 **completa el documento a quien ya existía sin él**, porque la semilla solo crea a quien falta y una base sembrada antes conservaba a las diecinueve sin `CC`— y, desde el 14-09-2026, `semilla-productos.sql` —**un catálogo de dieciséis productos**: once upgrades (un salto desde cada membresía, las cuatro renovaciones con la de Beca a precio cero, y uno inactivo) y cinco bots (uno gratuito, uno con precio de compra declarado y uno retirado)—. Los activos nacen ya activos, con descripción, en USD y con alcances e implementaciones mezclados, para que la oferta, el hotlink, el catálogo administrativo y la entrega manual tengan algo que enseñar sin sembrar nada a mano. Y desde el 01-10-2026, `semilla-tasas-comision.sql` —**las tasas de comisión de rol** de esos productos: AGENTE, DIRECTOR y MANAGER en cada producto vivo, por porcentaje (10 / 7 / 5 % en los upgrades con precio y en `BOT_PRO_ANUAL`) o por importe fijo (dos bots y los dos gratuitos), y ninguna en el retirado— |
| Cualquier otra cosa, o ausente | **La aplicación NO ARRANCA**, nombrando el valor recibido y los tres admitidos |

!!! danger "Un despliegue sin `ENVIRONMENT` declarada deja de arrancar"

    Es un cambio de comportamiento, no un matiz. Antes la variable se ignoraba y el servicio levantaba igual; ahora su ausencia es un fallo de arranque (Art. IX.5). **Compruebe que está declarada en cada entorno de Railway antes de desplegar esto.**

    La alternativa —asumir un valor por defecto— es lo que ese artículo prohíbe, y aquí el precio de acertar mal es concreto: `Production`, `prod` o el vacío contarían todos como «no es producción» y sembrarían diecinueve cuentas en el sistema real.

**Qué son esas diecinueve personas, y por qué esto se mira dos veces.** Comparten el **hash de contraseña del superadministrador** y nacen **sin marca de cambio obligatorio**, al revés que cualquier alta por la API. En `development` es justo lo que se quiere. En `testing` —que es un entorno **desplegado y alcanzable**— hay que saber que quedan nombres de usuario adivinables (`admin1`, `cliente1`) sobre un host público; lo que **no** añade es una credencial nueva, porque quien pueda entrar con ellas ya podía entrar como SUPERADMIN.

**El guion viaja dentro del artefacto de producción**, y tiene que hacerlo: el classpath es el mismo para todos los entornos. Lo que lo separa de esas cuentas **no es la ausencia del archivo, es el guardia** — y por eso hay una prueba dedicada (`ProductionSeedIT`) que enciende el interruptor a propósito para comprobar que en producción no siembra igual.

`DEV_SEED_ENABLED` apaga la semilla sin tocar el entorno. Existe porque **la suite la apaga** —sus pruebas cuentan personas y roles—, y **no puede reabrir producción**: el entorno se comprueba primero y por separado.

**`API_URL` sigue declarada y sin lector.** Se mantiene por el Art. IX.4 y para que el día que algo la lea no se descubra que faltaba en producción.

---

## 7. Los tres detalles de la plataforma que rompen el arranque

Ninguno de los tres es un error de la aplicación, y los tres producen síntomas que apuntan al sitio equivocado.

### 7.1 El puerto

Railway inyecta una variable `PORT` y encamina el tráfico al puerto que cree que la aplicación escucha. **La aplicación la obedece**: `application.yml` declara `server.port: ${PORT:8080}`, de modo que en un entorno desplegado manda la plataforma y en local siguen valiendo los 8080 de siempre —los que declara el `Dockerfile` y espera el `docker-compose.yml`—.

**No hay que declarar `PORT` en Railway** (§6.2). Hacerlo solo tiene sentido para forzar un puerto concreto, y no hace falta.

Lo que había antes, por si alguien se lo encuentra en una rama vieja: `server.port` era el literal `8080` y obligaba a fijar `PORT=8080` a mano para que los dos lados coincidieran. El día que dejaran de coincidir, el síntoma era **una sonda de salud en rojo sobre un arranque impecable en los logs** — es decir, el puerto es lo último que uno mira.

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

**El apagado ordenado sí está habilitado**, y es lo que hace tolerable el relevo: `server.shutdown: graceful` con treinta segundos de plazo (`spring.lifecycle.timeout-per-shutdown-phase`). Al recibir la orden de parar, la instancia vieja deja de aceptar peticiones nuevas y termina las que tiene, en lugar de cortarlas en seco — que para quien estaba escribiendo es una conexión caída, indistinguible de que el sistema esté roto.

Treinta segundos sobran para cualquier operación de este sistema —los umbrales del Art. XV.9 son de menos de un segundo— y quedan por debajo del plazo con el que la plataforma mata el proceso a la fuerza.

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

`V9__semilla_catalogos_y_superadmin.sql` siembra la única fila de `users` con identificador conocido, con el correo y el resumen de §4. Después de eso:

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

!!! danger "Esta tabla describe el destino, y a 27-08-2026 todavía no es cierta"

    Todo el trabajo vive en `feature/esqueleto-del-proyecto`, sin fusionar: `develop` y `main` van **veintitrés commits por detrás** y **no llevan ni `railway.json` ni el `server.port: ${PORT:8080}`**. Desplegar cualquiera de las dos produce «Application failed to respond» con un arranque impecable en los logs, por lo que dice §7.1.

    Hasta que se fusione, el servicio apunta a la rama de trabajo. **La comprobación de una línea:** una rama es desplegable si contiene `railway.json`.

    Es la misma advertencia que la portada de la documentación lleva desde el principio —«el trabajo vive en ramas `feature/…` sin fusionar»— vista desde el despliegue, que es donde muerde.

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

**La IP que queda en la auditoría es la del borde de Railway, no la de quien llamó — mientras `TRUSTED_PROXIES` siga vacía.**

`ClientIpResolver` solo confía en `X-Forwarded-For` si la IP del par inmediato figura en `TRUSTED_PROXIES`. Desde el 27-08-2026 esa lista **admite rangos CIDR** además de direcciones sueltas, que es lo que faltaba: la dirección con la que el borde de Railway habla con el contenedor no es fija ni publicada, pero **la red de la que sale sí se puede declarar**.

Lo que queda es **elegir ese rango**, y no es un trámite. Confiar en un bloque es confiar en todo lo que salga de él: quien pueda emitir peticiones desde dentro escribe en la auditoría la IP que quiera. La declaración debe ser la más estrecha que la plataforma permita, y hasta que se decida cuál es, la variable va **vacía** (§6.3).

Con ella vacía la consecuencia está acotada y hay que conocerla: los cinco registros que viven en PostgreSQL apuntan a la dirección del proxy —un dato incompleto pero **cierto**— en lugar de a una que el atacante elige escribiendo una cabecera. Es exactamente el comportamiento que ese componente busca cuando no hay lista.

De modo que el Art. V.15 sigue sin responderse en Railway **hoy**, pero ya no por falta de mecanismo: **D-21 vuelve a ser una decisión de configuración**, registrada en §13 y en `security.md` §12.

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
| 1 | **Una sola réplica** | No hay escalado horizontal ni tolerancia a la caída del único proceso | Canal compartido detrás de `AccessRevocationPublisher` (§2.1) |
| 2 | **La IP de auditoría es la del proxy** | El Art. V.15 no se cumple en Railway **mientras `TRUSTED_PROXIES` siga vacía** | El resolvedor ya admite rangos (27-08-2026). Falta **decidir qué rango** declarar: **D-21** |
| 3 | **Nadie raspa las métricas** | `/actuator/metrics` exige un JWT y un raspador no lo porta. Se consultan a mano | Permiso propio o red de administración |
| 4 | **Nadie alerta** | En particular, sigue sin vigilarse la **ausencia de eventos de auditoría**, que `RF-SP-001` §10 declara que debería. Una métrica que nadie mira es una métrica que no existe | — |
| 5 | **`request_log` crece sin techo** | La tabla existe desde `V35` y **ningún proceso la purga**, aunque la variable exista | **D-10** |
| 6 | **El contrato no llega solo al frontend** | Sigue siendo un archivo que alguien copia | Pendiente n.º 1 de [`ADR-001`](architecture/ADR-001-publicacion-del-contrato-openapi.md) |

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
