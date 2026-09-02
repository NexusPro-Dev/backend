package com.factech.nexus.modules.system.users.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Asignación y retiro de roles (`RF-SP-030`, `RF-SP-031`).
 *
 * <p>Las dos operaciones se prueban juntas <b>a propósito</b>. `RF-SP-031` · `T-14` lo exige para
 * `CA-SP-363`: repartida entre las dos tripletas, cada mitad pasaría sin comprobar la diferencia
 * que importa —que solo el retiro revoca sesiones—. Y las cascadas de `RN-SP-015` y `RN-SP-019`
 * solo se pueden observar montando antes el estado con la operación contraria.
 *
 * <p>Cadena comercial sembrada por `V7`: {@code MANAGER → DIRECTOR → AGENTE}. {@code MANAGER} es la
 * <b>cúspide</b>, porque su rol padre es {@code ADMIN}, que no es vendedor.
 */
@AutoConfigureMockMvc
class UserRolesIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000003";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID persona;

  /**
   * DOS roles de negocio con permisos acotados, que es lo que estas pruebas conceden y retiran.
   *
   * <p>Tienen que ser <b>dos y distintos</b>: la asignación es aditiva y no un reemplazo ({@link
   * #aditiva}), y desde `RN-SP-023` nadie puede quedarse sin ningún rol, de modo que retirar uno
   * exige que quede el otro. Y tienen que ser <b>acotados</b>: {@link #escaladaDePrivilegios}
   * necesita que quien porta uno de ellos NO alcance a conceder ADMIN.
   *
   * <p>Hasta el 29-08-2026 este papel lo hacían {@code CONTABILIDAD} y {@code LIDER_ACADEMICO},
   * retirados del catálogo sembrado por decisión del responsable del proyecto. En la siembra ya no
   * quedan dos roles con esa forma, de modo que la clase se los fabrica.
   */
  private String rolAcotado;

  private String rolDeReserva;

  @BeforeEach
  void dejarSoloAlSuperadministrador() {
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM user_roles");
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
    // Los permisos del rol van antes que el rol: otra prueba de la suite deja
    // roles no sistémicos CON permisos, y la clave foránea es RESTRICT.
    jdbc.update(
        "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE is_system = false)");
    jdbc.update("DELETE FROM roles WHERE is_system = false");
    jdbc.update("DELETE FROM memberships WHERE level > 0");

    // El rol raíz se REPONE, y no basta con no borrarlo: dos pruebas de esta
    // misma clase se lo retiran al superadministrador a propósito, y sin
    // reponerlo el actor de las siguientes se queda sin un solo permiso — con lo
    // que todas fallarían por `RN-SEG-010` en lugar de por lo que comprueban.
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        SUPERADMIN,
        SUPERADMIN_ROL);

    rolAcotado = crearRolAcotado(jdbc, "AUDITORIA_ACOTADA", "Auditoría acotada").toString();
    rolDeReserva = crearRolAcotado(jdbc, "AUDITORIA_RESERVA", "Auditoría de reserva").toString();

    persona = crearPersona("jperez");
  }

  // ---------------------------------------------------------------------------
  // Asignar — camino feliz e idempotencia
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-251 — agrega los roles y devuelve la lista actualizada")
  void asignacionValida() throws Exception {
    mvc.perform(asignar(persona, rolAcotado))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[0].code").value("AUDITORIA_ACOTADA"))
        // La membresía y el superior viajan SIEMPRE, en nulo cuando no hay:
        // «no tiene» tiene que distinguirse de «este endpoint no lo informa».
        .andExpect(jsonPath("$.membership").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.supervisor").value(org.hamcrest.Matchers.nullValue()));

    assertThat(rolesDe(persona)).containsExactly(rolAcotado);
  }

  @Test
  @DisplayName("CA-SP-256 — es ADITIVA: no reemplaza la lista")
  void aditiva() throws Exception {
    mvc.perform(asignar(persona, rolAcotado)).andExpect(status().isOk());
    mvc.perform(asignar(persona, rolDeReserva)).andExpect(status().isOk());

    // Si fuera un reemplazo, aquí quedaría solo el de reserva — y ese retiro implícito
    // se habría saltado `RN-SP-001`, `RN-SP-015` y `RN-SP-022`.
    assertThat(rolesDe(persona)).containsExactlyInAnyOrder(rolAcotado, rolDeReserva);
  }

  @Test
  @DisplayName("CA-SP-252 — repetir la asignación no cambia nada y NO deja auditoría")
  void idempotente() throws Exception {
    mvc.perform(asignar(persona, rolAcotado)).andExpect(status().isOk());
    int antes = eventosDeCambio(persona);

    mvc.perform(asignar(persona, rolAcotado)).andExpect(status().isOk());

    // Sin el cálculo del delta, cada repetición dejaría un evento describiendo
    // una asignación que ya existía, y el recuento de concesiones mentiría.
    assertThat(eventosDeCambio(persona)).isEqualTo(antes);
    assertThat(rolesDe(persona)).containsExactly(rolAcotado);
  }

  @Test
  @DisplayName("CA-SP-257 — la asignación deja evento de cambio y uno de seguridad ALTA")
  void auditoriaDeLaAsignacion() throws Exception {
    UUID correlacion = UUID.randomUUID();
    mvc.perform(asignar(persona, rolAcotado).header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isOk());

    Integer cambios =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE correlation_id = ? AND entity = 'user_roles'",
            Integer.class,
            correlacion);
    assertThat(cambios).isEqualTo(1);

    Integer seguridad =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM audit_security_log
             WHERE correlation_id = ? AND event_type = 'USER_ROLES_ASSIGNED'
               AND severity = 'ALTA' AND target_user_id = ?
            """,
            Integer.class,
            correlacion,
            persona);
    assertThat(seguridad).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // Asignar — rechazos
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("EX-004 — la persona inexistente o eliminada devuelve 404 y no se audita")
  void personaInexistente() throws Exception {
    UUID correlacion = UUID.randomUUID();
    mvc.perform(
            asignar(UUID.randomUUID(), rolAcotado)
                .header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("https://nexus.factech.co/errors/no-encontrado"));

    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", persona);
    mvc.perform(asignar(persona, rolAcotado)).andExpect(status().isNotFound());

    Integer errores =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_error_log WHERE correlation_id = ?",
            Integer.class,
            correlacion);
    assertThat(errores).isZero();
  }

  @Test
  @DisplayName("la cuenta INACTIVA sí se puede administrar: el paso 2 no exige que esté activa")
  void cuentaInactivaSeAdministra() throws Exception {
    // Exigir `ACTIVO` aquí convertiría una cuenta suspendida en una cuenta
    // inadministrable, y preparar su vuelta es justo lo que se hace con ella.
    jdbc.update("UPDATE users SET status = 'INACTIVO' WHERE id = ?", persona);

    mvc.perform(asignar(persona, rolAcotado)).andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "EX-002 y EX-003 llevan códigos DISTINTOS: corregir el id no es lo mismo que activar")
  void rolInexistenteYRolInactivo() throws Exception {
    mvc.perform(asignar(persona, UUID.randomUUID().toString()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    String inactivo = crearRol("PAUSADO", "FUNCIONARIO", ADMIN);
    jdbc.update("UPDATE roles SET status = 'INACTIVO' WHERE id = ?::uuid", inactivo);

    mvc.perform(asignar(persona, inactivo))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"))
        .andExpect(
            jsonPath("$.errors[0].message", org.hamcrest.Matchers.containsString("PAUSADO")));
  }

  @Test
  @DisplayName("el rol ELIMINADO del catálogo se rechaza como inexistente, no como inactivo")
  void rolEliminado() throws Exception {
    String eliminado = crearRol("RETIRADO", "FUNCIONARIO", ADMIN);
    jdbc.update("UPDATE roles SET deleted_at = now() WHERE id = ?::uuid", eliminado);

    mvc.perform(asignar(persona, eliminado))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
  }

  @Test
  @DisplayName("CA-SP-253 — RN-SEG-010: 409 que ENUMERA los roles fuera de alcance")
  void escaladaDePrivilegios() throws Exception {
    UUID contable = crearPersona("contable");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        contable,
        rolAcotado);

    // El rol acotado concede dos permisos de lectura de auditoría y nada más.
    mvc.perform(asignarComo(contable, persona, ADMIN))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SEG-010"))
        .andExpect(jsonPath("$.errors[0].message", org.hamcrest.Matchers.containsString("ADMIN")));

    // Y lo que sí alcanza, lo concede: la comparación es por PERMISOS.
    mvc.perform(asignarComo(contable, persona, rolAcotado)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("VAL-002 y VAL-005 — lista vacía y lista de 101 devuelven 400 desde el validador")
  void limitesDelCuerpo() throws Exception {
    mvc.perform(cuerpoDeAsignacion(persona, "[]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.code == 'VAL-002')]").exists());

    StringBuilder muchos = new StringBuilder("[");
    for (int i = 0; i < 101; i++) {
      muchos.append(i == 0 ? "" : ",").append('"').append(UUID.randomUUID()).append('"');
    }
    muchos.append(']');

    mvc.perform(cuerpoDeAsignacion(persona, muchos.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.code == 'VAL-005')]").exists());
  }

  // ---------------------------------------------------------------------------
  // Asignar — membresía y estructura comercial
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("RN-SP-018 — el primer rol de consumidor exige membresía, y es 422 y no 400")
  void consumidorExigeMembresia() throws Exception {
    String consumidor = crearRol("ESTUDIANTE", "CONSUMIDOR", ADMIN);

    mvc.perform(asignar(persona, consumidor))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-018"));

    String membresia = crearMembresia();
    mvc.perform(asignarConMembresia(persona, consumidor, membresia))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.membership.code").value("ORO"));
  }

  @Test
  @DisplayName("EX-006 — la membresía sin rol de consumidor no se ignora: se rechaza")
  void membresiaSinConsumidor() throws Exception {
    // Si se ignorara, una petición copiada de otra dejaría una membresía
    // colgando de quien no es consumidor.
    String membresia = crearMembresia();

    mvc.perform(asignarConMembresia(persona, rolAcotado, membresia))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));
  }

  @Test
  @DisplayName("RN-SP-019 — el primer rol de vendedor exige superior comercial")
  void vendedorExigeSuperior() throws Exception {
    mvc.perform(asignar(persona, AGENTE))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-019"));
  }

  @Test
  @DisplayName("RN-SP-020 — el superior debe portar el rol PADRE INMEDIATO, no un ancestro")
  void superiorConElRolPadreInmediato() throws Exception {
    UUID manager = crearPersona("elmanager");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        manager,
        MANAGER);

    // Un AGENTE reporta a un DIRECTOR. MANAGER es ancestro, no padre inmediato:
    // admitirlo rompería la aciclicidad que la cadena de roles garantiza.
    mvc.perform(asignarConSuperior(persona, AGENTE, manager))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-020"));

    UUID director = crearPersona("eldirector");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        director,
        DIRECTOR);

    mvc.perform(asignarConSuperior(persona, AGENTE, director))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.supervisor.username").value("eldirector"));
  }

  @Test
  @DisplayName("la cúspide comercial no declara superior: indicarlo se rechaza")
  void cuspideSinSuperior() throws Exception {
    UUID otro = crearPersona("otro");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        otro,
        ADMIN);

    mvc.perform(asignarConSuperior(persona, MANAGER, otro))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-008"));

    mvc.perform(asignar(persona, MANAGER)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("CA-SP-399 — el ASCENSO exige declarar de nuevo el superior")
  void ascenso() throws Exception {
    UUID director = crearPersona("eldirector");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        director,
        DIRECTOR);
    mvc.perform(asignarConSuperior(persona, AGENTE, director)).andExpect(status().isOk());

    // Ascender a DIRECTOR cambia con quién debe cumplirse la regla: un director
    // no puede seguir a cargo de otro director.
    mvc.perform(asignar(persona, DIRECTOR))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-019"));

    UUID manager = crearPersona("elmanager");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        manager,
        MANAGER);

    mvc.perform(asignarConSuperior(persona, DIRECTOR, manager))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.supervisor.username").value("elmanager"));

    // La asignación anterior queda CERRADA, no borrada: dice a quién se
    // atribuía cada resultado antes del ascenso.
    Integer cerradas =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_supervisors WHERE user_id = ? AND ended_at IS NOT NULL",
            Integer.class,
            persona);
    assertThat(cerradas).isEqualTo(1);
  }

  @Test
  @DisplayName("CA-SP-404 — el DESCENSO sustituye igual, y exige superior igual")
  void descenso() throws Exception {
    UUID manager = crearPersona("elmanager");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        manager,
        MANAGER);
    mvc.perform(asignarConSuperior(persona, DIRECTOR, manager)).andExpect(status().isOk());

    // HASTA EL 02-09-2026 ESTO ERA LO CONTRARIO. Se llamaba «asignación
    // lateral»: añadir AGENTE a un DIRECTOR dejaba los dos roles, no cambiaba
    // «el de mayor rango» y no pedía superior.
    //
    // Con `RN-SP-025` no conviven: el AGENTE SUSTITUYE al DIRECTOR, y eso es un
    // DESCENSO. Su superior porta MANAGER —el padre de DIRECTOR—, no el padre
    // de AGENTE, de modo que `RN-SP-020` dejaría de cumplirse exactamente igual
    // que en un ascenso. La regla mira el rol vendedor, no un techo entre
    // varios.
    mvc.perform(asignar(persona, AGENTE))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-019"));

    UUID director = crearPersona("eldirector");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        director,
        DIRECTOR);

    mvc.perform(asignarConSuperior(persona, AGENTE, director))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.supervisor.username").value("eldirector"));

    // Y termina portando UN SOLO rol vendedor: el DIRECTOR salió.
    assertThat(rolesVendedoresDe(persona)).containsExactly("AGENTE");
  }

  @Test
  @DisplayName("CA-SP-527 — asignar un vendedor RETIRA el anterior: nunca quedan dos")
  void asignarVendedorSustituye() throws Exception {
    UUID director = crearPersona("eldirector");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        director,
        DIRECTOR);
    mvc.perform(asignarConSuperior(persona, AGENTE, director)).andExpect(status().isOk());

    UUID manager = crearPersona("elmanager");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        manager,
        MANAGER);
    mvc.perform(asignarConSuperior(persona, DIRECTOR, manager)).andExpect(status().isOk());

    assertThat(rolesVendedoresDe(persona)).containsExactly("DIRECTOR");
  }

  @Test
  @DisplayName("CA-SP-528 — la auditoría cita el rol que SALE, no solo los que entran")
  void laAuditoriaCitaElRolRetirado() throws Exception {
    UUID director = crearPersona("eldirector");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        director,
        DIRECTOR);
    mvc.perform(asignarConSuperior(persona, AGENTE, director)).andExpect(status().isOk());

    UUID manager = crearPersona("elmanager");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        manager,
        MANAGER);
    mvc.perform(asignarConSuperior(persona, DIRECTOR, manager)).andExpect(status().isOk());

    // La operación se llama «asignar» y desde `RN-SP-025` también retira. Si el
    // evento solo citara lo que entra, el AGENTE desaparecería sin que ningún
    // registro lo explicara.
    String cambio =
        jdbc.queryForObject(
            "SELECT CAST(changes AS text) FROM audit_change_log"
                + " WHERE entity = 'user_roles' AND entity_id = ?"
                + " ORDER BY occurred_at DESC LIMIT 1",
            String.class,
            persona);

    assertThat(cambio).contains("removed_roles").contains("AGENTE").contains("DIRECTOR");
  }

  @Test
  @DisplayName("CA-SP-529 — el ESQUEMA lo impide: un INSERT directo del segundo vendedor falla")
  void elEsquemaImpideDosVendedores() throws Exception {
    UUID director = crearPersona("eldirector");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        director,
        DIRECTOR);
    mvc.perform(asignarConSuperior(persona, AGENTE, director)).andExpect(status().isOk());

    // TODAS LAS DEMÁS PRUEBAS PASAN POR EL CASO DE USO, que ya sustituye por su
    // cuenta: ninguna se enteraría si alguien retirara el índice. Esta va contra
    // el esquema, que es donde `RN-SP-025` vive desde `V51`.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO user_roles (user_id, role_id, role_type)"
                        + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
                    persona,
                    DIRECTOR))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  @DisplayName("`VAL-009` — dos roles vendedores en la MISMA petición se rechazan enteros")
  void dosVendedoresEnUnaPeticion() throws Exception {
    // No se resuelve aplicando uno y descartando el otro: no hay orden que no
    // viole la regla a mitad de camino, y elegir cuál gana sería decidir por
    // quien pidió la operación.
    mvc.perform(cuerpoDeAsignacion(persona, "[\"" + AGENTE + "\",\"" + DIRECTOR + "\"]"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-009"));

    assertThat(rolesVendedoresDe(persona)).isEmpty();
  }

  /** Los códigos de los roles de tipo `VENDEDOR` que porta una persona. */
  private List<String> rolesVendedoresDe(UUID usuario) {
    return jdbc.queryForList(
        "SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id"
            + " WHERE ur.user_id = ? AND ur.role_type = 'VENDEDOR' ORDER BY r.code",
        String.class,
        usuario);
  }

  @Test
  @DisplayName("CA-SP-259 — roles, membresía y superior se escriben en la MISMA transacción")
  void todoEnUnaTransaccion() throws Exception {
    String consumidor = crearRol("ESTUDIANTE", "CONSUMIDOR", ADMIN);
    String membresia = crearMembresia();

    // Un superior inadmisible hace fallar el paso 7; nada anterior debe quedar.
    UUID nadie = crearPersona("nadie");
    mvc.perform(asignarTodo(persona, List.of(consumidor, AGENTE), membresia, nadie.toString()))
        .andExpect(status().isUnprocessableEntity());

    assertThat(rolesDe(persona)).isEmpty();
    Integer membresias =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_memberships WHERE user_id = ?", Integer.class, persona);
    assertThat(membresias).isZero();
  }

  @Test
  @DisplayName("dos asignaciones simultáneas del mismo rol: ambas 200, una fila, ningún 500")
  void asignacionConcurrente() {
    // Sin `ON CONFLICT DO NOTHING`, la segunda inserción espera al desenlace de
    // la primera y recibe 23505 al confirmar esta — que sin tratamiento sale
    // como 500.
    List<ConcurrencyHarness.Outcome<Integer>> resultados =
        ConcurrencyHarness.runTogether(
            2,
            indice ->
                mvc.perform(asignar(persona, rolAcotado)).andReturn().getResponse().getStatus());

    assertThat(resultados).allMatch(ConcurrencyHarness.Outcome::succeeded);
    assertThat(resultados).allMatch(salida -> salida.value() == 200);

    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_roles WHERE user_id = ?", Integer.class, persona);
    assertThat(filas).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // Retirar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-262 — retira el rol indicado y conserva los demás")
  void retiroValido() throws Exception {
    // Se asignan DOS y se retira UNO: desde `RN-SP-023` (24-08-2026) no se puede
    // dejar a nadie sin ningún rol, de modo que la única forma de comprobar el
    // camino feliz del retiro es que quede algo detrás.
    mvc.perform(cuerpoDeAsignacion(persona, "[\"" + rolAcotado + "\",\"" + rolDeReserva + "\"]"))
        .andExpect(status().isOk());

    mvc.perform(retirar(persona, rolAcotado)).andExpect(status().isOk());

    assertThat(rolesDe(persona)).containsExactly(rolDeReserva);
  }

  @Test
  @DisplayName("CA-SP-269 — el retiro que dejaría a la persona sin ningún rol se rechaza")
  void retiroDelUltimoRol() throws Exception {
    mvc.perform(asignar(persona, rolAcotado)).andExpect(status().isOk());

    mvc.perform(retirar(persona, rolAcotado))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-023"));

    assertThat(rolesDe(persona)).as("un rechazo no puede retirar nada").containsExactly(rolAcotado);
  }

  @Test
  @DisplayName("FA-001 — retirar un rol que no se tiene no es un error y no deja rastro")
  void retiroSinEfecto() throws Exception {
    UUID correlacion = UUID.randomUUID();
    mvc.perform(retirar(persona, rolAcotado).header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isOk());

    Integer eliminaciones =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_deletion_log WHERE correlation_id = ?",
            Integer.class,
            correlacion);
    assertThat(eliminaciones).isZero();
  }

  @Test
  @DisplayName("se puede retirar un rol ELIMINADO del catálogo: la asignación sigue ahí")
  void retiroDeRolEliminado() throws Exception {
    conRolDeReserva(persona);
    String rol = crearRol("OBSOLETO", "FUNCIONARIO", ADMIN);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        persona,
        rol);
    jdbc.update("UPDATE roles SET deleted_at = now() WHERE id = ?::uuid", rol);

    // Es la asimetría con `RF-SP-030` que más se implementa de más: comprobar
    // aquí que el rol existe dejaría la asignación atrapada para siempre.
    mvc.perform(retirar(persona, rol)).andExpect(status().isOk());
    assertThat(rolesDe(persona)).containsExactly(rolDeReserva);
  }

  @Test
  @DisplayName("RN-SEG-010 gobierna también el RETIRO")
  void retiroFueraDeAlcance() throws Exception {
    UUID contable = crearPersona("contable");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        contable,
        rolAcotado);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        persona,
        ADMIN);

    // Quien no posee el permiso no puede quitar el rol que lo concede.
    mvc.perform(retirarComo(contable, persona, ADMIN))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SEG-010"));
  }

  @Test
  @DisplayName("RN-SP-001 — el sistema no se queda sin superadministrador activo")
  void ultimoSuperadministrador() throws Exception {
    mvc.perform(retirar(SUPERADMIN, SUPERADMIN_ROL))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-001"));

    // Con un segundo portador activo, el retiro procede.
    UUID reserva = crearPersona("reserva");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        reserva,
        SUPERADMIN_ROL);

    // `RN-SP-023` —«una persona debe conservar al menos un rol»— la incorporó
    // otra sesión de trabajo el 24-08-2026, y alcanza a este caso: al
    // superadministrador solo le queda el rol raíz, de modo que retirárselo lo
    // dejaría sin ninguno. Se le concede otro rol para que la prueba siga
    // comprobando lo suyo —`RN-SP-001`— y no la regla nueva.
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        SUPERADMIN,
        rolAcotado);

    mvc.perform(retirar(SUPERADMIN, SUPERADMIN_ROL)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("dos retiros simultáneos sobre los DOS últimos: uno 200 y otro 409")
  void ultimoSuperadministradorConcurrente() {
    UUID reserva = crearPersona("reserva");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        reserva,
        SUPERADMIN_ROL);

    // Con el bloqueo sobre la fila de `users` en lugar de sobre la asignación,
    // las dos transacciones tocarían filas distintas, no se esperarían, las dos
    // verían dos portadores y el sistema terminaría sin ninguno.
    // Ambos conservan un rol de reserva: lo que esta prueba mide es `RN-SP-001`
    // bajo concurrencia, no `RN-SP-023`.
    conRolDeReserva(SUPERADMIN);
    conRolDeReserva(reserva);

    List<UUID> victimas = List.of(SUPERADMIN, reserva);
    List<ConcurrencyHarness.Outcome<Integer>> resultados =
        ConcurrencyHarness.runTogether(
            2,
            indice ->
                mvc.perform(retirar(victimas.get(indice), SUPERADMIN_ROL))
                    .andReturn()
                    .getResponse()
                    .getStatus());

    assertThat(resultados).allMatch(ConcurrencyHarness.Outcome::succeeded);
    List<Integer> estados = resultados.stream().map(ConcurrencyHarness.Outcome::value).toList();
    assertThat(estados).containsExactlyInAnyOrder(200, 409);

    Integer quedan =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_roles WHERE role_id = ?::uuid",
            Integer.class,
            SUPERADMIN_ROL);
    assertThat(quedan).isEqualTo(1);
  }

  @Test
  @DisplayName("RN-SP-022 — con equipo a cargo se rechaza diciendo CUÁNTOS, nunca quiénes")
  void conEquipoACargo() throws Exception {
    UUID manager = crearPersona("elmanager");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        manager,
        MANAGER);
    mvc.perform(asignarConSuperior(persona, DIRECTOR, manager)).andExpect(status().isOk());

    UUID agente = crearPersona("elagente");
    mvc.perform(asignarConSuperior(agente, AGENTE, persona)).andExpect(status().isOk());

    mvc.perform(retirar(persona, DIRECTOR))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-022"))
        .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("1")))
        // Quién forma el equipo se consulta con `RF-SP-042`, que tiene su propio
        // permiso: devolverlo aquí lo concedería por una puerta lateral.
        .andExpect(
            jsonPath(
                "$.detail",
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("elagente"))));
  }

  @Test
  @DisplayName("RN-SP-015 — quedarse sin rol de consumidor BORRA la membresía")
  void cascadaDeMembresia() throws Exception {
    conRolDeReserva(persona);
    String consumidor = crearRol("ESTUDIANTE", "CONSUMIDOR", ADMIN);
    String membresia = crearMembresia();
    mvc.perform(asignarConMembresia(persona, consumidor, membresia)).andExpect(status().isOk());

    mvc.perform(retirar(persona, consumidor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.membership").value(org.hamcrest.Matchers.nullValue()));

    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_memberships WHERE user_id = ?", Integer.class, persona);
    assertThat(filas).isZero();
  }

  @Test
  @DisplayName("RN-SP-019 — quedarse sin rol de vendedor CIERRA el superior, no lo borra")
  void cascadaDeSuperior() throws Exception {
    conRolDeReserva(persona);
    UUID manager = crearPersona("elmanager");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        manager,
        MANAGER);
    mvc.perform(asignarConSuperior(persona, DIRECTOR, manager)).andExpect(status().isOk());

    mvc.perform(retirar(persona, DIRECTOR))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.supervisor").value(org.hamcrest.Matchers.nullValue()));

    // La fila SIGUE existiendo con su fecha de cierre: borrarla reescribiría la
    // historia de las comisiones.
    Integer cerradas =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_supervisors WHERE user_id = ? AND ended_at IS NOT NULL",
            Integer.class,
            persona);
    assertThat(cerradas).isEqualTo(1);
  }

  @Test
  @DisplayName("la auditoría distingue lo que se BORRA de lo que se CIERRA")
  void auditoriaDelRetiro() throws Exception {
    conRolDeReserva(persona);
    UUID manager = crearPersona("elmanager");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        manager,
        MANAGER);
    mvc.perform(asignarConSuperior(persona, DIRECTOR, manager)).andExpect(status().isOk());

    UUID correlacion = UUID.randomUUID();
    mvc.perform(retirar(persona, DIRECTOR).header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isOk());

    Integer eliminaciones =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM audit_deletion_log
             WHERE correlation_id = ? AND entity = 'user_roles'
               AND deletion_type = 'ASSOCIATION' AND reason IS NULL
            """,
            Integer.class,
            correlacion);
    assertThat(eliminaciones).isEqualTo(1);

    // El cierre del superior es un CAMBIO: esa fila sigue ahí.
    Integer cierres =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE correlation_id = ? AND entity = 'user_supervisors'",
            Integer.class,
            correlacion);
    assertThat(cierres).isEqualTo(1);

    Integer seguridad =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM audit_security_log
             WHERE correlation_id = ? AND event_type = 'USER_ROLES_REVOKED' AND severity = 'ALTA'
            """,
            Integer.class,
            correlacion);
    assertThat(seguridad).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // La asimetría — CA-SP-363
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-363 — asignar NO revoca sesiones; retirar SÍ")
  void soloElRetiroRevocaSesiones() throws Exception {
    conRolDeReserva(persona);
    // Repartida entre las dos tripletas, cada mitad pasaría sin comprobar la
    // diferencia. Por eso vive en una sola prueba.
    jdbc.update(
        """
        INSERT INTO refresh_tokens (id, user_id, token_hash, family_id, family_started_at, expires_at)
        VALUES (gen_random_uuid(), ?, 'hash-de-prueba', gen_random_uuid(), now(), now() + interval '7 days')
        """,
        persona);

    mvc.perform(asignar(persona, rolAcotado)).andExpect(status().isOk());
    assertThat(sesionesVivas(persona)).isEqualTo(1);

    mvc.perform(retirar(persona, rolAcotado)).andExpect(status().isOk());
    assertThat(sesionesVivas(persona)).isZero();

    String motivo =
        jdbc.queryForObject(
            "SELECT revoked_reason FROM refresh_tokens WHERE user_id = ?", String.class, persona);
    // No `ROTACION`: solo esa significa robo, y registrarlo así llenaría el
    // registro de seguridad de incidentes falsos.
    assertThat(motivo).isEqualTo("ACCESO_RETIRADO");
  }

  // ---------------------------------------------------------------------------
  // Auxiliares
  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder asignar(UUID usuario, String rol) {
    return cuerpoDeAsignacion(usuario, "[\"" + rol + "\"]");
  }

  private MockHttpServletRequestBuilder asignarComo(UUID actor, UUID usuario, String rol) {
    return post("/api/v1/users/{id}/roles", usuario)
        .with(comoActor(actor))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"roleIds\":[\"" + rol + "\"]}");
  }

  private MockHttpServletRequestBuilder cuerpoDeAsignacion(UUID usuario, String roles) {
    return post("/api/v1/users/{id}/roles", usuario)
        .with(superadmin())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"roleIds\":" + roles + "}");
  }

  private MockHttpServletRequestBuilder asignarConMembresia(
      UUID usuario, String rol, String membresia) {
    return asignarTodo(usuario, List.of(rol), membresia, null);
  }

  private MockHttpServletRequestBuilder asignarConSuperior(
      UUID usuario, String rol, UUID superior) {
    return asignarTodo(usuario, List.of(rol), null, superior.toString());
  }

  private MockHttpServletRequestBuilder asignarTodo(
      UUID usuario, List<String> roles, String membresia, String superior) {
    String lista =
        roles.stream().map(rol -> "\"" + rol + "\"").reduce((a, b) -> a + "," + b).orElse("");
    String cuerpo =
        "{\"roleIds\":["
            + lista
            + "],\"membershipId\":"
            + (membresia == null ? "null" : "\"" + membresia + "\"")
            + ",\"supervisorId\":"
            + (superior == null ? "null" : "\"" + superior + "\"")
            + "}";
    return post("/api/v1/users/{id}/roles", usuario)
        .with(superadmin())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private MockHttpServletRequestBuilder retirar(UUID usuario, String rol) {
    return post("/api/v1/users/{id}/roles/revocations", usuario)
        .with(superadmin())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"roleIds\":[\"" + rol + "\"]}");
  }

  private MockHttpServletRequestBuilder retirarComo(UUID actor, UUID usuario, String rol) {
    return post("/api/v1/users/{id}/roles/revocations", usuario)
        .with(comoActor(actor))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"roleIds\":[\"" + rol + "\"]}");
  }

  private RequestPostProcessor superadmin() {
    return comoActor(SUPERADMIN);
  }

  /** La autoridad del token solo abre `@PreAuthorize`; los permisos efectivos los da la base. */
  private RequestPostProcessor comoActor(UUID actor) {
    return user(actor.toString()).authorities(() -> "users:assign-roles");
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

  private String crearRol(String codigo, String tipo, String padre) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO roles (id, code, name, role_type, parent_role_id) VALUES (?, ?, ?, ?, ?::uuid)",
        id,
        codigo,
        codigo,
        tipo,
        padre);
    return id.toString();
  }

  private String crearMembresia() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, parent_membership_id, level, color)"
            + " VALUES (?, 'ORO', 'Oro', NULL, 1, 'D4AF37')",
        id);
    return id.toString();
  }

  /**
   * Da a la persona un rol de reserva por JDBC.
   *
   * <p>Desde `RN-SP-023` (24-08-2026) nadie puede quedarse sin ningún rol, de modo que toda prueba
   * que retire el único que tiene chocaría con esa regla en lugar de comprobar lo suyo. Se inserta
   * por JDBC y no por la API a propósito: es preparación de datos, no parte de lo que se prueba.
   */
  private void conRolDeReserva(UUID usuario) {
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid ON CONFLICT DO NOTHING",
        usuario,
        rolDeReserva);
  }

  private List<String> rolesDe(UUID usuario) {
    return jdbc.queryForList(
        "SELECT role_id::text FROM user_roles WHERE user_id = ?", String.class, usuario);
  }

  private int eventosDeCambio(UUID usuario) {
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE entity_id = ? AND entity = 'user_roles'",
            Integer.class,
            usuario);
    return total == null ? 0 : total;
  }

  private int sesionesVivas(UUID usuario) {
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
            Integer.class,
            usuario);
    return total == null ? 0 : total;
  }

  @SuppressWarnings("unused")
  private static int estado(MvcResult resultado) {
    return resultado.getResponse().getStatus();
  }
}
