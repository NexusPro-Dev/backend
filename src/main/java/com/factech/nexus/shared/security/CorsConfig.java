package com.factech.nexus.shared.security;

import com.factech.nexus.shared.observability.CorrelationFilter;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Autorización de orígenes cruzados (CORS) — pendiente n.º 2 de {@code ADR-001}.
 *
 * <p><b>El problema.</b> Publicar el contrato OpenAPI no basta para que el frontend pueda llamar:
 * el navegador impide a una página servida desde un origen leer la respuesta de otro salvo que el
 * servidor lo autorice de forma explícita. Sin esta clase, toda llamada desde el navegador falla
 * con un error de CORS aunque la petición sea correcta, esté autenticada y el backend responda
 * {@code 200} — y el síntoma que ve quien lo consume no menciona nunca al backend.
 *
 * <p><b>Los orígenes NO se escriben aquí.</b> Cuáles son depende del entorno —uno en local, otro en
 * pruebas, otro en producción— y por tanto son configuración y no código (Art. IX.1): llegan por
 * {@code CORS_ALLOWED_ORIGINS}, separados por coma. Un origen quemado en el repositorio obliga a
 * recompilar para desplegar bajo otro dominio y, peor, acaba autorizando en producción el {@code
 * localhost} de alguien.
 *
 * <p><b>Sin lista configurada no se autoriza a nadie</b>, que es el valor seguro por defecto (Art.
 * IV.1): un backend consumido de servidor a servidor no necesita CORS, y un despliegue que olvide
 * declarar la variable falla de forma visible en el navegador en lugar de quedar abierto en
 * silencio. La aplicación arranca igual, porque no hay nada inseguro que asumir.
 *
 * <p><b>Lo que sí está escrito aquí</b> son los métodos y las cabeceras, y no es un descuido: no
 * dependen del entorno sino del contrato, que es el mismo en todos. Hacerlos configurables
 * permitiría a un despliegue autorizar cabeceras que ningún endpoint acepta, sin ganar nada a
 * cambio.
 *
 * <p><b>Sin credenciales de navegador.</b> {@code allowCredentials} queda en {@code false} porque
 * el sistema no usa cookies de sesión (D-08): el token viaja en la cabecera {@code Authorization},
 * que el navegador no adjunta por su cuenta y que para CORS no cuenta como credencial. Activarlo
 * sin necesitarlo abriría la puerta a que una cookie futura viajara entre sitios sin que nadie lo
 * hubiera decidido.
 *
 * <p><b>El comodín {@code *} se rechaza al arrancar</b>, igual que un origen mal escrito. Autorizar
 * a cualquier origen convierte el navegador de cualquier persona en un cliente de esta API; y un
 * valor como {@code localhost:5173} —sin esquema— o {@code https://app.nexus.co/} —con barra final—
 * no casa jamás con el origen que envía el navegador, de modo que fallaría en ejecución con el
 * mismo error de CORS que se pretendía resolver. Falla al arrancar y con el motivo escrito (Art.
 * IX.5).
 */
@Configuration
public class CorsConfig {

  /**
   * Métodos autorizados: los que usa la API, más {@code OPTIONS} para la propia comprobación
   * previa. No aparecen {@code HEAD} ni {@code TRACE} porque ningún endpoint los declara.
   */
  private static final List<String> METODOS =
      List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

  /**
   * Cabeceras que el cliente puede enviar. {@code Authorization} y {@code Content-Type} son las de
   * cualquier llamada; {@code X-Correlation-Id} la propone el cliente para seguir una operación
   * entre sistemas ({@link CorrelationFilter}), y sin declararla aquí el navegador rechazaría la
   * petición en la comprobación previa.
   */
  private static final List<String> CABECERAS_ACEPTADAS =
      List.of("Authorization", "Content-Type", "Accept", CorrelationFilter.CABECERA);

  /**
   * Cabeceras que el cliente puede <b>leer</b> de la respuesta. El navegador solo expone un puñado
   * por omisión, y {@code Location} no está entre ellas: sin esta línea, quien registra un rol o
   * una persona recibe el {@code 201} y no puede leer la dirección del recurso que acaba de crear.
   * {@code X-Correlation-Id} se expone para que la interfaz pueda citarlo al reportar un error, que
   * es justo para lo que se devuelve (Art. XV.1).
   */
  private static final List<String> CABECERAS_EXPUESTAS =
      List.of("Location", CorrelationFilter.CABECERA);

  private final Set<String> origenes;
  private final Duration cacheDeLaComprobacionPrevia;

  public CorsConfig(
      @Value("${nexus.security.cors.allowed-origins:}") String configurados,
      @Value("${nexus.security.cors.max-age:PT30M}") Duration cacheDeLaComprobacionPrevia) {
    this.origenes =
        Arrays.stream(configurados.split(","))
            .map(String::trim)
            .filter(valor -> !valor.isEmpty())
            .peek(CorsConfig::exigirOrigenValido)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    this.cacheDeLaComprobacionPrevia = cacheDeLaComprobacionPrevia;
  }

  /**
   * Política aplicada a toda la API.
   *
   * <p>Spring Security coloca el filtro de CORS <b>antes</b> de la autorización, de modo que la
   * comprobación previa del navegador —un {@code OPTIONS} que nunca lleva la cabecera {@code
   * Authorization}— se responde sin llegar a la cadena, y no hace falta declararla ruta pública. Un
   * origen no autorizado recibe {@code 403} en esa comprobación previa y la llamada real ni
   * siquiera llega a emitirse.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration politica = new CorsConfiguration();
    politica.setAllowedOrigins(List.copyOf(origenes));
    politica.setAllowedMethods(METODOS);
    politica.setAllowedHeaders(CABECERAS_ACEPTADAS);
    politica.setExposedHeaders(CABECERAS_EXPUESTAS);
    // Ver el javadoc de la clase: no hay cookie de sesión que compartir.
    politica.setAllowCredentials(false);
    // Cuánto puede el navegador reutilizar la comprobación previa. Sin esto,
    // cada petición que no sea simple paga dos viajes en lugar de uno.
    politica.setMaxAge(cacheDeLaComprobacionPrevia);

    UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
    fuente.registerCorsConfiguration("/**", politica);
    return fuente;
  }

  /**
   * Un origen es esquema, host y puerto — nada más. Todo lo demás se rechaza al arrancar, con el
   * valor recibido en el mensaje para que quien lo declaró vea qué escribió.
   */
  private static void exigirOrigenValido(String origen) {
    if ("*".equals(origen)) {
      throw new IllegalStateException(
          "CORS_ALLOWED_ORIGINS no admite '*': autorizar a cualquier origen convierte el navegador"
              + " de cualquier persona en un cliente de esta API. Declare cada origen.");
    }

    URI uri;
    try {
      uri = new URI(origen);
    } catch (URISyntaxException noEsUri) {
      throw new IllegalStateException(
          "CORS_ALLOWED_ORIGINS contiene un origen mal formado: '" + origen + "'", noEsUri);
    }

    boolean esquemaValido = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
    boolean soloAutoridad =
        uri.getHost() != null
            && (uri.getPath() == null || uri.getPath().isEmpty())
            && uri.getQuery() == null
            && uri.getFragment() == null
            && uri.getUserInfo() == null;

    if (!esquemaValido || !soloAutoridad) {
      throw new IllegalStateException(
          "CORS_ALLOWED_ORIGINS admite orígenes con la forma esquema://host[:puerto] y recibió: '"
              + origen
              + "'. Un origen sin esquema, con barra final o con ruta no casa nunca con el que"
              + " envía el navegador, y la llamada fallaría con el mismo error de CORS.");
    }
  }
}
