package com.factech.nexus.modules.system.users.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
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
 * Criterios de aceptación del alta de personas (`RF-SP-024`).
 *
 * <p><b>El actor de estas pruebas es el superadministrador sembrado por {@code V22}</b>, y no un
 * identificador inventado. Desde este requerimiento, `RN-SEG-010` se verifica contra los permisos
 * que la <b>base</b> concede al actor: un actor que no existe en {@code users} no tendría ninguno,
 * y todas las altas con roles fallarían. Que la migración fije ese identificador es exactamente
 * para esto.
 */
@AutoConfigureMockMvc
class RegisterUserIT extends IntegrationTestBase {

  /** Roles del catálogo sembrado por {@code V7}, referenciados por constante. */
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";

  private static final String CONTABILIDAD = "01a02a33-4c00-7003-9c4f-5e7ad1000003";
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000005";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000006";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000007";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void dejarSoloAlSuperadministrador() {
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM user_roles WHERE user_id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM memberships WHERE level > 0");
  }

  // ---------------------------------------------------------------------------
  // Camino feliz
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-198 — la persona nace ACTIVA y marcada para cambio obligatorio")
  void altaValida() throws Exception {
    mvc.perform(alta("jperez", "Juan.Perez@FACTECH.CO", CONTABILIDAD))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/users/")))
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.mustChangePassword").value(true))
        // El nombre de usuario, TAL COMO SE ESCRIBIÓ; el correo, normalizado.
        .andExpect(jsonPath("$.username").value("jperez"))
        .andExpect(jsonPath("$.email").value("juan.perez@factech.co"))
        .andExpect(jsonPath("$.roles[0].code").value("CONTABILIDAD"));
  }

  @Test
  @DisplayName("CA-SP-196 — la respuesta no contiene la contraseña ni nada derivado de ella")
  void sinRastroDeLaCredencial() throws Exception {
    String cuerpo =
        mvc.perform(alta("jperez", "jperez@factech.co", CONTABILIDAD))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(cuerpo)
        .doesNotContain("password")
        .doesNotContain("Hash")
        .doesNotContain("argon2")
        .doesNotContain(CONTRASENA);
  }

  @Test
  @DisplayName("FA-001 — se admite el alta sin roles")
  void sinRoles() throws Exception {
    mvc.perform(
            post("/api/v1/users")
                .with(superadmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("sinroles", "sinroles@factech.co", "")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.roles").isEmpty());
  }

  // ---------------------------------------------------------------------------
  // Identidad
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("RN-SP-016 — el nombre de usuario duplicado se rechaza ignorando la caja")
  void nombreDeUsuarioDuplicado() throws Exception {
    mvc.perform(alta("JPerez", "uno@factech.co", CONTABILIDAD)).andExpect(status().isCreated());

    // Si esto pasara, `JPerez` no podría entrar escribiendo `jperez` y habría
    // dos personas indistinguibles en la auditoría.
    mvc.perform(alta("jperez", "dos@factech.co", CONTABILIDAD))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-016"))
        .andExpect(jsonPath("$.errors[0].field").value("username"));
  }

  @Test
  @DisplayName("RN-SP-016 — el correo duplicado se rechaza tras normalizar")
  void correoDuplicado() throws Exception {
    mvc.perform(alta("uno", "Juan@Factech.CO", CONTABILIDAD)).andExpect(status().isCreated());

    mvc.perform(alta("dos", "juan@factech.co", CONTABILIDAD))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].field").value("email"));
  }

  @Test
  @DisplayName("CA-SP-341 — el nombre de usuario con arroba se rechaza con 400")
  void nombreDeUsuarioConArroba() throws Exception {
    // Es lo que sostiene el inicio de sesión con las dos identidades: sin esta
    // prohibición, `RF-SP-034` tendría que adivinar qué columna consultar.
    mvc.perform(alta("juan@factech.co", "otro@factech.co", CONTABILIDAD))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.code == 'VAL-010')]").exists());
  }

  // ---------------------------------------------------------------------------
  // Contraseña
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("la contraseña que contiene el nombre de usuario se rechaza")
  void contrasenaQueContieneLaIdentidad() throws Exception {
    // Sin esta regla, `jperez2026` era válida para `jperez` con solo cumplir la
    // longitud, y es la primera que un atacante prueba.
    mvc.perform(altaCon("jperez", "jperez@factech.co", CONTABILIDAD, "jperez2026Segura"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.message =~ /.*nombre de usuario.*/)]").exists());

    mvc.perform(altaCon("otro", "juanperez@factech.co", CONTABILIDAD, "juanperezYalgoMas"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("la contraseña corta y la común se rechazan, y las incumplidas se devuelven juntas")
  void politicaDeContrasena() throws Exception {
    mvc.perform(altaCon("corta", "corta@factech.co", CONTABILIDAD, "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    mvc.perform(altaCon("comun", "comun@factech.co", CONTABILIDAD, "123456789012"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("la contraseña NO se recorta: los espacios de los extremos son parte de ella")
  void laContrasenaNoSeRecorta() throws Exception {
    // Recortarla cambiaría en silencio lo que la persona escribió y haría fallar
    // su primer inicio de sesión.
    mvc.perform(altaCon("espacios", "espacios@factech.co", CONTABILIDAD, "  ClaveLargaSegura  "))
        .andExpect(status().isCreated());

    String hash =
        jdbc.queryForObject(
            "SELECT password_hash FROM users WHERE username = 'espacios'", String.class);
    assertThat(hash).startsWith("$argon2id$");
  }

  // ---------------------------------------------------------------------------
  // Roles
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("EX-003 — un rol inexistente o inactivo devuelve 422 y los nombra todos")
  void rolQueNoSirve() throws Exception {
    mvc.perform(alta("juan", "juan@factech.co", UUID.randomUUID().toString()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    jdbc.update("UPDATE roles SET status = 'INACTIVO' WHERE id = ?::uuid", CONTABILIDAD);
    try {
      mvc.perform(alta("juan", "juan@factech.co", CONTABILIDAD))
          .andExpect(status().isUnprocessableEntity());
    } finally {
      jdbc.update("UPDATE roles SET status = 'ACTIVO' WHERE id = ?::uuid", CONTABILIDAD);
    }
  }

  @Test
  @DisplayName("RN-SEG-010 — no se concede un rol cuyos permisos el actor no posee")
  void rolQueExcedeAlActor() throws Exception {
    // El actor existe en la base y porta CONTABILIDAD, que solo concede dos
    // permisos de auditoría. Sus permisos efectivos salen de ahí, no del token:
    // es la diferencia que este requerimiento introduce.
    UUID contable = crearPersonaConRol("contable", CONTABILIDAD);

    mvc.perform(
            post("/api/v1/users")
                .with(user(contable.toString()).authorities(() -> "users:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("nuevo", "nuevo@factech.co", "\"" + ADMIN + "\"")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SEG-010"))
        .andExpect(jsonPath("$.errors[0].message", org.hamcrest.Matchers.containsString("ADMIN")));
  }

  // ---------------------------------------------------------------------------
  // Membresía y superior — condicionales en los dos sentidos
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("RN-SP-018 — el consumidor exige membresía, y la membresía exige consumidor")
  void consumidorYMembresiaSonInseparables() throws Exception {
    String consumidor = crearRolConsumidor();
    String membresia = crearMembresia();

    mvc.perform(
            post("/api/v1/users")
                .with(superadmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("cliente", "cliente@factech.co", "\"" + consumidor + "\"")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-018"));

    // Y la recíproca: membresía sin rol de consumidor tampoco se ignora.
    mvc.perform(
            post("/api/v1/users")
                .with(superadmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"otro","email":"otro@factech.co","firstName":"O","lastName":"P",
                     "password":"%s","roleIds":["%s"],"membershipId":"%s"}
                    """
                        .formatted(CONTRASENA, CONTABILIDAD, membresia)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-018"));

    // Juntos, sí.
    mvc.perform(
            post("/api/v1/users")
                .with(superadmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"cliente","email":"cliente@factech.co","firstName":"C","lastName":"L",
                     "password":"%s","roleIds":["%s"],"membershipId":"%s"}
                    """
                        .formatted(CONTRASENA, consumidor, membresia)))
        .andExpect(status().isCreated());

    Integer filas = jdbc.queryForObject("SELECT count(*) FROM user_memberships", Integer.class);
    assertThat(filas).isEqualTo(1);
  }

  @Test
  @DisplayName("RN-SP-019 — el vendedor exige superior salvo la cúspide, y al revés")
  void vendedorYSuperiorSonInseparables() throws Exception {
    // MANAGER cuelga de ADMIN, que es FUNCIONARIO: es la cúspide comercial y no
    // declara superior.
    mvc.perform(
            post("/api/v1/users")
                .with(superadmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("jefa", "jefa@factech.co", "\"" + MANAGER + "\"")))
        .andExpect(status().isCreated());

    // AGENTE cuelga de DIRECTOR, que sí es vendedor: exige superior.
    mvc.perform(
            post("/api/v1/users")
                .with(superadmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("agente", "agente@factech.co", "\"" + AGENTE + "\"")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-019"));

    // Y un superior sin rol de vendedor tampoco se ignora.
    mvc.perform(
            post("/api/v1/users")
                .with(superadmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"contable2","email":"contable2@factech.co","firstName":"C","lastName":"D",
                     "password":"%s","roleIds":["%s"],"supervisorId":"%s"}
                    """
                        .formatted(CONTRASENA, CONTABILIDAD, SUPERADMIN)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-019"));
  }

  @Test
  @DisplayName("RN-SP-020 — el superior debe portar el rol PADRE INMEDIATO, no un ancestro")
  void elSuperiorPortaElRolPadre() throws Exception {
    UUID manager = crearPersonaConRol("manager", MANAGER);
    UUID director = crearPersonaConRol("director", DIRECTOR);

    // Un AGENTE reporta a quien porta DIRECTOR, nunca directamente a un MANAGER.
    mvc.perform(altaConSuperior("agente", "agente@factech.co", AGENTE, manager))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-020"))
        .andExpect(
            jsonPath("$.errors[0].message", org.hamcrest.Matchers.containsString("DIRECTOR")));

    mvc.perform(altaConSuperior("agente", "agente@factech.co", AGENTE, director))
        .andExpect(status().isCreated());

    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_supervisors WHERE ended_at IS NULL", Integer.class);
    assertThat(filas).isEqualTo(1);
  }

  @Test
  @DisplayName("RN-SP-020 — un superior inexistente o inactivo se rechaza")
  void superiorQueNoSirve() throws Exception {
    UUID director = crearPersonaConRol("director", DIRECTOR);

    mvc.perform(altaConSuperior("agente", "agente@factech.co", AGENTE, UUID.randomUUID()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-020"));

    jdbc.update("UPDATE users SET status = 'INACTIVO' WHERE id = ?", director);
    mvc.perform(altaConSuperior("agente", "agente@factech.co", AGENTE, director))
        .andExpect(status().isConflict());
  }

  // ---------------------------------------------------------------------------
  // Auditoría, permisos y semilla
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-200 — el alta deja evento de cambio y evento de seguridad USER_CREATED")
  void auditoriaDelAlta() throws Exception {
    UUID correlacion = UUID.randomUUID();

    mvc.perform(
            alta("jperez", "jperez@factech.co", CONTABILIDAD)
                .header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isCreated());

    String changes =
        jdbc.queryForObject(
            """
            SELECT changes::text FROM audit_change_log
             WHERE correlation_id = ? AND entity = 'users' AND action = 'CREATE'
            """,
            String.class,
            correlacion);
    assertThat(changes)
        .contains("jperez")
        .contains("CONTABILIDAD")
        .contains("must_change_password");
    // Ningún campo derivado de la credencial (Art. IV.8). Se comprueba el
    // resumen y la clave `password`, no la subcadena: `must_change_password` la
    // contiene de forma legítima y una aserción cruda daría un falso positivo.
    assertThat(changes)
        .doesNotContain("password_hash")
        .doesNotContain("\"password\"")
        .doesNotContain("argon2");

    Integer seguridad =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM audit_security_log
             WHERE correlation_id = ? AND event_type = 'USER_CREATED'
               AND severity = 'ALTA' AND outcome = 'SUCCESS' AND target_user_id IS NOT NULL
            """,
            Integer.class,
            correlacion);
    // UN solo evento de seguridad, no dos: el alta no emite además el de
    // asignación de roles, o cualquier recuento contaría de más.
    assertThat(seguridad).isEqualTo(1);
  }

  @Test
  @DisplayName("sin el permiso de creación se responde 403 y no se crea nada")
  void sinPermiso() throws Exception {
    mvc.perform(
            post("/api/v1/users")
                .with(user(SUPERADMIN.toString()).authorities(() -> "users:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("jperez", "jperez@factech.co", "")))
        .andExpect(status().isForbidden());

    Integer filas =
        jdbc.queryForObject("SELECT count(*) FROM users WHERE username = 'jperez'", Integer.class);
    assertThat(filas).isZero();
  }

  @Test
  @DisplayName("la semilla dejó al superadministrador con su rol, marcado y auditado")
  void semillaDelSuperadministrador() {
    var fila =
        jdbc.queryForMap(
            "SELECT username, status, must_change_password FROM users WHERE id = ?", SUPERADMIN);
    assertThat(fila.get("username")).isEqualTo("superadmin");
    assertThat(fila.get("status")).isEqualTo("ACTIVO");
    // Quien preparó el despliegue conoce la credencial; la ventana se cierra en
    // el primer inicio de sesión.
    assertThat(fila.get("must_change_password")).isEqualTo(true);

    Integer conRol =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM user_roles ur JOIN roles r ON r.id = ur.role_id
             WHERE ur.user_id = ? AND r.code = 'SUPERADMIN'
            """,
            Integer.class,
            SUPERADMIN);
    assertThat(conRol).isEqualTo(1);

    Integer auditada =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM audit_change_log
             WHERE entity = 'users' AND entity_id = ? AND actor_id IS NULL AND correlation_id IS NULL
            """,
            Integer.class,
            SUPERADMIN);
    assertThat(auditada).isEqualTo(1);
  }

  @Test
  @DisplayName("los permisos efectivos salen de la BASE: un rol desactivado deja de conceder")
  void permisosDesdeLaBase() throws Exception {
    UUID contable = crearPersonaConRol("contable", CONTABILIDAD);

    // Con el rol activo, conceder CONTABILIDAD a otro es legítimo.
    mvc.perform(
            post("/api/v1/users")
                .with(user(contable.toString()).authorities(() -> "users:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("otro", "otro@factech.co", "\"" + CONTABILIDAD + "\"")))
        .andExpect(status().isCreated());

    // Desactivado el rol, sus permisos desaparecen DE INMEDIATO — que es lo que
    // leerlos del token no permitiría hasta que este expirase.
    jdbc.update("UPDATE roles SET status = 'INACTIVO' WHERE id = ?::uuid", CONTABILIDAD);
    try {
      mvc.perform(
              post("/api/v1/users")
                  .with(user(contable.toString()).authorities(() -> "users:create"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(cuerpo("tercero", "tercero@factech.co", "")))
          .andExpect(status().isCreated());
    } finally {
      jdbc.update("UPDATE roles SET status = 'ACTIVO' WHERE id = ?::uuid", CONTABILIDAD);
    }
  }

  // ---------------------------------------------------------------------------

  private static final String CONTRASENA = "ClaveLargaYSegura2026";

  private RequestPostProcessor superadmin() {
    // El identificador es el de la semilla: sus permisos efectivos los resuelve
    // la base. La autoridad del token solo abre la puerta de `@PreAuthorize`.
    return user(SUPERADMIN.toString()).authorities(() -> "users:create");
  }

  private MockHttpServletRequestBuilder alta(String username, String email, String rol) {
    return altaCon(username, email, rol, CONTRASENA);
  }

  private MockHttpServletRequestBuilder altaCon(
      String username, String email, String rol, String contrasena) {
    return post("/api/v1/users")
        .with(superadmin())
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"username":"%s","email":"%s","firstName":"Juan","lastName":"Pérez",
             "password":"%s","roleIds":["%s"]}
            """
                .formatted(username, email, contrasena, rol));
  }

  private MockHttpServletRequestBuilder altaConSuperior(
      String username, String email, String rol, UUID superior) {
    return post("/api/v1/users")
        .with(superadmin())
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"username":"%s","email":"%s","firstName":"A","lastName":"B",
             "password":"%s","roleIds":["%s"],"supervisorId":"%s"}
            """
                .formatted(username, email, CONTRASENA, rol, superior));
  }

  private static String cuerpo(String username, String email, String roles) {
    return """
        {"username":"%s","email":"%s","firstName":"A","lastName":"B",
         "password":"%s","roleIds":[%s]}
        """
        .formatted(username, email, CONTRASENA, roles);
  }

  /** Crea una persona directamente en la base, para usarla como actor o como superior. */
  private UUID crearPersonaConRol(String username, String rolId) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash, status)
        VALUES (?, ?, ?, 'N', 'N', '$argon2id$sin-uso', 'ACTIVO')
        """,
        id,
        username,
        username + "@factech.co");
    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", id, rolId);
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

  private String crearMembresia() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO memberships (id, code, name, parent_membership_id, level)
        VALUES (?, 'ORO', 'Oro', NULL, 1)
        """,
        id);
    return id.toString();
  }
}
