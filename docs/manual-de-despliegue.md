# Manual de despliegue — Backend NEXUS


| Campo                | Valor                                   |
| -------------------- | --------------------------------------- |
| Proyecto             | NEXUS — Renovación de plataforma        |
| Empresa              | FACTECH GROUP SAS                       |
| Documento            | `manual-de-despliegue.md`               |
| Versión              | 0.1.0                                   |
| Estado               | Borrador                                |
| Responsable técnico  | Bonilla Diaz William Steven             |
| Fecha de creación    | 27-08-2026                              |
| Última actualización | 27-08-2026                              |
| Documento superior   | `[deployment.md](deployment.md)` v0.2.0 |


---

!!! info "Este documento es el **manual**; `[deployment.md](deployment.md)` es la **referencia**"

```
Aquí está **qué tecleas y en qué orden**, la primera vez, con Railway abierto delante. Nada de por qué.

El **por qué** de cada decisión —por qué una sola réplica, por qué `EXPOSE_API_DOCS` va en `false`, por qué la IP de la auditoría es la del proxy— está en `[deployment.md](deployment.md)`, y cada paso enlaza a su sección.

Si algo de aquí contradice a `deployment.md`, manda `deployment.md`.
```

**Tiempo estimado:** 30–40 minutos el primer entorno, 15 el segundo.

---



## Antes de empezar

Ten esto a mano **antes** de abrir Railway. Ir a buscarlo a mitad de camino es de donde salen la mitad de los errores:

- [ ] El repositorio clonado y `mvn verify` **en verde** en tu máquina.
- [ ] `openssl` disponible (viene con Git Bash en Windows).
- [ ] JDK 21 en el `PATH` — hace falta `jshell` para el paso 2.
- [ ] Una cuenta de Railway con acceso a `NexusPro-Dev/backend`.
- [ ] **Decidido** el correo real del superadministrador y su contraseña.
- [ ] **Decidido** qué entorno vas a montar primero: `testing` (rama `develop`) o `production` (rama `main`).

!!! warning "Elige la contraseña del superadministrador antes de empezar"

```
No es una contraseña de prueba. Es **la única llave de entrada** al sistema recién desplegado: el primer superadministrador no puede crearse por la API. Mínimo 12 caracteres.

La vas a cambiar en el paso 8, pero hasta entonces es la que hay.
```

---



## Paso 1 — Generar el secreto de firma

En una terminal, en cualquier carpeta:

```bash
openssl rand -base64 48op
```

Copia la salida completa. Ese es tu `JWT_SECRET`.

- [ ] Guardado en un gestor de contraseñas, **no** en un archivo del proyecto ni en un chat.
- [ ] Si vas a montar los dos entornos, **genera uno distinto para cada uno**. Compartirlo significa que un token de pruebas vale en producción.

---



