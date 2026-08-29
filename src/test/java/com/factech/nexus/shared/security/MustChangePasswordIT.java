package com.factech.nexus.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * El cambio obligatorio de contraseña <b>retiene de verdad</b> (`RF-SP-034` · `FA-002` · `T-12`,
 * `RF-SP-037` · `T-06`, `RF-SP-039` · `T-05`).
 *
 * <p>Las tres tareas se prueban juntas porque son <b>una sola afirmación</b>: la marca cierra la
 * aplicación entera menos dos puertas, y comprobar el cierre sin comprobar las dos puertas
 * verificaría un sistema en el que la cuenta queda sin salida. Repartidas en tres archivos, cada
 * mitad pasaría por su cuenta.
 *
 * <p>Autentica de verdad —inicia sesión y presenta el token— porque el claim {@code mcp} solo
 * existe en un token real: con el actor simulado no habría nada que leer y la prueba daría verde
 * sin haber ejercitado el filtro.
 */
@AutoConfigureMockMvc
class MustChangePasswordIT extends IntegrationTestBase {

  private static final String CODIGO_ACOTADO = "AUDITORIA_ACOTADA";
  private static final String CLAVE = "ClaveLargaYSegura2026";
  private static final String NUEVA = "OtraClaveLargaDistinta2026";

  /** Un endpoint cualquiera que la persona SÍ tiene permiso para usar. */
  private static final String CUALQUIERA = "/api/v1/audit/changes";

  private static final String TIPO_RETENIDA =
      "https://nexus.factech.co/errors/cambio-de-contrasena-requerido";
  private static final String TIPO_SIN_PERMISO = "https://nexus.factech.co/errors/sin-permiso";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private PasswordHasher hasher;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping rutas;

  private UUID persona;

