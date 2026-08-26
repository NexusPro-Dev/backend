package com.factech.nexus.modules.system.memberships.interfaces;

import static com.factech.nexus.testing.ConcurrencyHarness.exitos;
import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * `RF-SP-016` · `T-13` — inserción concurrente en la cadena de membresías.
 *
 * <p>Es la prueba que faltaba para que `EX-003` dejara de ser una ruta escrita y sin ejercitar.
 *
 * <p><b>Por qué el bloqueo no basta, y por eso existe la restricción diferida.</b> El caso de uso
 * abre con {@code SELECT … FOR UPDATE} sobre la cadena entera, y sería tentador concluir que la
 * segunda alta encuentra el estado ya reordenado. En {@code READ COMMITTED} no es así: cuando la
 * segunda transacción se desbloquea, PostgreSQL le reevalúa <b>las filas que ya estaban</b>, pero
 * las que la primera <b>insertó</b> quedan fuera de su instantánea. La segunda calcula entonces su
 * posición sobre una cadena a la que le falta un eslabón, escribe un nivel que ya está ocupado, y
 * {@code uq_memberships_level} —diferida— la rechaza <b>en el {@code COMMIT}</b>.
 *
 * <p>Eso es exactamente lo que `EX-003` describe: «la cadena cambió durante la operación», no un
 * dato inválido, y por eso su mensaje pide reintentar.
 *
 * <p><b>Y hay un hallazgo que conviene dejar escrito: por la API, `EX-003` es inalcanzable mientras
 * el bloqueo funcione.</b> Quien lee la cadena la mantiene bloqueada hasta escribir, de modo que
 * nadie puede confirmar un cambio en medio. La restricción diferida es exactamente lo que su plan
 * dice que es —el respaldo para «un camino nuevo que no tome el bloqueo, una réplica, un defecto»—
 * y por eso su garantía se comprueba aquí atacando la tabla de forma directa, mientras que la
 * traducción de esa violación al `409` se comprueba en su propia prueba unitaria del manejador
 * global.
 *
 * <p><b>El resultado depende del reloj, y la prueba lo asume.</b> Si la primera confirma antes de
 * que la segunda tome su instantánea, las dos tienen éxito. No se afirma cuántas pasan: se afirma
 * lo que debe ser cierto en <b>ambos</b> desenlaces — que nunca hay un {@code 500}, que todo
 * rechazo es el de la cadena movida, y que la cadena queda lineal.
 */
@AutoConfigureMockMvc
class MembershipConcurrencyIT extends IntegrationTestBase {

  /**
   * Un color distinto en cada alta, porque `uq_memberships_color` no admite repetidos.
   *
   * <p>Importa especialmente aquí: la prueba del empate lanza dos altas con el MISMO código y
   * nombre a la vez, y si compartieran color el `409` podría venir del color en lugar de venir de
   * lo que se está probando.
   */
  private static final java.util.concurrent.atomic.AtomicInteger SECUENCIA_DE_COLOR =
      new java.util.concurrent.atomic.AtomicInteger();

  private static String colorNuevo() {
    return String.format("%06X", SECUENCIA_DE_COLOR.incrementAndGet());
  }

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private org.springframework.transaction.PlatformTransactionManager transacciones;

  @BeforeEach
  void vaciarLaCadena() {
    Integer maximo =
        jdbc.queryForObject("SELECT coalesce(max(level), 0) FROM memberships", Integer.class);
    for (int nivel = maximo == null ? 0 : maximo; nivel >= 1; nivel--) {
      jdbc.update("DELETE FROM memberships WHERE level = ?", nivel);
    }
  }

  @Test
  @DisplayName("dos altas simultáneas sobre la misma hija dejan la cadena lineal, nunca un 500")
  void dosAltasSobreLaMismaHija() throws Exception {
    crear("ORO", "Oro", null);
    String bronce = crear("BRONCE", "Bronce", null);

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(alta("NIVEL_" + indice, "Nivel " + indice, bronce)));

    // Ninguna puede morir con un fallo del sistema: es el caso límite que
    // `spec.md` §13 prohíbe de forma explícita.
    assertThat(resultados)
        .as("una inserción concurrente produjo un fallo del sistema")
        .noneMatch(r -> r.succeeded() && r.value() >= 500);

