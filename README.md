# NEXUS — Backend

Backend de la plataforma NEXUS, de **FACTECH GROUP SAS**.

Java 21 · Spring Boot 3.5 · PostgreSQL 17 · Maven

---

## Documentación

Toda la documentación técnica vive en [`docs/`](docs/) y se publica como sitio navegable. Antes de escribir código, leer al menos:

| Documento | Para qué |
|---|---|
| [`docs/constitution.md`](docs/constitution.md) | Los principios no negociables. Prevalece sobre todo lo demás |
| [`docs/architecture.md`](docs/architecture.md) | Estructura del código, esquema, contrato de API, auditoría |
| [`docs/development-guide.md`](docs/development-guide.md) | Cómo se escribe código aquí, día a día |
| [`docs/security.md`](docs/security.md) | Roles, permisos, autenticación |
| [`docs/deployment.md`](docs/deployment.md) | Cómo se despliega, en Railway, y qué variable va en cada entorno |

## Puesta en marcha

**Prerrequisitos:** JDK Temurin 21, Maven 3.9+, Docker, Git.

```bash
cp .env.example .env          # completar los valores locales
docker compose up -d db       # levanta PostgreSQL
mvn verify                    # compila, formatea, prueba
mvn spring-boot:run           # arranca la aplicación
```

O el entorno completo en contenedores:

```bash
docker compose up
```

### Comprobación

| Recurso | URL |
|---|---|
| Estado de salud | `http://localhost:8080/actuator/health` |
| Documentación de API | `http://localhost:8080/swagger-ui.html` |
| Especificación OpenAPI | `http://localhost:8080/v3/api-docs` |

### Comandos frecuentes

```bash
mvn spotless:apply     # formatea el código
mvn test               # solo pruebas unitarias
mvn verify             # todo, incluidas las de integración
docker compose down -v # reinicia la base de datos desde cero
```

Si `mvn verify` falla en tu máquina, **no** abras el Pull Request esperando que CI lo resuelva.

## Despliegue

Los entornos `testing` y `production` corren en **Railway**, uno por rama (`develop` y `main`), construidos desde este mismo `Dockerfile`. **Desplegar es integrar**: no hay una acción manual aparte.

El procedimiento completo —variables de cada entorno, generación de los dos secretos, primer arranque, verificación y operación— está en [`docs/deployment.md`](docs/deployment.md), y la decisión de plataforma en [`ADR-002`](docs/architecture/ADR-002-plataforma-de-despliegue-railway.md).

Nada del `docker-compose.yml` viaja a un entorno desplegado: sus valores son de desarrollo y están versionados.

## Estructura

```
src/main/java/com/factech/nexus/
├── NexusApplication.java
├── shared/          Infraestructura transversal, sin lógica de negocio
└── modules/         Un paquete por módulo de negocio

src/main/resources/
├── application.yml  Configuración base, sin valores de entorno
└── db/migration/    Migraciones Flyway — fuente de verdad del esquema
```

Las reglas de dependencia entre capas están en `architecture.md` §5.2 y las verifica **ArchUnit**, no la disciplina: una violación rompe la construcción.

## Cómo se trabaja

El proyecto usa **Spec-Driven Development**. Cada funcionalidad tiene una tripleta en `docs/specs/`:

```
spec.md    qué debe pasar y por qué
plan.md    cómo se construye
tasks.md   en qué pasos
```

Se aprueban en ese orden, y **no se escribe código antes de que las tres estén aprobadas** (Art. I.6). El flujo completo está en `development-guide.md` §3.

## Estado

En construcción. La documentación del módulo `SP` (Sistema Principal) está completa —sus cuarenta y dos tripletas aprobadas— y la implementación cubre **cuarenta y uno de los cuarenta y dos requerimientos con endpoint funcionando**. El único sin él es `RF-SP-040` (restablecer la propia contraseña olvidada), bloqueado por la decisión **D-23**: la forma del canal de envío está decidida, el mecanismo no.

**Ningún requerimiento figura todavía como `Implementado`**, y no es un descuido: la definición de terminado (constitución §16) exige además Issue, Pull Request aprobado por alguien distinto del autor e integración.

Lo que falta no son funcionalidades sino comportamientos declarados y aún no implementados —límite de tasa, aplicación del cambio obligatorio de contraseña, `request_log`— y las decisiones que siguen abiertas. Están registrados como issues del repositorio.

Consultar la matriz de trazabilidad en [`docs/requirements.md`](docs/requirements.md) para el estado de cada requerimiento.
