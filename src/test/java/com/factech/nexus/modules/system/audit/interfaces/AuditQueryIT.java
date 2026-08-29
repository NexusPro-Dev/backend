package com.factech.nexus.modules.system.audit.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Los cuatro registros de auditoría (`RF-SP-011` a `RF-SP-014`).
 *
 * <p><b>Las filas no se siembran a mano salvo donde hace falta.</b> La mayoría de estas pruebas
 * <b>provoca el evento por la API</b> —crea un rol, lo elimina, provoca un rechazo— y luego lo
 * busca en el registro. Es la única forma de verificar lo que de verdad importa: que lo que el
 * sistema escribe es lo que la consulta devuelve. Con filas inventadas, ambos lados podrían estar
 * de acuerdo entre sí y equivocados respecto de la realidad.
 */
@AutoConfigureMockMvc
class AuditQueryIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  @BeforeEach
  void preparar() {
    limpiar();
  }

  @AfterEach
  void devolverElEstadoCompartidoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // RF-SP-011 — cambios
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-SP-081 y CA-SP-083 — el alta aparece con su estado inicial, la más reciente arriba")
  void altaEnLaAuditoriaDeCambios() throws Exception {
    UUID primero = crearRolPorApi("PRIMERO", "Rol primero");
    UUID segundo = crearRolPorApi("SEGUNDO", "Rol segundo");

    mvc.perform(cambios())
        .andExpect(status().isOk())
        // Del más reciente al más antiguo: el segundo rol encabeza el registro.
        .andExpect(jsonPath("$.content[0].entityId").value(segundo.toString()))
        .andExpect(jsonPath("$.content[0].action").value("CREATE"))
        .andExpect(jsonPath("$.content[0].module").value("SP"))
        .andExpect(jsonPath("$.content[0].entity").value("roles"))
        // En un CREATE, `changes` es el estado inicial completo — no un diff con
        // `before` en nulo.
        .andExpect(jsonPath("$.content[0].changes.code").value("SEGUNDO"))
        .andExpect(jsonPath("$.content[0].changes.status").value("ACTIVO"))
        .andExpect(jsonPath("$.content[1].entityId").value(primero.toString()));
  }

  @Test
  @DisplayName("CA-SP-082 — en una edición, el detalle son SOLO los campos que cambiaron")
  void edicionEnLaAuditoriaDeCambios() throws Exception {
    UUID rol = crearRolPorApi("EDITABLE", "Rol editable");

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/api/v1/roles/{id}", rol)
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Rol renombrado\"}"))
        .andExpect(status().isOk());

    mvc.perform(cambios().param("action", "UPDATE").param("entityId", rol.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].changes.name.before").value("Rol editable"))
        .andExpect(jsonPath("$.content[0].changes.name.after").value("Rol renombrado"))
        // El código no se envió y no cambió: un diff con campos que nadie tocó
        // haría ilegible la línea de tiempo del registro.
        .andExpect(jsonPath("$.content[0].changes.code").doesNotExist());
  }

  @Test
  @DisplayName("CA-SP-084 y CA-SP-085 — filtra por módulo, entidad, registro, actor y correlación")
  void filtrosDeCambios() throws Exception {
    UUID rol = crearRolPorApi("FILTRABLE", "Rol filtrable");

    // Con `entityId` porque los ocho roles de sistema que siembra `V7` también
    // son eventos de `SP`/`roles`: son parte del registro y no deben desaparecer
    // para que una prueba cuadre.
    mvc.perform(
            cambios()
                .param("module", "SP")
                .param("entity", "roles")
                .param("entityId", rol.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1));

    // Un módulo que no existe devuelve la colección vacía, no un error.
    mvc.perform(cambios().param("module", "XX"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(0));

    // Y un identificador de registro que ya no existe sigue devolviendo su
    // historia: es la razón de ser del registro.
    String correlacion =
        jdbc.queryForObject(
            "SELECT correlation_id FROM audit_change_log WHERE entity_id = ?", String.class, rol);

    mvc.perform(cambios().param("correlationId", correlacion))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].entityId").value(rol.toString()));
  }

  @Test
  @DisplayName("CA-SP-086 — un evento sin origen de red devuelve correlación e IP vacías A LA VEZ")
  void eventoSinOrigenDeRed() throws Exception {
    // Lo escribe una migración: `V7__seed_system_roles.sql` siembra los roles de
    // sistema sin petición HTTP detrás.
    mvc.perform(cambios().param("entity", "roles").param("module", "SP"))
        .andExpect(status().isOk());

    sembrarCambioSinOrigen();

    mvc.perform(cambios().param("entity", "migracion"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        // Presentes y nulos, no ausentes: un campo que desaparece es
        // indistinguible de uno que el cliente no conoce.
        // PRESENTES y nulos, no ausentes: un campo que desaparece es
        // indistinguible de uno que el cliente no conoce. La presencia se
        // comprueba sobre el árbol JSON unas líneas más abajo, porque `jsonPath`
        // no distingue «ausente» de «nulo».
        .andExpect(jsonPath("$.content[0].correlationId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.content[0].actorId").value(org.hamcrest.Matchers.nullValue()));

    String cuerpo =
        mvc.perform(cambios().param("entity", "migracion"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode fila = json.readTree(cuerpo).get("content").get(0);
    assertThat(fila.has("correlationId")).isTrue();
    assertThat(fila.get("correlationId").isNull()).isTrue();
    assertThat(fila.get("ipAddress").isNull()).isTrue();
  }

  @Test
  @DisplayName("CA-SP-087 — el alta de un rol no deja credenciales en el registro")
  void sinCredencialesEnElRegistro() throws Exception {
    crearRolPorApi("SIN_SECRETOS", "Rol sin secretos");

    String cuerpo =
        mvc.perform(cambios())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Lo que se comprueba son CREDENCIALES, no la palabra: el alta de una
    // persona registra `must_change_password` en su estado inicial, que es una
    // marca de estado y no un secreto. Lo que no puede aparecer nunca es el
    // resumen de la contraseña, un token o una cabecera de autorización.
    assertThat(cuerpo.toLowerCase())
        .doesNotContain("password_hash")
        .doesNotContain("passwordhash")
        .doesNotContain("token")
        .doesNotContain("authorization");
  }

  // ---------------------------------------------------------------------------
  // RF-SP-012 — eliminaciones
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("la eliminación de un rol aparece con su motivo y su estado conservado")
  void eliminacionEnElRegistro() throws Exception {
    UUID rol = crearRolPorApi("BORRABLE", "Rol borrable");

    mvc.perform(
            post("/api/v1/roles/{id}/deletion", rol)
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Duplicado del rol contable.\"}"))
        .andExpect(status().isNoContent());

    mvc.perform(eliminaciones())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].deletionType").value("LOGICAL"))
        .andExpect(jsonPath("$.content[0].reason").value("Duplicado del rol contable."))
        // Sin el estado conservado, la fila diría que un uuid fue eliminado y
        // nadie recordaría qué era.
        .andExpect(jsonPath("$.content[0].snapshot.code").value("BORRABLE"))
        .andExpect(jsonPath("$.content[0].snapshot.role_type").value("FUNCIONARIO"));
  }

  @Test
  @DisplayName("FA-001 — la eliminación de una asociación va SIN motivo, y es correcto")
  void eliminacionDeAsociacion() throws Exception {
    UUID rol = crearRolPorApi("CON_PERMISOS", "Rol con permisos");
    UUID permiso = permisoDelAdministrador();

    mvc.perform(
            post("/api/v1/roles/{id}/permissions", rol)
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissionIds\":[\"" + permiso + "\"]}"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/roles/{id}/permissions/revocations", rol)
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissionIds\":[\"" + permiso + "\"]}"))
        .andExpect(status().isOk());

    mvc.perform(eliminaciones().param("deletionType", "ASSOCIATION"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        // Nulo y presente: el motivo vacío de una asociación es un dato —«esta
        // eliminación no exigía motivo»— y no un campo que falte.
        .andExpect(jsonPath("$.content[0].reason").value(org.hamcrest.Matchers.nullValue()))
        // Los códigos, legibles sin resolver referencias contra el catálogo.
        .andExpect(jsonPath("$.content[0].snapshot.role_code").value("CON_PERMISOS"));
  }

  @Test
  @DisplayName("el filtro por motivo busca por texto, sin distinguir acentos ni mayúsculas")
  void busquedaPorMotivo() throws Exception {
    UUID rol = crearRolPorApi("MOTIVADO", "Rol motivado");

    mvc.perform(
            post("/api/v1/roles/{id}/deletion", rol)
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Reorganización del área contable.\"}"))
        .andExpect(status().isNoContent());

    // Sin tilde y en otra caja: nadie recuerda cómo se redactó el motivo.
    mvc.perform(eliminaciones().param("reason", "REORGANIZACION"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1));

    mvc.perform(eliminaciones().param("reason", "no-aparece-en-ningun-motivo"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  // ---------------------------------------------------------------------------
  // RF-SP-013 — errores
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("un rechazo por regla de negocio queda registrado con su código y su severidad")
  void rechazoEnLaAuditoriaDeError() throws Exception {
    crearRolPorApi("DUPLICADO", "Rol duplicado");

    // Segundo alta con el mismo código: 409 por `RN-SEG-001`.
    mvc.perform(
            post("/api/v1/roles")
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDeAlta("DUPLICADO", "Otro nombre")))
        .andExpect(status().isConflict());

    mvc.perform(errores())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].errorCode").value("RN-SEG-001"))
        .andExpect(jsonPath("$.content[0].errorType").value("BUSINESS_RULE"))
        .andExpect(jsonPath("$.content[0].httpStatus").value(409))
        .andExpect(jsonPath("$.content[0].severity").value("MEDIA"))
        // El recurso es la RUTA, tal como la registra el manejador global: es lo
        // que permite localizar el fallo sin adivinar a qué entidad pertenecía.
        .andExpect(jsonPath("$.content[0].resource").value("/api/v1/roles"))
        .andExpect(jsonPath("$.content[0].operation").value("POST /api/v1/roles"));
  }

  @Test
  @DisplayName("CA-SP-108 — la denegación de autorización NO está aquí: está en seguridad")
  void laDenegacionNoEsUnError() throws Exception {
    // Un 403 no es un fallo del sistema sino el sistema funcionando.
    mvc.perform(
            get("/api/v1/roles").with(user(SUPERADMIN.toString()).authorities(() -> "nada:nada")))
        .andExpect(status().isForbidden());

    mvc.perform(errores()).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());

    mvc.perform(seguridad().param("eventType", "AUTHORIZATION_DENIED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].outcome").value("FAILURE"));
  }

  // ---------------------------------------------------------------------------
  // RF-SP-014 — seguridad
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-103 y CA-SP-105 — los eventos de privilegio se listan y se filtran")
  void registroDeSeguridad() throws Exception {
    crearRolPorApi("VIGILADO", "Rol vigilado");

    mvc.perform(seguridad().param("eventType", "ROLE_CREATED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].severity").value("ALTA"))
        .andExpect(jsonPath("$.content[0].outcome").value("SUCCESS"))
        .andExpect(jsonPath("$.content[0].detail.roleCode").value("VIGILADO"));

    mvc.perform(seguridad().param("outcome", "FAILURE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @Test
  @DisplayName("CA-SP-167 — cada consulta de este registro deja su propio evento, con los filtros")
  void laConsultaDeSeguridadSeAuditaASiMisma() throws Exception {
    long antes = eventosDeLectura();

    mvc.perform(seguridad().param("outcome", "SUCCESS").param("severity", "ALTA"))
        .andExpect(status().isOk());

    assertThat(eventosDeLectura()).isEqualTo(antes + 1);

    String detalle =
        jdbc.queryForObject(
            "SELECT detail::text FROM audit_security_log WHERE event_type = 'SECURITY_AUDIT_READ'"
                + " ORDER BY occurred_at DESC LIMIT 1",
            String.class);

    // Los filtros usados, y solo los informados: «alguien consultó el registro
    // de seguridad» no dice nada; con qué filtros, sí.
    assertThat(detalle).contains("outcome").contains("ALTA").doesNotContain("targetUserId");
  }

  @Test
  @DisplayName("las otras tres consultas NO se auditan a sí mismas")
  void lasOtrasTresNoDejanEvento() throws Exception {
    long antes = eventosDeLectura();

    mvc.perform(cambios()).andExpect(status().isOk());
    mvc.perform(eliminaciones()).andExpect(status().isOk());
    mvc.perform(errores()).andExpect(status().isOk());

    // Su trazabilidad la aporta el registro de peticiones; un evento por listado
    // sepultaría bajo ruido informativo la búsqueda de eventos reales.
    assertThat(eventosDeLectura()).isEqualTo(antes);
  }

  // ---------------------------------------------------------------------------
  // Común a los cuatro
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-088 y CA-SP-110 — cada registro exige SU permiso, y no vale el de otro")
  void cuatroPermisosDistintos() throws Exception {
    RequestPostProcessor soloErrores =
        user(SUPERADMIN.toString()).authorities(() -> "audit:read-errors");

    mvc.perform(get("/api/v1/audit/changes").with(soloErrores)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/audit/deletions").with(soloErrores)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/audit/security").with(soloErrores)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/audit/errors").with(soloErrores)).andExpect(status().isOk());

    mvc.perform(get("/api/v1/audit/changes")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("VAL-001 — un rango de fechas al revés se rechaza en los cuatro")
  void rangoInvalido() throws Exception {
    String desde = "2026-09-01T00:00:00Z";
    String hasta = "2026-08-01T00:00:00Z";

    for (MockHttpServletRequestBuilder consulta :
        java.util.List.of(cambios(), eliminaciones(), errores(), seguridad())) {
      mvc.perform(consulta.param("from", desde).param("to", hasta))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[?(@.code == 'VAL-001')]").isNotEmpty());
    }
  }

  @Test
  @DisplayName("VAL-003 — un filtro fuera de su dominio se rechaza enumerando los admitidos")
  void dominioInvalido() throws Exception {
    mvc.perform(cambios().param("action", "INVENTADA"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"))
        // La enumeración va en el error del campo y no en el `detail` general:
        // el `detail` describe la petición, y quien corrige mira el campo.
        .andExpect(
            jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("UPDATE")));

    mvc.perform(eliminaciones().param("deletionType", "INVENTADA"))
        .andExpect(status().isBadRequest());
    mvc.perform(errores().param("severity", "INVENTADA")).andExpect(status().isBadRequest());
    mvc.perform(seguridad().param("eventType", "INVENTADO")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("los rechazos se evalúan y se devuelven JUNTOS")
  void rechazosJuntos() throws Exception {
    mvc.perform(
            cambios()
                .param("page", "-1")
                .param("from", "2026-09-01T00:00:00Z")
                .param("to", "2026-08-01T00:00:00Z")
                .param("action", "INVENTADA"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.field == 'page')]").isNotEmpty())
        .andExpect(jsonPath("$.errors[?(@.field == 'from')]").isNotEmpty())
        .andExpect(jsonPath("$.errors[?(@.field == 'action')]").isNotEmpty());
  }

  @Test
  @DisplayName("el orden no se puede cambiar: `sort` no existe y se ignora")
  void sinOrdenamiento() throws Exception {
    crearRolPorApi("UNO", "Rol uno");
    crearRolPorApi("DOS", "Rol dos");

    // Spring ignora en silencio los parámetros que el DTO no declara, de modo
    // que la petición pasa y el orden sigue siendo el cronológico inverso.
    mvc.perform(cambios().param("sort", "module,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].changes.code").value("DOS"));
  }

  @Test
  @DisplayName("el total es exacto mientras no se toque el techo del conteo")
  void totalExacto() throws Exception {
    UUID rol = crearRolPorApi("CONTADO", "Rol contado");

    mvc.perform(cambios().param("entityId", rol.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.totalIsExact").value(true));
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  // ---------------------------------------------------------------------------
  // El actor resuelto (28-08-2026)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-471` — el actor llega resuelto, y el identificador sigue estando")
  void elActorLlegaResuelto() throws Exception {
    crearRolPorApi("PRIMERO", "Rol primero");

    mvc.perform(cambios())
        .andExpect(status().isOk())
        // El identificador NO se va: la adición es aditiva y quien ya lo
        // consumía no se entera del cambio.
        .andExpect(jsonPath("$.content[0].actorId").value(SUPERADMIN.toString()))
        .andExpect(jsonPath("$.content[0].actor.username").value("superadmin"))
        .andExpect(jsonPath("$.content[0].actor.fullName").value("Super Administrador"));
  }

  @Test
  @DisplayName("lo que hizo el SISTEMA trae actorId nulo y actor nulo, y eso lo distingue")
  void elEventoDelSistemaNoTieneActor() throws Exception {
    sembrarCambio(null);

    mvc.perform(cambios())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].actorId").value(org.hamcrest.Matchers.nullValue()))
        // Presente y nulo, no ausente: el campo se declara siempre.
        .andExpect(jsonPath("$.content[0].actor").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("un actor ELIMINADO sigue resolviendo: la auditoría no pierde el quién")
  void elActorEliminadoSigueResolviendo() throws Exception {
    UUID id = sembrarPersona("retirada", "Persona", "Retirada", true);
    sembrarCambio(id);

    mvc.perform(cambios())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].actor.username").value("retirada"))
        .andExpect(jsonPath("$.content[0].actor.fullName").value("Persona Retirada"));
  }

  @Test
  @DisplayName("un actor que ya NO está en la tabla deja el identificador y el actor nulo")
  void elActorInexistenteDejaElIdentificador() throws Exception {
    UUID fantasma = UUID.randomUUID();
    sembrarCambio(fantasma);

    // Las dos ausencias se distinguen sin un campo que lo diga: aquí hay
    // identificador y no hay actor; en el evento del sistema no hay ninguno.
    mvc.perform(cambios())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].actorId").value(fantasma.toString()))
        .andExpect(jsonPath("$.content[0].actor").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName(
      "`CA-SP-472` — en seguridad se resuelven los DOS: quién lo hizo y sobre quién recayó")
  void seguridadResuelveActorYObjetivo() throws Exception {
    UUID afectada = sembrarPersona("bloqueada", "Persona", "Bloqueada", false);
    jdbc.update(
        "INSERT INTO audit_security_log (id, occurred_at, actor_id, event_type, severity,"
            + " outcome, target_user_id) VALUES (CAST(? AS uuid), now(), CAST(? AS uuid),"
            + " 'ACCOUNT_LOCKED', 'ALTA', 'SUCCESS', CAST(? AS uuid))",
        UUID.randomUUID().toString(),
        SUPERADMIN.toString(),
        afectada.toString());

    mvc.perform(seguridad())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].actor.username").value("superadmin"))
        .andExpect(jsonPath("$.content[0].targetUserId").value(afectada.toString()))
        .andExpect(jsonPath("$.content[0].targetUser.username").value("bloqueada"))
        .andExpect(jsonPath("$.content[0].targetUser.fullName").value("Persona Bloqueada"));
  }

  @Test
  @DisplayName("el filtro por actor sigue funcionando con el JOIN puesto")
  void elFiltroPorActorSobreviveAlJoin() throws Exception {
    crearRolPorApi("PRIMERO", "Rol primero");
    UUID otro = UUID.randomUUID();
    sembrarCambio(otro);

    mvc.perform(cambios().param("actorId", otro.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].actorId").value(otro.toString()));
  }

  /** Una fila de cambio con el actor que se le indique. {@code null} es el sistema. */
  private void sembrarCambio(UUID actor) {
    jdbc.update(
        "INSERT INTO audit_change_log (id, occurred_at, actor_id, module, entity, entity_id,"
            + " action, changes) VALUES (CAST(? AS uuid), now(), CAST(? AS uuid), 'SP', 'prueba',"
            + " CAST(? AS uuid), 'CREATE', CAST('{}' AS jsonb))",
        UUID.randomUUID().toString(),
        actor == null ? null : actor.toString(),
        UUID.randomUUID().toString());
  }

  /** Una persona sembrada a mano, opcionalmente ya retirada. */
  private UUID sembrarPersona(String usuario, String nombre, String apellido, boolean retirada) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, username, email, first_name, last_name, password_hash, status,"
            + " deleted_at) VALUES (CAST(? AS uuid), ?, ?, ?, ?, 'x', 'ACTIVO', "
            + (retirada ? "now()" : "NULL")
            + ")",
        id.toString(),
        usuario,
        usuario + "@factech.co",
        nombre,
        apellido);
    return id;
  }

  private MockHttpServletRequestBuilder cambios() {
    return get("/api/v1/audit/changes")
        .with(user(SUPERADMIN.toString()).authorities(() -> "audit:read-changes"));
  }

  private MockHttpServletRequestBuilder eliminaciones() {
    return get("/api/v1/audit/deletions")
        .with(user(SUPERADMIN.toString()).authorities(() -> "audit:read-deletions"));
  }

  private MockHttpServletRequestBuilder errores() {
    return get("/api/v1/audit/errors")
        .with(user(SUPERADMIN.toString()).authorities(() -> "audit:read-errors"));
  }

  private MockHttpServletRequestBuilder seguridad() {
    return get("/api/v1/audit/security")
        .with(user(SUPERADMIN.toString()).authorities(() -> "audit:read-security"));
  }

  private RequestPostProcessor administrador() {
    return user(SUPERADMIN.toString())
        .authorities(
            () -> "roles:create", () -> "roles:update", () -> "roles:delete", () -> "roles:read");
  }

  private UUID crearRolPorApi(String codigo, String nombre) throws Exception {
    String cuerpo =
        mvc.perform(
                post("/api/v1/roles")
                    .with(administrador())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cuerpoDeAlta(codigo, nombre)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    return UUID.fromString(json.readTree(cuerpo).get("id").asText());
  }

  private String cuerpoDeAlta(String codigo, String nombre) {
    return "{\"code\":\"%s\",\"name\":\"%s\",\"roleType\":\"FUNCIONARIO\",\"parentRoleId\":\"%s\"}"
        .formatted(codigo, nombre, ADMIN);
  }

  private UUID permisoDelAdministrador() {
    return jdbc.queryForObject(
        "SELECT permission_id FROM role_permissions WHERE role_id = ?::uuid LIMIT 1",
        UUID.class,
        ADMIN);
  }

  /** Un evento escrito sin petición HTTP detrás, como el de una migración. */
  private void sembrarCambioSinOrigen() {
    jdbc.update(
        """
        INSERT INTO audit_change_log (id, occurred_at, actor_id, correlation_id, ip_address,
                                      user_agent, module, entity, entity_id, action, changes)
        VALUES (?, now(), NULL, NULL, NULL, NULL, 'SP', 'migracion', ?, 'CREATE', '{"seed":true}')
        """,
        UUID.randomUUID(),
        UUID.randomUUID());
  }

  private long eventosDeLectura() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_security_log WHERE event_type = 'SECURITY_AUDIT_READ'",
        Long.class);
  }

  /**
   * Borra <b>solo lo que produjo una petición</b>, y nunca lo que sembró una migración.
   *
   * <p>La distinción es {@code actor_id}: las filas de las migraciones lo llevan en nulo —«lo hizo
   * el sistema»— y otras clases verifican precisamente esas filas. Vaciar las tablas enteras hacía
   * fallar a `SystemRolesSeedIT` y a `RegisterUserIT` por algo que esta clase no estaba
   * comprobando, y el fallo aparecía o desaparecía según el orden de la suite.
   *
   * <p>Los registros de error y de seguridad sí se vacían: ninguna migración escribe en ellos
   * —`PermissionsSeedIT` lo comprueba leyendo los propios guiones— y lo que haya venido de otras
   * clases es ruido para las cuentas de esta.
   */
  private void limpiar() {
    // Las filas sembradas por las pruebas del actor: la de un evento del sistema
    // lleva `actor_id` nulo y no la barre el DELETE de la línea siguiente.
    jdbc.update("DELETE FROM audit_change_log WHERE entity = 'prueba'");
    jdbc.update("DELETE FROM users WHERE username IN ('retirada', 'bloqueada')");
    jdbc.update("DELETE FROM audit_change_log WHERE actor_id IS NOT NULL OR entity = 'migracion'");
    jdbc.update("DELETE FROM audit_deletion_log WHERE actor_id IS NOT NULL");
    jdbc.update("DELETE FROM audit_error_log");
    jdbc.update("DELETE FROM audit_security_log");
    jdbc.update(
        "DELETE FROM role_permissions WHERE role_id IN"
            + " (SELECT id FROM roles WHERE is_system = false)");
    jdbc.update(
        "DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE is_system = false)");
    jdbc.update("DELETE FROM roles WHERE is_system = false");
    jdbc.update("UPDATE roles SET status = 'ACTIVO', deleted_at = NULL WHERE is_system = true");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid) ON CONFLICT DO NOTHING",
        SUPERADMIN,
        SUPERADMIN_ROL);
  }
}