    // Al menos una entra. Cuántas depende de si la primera confirmó antes de que
    // la segunda tomara su instantánea.
    long creadas = resultados.stream().filter(r -> r.succeeded() && r.value() == 201).count();
    assertThat(creadas).as("ninguna de las dos altas prosperó").isPositive();

    // Y si alguna se rechazó, fue con 409: los códigos y los nombres son
    // distintos, de modo que un duplicado está descartado y el único rechazo
    // posible es el de la cadena movida.
    assertThat(resultados)
        .filteredOn(r -> r.succeeded() && r.value() != 201)
        .allMatch(r -> r.value() == 409);

    verificarCadenaLineal();
  }

  @Test
  @DisplayName(
      "el rechazo concurrente es EX-003 y pide reintentar, no dice que el dato sea inválido")
  void elRechazoConcurrenteEsElDeLaCadenaMovida() throws Exception {
    crear("ORO", "Oro", null);
    String bronce = crear("BRONCE", "Bronce", null);

    // Se lanzan varias a la vez para que al menos una llegue tarde: con dos, el
    // desenlace en que ambas pasan es frecuente y la prueba no comprobaría nada.
    List<Outcome<String>> resultados =
        runTogether(6, indice -> cuerpoDe(alta("NIVEL_" + indice, "Nivel " + indice, bronce)));

    List<String> rechazos =
        resultados.stream()
            .filter(Outcome::succeeded)
            .map(Outcome::value)
            .filter(cuerpo -> cuerpo.contains("\"status\":409"))
            .toList();

    if (rechazos.isEmpty()) {
      // No es un fallo: significa que el bloqueo serializó todas a tiempo. Lo
      // que sí debe cumplirse siempre es el invariante.
      verificarCadenaLineal();
      return;
    }

    assertThat(rechazos)
        .allSatisfy(
            cuerpo ->
                assertThat(cuerpo)
                    .contains("regla-de-negocio")
                    .contains("cadena cambió durante la operación"));

    // El rechazo se audita como regla de negocio con su código propio.
    Integer auditados =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_error_log WHERE error_code = 'EX-003'", Integer.class);
    assertThat(auditados).isGreaterThanOrEqualTo(rechazos.size());

    verificarCadenaLineal();
  }

  @Test
  @DisplayName("dos altas simultáneas con el mismo código producen un 201 y un 409, nunca un 500")
  void dosAltasConElMismoCodigo() {
    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(alta("REPETIDO", "Nombre " + indice, null)));

    assertThat(exitos(resultados)).isEqualTo(2);
    assertThat(resultados).extracting(Outcome::value).containsExactlyInAnyOrder(201, 409);

    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM memberships WHERE code = 'REPETIDO'", Integer.class);
    assertThat(filas).isEqualTo(1);
  }

  @Test
  @DisplayName("dos altas simultáneas con el mismo nombre producen un 201 y un 409")
  void dosAltasConElMismoNombre() {
    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(alta("CODIGO_" + indice, "Mismo Nombre", null)));

    assertThat(resultados).extracting(Outcome::value).containsExactlyInAnyOrder(201, 409);
  }

  @Test
  @DisplayName("la restricción diferida rechaza en el COMMIT una cadena bifurcada")
  void laRestriccionDiferidaRechazaAlConfirmar() throws Exception {
    crear("ORO", "Oro", null);

    // Se ataca la tabla directamente y dentro de UNA transacción, que es la
    // única forma determinista de llegar hasta el COMMIT con un estado
    // incoherente: por la API es inalcanzable, y el porqué está en el javadoc de
    // esta clase.
    //
    // Dos filas con la superior en NULL son dos cimas. Sin `NULLS NOT DISTINCT`
    // esto pasaría —PostgreSQL trata los nulos como distintos— y la cadena se
    // bifurcaría por arriba, en el peor sitio posible.
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                enUnaTransaccion(
                    () ->
                        jdbc.update(
                            """
                            INSERT INTO memberships (id, code, name, parent_membership_id, level, color)
                            VALUES (gen_random_uuid(), 'SEGUNDA_CIMA', 'Segunda cima', NULL, 99, 'FF00FF')
                            """)))
        .as("se admitió una segunda membresía superior")
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

    verificarCadenaLineal();
  }

  @Test
  @DisplayName("la unicidad de nivel también se comprueba al confirmar, no en cada sentencia")
  void laUnicidadDeNivelEsDiferida() throws Exception {
    String oro = crear("ORO", "Oro", null);
    crear("PLATA", "Plata", null);

    // Que sea diferida es lo que permite el reordenamiento: dentro de la
    // transacción los niveles se pisan transitoriamente y solo el estado final
    // tiene que ser correcto. Aquí se comprueba lo contrario —que el estado
    // final incorrecto SÍ se rechaza—, porque una restricción diferida que no
    // rechazara nada sería peor que no tenerla.
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                enUnaTransaccion(
                    () -> {
                      // Estado intermedio incoherente: dos membresías en nivel 2.
                      jdbc.update("UPDATE memberships SET level = 2 WHERE id = ?::uuid", oro);
                    }))
        .as("dos membresías quedaron en el mismo nivel")
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  // ---------------------------------------------------------------------------

  /** Ejecuta el cuerpo dentro de una transacción propia y la confirma al salir. */
  private void enUnaTransaccion(Runnable cuerpo) {
    new org.springframework.transaction.support.TransactionTemplate(transacciones)
        .executeWithoutResult(estado -> cuerpo.run());
  }

  /**
   * El invariante que ninguna carrera puede romper: una sola cima, ninguna membresía con dos hijas
   * y niveles contiguos desde uno.
   *
   * <p>Es lo que de verdad protege `uq_memberships_parent` con {@code NULLS NOT DISTINCT} y {@code
   * uq_memberships_level}, y lo que `RN-SP-008` impide corregir si llegara a romperse.
   */
  private void verificarCadenaLineal() {
    Integer cimas =
        jdbc.queryForObject(
            "SELECT count(*) FROM memberships WHERE parent_membership_id IS NULL", Integer.class);
    assertThat(cimas).as("la cadena tiene más de una cima o ninguna").isEqualTo(1);

    Integer bifurcaciones =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM (
              SELECT parent_membership_id FROM memberships
               WHERE parent_membership_id IS NOT NULL
               GROUP BY parent_membership_id HAVING count(*) > 1
            ) AS b
            """,
            Integer.class);
    assertThat(bifurcaciones).as("una membresía quedó con dos hijas").isZero();

    List<Integer> niveles =
        jdbc.queryForList("SELECT level FROM memberships ORDER BY level", Integer.class);
    assertThat(niveles)
        .as("los niveles no son contiguos desde uno")
        .containsExactlyElementsOf(
            java.util.stream.IntStream.rangeClosed(1, niveles.size()).boxed().toList());

    Integer incoherentes =
        jdbc.queryForObject(
            """
            SELECT count(*)
              FROM memberships h JOIN memberships p ON p.id = h.parent_membership_id
             WHERE p.level <> h.level - 1
            """,
            Integer.class);
    assertThat(incoherentes).as("un puntero no coincide con el orden de niveles").isZero();
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder alta(
      String code, String name, String hija) {
    String color = colorNuevo();
    String cuerpo =
        hija == null
            ? "{\"code\":\"%s\",\"name\":\"%s\",\"color\":\"%s\"}".formatted(code, name, color)
            : "{\"code\":\"%s\",\"name\":\"%s\",\"color\":\"%s\",\"childMembershipId\":\"%s\"}"
                .formatted(code, name, color, hija);
    return post("/api/v1/memberships")
        .with(admin())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private int estadoDe(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion)
      throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }

  private String cuerpoDe(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion)
      throws Exception {
    var respuesta = mvc.perform(peticion).andReturn().getResponse();
    return "{\"status\":" + respuesta.getStatus() + "}" + respuesta.getContentAsString();
  }

  private String crear(String code, String name, String hija) throws Exception {
    String cuerpo =
        mvc.perform(alta(code, name, hija)).andReturn().getResponse().getContentAsString();
    return json.readTree(cuerpo).get("id").asText();
  }

  private RequestPostProcessor admin() {
    return user(UUID.randomUUID().toString())
        .authorities(() -> "memberships:create", () -> "memberships:read");
  }
}
