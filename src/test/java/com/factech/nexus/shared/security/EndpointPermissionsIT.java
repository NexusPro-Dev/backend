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
  // `Map.ofEntries` Y NO `Map.of`: aquella se acaba en diez pares, y la lista
  // llegó a once el 05-09-2026 con las dos rutas de `RF-MV-008`. El límite no
  // avisa con un mensaje útil — dice que no hay método aplicable— y perder el
  // rato con eso una segunda vez no hace falta.
  private static final Map<String, String> SIN_PERMISO_A_PROPOSITO =
      Map.ofEntries(
          Map.entry(
              "POST /api/v1/auth/registration",
              "PÚBLICO POR DEFINICIÓN (`RF-SP-045`, 09-09-2026): quien se registra no tiene cuenta con"
                  + " la que autenticarse. Es el PRIMER endpoint público que ESCRIBE, y lo que"
                  + " sostiene que no sea un agujero no es un token: la cuenta nace en"
                  + " `FTD_PENDIENTE` —autentica y no opera— y el origen está acotado por"
                  + " `RateLimitFilter`"),
          Map.entry(
              "GET /api/v1/countries",
              "PÚBLICO POR DECISIÓN desde el 08-09-2026: el formulario de registro elige país"
                  + " antes de que exista la cuenta (`RF-SP-045`). Es una lista de opciones y no"
                  + " identifica a nadie. `countries:read` sigue sembrado y deja de gobernar esta"
                  + " lectura; el `POST` y el `PATCH` de países NO se abren"),
          Map.entry(
              "GET /api/v1/document-types",
              "PÚBLICO POR DECISIÓN desde el 08-09-2026, por lo mismo que el de países: sin él,"
                  + " el formulario de registro no tiene de dónde sacar el tipo de documento"),
          Map.entry(
              "GET /api/v1/brokers",
              "PÚBLICO POR DECISIÓN desde el 08-09-2026 (`RF-SP-052`), por lo mismo que los otros"
                  + " dos catálogos. `brokers:read` nació el mismo día y quedó sin endpoint que lo"
                  + " exija, como `products:hotlink`"),
          Map.entry(
              "GET /api/v1/users/{id}/broker-accounts",
              "SIN @PreAuthorize A PROPÓSITO (`RF-SP-055`, 10-09-2026), y es el primero de todo el"
                  + " sistema por este motivo: la autorización no es una función del actor sino"
                  + " DEL PAR (actor, persona consultada) —`broker-accounts:read`, o ser su"
                  + " SUPERIOR COMERCIAL VIGENTE (`RN-SP-046`)—, y expresarla en SpEL metería una"
                  + " consulta a la base dentro de una anotación, donde no se prueba ni se depura."
                  + " Vive en `GetBrokerAccountsService`. Quien no es ninguna de las dos cosas"
                  + " recibe `404`, indistinguible del de una persona inexistente: con `403`,"
                  + " cualquier vendedor podría recorrer identificadores y saber cuáles son"
                  + " personas reales"),
          Map.entry(
              "GET /api/v1/users/me/team/broker-accounts",
              "Solo estar autenticado (`RF-SP-056`, 10-09-2026): NO ADMITE DECIR SOBRE QUIÉN se"
                  + " pregunta —el equipo es el del actor—, de modo que no hay alcance que"
                  + " autorizar. `broker-accounts:read` NO la gobierna: quien lo tenga ve las"
                  + " cuentas de cualquiera por `GET /api/v1/users/{id}/broker-accounts`, persona"
                  + " a persona, y no el equipo ajeno de una vez. Es el mismo criterio que"
                  + " `GET /api/v1/movements/mine`"),
          Map.entry(
              "POST /api/v1/auth/password-recovery",
              "Público por definición (`RF-SP-040`): quien olvidó su contraseña no puede"
                  + " autenticarse para pedir recuperarla"),
          Map.entry(
              "POST /api/v1/auth/password-recovery/confirmation",
              "Público a propósito (`RF-SP-040`): lo que autoriza es el permiso temporal que la"
                  + " solicitud envió al correo de la cuenta, no un token"),
          Map.entry(
              "POST /api/v1/auth/login",
              "Público por definición: no puede exigirse credencial para obtener una credencial"),
          Map.entry(
              "POST /api/v1/auth/refresh",
              "Público por definición: quien renueva no porta todavía un token de acceso válido"),
          Map.entry(
              "POST /api/v1/auth/logout",
              "Público a propósito (`RF-SP-036`): exigir token vigente impediría cerrar la sesión"
                  + " justo cuando más falta hace, que es cuando se sospecha que la robaron"),
          Map.entry(
              "GET /api/v1/users/me",
              "El actor y solo el actor (`RF-SP-039`): no admite parámetro, de modo que no hay"
                  + " nada que autorizar más allá de estar autenticado"),
          Map.entry(
              "PATCH /api/v1/users/me",
              "El actor y solo el actor (`RF-SP-044`): toma la persona del token y no admite"
                  + " identificador, de modo que no hay nadie más a quien pudiera editar. Editar"
                  + " la ficha ajena es `RF-SP-027`, y esa sí exige `users:update`"),
          Map.entry(
              "POST /api/v1/auth/password",
              "La propia contraseña (`RF-SP-037` §5): «no hay permiso asociado más allá de estar"
                  + " autenticado. Nadie cambia la contraseña de otro por este camino». Cambiar la"
                  + " ajena es `RF-SP-038`, y esa sí exige `users:reset-password`"),
          Map.entry(
              "GET /api/v1/payment-methods",
              "PÚBLICO POR DECISIÓN desde el 09-09-2026 (`RF-MV-009`, `RN-MV-024`): el formulario"
                  + " de registro por enlace elige CON QUÉ SE PAGA antes de que exista la cuenta,"
                  + " igual que elige país, tipo de documento y broker. Ya no exigía permiso"
                  + " —`movements:read` gobierna VER VENTAS y está reservado al"
                  + " superadministrador—, de modo que abrirlo NO DEJA NINGÚN PERMISO HUÉRFANO:"
                  + " es la diferencia con los tres catálogos de `SP`. Lo que sostiene que no"
                  + " publique nada es el predicado de la consulta, que lleva los DOS EJES —activo"
                  + " y visibilidad `PUBLICO`—: el anónimo recibe exactamente lo que recibía el"
                  + " autenticado"),
          Map.entry(
              "GET /api/v1/movements/mine",
              "Los movimientos del actor y de nadie más (`RF-MV-008`): no admite decir sobre"
                  + " quién se pregunta, de modo que no hay alcance que autorizar. Exigir"
                  + " `movements:read` obligaría a concederle a todo vendedor un permiso de"
                  + " ADMINISTRACIÓN que le daría de paso las ventas de sus compañeros — y a un"
                  + " cliente, las de todo el mundo"),
          Map.entry(
              "GET /api/v1/hotlinks/{username}/{code}",
              "PÚBLICO POR DECISIÓN y no por definición (`RF-PM-008`): un enlace se abre antes de"
                  + " registrarse. Es el primero del sistema que publica el nombre de una persona,"
                  + " y su alcance está acotado dos veces — solo productos activos de alcance"
                  + " HOTLINKS (`RN-PM-021`) y solo el nombre de quien es fuerza comercial"
                  + " (`RN-PM-022`). El recorrido a ciegas lo acota el límite de tasa por origen"),
          Map.entry(
              "GET /api/v1/movements/mine/{id}",
              "El detalle de lo propio (`RF-MV-008`): el alcance va dentro de la consulta y un"
                  + " movimiento ajeno responde `404`, igual que uno inexistente. Sin esta ruta el"
                  + " listado no llevaría a ninguna parte, porque `RF-MV-007` exige"
                  + " `movements:read`"));

  // `GET /api/v1/products/available` (`RF-PM-007`) figuraba aquí hasta el
  // 02-09-2026: exigía solo estar autenticado. Desde `products:sale`
  // declara su permiso como cualquier otro endpoint, y `declaraPermiso`
  // la reconoce sin necesitar la excepción.

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
