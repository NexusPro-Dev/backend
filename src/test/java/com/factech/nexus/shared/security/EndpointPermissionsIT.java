package com.factech.nexus.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
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
   * Endpoints <b>públicos</b>: los que se atienden <b>sin token</b>, y el motivo de cada uno.
   *
   * <p>Desde el 21-09-2026 (`RF-SP-062`, `RN-SEG-015`: autenticarse no autoriza nada) esta lista
   * solo admite públicas. Hasta entonces se llamaba {@code SIN_PERMISO_A_PROPOSITO} y admitía una
   * segunda clase de excepción —«autenticada a propósito: el actor sale del token y no hay nada que
   * autorizar»—, con once entradas. Las once tienen permiso propio y la clase desapareció: una
   * operación con token sin {@code @PreAuthorize} falla aquí diciendo cuál, y la única salida es
   * declararla pública en {@code SecurityConfig} <b>y</b> aquí, con el motivo. La segunda mitad la
   * vigila {@link #lasPublicasSeAtiendenSinToken}: cada entrada responde sin token con algo que no
   * es 401.
   */
  // `Map.ofEntries` Y NO `Map.of`: aquella se acaba en diez pares, y la lista
  // llegó a once el 05-09-2026 con las dos rutas de `RF-MV-008`. El límite no
  // avisa con un mensaje útil — dice que no hay método aplicable— y perder el
  // rato con eso una segunda vez no hace falta.
  private static final Map<String, String> PUBLICAS =
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
              "GET /api/v1/hotlinks/{username}/{code}",
              "PÚBLICO POR DECISIÓN y no por definición (`RF-PM-008`): un enlace se abre antes de"
                  + " registrarse. Es el primero del sistema que publica el nombre de una persona,"
                  + " y su alcance está acotado dos veces — solo productos activos de alcance"
                  + " HOTLINK o AMBOS (`RN-PM-021`) y solo el nombre de quien es fuerza comercial"
                  + " (`RN-PM-022`). El recorrido a ciegas lo acota el límite de tasa por origen"),
          Map.entry(
              "GET /api/v1/hotlinks/{username}/packages/{code}",
              "PÚBLICO POR DECISIÓN (`RF-PM-026`, 15-09-2026): el hotlink del PAQUETE, la cuarta"
                  + " ruta pública del módulo. Hereda del hotlink del producto sus dos acotaciones"
                  + " —paquete activo, vivo y de alcance HOTLINK o AMBOS, y solo el nombre de quien"
                  + " es fuerza comercial— y su 404 uniforme, que aquí cubre además al paquete que"
                  + " hoy no se puede ofrecer. Necesitó su PROPIA declaración en RUTAS_PUBLICAS:"
                  + " el patrón del producto es de dos segmentos y esta ruta tiene tres. La cota"
                  + " de tasa la hereda, porque el filtro decide por prefijo"),
          Map.entry(
              "GET /api/v1/products/{id}/comments",
              "PÚBLICO POR DECISIÓN (`RF-PM-012`, 14-09-2026): la pantalla del hotlink necesita"
                  + " las reseñas y no tiene con qué autenticarse. Es la SEGUNDA ruta pública que"
                  + " publica el nombre de una persona, y hereda del hotlink sus dos decisiones —"
                  + " del autor solo nombre y apellido (`RN-PM-030`) y 404 uniforme para el producto"
                  + " inexistente, inactivo o retirado (`RN-PM-028`)—. Solo el GET: el POST de la"
                  + " misma ruta, `/comments/mine` y el PATCH/DELETE de `/comments/{commentId}`"
                  + " exigen `products:comment`. La cota de tasa se cuenta por la familia"),
          Map.entry(
              "GET /api/v1/product-images/{imageId}",
              "PÚBLICO POR DECISIÓN (`RF-PM-016`, 14-09-2026): las cuatro lecturas del producto"
                  + " devuelven la dirección de su portada en `coverImageUrl`, una de ellas —el"
                  + " hotlink— es pública, y un `<img>` no lleva token. Es la ÚNICA ruta del"
                  + " sistema que sirve bytes y no JSON, y no identifica a nadie: sirve una imagen"
                  + " que administración subió para que se viera, sin mirar el producto. Solo el"
                  + " GET: subir y quitar la portada viven en `/products/{id}/cover` bajo"
                  + " `products:update`. Solo tres tipos —SVG fuera—, `nosniff`, y la cota de tasa"
                  + " por la familia (`security.md` §6)"));

  // `GET /api/v1/products/available` (`RF-PM-007`) figuraba aquí hasta el
  // 02-09-2026: exigía solo estar autenticado. Desde `products:sale`
  // declara su permiso como cualquier otro endpoint, y `declaraPermiso`
  // la reconoce sin necesitar la excepción.

  /**
   * Permisos que TODAVÍA gobiernan más de una operación, con fecha y motivo (`RN-SEG-014`,
   * `RF-SP-060` · plan §4).
   *
   * <p>Es la misma técnica que la lista blanca de arriba: una excepción con fecha es la diferencia
   * entre un pendiente y un olvido. `V28` ya sembró los dieciocho permisos de `AC`; sus
   * controladores los declaran en el tramo 3 de `RF-SP-060`, después del bloque 4 de `AC`, y ese
   * tramo VACÍA este mapa. La prueba `elPendienteNoSePudre` exige que cada entrada siga
   * repitiéndose de verdad, para que el tramo 3 no pueda olvidarse de quitarla.
   */
  private static final Map<String, String> REPARTO_PENDIENTE =
      Map.ofEntries(
          Map.entry(
              "courses:update",
              "AC se reparte entero en el tramo 3 de RF-SP-060, después de su bloque 4 (19-09-2026):"
                  + " estado, módulos, lecciones y relaciones tienen ya código propio en V28"),
          Map.entry(
              "courses:read",
              "Lo mismo: el listado es courses:list y la lección lessons:read desde V28"),
          Map.entry(
              "course-categories:read",
              "Lo mismo: el listado es course-categories:list desde V28"));

  /**
   * La tabla operación → permiso de `RF-SP-060` · `spec.md` §6.2, completa: TODAS las operaciones
   * que exigen permiso, con el código exacto que exigen (`CA-SP-690`, la mitad positiva; la
   * negativa —con el padre y sin el hijo, 403— vive en la suite de cada módulo).
   *
   * <p>`AC` figura con los códigos de HOY —los padres— y no con los de `V28`, hasta el tramo 3.
   */
  private static final Map<String, String> PERMISO_DE_CADA_OPERACION =
      Map.ofEntries(
          // ---- SP · roles ----
          Map.entry("POST /api/v1/roles", "roles:create"),
          Map.entry("GET /api/v1/roles", "roles:list"),
          Map.entry("GET /api/v1/roles/{id}", "roles:read"),
          Map.entry("PATCH /api/v1/roles/{id}", "roles:update"),
          Map.entry("PATCH /api/v1/roles/{id}/status", "roles:change-status"),
          Map.entry("PATCH /api/v1/roles/{id}/parent", "roles:assign-parent"),
          Map.entry("POST /api/v1/roles/{id}/permissions", "roles:assign-permissions"),
          Map.entry("POST /api/v1/roles/{id}/permissions/revocations", "roles:revoke-permissions"),
          Map.entry("POST /api/v1/roles/{id}/deletion", "roles:delete"),
          // ---- SP · permisos, membresías, auditoría, catálogos ----
          Map.entry("GET /api/v1/permissions", "permissions:list"),
          Map.entry("GET /api/v1/permissions/{id}", "permissions:read"),
          Map.entry("POST /api/v1/memberships", "memberships:create"),
          Map.entry("GET /api/v1/memberships", "memberships:list"),
          Map.entry("GET /api/v1/memberships/{id}", "memberships:read"),
          Map.entry("GET /api/v1/audit/changes", "audit:read-changes"),
          Map.entry("GET /api/v1/audit/deletions", "audit:read-deletions"),
          Map.entry("GET /api/v1/audit/errors", "audit:read-errors"),
          Map.entry("GET /api/v1/audit/security", "audit:read-security"),
          Map.entry("POST /api/v1/countries", "countries:create"),
          Map.entry("PATCH /api/v1/countries/{id}/status", "countries:update"),
          Map.entry("GET /api/v1/currencies", "currencies:read"),
          Map.entry("PATCH /api/v1/currencies/{id}/status", "currencies:update"),
          Map.entry("POST /api/v1/exchange-rates", "exchange-rates:create"),
          // ---- SP · usuarios ----
          Map.entry("POST /api/v1/users", "users:create"),
          Map.entry("GET /api/v1/users", "users:list"),
          Map.entry("GET /api/v1/users/{id}", "users:read"),
          // ---- SP · alcance propio (RF-SP-062, desde el 21-09-2026) ----
          Map.entry("GET /api/v1/users/me", "users:read-own-profile"),
          Map.entry("PATCH /api/v1/users/me", "users:update-own-profile"),
          Map.entry("POST /api/v1/auth/password", "users:change-own-password"),
          Map.entry("GET /api/v1/users/me/sellers", "users:read-own-sellers"),
          Map.entry("GET /api/v1/users/me/clients", "users:read-own-clients"),
          Map.entry("GET /api/v1/users/me/team/broker-accounts", "broker-accounts:read-own-team"),
          Map.entry("GET /api/v1/users/{id}/broker-accounts", "broker-accounts:read-team-member"),
          Map.entry("GET /api/v1/users/{id}/team", "users:read-team"),
          Map.entry("GET /api/v1/users/{id}/sellers", "users:read-sellers"),
          Map.entry("GET /api/v1/users/{id}/clients", "users:read-clients"),
          Map.entry("PATCH /api/v1/users/{id}", "users:update"),
          Map.entry("PATCH /api/v1/users/{id}/status", "users:change-status"),
          Map.entry("POST /api/v1/users/{id}/deletion", "users:delete"),
          Map.entry("POST /api/v1/users/{id}/roles", "users:assign-roles"),
          Map.entry("POST /api/v1/users/{id}/roles/revocations", "users:revoke-roles"),
          Map.entry("PATCH /api/v1/users/{id}/supervisor", "users:assign-supervisor"),
          Map.entry("POST /api/v1/users/{id}/password-reset", "users:reset-password"),
          // ---- SP · cuentas de broker ----
          Map.entry("GET /api/v1/broker-accounts", "broker-accounts:read"),
          Map.entry("GET /api/v1/broker-accounts/indicators", "broker-accounts:read-indicators"),
          // ---- PM · productos ----
          Map.entry("POST /api/v1/products", "products:create"),
          Map.entry("GET /api/v1/products", "products:list"),
          Map.entry("GET /api/v1/products/{id}", "products:read"),
          Map.entry("GET /api/v1/products/available", "products:sale"),
          Map.entry("GET /api/v1/products/hotlinks", "products:hotlink"),
          Map.entry("PATCH /api/v1/products/{id}", "products:update"),
          Map.entry("PATCH /api/v1/products/{id}/status", "products:change-status"),
          Map.entry("PUT /api/v1/products/{id}/cover", "products:set-cover"),
          Map.entry("DELETE /api/v1/products/{id}/cover", "products:remove-cover"),
          Map.entry("POST /api/v1/products/{id}/deletion", "products:delete"),
          Map.entry("POST /api/v1/products/{id}/comments", "products:comment"),
          Map.entry("GET /api/v1/products/{id}/comments/mine", "products:read-own-comments"),
          Map.entry("PATCH /api/v1/products/{id}/comments/{commentId}", "products:update-comment"),
          Map.entry("DELETE /api/v1/products/{id}/comments/{commentId}", "products:delete-comment"),
          // ---- PM · paquetes ----
          Map.entry("POST /api/v1/packages", "packages:create"),
          Map.entry("GET /api/v1/packages", "packages:list"),
          Map.entry("GET /api/v1/packages/{id}", "packages:read"),
          Map.entry("PATCH /api/v1/packages/{id}", "packages:update"),
          Map.entry("PATCH /api/v1/packages/{id}/status", "packages:change-status"),
          Map.entry("PUT /api/v1/packages/{id}/cover", "packages:set-cover"),
          Map.entry("DELETE /api/v1/packages/{id}/cover", "packages:remove-cover"),
          Map.entry("POST /api/v1/packages/{id}/products", "packages:add-product"),
          Map.entry("PATCH /api/v1/packages/{id}/products/{productId}", "packages:update-product"),
          Map.entry("DELETE /api/v1/packages/{id}/products/{productId}", "packages:remove-product"),
          Map.entry("POST /api/v1/packages/{id}/deletion", "packages:delete"),
          // ---- CM ----
          Map.entry("POST /api/v1/commission-rates", "commissions:create"),
          Map.entry("GET /api/v1/commission-rates", "commissions:read"),
          Map.entry("PATCH /api/v1/commission-rates/{id}", "commissions:update"),
          Map.entry("POST /api/v1/commission-rates/{id}/deletion", "commissions:delete"),
          Map.entry("GET /api/v1/commissions/effective", "commissions:read-effective"),
          Map.entry("POST /api/v1/user-commission-rates", "user-commission-rates:create"),
          Map.entry("GET /api/v1/user-commission-rates", "user-commission-rates:read"),
          Map.entry("PATCH /api/v1/user-commission-rates/{id}", "user-commission-rates:update"),
          Map.entry(
              "POST /api/v1/user-commission-rates/{id}/deletion", "user-commission-rates:delete"),
          Map.entry("GET /api/v1/product-commission-rates", "product-commission-rates:read"),
          // ---- MV ----
          Map.entry("POST /api/v1/movements", "movements:create"),
          Map.entry("GET /api/v1/movements", "movements:read"),
          Map.entry("POST /api/v1/movements/{id}/confirmation", "movements:confirm"),
          Map.entry("POST /api/v1/movements/{id}/voiding", "movements:void"),
          Map.entry("POST /api/v1/movements/{id}/seller-assignments", "movements:assign-sellers"),
          // ---- MV · alcance propio (RF-SP-062, desde el 21-09-2026) ----
          Map.entry("GET /api/v1/movements/mine/shopping", "movements:list-own"),
          Map.entry("GET /api/v1/movements/mine/{id}", "movements:read-own"),
          Map.entry("GET /api/v1/movements/mine/products", "movements:read-own-products"),
          Map.entry("GET /api/v1/movements/sales", "movements:list-sales"),
          Map.entry("GET /api/v1/movements/sales/lines", "movements:list-sale-lines"),
          Map.entry("POST /api/v1/packages/{code}/purchases", "packages:buy"),
          // ---- SP · equipos (RF-SP-063 a RF-SP-070, V34) ----
          Map.entry("POST /api/v1/teams", "teams:create"),
          Map.entry("GET /api/v1/teams", "teams:list"),
          Map.entry("GET /api/v1/teams/{id}", "teams:read"),
          Map.entry("PATCH /api/v1/teams/{id}", "teams:update"),
          Map.entry("PATCH /api/v1/teams/{id}/status", "teams:change-status"),
          Map.entry("POST /api/v1/teams/{id}/deletion", "teams:delete"),
          Map.entry("POST /api/v1/teams/{id}/members", "teams:assign-members"),
          Map.entry("POST /api/v1/teams/{id}/members/removals", "teams:remove-members"),
          // ---- AC · con los PADRES hasta el tramo 3 de RF-SP-060 ----
          Map.entry("POST /api/v1/course-categories", "course-categories:create"),
          Map.entry("GET /api/v1/course-categories", "course-categories:read"),
          Map.entry("GET /api/v1/course-categories/{id}", "course-categories:read"),
          Map.entry("PATCH /api/v1/course-categories/{id}", "course-categories:update"),
          Map.entry("POST /api/v1/course-categories/{id}/deletion", "course-categories:delete"),
          Map.entry("POST /api/v1/courses", "courses:create"),
          Map.entry("GET /api/v1/courses", "courses:read"),
          Map.entry("GET /api/v1/courses/{id}", "courses:read"),
          Map.entry("PATCH /api/v1/courses/{id}", "courses:update"),
          Map.entry("PATCH /api/v1/courses/{id}/status", "courses:update"),
          Map.entry("POST /api/v1/courses/{id}/deletion", "courses:delete"),
          Map.entry("POST /api/v1/courses/{courseId}/modules", "courses:update"),
          Map.entry("PATCH /api/v1/courses/{courseId}/modules/{moduleId}", "courses:update"),
          Map.entry("PATCH /api/v1/courses/{courseId}/modules/{moduleId}/status", "courses:update"),
          Map.entry(
              "POST /api/v1/courses/{courseId}/modules/{moduleId}/deletion", "courses:update"),
          Map.entry("POST /api/v1/courses/{courseId}/modules/{moduleId}/lessons", "courses:update"),
          Map.entry(
              "GET /api/v1/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}",
              "courses:read"),
          Map.entry(
              "PATCH /api/v1/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}",
              "courses:update"),
          Map.entry(
              "PATCH /api/v1/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}/status",
              "courses:update"),
          Map.entry(
              "POST /api/v1/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}/deletion",
              "courses:update"));

  private static final Pattern HAS_AUTHORITY = Pattern.compile("hasAuthority\\('([^']+)'\\)");

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
        if (declaraPermiso(entrada.getValue()) || PUBLICAS.containsKey(firma)) {
          continue;
        }
        sinDeclarar.add(firma);
      }
    }

    assertThat(sinDeclarar)
        .as(
            "estos endpoints se atienden con token y no exigen permiso (RN-SEG-015). Desde el"
                + " 21-09-2026 no hay «autenticada a propósito»: o declara su permiso, o es pública"
                + " —en SecurityConfig y en PUBLICAS, con el motivo—")
        .isEmpty();
  }

  @Test
  @DisplayName(
      "RN-SEG-015: cada pública se atiende SIN token —nunca 401— y una operación con permiso sin"
          + " token es 401 (CA-SP-723)")
  void lasPublicasSeAtiendenSinToken() {
    // Las dos mitades de la regla, contra la misma función que usa el filtro:
    // cada entrada de PUBLICAS es lo que SecurityConfig sirve con permitAll, y
    // ninguna operación anotada lo es. Sin esto, una ruta podría estar en la
    // lista y seguir exigiendo token —o al revés— sin que nadie lo notara.
    for (String firma : PUBLICAS.keySet()) {
      String[] partes = firma.split(" ", 2);
      assertThat(SecurityConfig.esPublica(HttpMethod.valueOf(partes[0]), partes[1]))
          .as("%s está en PUBLICAS pero SecurityConfig la exige autenticada", firma)
          .isTrue();
    }
    for (String firma : PERMISO_DE_CADA_OPERACION.keySet()) {
      String[] partes = firma.split(" ", 2);
      assertThat(SecurityConfig.esPublica(HttpMethod.valueOf(partes[0]), partes[1]))
          .as("%s declara permiso y SecurityConfig la sirve sin token", firma)
          .isFalse();
    }

    // Y las once que hasta el 21-09-2026 iban sin permiso, con el suyo (CA-SP-724
    // lo afirma una a una en OwnScopePermissionsIT; aquí basta con que conste).
    assertThat(PERMISO_DE_CADA_OPERACION)
        .containsEntry("GET /api/v1/users/me", "users:read-own-profile")
        .containsEntry("POST /api/v1/packages/{code}/purchases", "packages:buy");
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
        .containsAll(PUBLICAS.keySet());
  }

  @Test
  @DisplayName(
      "RN-SEG-014: ningún permiso gobierna dos operaciones, salvo los pendientes con fecha"
          + " (CA-SP-689)")
  void ningunPermisoGobiernaDosOperaciones() {
    Map<String, Set<String>> operacionesPorPermiso = operacionesPorPermiso();

    Map<String, Set<String>> repetidos = new TreeMap<>();
    operacionesPorPermiso.forEach(
        (permiso, operaciones) -> {
          if (operaciones.size() > 1 && !REPARTO_PENDIENTE.containsKey(permiso)) {
            repetidos.put(permiso, operaciones);
          }
        });

    assertThat(repetidos)
        .as(
            "estos permisos gobiernan más de una operación: conceder uno concede varias cosas."
                + " Un permiso, una operación (RN-SEG-014): separa las que sobran con su código"
                + " propio y su siembra")
        .isEmpty();
  }

  @Test
  @DisplayName("el reparto pendiente no se pudre: cada entrada sigue repitiéndose de verdad")
  void elPendienteNoSePudre() {
    Map<String, Set<String>> operacionesPorPermiso = operacionesPorPermiso();

    // Una excepción que sobrevive al reparto que la justificaba dejaría la
    // puerta abierta a que ese permiso volviera a agrupar sin que nadie lo
    // notara. El tramo 3 de RF-SP-060 tiene que vaciar el mapa.
    REPARTO_PENDIENTE
        .keySet()
        .forEach(
            permiso ->
                assertThat(operacionesPorPermiso.getOrDefault(permiso, Set.of()))
                    .as("REPARTO_PENDIENTE cita a %s, que ya gobierna una sola operación", permiso)
                    .hasSizeGreaterThan(1));
  }

  @Test
  @DisplayName(
      "cada operación exige exactamente el permiso de RF-SP-060 · spec.md §6.2 (CA-SP-690)")
  void cadaOperacionExigeElPermisoDeLaTabla() {
    Map<String, String> reales = new TreeMap<>();
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada :
        rutas.getHandlerMethods().entrySet()) {
      String permiso = permisoDe(entrada.getValue());
      if (permiso == null) {
        continue;
      }
      for (String firma : firmasDe(entrada.getKey())) {
        if (firma.contains("/api/v1")) {
          reales.put(firma, permiso);
        }
      }
    }

    // Las dos direcciones: ninguna operación fuera de la tabla, y ninguna fila
    // de la tabla sin operación. Con `containsExactlyInAnyOrderEntriesOf` un
    // endpoint nuevo que nazca con permiso obliga a escribirlo aquí, que es
    // donde se lee de un vistazo qué exige cada uno.
    assertThat(reales).containsExactlyInAnyOrderEntriesOf(PERMISO_DE_CADA_OPERACION);
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

  /** Todas las operaciones de `/api/v1` agrupadas por el permiso que exigen. */
  private Map<String, Set<String>> operacionesPorPermiso() {
    Map<String, Set<String>> porPermiso = new TreeMap<>();
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada :
        rutas.getHandlerMethods().entrySet()) {
      String permiso = permisoDe(entrada.getValue());
      if (permiso == null) {
        continue;
      }
      for (String firma : firmasDe(entrada.getKey())) {
        if (firma.contains("/api/v1")) {
          porPermiso.computeIfAbsent(permiso, p -> new TreeSet<>()).add(firma);
        }
      }
    }
    return porPermiso;
  }

  /**
   * El permiso que exige el manejador, o {@code null} si no exige ninguno (solo token, o sin
   * anotación). Lee la misma `@PreAuthorize` que Spring evalúa, como
   * `RequiredPermissionCustomizer`.
   */
  private static String permisoDe(HandlerMethod manejador) {
    Method metodo = manejador.getMethod();
    PreAuthorize anotacion = metodo.getAnnotation(PreAuthorize.class);
    if (anotacion == null) {
      anotacion = metodo.getDeclaringClass().getAnnotation(PreAuthorize.class);
    }
    if (anotacion == null) {
      return null;
    }
    Matcher m = HAS_AUTHORITY.matcher(anotacion.value());
    return m.find() ? m.group(1) : null;
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
