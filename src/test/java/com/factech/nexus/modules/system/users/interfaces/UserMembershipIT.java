package com.factech.nexus.modules.system.users.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.ConcurrencyHarness;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Membresía de una persona (`RF-SP-032`, `RF-SP-033`).
 *
 * <p>Las dos operaciones van juntas porque **`RF-SP-033` rechaza exactamente a quien `RF-SP-032`
 * admite**, y esa oposición es lo único que hay que probar de verdad: invertir una de las dos
 * condiciones produce un rechazo que parece un defecto del sistema y un permiso que no debería
 * concederse, y ninguna de las dos cosas falla en la otra mitad.
 */
@AutoConfigureMockMvc
class UserMembershipIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final String ADMIN_ROL = "01a02a33-4c00-7002-9c4f-5e7ad1000002";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID persona;
  private String consumidor;
  private String oro;
  private String plata;

  @BeforeEach
  void preparar() {
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM user_roles");
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
    jdbc.update(
        "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE is_system = false)");
    jdbc.update("DELETE FROM roles WHERE is_system = false");
    jdbc.update("DELETE FROM memberships WHERE level > 0");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        SUPERADMIN,
        SUPERADMIN_ROL);

    persona = crearPersona("jperez");
    consumidor = crearRolConsumidor();
    // La cadena es lineal y con una sola cima: `uq_memberships_parent` con NULLS
    // NOT DISTINCT solo admite una membresía sin superior.
    oro = crearMembresia("ORO", "Oro", 1, null);
    plata = crearMembresia("PLATA", "Plata", 2, oro);
  }

  // ---------------------------------------------------------------------------
  // Fijar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-272 — la primera membresía devuelve 200 y no 201: PUT no crea una ruta nueva")
  void primeraMembresia() throws Exception {
    hacerConsumidor(persona);

    mvc.perform(fijar(persona, oro, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("ORO"))
        .andExpect(jsonPath("$.level").value(1))
        // Presente y en nulo: «indefinida» tiene que distinguirse de «este
        // endpoint no informa de la vigencia».
        .andExpect(jsonPath("$.endsAt").value(org.hamcrest.Matchers.nullValue()));

    assertThat(membresiaDe(persona)).isEqualTo(oro);
  }

  @Test
  @DisplayName("la sustitución deja UNA fila: RN-SP-014, una membresía por persona")
  void sustitucion() throws Exception {
    hacerConsumidor(persona);
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isOk());

    mvc.perform(fijar(persona, plata, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("PLATA"));

    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_memberships WHERE user_id = ?", Integer.class, persona);
    assertThat(filas).isEqualTo(1);
    assertThat(membresiaDe(persona)).isEqualTo(plata);
  }

  @Test
  @DisplayName("FA-002 — repetir la petición idéntica no escribe ni audita")
  void sinCambio() throws Exception {
    hacerConsumidor(persona);
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isOk());
    int antes = eventosDeCambio(persona);

    mvc.perform(fijar(persona, oro, null)).andExpect(status().isOk());

    // Una interfaz que reenvía el formulario al guardar dejaría, sin esto, una
    // fila de auditoría por cada pulsación describiendo un cambio que no ocurrió.
    assertThat(eventosDeCambio(persona)).isEqualTo(antes);
  }

  @Test
  @DisplayName("FA-003 — cambiar SOLO la fecha sí es un cambio y sí se audita")
  void renovacion() throws Exception {
    hacerConsumidor(persona);
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isOk());
    int antes = eventosDeCambio(persona);

    String futuro = OffsetDateTime.now(ZoneOffset.UTC).plusYears(1).toString();
    mvc.perform(fijar(persona, oro, futuro))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.endsAt").isNotEmpty());

    assertThat(eventosDeCambio(persona)).isEqualTo(antes + 1);
  }

  @Test
  @DisplayName("enviar endsAt ausente sobre una fechada la convierte en INDEFINIDA")
  void deFechadaAIndefinida() throws Exception {
    // Es un caso normal de `FA-003`, no un olvido que haya que interpretar: el
    // cuerpo de un `PUT` representa el estado final.
    hacerConsumidor(persona);
    String futuro = OffsetDateTime.now(ZoneOffset.UTC).plusYears(1).toString();
    mvc.perform(fijar(persona, oro, futuro)).andExpect(status().isOk());

    mvc.perform(fijar(persona, oro, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.endsAt").value(org.hamcrest.Matchers.nullValue()));

    Object hasta =
        jdbc.queryForObject(
            "SELECT ends_at FROM user_memberships WHERE user_id = ?", Object.class, persona);
    assertThat(hasta).isNull();
  }

  @Test
  @DisplayName("el evento de cambio conserva AMBOS niveles: sin ellos no se sabe si fue un ascenso")
  void auditoriaConNiveles() throws Exception {
    hacerConsumidor(persona);
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isOk());

    UUID correlacion = UUID.randomUUID();
    mvc.perform(fijar(persona, plata, null).header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isOk());

    String cambios =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE correlation_id = ?",
            String.class,
            correlacion);
    assertThat(cambios).contains("ORO").contains("PLATA").contains("level");
  }

  @Test
  @DisplayName("la membresía NO deja evento de seguridad: es un dato comercial, no un permiso")
  void sinEventoDeSeguridad() throws Exception {
    hacerConsumidor(persona);
    UUID correlacion = UUID.randomUUID();

    mvc.perform(fijar(persona, oro, null).header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isOk());

    // Emitirlo diluiría el registro que existe para investigar accesos, y
    // `RF-SP-014` acabaría devolviendo ruido entre los incidentes.
    Integer seguridad =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_security_log WHERE correlation_id = ?",
            Integer.class,
            correlacion);
    assertThat(seguridad).isZero();
  }

  // ---------------------------------------------------------------------------
  // Fijar — los tres rechazos, en sus tres categorías
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("EX-004 es 400: la fecha pasada se decide mirando solo el cuerpo y el reloj")
  void fechaPasada() throws Exception {
    hacerConsumidor(persona);
    String pasado = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toString();

    mvc.perform(fijar(persona, oro, pasado))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));
  }

  @Test
  @DisplayName("EX-003 es 404: la persona inexistente o eliminada")
  void personaInexistente() throws Exception {
    mvc.perform(fijar(UUID.randomUUID(), oro, null))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("https://nexus.factech.co/errors/no-encontrado"));

    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", persona);
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("EX-002 es 422: una referencia del cuerpo que no resuelve")
  void membresiaInexistente() throws Exception {
    hacerConsumidor(persona);

    mvc.perform(fijar(persona, UUID.randomUUID().toString(), null))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
  }

  @Test
  @DisplayName("EX-001 es 409, y el cuerpo dice cuál es la operación que falta")
  void sinRolDeConsumidor() throws Exception {
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        persona,
        ADMIN_ROL);

    mvc.perform(fijar(persona, oro, null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-013"))
        // Sin esta indicación, quien lo recibe no tiene forma de saber que la
        // operación que le falta es otra.
        .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("roles")));
  }

  @Test
  @DisplayName("la membresía se comprueba ANTES que el rol: es el error más accionable")
  void ordenDeLosDosRechazos() throws Exception {
    // Sin rol de consumidor Y con una membresía inexistente, gana el `422`: un
    // identificador equivocado es un dato de la petición, y la falta de rol es
    // una operación previa pendiente.
    mvc.perform(fijar(persona, UUID.randomUUID().toString(), null))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName("VAL-001 — la membresía es obligatoria")
  void membresiaObligatoria() throws Exception {
    mvc.perform(
            put("/api/v1/users/{id}/membership", persona)
                .with(actor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"membershipId\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.code == 'VAL-001')]").exists());
  }

  @Test
  @DisplayName("dos asignaciones simultáneas dejan UNA fila y ninguna produce 500")
  void asignacionConcurrente() {
    hacerConsumidor(persona);

    // La clave primaria impide dos filas, pero sin `ON CONFLICT` la segunda
    // recibiría 23505 y saldría como 500.
    List<String> destinos = List.of(oro, plata);
    List<ConcurrencyHarness.Outcome<Integer>> resultados =
        ConcurrencyHarness.runTogether(
            2,
            indice ->
                mvc.perform(fijar(persona, destinos.get(indice), null))
                    .andReturn()
                    .getResponse()
                    .getStatus());

    assertThat(resultados).allMatch(ConcurrencyHarness.Outcome::succeeded);
    assertThat(resultados).allMatch(salida -> salida.value() == 200);

    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_memberships WHERE user_id = ?", Integer.class, persona);
    assertThat(filas).isEqualTo(1);
    // El resultado es una de las dos, nunca una mezcla.
    assertThat(membresiaDe(persona)).isIn(oro, plata);
  }

  // ---------------------------------------------------------------------------
  // Retirar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-281 — sin rol de consumidor, la membresía se retira y devuelve 204")
  void retiroValido() throws Exception {
    // El estado que esta operación existe para corregir: alguien con membresía
    // que ya no porta ningún rol de consumidor.
    hacerConsumidor(persona);
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isOk());
    jdbc.update("DELETE FROM user_roles WHERE user_id = ?", persona);

    mvc.perform(retirar(persona)).andExpect(status().isNoContent());

    assertThat(membresiaDe(persona)).isNull();
    // La membresía sigue existiendo en la cadena: se retiró la asignación, no la
    // membresía.
    Integer enLaCadena =
        jdbc.queryForObject(
            "SELECT count(*) FROM memberships WHERE id = ?::uuid", Integer.class, oro);
    assertThat(enLaCadena).isEqualTo(1);
  }

  @Test
  @DisplayName("EX-001 — RECHAZA a quien SÍ es consumidor, y cita LAS DOS salidas reales")
  void rechazaAlConsumidor() throws Exception {
    hacerConsumidor(persona);
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isOk());

    mvc.perform(retirar(persona))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-018"))
        .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("nivel")))
        .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("rol")));

    assertThat(membresiaDe(persona)).isEqualTo(oro);
  }

  @Test
  @DisplayName("FA-001 — sin membresía previa devuelve 204 igual, sin escribir ni auditar")
  void retiroIdempotente() throws Exception {
    UUID correlacion = UUID.randomUUID();
    mvc.perform(retirar(persona).header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isNoContent());

    // Un evento de eliminación que no eliminó nada es un dato falso.
    Integer eliminaciones =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_deletion_log WHERE correlation_id = ?",
            Integer.class,
            correlacion);
    assertThat(eliminaciones).isZero();
  }

  @Test
  @DisplayName("el retiro se audita como eliminación de ASOCIACIÓN, sin motivo y con la vigencia")
  void auditoriaDelRetiro() throws Exception {
    hacerConsumidor(persona);
    String futuro = OffsetDateTime.now(ZoneOffset.UTC).plusYears(1).toString();
    mvc.perform(fijar(persona, oro, futuro)).andExpect(status().isOk());
    jdbc.update("DELETE FROM user_roles WHERE user_id = ?", persona);

    UUID correlacion = UUID.randomUUID();
    mvc.perform(retirar(persona).header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isNoContent());

    var fila =
        jdbc.queryForMap(
            """
            SELECT deletion_type, reason, snapshot::text AS snapshot
              FROM audit_deletion_log WHERE correlation_id = ?
            """,
            correlacion);
    assertThat(fila.get("deletion_type")).isEqualTo("ASSOCIATION");
    assertThat(fila.get("reason")).isNull();
    // Sin la fecha no se podría distinguir si se retiró una membresía viva o
    // una ya vencida, que es lo que alguien querría saber.
    assertThat((String) fila.get("snapshot")).contains("ORO").contains("ends_at");
  }

  @Test
  @DisplayName("se retira una membresía VENCIDA sin particularidad")
  void membresiaVencida() throws Exception {
    hacerConsumidor(persona);
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isOk());
    // `ck_user_memberships_periodo` exige `ends_at > started_at`: para dejarla
    // vencida hay que retrasar también el inicio, no solo el fin.
    jdbc.update(
        """
        UPDATE user_memberships
           SET started_at = now() - interval '3 days', ends_at = now() - interval '1 day'
         WHERE user_id = ?
        """,
        persona);
    jdbc.update("DELETE FROM user_roles WHERE user_id = ?", persona);

    mvc.perform(retirar(persona)).andExpect(status().isNoContent());
    assertThat(membresiaDe(persona)).isNull();
  }

  @Test
  @DisplayName("EX-002 — la persona inexistente devuelve 404")
  void retiroSobrePersonaInexistente() throws Exception {
    mvc.perform(retirar(UUID.randomUUID())).andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------------------
  // La oposición entre las dos operaciones
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("las dos condiciones son OPUESTAS: lo que una admite, la otra rechaza")
  void lasDosDirecciones() throws Exception {
    // Ambas mitades en la misma prueba. Invertir la condición en cualquiera de
    // los dos servicios tiene que hacer fallar esto, y ejecutar solo una mitad
    // no probaría nada.
    hacerConsumidor(persona);
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isOk());
    mvc.perform(retirar(persona)).andExpect(status().isConflict());

    jdbc.update("DELETE FROM user_roles WHERE user_id = ?", persona);
    mvc.perform(retirar(persona)).andExpect(status().isNoContent());
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("el retiro de ROLES arrastra la membresía por su cuenta, sin pasar por aquí")
  void laCascadaDeRolesNoNecesitaEstaOperacion() throws Exception {
    hacerConsumidor(persona);
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isOk());

    // Rol de reserva: desde `RN-SP-023` (24-08-2026) nadie puede quedarse sin
    // ningún rol, y sin él el retiro chocaría con esa regla en lugar de disparar
    // la cascada de `RN-SP-015`, que es lo que esta prueba mide.
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid ON CONFLICT DO NOTHING",
        persona,
        "01a02a33-4c00-7007-9c4f-5e7ad1000005");

    // Es la salida que el `409` de arriba cita: `RF-SP-031` retira la membresía
    // en cascada, y por eso no hace falta que esta operación admita al consumidor.
    mvc.perform(
            post("/api/v1/users/{id}/roles/revocations", persona)
                .with(rolesActor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[\"" + consumidor + "\"]}"))
        .andExpect(status().isOk());

    assertThat(membresiaDe(persona)).isNull();
  }

  // ---------------------------------------------------------------------------
  // Auxiliares
  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder fijar(UUID usuario, String membresia, String hasta) {
    String cuerpo =
        "{\"membershipId\":\""
            + membresia
            + "\",\"endsAt\":"
            + (hasta == null ? "null" : "\"" + hasta + "\"")
            + "}";
    return put("/api/v1/users/{id}/membership", usuario)
        .with(actor())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private MockHttpServletRequestBuilder retirar(UUID usuario) {
    return delete("/api/v1/users/{id}/membership", usuario).with(actor());
  }

  private RequestPostProcessor actor() {
    return user(SUPERADMIN.toString()).authorities(() -> "users:assign-membership");
  }

  private RequestPostProcessor rolesActor() {
    return user(SUPERADMIN.toString()).authorities(() -> "users:assign-roles");
  }

  private void hacerConsumidor(UUID usuario) {
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid ON CONFLICT DO NOTHING",
        usuario,
        consumidor);
  }

  private UUID crearPersona(String username) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, 'ACTIVO')
        """,
        id,
        username,
        username + "@factech.co");
    return id;
  }

  private String crearRolConsumidor() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO roles (id, code, name, role_type, parent_role_id)
        VALUES (?, 'ESTUDIANTE', 'Estudiante', 'CONSUMIDOR', ?::uuid)
        """,
        id,
        ADMIN);
    return id.toString();
  }

  private String crearMembresia(String codigo, String nombre, int nivel, String superior) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, parent_membership_id, level, color)"
            + " VALUES (?, ?, ?, ?::uuid, ?, upper(lpad(to_hex(? * 4919), 6, '0')))",
        id,
        codigo,
        nombre,
        superior,
        nivel,
        nivel);
    return id.toString();
  }

  private String membresiaDe(UUID usuario) {
    List<String> filas =
        jdbc.queryForList(
            "SELECT membership_id::text FROM user_memberships WHERE user_id = ?",
            String.class,
            usuario);
    return filas.isEmpty() ? null : filas.get(0);
  }

  private int eventosDeCambio(UUID usuario) {
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE entity_id = ? AND entity = 'user_memberships'",
            Integer.class,
            usuario);
    return total == null ? 0 : total;
  }
}
