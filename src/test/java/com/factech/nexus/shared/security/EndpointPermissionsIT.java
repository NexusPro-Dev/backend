package com.factech.nexus.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * `RNF-SEG-002` — <b>todo endpoint declara su permiso</b> (`security.md` §11, issue #26).
 *
 * <p><b>Por qué esta prueba y no la disciplina.</b> La configuración de seguridad deniega por
 * defecto, de modo que ninguna ruta queda abierta a un anónimo. Pero eso cubre media pregunta: un
 * endpoint autenticado <b>sin {@code @PreAuthorize}</b> queda accesible a <b>cualquier persona
 * autenticada</b>, con el rol que sea. Y ese olvido no rompe nada: no falla la compilación, y las
 * pruebas del propio endpoint se escriben con un actor que tiene permisos, así que pasan.
 *
 * <p>`security.md` §11 la describe como <b>«la única forma de garantizar que un endpoint nuevo no
 * quede expuesto por descuido»</b>. Con cuarenta y tres endpoints publicados, es lo que impide que
 * el cuarenta y cuatro nazca abierto.
 *
 * <p><b>La lista blanca es la parte que importa.</b> No está para hacer pasar la prueba: está para
 * que añadir un endpoint sin permiso <b>obligue a escribir por qué</b>, en una revisión que alguien
 * lee, en lugar de que pase inadvertido.
 */
class EndpointPermissionsIT extends IntegrationTestBase {

  /**
   * Endpoints deliberadamente <b>sin</b> exigencia de permiso, y el motivo de cada uno.
   *
   * <p>Quien añada una entrada aquí está declarando que ese endpoint es accesible para cualquier
   * persona autenticada —o para nadie autenticado, si además es público—, y el motivo queda escrito
   * al lado. Es la diferencia entre una excepción y un olvido.
   */
  private static final Map<String, String> SIN_PERMISO_A_PROPOSITO =
      Map.of(
          "POST /api/v1/auth/password-recovery",
          "Público por definición (`RF-SP-040`): quien olvidó su contraseña no puede"
              + " autenticarse para pedir recuperarla",
          "POST /api/v1/auth/password-recovery/confirmation",
          "Público a propósito (`RF-SP-040`): lo que autoriza es el permiso temporal que la"
              + " solicitud envió al correo de la cuenta, no un token",
          "POST /api/v1/auth/login",
          "Público por definición: no puede exigirse credencial para obtener una credencial",
          "POST /api/v1/auth/refresh",
          "Público por definición: quien renueva no porta todavía un token de acceso válido",
          "POST /api/v1/auth/logout",
          "Público a propósito (`RF-SP-036`): exigir token vigente impediría cerrar la sesión"
              + " justo cuando más falta hace, que es cuando se sospecha que la robaron",
          "GET /api/v1/users/me",
          "El actor y solo el actor (`RF-SP-039`): no admite parámetro, de modo que no hay"
              + " nada que autorizar más allá de estar autenticado",
          "PATCH /api/v1/users/me",
          "El actor y solo el actor (`RF-SP-044`): toma la persona del token y no admite"
              + " identificador, de modo que no hay nadie más a quien pudiera editar. Editar la"
              + " ficha ajena es `RF-SP-027`, y esa sí exige `users:update`",
          "POST /api/v1/auth/password",
          "La propia contraseña (`RF-SP-037` §5): «no hay permiso asociado más allá de estar"
              + " autenticado. Nadie cambia la contraseña de otro por este camino». Cambiar la"
              + " ajena es `RF-SP-038`, y esa sí exige `users:reset-password`");

  /**
   * El mapeo de la aplicación, <b>por nombre</b>.
   *
   * <p>Actuator registra el suyo —{@code controllerEndpointHandlerMapping}— y sin cualificar hay
   * dos candidatos del mismo tipo: el contexto no arranca. Se pide el de la aplicación a propósito,
   * que es el que contiene los endpoints de negocio; los de actuator los gobierna el Art. XV.10 y
   * no esta regla.
   */
  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping rutas;

  @Test
  @DisplayName("todo endpoint de /api/v1 declara su permiso, o figura en la lista blanca")
  void ningunEndpointQuedaExpuestoPorDescuido() {
    Set<String> sinDeclarar = new TreeSet<>();

    for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada :
        rutas.getHandlerMethods().entrySet()) {

      for (String firma : firmasDe(entrada.getKey())) {
        if (!firma.contains("/api/v1")) {
          // `/actuator/health` lo exige el Art. XV.10 sin autenticación de
          // negocio, y las rutas de springdoc las gobierna `EXPOSE_API_DOCS`.
          continue;
        }
        if (declaraPermiso(entrada.getValue()) || SIN_PERMISO_A_PROPOSITO.containsKey(firma)) {
          continue;
        }
        sinDeclarar.add(firma);
      }
    }

    assertThat(sinDeclarar)
        .as(
            "estos endpoints no exigen permiso: cualquier persona autenticada puede ejecutarlos."
                + " Si es deliberado, decláralo en SIN_PERMISO_A_PROPOSITO con su motivo")
        .isEmpty();
  }

  @Test
  @DisplayName("la lista blanca no se pudre: cada excepción sigue correspondiendo a un endpoint")
  void laListaBlancaNoConservaFantasmas() {
    Set<String> existentes = new TreeSet<>();
    rutas.getHandlerMethods().keySet().forEach(info -> existentes.addAll(firmasDe(info)));

    // Una excepción que sobrevive al endpoint que la justificaba es peor que
    // inútil: el día que alguien reutilice esa ruta, nacerá abierta y con una
    // justificación escrita para otra cosa.
    assertThat(existentes)
        .as("la lista blanca cita endpoints que ya no existen")
        .containsAll(SIN_PERMISO_A_PROPOSITO.keySet());
  }

  @Test
  @DisplayName("hay al menos cuarenta endpoints: la prueba no pasa por no encontrar ninguno")
  void laPruebaEstaMirandoAlgo() {
    long deLaApi =
        rutas.getHandlerMethods().keySet().stream()
            .flatMap(info -> firmasDe(info).stream())
            .filter(firma -> firma.contains("/api/v1"))
            .count();

    // Sin esto, un cambio que dejara el mapeo vacío haría pasar la prueba en
    // verde sin haber comprobado nada — que es la forma en que una prueba de
    // ausencia deja de servir sin avisar.
    assertThat(deLaApi).isGreaterThanOrEqualTo(40);
  }

  /**
   * ¿El manejador exige un permiso?
   *
   * <p>Se mira el método y también la clase: {@code @PreAuthorize} sobre el controlador entero es
   * válido, aunque `security.md` §6 recomienda declararlo por método para que uno añadido más tarde
   * no herede en silencio un permiso que quizá no le corresponde.
   */
  private static boolean declaraPermiso(HandlerMethod manejador) {
    Method metodo = manejador.getMethod();
    return metodo.isAnnotationPresent(PreAuthorize.class)
        || metodo.getDeclaringClass().isAnnotationPresent(PreAuthorize.class);
  }

  /** Todas las combinaciones de método y ruta de un mapeo, como {@code "GET /api/v1/roles"}. */
  private static List<String> firmasDe(RequestMappingInfo info) {
    var metodos = info.getMethodsCondition().getMethods();
    var patrones = info.getPathPatternsCondition();

    List<String> rutas =
        patrones == null ? List.of() : patrones.getPatternValues().stream().sorted().toList();

    if (metodos.isEmpty()) {
      return rutas.stream().map(ruta -> "ANY " + ruta).toList();
    }
    return metodos.stream()
        .flatMap(metodo -> rutas.stream().map(ruta -> metodo.name() + " " + ruta))
        .toList();
  }
}
