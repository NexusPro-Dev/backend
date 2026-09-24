package com.factech.nexus.modules.system.users.interfaces;

import static org.hamcrest.Matchers.hasSize;
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
 * `RF-SP-061` — los clientes de un vendedor, por las dos rutas (`CA-SP-714` a `CA-SP-721`).
 *
 * <p>La cartera de `lgarcia`: tres clientes que registró —uno activo, uno desactivado y uno
 * eliminado— y uno que le compró por hotlink, <b>insertado a mano</b> porque nadie escribe esas
 * filas todavía. Encima de ella un director, que NO debe ver su cartera por serlo; debajo, nadie:
 * un agente subordinado en {@code user_supervisors} no es un cliente, y la prueba lo afirma con
 * uno.
 *
 * <p><b>Las fechas de vínculo se insertan en orden inverso al esperado</b>, para que el orden salga
 * de la consulta y no de la casualidad.
 */
@AutoConfigureMockMvc
class SellerClientsIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";
  private static final String CLIENTE = "01a02a33-4c00-7008-9c4f-5e7ad1000008";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID director;
  private UUID agente;
  private UUID otroAgente;
  private UUID subordinado;
  private UUID clienteReciente;
  private UUID clienteAntiguo;
  private UUID clienteDesactivado;
  private UUID clienteEliminado;
  private UUID clienteVinculado;

  @BeforeEach
  void preparar() {
    limpiar();
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        SUPERADMIN,
        SUPERADMIN_ROL);

    director = crearPersona("amartinez", "Ana", "Martínez", DIRECTOR, "ACTIVO");
    agente = crearPersona("lgarcia", "Lucía", "García", AGENTE, "ACTIVO");
    otroAgente = crearPersona("projas", "Pedro", "Rojas", AGENTE, "ACTIVO");
    subordinado = crearPersona("jsoto", "Julián", "Soto", AGENTE, "ACTIVO");
    reportar(agente, director);
    reportar(subordinado, agente);

    // La cartera de `lgarcia`, del vínculo más antiguo al más reciente:
    //   hace 30 días registró a `bruiz` (hoy desactivada),
    //   hace 20 días a `eliminado` (hoy eliminado),
    //   hace 10 días a `mlozano` (ACTIVO),
    //   hace 2 días `cperez`, registrada por `projas`, le compró por su hotlink,
    //   ayer registró a `nvega` (FTD_PENDIENTE, aún no depositó).
    clienteDesactivado = crearPersona("bruiz", "Beatriz", "Ruiz", CLIENTE, "INACTIVO");
    clienteEliminado = crearPersona("eliminado", "Elena", "Mora", CLIENTE, "ACTIVO");
    clienteAntiguo = crearPersona("mlozano", "Marta", "Lozano", CLIENTE, "ACTIVO");
    clienteVinculado = crearPersona("cperez", "Carla", "Pérez", CLIENTE, "ACTIVO");
    clienteReciente = crearPersona("nvega", "Nora", "Vega", CLIENTE, "FTD_PENDIENTE");

    vincular(clienteReciente, agente, "REGISTRO", "1 day");
    vincular(clienteVinculado, otroAgente, "REGISTRO", "3 days");
    vincular(clienteVinculado, agente, "HOTLINK", "2 days");
    vincular(clienteAntiguo, agente, "REGISTRO", "10 days");
    vincular(clienteEliminado, agente, "REGISTRO", "20 days");
    vincular(clienteDesactivado, agente, "REGISTRO", "30 days");
    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", clienteEliminado);
  }

  @AfterEach
  void vaciar() {
    jdbc.update("DELETE FROM client_sellers");
    jdbc.update("DELETE FROM user_supervisors");
  }

  // ---------------------------------------------------------------------------
  // `GET /users/me/clients`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-SP-714` — el vendedor ve su cartera sin permiso, los más recientes primero, con id,"
          + " estado, origen, principal y fecha")
  void elVendedorVeSuCartera() throws Exception {
    mvc.perform(get("/api/v1/users/me/clients").with(comoPersona(agente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.totalIsExact").value(true))
        .andExpect(jsonPath("$.content", hasSize(4)))
        .andExpect(jsonPath("$.content[0].id").value(clienteReciente.toString()))
        .andExpect(jsonPath("$.content[0].username").value("nvega"))
        .andExpect(jsonPath("$.content[0].firstName").value("Nora"))
        .andExpect(jsonPath("$.content[0].lastName").value("Vega"))
        .andExpect(jsonPath("$.content[0].status").value("FTD_PENDIENTE"))
        .andExpect(jsonPath("$.content[0].origin").value("REGISTRO"))
        .andExpect(jsonPath("$.content[0].principal").value(true))
        .andExpect(jsonPath("$.content[0].linkedAt").isNotEmpty())
        .andExpect(jsonPath("$.content[1].username").value("cperez"))
        .andExpect(jsonPath("$.content[1].origin").value("HOTLINK"))
        .andExpect(jsonPath("$.content[1].principal").value(false))
        .andExpect(jsonPath("$.content[2].username").value("mlozano"))
        .andExpect(jsonPath("$.content[3].username").value("bruiz"))
        // Lo que NO viaja de un cliente: lo mismo que su ficha no da a la cartera.
        .andExpect(jsonPath("$.content[0].email").doesNotExist())
        .andExpect(jsonPath("$.content[0].roles").doesNotExist());
  }

  @Test
  @DisplayName(
      "`CA-SP-715` — origin=REGISTRO da los propios, origin=HOTLINK los vinculados, otro valor es"
          + " 400")
  void elFiltroPorOrigen() throws Exception {
    mvc.perform(get("/api/v1/users/me/clients?origin=registro").with(comoPersona(agente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content[0].username").value("nvega"))
        .andExpect(jsonPath("$.content[1].username").value("mlozano"))
        .andExpect(jsonPath("$.content[2].username").value("bruiz"));

    mvc.perform(get("/api/v1/users/me/clients?origin=HOTLINK").with(comoPersona(agente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].username").value("cperez"))
        .andExpect(jsonPath("$.content[0].principal").value(false));

    mvc.perform(get("/api/v1/users/me/clients?origin=TIENDA").with(comoPersona(agente)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("origin"))
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));
  }

  @Test
  @DisplayName(
      "`CA-SP-716` — se pagina como todo listado: totalElements cuenta la cartera entera y la"
          + " paginación fuera de límites es 400")
  void sePagina() throws Exception {
    mvc.perform(get("/api/v1/users/me/clients?page=1&size=3").with(comoPersona(agente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(3))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].username").value("bruiz"));

    mvc.perform(get("/api/v1/users/me/clients?page=-1").with(comoPersona(agente)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    mvc.perform(get("/api/v1/users/me/clients?size=0").with(comoPersona(agente)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
  }

  @Test
  @DisplayName(
      "`CA-SP-717` — quien no tiene cartera recibe 200 y la página vacía, no 404; un cliente, 403")
  void sinCarteraEsVacio() throws Exception {
    // Un cliente: desde RF-SP-062 (21-09-2026) su rol no porta users:read-own-clients
    // y recibe 403 —el frontend no le ofrece la vista—; hasta entonces, 200 vacío.
    mvc.perform(get("/api/v1/users/me/clients").with(comoCliente(clienteAntiguo)))
        .andExpect(status().isForbidden());
    // Un funcionario sin cartera: 200 con la página vacía.
    mvc.perform(get("/api/v1/users/me/clients").with(comoPersona(SUPERADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.content", hasSize(0)));
    // Un vendedor sin registros.
    mvc.perform(get("/api/v1/users/me/clients").with(comoPersona(subordinado)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    // Y por identificador, lo mismo.
    mvc.perform(get("/api/v1/users/" + subordinado + "/clients").with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName(
      "`CA-SP-719` — el cliente desactivado sigue en la cartera con su estado; el eliminado no sale"
          + " ni se cuenta")
  void elDesactivadoSaleYElEliminadoNo() throws Exception {
    mvc.perform(get("/api/v1/users/me/clients?origin=REGISTRO").with(comoPersona(agente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content[2].username").value("bruiz"))
        .andExpect(jsonPath("$.content[2].status").value("INACTIVO"))
        .andExpect(jsonPath("$.content[?(@.username == 'eliminado')]").doesNotExist());
  }

  @Test
  @DisplayName(
      "`CA-SP-721` — un vendedor subordinado no es un cliente: la lista lee solo client_sellers")
  void elSubordinadoNoEsCliente() throws Exception {
    // `lgarcia` reporta a `amartinez` y `jsoto` reporta a `lgarcia`: nada de eso es cartera.
    mvc.perform(get("/api/v1/users/me/clients").with(comoPersona(director)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get("/api/v1/users/me/clients").with(comoPersona(agente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.username == 'jsoto')]").doesNotExist());
  }

  // ---------------------------------------------------------------------------
  // `GET /users/{id}/clients`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-SP-718` — con users:read-clients se ve la cartera de cualquiera; sin él 403 aunque se"
          + " porten users:read, users:read-team o users:read-sellers; inexistente o eliminado 404")
  void administracionVeLaDeCualquiera() throws Exception {
    mvc.perform(get("/api/v1/users/" + agente + "/clients").with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.content[0].username").value("nvega"))
        .andExpect(jsonPath("$.content[0].id").value(clienteReciente.toString()));

    mvc.perform(get("/api/v1/users/" + otroAgente + "/clients").with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].username").value("cperez"))
        .andExpect(jsonPath("$.content[0].origin").value("REGISTRO"));

    // Un permiso, una operación (RN-SEG-014): ningún vecino abre esta ruta.
    for (String vecino : new String[] {"users:read", "users:read-team", "users:read-sellers"}) {
      mvc.perform(
              get("/api/v1/users/" + agente + "/clients")
                  .with(user(SUPERADMIN.toString()).authorities(() -> vecino)))
          .andExpect(status().isForbidden());
    }

    mvc.perform(get("/api/v1/users/" + UUID.randomUUID() + "/clients").with(lector()))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/users/" + clienteEliminado + "/clients").with(lector()))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/users/no-es-uuid/clients").with(lector()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "`CA-SP-720` — el director no ve la cartera de su agente por serlo: sin el permiso, 403")
  void laEstructuraNoAutoriza() throws Exception {
    mvc.perform(get("/api/v1/users/" + agente + "/clients").with(comoPersona(director)))
        .andExpect(status().isForbidden());
    // Y el permiso lo abre para cualquiera, también para quien está fuera de la rama.
    mvc.perform(
            get("/api/v1/users/" + agente + "/clients")
                .with(user(otroAgente.toString()).authorities(() -> "users:read-clients")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(4));
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  // Desde RF-SP-062 (21-09-2026) `/me/clients` exige `users:read-own-clients`
  // —autenticarse no autoriza nada—, que V31 da a todo rol de vendedor y de
  // funcionario, y a CLIENTE no. Hasta entonces bastaba con `user(id)`.
  private static RequestPostProcessor comoPersona(UUID persona) {
    return user(persona.toString()).authorities(() -> "users:read-own-clients");
  }

  /** Un cliente: porta lo suyo (`users:read-own-sellers`) y no lo de vendedor. */
  private static RequestPostProcessor comoCliente(UUID persona) {
    return user(persona.toString()).authorities(() -> "users:read-own-sellers");
  }

  private static RequestPostProcessor lector() {
    return user(SUPERADMIN.toString()).authorities(() -> "users:read-clients");
  }

  private void vincular(UUID cliente, UUID vendedor, String origen, String hace) {
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin, created_at)"
            + " VALUES (?, ?, ?, now() - CAST(? AS interval))",
        cliente,
        vendedor,
        origen,
        hace);
  }

  private UUID crearPersona(
      String username, String nombre, String apellido, String rol, String estado) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, ?, ?, 'x', false, ?,
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co",
        nombre,
        apellido,
        estado);
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
    jdbc.update("DELETE FROM user_products");
    jdbc.update("DELETE FROM user_roles");
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
  }
}
