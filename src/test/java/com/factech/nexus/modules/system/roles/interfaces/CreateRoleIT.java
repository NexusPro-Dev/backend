package com.factech.nexus.modules.system.roles.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Criterios de aceptación de `spec.md` §12 y casos límite de §13 (`RF-SP-001` · `T-18`, `T-19`).
 *
 * <p>Extremo a extremo por HTTP y contra PostgreSQL real: es el único nivel en el que se comprueba
 * a la vez el estado devuelto, el {@code error_code} del contrato y la fila que quedó en la
 * auditoría.
 *
 * <p><b>El actor se simula con un post-procesador de Spring Security</b> y no con un token: el
 * inicio de sesión es `RF-SP-034` y todavía no existe. Lo que sí es real es lo que la seguridad
 * hace con ese actor —resolver {@code @PreAuthorize} y publicar sus permisos hacia `RN-SEG-010`—,
 * que es lo que estos criterios verifican.
 */
@AutoConfigureMockMvc
class CreateRoleIT extends IntegrationTestBase {

  /** Roles sembrados por {@code V7}, referenciados por constante. */
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  // ---------------------------------------------------------------------------
  // Camino feliz
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-001 — registra el rol y devuelve 201, Location, padre y permisos")
  void altaValida() throws Exception {
    String permiso = idDePermiso("audit:read-changes");

    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("CA_001", "Criterio 001", ADMIN, "\"" + permiso + "\"")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.code").value("CA_001"))
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.isSystem").value(false))
        .andExpect(jsonPath("$.parentRole.code").value("ADMIN"))
        .andExpect(jsonPath("$.permissions.length()").value(1))
        .andExpect(jsonPath("$.permissions[0].code").value("audit:read-changes"))
        .andExpect(jsonPath("$.permissions[0].name").isNotEmpty())
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        // El actor no vive en la tabla de negocio (Art. V.7): quién lo creó se
        // responde con RF-SP-011, no con un campo de esta respuesta.
        .andExpect(jsonPath("$.createdBy").doesNotExist());
  }

