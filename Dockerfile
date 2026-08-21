# Construcción en varias etapas: la imagen final no lleva Maven ni el JDK
# completo, solo el JRE y el artefacto (architecture.md §12).

# ---------- Etapa 1: construcción ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Las dependencias se resuelven antes de copiar el código, para que un cambio
# en el código no invalide la capa de dependencias.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- Etapa 2: ejecución ----------
FROM eclipse-temurin:21-jre-alpine

# El contenedor se ejecuta con un usuario sin privilegios
# (architecture.md §12, principio de mínimo privilegio del Art. IV.2).
RUN addgroup -S nexus && adduser -S nexus -G nexus

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown -R nexus:nexus /app

USER nexus
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
