# Manual de despliegue — Backend NEXUS

| Campo | Valor |
|---|---|
| Proyecto | NEXUS — Renovación de plataforma |
| Empresa | FACTECH GROUP SAS |
| Documento | `manual-de-despliegue.md` |
| Versión | 0.3.0 |
| Estado | Borrador |
| Responsable técnico | Bonilla Diaz William Steven |
| Fecha de creación | 27-08-2026 |
| Última actualización | 14-10-2026 |
| Documento superior | [`deployment.md`](deployment.md) v0.26.0 |

---

!!! info "Este documento es el **manual**; [`deployment.md`](deployment.md) es la **referencia**"

    Aquí está **qué tecleas y en qué orden**, la primera vez, con el panel de Dokploy abierto delante. Nada de por qué.

    El **por qué** de cada decisión —por qué una sola réplica, por qué 35 segundos de gracia, por qué la subred de `dokploy-network` en `TRUSTED_PROXIES`— está en [`deployment.md`](deployment.md), y cada paso enlaza a su sección.

    Si algo de aquí contradice a `deployment.md`, manda `deployment.md`.

**Tiempo estimado:** 1–2 horas la primera vez, contando el servidor. El segundo entorno, 20 minutos.

---

## Antes de empezar

Ten esto a mano **antes** de abrir nada. Ir a buscarlo a mitad de camino es de donde salen la mitad de los errores:

