package com.factech.nexus.modules.system.users.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.dao.DataIntegrityViolationException;
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
    // FREE SOBREVIVE AL BARRIDO desde el 05-09-2026: `RN-SP-018` da nivel a toda
    // persona y el alta lo resuelve por código, de modo que un catálogo vacío ya
    // no es un estado del que el sistema pueda salir. Borrarla aquí probaría algo
    // que `RN-SP-008` no deja ocurrir: la membresía sembrada no se elimina.
    // BARRIDO TOTAL Y REPOSICIÓN, en ese orden: conservar FREE haría depender esta
    // clase del ORDEN DE EJECUCIÓN — según quién haya corrido antes, la fila queda
    // colgando de VIP (`V47`) o suelta, y el barrido choca con `fk_memberships_parent`.
    jdbc.update("DELETE FROM memberships");
    reponerElSuelo(jdbc);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        SUPERADMIN,
        SUPERADMIN_ROL);

    persona = crearPersona("jperez");
    consumidor = crearRolConsumidor();
    // CUELGAN DE FREE, que sobrevive al barrido desde el 05-09-2026:
    // `uq_memberships_parent` va con NULLS NOT DISTINCT y solo admite UN suelo en
    // toda la cadena, de modo que ya no se puede crear otra sin padre.
    String suelo =
        jdbc.queryForObject("SELECT id::text FROM memberships WHERE code = 'FREE'", String.class);
    oro = crearMembresia("ORO", "Oro", 2, suelo);
    plata = crearMembresia("PLATA", "Plata", 3, oro);
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
        // ORO cuelga de FREE, que sobrevive al barrido: por eso su nivel es 2.
        .andExpect(jsonPath("$.level").value(2))
        // Presente y en nulo: «indefinida» tiene que distinguirse de «este
        // endpoint no informa de la vigencia».
        .andExpect(jsonPath("$.endsAt").value(org.hamcrest.Matchers.nullValue()));

    assertThat(membresiaDe(persona)).isEqualTo(oro);
  }

  @Test
  @DisplayName("la sustitución CIERRA la anterior y abre una nueva: RN-SP-014 desde V56")
  void sustitucion() throws Exception {
    hacerConsumidor(persona);
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isOk());

    mvc.perform(fijar(persona, plata, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("PLATA"));

    // DOS FILAS, no una: la tabla es un historial desde `V56`. Hasta el
    // 05-09-2026 esta prueba exigía UNA, porque asignar sustituía con un UPDATE
    // y el nivel anterior desaparecía.
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_memberships WHERE user_id = ?", Integer.class, persona);
    assertThat(filas).isEqualTo(2);

    // Y UNA SOLA ABIERTA, que es lo que `RN-SP-014` dice ahora y lo que sostiene
    // `uq_user_memberships_abierta`.
    Integer abiertas =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_memberships WHERE user_id = ? AND closed_at IS NULL",
            Integer.class,
            persona);
    assertThat(abiertas).isEqualTo(1);
    assertThat(membresiaDe(persona)).isEqualTo(plata);

    // LO QUE ESTE CAMBIO EXISTE PARA CONSEGUIR: el nivel anterior se puede leer.
    String cerrada =
        jdbc.queryForObject(
            "SELECT membership_id::text FROM user_memberships"
                + " WHERE user_id = ? AND closed_at IS NOT NULL",
            String.class,
            persona);
    assertThat(cerrada).isEqualTo(oro);
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
  @DisplayName("EX-001 retirado — asignar a quien NO es consumidor ya no se rechaza")
  void sinRolDeConsumidorSeAsignaIgual() throws Exception {
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        persona,
        ADMIN_ROL);

    // Hasta el 05-09-2026 esto era un `409` con código `RN-SP-013`. La regla se
    // retiró porque se contradice con la que la sustituye: `RN-SP-018` da nivel a
    // TODA persona, y el superadministrador tiene `FREE` sin ser consumidor.
    mvc.perform(fijar(persona, oro, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("ORO"));

    assertThat(membresiaDe(persona)).isEqualTo(oro);
  }

  @Test
  @DisplayName("la membresía inexistente sigue siendo 422, que era el otro de los dos rechazos")
  void ordenDeLosDosRechazos() throws Exception {
    // Esta prueba medía CUÁL de los dos rechazos ganaba. Retirado `RN-SP-013`
    // solo queda uno, y lo que sigue valiendo es que un identificador
    // equivocado es un `422` y no un `409`.
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
  @DisplayName("dos asignaciones simultáneas dejan UNA fila ABIERTA y ninguna produce 500")
  void asignacionConcurrente() {
    hacerConsumidor(persona);

    // QUÉ LAS SERIALIZA, DESDE EL 05-09-2026: el bloqueo sobre la fila de la
    // persona, no un `ON CONFLICT`. Aquel se apoyaba en que `user_id` fuera la
    // clave primaria y desapareció con `V56`. Sin el bloqueo, las dos leerían la
    // misma fila abierta, las dos la cerrarían y las dos insertarían — y el
    // 23505 de `uq_user_memberships_abierta` saldría como 500.
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

    // UNA SOLA ABIERTA. El total ya no es 1 —la primera queda cerrada en el
    // historial—, y contarlo sería volver a exigir lo que `V56` deshizo.
    Integer abiertas =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_memberships WHERE user_id = ? AND closed_at IS NULL",
            Integer.class,
            persona);
    assertThat(abiertas).isEqualTo(1);
    // El resultado es una de las dos, nunca una mezcla.
    assertThat(membresiaDe(persona)).isIn(oro, plata);
  }

  // ---------------------------------------------------------------------------
  // Devolver al suelo — era «retirar» hasta el 05-09-2026
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-281 — devuelve al suelo y responde 200 con la membresía FREE")
  void devuelveAlSuelo() throws Exception {
    hacerConsumidor(persona);
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isOk());

    // 200 Y NO 204: devolver un cuerpo vacío diría que no queda nada, y queda el
    // nivel de arranque. Quien llama necesita saber en qué quedó la persona.
    mvc.perform(retirar(persona))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("FREE"));

    assertThat(codigoDe(persona)).isEqualTo("FREE");

    // La membresía sigue existiendo en la cadena: se cerró la asignación, no la
    // membresía.
    Integer enLaCadena =
        jdbc.queryForObject(
            "SELECT count(*) FROM memberships WHERE id = ?::uuid", Integer.class, oro);
    assertThat(enLaCadena).isEqualTo(1);
  }

  @Test
  @DisplayName("EX-001 retirado — YA NO rechaza a quien es consumidor")
  void aceptaAlConsumidor() throws Exception {
    hacerConsumidor(persona);
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isOk());

    // Hasta el 05-09-2026 esto era un `409`: la persona portaba rol de consumidor
    // y `RN-SP-018` no admitía consumidores sin nivel. Ahora no queda sin nivel —
    // queda en el suelo—, de modo que la precondición no protegía nada.
    mvc.perform(retirar(persona))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("FREE"));

    assertThat(codigoDe(persona)).isEqualTo("FREE");
  }

  @Test
  @DisplayName("es idempotente: sobre quien ya está en el suelo no escribe ni audita")
  void devolverAlSueloEsIdempotente() throws Exception {
    // La persona nace en FREE, de modo que esta es la primera invocación y ya no
    // hay nada que cambiar. Antes esto era `FA-001` —sin membresía previa— y ese
    // estado dejó de existir.
    UUID correlacion = UUID.randomUUID();
    mvc.perform(retirar(persona).header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("FREE"));

    Integer eliminaciones =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_deletion_log WHERE correlation_id = ?",
            Integer.class,
            correlacion);
    assertThat(eliminaciones).isZero();

    Integer abiertas =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_memberships WHERE user_id = ? AND closed_at IS NULL",
            Integer.class,
            persona);
    assertThat(abiertas).isEqualTo(1);
  }

  @Test
  @DisplayName("se audita como eliminación de ASOCIACIÓN, sin motivo y con la vigencia")
  void auditoriaDelRetiro() throws Exception {
    hacerConsumidor(persona);
    String futuro = OffsetDateTime.now(ZoneOffset.UTC).plusYears(1).toString();
    mvc.perform(fijar(persona, oro, futuro)).andExpect(status().isOk());

    UUID correlacion = UUID.randomUUID();
    mvc.perform(retirar(persona).header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isOk());

    var fila =
        jdbc.queryForMap(
            """
            SELECT deletion_type, reason, snapshot::text AS snapshot
              FROM audit_deletion_log WHERE correlation_id = ?
            """,
            correlacion);
    assertThat(fila.get("deletion_type")).isEqualTo("ASSOCIATION");
    assertThat(fila.get("reason")).isNull();
    // Sin la fecha no se podría distinguir si se devolvió al suelo desde una
    // membresía viva o desde una ya vencida.
    assertThat((String) fila.get("snapshot")).contains("ORO").contains("ends_at");
  }

  @Test
  @DisplayName("se devuelve al suelo desde una membresía VENCIDA sin particularidad")
  void membresiaVencida() throws Exception {
    hacerConsumidor(persona);
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isOk());
    // `ck_user_memberships_periodo` exige `ends_at > started_at`: para dejarla
    // vencida hay que retrasar también el inicio, no solo el fin.
    jdbc.update(
        """
        UPDATE user_memberships
           SET started_at = now() - interval '3 days', ends_at = now() - interval '1 day'
         WHERE user_id = ? AND closed_at IS NULL
        """,
        persona);

    mvc.perform(retirar(persona)).andExpect(status().isOk());
    assertThat(codigoDe(persona)).isEqualTo("FREE");
  }

  @Test
  @DisplayName("EX-002 — la persona inexistente devuelve 404")
  void retiroSobrePersonaInexistente() throws Exception {
    mvc.perform(retirar(UUID.randomUUID())).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("RN-SP-015 retirada — el retiro de ROLES ya no arrastra la membresía")
  void elRetiroDeRolesYaNoArrastraLaMembresia() throws Exception {
    hacerConsumidor(persona);
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isOk());

    // Rol de reserva: desde `RN-SP-023` nadie puede quedarse sin ningún rol.
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid ON CONFLICT DO NOTHING",
        persona,
        "01a02a33-4c00-7007-9c4f-5e7ad1000005");

    mvc.perform(
            post("/api/v1/users/{id}/roles/revocations", persona)
                .with(rolesActor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[\"" + consumidor + "\"]}"))
        .andExpect(status().isOk());

    // CONSERVA `ORO`. Esta prueba existía para comprobar la cascada que hacía
    // innecesario que esta operación admitiera al consumidor; retirada
    // `RN-SP-015`, comprueba justo lo contrario, que es lo que hay que fijar.
    assertThat(membresiaDe(persona)).isEqualTo(oro);
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
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, 'ACTIVO', (SELECT id FROM countries WHERE code = 'COL'))
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

  private String codigoDe(UUID usuario) {
    return jdbc.queryForObject(
        "SELECT m.code FROM user_memberships um JOIN memberships m ON m.id = um.membership_id"
            + " WHERE um.user_id = ? AND um.closed_at IS NULL",
        String.class,
        usuario);
  }

  private String membresiaDe(UUID usuario) {
    List<String> filas =
        jdbc.queryForList(
            "SELECT membership_id::text FROM user_memberships WHERE user_id = ? AND closed_at IS NULL",
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

  // ---------------------------------------------------------------------------
  // Lo que sostiene `RN-SP-014` desde `V56`, ejercitado contra el motor
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("uq_user_memberships_abierta rechaza una SEGUNDA fila abierta")
  void dosAbiertasNoCaben() throws Exception {
    hacerConsumidor(persona);
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isOk());

    // Por la puerta de atrás, saltándose el caso de uso: es la única forma de
    // comprobar que la regla la sostiene EL MOTOR y no el código que la respeta.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO user_memberships (id, user_id, membership_id, started_at)"
                        + " VALUES (gen_random_uuid(), ?, ?::uuid, now())",
                    persona,
                    plata))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("ex_user_memberships_sin_solape rechaza dos periodos que se pisan")
  void periodosSolapadosNoCaben() throws Exception {
    hacerConsumidor(persona);
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isOk());

    // La abierta empieza hace diez días y sigue viva.
    jdbc.update(
        "UPDATE user_memberships SET started_at = now() - interval '10 days' WHERE user_id = ?",
        persona);

    // Una fila CERRADA cuyo periodo cae dentro del de la abierta. El único
    // parcial no la vería —está cerrada— y aun así no puede existir: dos niveles
    // a la vez en los mismos días no describen nada real.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO user_memberships
                           (id, user_id, membership_id, started_at, closed_at)
                    VALUES (gen_random_uuid(), ?, ?::uuid,
                            now() - interval '5 days', now() - interval '2 days')
                    """,
                    persona,
                    plata))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("dos periodos CONSECUTIVOS sí caben: el fin de uno es el inicio del otro")
  void periodosConsecutivosSiCaben() throws Exception {
    hacerConsumidor(persona);
    mvc.perform(fijar(persona, oro, null)).andExpect(status().isOk());
    jdbc.update(
        """
        UPDATE user_memberships
           SET started_at = now() - interval '10 days', closed_at = now() - interval '5 days'
         WHERE user_id = ?
        """,
        persona);

    // Empieza exactamente donde terminó la anterior. `tstzrange` es `[)`, de modo
    // que no se pisan — y si el rango fuera cerrado, cada sustitución fallaría.
    jdbc.update(
        """
        INSERT INTO user_memberships (id, user_id, membership_id, started_at)
        VALUES (gen_random_uuid(), ?, ?::uuid, now() - interval '5 days')
        """,
        persona,
        plata);

    assertThat(membresiaDe(persona)).isEqualTo(plata);
  }
}