  @BeforeEach
  void prepararCuenta() {
    limpiar();
    persona = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status)
        VALUES (?, 'JPerez', 'juan@factech.co', 'Juan', 'Pérez', ?, false, 'ACTIVO')
        """,
        persona,
        hasher.hash(CLAVE));
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
        persona,
        crearRolAcotado(jdbc, CODIGO_ACOTADO, "Auditoría acotada"));
  }

  @AfterEach
  void noDejarSesionesDetras() {
    limpiar();
  }

  private void limpiar() {
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_roles WHERE user_id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
    jdbc.update(
        "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)",
        CODIGO_ACOTADO);
    jdbc.update("DELETE FROM roles WHERE code = ?", CODIGO_ACOTADO);
  }

  // ---------------------------------------------------------------------------
  // La marca cierra la aplicación
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`T-12` — con la marca puesta, un endpoint cualquiera responde el rechazo declarado")
  void conLaMarcaElRestoSeNiega() throws Exception {
    marcar();
    String token = accessToken(login());

    mvc.perform(get(CUALQUIERA).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TIPO_RETENIDA))
        .andExpect(jsonPath("$.changePasswordPath").value("/api/v1/auth/password"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  @DisplayName("el rechazo por la marca NO se confunde con el de un permiso que falta")
  void sonDosDenegacionesDistintas() throws Exception {
    // El rol acotado NO concede `memberships:read`. Sin la marca, ese mismo
    // endpoint da el OTRO 403 -- y la interfaz debe poder distinguirlos, porque
    // ante uno oculta la opción y ante el otro lleva a cambiar la contraseña.
    String limpio = accessToken(login());
    mvc.perform(get("/api/v1/memberships").header("Authorization", "Bearer " + limpio))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TIPO_SIN_PERMISO));

    marcar();
    String marcado = accessToken(login());
    mvc.perform(get("/api/v1/memberships").header("Authorization", "Bearer " + marcado))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TIPO_RETENIDA));
  }

  @Test
  @DisplayName("`CA-SP-481` — vencida y por vencer retienen igual: no se compara con el reloj")
  void vencidaYPorVencerRetienenIgual() throws Exception {
    // `ck_users_provisional_expiry` ata la caducidad a la marca: no puede haber
    // fecha sin `must_change_password`. Las dos columnas conviven en el esquema
    // aunque solo una decida el acceso — es la restricción la que impide que
    // discrepen en la dirección que importaría.
    caducar("now() - interval '90 days'");
    mvc.perform(get(CUALQUIERA).header("Authorization", "Bearer " + accessToken(login())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TIPO_RETENIDA));

    caducar("now() + interval '2 days'");
    mvc.perform(get(CUALQUIERA).header("Authorization", "Bearer " + accessToken(login())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TIPO_RETENIDA));
  }

  @Test
  @DisplayName("`CA-SP-480` — con la caducidad NULA se navega, aunque `must_change_password` esté")
  void sinCaducidadSeNavega() throws Exception {
    jdbc.update(
        """
        UPDATE users SET must_change_password = true, provisional_password_expires_at = NULL
         WHERE id = ?
        """,
        persona);

    mvc.perform(get(CUALQUIERA).header("Authorization", "Bearer " + accessToken(login())))
        .andExpect(status().isOk());
  }

  // ---------------------------------------------------------------------------
  // Las dos puertas que quedan abiertas
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`RF-SP-039` `T-05` — el perfil propio responde, y el indicador llega activo")
  void elPerfilPropioSigueAlcanzable() throws Exception {
    marcar();
    String token = accessToken(login());

    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true));
  }

  @Test
  @DisplayName("`RF-SP-037` `T-06` — la cuenta TIENE salida: cambia la contraseña y queda libre")
  void laCuentaTieneSalida() throws Exception {
    marcar();
    String token = accessToken(login());

    // Retenida...
    mvc.perform(get(CUALQUIERA).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());

    // ...pero la salida está abierta.
    mvc.perform(
            post("/api/v1/auth/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(CLAVE, NUEVA)))
        .andExpect(status().isNoContent());

    // `RF-SP-037` pone la caducidad a nula, de modo que el token SIGUIENTE ya
    // no retiene. El anterior sí lo haría durante quince minutos, y por eso ese
    // requerimiento revoca todas las sesiones: no queda con qué renovarlo.
    assertThat(
            jdbc.queryForObject(
                "SELECT provisional_password_expires_at FROM users WHERE id = ?",
                Object.class,
                persona))
        .isNull();

    mvc.perform(
            get(CUALQUIERA)
                .header("Authorization", "Bearer " + accessToken(login("JPerez", NUEVA))))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("con la marca puesta se puede CERRAR la sesión, aun adjuntando el token")
  void elCierreDeSesionNoQuedaRetenido() throws Exception {
    marcar();
    String cuerpo = respuesta(login());
    String acceso = campo(cuerpo, "accessToken");
    String refresco = campo(cuerpo, "refreshToken");

    // El cliente que guarda el token lo adjunta en TODA petición, incluida
    // esta. Sin la excepción, quien tiene la marca no podría cerrar su sesión.
    mvc.perform(
            post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + acceso)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\",\"allSessions\":false}".formatted(refresco)))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("`CA-SP-482` — el refresco recalcula la marca y no la arrastra del token anterior")
  void elRefrescoRecalcula() throws Exception {
    marcar();
    String refresco = campo(respuesta(login()), "refreshToken");

    // Se levanta la marca en la base con la sesión ya abierta.
    jdbc.update("UPDATE users SET provisional_password_expires_at = NULL WHERE id = ?", persona);

    String renovado =
        campo(
            respuesta(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"%s\"}".formatted(refresco))),
            "accessToken");

    mvc.perform(get(CUALQUIERA).header("Authorization", "Bearer " + renovado))
        .andExpect(status().isOk());
  }

  // ---------------------------------------------------------------------------
  // La lista de excepciones no se pudre
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("cada excepción del filtro corresponde a un endpoint real, o está declarada futura")
  void laListaDeExcepcionesNoConservaFantasmas() {
    // Las dos de `RF-SP-040` se escribieron por adelantado a propósito, con el
    // mismo criterio que la política de límite de tasa que les corresponde. Se
    // exceptúan aquí, y esta entrada es lo que obligará a retirarlas de la
    // excepción el día que el requerimiento exista.
    Set<String> declaradasFuturas =
        Set.of(
            "POST /api/v1/auth/password-recovery",
            "POST /api/v1/auth/password-recovery/confirmation");

    Set<String> existentes = new TreeSet<>();
    rutas.getHandlerMethods().keySet().forEach(info -> existentes.addAll(firmasDe(info)));

    Set<String> fantasmas =
        new TreeSet<>(MustChangePasswordFilter.ALCANZABLE_CON_LA_MARCA.keySet());
    fantasmas.removeAll(existentes);
    fantasmas.removeAll(declaradasFuturas);

    assertThat(fantasmas)
        .as(
            "el filtro exceptúa rutas que ya no existen: el día que alguien reutilice una,"
                + " nacerá alcanzable con la marca puesta y con un motivo escrito para otra cosa")
        .isEmpty();
  }

  // ---------------------------------------------------------------------------

  private void marcar() {
    caducar("now() + interval '7 days'");
  }

  /**
   * Pone la caducidad de la credencial provisional, que es lo único que decide la marca.
   *
   * <p>La expresión va interpolada y no parametrizada porque es SQL —{@code now() - interval '90
   * days'}— y no un valor. Es una prueba, la cadena la escribe este archivo, y calcular el instante
   * en Java lo dejaría a merced de la diferencia entre el reloj de la JVM y el del contenedor.
   */
  private void caducar(String expresion) {
    jdbc.update(
        "UPDATE users SET must_change_password = true, provisional_password_expires_at = "
            + expresion
            + " WHERE id = ?",
        persona);
  }

  private MockHttpServletRequestBuilder login() {
    return login("JPerez", CLAVE);
  }

  private MockHttpServletRequestBuilder login(String identificador, String clave) {
    return post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identificador, clave));
  }

  private String accessToken(MockHttpServletRequestBuilder peticion) throws Exception {
    return campo(respuesta(peticion), "accessToken");
  }

  private String respuesta(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getContentAsString();
  }

  private String campo(String cuerpo, String nombre) throws Exception {
    JsonNode arbol = json.readTree(cuerpo);
    return arbol.get(nombre).asText();
  }

  /**
   * Todas las combinaciones de método y ruta de un mapeo, como {@code "GET /api/v1/roles"}.
   *
   * <p>La misma forma que usa {@code EndpointPermissionsIT}, y a propósito: las dos clases comparan
   * una lista de excepciones declaradas contra los endpoints que existen de verdad.
   */
  private static List<String> firmasDe(RequestMappingInfo info) {
    var metodos = info.getMethodsCondition().getMethods();
    var patrones = info.getPathPatternsCondition();

    List<String> caminos =
        patrones == null ? List.of() : patrones.getPatternValues().stream().sorted().toList();

    if (metodos.isEmpty()) {
      return caminos.stream().map(ruta -> "ANY " + ruta).toList();
    }
    return metodos.stream()
        .flatMap(metodo -> caminos.stream().map(ruta -> metodo.name() + " " + ruta))
        .toList();
  }
}
