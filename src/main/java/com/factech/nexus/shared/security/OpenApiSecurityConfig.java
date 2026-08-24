package com.factech.nexus.shared.security;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Declara en el contrato <b>cómo se autentica</b> (Art. VIII.2, VIII.6).
 *
 * <p><b>Sin esto, la documentación es inutilizable aunque esté completa.</b> Swagger UI no muestra
 * el botón «Authorize» si el contrato no declara ningún esquema de seguridad, de modo que no hay
 * forma de adjuntar la cabecera {@code Authorization} desde la interfaz: <b>toda</b> operación
 * protegida responde {@code 401} y quien explora la API concluye que está rota. El contrato
 * describía cada endpoint y cada permiso, y callaba lo único que hacía falta para probarlos.
 *
 * <p>El esquema se declara <b>global</b> —{@code security} a nivel de documento— en lugar de
 * anotarlo endpoint por endpoint. Es lo correcto aquí porque la regla del sistema es que <b>todo
 * requiere token salvo tres rutas</b> ({@code SecurityConfig}): con la declaración por operación,
 * cada endpoint nuevo nacería sin ella y el hueco no rompería nada — exactamente el modo de fallo
 * que `OpenApiContractIT` existe para evitar en las rutas. Las tres excepciones se marcan en su
 * propio controlador con {@code @SecurityRequirements} vacío.
 *
 * <p>{@code bearerFormat = "JWT"} no cambia el comportamiento; es una pista para quien lee el
 * contrato, y evita que alguien intente pegar ahí una credencial de otra forma.
 */
@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "NEXUS — API del Sistema Principal",
            version = "v1",
            description =
                """
                API del módulo `SP`. **Toda operación exige un token de acceso**
                salvo el inicio, la renovación y el cierre de sesión.

                Para probar cualquier endpoint: obtenga el token con
                `POST /api/v1/auth/login`, pulse **Authorize** y pegue el valor de
                `accessToken` — sin el prefijo `Bearer`, que Swagger añade solo.

                El token vive **quince minutos** y no es revocable: cuando expire,
                renuévelo con `POST /api/v1/auth/refresh` y vuelva a autorizar.
                """),
    servers = @Server(url = "/", description = "Este mismo servidor"),
    security = @SecurityRequirement(name = OpenApiSecurityConfig.ESQUEMA))
@SecurityScheme(
    name = OpenApiSecurityConfig.ESQUEMA,
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER,
    description =
        "Token de acceso emitido por `POST /api/v1/auth/login`. Pegue solo el valor de"
            + " `accessToken`: el prefijo `Bearer` lo añade la interfaz.")
public class OpenApiSecurityConfig {

  /** Nombre del esquema. Constante porque lo referencian la definición y cada excepción. */
  public static final String ESQUEMA = "bearerAuth";
}
