package com.factech.nexus.modules.system.users.interfaces;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * `RF-SP-059` — los vendedores de un cliente, por las dos rutas (`CA-SP-700` a `CA-SP-705`).
 *
 * <p><b>La fila {@code HOTLINK} se inserta a mano</b>, y no por la API: nadie la escribe todavía
 * —`RF-MV-011` y `RF-MV-013` no existen—. Sin ella no se podría probar ni el orden ni que {@code
 * principal} va en falso, y el contrato ya es el definitivo.
 *
 * <p><b>El cliente NO está en {@code user_supervisors}</b>, a propósito y en todos los casos: es lo
 * que la decisión del 18-09-2026 fija (`RN-SP-028` revertida), y una prueba que lo colgara allí
 * probaría un estado que `V20` borró.
 */
@AutoConfigureMockMvc
class ClientSellersIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";
  private static final String CLIENTE = "01a02a33-4c00-7008-9c4f-5e7ad1000008";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID director;
  private UUID agente;
  private UUID otroAgente;
  private UUID cliente;
  private UUID clienteSinVendedor;

  @BeforeEach
  void preparar() {
    limpiar();
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        SUPERADMIN,
        SUPERADMIN_ROL);

    director = crearPersona("amartinez", "Ana", "Martínez", DIRECTOR);
    agente = crearPersona("lgarcia", "Lucía", "García", AGENTE);
    otroAgente = crearPersona("projas", "Pedro", "Rojas", AGENTE);
    reportar(agente, director);
    reportar(otroAgente, director);

    // Registrada por `lgarcia` hace una semana; le compró a `projas` ayer por su
    // hotlink. El orden de inserción es el INVERSO al esperado en la respuesta,
    // para que el orden salga de la consulta y no de la casualidad.
    cliente = crearPersona("cperez", "Carla", "Pérez", CLIENTE);
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin, created_at)"
            + " VALUES (?, ?, 'HOTLINK', now() - interval '1 day')",
        cliente,
        otroAgente);
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin, created_at)"
            + " VALUES (?, ?, 'REGISTRO', now() - interval '7 days')",
        cliente,
        agente);

    // Dado de alta por un funcionario: nadie lo registró por enlace (`FA-001`).
    clienteSinVendedor = crearPersona("sperez", "Sara", "Pérez", CLIENTE);
  }

  /**
   * Deja {@code client_sellers} <b>vacía</b>: referencia a {@code users}, y una fila que sobreviva
   * hace fallar el {@code DELETE FROM users} de la clase que se ejecute después — señalando a la
   * clase equivocada. Lo demás se deja como lo dejan las otras suites de personas.
   */
  @AfterEach
  void vaciar() {
    jdbc.update("DELETE FROM client_sellers");
    jdbc.update("DELETE FROM user_supervisors");
  }

  // ---------------------------------------------------------------------------
  // `GET /users/me/sellers`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-SP-700`, `CA-SP-701` — el cliente ve a sus vendedores, principal primero y sin permiso")
  void elClienteVeASusVendedores() throws Exception {
    mvc.perform(get("/api/v1/users/me/sellers").with(comoPersona(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        // El principal PRIMERO aunque su vínculo sea el más antiguo y se
        // insertara el último: el orden es `REGISTRO` y después la fecha.
        .andExpect(jsonPath("$.content[0].username").value("lgarcia"))
        .andExpect(jsonPath("$.content[0].firstName").value("Lucía"))
        .andExpect(jsonPath("$.content[0].lastName").value("García"))
        .andExpect(jsonPath("$.content[0].origin").value("REGISTRO"))
        .andExpect(jsonPath("$.content[0].principal").value(true))
        .andExpect(jsonPath("$.content[0].linkedAt").isNotEmpty())
        .andExpect(jsonPath("$.content[1].username").value("projas"))
        .andExpect(jsonPath("$.content[1].origin").value("HOTLINK"))
        .andExpect(jsonPath("$.content[1].principal").value(false))
        // `CA-SP-701`: lo que NO viaja. Ni identificador, ni correo, ni estado,
        // ni roles — lo mismo que publica el hotlink (`RN-PM-022`).
        .andExpect(jsonPath("$.content[0].id").doesNotExist())
        .andExpect(jsonPath("$.content[0].email").doesNotExist())
        .andExpect(jsonPath("$.content[0].status").doesNotExist())
        .andExpect(jsonPath("$.content[0].roles").doesNotExist());
  }

  @Test
  @DisplayName("`CA-SP-703` — un cliente sin vendedor recibe 200 con la colección vacía")
  void sinVendedorNoEsUnError() throws Exception {
    mvc.perform(get("/api/v1/users/me/sellers").with(comoPersona(clienteSinVendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  @DisplayName("`CA-SP-704` — un vendedor que pregunta por los suyos recibe 200 vacío")
  void unVendedorNoTieneVendedores() throws Exception {
    // `lgarcia` tiene superior en `user_supervisors`; eso no es «su vendedor»
    // (`FA-002`): la estructura de mando no se publica por esta vía.
    mvc.perform(get("/api/v1/users/me/sellers").with(comoPersona(agente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  @DisplayName("sin token, 401")
  void sinSesion() throws Exception {
    mvc.perform(get("/api/v1/users/me/sellers")).andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------
  // `GET /users/{id}/sellers`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-SP-705` — con `users:read-sellers` se ven los de cualquiera; sin él 403; inexistente 404")
  void administracionVeLosDeCualquiera() throws Exception {
    mvc.perform(get("/api/v1/users/" + cliente + "/sellers").with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].username").value("lgarcia"))
        .andExpect(jsonPath("$.content[0].principal").value(true));

    // El vendedor `REGISTRO` NO puede mirar por esta ruta a su propio cliente
    // sin el permiso: la ruta por identificador es `users:read-sellers`, no
    // estructura. Y `users:read` —el detalle de la persona— tampoco la abre
    // desde el 21-09-2026 (RN-SEG-014): un permiso, una operación.
    mvc.perform(
            get("/api/v1/users/" + cliente + "/sellers")
                .with(user(SUPERADMIN.toString()).authorities(() -> "users:read")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/users/" + cliente + "/sellers").with(comoPersona(agente)))
        .andExpect(status().isForbidden());

    mvc.perform(get("/api/v1/users/" + UUID.randomUUID() + "/sellers").with(lector()))
        .andExpect(status().isNotFound());

    // Eliminada lógicamente: el mismo 404 que si no existiera.
    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", clienteSinVendedor);
    mvc.perform(get("/api/v1/users/" + clienteSinVendedor + "/sellers").with(lector()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("`FA-005` — un vendedor desactivado sigue saliendo: el vínculo es un hecho")
  void elVendedorRetiradoSigueSaliendo() throws Exception {
    jdbc.update("UPDATE users SET status = 'INACTIVO' WHERE id = ?", agente);

    mvc.perform(get("/api/v1/users/me/sellers").with(comoPersona(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].username").value("lgarcia"));
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  private static RequestPostProcessor comoPersona(UUID persona) {
    return user(persona.toString());
  }

  private static RequestPostProcessor lector() {
    return user(SUPERADMIN.toString()).authorities(() -> "users:read-sellers");
  }

  private UUID crearPersona(String username, String nombre, String apellido, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, ?, ?, 'x', false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co",
        nombre,
        apellido);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        id,
        rol);
    return id;
  }

  private void reportar(UUID subordinado, UUID superior) {
    jdbc.update(
        """
        INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at)
        VALUES (gen_random_uuid(), ?, ?, now())
        """,
        subordinado,
        superior);
  }

  private void limpiar() {
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM client_sellers");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM user_roles");
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
  }
}
