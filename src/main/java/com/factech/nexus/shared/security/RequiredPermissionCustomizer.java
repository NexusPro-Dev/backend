package com.factech.nexus.shared.security;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import java.util.ArrayList;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * Escribe en el contrato <b>qué permiso exige cada operación</b>, leyéndolo de donde se aplica.
 *
 * <p><b>Por qué no se escribe a mano.</b> Hasta el 18-09-2026 el permiso de cada ruta vivía en la
 * prosa del {@code 403} de su {@code @ApiResponse}, y esa prosa la escribe cada quien a su manera:
 * unas rutas lo nombraban ({@code users:read}), otras decían «Sin permiso» a secas, otras lo
 * describían sin nombrarlo («el permiso de creación de países»), y tres catálogos que se abrieron
 * al público seguían declarando un {@code 403} que ya no podía ocurrir. El frontend, que necesita
 * el nombre exacto para decidir qué botón pintar, no tenía de dónde sacarlo con fiabilidad. Es el
 * mismo modo de fallo que {@code OpenApiContractIT} existe para evitar en las rutas: lo que se
 * declara aparte de donde se aplica envejece sin que nada lo note.
 *
 * <p>Aquí el permiso sale de la <b>misma</b> {@code @PreAuthorize} que Spring evalúa, de modo que
 * no puede decir una cosa mientras el filtro hace otra. Cada operación gana dos cosas:
 *
 * <ul>
 *   <li>La extensión <b>{@code x-required-permission}</b> con el nombre del permiso, para el
 *       cliente generado y para cualquier herramienta: un campo, no una frase que haya que
 *       interpretar. Solo está cuando hay permiso.
 *   <li>Una <b>primera línea en la descripción</b>, para quien lee Swagger UI: «Permiso requerido:
 *       …». Es lo primero que se quiere saber de una ruta, y por eso va antes que cualquier otra
 *       cosa.
 * </ul>
 *
 * <p>Las operaciones sin {@code hasAuthority} son de dos clases y el contrato las distingue: las
 * <b>públicas</b> —las que {@link SecurityConfig#esPublica} reconoce— quedan con {@code security}
 * vacío, que es como OpenAPI dice «sin token» y lo que hace que Swagger UI no les pinte el candado;
 * y las que solo exigen <b>estar autenticado</b> lo dicen en la línea, y remiten a la descripción
 * para el alcance, porque en ellas el alcance lo decide el servicio (el actor, o el par
 * actor–consultado) y no una anotación.
 *
 * <p><b>Una expresión que no se reconoce detiene el arranque.</b> Hoy toda {@code @PreAuthorize}
 * del sistema es {@code hasAuthority('recurso:accion')} o {@code isAuthenticated()}. El día que
 * alguien escriba una combinación —{@code hasAuthority('a') or hasAuthority('b')}— el contrato no
 * sabría qué decir, y decir algo a medias es peor que fallar: aquí se falla, y quien la escribió
 * amplía este traductor a la vez.
 */
@Component
public class RequiredPermissionCustomizer implements OperationCustomizer, OpenApiCustomizer {

  /** Nombre de la extensión en el contrato. Constante porque la prueba la referencia. */
  public static final String EXTENSION = "x-required-permission";

  /** Prefijo de la línea que encabeza la descripción. La prueba busca por él. */
  public static final String ENCABEZADO = "**Permiso requerido:**";

  /**
   * La operación es sensible (`RN-SP-063`, `RF-SP-073`). Constante porque la prueba la referencia.
   */
  public static final String EXTENSION_SENSIBLE = "x-requires-recent-mfa";

  private final SensitivePermissions sensibles;

  public RequiredPermissionCustomizer(SensitivePermissions sensibles) {
    this.sensibles = sensibles;
  }

  /** Primera fase, operación por operación: el permiso pasa de la anotación a la extensión. */
  @Override
  public Operation customize(Operation operation, HandlerMethod handlerMethod) {
    // El análisis vive en `PreAuthorizePermission`, compartido con el interceptor de las
    // operaciones sensibles (`RF-SP-073`): sigue negándose a describir una regla que no sea un
    // `hasAuthority` simple, y eso es lo que permite al interceptor fiarse de él.
    PreAuthorizePermission.de(handlerMethod)
        .ifPresent(
            permiso -> {
              operation.addExtension(EXTENSION, permiso);
              if (sensibles.esSensible(permiso)) {
                operation.addExtension(EXTENSION_SENSIBLE, true);
              }
            });
    return operation;
  }

  /**
   * Segunda fase, con el documento entero: la línea de prosa y el {@code security} vacío de las
   * públicas. Va aquí y no en la primera porque la ruta —que es lo que decide si es pública— no
   * llega al {@link OperationCustomizer}: solo el método Java.
   */
  @Override
  public void customise(OpenAPI openApi) {
    if (openApi.getPaths() == null) {
      return;
    }
    openApi
        .getPaths()
        .forEach(
            (ruta, item) -> {
              for (Map.Entry<PathItem.HttpMethod, Operation> entrada :
                  item.readOperationsMap().entrySet()) {
                describir(HttpMethod.valueOf(entrada.getKey().name()), ruta, entrada.getValue());
              }
            });
  }

  private static void describir(HttpMethod metodo, String ruta, Operation operacion) {
    String linea;
    if (permisoDe(operacion) != null) {
      linea = ENCABEZADO + " `" + permisoDe(operacion) + "`. Sin él, `403` (`AUTH-002`).";
      if (sensible(operacion)) {
        linea +=
            "\n\n**Operación sensible** (`RN-SP-063`): exige haber verificado el segundo factor"
                + " hace cinco minutos o menos. Si no, `403` con el tipo `reverificacion-requerida` y"
                + " `verificationPath`: se presenta el código en `POST /api/v1/auth/mfa/verification`"
                + " y se repite la petición con el token que devuelve.";
      }
    } else if (SecurityConfig.esPublica(metodo, ruta)) {
      linea = ENCABEZADO + " ninguno. Ruta pública: responde sin token.";
      // `security: []` es la forma en que OpenAPI dice «esta operación no usa
      // el esquema global». Sin esto, Swagger UI le pinta el candado y quien
      // integra cree que necesita el token.
      operacion.setSecurity(new ArrayList<>());
    } else {
      // Hasta el 21-09-2026 aquí se escribía «ninguno más allá del token. El
      // alcance lo acota el servicio». Desde RF-SP-062 (RN-SEG-015) no existe
      // esa clase de operación: o declara permiso, o es pública. Una que llegue
      // aquí es un olvido, y el contrato no lo describe: lo señala.
      throw new IllegalStateException(
          "RN-SEG-015: "
              + metodo
              + " "
              + ruta
              + " se atiende con token y no declara permiso. Declárelo con @PreAuthorize o,"
              + " si es pública, en SecurityConfig");
    }
    String descripcion = operacion.getDescription();
    operacion.setDescription(
        descripcion == null || descripcion.isBlank() ? linea : linea + "\n\n" + descripcion);
  }

  private static boolean sensible(Operation operacion) {
    Map<String, Object> extensiones = operacion.getExtensions();
    return extensiones != null && Boolean.TRUE.equals(extensiones.get(EXTENSION_SENSIBLE));
  }

  private static String permisoDe(Operation operacion) {
    Map<String, Object> extensiones = operacion.getExtensions();
    return extensiones == null ? null : (String) extensiones.get(EXTENSION);
  }
}
