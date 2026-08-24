package com.factech.nexus.modules.system.users.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.ConcurrencyHarness;
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
 * Estructura comercial (`RF-SP-041`, `RF-SP-042`).
 *
 * <p>Las dos operaciones van juntas porque la consulta es la única forma de comprobar lo que la
 * reasignación <b>no</b> hace: `CA-SP-447` exige que el total del equipo coincida con el número que
 * informa el rechazo de `RN-SP-022`, y ese número solo se puede contrastar leyendo el equipo.
 *
 * <p>Cadena sembrada por `V7`: {@code MANAGER → DIRECTOR → AGENTE}, con {@code MANAGER} como
 * cúspide comercial porque su rol padre es {@code ADMIN}, que no es vendedor.
 */
@AutoConfigureMockMvc
class CommercialStructureIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String CONTABILIDAD = "01a02a33-4c00-7003-9c4f-5e7ad1000003";
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000005";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000006";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000007";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  /** Cadena de tres niveles: {@code jefe} ← {@code medio} ← {@code base}. */
  private UUID jefe;

  private UUID medio;
  private UUID base;
  private UUID otroJefe;

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
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)",
        SUPERADMIN,
        SUPERADMIN_ROL);

    jefe = crearPersona("rlopez", MANAGER);
    otroJefe = crearPersona("mgomez", MANAGER);
    medio = crearPersona("amartinez", DIRECTOR);
    base = crearPersona("lgarcia", AGENTE);

    reportar(medio, jefe);
    reportar(base, medio);
  }

  // ---------------------------------------------------------------------------
  // Reasignar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-412 — la reasignación devuelve el superior nuevo Y el anterior con su cierre")
  void reasignacionValida() throws Exception {
    mvc.perform(reasignar(medio, otroJefe, "Reorganización de la zona norte"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.username").value("amartinez"))
        .andExpect(jsonPath("$.supervisor.username").value("mgomez"))
        // Devolver el anterior no es adorno: es lo que permite confirmar de un
        // vistazo que se cerró el tramo que se creía cerrar.
        .andExpect(jsonPath("$.previousSupervisor.username").value("rlopez"))
        .andExpect(jsonPath("$.previousSupervisorEndedAt").isNotEmpty());
  }

  @Test
  @DisplayName("T-02 — el cambio CIERRA un tramo y ABRE otro: quedan DOS filas, no una modificada")
  void elHistorialNoSeSobrescribe() throws Exception {
    mvc.perform(reasignar(medio, otroJefe, "Cambio de zona")).andExpect(status().isOk());

    // Sobrescribir destruiría la historia de las comisiones ya liquidadas.
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_supervisors WHERE user_id = ?", Integer.class, medio);
    assertThat(total).isEqualTo(2);

    Integer vigentes =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_supervisors WHERE user_id = ? AND ended_at IS NULL",
            Integer.class,
            medio);
    assertThat(vigentes).isEqualTo(1);
  }

  @Test
  @DisplayName("T-07 — EL EQUIPO SE MUEVE CON EL REASIGNADO: sus subordinados le siguen apuntando")
  void elEquipoSeMueveConElReasignado() throws Exception {
    // Una implementación que arrastrara a los subordinados al superior nuevo
    // reorganizaría la empresa entera con cada cambio, y pasaría todas las demás
    // pruebas de este archivo.
    mvc.perform(reasignar(medio, otroJefe, "Cambio de zona")).andExpect(status().isOk());

    String superiorDeBase =
        jdbc.queryForObject(
            "SELECT supervisor_id::text FROM user_supervisors WHERE user_id = ? AND ended_at IS NULL",
            String.class,
            base);
    assertThat(superiorDeBase).isEqualTo(medio.toString());
  }

  @Test
  @DisplayName("FA-001 — el mismo superior no cierra ni abre nada y NO deja auditoría")
  void mismoSuperior() throws Exception {
    UUID correlacion = UUID.randomUUID();
    mvc.perform(
            reasignar(medio, jefe, "Confirmación")
                .header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isOk());

    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_supervisors WHERE user_id = ?", Integer.class, medio);
    assertThat(filas).isEqualTo(1);

    // Partir el historial en dos tramos idénticos con una fecha de corte
    // inventada describiría un cambio que nadie hizo.
    Integer eventos =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE correlation_id = ?",
            Integer.class,
            correlacion);
    assertThat(eventos).isZero();
  }

  @Test
  @DisplayName("EX-007 — el motivo se exige IGUAL cuando no habrá cambio")
  void elMotivoSeExigeAntesDeSaberSiHayCambio() throws Exception {
    // Exigirlo solo cuando resulte haber cambio obligaría a validar en dos
    // momentos distintos según el estado previo.
    mvc.perform(reasignar(medio, jefe, "   "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-008"));
  }

  @Test
  @DisplayName("CA-SP-428 — la reasignación NO deja evento de seguridad: no toca ningún permiso")
  void sinEventoDeSeguridad() throws Exception {
    UUID correlacion = UUID.randomUUID();
    mvc.perform(
            reasignar(medio, otroJefe, "Cambio de zona")
                .header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isOk());

    Integer seguridad =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_security_log WHERE correlation_id = ?",
            Integer.class,
            correlacion);
    assertThat(seguridad).isZero();

    // Un solo evento de cambio, aunque la operación cierre y abra: dos harían
    // que cualquier recuento de reasignaciones contase el doble.
    Integer cambios =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE correlation_id = ?",
            Integer.class,
            correlacion);
    assertThat(cambios).isEqualTo(1);
  }

  @Test
  @DisplayName("T-10 — la operación no revoca ninguna sesión de ninguno de los tres implicados")
  void noTocaSesiones() throws Exception {
    for (UUID quien : List.of(medio, jefe, otroJefe)) {
      jdbc.update(
          """
          INSERT INTO refresh_tokens (id, user_id, token_hash, family_id, family_started_at, expires_at)
          VALUES (gen_random_uuid(), ?, ?, gen_random_uuid(), now(), now() + interval '7 days')
          """,
          quien,
          "hash-" + quien);
    }

    mvc.perform(reasignar(medio, otroJefe, "Cambio de zona")).andExpect(status().isOk());

    Integer vivas =
        jdbc.queryForObject(
            "SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class);
    assertThat(vivas).isEqualTo(3);
  }

  // ---------------------------------------------------------------------------
  // Reasignar — rechazos
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("EX-003 — el 409 dice QUÉ ROL debería portar el superior")
  void superiorSinElRolExigido() throws Exception {
    UUID contable = crearPersona("contable", CONTABILIDAD);

    // Sin ese dato, quien recibe el error no sabe a quién buscar.
    mvc.perform(reasignar(medio, contable, "Cambio"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-020"))
        .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("MANAGER")));
  }

  @Test
  @DisplayName("EX-001 y EX-002 — sin rol comercial, y la cúspide")
  void sinRolComercialYCuspide() throws Exception {
    UUID contable = crearPersona("contable", CONTABILIDAD);

    mvc.perform(reasignar(contable, jefe, "Cambio"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));

    mvc.perform(reasignar(jefe, otroJefe, "Cambio"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));
  }

  @Test
  @DisplayName("EX-004 — el superior inactivo se rechaza")
  void superiorInactivo() throws Exception {
    jdbc.update("UPDATE users SET status = 'INACTIVO' WHERE id = ?", otroJefe);

    mvc.perform(reasignar(medio, otroJefe, "Cambio"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-006"));
  }

  @Test
  @DisplayName("EX-005 — nadie a su propio cargo, y el 409 lo explica en lugar de reventar")
  void aSuPropioCargo() throws Exception {
    // El esquema también lo impide con `ck_user_supervisors_no_self`, pero un
    // 409 explica y un 500 no.
    mvc.perform(reasignar(medio, medio, "Cambio"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-007"));
  }

  @Test
  @DisplayName("RN-SP-017 — el actor no se reasigna a sí mismo")
  void sobreSuPropiaCuenta() throws Exception {
    // Si el afectado y el actor son la misma persona, el registro no documenta
    // una decisión: documenta una preferencia.
    mvc.perform(
            patch("/api/v1/users/{id}/supervisor", medio)
                .with(user(medio.toString()).authorities(() -> "users:assign-supervisor"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supervisorId\":\"" + otroJefe + "\",\"reason\":\"Cambio\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-017"));
  }

  @Test
  @DisplayName("EX-006 — cualquiera de las dos personas inexistente devuelve 404")
  void personasInexistentes() throws Exception {
    mvc.perform(reasignar(UUID.randomUUID(), jefe, "Cambio")).andExpect(status().isNotFound());
    mvc.perform(reasignar(medio, UUID.randomUUID(), "Cambio")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("dos reasignaciones simultáneas se serializan: nunca dos vigentes, nunca 500")
  void reasignacionConcurrente() {
    // Sin el bloqueo sobre el subordinado, la unicidad parcial
    // `uq_user_supervisors_vigente` hace fallar a la segunda con 23505.
    UUID tercero = crearPersona("tjefe", MANAGER);
    List<UUID> destinos = List.of(otroJefe, tercero);

    List<ConcurrencyHarness.Outcome<Integer>> resultados =
        ConcurrencyHarness.runTogether(
            2,
            indice ->
                mvc.perform(reasignar(medio, destinos.get(indice), "Cambio " + indice))
                    .andReturn()
                    .getResponse()
                    .getStatus());

    assertThat(resultados).allMatch(ConcurrencyHarness.Outcome::succeeded);
    assertThat(resultados).allMatch(salida -> salida.value() == 200);

    Integer vigentes =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_supervisors WHERE user_id = ? AND ended_at IS NULL",
            Integer.class,
            medio);
    assertThat(vigentes).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // Consultar el equipo
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-442 — devuelve el superior inmediato y el equipo directo")
  void equipoDirecto() throws Exception {
    mvc.perform(equipo(medio))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.username").value("amartinez"))
        .andExpect(jsonPath("$.user.roleCode").value("DIRECTOR"))
        .andExpect(jsonPath("$.supervisor.username").value("rlopez"))
        .andExpect(jsonPath("$.supervisor.since").isNotEmpty())
        .andExpect(jsonPath("$.team.content[0].username").value("lgarcia"))
        .andExpect(jsonPath("$.team.totalElements").value(1));
  }

  @Test
  @DisplayName("CA-SP-449 — UN SOLO NIVEL: el equipo del jefe no incluye al nieto")
  void unSoloNivel() throws Exception {
    // Devolver la rama completa publicaría de una vez la estructura de la
    // empresa por un permiso de lectura de usuarios, y adelantaría D-22.
    mvc.perform(equipo(jefe))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.team.totalElements").value(1))
        .andExpect(jsonPath("$.team.content[0].username").value("amartinez"))
        .andExpect(
            jsonPath("$.team.content[?(@.username == 'lgarcia')]", org.hamcrest.Matchers.empty()));
  }

  @Test
  @DisplayName("CA-SP-445 — la cúspide OMITE el superior: ausente, no en nulo")
  void laCuspideOmiteElSuperior() throws Exception {
    // Es lo que distingue «no depende de nadie» de «no se pudo resolver».
    mvc.perform(equipo(jefe))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.supervisor").doesNotExist());
  }

  @Test
  @DisplayName("FA-001 — quien no pertenece a la fuerza comercial recibe 200, no 404 ni 409")
  void sinEstructuraComercial() throws Exception {
    UUID contable = crearPersona("contable", CONTABILIDAD);

    mvc.perform(equipo(contable))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.roleCode").doesNotExist())
        .andExpect(jsonPath("$.supervisor").doesNotExist())
        .andExpect(jsonPath("$.team.totalElements").value(0));
  }

  @Test
  @DisplayName("CA-SP-450 — sin historial de superiores: solo el vigente")
  void sinHistorial() throws Exception {
    mvc.perform(reasignar(medio, otroJefe, "Cambio")).andExpect(status().isOk());

    String cuerpo = mvc.perform(equipo(medio)).andReturn().getResponse().getContentAsString();

    assertThat(cuerpo).contains("mgomez").doesNotContain("rlopez");
  }

  @Test
  @DisplayName("CA-SP-447 — el total del equipo es EL MISMO que informa el rechazo de RN-SP-022")
  void elTotalCoincideConElDelRechazo() throws Exception {
    String cuerpo = mvc.perform(equipo(medio)).andReturn().getResponse().getContentAsString();
    assertThat(cuerpo).contains("\"totalElements\":1");

    // Retirarle el rol comercial debe rechazarse citando ese mismo número.
    mvc.perform(
            post("/api/v1/users/{id}/roles/revocations", medio)
                .with(user(SUPERADMIN.toString()).authorities(() -> "users:assign-roles"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[\"" + DIRECTOR + "\"]}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-022"))
        .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("1")));
  }

  @Test
  @DisplayName("el subordinado INACTIVO aparece y cuenta; el ELIMINADO no aparece")
  void inactivoYEliminado() throws Exception {
    UUID otro = crearPersona("pruiz", AGENTE);
    reportar(otro, medio);

    jdbc.update("UPDATE users SET status = 'INACTIVO' WHERE id = ?", otro);
    mvc.perform(equipo(medio))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.team.totalElements").value(2));

    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", otro);
    mvc.perform(equipo(medio))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.team.totalElements").value(1));
  }

  @Test
  @DisplayName("VAL-003 — un tamaño de página por encima del máximo se RECHAZA, no se recorta")
  void paginacionFueraDeLimites() throws Exception {
    // Recortarla en silencio haría que quien pide doscientos reciba cien y crea
    // que solo hay cien.
    mvc.perform(get("/api/v1/users/{id}/team", medio).with(lector()).param("size", "500"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));

    mvc.perform(get("/api/v1/users/{id}/team", medio).with(lector()).param("page", "-1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("CA-SP-455 — no admite filtros: un parámetro de filtro no cambia el resultado")
  void sinFiltros() throws Exception {
    mvc.perform(
            get("/api/v1/users/{id}/team", medio)
                .with(lector())
                .param("search", "no-existe")
                .param("status", "INACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.team.totalElements").value(1));
  }

  @Test
  @DisplayName("EX-001 — la persona inexistente devuelve 404, y el identificador malformado 400")
  void consultaSobrePersonaInexistente() throws Exception {
    mvc.perform(get("/api/v1/users/{id}/team", UUID.randomUUID()).with(lector()))
        .andExpect(status().isNotFound());

    mvc.perform(get("/api/v1/users/{id}/team", "no-es-un-uuid").with(lector()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("la respuesta NO es un perfil: sin correo y sin fechas de la persona")
  void noEsUnPerfil() throws Exception {
    // La restricción impide que este endpoint se convierta en un listado de
    // usuarios con otro permiso: `RF-SP-025` ya existe para eso.
    String cuerpo = mvc.perform(equipo(medio)).andReturn().getResponse().getContentAsString();

    assertThat(cuerpo).doesNotContain("@factech.co").doesNotContain("createdAt");
  }

  // ---------------------------------------------------------------------------
  // Auxiliares
  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder reasignar(UUID subordinado, UUID superior, String motivo) {
    return patch("/api/v1/users/{id}/supervisor", subordinado)
        .with(asignador())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"supervisorId\":\"" + superior + "\",\"reason\":\"" + motivo + "\"}");
  }

  private MockHttpServletRequestBuilder equipo(UUID usuario) {
    return get("/api/v1/users/{id}/team", usuario).with(lector());
  }

  private RequestPostProcessor asignador() {
    return user(SUPERADMIN.toString()).authorities(() -> "users:assign-supervisor");
  }

  private RequestPostProcessor lector() {
    return user(SUPERADMIN.toString()).authorities(() -> "users:read");
  }

  private UUID crearPersona(String username, String rol) {
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
    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", id, rol);
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
}
