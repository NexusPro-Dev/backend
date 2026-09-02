package com.factech.nexus.modules.system.users.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.shared.security.SessionRevoker;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Si la revocación de sesiones falla, <b>el retiro de roles falla con ella</b> (`RF-SP-031`).
 *
 * <h2>Qué se está protegiendo</h2>
 *
 * <p>Retirar un rol revoca las sesiones de la persona, y no por pulcritud: el token de acceso
 * <b>transporta los códigos de rol</b>, de modo que sin revocación quien acaba de perder un permiso
 * lo sigue ejerciendo hasta quince minutos. Esa ventana se abre justo en el momento en que alguien
 * decidió que dejara de poder hacer algo.
 *
 * <p>De ahí la decisión de `plan.md` §154: la revocación va <b>dentro</b> de la transacción, y no
 * después del commit. La diferencia es qué pasa cuando falla. Fuera, el retiro quedaría confirmado
 * y las sesiones vivas — la peor combinación posible, porque la respuesta diría `200` y el acceso
 * seguiría abierto. Dentro, el fallo arrastra al retiro: la persona conserva el rol, quien opera
 * recibe un error y puede reintentar, y en ningún instante hay alguien ejerciendo un rol que el
 * sistema cree haberle quitado.
 *
 * <h2>Por qué no se puede comprobar sin doblar el revocador</h2>
 *
 * <p>Es un fallo que no se provoca desde fuera: no hay entrada que haga fallar a
 * `RefreshTokenSessionRevoker`. Se sustituye por un doble que revienta, que es la única forma de
 * ejercitar la rama, y por eso esta clase tiene su propio contexto.
 */
@AutoConfigureMockMvc
class RevokeUserRolesSessionFailureIT extends IntegrationTestBase {

  private static final String ADMIN_ROL = "01a02a33-4c00-7002-9c4f-5e7ad1000002";

  /**
   * La persona lleva DOS roles y solo se le retira uno.
   *
   * <p>`RN-SP-023` impide dejar a nadie sin ninguno —para eso está desactivar la cuenta—, de modo
   * que con un solo rol la petición se rechazaría con `409` antes de llegar a la revocación de
   * sesiones, que es lo que esta clase quiere ejercitar.
   */
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private SessionRevoker sesiones;

  private UUID persona;

  @BeforeEach
  void preparar() {
    limpiar();

    persona = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status)
        VALUES (?, 'MRevoca', 'mrevoca@factech.co', 'Marta', 'Revoca', 'x', false, 'ACTIVO')
        """,
        persona);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        persona,
        ADMIN_ROL);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        persona,
        AGENTE);
  }

  @AfterEach
  void limpiarDespues() {
    limpiar();
  }

  @Test
  @DisplayName("si no se pueden revocar las sesiones, el rol NO se retira")
  void elRetiroSeDeshace() throws Exception {
    when(sesiones.revokeAllForAccessChange(any()))
        .thenThrow(new IllegalStateException("el almacén de sesiones no responde"));

    mvc.perform(retirar()).andExpect(status().is5xxServerError());

    /*
     * Lo que de verdad se comprueba. Si la revocación viviera fuera de la
     * transacción, aquí habría CERO: el retiro confirmado y las sesiones vivas.
     */
    assertThat(rolesDe(persona))
        .as("el retiro debe deshacerse entero cuando la revocación falla")
        .isEqualTo(2);
  }

  @Test
  @DisplayName("y con la revocación en pie, el retiro sí se aplica")
  void elCaminoNormalSigueFuncionando() throws Exception {
    // Sin esta pareja, la anterior pasaría igual si el endpoint estuviera roto
    // por cualquier otro motivo.
    when(sesiones.revokeAllForAccessChange(any())).thenReturn(0);

    mvc.perform(retirar()).andExpect(status().isOk());

    assertThat(rolesDe(persona)).isEqualTo(1);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder retirar() {
    return post("/api/v1/users/{id}/roles/revocations", persona)
        .with(administrador())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"roleIds\":[\"" + ADMIN_ROL + "\"]}");
  }

  private RequestPostProcessor administrador() {
    return user(SUPERADMIN.toString())
        .authorities(() -> "users:assign-roles", () -> "users:read", () -> "roles:read");
  }

  private int rolesDe(UUID usuario) {
    Integer cuantos =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_roles WHERE user_id = ?", Integer.class, usuario);
    return cuantos == null ? 0 : cuantos;
  }

  private void limpiar() {
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM user_roles WHERE user_id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
  }
}