- [ ] El repositorio clonado y `mvn verify` **en verde** en tu máquina.
- [ ] `openssl` disponible (viene con Git Bash en Windows).
- [ ] JDK 21 en el `PATH` — hace falta `jshell` para el paso 4.
- [ ] Un **VPS contratado**: Ubuntu 24.04 LTS, **4 vCPU, 8 GB de RAM, 80 GB NVMe** como mínimo ([`deployment.md` §5.1](deployment.md#51-el-servidor)), con tu llave SSH cargada.
- [ ] Acceso al **DNS** del dominio. Vas a crear tres registros `A`: el panel, la API de `testing` y la de `production`.
- [ ] Un **almacenamiento compatible con S3** para las copias —Cloudflare R2, Backblaze B2 o el del proveedor— con un bucket creado y su clave de acceso.
- [ ] Permiso de administración en la organización `NexusPro-Dev` de GitHub: vas a crear entornos, secretos y un token de lectura de paquetes.
- [ ] **Decidido** el correo real del superadministrador y su contraseña.

!!! warning "Elige la contraseña del superadministrador antes de empezar"

    No es una contraseña de prueba. Es **la única llave de entrada** al sistema recién desplegado: el primer superadministrador no puede crearse por la API. Mínimo 12 caracteres.

    La vas a cambiar en el paso 12, pero hasta entonces es la que hay.

---

## Paso 1 — Preparar el servidor

**En el panel de tu proveedor**, antes de entrar al servidor: crea un **firewall** y asígnaselo, con entrada permitida solo a `22`, `80`, `443` y, de momento, `3000`.

!!! danger "El firewall tiene que ser el del proveedor, no UFW"

    Docker publica puertos con sus propias reglas de `iptables`, que van por delante de las de UFW. Un `ufw deny 5432` en el servidor **no cierra** un puerto que Docker haya abierto. Ver [`deployment.md` §5.1](deployment.md#51-el-servidor).

Después, por SSH como `root`:

```bash
apt update && apt upgrade -y
apt install -y unattended-upgrades
dpkg-reconfigure -plow unattended-upgrades
```

Y en `/etc/ssh/sshd_config`, `PasswordAuthentication no`. Reinicia con `systemctl restart ssh` **sin cerrar la sesión abierta**, y comprueba desde otra terminal que sigues pudiendo entrar con tu llave.

- [ ] El firewall del proveedor está asignado al servidor.
- [ ] SSH solo admite llave.

---

## Paso 2 — Instalar Dokploy

```bash
curl -sSL https://dokploy.com/install.sh | sh
```

Al terminar, abre `http://IP-DEL-SERVIDOR:3000` y **crea la cuenta de administración**.

Luego, en este orden:

1. En tu DNS, un registro `A` —por ejemplo `panel.tu-dominio`— hacia la IP del servidor.
2. *Settings → Server* en Dokploy: ese dominio, con **Let's Encrypt**.
3. Abre `https://panel.tu-dominio`. Si entra con candado, **quita el `3000` del firewall del proveedor**.
4. *Settings → Profile*: activa el **doble factor**.

- [ ] El panel responde por `https://` y su certificado es válido.
- [ ] El `3000` ya no está abierto en el firewall.
- [ ] La cuenta de administración tiene doble factor.

---

## Paso 3 — Generar los secretos

En una terminal, en cualquier carpeta, **una vez por entorno**:

```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 32   # MFA_ENCRYPTION_KEY
openssl rand -base64 32   # PAYRETAILERS_ENCRYPTION_KEY
openssl rand -hex 24      # BROKER_NOTIFICATION_TOKEN
```

- [ ] Guardados en un gestor de contraseñas, **no** en un archivo del proyecto ni en un chat.
- [ ] **Uno distinto para cada entorno.** Compartir `JWT_SECRET` significa que un token de pruebas vale en producción.
- [ ] Sabes que `MFA_ENCRYPTION_KEY` y `PAYRETAILERS_ENCRYPTION_KEY` **no se cambian nunca** y **no viajan en las copias de la base**: si se pierden, los datos que cifran son ilegibles.

---

## Paso 4 — Generar el hash de la contraseña

En la carpeta del repositorio:

```bash
mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
jshell --class-path "$(cat cp.txt)"
```

Cuando aparezca el `jshell>`, pega estas dos líneas cambiando la contraseña:

```java
var encoder = new org.springframework.security.crypto.argon2.Argon2PasswordEncoder(16, 32, 1, 16384, 2);
System.out.println(encoder.encode("TU-CONTRASENA-REAL"));
```

Sal con `/exit` y limpia:

```bash
rm cp.txt
```

**Lo que debes tener copiado** es algo con esta forma:

```
$argon2id$v=19$m=16384,t=2,p=1$c29tZXNhbHQ$H2f4...
```

- [ ] Empieza por `$argon2id`. Si no, algo salió mal — repite.
- [ ] **Los `$` van tal cual.** No los dupliques. La duplicación es solo para el archivo `.env` local, y hacerla en Dokploy deja al superadministrador sin poder entrar nunca, con la migración en verde y sin ningún error. Ver [`deployment.md` §4.2](deployment.md#42-superadmin_password_hash).

---

## Paso 5 — Preparar GitHub

**Un token para que el servidor descargue las imágenes.** Los paquetes de GHCR son privados.

1. GitHub → *Settings → Developer settings → Personal access tokens → Tokens (classic)* → nuevo token con **solo** `read:packages`.
2. En Dokploy, *Settings → Registry* → *Add registry*: URL `ghcr.io`, tu usuario de GitHub y ese token como contraseña.

**Los entornos y sus secretos**, en el repositorio `NexusPro-Dev/backend` → *Settings → Environments*. Crea **`testing`** y **`production`**, y en cada uno:

| Secreto | Valor |
|---|---|
| `DOKPLOY_URL` | `https://panel.tu-dominio`, **sin barra final** |
| `DOKPLOY_API_KEY` | Generada en Dokploy: *Settings → Profile → API/CLI* |
| `DOKPLOY_APPLICATION_ID` | Lo rellenas en el paso 8, cuando exista el servicio |

**No crees todavía** la variable `DOKPLOY_DEPLOY`. Va en el paso 9.

- [ ] Dokploy tiene el registro `ghcr.io` guardado.
- [ ] Los dos entornos existen en GitHub con `DOKPLOY_URL` y `DOKPLOY_API_KEY`.

---

## Paso 6 — Publicar la primera imagen

El servicio de Dokploy necesita que la imagen **ya exista** en GHCR. La primera la publicas a mano, desde la carpeta del repositorio, en la rama del entorno:

```bash
echo TU-TOKEN-CON-write:packages | docker login ghcr.io -u TU-USUARIO --password-stdin
docker build -t ghcr.io/nexuspro-dev/backend:develop .
docker push ghcr.io/nexuspro-dev/backend:develop
```

Para esto hace falta un token con `write:packages`, **distinto** del del paso 5, que solo lee. Bórralo al terminar: a partir del paso 9 publica el CI, con su propio token.

- [ ] En GitHub, *Packages* de la organización muestra `backend` con la etiqueta `develop`.

---

## Paso 7 — Crear el proyecto y la base de datos

En Dokploy:

1. *Projects* → **Create Project** → `nexus`.
2. Dentro, crea el entorno **`testing`** (y más tarde `production`).
3. En `testing`: *Create Service* → *Database* → **PostgreSQL**.
   - Nombre: `postgres`.
   - *Database Name*: `nexus`.
   - *Docker Image*: **`postgres:17`**. No la última: [`deployment.md` §5.2](deployment.md#52-los-servicios-de-cada-entorno).
   - Usuario y contraseña: deja que los genere.
4. *Deploy* en el servicio.
5. Abre *Internal Credentials* y **apunta tres cosas**: *Internal Host*, *User* y *Password*.
6. *Advanced → Resources*: límite de memoria **512 MB** en `testing` (**1 GB** en `production`).

**No publiques el puerto** de la base (*External Port* vacío) y **no crees tablas**. El esquema lo aplica Flyway cuando arranque la aplicación.

- [ ] La base está en verde.
- [ ] Tienes el *Internal Host* apuntado, algo como `nexus-postgres-a1b2c3`.

---

## Paso 8 — Crear el servicio del backend

En el mismo entorno: *Create Service* → **Application** → nombre `backend`.

**General.** Proveedor **Docker**:

- Imagen: `ghcr.io/nexuspro-dev/backend:develop` (en `production`, `…:main`).
- Registro: el `ghcr.io` del paso 5.

**Ahora la configuración que no vive en el repositorio.** Va tal cual, sin redondear. El porqué de cada fila está en [`deployment.md` §5.3](deployment.md#53-la-configuracion-del-servicio-backend):

| Dónde | Qué | Valor |
|---|---|---|
| *Advanced → Cluster Settings* | Réplicas | **`1`** |
| *Swarm Settings → Update Config* | `Parallelism` · `Order` · `FailureAction` · `Monitor` | `1` · **`start-first`** · **`rollback`** · `30000000000` |
| *Swarm Settings* | Stop Grace Period | **`35000000000`** (35 s en nanosegundos) |
| *Swarm Settings → Health Check* | — | **Vacío.** La sonda ya viene en la imagen |
| *Advanced → Resources* | Límite de memoria | **1 GB** en `testing` · **2 GB** en `production` |
| *Advanced → Ports* | — | **Ninguno** |

Si *Update Config* te pide JSON, es este:

```json
{
  "Parallelism": 1,
  "Order": "start-first",
  "FailureAction": "rollback",
  "Monitor": 30000000000
}
```

**El identificador del servicio**: está en la dirección del navegador al abrir el servicio. Cópialo y guárdalo en GitHub como `DOKPLOY_APPLICATION_ID` del entorno `testing`.

- [ ] Las seis filas de la tabla, tal cual.
- [ ] `DOKPLOY_APPLICATION_ID` guardado en el entorno de GitHub que corresponde.

!!! danger "El Stop Grace Period no es un detalle"

    Swarm mata el proceso a los **diez segundos** por defecto. La aplicación necesita treinta para terminar las peticiones en curso. Sin los 35 segundos, cada despliegue corta a quien estaba escribiendo, y ningún log lo dice. [`deployment.md` §7.3](deployment.md#73-durante-un-redespliegue-conviven-dos-instancias).

---

## Paso 9 — Cargar las variables

Antes, en el servidor, **lee la subred** que va en `TRUSTED_PROXIES`:

```bash
docker network inspect dokploy-network --format '{{(index .IPAM.Config 0).Subnet}}'
```

Ahora *Environment* del servicio **`backend`** → pega esto y sustituye lo marcado:

```bash
DATABASE_URL=jdbc:postgresql://PEGA-EL-INTERNAL-HOST:5432/nexus
DATABASE_USER=PEGA-EL-USER
DATABASE_PASSWORD=PEGA-EL-PASSWORD

ENVIRONMENT=testing
API_URL=https://api-testing.tu-dominio

JWT_SECRET=PEGA-EL-DEL-PASO-3
MFA_ENCRYPTION_KEY=PEGA-EL-DEL-PASO-3
SUPERADMIN_EMAIL=el-correo-real@factech.co
SUPERADMIN_PASSWORD_HASH=PEGA-EL-DEL-PASO-4

CORS_ALLOWED_ORIGINS=
EXPOSE_API_DOCS=false
TRUSTED_PROXIES=PEGA-LA-SUBRED

LOG_LEVEL=INFO
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60
REQUEST_LOG_RETENTION_DAYS=

NOTIFICATION_ENABLED=false
RESEND_API_KEY=
NOTIFICATION_FROM=
NOTIFICATION_TEMPLATE_PASSWORD_RECOVERY=

PAYRETAILERS_ENCRYPTION_KEY=PEGA-EL-DEL-PASO-3
BROKER_NOTIFICATION_TOKEN=PEGA-EL-DEL-PASO-3
```

Las pasarelas, los brokers y Zoom tienen más variables, todas opcionales: sin ellas cada integración queda apagada y lo avisa al arrancar. Están en [`deployment.md` §6.5](deployment.md#65-envio-saliente) y siguientes; añádelas cuando vayas a usarlas.

!!! danger "Los cinco errores que cuestan una tarde"

    | No hagas esto | Qué pasa |
    |---|---|
    | Pegar la *Internal Connection URL* de Dokploy en `DATABASE_URL` | Es una URI `postgresql://`, no una URL de JDBC. El arranque muere con un error de driver que no menciona nada de esto |
    | Duplicar los `$` del hash | La migración pasa en verde y el superadministrador **no puede entrar nunca** |
    | Poner `*` en `CORS_ALLOWED_ORIGINS` | La aplicación **no arranca**, a propósito |
    | Poner `localhost` en `CORS_ALLOWED_ORIGINS` | Abre la API al navegador de cualquiera que corra un frontend en su máquina |
    | Poner en `TRUSTED_PROXIES` algo más ancho que la subred | Cualquiera dentro de ese rango escribe en la auditoría la IP que quiera |

**No declares `PORT`.** La aplicación escucha en `8080` y es a donde apuntará el dominio.

**Guardar no despliega.** Pulsa **Deploy**.

---

## Paso 10 — El primer arranque

*Deployments* → el despliegue en curso, y luego *Logs* del servicio.

**Tarda.** El primer arranque aplica **todas** las migraciones antes de que la sonda responda. Dos o tres minutos es normal, y la imagen da cinco de gracia.

Lo que debes ver, en este orden:

```
Picked up JAVA_TOOL_OPTIONS: -XX:MaxRAMPercentage=60
Flyway Community Edition ... by Redgate
Database: jdbc:postgresql://nexus-postgres-a1b2c3:5432/nexus
Successfully validated N migrations
...
Successfully applied N migrations
...
Started NexusApplication in XX.XXX seconds
```

- [ ] `Successfully applied N migrations` — el esquema quedó puesto.
- [ ] `Started NexusApplication` — arrancó.
- [ ] En el servidor, `docker service ps <servicio>` muestra la tarea en `Running`, y `docker ps` la marca `(healthy)`.

**Uno o dos reinicios al principio son normales** si la base todavía estaba levantando ([`deployment.md` §7.2](deployment.md#72-la-base-puede-no-estar-lista-cuando-arranca-la-aplicacion)). Un bucle no: salta a [**Cuando algo falla**](#cuando-algo-falla).

---

## Paso 11 — Dominio y comprobación

1. En tu DNS, un registro `A` —`api-testing.tu-dominio`— hacia la IP del servidor. **Antes** del paso 2: Let's Encrypt lo comprueba.
2. *Domains* del servicio `backend` → *Add Domain*: ese dominio, **Container Port `8080`**, **HTTPS** con **Let's Encrypt**.

Si usas Cloudflare para el DNS, el registro va en **DNS only** (nube gris). Con el proxy activado, la auditoría registra las IP de Cloudflare.

Ahora los cinco de siempre:

```bash
BASE=https://api-testing.tu-dominio

curl -s $BASE/actuator/health/liveness
curl -s $BASE/actuator/health/readiness
curl -s -o /dev/null -w '%{http_code}\n' $BASE/v3/api-docs
curl -s -o /dev/null -w '%{http_code}\n' $BASE/actuator/metrics
curl -s -o /dev/null -w '%{http_code}\n' $BASE/api/v1/roles
```

| # | Debe responder | Si responde otra cosa |
|---|---|---|
| 1 | `{"status":"UP"}` | No arrancó, o Traefik no llega. Mira los logs y el puerto del dominio |
| 2 | `{"status":"UP"}` | Arrancó pero no puede atender: casi siempre la base de datos |
| 3 | `401` | **Un `200` aquí es un despliegue mal configurado.** `EXPOSE_API_DOCS` quedó en `true` |
| 4 | `401` | **Un `200` aquí es lo mismo.** No debería poder pasar; avisa |
| 5 | `401` | Un `500` significa que algo del arranque quedó a medias |

Y en el servidor, que el panel no se haya quedado con otro valor:

```bash
docker service inspect <servicio> --format \
  'réplicas={{.Spec.Mode.Replicated.Replicas}} orden={{.Spec.UpdateConfig.Order}} fallo={{.Spec.UpdateConfig.FailureAction}} gracia={{.Spec.TaskTemplate.ContainerSpec.StopGracePeriod}}'
docker service ls
```

- [ ] Los cinco `curl` responden lo esperado, y la salud es `{"status":"UP"}` **y nada más**.
- [ ] El primer comando dice `réplicas=1 orden=start-first fallo=rollback gracia=35000000000`.
- [ ] En `docker service ls`, **ningún servicio del entorno tiene `PORTS`**.

---

## Paso 12 — Entrar y cambiar la contraseña

Con el correo y la contraseña del paso 4:

```bash
curl -s -X POST $BASE/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"identifier":"el-correo-real@factech.co","password":"TU-CONTRASENA-REAL"}'
```

- [ ] Devuelve un token de acceso. Si devuelve credenciales inválidas, el hash del paso 4 llegó mal — ver la tabla del final.
- [ ] **Activa el segundo factor**: el superadministrador queda retenido hasta hacerlo ([`deployment.md` §4.1.1](deployment.md#411-mfa_encryption_key)). Ten el teléfono a mano y **guarda los diez códigos de recuperación**.
- [ ] **Cambia la contraseña de inmediato**. La del paso 4 pasó por tu terminal y por tu portapapeles.
- [ ] Crea desde ahí las cuentas reales. La del superadministrador es la llave del sistema, no una cuenta de trabajo.

**La IP de la auditoría**, ahora que hay tráfico real ([`deployment.md` §11.2](deployment.md#112-la-ip-de-la-auditoria)):

```bash
docker exec -it <contenedor-de-postgres> psql -U <usuario> -d nexus \
  -c "SELECT ip_address, path, occurred_at FROM request_log ORDER BY occurred_at DESC LIMIT 5;"
```

- [ ] Tu inicio de sesión aparece **con tu IP pública**, no con una `10.x`.

---

## Paso 13 — Encender el despliegue automático

Hasta aquí el despliegue lo hiciste tú. Desde aquí lo hace el CI.

En `NexusPro-Dev/backend` → *Settings → Secrets and variables → Actions* → pestaña **Variables** → nueva variable **de repositorio**:

```
DOKPLOY_DEPLOY = true
```

Comprueba que funciona con un commit cualquiera de código —no solo documentación, que no dispara el CI— en `develop`:

- [ ] En *Actions*, el run tiene dos jobs: **Compilar, formatear y probar** y **Publicar la imagen y desplegar**, los dos en verde.
- [ ] En Dokploy, *Deployments* del servicio muestra un despliegue nuevo con los siete primeros caracteres del commit como título.
- [ ] En GHCR, el paquete `backend` tiene la etiqueta `sha-<commit>`.

---

## Paso 14 — Las copias de seguridad

1. Dokploy → *Settings → Destinations* → el bucket S3 de la lista de *Antes de empezar*. Pulsa **Test**.
2. Servicio `postgres` → *Backups* → nueva copia: ese destino, programación **`0 4 * * *`** y prefijo `testing/` (o `production/`).
3. Lanza una a mano y comprueba que el archivo aparece en el bucket.

- [ ] Hay una copia en el bucket.
- [ ] **Antes de que haya datos reales, restaura una** en una base de pruebas y comprueba que la aplicación arranca contra ella. Una copia que nadie ha restaurado nunca no es una copia.

---

## Paso 15 — Lo que se llena después

**El dominio del frontend en CORS**, cuando exista el frontend desplegado:

```bash
CORS_ALLOWED_ORIGINS=https://app.tu-dominio
```

Con esquema, **sin barra final**, sin ruta; varios, separados por coma y sin espacios. `*` **tumba el arranque**.

**El correo saliente**, solo si quieres que funcione la recuperación de contraseña:

1. En Resend, **verifica el dominio** desde el que vas a enviar.
2. Crea una clave de API.
3. Actualiza y **despliega**:

```bash
NOTIFICATION_ENABLED=true
RESEND_API_KEY=re_...
NOTIFICATION_FROM=NEXUS <no-responder@tu-dominio-verificado>
NOTIFICATION_TEMPLATE_PASSWORD_RECOVERY=nexus-password-recovery
```

La última es el **alias de la plantilla con estilos**, publicada en Resend. Vacía, el correo sale en texto plano; con un alias que no existe, **el correo se pierde**.

- [ ] Tras desplegar, el frontend llama a la API desde el navegador sin error de CORS.
- [ ] En los logs del arranque **ya no aparece** el aviso de envío apagado, y una recuperación real llega.

---

## Paso 16 — El segundo entorno

Repite los pasos 3, 4 y del 7 al 14 en el entorno **`production`** del mismo proyecto, cambiando:

| | `testing` | `production` |
|---|---|---|
| Imagen | `…/backend:develop` | `…/backend:main` |
| `ENVIRONMENT` | `testing` | `production` |
| Dominio | `api-testing.tu-dominio` | `api.tu-dominio` |
| Memoria `backend` · `postgres` | 1 GB · 512 MB | 2 GB · 1 GB |
| Secretos del paso 3 | Los suyos | **Otros distintos** |
| Base de datos | La suya | La suya |
| Entorno de GitHub | `testing` | `production` |

El paso 6 cambia la etiqueta: `ghcr.io/nexuspro-dev/backend:main`, desde `main`. El paso 13 ya está hecho: la variable vale para los dos.

- [ ] **No comparten nada**: ni base de datos, ni secretos, ni clave de Resend.

A partir de aquí, **desplegar es integrar**: lo que entra en `develop` con el CI en verde está en `testing` en minutos, y lo que entra en `main`, en producción. No hay botón de desplegar.

---

## Después de esto

- **Nadie vigila el sistema todavía.** *Monitoring* de Dokploy muestra CPU y memoria, pero las métricas de la aplicación se consultan a mano y no hay alertas. Está declarado en [`deployment.md` §13](deployment.md#13-lo-que-este-despliegue-no-resuelve), con lo demás que este despliegue no resuelve.
- **El servidor es tuyo.** Las actualizaciones automáticas cubren los parches de seguridad; las de Dokploy, desde su panel y leyendo antes sus notas.

---

## Cuando algo falla

| Síntoma | Causa casi segura | Arreglo |
|---|---|---|
| `Driver claims to not accept jdbcUrl` | Pegaste la *Internal Connection URL* de Dokploy | Constrúyela como en el paso 9: `jdbc:postgresql://<Internal Host>:5432/nexus` |
| `Could not resolve placeholder 'JWT_SECRET'` (o cualquier otra) | Falta esa variable | Cárgala **y despliega**. La aplicación se niega a arrancar sin ella a propósito |
| Cambiaste una variable y **no pasa nada** | Guardar no despliega | Pulsa *Deploy* |
| `SUPERADMIN_PASSWORD_HASH no parece un hash Argon2id` | El valor llegó destrozado | Pégalo de nuevo **sin duplicar los `$`** |
| Migración en verde pero **el superadministrador no entra** | Duplicaste los `$` | Ese hash ya está sembrado: hay que restablecer la contraseña en la base, o borrar la base y volver a empezar |
| `El origen '*' no se admite` y no arranca | `CORS_ALLOWED_ORIGINS=*` | Pon el dominio real, o déjalo vacío |
| `Connection refused` a la base, en bucle | El *Internal Host*, el usuario o la contraseña no son los de **este** entorno | Cópialos otra vez de *Internal Credentials* |
| La tarea arranca, se mata sola al poco y vuelve a empezar | La memoria: el proceso pasa del límite del servicio | *Monitoring* lo muestra. Comprueba `JAVA_TOOL_OPTIONS` y el límite de §5.4 |
| Arranque impecable en los logs pero **`404` o `502`** en el dominio | El dominio apunta a otro puerto | *Domains*: Container Port **`8080`** |
| El dominio responde con **certificado no válido** | El registro `A` no existía o no apuntaba al servidor cuando Traefik pidió el certificado | Corrige el DNS y vuelve a guardar el dominio |
| `pull access denied` o `unauthorized` al desplegar | El registro de GHCR en Dokploy falta, o su token caducó | Paso 5. El token necesita `read:packages` |
| El job **Publicar la imagen y desplegar** no aparece | Falta `DOKPLOY_DEPLOY=true`, o el commit solo tocaba documentación | Paso 13 |
| El job falla en *Desplegar en Dokploy* con `401` | La clave de la API, o la dirección del panel con barra final | Secretos del entorno de GitHub, paso 5 |
| El despliegue de Dokploy queda fallido y **el servicio sigue con la versión vieja** | La nueva no pasó la sonda y Swarm volvió atrás | Es lo previsto. El motivo está en el log del despliegue |
| `Validate failed: migration checksum mismatch` | Alguien editó una migración ya aplicada | **Nunca se edita una migración aplicada.** Se escribe otra que corrija |
| `Schema-validation: missing table` | El esquema no coincide con el mapeo | Falta una migración. No lo tapes con `ddl-auto`: es la comprobación funcionando |

**Si tienes que empezar de cero con la base de datos:** borra el servicio `postgres` **y su volumen**, créalo otra vez como en el paso 7 y actualiza las tres variables de la base, que cambian. Esto **destruye todos los datos** — comprueba dos veces en qué entorno estás.

---

## Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 27-08-2026 | Creación inicial. Manual paso a paso del despliegue en Railway, complementario de [`deployment.md`](deployment.md): once pasos en orden, con los comandos exactos, el bloque de variables listo para pegar en el editor en crudo, las comprobaciones de cada paso y una tabla de síntoma → causa → arreglo. Separa lo que se teclea de lo que se razona: el porqué de cada decisión sigue viviendo en `deployment.md`, que manda si los dos se contradicen. | Responsable técnico |
| 0.2.0 | 27-08-2026 | **El paso 4 mandaba desplegar una rama que no es desplegable, y eso tumbó un despliegue.** Decía «`develop` para `testing`, `main` para `production`» —que es el destino— sin comprobar que **hoy ninguna de las dos lleva el trabajo**: van veintitrés commits por detrás de `feature/esqueleto-del-proyecto` y les faltan `railway.json` y el `server.port: ${PORT:8080}`. El resultado es «Application failed to respond» **con un arranque impecable en los logs**, que es el fallo que `deployment.md` §7.1 describe y que este manual mandaba a reproducir. Gana el aviso, las dos salidas —apuntar a la rama de trabajo ahora, fusionar después— y una comprobación que no depende de acordarse: **una rama es desplegable si contiene `railway.json`**. | Responsable técnico |
| 0.3.0 | 14-10-2026 | **Se reescribe para Dokploy en un VPS propio** ([`ADR-007`](architecture/ADR-007-plataforma-de-despliegue-dokploy.md)). Pasa de once pasos a dieciséis porque ahora el servidor es nuestro: firewall del proveedor —**no UFW, que Docker se salta**—, SSH solo con llave, actualizaciones automáticas, la instalación de Dokploy y su panel con dominio y doble factor. La aplicación deja de construirse en la plataforma: el servidor descarga de GHCR con un token de solo lectura, la primera imagen se publica a mano y, desde el paso 13, **el CI publica y despliega solo cuando `verificar` queda en verde**. El paso 8 transcribe la configuración del servicio que Dokploy no lee de ningún archivo, con el *Stop Grace Period* de 35 segundos que Swarm no trae; el paso 9 gana `JAVA_TOOL_OPTIONS`, `TRUSTED_PROXIES` con la subred de `dokploy-network` y los secretos que faltaban en el bloque —`MFA_ENCRYPTION_KEY`, `PAYRETAILERS_ENCRYPTION_KEY`, `BROKER_NOTIFICATION_TOKEN`—; el paso 12, el segundo factor del superadministrador y la comprobación de la IP en `request_log`; y el 14, las copias a S3 con la restauración como obligación. La tabla de fallos cambia los de Railway por los de esta plataforma. Se retira el aviso de `feature/esqueleto-del-proyecto`, que hace semanas que no es cierto. | Responsable técnico |