## Paso 2 — Generar el hash de la contraseña

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
- [ ] **Los** `$` **van tal cual.** No los dupliques. La duplicación es solo para el archivo `.env` local, y hacerla aquí deja al superadministrador sin poder entrar nunca, con la migración en verde y sin ningún error. Ver `deployment.md` [§4.2](deployment.md#42-superadmin_password_hash).

---



## Paso 3 — Crear el proyecto y la base de datos

En Railway:

1. **New Project** → *Deploy PostgreSQL*.
2. Clic en el servicio → *Settings* → renómbralo a `Postgres`, exactamente así.
3. En *Variables* del servicio, comprueba que la versión es **PostgreSQL 17**.

- [ ] El servicio se llama `Postgres`. **El nombre importa**: las referencias del paso 5 lo usan literalmente.

**No crees tablas ni bases de datos.** El esquema lo aplica Flyway solo, cuando arranque la aplicación.

---



## Paso 4 — Crear el servicio del backend

En el mismo proyecto:

1. **New** → *GitHub Repo* → `NexusPro-Dev/backend`. Autoriza a Railway si te lo pide.
2. Clic en el servicio nuevo → *Settings* → renómbralo a `backend`.
3. *Settings → Source → Branch*: pon `develop` si estás montando `testing`, o `main` si es `production`.
4. Comprueba en *Settings → Build* que el constructor es **Dockerfile**. Railway lo detecta solo; si no lo hizo, `railway.json` del repositorio lo fuerza.

**Va a intentar desplegar y va a fallar.** Es lo correcto: todavía no tiene variables y la aplicación se niega a arrancar sin ellas. Déjalo fallar y sigue.

- [ ] El servicio se llama `backend` y apunta a la rama correcta.

---



## Paso 5 — Cargar las variables

Ve a *Variables* del servicio `backend` → botón **Raw Editor** → pega esto tal cual y sustituye los tres valores marcados:

```bash
DATABASE_URL=jdbc:postgresql://${{Postgres.RAILWAY_PRIVATE_DOMAIN}}:5432/${{Postgres.PGDATABASE}}
DATABASE_USER=${{Postgres.PGUSER}}
DATABASE_PASSWORD=${{Postgres.PGPASSWORD}}

ENVIRONMENT=production
API_URL=https://PON-AQUI-TU-DOMINIO

JWT_SECRET=PEGA-AQUI-EL-DEL-PASO-1
SUPERADMIN_EMAIL=el-correo-real@factech.co
SUPERADMIN_PASSWORD_HASH=PEGA-AQUI-EL-DEL-PASO-2

CORS_ALLOWED_ORIGINS=
EXPOSE_API_DOCS=false
TRUSTED_PROXIES=

LOG_LEVEL=INFO
REQUEST_LOG_RETENTION_DAYS=

NOTIFICATION_ENABLED=false
RESEND_API_KEY=
NOTIFICATION_FROM=
```

Las líneas con `${{Postgres...}}` **se pegan literalmente**, con las llaves dobles. Railway las resuelve solo.

!!! danger "Los cuatro errores que cuestan una tarde"

```
| No hagas esto | Qué pasa |
|---|---|
| Referenciar `${{Postgres.DATABASE_URL}}` | Es una URI, no una URL de JDBC. El arranque muere con un error de driver que no menciona nada de esto |
| Duplicar los `$` del hash | La migración pasa en verde y el superadministrador **no puede entrar nunca** |
| Poner `*` en `CORS_ALLOWED_ORIGINS` | La aplicación **no arranca**, a propósito |
| Poner `localhost` en `CORS_ALLOWED_ORIGINS` | Abre la API al navegador de cualquiera que corra un frontend en su máquina |
```

**Tres notas sobre lo que dejaste vacío**, y no es un olvido:

- `CORS_ALLOWED_ORIGINS` vacío = ningún navegador autorizado. La API funciona igual de servidor a servidor. Lo llenas en el paso 9, cuando exista el frontend.
- `TRUSTED_PROXIES` vacío es lo correcto **hoy en Railway**: no hay valor bueno que poner. Ver `deployment.md` [§11.2](deployment.md#112-lo-que-la-verificacion-no-puede-comprobar-hoy).
- `REQUEST_LOG_RETENTION_DAYS` vacío porque **hoy no lo lee nadie**. Ponerle un número no purga nada y hace creer que sí.

**No declares** `PORT`**.** Railway lo inyecta y la aplicación lo obedece.

Al guardar, Railway redespliega solo.

---



## Paso 6 — El primer arranque

Ve a *Deployments* → el despliegue en curso → *View Logs*.

**Tarda.** El primer arranque aplica **todas** las migraciones antes de que el puerto responda. Dos o tres minutos es normal.

Lo que debes ver, en este orden:

```
Flyway Community Edition ... by Redgate
Database: jdbc:postgresql://postgres.railway.internal:5432/railway
Successfully validated N migrations
Migrating schema "public" to version "1 - create users"
...
Successfully applied N migrations
...
Started NexusApplication in XX.XXX seconds
```

- [ ] `Successfully applied N migrations` — el esquema quedó puesto.
- [ ] `Started NexusApplication` — arrancó.
- [ ] El despliegue queda en verde (**Active**) tras pasar la sonda de salud.

Si en lugar de eso ves un error, salta a **[Cuando algo falla](#cuando-algo-falla)** al final.

---



## Paso 7 — Comprobar que quedó bien

Copia el dominio de *Settings → Networking → Public Networking* (algo como `backend-production-xxxx.up.railway.app`) y corre estos cinco:

```bash
BASE=https://TU-DOMINIO

curl -s $BASE/actuator/health/liveness
curl -s $BASE/actuator/health/readiness
curl -s -o /dev/null -w '%{http_code}\n' $BASE/v3/api-docs
curl -s -o /dev/null -w '%{http_code}\n' $BASE/actuator/metrics
curl -s -o /dev/null -w '%{http_code}\n' $BASE/api/v1/roles
```


| #   | Debe responder    | Si responde otra cosa                                                                     |
| --- | ----------------- | ----------------------------------------------------------------------------------------- |
| 1   | `{"status":"UP"}` | No arrancó. Mira los logs                                                                 |
| 2   | `{"status":"UP"}` | Arrancó pero no puede atender: casi siempre la base de datos                              |
| 3   | `401`             | **Un** `200` **aquí es un despliegue mal configurado.** `EXPOSE_API_DOCS` quedó en `true` |
| 4   | `401`             | **Un** `200` **aquí es lo mismo.** No debería poder pasar; avisa                          |
| 5   | `401`             | Un `500` significa que algo del arranque quedó a medias                                   |


- [ ] Los cinco responden lo esperado.
- [ ] La respuesta de salud es `{"status":"UP"}` **y nada más**. Si trae componentes o versiones, algo cambió `show-details` y hay que corregirlo.

---



## Paso 8 — Entrar y cambiar la contraseña

Con el correo y la contraseña del paso 2:

```bash
curl -s -X POST $BASE/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"identifier":"el-correo-real@factech.co","password":"TU-CONTRASENA-REAL"}'
```

- [ ] Devuelve un token de acceso. Si devuelve credenciales inválidas, el hash del paso 2 llegó mal — ver la tabla del final.
- [ ] **Cambia la contraseña de inmediato**, desde la API o desde el frontend. La del paso 2 pasó por tu terminal y por tu portapapeles.
- [ ] Crea desde ahí las cuentas reales. La del superadministrador es la llave del sistema, no una cuenta de trabajo.

---



## Paso 9 — Dominio y CORS

**Cuando exista el frontend desplegado**, y no antes:

1. Si vas a usar dominio propio: *Settings → Networking → Custom Domain*, y crea el `CNAME` que Railway te indique. El certificado lo pone él.
2. Actualiza dos variables:

```bash
API_URL=https://api.nexus.co
CORS_ALLOWED_ORIGINS=https://app.nexus.co
```

Reglas de `CORS_ALLOWED_ORIGINS`, que la aplicación verifica al arrancar:

- Con esquema (`https://`), **sin barra final**, sin ruta.
- Varios se separan por coma, sin espacios.
- `*` **tumba el arranque**. Un origen mal formado, también.

- [ ] Tras guardar, el frontend llama a la API desde el navegador sin error de CORS.

---



## Paso 10 — El correo saliente

**Solo si quieres que funcione la recuperación de contraseña.** Con `NOTIFICATION_ENABLED=false`, quien olvide su contraseña recibe una respuesta normal y **ningún correo**.

1. En Resend, **verifica el dominio** desde el que vas a enviar.
2. Crea una clave de API.
3. Actualiza:

```bash
NOTIFICATION_ENABLED=true
RESEND_API_KEY=re_...
NOTIFICATION_FROM=NEXUS <no-responder@tu-dominio-verificado>
```

- [ ] En los logs del arranque **ya no aparece** el aviso de envío apagado.
- [ ] Prueba una recuperación real y comprueba que el correo llega.

!!! warning "Verifica el dominio antes, no después"

```
Si el remitente no pertenece a un dominio verificado, Resend responde `403` — y **eso no se ve al arrancar**. La aplicación levanta con normalidad y el fallo aparece la primera vez que alguien olvide su contraseña, que es cuando nadie está mirando. No hay reintento: ese mensaje se pierde.
```

---



## Paso 11 — El segundo entorno

Repite del paso 1 al 7 en un **entorno de Railway distinto**, cambiando:


|               | `testing`  | `production`      |
| ------------- | ---------- | ----------------- |
| Rama          | `develop`  | `main`            |
| `ENVIRONMENT` | `testing`  | `production`      |
| `JWT_SECRET`  | Uno propio | **Otro distinto** |
| Base de datos | La suya    | La suya           |


- [ ] **No comparten nada**: ni base de datos, ni secreto de firma, ni clave de Resend.

A partir de aquí, **desplegar es integrar**: lo que entre en `develop` está en `testing` en minutos, y lo que entre en `main` está en producción. No hay botón de desplegar.

---



## Después de esto

- **Verifica las copias de seguridad** del servicio Postgres, y **restaura una** antes de que haya datos reales. Una copia que nadie ha restaurado nunca no es una copia.
- **Nadie vigila el sistema todavía.** Las métricas se consultan a mano y no hay alertas. Está declarado en `deployment.md` [§13](deployment.md#13-lo-que-este-despliegue-no-resuelve), con lo demás que este despliegue no resuelve.

---



## Cuando algo falla


| Síntoma                                                         | Causa casi segura                          | Arreglo                                                                                                      |
| --------------------------------------------------------------- | ------------------------------------------ | ------------------------------------------------------------------------------------------------------------ |
| `Driver claims to not accept jdbcUrl`                           | Referenciaste `${{Postgres.DATABASE_URL}}` | Constrúyela como en el paso 5: `jdbc:postgresql://...`                                                       |
| `Could not resolve placeholder 'JWT_SECRET'` (o cualquier otra) | Falta esa variable                         | Cárgala. La aplicación se niega a arrancar sin ella a propósito                                              |
| `SUPERADMIN_PASSWORD_HASH no parece un hash Argon2id`           | El valor llegó destrozado                  | Pégalo de nuevo **sin duplicar los** `$`                                                                     |
| Migración en verde pero **el superadministrador no entra**      | Duplicaste los `$`                         | Ese hash ya está sembrado: hay que restablecer la contraseña en la base, o borrar la base y volver a empezar |
| `El origen '*' no se admite` y no arranca                       | `CORS_ALLOWED_ORIGINS=*`                   | Pon el dominio real, o déjalo vacío                                                                          |
| Arranque impecable en los logs pero la **sonda en rojo**        | Puerto                                     | No debería pasar ya. Comprueba que no declaraste `PORT` con otro valor                                       |
| Error de red hacia `*.railway.internal`                         | La red privada es solo IPv6                | Añade `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv6Addresses=true` y redespliega                                  |
| Falla el **primer** intento y el segundo arranca solo           | La red privada tarda unos segundos         | Ninguno. Es lo previsto, la política de reinicio lo cubre                                                    |
| `Validate failed: migration checksum mismatch`                  | Alguien editó una migración ya aplicada    | **Nunca se edita una migración aplicada.** Se escribe otra que corrija                                       |
| `Schema-validation: missing table`                              | El esquema no coincide con el mapeo        | Falta una migración. No lo tapes con `ddl-auto`: es la comprobación funcionando                              |


**Si tienes que empezar de cero con la base de datos:** borra el servicio `Postgres`, créalo otra vez y redespliega el backend. Las variables del paso 5 lo reconectan solas. Esto **destruye todos los datos** — comprueba dos veces en qué entorno estás.

---



## Control de cambios


| Versión | Fecha      | Cambio                                                                                                                                                                                                                                                                                                                                                                                                                                                      | Responsable         |
| ------- | ---------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------- |
| 0.1.0   | 27-08-2026 | Creación inicial. Manual paso a paso del despliegue en Railway, complementario de `[deployment.md](deployment.md)`: once pasos en orden, con los comandos exactos, el bloque de variables listo para pegar en el editor en crudo, las comprobaciones de cada paso y una tabla de síntoma → causa → arreglo. Separa lo que se teclea de lo que se razona: el porqué de cada decisión sigue viviendo en `deployment.md`, que manda si los dos se contradicen. | Responsable técnico |


