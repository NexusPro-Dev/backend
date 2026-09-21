package com.factech.nexus.modules.system.brokers.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
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
 * Todas las cuentas de broker, para quien administra (`RF-SP-057`).
 *
 * <p><b>La cadena tiene CUATRO niveles y no tres</b>, al revés que {@code BrokerAccountsIT}: con
 * tres, «la red entera» y «dos niveles» dan el mismo resultado, y la prueba no distinguiría una
 * recursiva de una consulta que baja un escalón de más.
 *
 * <p><b>La prueba que sostiene el requerimiento es la de la rama que NO se corta</b> —una persona
 * eliminada con subordinados vivos—, porque es la única que distingue <b>filtrar</b> de
 * <b>podar</b>: meter el {@code deleted_at} dentro de la recursiva devuelve un resultado plausible,
 * más pequeño, y nadie lo nota.
 */
@AutoConfigureMockMvc
class AllBrokerAccountsIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000003";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";
  private static final String CLIENTE = "01a02a33-4c00-7008-9c4f-5e7ad1000008";

  private static final String RUTA = "/api/v1/broker-accounts";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  /** {@code jefe} ← {@code medio} ← {@code base} ← {@code nieto}. Cuatro niveles. */
  private UUID jefe;

  private UUID medio;
  private UUID base;
  private UUID nieto;
  private UUID ajeno;

  private UUID brokerA;
  private UUID brokerB;

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

    jefe = crearPersona("rlopez", "Ramón", MANAGER);
    medio = crearPersona("amartinez", "Ana", DIRECTOR);
    base = crearPersona("lgarcia", "Lucía", AGENTE);
    nieto = crearPersona("pnieto", "Pedro", AGENTE);
    ajeno = crearPersona("zruiz", "Zoe", AGENTE);

    reportar(medio, jefe);
    reportar(base, medio);
    reportar(nieto, base);

    brokerA = brokerAsegurado("01a081f0-6000-7102-9c4f-5e7adb000002", "EXNOVA");
    brokerB = brokerAsegurado("01a081f0-6000-7101-9c4f-5e7adb000001", "IQOPTION");

    declarar(jefe, brokerA, "10000001");
    declarar(medio, brokerA, "20000001");
    declarar(base, brokerB, "30000001");
    declarar(nieto, brokerA, "40000001");
    declarar(ajeno, brokerB, "50000001");
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
  // El listado y su permiso
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-646` — sin filtros devuelve TODAS las cuentas del sistema")
  void todasLasCuentas() throws Exception {
    mvc.perform(get(RUTA).with(administrador()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(5));
  }

  @Test
  @DisplayName("`CA-SP-647` — sin el permiso es 403, y NO el 404 de `RF-SP-055`")
  void sinPermisoEs403() throws Exception {
    // La diferencia es deliberada: alli el actor es un vendedor cualquiera y un
    // 403 le dejaria recorrer identificadores; aqui no hay recurso ajeno que
    // pedir, porque quien pasa ya lo ve todo.
    mvc.perform(get(RUTA).with(user(jefe.toString()))).andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // La red, que es lo nuevo (`RN-SP-047`)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-648` — `supervisorId` devuelve la red ENTERA: el bisnieto también sale")
  void laRedEnProfundidad() throws Exception {
    // Cuatro niveles: si la consulta bajara uno o dos escalones en vez de
    // recorrer, saldrían 1 o 2 y la prueba lo diría. Con tres niveles, «entera»
    // y «dos escalones» son indistinguibles.
    mvc.perform(get(RUTA + "?supervisorId=" + jefe).with(administrador()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(
            jsonPath("$.content[?(@.user.username == 'pnieto')]")
                .value(org.hamcrest.Matchers.hasSize(1)));
  }

  @Test
  @DisplayName("`CA-SP-649` — la red NO incluye a la propia raíz")
  void laRaizNoSeIncluye() throws Exception {
    // «Su red» son los suyos. Las de él se piden con `userId`, y por eso los dos
    // filtros se combinan en vez de excluirse.
    mvc.perform(get(RUTA + "?supervisorId=" + jefe).with(administrador()))
        .andExpect(
            jsonPath("$.content[?(@.user.username == 'rlopez')]")
                .value(org.hamcrest.Matchers.empty()));
  }

  @Test
  @DisplayName(
      "`CA-SP-648` precisado — los clientes REGISTRO de cada nodo entran en la red, en la hoja")
  void losClientesEntranEnLaHoja() throws Exception {
    // Desde el 18-09-2026 el cliente no está en `user_supervisors` (`RN-SP-028`
    // revertida): la recursiva recorre solo fuerza comercial y los clientes se
    // unen después, desde `client_sellers`. Dos clientes: uno del bisnieto —el
    // fondo de la rama— y otro de la propia raíz, que también es su red aunque
    // sus cuentas propias no lo sean (`CA-SP-649`).
    UUID delBisnieto = crearPersona("c-bisnieto", "Carla", CLIENTE);
    registrar(delBisnieto, nieto);
    declarar(delBisnieto, brokerA, "60000001");
    UUID delJefe = crearPersona("c-jefe", "Cora", CLIENTE);
    registrar(delJefe, jefe);
    declarar(delJefe, brokerB, "60000002");
    // Y un vínculo HOTLINK del ajeno sobre el cliente de la raíz: NO lo trae a
    // la red del ajeno, porque no es su principal.
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin) VALUES (?, ?, 'HOTLINK')",
        delJefe,
        ajeno);

    mvc.perform(get(RUTA + "?supervisorId=" + jefe).with(administrador()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(
            jsonPath("$.content[?(@.user.username == 'c-bisnieto')]")
                .value(org.hamcrest.Matchers.hasSize(1)))
        .andExpect(
            jsonPath("$.content[?(@.user.username == 'c-jefe')]")
                .value(org.hamcrest.Matchers.hasSize(1)))
        .andExpect(
            jsonPath("$.content[?(@.user.username == 'rlopez')]")
                .value(org.hamcrest.Matchers.empty()));

    mvc.perform(get(RUTA + "?supervisorId=" + ajeno).with(administrador()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("`CA-SP-650` — quien DEJÓ la estructura no sale, ni los que colgaban de él")
  void elQueSeFueSeLlevaSuRama() throws Exception {
    jdbc.update(
        "UPDATE user_supervisors SET ended_at = now() WHERE user_id = ? AND supervisor_id = ?",
        medio,
        jefe);

    // `ended_at IS NULL` tiene que estar en LOS DOS brazos de la recursiva:
    // omitirlo en el recursivo haría descender por la estructura de ayer sin
    // que nada fallara.
    mvc.perform(get(RUTA + "?supervisorId=" + jefe).with(administrador()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName(
      "`CA-SP-651` — el ELIMINADO no sale y su rama NO se corta: los suyos siguen saliendo")
  void laRamaNoSeCorta() throws Exception {
    // ESTA es la que distingue filtrar de podar. Con el `deleted_at` dentro de
    // la recursiva, `base` dejaría de expandirse y `nieto` desaparecería del
    // resultado aunque siga vivo y colgando — un resultado plausible, más
    // pequeño, que nadie relaciona con esta línea.
    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", base);

    mvc.perform(get(RUTA + "?supervisorId=" + jefe).with(administrador()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(
            jsonPath("$.content[?(@.user.username == 'lgarcia')]")
                .value(org.hamcrest.Matchers.empty()))
        .andExpect(
            jsonPath("$.content[?(@.user.username == 'pnieto')]")
                .value(org.hamcrest.Matchers.hasSize(1)));
  }

  // ---------------------------------------------------------------------------
  // Los demás filtros
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-652` — `userId` acota, y se COMBINA con `supervisorId`")
  void personaYRedSeCombinan() throws Exception {
    mvc.perform(get(RUTA + "?userId=" + nieto).with(administrador()))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].accountId").value("40000001"));

    // «De la red de este vendedor, las cuentas de esta persona»: la comprobación
    // natural al revisar un caso concreto.
    mvc.perform(get(RUTA + "?supervisorId=" + jefe + "&userId=" + nieto).with(administrador()))
        .andExpect(jsonPath("$.totalElements").value(1));

    // Y el ajeno no está en esa red, aunque exista y tenga cuentas.
    mvc.perform(get(RUTA + "?supervisorId=" + jefe + "&userId=" + ajeno).with(administrador()))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("`CA-SP-653` — `search` encuentra por número de cuenta y por nombre de usuario")
  void busquedaPorTexto() throws Exception {
    mvc.perform(get(RUTA + "?search=40000").with(administrador()))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].user.username").value("pnieto"));

    // Sin distinguir mayúsculas, como el listado de personas.
    mvc.perform(get(RUTA + "?search=RLOPEZ").with(administrador()))
        .andExpect(jsonPath("$.totalElements").value(1));

    // Y sin acentos: «Ramón» se encuentra escribiendo «ramon».
    mvc.perform(get(RUTA + "?search=ramon").with(administrador()))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].user.username").value("rlopez"));
  }

  @Test
  @DisplayName("un `%` en la búsqueda se escapa: no devuelve la tabla entera")
  void elComodinSeEscapa() throws Exception {
    // El valor va enlazado, de modo que esto no es inyección: es que sin
    // escapar, `%` seria el comodín de LIKE y devolvería las cinco.
    mvc.perform(get(RUTA + "?search=%25").with(administrador()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("`CA-SP-654` — el rango de fechas es SEMIABIERTO: incluye `from` y excluye `to`")
  void rangoSemiabierto() throws Exception {
    java.time.OffsetDateTime alta =
        jdbc.queryForObject(
            "SELECT created_at FROM user_brokers WHERE external_id = '40000001'",
            java.time.OffsetDateTime.class);

    // Con `.param` y no dentro de la URL: `MockMvc` no descodifica el porciento
    // de la cadena de consulta, y los dos puntos de la hora llegarían como
    // `%3A` al conversor. Es como lo pasa `AuditQueryIT`.
    //
    // El instante exacto del alta SÍ entra por `from`...
    mvc.perform(
            get(RUTA)
                .param("userId", nieto.toString())
                .param("from", alta.toString())
                .with(administrador()))
        .andExpect(jsonPath("$.totalElements").value(1));

    // ...y NO entra por `to`, que es el borde donde se equivoca quien lo
    // escribe: con `<=`, dos rangos consecutivos contarían dos veces lo que
    // cayera justo ahí.
    mvc.perform(
            get(RUTA)
                .param("userId", nieto.toString())
                .param("to", alta.toString())
                .with(administrador()))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("`CA-SP-655` — estado inválido y rango imposible son 400; lo inexistente, vacío")
  void erroresYVacios() throws Exception {
    mvc.perform(get(RUTA + "?status=depositado").with(administrador()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("status"));

    // Un rango imposible NO es un rango sin resultados: devolverlo vacío haría
    // creer que no hubo altas en ese periodo.
    mvc.perform(
            get(RUTA + "?from=2026-09-10T00:00:00Z&to=2026-09-01T00:00:00Z").with(administrador()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("to"));

    // En cambio, un identificador que no designa nada es una pregunta legítima
    // con respuesta vacía.
    for (String filtro : new String[] {"supervisorId", "userId", "brokerId"}) {
      mvc.perform(get(RUTA + "?" + filtro + "=" + UUID.randomUUID()).with(administrador()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalElements").value(0));
    }
  }

  @Test
  @DisplayName("los filtros por estado y por broker acotan, y el total cuenta lo filtrado")
  void estadoYBroker() throws Exception {
    // Sembrado por SQL: no hay API que mueva el estado (`RF-SP-054`).
    jdbc.update("UPDATE user_brokers SET status = 'FIRST_DEPOSIT' WHERE external_id = '30000001'");

    mvc.perform(get(RUTA + "?status=FIRST_DEPOSIT").with(administrador()))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].accountId").value("30000001"));

    mvc.perform(get(RUTA + "?brokerId=" + brokerB).with(administrador()))
        .andExpect(jsonPath("$.totalElements").value(2));

    mvc.perform(get(RUTA + "?brokerId=" + brokerA + "&supervisorId=" + jefe).with(administrador()))
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  @DisplayName("`CA-SP-656` — dos páginas seguidas no repiten ni pierden filas")
  void paginacionEstable() throws Exception {
    String primera =
        mvc.perform(get(RUTA + "?page=0&size=2").with(administrador()))
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String segunda =
        mvc.perform(get(RUTA + "?page=1&size=2").with(administrador()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Orden por nombre de usuario: amartinez, lgarcia | pnieto, rlopez | zruiz.
    assertThat(primera).contains("amartinez", "lgarcia").doesNotContain("pnieto");
    assertThat(segunda).contains("pnieto", "rlopez").doesNotContain("lgarcia");
  }

  @Test
  @DisplayName("`CA-SP-657` — la fila es la MISMA que la del listado del equipo, campo por campo")
  void laFilaEsLaMisma() throws Exception {
    String global =
        mvc.perform(get(RUTA + "?userId=" + base).with(administrador()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // `medio` es el superior directo de `base`, de modo que su listado de equipo
    // trae exactamente esa cuenta.
    String equipo =
        mvc.perform(
                get("/api/v1/users/me/team/broker-accounts?brokerId=" + brokerB)
                    .with(user(medio.toString())))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Que las dos coincidan es CONTRATO: el frontend pinta las dos pantallas con
    // un solo componente.
    assertThat(fila(global)).isEqualTo(fila(equipo));
  }

  // ---------------------------------------------------------------------------
  // El resumen (10-09-2026)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-669`, `CA-SP-670` — `accounts.total` es `totalElements`, y el desglose suma")
  void elResumenCuadraConLaPagina() throws Exception {
    jdbc.update("UPDATE user_brokers SET status = 'FIRST_DEPOSIT' WHERE external_id = '30000001'");

    mvc.perform(get(RUTA).with(administrador()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(5))
        // El mismo entero, y sale de la MISMA consulta: no pueden discrepar.
        .andExpect(jsonPath("$.summary.accounts.total").value(5))
        .andExpect(jsonPath("$.summary.firstDeposit.total").value(1))
        // EXNOVA tiene 3 (10000001, 20000001, 40000001) e IQOPTION 2.
        .andExpect(deBroker("accounts", "EXNOVA", 3))
        .andExpect(deBroker("accounts", "IQOPTION", 2))
        .andExpect(deBroker("firstDeposit", "IQOPTION", 1))
        // La única depositada es de IQOPTION, de modo que EXNOVA sale EN CERO
        // y no ausente.
        .andExpect(deBroker("firstDeposit", "EXNOVA", 0));
  }

  @Test
  @DisplayName("`CA-SP-676` — van los DOS estados, y `accounts` es siempre su suma")
  void losDosEstados() throws Exception {
    jdbc.update("UPDATE user_brokers SET status = 'FIRST_DEPOSIT' WHERE external_id = '30000001'");

    // Sin el bloque `register`, el cliente tendría que restar. Con dos estados
    // esa resta es trivial HOY; el día que haya un tercero dejaría de serlo, y
    // quien la hubiera escrito no se enteraría.
    mvc.perform(get(RUTA).with(administrador()))
        .andExpect(jsonPath("$.summary.accounts.total").value(5))
        .andExpect(jsonPath("$.summary.register.total").value(4))
        .andExpect(jsonPath("$.summary.firstDeposit.total").value(1))
        // Y broker a broker, no solo en el total: IQOPTION tiene 2 = 1 + 1.
        .andExpect(deBroker("register", "IQOPTION", 1))
        .andExpect(deBroker("register", "EXNOVA", 3));
  }

  @Test
  @DisplayName(
      "`CA-SP-675` — el desglose trae TODO el catálogo, y su longitud NO cambia al filtrar")
  void elDesgloseTraeTodoElCatalogo() throws Exception {
    // EXOPTION está en el catálogo y no tiene ninguna cuenta: aparece EN CERO.
    // Es lo contrario de lo que este endpoint hacía al nacer, y se invirtió
    // porque un arreglo cuya longitud depende del filtro obliga a rearmar las
    // columnas de la tabla en cada consulta.
    brokerAsegurado("01a081f0-6000-7103-9c4f-5e7adb000003", "EXOPTION");
    int catalogo = brokersEnCatalogo();

    mvc.perform(get(RUTA).with(administrador()))
        .andExpect(jsonPath("$.summary.accounts.byBroker.length()").value(catalogo))
        .andExpect(deBroker("accounts", "EXOPTION", 0));

    // Y con un filtro que deja UNA sola cuenta, el arreglo sigue midiendo lo
    // mismo. Eso es lo que se está probando: la longitud no depende del filtro.
    mvc.perform(get(RUTA + "?search=40000").with(administrador()))
        .andExpect(jsonPath("$.summary.accounts.total").value(1))
        .andExpect(jsonPath("$.summary.accounts.byBroker.length()").value(catalogo))
        .andExpect(deBroker("accounts", "IQOPTION", 0));
  }

  @Test
  @DisplayName(
      "`CA-SP-672` — con `?status=REGISTER` el FTD vale CERO, y ese cero no dice lo que parece")
  void elCeroQueEngana() throws Exception {
    jdbc.update("UPDATE user_brokers SET status = 'FIRST_DEPOSIT' WHERE external_id = '30000001'");

    // Decisión del 10-09-2026: el resumen respeta TODOS los filtros. El cero de
    // aquí NO significa «nadie ha depositado» —hay uno, y se ve sin el filtro—
    // sino «no pediste ninguno». La prosa del contrato lo dice con esas
    // palabras, y esta prueba es lo que impide que alguien lo «arregle»
    // haciendo que el FTD ignore el filtro.
    mvc.perform(get(RUTA + "?status=REGISTER").with(administrador()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.accounts.total").value(4))
        .andExpect(jsonPath("$.summary.register.total").value(4))
        .andExpect(jsonPath("$.summary.firstDeposit.total").value(0))
        // El desglose sigue trayendo el catálogo entero: lo que va a cero son
        // los valores, no la longitud.
        .andExpect(jsonPath("$.summary.firstDeposit.byBroker.length()").value(brokersEnCatalogo()))
        .andExpect(deBroker("firstDeposit", "IQOPTION", 0));

    // Y con el filtro contrario, el FTD es TODO el total y `register` cae a cero.
    mvc.perform(get(RUTA + "?status=FIRST_DEPOSIT").with(administrador()))
        .andExpect(jsonPath("$.summary.accounts.total").value(1))
        .andExpect(jsonPath("$.summary.register.total").value(0))
        .andExpect(jsonPath("$.summary.firstDeposit.total").value(1));
  }

  @Test
  @DisplayName("`CA-SP-673` — el resumen respeta el resto de filtros")
  void elResumenSigueAlFiltro() throws Exception {
    // Por red: de `jefe` cuelgan 3 cuentas (medio, base, nieto).
    mvc.perform(get(RUTA + "?supervisorId=" + jefe).with(administrador()))
        .andExpect(jsonPath("$.summary.accounts.total").value(3))
        .andExpect(deBroker("accounts", "EXNOVA", 2))
        .andExpect(deBroker("accounts", "IQOPTION", 1));

    // Por broker: solo IQOPTION suma, y EXNOVA queda en cero SIN desaparecer.
    mvc.perform(get(RUTA + "?brokerId=" + brokerB).with(administrador()))
        .andExpect(jsonPath("$.summary.accounts.total").value(2))
        .andExpect(deBroker("accounts", "IQOPTION", 2))
        .andExpect(deBroker("accounts", "EXNOVA", 0));

    // Por texto: una sola cuenta.
    mvc.perform(get(RUTA + "?search=40000").with(administrador()))
        .andExpect(jsonPath("$.summary.accounts.total").value(1));
  }

  @Test
  @DisplayName("`CA-SP-674` — con la página vacía el resumen va en ceros, no ausente")
  void elResumenVacio() throws Exception {
    mvc.perform(get(RUTA + "?userId=" + UUID.randomUUID()).with(administrador()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        // Presente y en ceros: ausente obligaría al cliente a distinguir dos
        // formas de respuesta para pintar lo mismo.
        .andExpect(jsonPath("$.summary.accounts.total").value(0))
        .andExpect(jsonPath("$.summary.register.total").value(0))
        .andExpect(jsonPath("$.summary.firstDeposit.total").value(0))
        // Y el desglose sigue siendo el catálogo entero, todo a cero.
        .andExpect(jsonPath("$.summary.accounts.byBroker.length()").value(brokersEnCatalogo()))
        .andExpect(deBroker("accounts", "EXNOVA", 0));
  }

  @Test
  @DisplayName("el resumen NO depende de la página: la segunda página trae los mismos totales")
  void elResumenNoSigueALaPagina() throws Exception {
    // Es lo que distingue un resumen de un conteo de lo devuelto: describe el
    // FILTRO, no la porción que cupo en la página.
    mvc.perform(get(RUTA + "?page=1&size=2").with(administrador()))
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.summary.accounts.total").value(5));
  }

  /**
   * El total de un broker DENTRO de un bloque del resumen, buscado por nombre.
   *
   * <p><b>Por nombre y no por posición</b>: el desglose trae ahora el catálogo entero, de modo que
   * su longitud y su orden dependen de cuántos brokers haya sembrados — y una prueba que indexara
   * fallaría el día que entre uno nuevo, por un motivo que no tiene nada que ver con lo que afirma.
   */
  private static org.springframework.test.web.servlet.ResultMatcher deBroker(
      String bloque, String nombre, int esperado) {
    // El filtro de JsonPath devuelve una LISTA aunque coincida uno solo, de modo
    // que se compara con `contains` y no con `value(int)`. Eso además afirma
    // algo útil de paso: que el broker aparece EXACTAMENTE una vez.
    return jsonPath(
        "$.summary." + bloque + ".byBroker[?(@.broker.name == '" + nombre + "')].total",
        org.hamcrest.Matchers.contains(esperado));
  }

  /** Cuántos brokers hay sembrados ahora mismo. Ver {@code brokerAsegurado}. */
  private int brokersEnCatalogo() {
    return jdbc.queryForObject("SELECT count(*) FROM brokers", Integer.class);
  }

  // Utilidades
  // ---------------------------------------------------------------------------

  /** El primer elemento de `content`, que es lo que las dos respuestas comparten. */
  private static String fila(String cuerpo) {
    int inicio = cuerpo.indexOf("\"content\":[") + "\"content\":[".length();
    int fin = cuerpo.indexOf("}]", inicio) + 1;
    return cuerpo.substring(inicio, fin);
  }

  /**
   * Un actor <b>con el permiso en el token</b>.
   *
   * <p>Y aquí sí hay que declararlo, al revés que en {@code BrokerAccountsIT}: esta ruta la
   * gobierna un {@code @PreAuthorize}, que <b>evalúa las autoridades del {@code Authentication}</b>
   * y no los permisos que {@code CurrentActor} lee de la base. Allí la comprobación vive dentro del
   * servicio y por eso bastaba con que la persona existiera.
   */
  private static RequestPostProcessor administrador() {
    return user(SUPERADMIN.toString())
        .authorities(() -> "broker-accounts:read", () -> "broker-accounts:read-indicators");
  }

  private UUID crearPersona(String username, String nombre, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, ?, 'Apellido', 'x', false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co",
        nombre);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        id,
        rol);
    return id;
  }

  /** El cliente y su principal: la fila REGISTRO de `client_sellers` (`RN-SP-049`, 18-09-2026). */
  private void registrar(UUID cliente, UUID vendedor) {
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin) VALUES (?, ?, 'REGISTRO')",
        cliente,
        vendedor);
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

  private void declarar(UUID persona, UUID broker, String cuenta) {
    jdbc.update(
        """
        INSERT INTO user_brokers (id, user_id, broker_id, external_id)
        VALUES (gen_random_uuid(), ?, ?, ?)
        """,
        persona,
        broker,
        cuenta);
  }

  private UUID brokerAsegurado(String id, String nombre) {
    jdbc.update(
        "INSERT INTO brokers (id, name) VALUES (CAST(? AS uuid), ?) ON CONFLICT DO NOTHING",
        id,
        nombre);
    return jdbc.queryForObject("SELECT id FROM brokers WHERE name = ?", UUID.class, nombre);
  }
}
