package com.factech.nexus.modules.system.users.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * El detalle de una persona cuesta <b>un número fijo</b> de sentencias, y no una por rol
 * (`RF-SP-026` · `T-12`).
 *
 * <p><b>Ese número no es el que el plan dice.</b> `plan.md` §166 promete dos y son cuatro; el
 * desglose y por qué la cifra se fija en lugar de corregirse aquí están en {@code
 * COSTE_DEL_DETALLE}. Lo que sí se cumple —y es lo que la tarea existía para proteger— es que no
 * crece con los roles.
 *
 * <h2>Por qué esta prueba y no la confianza</h2>
 *
 * <p>«Dos sentencias con independencia del número de roles» estaba <b>construido y no
 * verificado</b> — lo dice el propio `tasks.md`, que dejó `T-12` pendiente por no haber contador
 * montado. Un `N+1` introducido en una refactorización posterior <b>no rompe ninguna prueba
 * funcional</b>: la respuesta es idéntica, byte a byte, tanto con dos consultas como con veintiuna.
 * Solo cambia lo que cuesta, y eso no lo ve nadie hasta que la tabla crece.
 *
 * <p>Es exactamente la clase de defecto que se cuela por la puerta de atrás: basta que alguien
 * cargue el agregado {@code User} en lugar de proyectar, y Hibernate recorrerá la colección de
 * roles de uno en uno sin que nada se queje.
 *
 * <h2>Se cuenta en el servicio, no en la petición</h2>
 *
 * <p>Lo que `plan.md` §166 promete son las sentencias <b>de este requerimiento</b>. Contarlas desde
 * la petición HTTP mezclaría lo suyo con el registro de peticiones, la resolución de permisos y lo
 * que cada filtro haga: el número dejaría de significar nada y la prueba habría que retocarla cada
 * vez que alguien tocara un filtro. Se invoca el caso de uso directamente, que es donde vive la
 * promesa.
 *
 * <p>El contador es el de Hibernate ({@code generate_statistics}), y se enciende <b>solo para esta
 * clase</b>: es instrumentación con coste, y no tiene por qué pagarla toda la suite. Eso levanta un
 * contexto propio, que es el precio de no ensuciar el compartido.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class UserDetailStatementCountIT extends IntegrationTestBase {

  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000003";

  @Autowired private GetUserService detalle;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory fabrica;

  private UUID conUnRol;
  private UUID conCuatroRoles;

  @BeforeEach
  void preparar() {
    limpiar();
    conUnRol = crearPersona("UnRol", List.of(ADMIN));
    conCuatroRoles = crearPersona("CuatroRoles", List.of(ADMIN, DIRECTOR, AGENTE, MANAGER));
  }

  @AfterEach
  void limpiarDespues() {
    limpiar();
  }

  /**
   * El coste real del detalle, medido el 27-08-2026.
   *
   * <p><b>`plan.md` §166 dice «dos contra PostgreSQL», y son cuatro.</b> Las cuatro, en orden:
   *
   * <ol>
   *   <li>{@code findDetail} — la persona con su membresía.
   *   <li>{@code rolesOf} — sus roles, en una sola consulta para todos.
   *   <li>{@code JpaEffectivePermissions#forUser}, comprobación de existencia — <b>redundante
   *       aquí</b>: {@code findDetail} ya estableció que la persona existe.
   *   <li>{@code JpaEffectivePermissions#forUser}, la unión de permisos de sus roles activos.
   * </ol>
   *
   * <p>De donde se sigue que §188 y §189 del plan tampoco son exactos: afirman que «la resolución
   * de permisos no añade sentencias en el caso común, porque la caché ya tiene la entrada de cada
   * rol», y <b>ese camino no consulta ninguna caché</b> — siempre lanza sus dos consultas.
   *
   * <p>No es un defecto que rompa nada, y por eso la cifra se <b>fija</b> en lugar de corregirse
   * por cuenta propia: bajarla es una decisión de rendimiento con su propio pase. Lo que esta
   * constante garantiza es que no suba sin que alguien se entere.
   */
  private static final long COSTE_DEL_DETALLE = 4;

  @Test
  @DisplayName("cuesta lo MISMO con cuatro roles que con uno: no hay N+1")
  void noHayEneMasUno() {
    /*
     * Esta es la afirmación que el requerimiento existe para proteger, y la
     * única que no depende de cuánto cueste hoy: si alguien sustituye la
     * proyección por el agregado `User`, Hibernate recorrerá la colección de
     * roles de uno en uno y el de cuatro costará más que el de uno.
     *
     * Se pide primero el de cuatro para que un `N+1` no pueda esconderse detrás
     * de una caché que el primero hubiera calentado.
     */
    long conCuatro = sentenciasDe(() -> detalle.detail(conCuatroRoles));
    long conUno = sentenciasDe(() -> detalle.detail(conUnRol));

    assertThat(conCuatro)
        .as("el detalle debe costar lo mismo con cuatro roles que con uno")
        .isEqualTo(conUno);
  }

  @Test
  @DisplayName("y son cuatro sentencias fijas — el plan dice dos, y no lo son")
  void elCosteQuedaFijado() {
    // Ver `COSTE_DEL_DETALLE`: la cifra se fija para que no suba en silencio,
    // no porque cuatro sea el número correcto.
    assertThat(sentenciasDe(() -> detalle.detail(conUnRol)))
        .as("si esto cambia, `plan.md` §166 y §188 de `RF-SP-026` hay que revisarlos con ello")
        .isEqualTo(COSTE_DEL_DETALLE);
  }

  @Test
  @DisplayName("una sola cuando la persona no existe: no se pregunta por lo que no hay")
  void unaSentenciaSiNoExiste() {
    UUID inventada = UUID.randomUUID();

    long sentencias =
        sentenciasDe(
            () ->
                assertThatThrownBy(() -> detalle.detail(inventada))
                    .isInstanceOf(ResourceNotFoundException.class));

    assertThat(sentencias)
        .as("resuelto el `404` en la primera, la segunda consulta no debe llegar a hacerse")
        .isEqualTo(1);
  }

  /**
   * Cuántas sentencias preparó Hibernate durante el trabajo.
   *
   * <p>Se limpia el contador antes y se lee después, en lugar de restar dos lecturas absolutas: las
   * estadísticas son de toda la fábrica de sesiones y otra cosa podría haber contado en medio.
   */
  private long sentenciasDe(Runnable trabajo) {
    Statistics estadisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
    estadisticas.clear();
    trabajo.run();
    return estadisticas.getPrepareStatementCount();
  }

  private UUID crearPersona(String usuario, List<String> roles) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, 'ACTIVO')
        """,
        id,
        usuario,
        // `ck_users_email_normalized` exige el correo ya normalizado: la
        // normalización es del dominio y esta inserción entra por debajo.
        usuario.toLowerCase(java.util.Locale.ROOT) + "@factech.co");
    roles.forEach(
        rol ->
            jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", id, rol));
    return id;
  }

  private void limpiar() {
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM user_roles WHERE user_id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
  }
}