  @Test
  @DisplayName("CA-SP-001 — la cabecera Location apunta al rol creado")
  void cabeceraLocation() throws Exception {
    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("CA_001_LOC", "Criterio 001 location", ADMIN, "")))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/roles/")));
  }

  @Test
  @DisplayName("CA-SP-005 — FA-001: ausente, null y lista vacía significan lo mismo")
  void altaSinPermisos() throws Exception {
    // Los tres caminos deben terminar en 201 con `permissions` vacío.
    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"CA_005_A","name":"Sin permisos ausente","roleType":"FUNCIONARIO",
                     "parentRoleId":"%s"}
                    """
                        .formatted(ADMIN)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.permissions").isEmpty());

    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoConPermisos("CA_005_B", "Sin permisos null", ADMIN, "null")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.permissions").isEmpty());

    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoConPermisos("CA_005_C", "Sin permisos vacia", ADMIN, "[]")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.permissions").isEmpty());
  }

  @Test
  @DisplayName("CA-SP-145 — un rol CONSUMIDOR bajo un padre FUNCIONARIO se registra sin error")
  void clasificacionDistintaDeLaDelPadre() throws Exception {
    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"CA_145","name":"Criterio 145","roleType":"CONSUMIDOR",
                     "parentRoleId":"%s","permissionIds":[]}
                    """
                        .formatted(ADMIN)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.roleType").value("CONSUMIDOR"));
  }

  @Test
  @DisplayName("CA-SP-006 — el código de un rol eliminado lógicamente se reutiliza")
  void codigoDeRolEliminado() throws Exception {
    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("CA_006", "Criterio 006", ADMIN, "")))
        .andExpect(status().isCreated());

    jdbc.update("UPDATE roles SET deleted_at = now() WHERE code = 'CA_006'");

    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("CA_006", "Criterio 006 renacido", ADMIN, "")))
        .andExpect(status().isCreated());
  }

  // ---------------------------------------------------------------------------
  // Rechazos
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-002 — el duplicado devuelve 409 y dice si es de código o de nombre")
  void duplicado() throws Exception {
    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("CA_002", "Criterio 002", ADMIN, "")))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("CA_002", "Otro nombre distinto", ADMIN, "")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SEG-001"))
        .andExpect(jsonPath("$.errors[0].field").value("code"));

    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("CA_002_OTRO", "Criterio 002", ADMIN, "")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SEG-001"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  @DisplayName("CA-SP-003 — RN-SEG-003: 409 enumerando TODOS los permisos fuera del padre")
  void permisoFueraDelPadre() throws Exception {
    // El padre tiene que ser un rol con permisos ACOTADOS, y la prueba se lo
    // fabrica en lugar de tomarlo de la siembra: los dos roles sembrados con
    // permisos son SUPERADMIN —que los tiene todos— y ADMIN —que solo se
    // reserva dos—, de modo que con cualquiera de ellos como padre casi ningún
    // permiso quedaría fuera y la prueba devolvería 201 sin haber ejercitado la
    // regla. Hasta el 29-08-2026 ese padre era `CONTABILIDAD`, que se retiró de
    // la siembra.
    String padreAcotado = crearRolConPermisos("CA_003_PADRE", "audit:read-changes");

    String uno = idDePermiso("roles:read");
    String dos = idDePermiso("permissions:read");

    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    cuerpo(
                        "CA_003", "Criterio 003", padreAcotado, "\"" + uno + "\",\"" + dos + "\"")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("https://nexus.factech.co/errors/regla-de-negocio"))
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(jsonPath("$.errors[0].code").value("RN-SEG-003"))
        .andExpect(jsonPath("$.errors[0].field").value("permissionIds"));
  }

  @Test
  @DisplayName("CA-SP-004 — RN-SEG-010: 409 con un actor que sí puede crear roles")
  void permisoFueraDelActor() throws Exception {
    // El actor posee roles:create pero NO audit:read-changes, y el padre sí lo
    // tiene: es lo que separa esta regla de RN-SEG-003.
    String permiso = idDePermiso("audit:read-changes");

    mvc.perform(
            post("/api/v1/roles")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "roles:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("CA_004", "Criterio 004", ADMIN, "\"" + permiso + "\"")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SEG-010"))
        .andExpect(jsonPath("$.errors[0].field").value("permissionIds"));
  }

  @Test
  @DisplayName("CA-SP-008 — sin roles:create se responde 403 y el rol no se crea")
  void sinPermisoDeCreacion() throws Exception {
    mvc.perform(
            post("/api/v1/roles")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "roles:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("CA_008", "Criterio 008", ADMIN, "")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("https://nexus.factech.co/errors/sin-permiso"));

    assertThat(existeRol("CA_008")).isFalse();
  }

  @Test
  @DisplayName(
      "CA-SP-008 — la denegación deja su evento en audit_security_log, no en el de errores")
  void denegacionAuditada() throws Exception {
    mvc.perform(
            post("/api/v1/roles")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "roles:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("CA_008_AUD", "Criterio 008 auditado", ADMIN, "")))
        .andExpect(status().isForbidden());

    Integer eventos =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM audit_security_log
             WHERE event_type = 'AUTHORIZATION_DENIED' AND severity = 'MEDIA' AND outcome = 'FAILURE'
            """,
            Integer.class);
    assertThat(eventos).isPositive();

    // Y NO en audit_error_log: el esquema lo impide con ck_audit_error_log_status,
    // pero esta comprobación deja constancia de la frontera.
    Integer errores =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_error_log WHERE http_status = 403", Integer.class);
    assertThat(errores).isZero();
  }

  @Test
  @DisplayName("CA-SP-144 — el código con formato inválido devuelve 400 con VAL-008")
  void formatoDelCodigo() throws Exception {
    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("codigo-malo", "Criterio 144", ADMIN, "")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("https://nexus.factech.co/errors/validacion"))
        .andExpect(jsonPath("$.errors[?(@.code == 'VAL-008')]").exists());
  }

  @Test
  @DisplayName("las validaciones de formato se devuelven TODAS juntas, no de a una")
  void todasLasValidaciones() throws Exception {
    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"","name":"","roleType":null,"parentRoleId":null}
                    """))
        .andExpect(status().isBadRequest())
        // code (VAL-001), name (VAL-002), roleType (VAL-003) y parentRoleId (VAL-004).
        .andExpect(
            jsonPath("$.errors.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
  }

  @Test
  @DisplayName("CA-SP-146 — enviar `status` devuelve 400: no hay camino hacia INACTIVO en el alta")
  void campoDesconocido() throws Exception {
    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"CA_146","name":"Criterio 146","roleType":"FUNCIONARIO",
                     "parentRoleId":"%s","status":"INACTIVO"}
                    """
                        .formatted(ADMIN)))
        .andExpect(status().isBadRequest());

    assertThat(existeRol("CA_146")).isFalse();
  }

  @Test
  @DisplayName("EX-002 — el rol padre eliminado lógicamente se trata como inexistente: 422")
  void padreEliminado() throws Exception {
    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("EX_002_PADRE", "Padre que se elimina", ADMIN, "")))
        .andExpect(status().isCreated());

    String padre =
        jdbc.queryForObject("SELECT id::text FROM roles WHERE code = 'EX_002_PADRE'", String.class);
    jdbc.update("UPDATE roles SET deleted_at = now() WHERE code = 'EX_002_PADRE'");

    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("EX_002_HIJO", "Hijo huérfano", padre, "")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        .andExpect(jsonPath("$.errors[0].field").value("parentRoleId"));
  }

  @Test
  @DisplayName("EX-005 — un permiso ausente del catálogo devuelve 422 y los nombra todos")
  void permisoInexistente() throws Exception {
    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    cuerpo(
                        "EX_005",
                        "Permiso inexistente",
                        ADMIN,
                        "\"" + UUID.randomUUID() + "\",\"" + UUID.randomUUID() + "\"")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.type").value("https://nexus.factech.co/errors/entidad-no-procesable"))
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"));
  }

  // ---------------------------------------------------------------------------
  // Auditoría y casos límite
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-007 — el alta deja fila en audit_change_log y en audit_security_log")
  void auditoriaDelAlta() throws Exception {
    String permiso = idDePermiso("audit:read-deletions");

    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("CA_007", "Criterio 007", ADMIN, "\"" + permiso + "\"")))
        .andExpect(status().isCreated());

    String rol =
        jdbc.queryForObject("SELECT id::text FROM roles WHERE code = 'CA_007'", String.class);

    // Auditoría de cambios: misma transacción que el alta, con el estado inicial.
    String changes =
        jdbc.queryForObject(
            """
            SELECT changes::text FROM audit_change_log
             WHERE entity_id = ?::uuid AND action = 'CREATE' AND module = 'SP' AND entity = 'roles'
            """,
            String.class,
            rol);
    assertThat(changes).contains("\"code\": \"CA_007\"").contains("audit:read-deletions");

    // Auditoría de seguridad: transacción propia, después del commit.
    Integer seguridad =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM audit_security_log
             WHERE event_type = 'ROLE_CREATED' AND severity = 'ALTA' AND outcome = 'SUCCESS'
               AND detail->>'roleCode' = 'CA_007'
            """,
            Integer.class);
    assertThat(seguridad).isEqualTo(1);
  }

  @Test
  @DisplayName("CA-SP-007 — ambas filas llevan la correlación y la IP de la petición")
  void auditoriaConOrigen() throws Exception {
    UUID correlacion = UUID.randomUUID();

    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .header("X-Correlation-Id", correlacion.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("CA_007_ORIG", "Criterio 007 origen", ADMIN, "")))
        .andExpect(status().isCreated());

    Integer cambios =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE correlation_id = ? AND ip_address IS NOT NULL",
            Integer.class,
            correlacion);
    assertThat(cambios).isEqualTo(1);

    Integer seguridad =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_security_log WHERE correlation_id = ? AND ip_address IS NOT NULL",
            Integer.class,
            correlacion);
    assertThat(seguridad).isEqualTo(1);
  }

  @Test
  @DisplayName("los permisos duplicados en la petición se colapsan sin error")
  void permisosDuplicados() throws Exception {
    String permiso = idDePermiso("roles:read");

    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    cuerpo(
                        "DUP_PERM",
                        "Permisos duplicados",
                        ADMIN,
                        "\"" + permiso + "\",\"" + permiso + "\",\"" + permiso + "\"")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.permissions.length()").value(1));

    Integer filas =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
             WHERE r.code = 'DUP_PERM'
            """,
            Integer.class);
    assertThat(filas).isEqualTo(1);
  }

  @Test
  @DisplayName("el nombre se recorta: la unicidad no se burla con un espacio")
  void nombreRecortado() throws Exception {
    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("TRIM_UNO", "  Nombre recortado  ", ADMIN, "")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Nombre recortado"));

    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("TRIM_DOS", "Nombre recortado", ADMIN, "")))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("sin autenticar se responde 401, no 403")
  void sinAutenticar() throws Exception {
    mvc.perform(
            post("/api/v1/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("NO_AUTH", "Sin autenticar", ADMIN, "")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("la respuesta de error siempre lleva correlationId (Art. XV.1)")
  void correlacionEnElError() throws Exception {
    mvc.perform(
            post("/api/v1/roles")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("codigo-malo-2", "Correlación", ADMIN, "")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  // ---------------------------------------------------------------------------

  /** Actor con {@code roles:create} y el catálogo completo, como lo tendría un SUPERADMIN. */
  private RequestPostProcessor admin() {
    List<String> todos = jdbc.queryForList("SELECT code FROM permissions", String.class);
    return user(UUID.randomUUID().toString())
        .authorities(
            todos.stream()
                .map(c -> (org.springframework.security.core.GrantedAuthority) () -> c)
                .toList());
  }

  /**
   * Un rol de negocio colgado de ADMIN con exactamente los permisos indicados, para poder usarlo
   * como padre acotado. Se inserta por SQL y no por la API porque lo que se va a verificar es el
   * endpoint de creación: construir el fixture con él haría que un fallo del propio endpoint se
   * confundiera con el caso bajo prueba.
   */
  private String crearRolConPermisos(String codigo, String... permisos) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO roles (id, code, name, description, role_type, parent_role_id,
                           status, is_system)
        VALUES (?, ?, ?, 'Rol de prueba.', 'FUNCIONARIO', ?::uuid, 'ACTIVO', false)
        """,
        id,
        codigo,
        codigo,
        ADMIN);
    for (String permiso : permisos) {
      jdbc.update(
          "INSERT INTO role_permissions (role_id, permission_id) VALUES (?, ?::uuid)",
          id,
          idDePermiso(permiso));
    }
    return id.toString();
  }

  private String idDePermiso(String codigo) {
    return jdbc.queryForObject(
        "SELECT id::text FROM permissions WHERE code = ?", String.class, codigo);
  }

  private boolean existeRol(String codigo) {
    Integer filas =
        jdbc.queryForObject("SELECT count(*) FROM roles WHERE code = ?", Integer.class, codigo);
    return filas != null && filas > 0;
  }

  private static String cuerpo(String code, String name, String padre, String permisos) {
    return """
        {"code":"%s","name":"%s","roleType":"FUNCIONARIO","parentRoleId":"%s","permissionIds":[%s]}
        """
        .formatted(code, name, padre, permisos);
  }

  private static String cuerpoConPermisos(String code, String name, String padre, String permisos) {
    return """
        {"code":"%s","name":"%s","roleType":"FUNCIONARIO","parentRoleId":"%s","permissionIds":%s}
        """
        .formatted(code, name, padre, permisos);
  }
}
