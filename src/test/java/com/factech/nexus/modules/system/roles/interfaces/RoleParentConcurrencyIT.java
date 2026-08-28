package com.factech.nexus.modules.system.roles.interfaces;

import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import java.util.List;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * `CA-SP-161` — dos reubicaciones simultáneas que formarían un ciclo no llegan a producirlo
 * (`RF-SP-008` · `T-10`).
 *
 * <p><b>Es la prueba que el requerimiento declaró innegociable y que no estaba escrita.</b> La
 * tripleta la daba por hecha en su tabla de tareas y su propia nota de cierre lo desmentía: «el
 * mecanismo sin la prueba es una intención, no una garantía». Al escribirla se vio que además
 * faltaba el mecanismo — ver la nota de abajo.
 *
 * <p><b>Por qué el bloqueo de fila no bastaba.</b> Cargar el rol que se mueve toma un {@code
 * PESSIMISTIC_WRITE} sobre <b>su</b> fila, y eso serializa dos ediciones del mismo rol. Aquí las
 * dos peticiones mueven roles <b>distintos</b>: no comparten ninguna fila, ninguna espera a la
 * otra, y cada una comprueba la ausencia de ciclo contra una jerarquía en la que todavía no está el
 * movimiento de la otra. Las dos pasan su comprobación y las dos escriben.
 *
 * <pre>
 *   Estado inicial:  A → B      C → D
 *   Petición 1:      mover B bajo D    (válido: D no desciende de B)
 *   Petición 2:      mover D bajo B    (válido: B no desciende de D)
 *   Resultado sin serializar:  B → D → B
 * </pre>
 *
 * <p>Un ciclo no es un dato incorrecto más: el recorrido de la descendencia deja de terminar, y con
 * él toda comprobación futura de la jerarquía. El límite de profundidad de la consulta recursiva
 * convierte ese cuelgue en un rechazo, pero la estructura queda corrupta igual.
 *
 * <p><b>Qué se afirma y qué no.</b> No se afirma <b>cuál</b> de las dos peticiones gana: depende de
 * quién tome el bloqueo primero, y fijarlo haría la prueba intermitente. Se afirma lo que debe ser
 * cierto en <b>todos</b> los desenlaces: exactamente una tiene éxito, exactamente una recibe {@code
 * 409}, ninguna produce un fallo del sistema, y la jerarquía final no contiene ciclo.
 *
 * <p><b>Por qué se repite en rondas.</b> La ventana en la que las dos comprobaciones se solapan es
 * estrecha, y una sola ronda podría no acertarla: pasaría en verde con la garantía rota. Repetir
 * multiplica las oportunidades de que el defecto aparezca. Es la limitación honesta de toda prueba
 * de carrera, y por eso {@code RoleHierarchyLockIT} verifica el mecanismo aparte y sin depender de
 * la temporización.
 */
@AutoConfigureMockMvc
class RoleParentConcurrencyIT extends IntegrationTestBase {

  private static final String CONTABILIDAD = "01a02a33-4c00-7003-9c4f-5e7ad1000003";

  /**
   * Cuántas veces se intenta la carrera.
   *
   * <p>Cada ronda son dos peticiones en proceso, de modo que el coste es bajo. Con una sola, la
   * prueba dependería de acertar la ventana a la primera.
   */
  private static final int RONDAS = 8;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void preparar() {
    limpiar();
  }

  @AfterEach
  void devolverElEstadoCompartidoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName("dos reubicaciones que formarían un ciclo: una gana, la otra se rechaza")
  void cicloConcurrente() {
    for (int ronda = 0; ronda < RONDAS; ronda++) {
      UUID a = crearRol("A" + ronda, CONTABILIDAD);
      UUID c = crearRol("C" + ronda, CONTABILIDAD);
      UUID b = crearRol("B" + ronda, a.toString());
      UUID d = crearRol("D" + ronda, c.toString());

      List<Outcome<Integer>> resultados =
          runTogether(List.of(() -> reubicar(b, d), () -> reubicar(d, b)));

      List<Integer> estados = resultados.stream().map(Outcome::value).toList();

      assertThat(resultados)
          .as("ninguna petición debe reventar: un rechazo es una respuesta, no una excepción")
          .allMatch(Outcome::succeeded);

      assertThat(estados)
          .as("ronda %s: una acepta y la otra se rechaza", ronda)
          .containsExactlyInAnyOrder(200, 409);

      // El invariante, que es lo que de verdad importa: mirado desde el estado
      // final, B y D no pueden colgar el uno del otro.
      UUID padreDeB = padreDe(b);
      UUID padreDeD = padreDe(d);
      assertThat(padreDeB.equals(d) && padreDeD.equals(b))
          .as("ronda %s: la jerarquía quedó con un ciclo B ↔ D", ronda)
          .isFalse();

      limpiar();
    }
  }

  /**
   * Devuelve el estado HTTP en lugar de aseverar dentro del hilo: aserta quien lee el resultado.
   */
  private int reubicar(UUID rol, UUID nuevoPadre) throws Exception {
    return mvc.perform(
            patch("/api/v1/roles/{id}/parent", rol)
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentRoleId\":\"" + nuevoPadre + "\"}"))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private RequestPostProcessor administrador() {
    return user(SUPERADMIN.toString()).authorities(() -> "roles:update", () -> "roles:read");
  }

  private UUID padreDe(UUID roleId) {
    return jdbc.queryForObject("SELECT parent_role_id FROM roles WHERE id = ?", UUID.class, roleId);
  }

  /**
   * Un rol con los mismos dos permisos que los demás.
   *
   * <p>Que los cuatro declaren lo mismo es deliberado: así <b>ninguna</b> de las dos reubicaciones
   * se rechaza por contención (`RN-SEG-013`), y el único motivo posible de rechazo es el que esta
   * prueba mide. Con permisos distintos, un `409` no diría cuál de los dos invariantes actuó.
   */
  private UUID crearRol(String sufijo, String padre) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO roles (id, code, name, description, role_type, parent_role_id,
                           status, is_system)
        VALUES (?, ?, ?, 'Rol de prueba.', 'FUNCIONARIO', ?::uuid, 'ACTIVO', false)
        """,
        id,
        "CICLO_" + sufijo,
        "Rol de ciclo " + sufijo,
        padre);

    List<UUID> heredables =
        jdbc.queryForList(
            "SELECT permission_id FROM role_permissions WHERE role_id = ?::uuid"
                + " ORDER BY permission_id LIMIT 2",
            UUID.class,
            CONTABILIDAD);
    heredables.forEach(
        permiso ->
            jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) VALUES (?, ?)",
                id,
                permiso));
    return id;
  }

  private void limpiar() {
    // Las tablas de auditoría NO se vacían: la semilla escribe en ellas y otras
    // clases verifican esas filas. Lo que se acota aquí es lo que esta prueba
    // crea, que son roles no de sistema.
    jdbc.update(
        "DELETE FROM role_permissions WHERE role_id IN"
            + " (SELECT id FROM roles WHERE is_system = false)");
    // Se borran de una sola sentencia y NO se les vacía antes el padre: hacerlo
    // dejaría varios roles sin padre a la vez y `uq_roles_single_root` —que
    // admite una sola raíz— lo rechaza. En un único `DELETE`, las referencias
    // se comprueban al final y para entonces no queda ninguna que romper.
    jdbc.update("DELETE FROM roles WHERE is_system = false");
  }
}
