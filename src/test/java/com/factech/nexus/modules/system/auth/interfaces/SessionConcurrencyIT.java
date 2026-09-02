package com.factech.nexus.modules.system.auth.interfaces;

import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.shared.security.PasswordHasher;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Concurrencia de sesión (`RF-SP-035`).
 *
 * <h2>El invariante que se protege</h2>
 *
 * <p>El refresco <b>rota</b> el token: el que se presenta queda revocado y nace uno nuevo en la
 * misma familia. De ahí sale la detección de robo — presentar un token ya rotado significa que
 * alguien tiene una copia, y cierra la familia entera—. Todo eso descansa en una propiedad: de una
 * familia solo hay <b>una cadena viva</b>.
 *
 * <p>Dos refrescos simultáneos con el mismo token son justo lo que puede romperla. Sin serializar,
 * los dos leen la fila sin revocar, los dos la rotan, y quedan <b>dos cadenas vivas</b> de la misma
 * sesión: dos personas con tokens válidos que se renuevan solos, y la detección de reutilización
 * apagada para las dos. `plan.md` §2 lo resuelve con {@code SELECT … FOR UPDATE} sobre la fila, y
 * §11 pedía esta prueba porque <b>el bloqueo sin ella es una intención</b>: quien lo sustituya por
 * una lectura corriente no rompe nada visible.
 *
 * <h2>Qué se afirma y qué no</h2>
 *
 * <p>No se afirma cuál de los dos refrescos gana. Se afirma lo que debe ser cierto en todos los
 * desenlaces: <b>exactamente uno</b> obtiene `200`, el otro recibe `401`, y en la base de datos no
 * queda más de un token vivo de esa familia. La segunda prueba lleva el invariante a su forma más
 * dura: tras la carrera, la familia entera está cerrada, porque el segundo intento se lee como una
 * <b>reutilización</b>.
 */
@AutoConfigureMockMvc
class SessionConcurrencyIT extends IntegrationTestBase {

  private static final String ADMIN_ROL = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final String CLAVE = "ClaveLargaYSegura2026";

  /**
   * La carrera se repite: la ventana entre leer la fila y revocarla es estrecha, y una sola ronda
   * podría no acertarla y pasar en verde con la garantía rota.
   */
  private static final int RONDAS = 6;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private PasswordHasher hasher;

  private UUID persona;

  @BeforeEach
  void prepararCuenta() {
    limpiar();

    persona = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status)
        VALUES (?, 'JCarrera', 'carrera@factech.co', 'Juana', 'Carrera', ?, false, 'ACTIVO')
        """,
        persona,
        hasher.hash(CLAVE));
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        persona,
        ADMIN_ROL);
  }

  @AfterEach
  void noDejarSesionesDetras() {
    limpiar();
  }

  @Test
  @DisplayName("dos refrescos a la vez con el MISMO token: uno gana y uno solo")
  void refrescoConcurrente() throws Exception {
    for (int ronda = 0; ronda < RONDAS; ronda++) {
      String token = refreshToken(login());

      List<Outcome<Integer>> resultados = dosRefrescos(token);
      List<Integer> estados = resultados.stream().map(Outcome::value).toList();

      assertThat(resultados)
          .as("un rechazo es una respuesta, no una excepción")
          .allMatch(Outcome::succeeded);

      assertThat(estados)
          .as("ronda %s: uno rota la sesión y el otro se rechaza", ronda)
          .containsExactlyInAnyOrder(200, 401);

      // El invariante de verdad. Sin `FOR UPDATE` quedarían DOS.
      assertThat(vivos())
          .as("ronda %s: quedaron varias cadenas vivas de la misma sesión", ronda)
          .isLessThanOrEqualTo(1);

      limpiar();
      prepararCuenta();
    }
  }

  @Test
  @DisplayName(
      "el que pierde no es un error cualquiera: se lee como REUTILIZACIÓN y cierra la familia")
  void elPerdedorCierraLaFamilia() throws Exception {
    /*
     * Es la consecuencia que hace útil a la rotación, y la que se perdería en
     * silencio si la carrera no se serializara: presentar un token ya rotado
     * significa que existe una copia, y lo que corresponde no es rechazar esa
     * petición sino **cerrar la sesión entera**.
     */
    String token = refreshToken(login());

    dosRefrescos(token);

    assertThat(vivos()).as("tras la carrera no debe quedar ninguna cadena viva").isZero();
    assertThat(eventosDeReutilizacion())
        .as("la reutilización debe dejar su evento de seguridad")
        .isPositive();
  }

  /**
   * Los dos refrescos, soltados a la vez.
   *
   * <p>Se devuelve el estado HTTP en lugar de aseverar dentro del hilo: quien aserta es quien lee
   * los resultados, y así un fallo se lee como una comparación y no como una excepción perdida.
   */
  private List<Outcome<Integer>> dosRefrescos(String token) {
    Callable<Integer> refrescar =
        () -> mvc.perform(refresh(token)).andReturn().getResponse().getStatus();
    return runTogether(List.of(refrescar, refrescar));
  }

  /** Cuántas cadenas de esta persona siguen autenticando. */
  private int vivos() {
    Integer cuantos =
        jdbc.queryForObject(
            "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
            Integer.class,
            persona);
    return cuantos == null ? 0 : cuantos;
  }

  private int eventosDeReutilizacion() {
    Integer cuantos =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_security_log WHERE event_type = 'REFRESH_TOKEN_REUSE'",
            Integer.class);
    return cuantos == null ? 0 : cuantos;
  }

  private MockHttpServletRequestBuilder login() {
    return post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"identifier\":\"JCarrera\",\"password\":\"%s\"}".formatted(CLAVE));
  }

  private MockHttpServletRequestBuilder refresh(String token) {
    return post("/api/v1/auth/refresh")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"refreshToken\":\"%s\"}".formatted(token));
  }

  private String refreshToken(MockHttpServletRequestBuilder peticion) throws Exception {
    String cuerpo = mvc.perform(peticion).andReturn().getResponse().getContentAsString();
    return json.readTree(cuerpo).get("refreshToken").asText();
  }

  private void limpiar() {
    // `refresh_tokens` cuelga de `users`: se barre primero, o la limpieza muere
    // con una violación de integridad que no tiene que ver con nada de esto.
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_roles WHERE user_id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
  }
}
