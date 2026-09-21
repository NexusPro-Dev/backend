package com.factech.nexus.modules.system.brokers.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Los indicadores de la red comercial (`RF-SP-058`).
 *
 * <p><b>La red de prueba está armada para que cada número sea distinto de los demás.</b> Con
 * cantidades iguales, un error de acumulación —sumar de más, sumar de menos, sumar dos veces—
 * seguiría dando un número plausible en algún nodo.
 *
 * <pre>
 * MANAGER  rlopez   own: 1 cuenta  (1 pending)
 *   DIRECTOR amartinez own: 2 cuentas (1 ftd, 1 pending)
 *     AGENTE  lgarcia   own: 3 cuentas (2 ftd de UN cliente, 1 pending)
 *   AGENTE   pnieto    own: 0
 * FUERA: un consumidor sin superior, con 1 cuenta → `unassigned`
 * FUERA: la cuenta propia del director            → NO cuenta en ningún sitio
 * </pre>
 *
 * <p><b>La prueba que sostiene el requerimiento es {@code losNumerosCuadran}</b>: no comprueba un
 * valor sino <b>una igualdad entre dos endpoints</b>, y es lo único que detecta un doble conteo o
 * una omisión — los dos errores que devuelven números plausibles.
 */
@AutoConfigureMockMvc
class NetworkIndicatorsIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000003";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";
  private static final String CLIENTE = "01a02a33-4c00-7008-9c4f-5e7ad1000008";

  private static final String RUTA = "/api/v1/broker-accounts/indicators";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private UUID manager;
  private UUID director;
  private UUID agente;
  private UUID agenteVacio;
  private UUID broker;

  @BeforeEach
  void preparar() {
    jdbc.update("DELETE FROM user_brokers");
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM client_sellers");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM user_roles");
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        SUPERADMIN,
        SUPERADMIN_ROL);

    broker = brokerAsegurado("01a081f0-6000-7102-9c4f-5e7adb000002", "EXNOVA");

    manager = persona("rlopez", MANAGER);
    director = persona("amartinez", DIRECTOR);
    agente = persona("lgarcia", AGENTE);
    agenteVacio = persona("pnieto", AGENTE);

    reportar(director, manager);
    reportar(agente, director);
    reportar(agenteVacio, manager);

    // Del manager cuelga un cliente directo: la regla es ESTRUCTURAL y no exige
    // que un cliente cuelgue de un agente (`FA-004`). Y «cuelga» es, desde el
    // 18-09-2026, la fila REGISTRO de `client_sellers` (`RN-SP-048` (5)): los
    // clientes NO están en `user_supervisors`, y es lo que hace de esta suite
    // la prueba de que los conteos leen la tabla nueva (`CA-SP-694`).
    UUID delManager = persona("c-manager", CLIENTE);
    registrar(delManager, manager);
    cuenta(delManager, "M-1", "REGISTER");

    UUID delDirector = persona("c-director", CLIENTE);
    registrar(delDirector, director);
    cuenta(delDirector, "D-1", "FIRST_DEPOSIT");
    cuenta(delDirector, "D-2", "REGISTER");

    // UN cliente con DOS cuentas depositadas: 2 en `ftd` y 1 en `consumers`.
    UUID delAgente = persona("c-agente", CLIENTE);
    registrar(delAgente, agente);
    cuenta(delAgente, "A-1", "FIRST_DEPOSIT");
    cuenta(delAgente, "A-2", "FIRST_DEPOSIT");

    UUID otroDelAgente = persona("c-agente-2", CLIENTE);
    registrar(otroDelAgente, agente);
    cuenta(otroDelAgente, "A-3", "REGISTER");

    // Un vínculo HOTLINK sobre el cliente del manager, a nombre del agente: NO
    // suma en el agente (`CA-SP-694`) — el indicador mide la captación, y captó
    // quien registró.
    vincularPorHotlink(delManager, agente);

    // La cuenta PROPIA del director: no es una captación y no cuenta en ningún
    // indicador (`RN-SP-048`).
    cuenta(director, "V-1", "FIRST_DEPOSIT");

    // Un consumidor sin superior: no entra en ningún nodo y por eso existe
    // `unassigned` — sin él, el árbol sumaría menos que el listado global.
    UUID huerfano = persona("c-huerfano", CLIENTE);
    cuenta(huerfano, "H-1", "REGISTER");
  }

  /**
   * Deja {@code user_brokers} <b>vacía</b>, y no es cortesía: es lo que impide romper la suite.
   *
   * <p>{@code fk_user_brokers_user} es {@code RESTRICT}, de modo que una fila que sobreviva a esta
   * clase hace fallar el {@code DELETE FROM users} de <b>todas</b> las que se ejecuten después —y
   * el error señala a la clase equivocada—. <b>Limpiar solo en {@code @BeforeEach} no basta</b>:
   * protege a esta clase y deja el estropicio para las demás.
   */
  @AfterEach
  void vaciar() {
    jdbc.update("DELETE FROM user_brokers");
  }

  // ---------------------------------------------------------------------------
  // La aritmética
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-658`, `CA-SP-659` — cada `own` es lo directo y cada `network` acumula")
  void laSumaPorNiveles() throws Exception {
    JsonNode raiz = arbol(null);
    JsonNode nodoManager = raiz.get("nodes").get(0);

    // El manager: 1 cuenta propia directa.
    assertThat(nodoManager.get("user").get("username").asText()).isEqualTo("rlopez");
    assertThat(nodoManager.get("own").get("accounts").asInt()).isEqualTo(1);
    assertThat(nodoManager.get("own").get("ftd").asInt()).isZero();

    JsonNode nodoDirector = hijo(nodoManager, "amartinez");
    JsonNode nodoAgente = hijo(nodoDirector, "lgarcia");

    // El agente: 3 cuentas, 2 con depósito. Sin hijos, `network` == `own`.
    assertThat(nodoAgente.get("own").get("accounts").asInt()).isEqualTo(3);
    assertThat(nodoAgente.get("own").get("ftd").asInt()).isEqualTo(2);
    assertThat(nodoAgente.get("network").get("accounts").asInt()).isEqualTo(3);
    assertThat(nodoAgente.get("network").get("ftd").asInt()).isEqualTo(2);

    // El director: los suyos (2) MÁS los del agente (3).
    assertThat(nodoDirector.get("own").get("accounts").asInt()).isEqualTo(2);
    assertThat(nodoDirector.get("network").get("accounts").asInt()).isEqualTo(5);
    assertThat(nodoDirector.get("network").get("ftd").asInt()).isEqualTo(3);

    // El manager: el suyo (1) MÁS la red del director (5). El agente vacío no
    // aporta, y aparece igual como hoja.
    assertThat(nodoManager.get("network").get("accounts").asInt()).isEqualTo(6);
    assertThat(nodoManager.get("network").get("ftd").asInt()).isEqualTo(3);
    assertThat(hijo(nodoManager, "pnieto").get("network").get("accounts").asInt()).isZero();
  }

  @Test
  @DisplayName("`CA-SP-660` — la unidad es LA CUENTA: dos cuentas de un cliente suman 2, no 1")
  void laUnidadEsLaCuenta() throws Exception {
    JsonNode agenteNodo = hijo(hijo(arbol(null).get("nodes").get(0), "amartinez"), "lgarcia");

    // `c-agente` tiene DOS cuentas depositadas y `c-agente-2` una pendiente.
    assertThat(agenteNodo.get("own").get("ftd").asInt()).isEqualTo(2);
    // ...y son DOS personas, no tres: `accounts` y `consumers` miden cosas
    // distintas y por eso van los dos.
    assertThat(agenteNodo.get("own").get("consumers").asInt()).isEqualTo(2);
  }

  @Test
  @DisplayName("`CA-SP-661` — la cuenta PROPIA de un vendedor no cuenta en ningún indicador")
  void laCuentaDelVendedorNoCuenta() throws Exception {
    JsonNode raiz = arbol(null);

    // El director tiene una cuenta suya en FIRST_DEPOSIT. Si contara, su `own`
    // sería 3 y el `ftd` de todo el árbol subiría en uno.
    JsonNode nodoDirector = hijo(raiz.get("nodes").get(0), "amartinez");
    assertThat(nodoDirector.get("own").get("accounts").asInt()).isEqualTo(2);
    assertThat(raiz.get("totals").get("ftd").asInt()).isEqualTo(3);

    // Y tampoco cae en `unassigned`: no es de un consumidor, luego no es de
    // nadie a efectos de este indicador.
    assertThat(raiz.get("unassigned").get("accounts").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-SP-662` — los consumidores NO son nodos del árbol")
  void losConsumidoresNoSonNodos() throws Exception {
    String cuerpo = json.writeValueAsString(arbol(null));

    // Aportan el número y no aparecen: publicarlos convertiría el indicador en
    // el listado de clientes de la empresa.
    assertThat(cuerpo).doesNotContain("c-manager", "c-director", "c-agente", "c-huerfano");
    assertThat(cuerpo).contains("rlopez", "amartinez", "lgarcia", "pnieto");
  }

  @Test
  @DisplayName("`CA-SP-663` — la conversión es NULA sin cuentas, y se RECALCULA, no se promedia")
  void laConversion() throws Exception {
    JsonNode raiz = arbol(null);
    JsonNode nodoManager = raiz.get("nodes").get(0);

    // Nula y no cero: cero se leería como «nadie convirtió».
    assertThat(hijo(nodoManager, "pnieto").get("network").get("conversion").isNull()).isTrue();

    // El agente convierte 2/3 y el director 1/2 en lo suyo. La red del director
    // es 3/5 = 0.6 — NO el promedio de 0.6667 y 0.5, que daría 0.5833.
    JsonNode nodoDirector = hijo(nodoManager, "amartinez");
    assertThat(nodoDirector.get("network").get("conversion").asDouble()).isEqualTo(0.6);
    assertThat(hijo(nodoDirector, "lgarcia").get("own").get("conversion").asDouble())
        .isEqualTo(0.6667);
  }

  // ---------------------------------------------------------------------------
  // La igualdad que sostiene el requerimiento
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-664` — `totals` + `unassigned` CUADRA con el total de `RF-SP-057`")
  void losNumerosCuadran() throws Exception {
    JsonNode raiz = arbol(null);

    int enElArbol = raiz.get("totals").get("accounts").asInt();
    int sinAtribuir = raiz.get("unassigned").get("accounts").asInt();

    // El listado global cuenta TODAS las cuentas, incluida la del vendedor, de
    // modo que se descuenta: es la única que el indicador excluye por regla.
    int todas =
        json.readTree(
                mvc.perform(get("/api/v1/broker-accounts").with(administrador()))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("totalElements")
            .asInt();

    int deVendedores =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM user_brokers ub
             WHERE NOT EXISTS (SELECT 1 FROM user_roles ur
                                WHERE ur.user_id = ub.user_id AND ur.role_type = 'CONSUMIDOR')
            """,
            Integer.class);

    // ESTA IGUALDAD ES EL REQUISITO. Cualquier doble conteo o cualquier omisión
    // la rompe, y ninguna otra prueba lo notaría: los dos errores devuelven
    // números plausibles.
    assertThat(enElArbol + sinAtribuir).isEqualTo(todas - deVendedores);
  }

  // ---------------------------------------------------------------------------
  // Rama, exclusiones y permiso
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-665` — `rootId` da la rama con la persona de raíz y los MISMOS números")
  void laRama() throws Exception {
    JsonNode completo = hijo(arbol(null).get("nodes").get(0), "amartinez");
    JsonNode rama = arbol(director);

    assertThat(rama.get("nodes")).hasSize(1);
    assertThat(rama.get("nodes").get(0).get("user").get("username").asText())
        .isEqualTo("amartinez");
    // Los mismos números que dentro del árbol completo: la rama no recalcula
    // nada distinto.
    assertThat(rama.get("nodes").get(0).get("network").get("accounts").asInt())
        .isEqualTo(completo.get("network").get("accounts").asInt());
    // `unassigned` se omite: dentro de una rama no significa nada.
    assertThat(rama.get("unassigned").isNull()).isTrue();

    mvc.perform(get(RUTA + "?rootId=" + UUID.randomUUID()).with(administrador()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("`CA-SP-666` — un ELIMINADO no aporta números y su rama NO se corta")
  void elEliminadoNoCortaLaRama() throws Exception {
    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", director);

    JsonNode arbol = arbol(null);

    // El director desaparece del árbol y sus dos cuentas dejan de contar...
    assertThat(json.writeValueAsString(arbol)).doesNotContain("amartinez");

    // ...pero el agente que colgaba de él NO desaparece: pasa a ser RAÍZ,
    // porque su superior ya no es fuerza comercial vigente. Sus tres cuentas
    // siguen contando, y eso es lo que distingue filtrar de podar.
    assertThat(arbol.get("totals").get("accounts").asInt()).isEqualTo(4);
    assertThat(raiz(arbol, "rlopez").get("network").get("accounts").asInt()).isEqualTo(1);
    assertThat(raiz(arbol, "lgarcia").get("network").get("accounts").asInt()).isEqualTo(3);
  }

  @Test
  @DisplayName("`CA-SP-667` — quien DEJÓ la estructura no aparece ni aporta")
  void elQueSeFue() throws Exception {
    jdbc.update(
        "UPDATE user_supervisors SET ended_at = now() WHERE user_id = ? AND supervisor_id = ?",
        agente,
        director);

    JsonNode arbol = arbol(null);
    JsonNode nodoDirector = hijo(raiz(arbol, "rlopez"), "amartinez");

    // El agente deja de colgar del director: su red baja de 5 a 2.
    assertThat(nodoDirector.get("network").get("accounts").asInt()).isEqualTo(2);
    // Y él pasa a ser raíz, porque no tiene superior vigente. El total NO
    // cambia: la estructura se reorganiza, las cuentas no se pierden.
    assertThat(arbol.get("totals").get("accounts").asInt()).isEqualTo(6);
    assertThat(raiz(arbol, "lgarcia").get("network").get("accounts").asInt()).isEqualTo(3);
  }

  @Test
  @DisplayName("`CA-SP-668` — sin `broker-accounts:read`, 403")
  void sinPermiso() throws Exception {
    mvc.perform(get(RUTA).with(user(manager.toString()))).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("sin fuerza comercial devuelve el árbol vacío, con lo no atribuido aparte")
  void sinFuerzaComercial() throws Exception {
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_roles WHERE role_type = 'VENDEDOR'");

    mvc.perform(get(RUTA).with(administrador()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nodes.length()").value(0))
        .andExpect(jsonPath("$.totals.accounts").value(0))
        // Todo lo de los consumidores pasa a no atribuido: 1+2+3+1 = 7.
        .andExpect(jsonPath("$.unassigned.accounts").value(7));
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  private JsonNode arbol(UUID rootId) throws Exception {
    String url = rootId == null ? RUTA : RUTA + "?rootId=" + rootId;
    return json.readTree(
        mvc.perform(get(url).with(administrador()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  /**
   * La raíz con ese nombre de usuario.
   *
   * <p><b>Por nombre y no por índice</b>: las raíces se ordenan por nombre de usuario, de modo que
   * quitar a alguien de en medio cambia <b>cuántas</b> raíces hay y en qué orden. Buscar por
   * posición haría que estas pruebas fallaran por el motivo equivocado.
   */
  private static JsonNode raiz(JsonNode arbol, String username) {
    for (JsonNode candidato : arbol.get("nodes")) {
      if (username.equals(candidato.get("user").get("username").asText())) {
        return candidato;
      }
    }
    throw new AssertionError("«" + username + "» no es una raíz del árbol");
  }

  /** El hijo con ese nombre de usuario. Falla con un mensaje útil si no está. */
  private static JsonNode hijo(JsonNode nodo, String username) {
    for (JsonNode candidato : nodo.get("children")) {
      if (username.equals(candidato.get("user").get("username").asText())) {
        return candidato;
      }
    }
    throw new AssertionError(
        "no cuelga «" + username + "» de «" + nodo.get("user").get("username").asText() + "»");
  }

  private static RequestPostProcessor administrador() {
    return user(SUPERADMIN.toString())
        .authorities(() -> "broker-accounts:read", () -> "broker-accounts:read-indicators");
  }

  private UUID persona(String username, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        id,
        rol);
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

  /** El cliente y su principal: la fila REGISTRO de `client_sellers` (`RN-SP-049`). */
  private void registrar(UUID cliente, UUID vendedor) {
    jdbc.update(
        """
        INSERT INTO client_sellers (client_id, seller_id, origin, first_movement_id, created_at)
        VALUES (?, ?, 'REGISTRO', NULL, now())
        """,
        cliente,
        vendedor);
  }

  private void vincularPorHotlink(UUID cliente, UUID vendedor) {
    jdbc.update(
        """
        INSERT INTO client_sellers (client_id, seller_id, origin, first_movement_id, created_at)
        VALUES (?, ?, 'HOTLINK', NULL, now())
        """,
        cliente,
        vendedor);
  }

  private void cuenta(UUID persona, String numero, String estado) {
    jdbc.update(
        """
        INSERT INTO user_brokers (id, user_id, broker_id, external_id, status)
        VALUES (gen_random_uuid(), ?, ?, ?, ?)
        """,
        persona,
        broker,
        numero,
        estado);
  }

  private UUID brokerAsegurado(String id, String nombre) {
    jdbc.update(
        "INSERT INTO brokers (id, name) VALUES (CAST(? AS uuid), ?) ON CONFLICT DO NOTHING",
        id,
        nombre);
    return jdbc.queryForObject("SELECT id FROM brokers WHERE name = ?", UUID.class, nombre);
  }
}
