package com.factech.nexus.modules.system.users.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.Map;
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
 * Registro de clientes por enlace (`RF-SP-045`).
 *
 * <p><b>Es el primer endpoint público del sistema que ESCRIBE</b>, y de ahí sale lo que estas
 * pruebas vigilan: que la cuenta nazca sin poder operar, que los rechazos no delaten el catálogo ni
 * la plantilla, y que <b>ningún rechazo deje nada escrito</b>.
 *
 * <p>La siembra la hace SQL: hace falta un producto `BECA → BECA`, un vendedor con rol de tipo
 * {@code VENDEDOR} y un broker, y ninguno de los tres se puede crear por HTTP sin credenciales.
 */
@AutoConfigureMockMvc
class SelfRegistrationIT extends IntegrationTestBase {

  /** `AGENTE`, sembrado por `V7` con `role_type = VENDEDOR`. */
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";

  /** `CLIENTE`, sembrado por `V30`: es de tipo `CONSUMIDOR`. */
  private static final String CLIENTE = "01a02a33-4c00-7008-9c4f-5e7ad1000008";

  /** `IQOPTION`, sembrado por `V76`. */
  private static final String BROKER = "01a081f0-6000-7101-9c4f-5e7adb000001";

  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  /** `CREDIT_CARD`, sembrado por `V54`: el método del camino de pago. */
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";

  /** El precio de `REG_ORO`, para comprobar el importe de la venta. */
  private static final java.math.BigDecimal CIEN = new java.math.BigDecimal("100.00");

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID free;
  private UUID oro;

  @BeforeEach
  void sembrar() {
    limpiar();

    // La cadena encadenada: `uq_memberships_parent` es UNIQUE NULLS NOT
    // DISTINCT, de modo que solo UNA puede no tener superior.
    // Colores distintos: `uq_memberships_color` es único, y dos iguales revientan
    // en el segundo INSERT y no en lo que la prueba comprueba.
    oro = membresia("ORO", "Oro", 1, null, "D4AF37");
    free = membresia("BECA", "Beca", 2, oro, "9E9E9E");

    // El producto del enlace gratuito: `BECA → BECA`, la renovación. Vale CERO,
    // y de ahí sale que su venta se anote con el pago gratuito (`RN-MV-022`).
    producto("REG_FREE", free, free, 30, "0.00");
    // Uno DE PAGO. Hasta el 09-09-2026 lo rechazaba `EX-004`; desde que el
    // registro anota la venta, este camino se admite y lo que cambia es el
    // estado en que nace la cuenta y qué membresía se concede.
    producto("REG_ORO", free, oro, 365, "100.00");

    vendedor("reg-agente");
    // Una persona que existe y NO es fuerza comercial: `EX-002` no la distingue
    // de una inexistente.
    cliente("reg-cliente");
  }

  @AfterEach
  void vaciar() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // El canal: la venta del enlace se valida contra el hotlink (`RN-MV-007`)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-686` — un producto SOLO de hotlink se compra por el enlace")
  void productoSoloHotlink() throws Exception {
    // Hasta el 19-09-2026 la venta del enlace se validaba contra la oferta de
    // la TIENDA, que no publica HOTLINK: el enlace lo mostraba y la compra lo
    // rechazaba con EX-004. Es lo que ese canal existe para vender.
    // La pareja BECA → BECA es única por producto activo (RN-PM-004): el gratuito
    // de siempre deja el sitio al que solo se vende por hotlink.
    jdbc.update("DELETE FROM products WHERE code = 'REG_FREE'");
    producto("REG_SOLO_HOTLINK", free, free, 30, "0.00", "HOTLINK");

    mvc.perform(
            registro(
                cuerpoCon("REG_SOLO_HOTLINK", "ana.ruiz", "ana@ejemplo.com", "12345678", BROKER)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value("ana.ruiz"));
  }

  @Test
  @DisplayName(
      "`CA-SP-687` — un producto SOLO de tienda NO se compra por el enlace, y no queda cuenta")
  void productoSoloTienda() throws Exception {
    // El hotlink no lo publica (`RN-PM-021`), así que tampoco lo vende: es la
    // otra mitad de la misma regla, y lo que impide que un enlace armado a mano
    // venda lo que el canal no muestra. La cuenta se deshace con la venta.
    jdbc.update("DELETE FROM products WHERE code = 'REG_FREE'");
    producto("REG_SOLO_TIENDA", free, free, 30, "0.00", "TIENDA");

    mvc.perform(
            registro(
                cuerpoCon("REG_SOLO_TIENDA", "ana.ruiz", "ana@ejemplo.com", "12345678", BROKER)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE username = 'ana.ruiz'", Integer.class))
        .isZero();
  }

  // ---------------------------------------------------------------------------
  // El camino feliz
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-507`, `CA-SP-508` y `CA-SP-512` — nace SIN TOKEN, en FTD_PENDIENTE")
  void registroValido() throws Exception {
    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value("ana.ruiz"))
        .andExpect(jsonPath("$.status").value("FTD_PENDIENTE"))
        // Lo que falta para operar, dicho en la respuesta: sin esto, quien se
        // registra no sabe por qué no puede hacer nada.
        .andExpect(jsonPath("$.pending").isNotEmpty());

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT status, must_change_password FROM users WHERE username = 'ana.ruiz'");

    assertThat(fila.get("status")).isEqualTo("FTD_PENDIENTE");
    // La contraseña LA ELIGIÓ ELLA: se marca lo que fijó otra persona, no lo
    // que uno fijó.
    assertThat(fila.get("must_change_password")).isEqualTo(false);
  }

  @Test
  @DisplayName("`CA-SP-509`, `CA-SP-510` y `CA-SP-711` — rol, membresía con vigencia y atribución")
  void losCuatroHechos() throws Exception {
    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isCreated());

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_roles ur JOIN users u ON u.id = ur.user_id"
                    + " WHERE u.username = 'ana.ruiz' AND ur.role_id = ?::uuid",
                Integer.class,
                CLIENTE))
        .isOne();

    // La vigencia sale del producto: treinta días, no nula.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_memberships um JOIN users u ON u.id = um.user_id"
                    + " WHERE u.username = 'ana.ruiz' AND um.membership_id = ?::uuid"
                    + " AND um.ends_at IS NOT NULL",
                Integer.class,
                free.toString()))
        .isOne();

    // `CA-SP-711` (18-09-2026, invierte `CA-SP-513`): la atribución es la fila
    // REGISTRO de `client_sellers`, cita la venta del enlace, y `user_supervisors`
    // NO recibe nada — el cliente no cuelga de la estructura de mando.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM client_sellers cs JOIN users u ON u.id = cs.client_id"
                    + " JOIN users v ON v.id = cs.seller_id"
                    + " JOIN movements m ON m.id = cs.first_movement_id"
                    + " WHERE u.username = 'ana.ruiz' AND v.username = 'reg-agente'"
                    + " AND cs.origin = 'REGISTRO' AND m.user_id = u.id",
                Integer.class))
        .isOne();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_supervisors us JOIN users u ON u.id = us.user_id"
                    + " WHERE u.username = 'ana.ruiz'",
                Integer.class))
        .isZero();

    // Y la línea de esa venta lleva al mismo vendedor: la venta leyó el vínculo
    // que el registro acababa de escribir (`RN-MV-003`, `CA-SP-709`).
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM movement_details md JOIN movements m ON m.id = md.movement_id"
                    + " JOIN users u ON u.id = m.user_id JOIN users v ON v.id = md.seller_id"
                    + " WHERE u.username = 'ana.ruiz' AND v.username = 'reg-agente'",
                Integer.class))
        .isOne();

    // `CA-MV-147` (RF-MV-016, 23-09-2026): el cliente nace con UN vendedor —quien lo
    // registró—, de modo que la venta de su alta nace VALIDADO (`RN-MV-034`).
    assertThat(
            jdbc.queryForObject(
                "SELECT s.code FROM movements m JOIN users u ON u.id = m.user_id"
                    + " JOIN movement_type_statuses s ON s.id = m.type_status_id"
                    + " WHERE u.username = 'ana.ruiz'",
                String.class))
        .isEqualTo("VALIDADO");
  }

  @Test
  @DisplayName(
      "`CA-SP-712` y `CA-SP-713` — el vendedor con clientes se puede desactivar, y su equipo no los lista")
  void laCarteraNoEsEquipo() throws Exception {
    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isCreated());

    String agente =
        jdbc.queryForObject(
            "SELECT id::text FROM users WHERE username = 'reg-agente'", String.class);

    // `CA-SP-713` (invierte `CA-SP-526`): el equipo de `RF-SP-042` es solo fuerza
    // comercial; la cliente no aparece, ni pidiendo el rol CLIENTE.
    mvc.perform(get("/api/v1/users/" + agente + "/team").with(administrador()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.team.totalElements").value(0));
    mvc.perform(get("/api/v1/users/" + agente + "/team?roles=CLIENTE").with(administrador()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.team.totalElements").value(0));

    // `CA-SP-712` (invierte `CA-SP-525`): retirar al agente no exige reasignar
    // a nadie — la cartera no cuenta para `RN-SP-022`— y el vínculo queda.
    mvc.perform(
            patch("/api/v1/users/" + agente + "/status")
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVO\",\"reason\":\"Deja la empresa\"}"))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM client_sellers cs JOIN users v ON v.id = cs.seller_id"
                    + " WHERE v.username = 'reg-agente' AND cs.origin = 'REGISTRO'",
                Integer.class))
        .isOne();
  }

  @Test
  @DisplayName("`CA-SP-511` — un producto sin vigencia produce una membresía sin fecha de fin")
  void sinVigencia() throws Exception {
    jdbc.update("UPDATE products SET validity_days = NULL WHERE code = 'REG_FREE'");

    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isCreated());

    // Nula significa que NO CADUCA, no que caduque hoy.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_memberships um JOIN users u ON u.id = um.user_id"
                    + " WHERE u.username = 'ana.ruiz' AND um.ends_at IS NULL",
                Integer.class))
        .isOne();
  }

  @Test
  @DisplayName("`CA-SP-623` — el producto y el vendedor salen del MOVIMIENTO, y son obligatorios")
  void elProductoYElVendedorSonDelMovimiento() throws Exception {
    String producto =
        jdbc.queryForObject("SELECT id::text FROM products WHERE code = 'REG_FREE'", String.class);

    // Desde el 09-09-2026 no hay `product` ni `referrer` en el primer nivel:
    // decían lo mismo que estos dos, y dos campos para un dato son dos valores
    // que pueden discrepar.
    mvc.perform(registro(conMovimiento(movimientoCrudo(null, null, "reg-agente", "VENTA"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].code").value(hasItem("VAL-001")));

    mvc.perform(registro(conMovimiento(movimientoCrudo(producto, null, null, "VENTA"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].code").value(hasItem("VAL-002")));

    assertThat(cuantasPersonas()).isZero();
  }

  @Test
  @DisplayName("`CA-SP-521` — el registro NO devuelve credenciales de sesión")
  void sinCredenciales() throws Exception {
    String respuesta =
        mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Registrarse no es iniciar sesión: quien acaba de crear su cuenta pasa por
    // `RF-SP-034` como todo el mundo.
    assertThat(respuesta).doesNotContain("token").doesNotContain("Bearer");
  }

  @Test
  @DisplayName("`CA-SP-522` — la persona registrada PUEDE autenticarse pese a no estar ACTIVO")
  void autenticaSinEstarActivo() throws Exception {
    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isCreated());

    // Es la primera vez que un estado distinto de `ACTIVO` autentica. Impedirlo
    // dejaría una cuenta que existe y a la que su titular no puede asomarse.
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"ana.ruiz\",\"password\":\"ClaveSegura2026!\"}"))
        .andExpect(status().isOk());
  }

  // ---------------------------------------------------------------------------
  // La cuenta de broker — `RN-SP-042`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-609` — el enlace BECA → BECA exige AL MENOS UNA cuenta de broker")
  void elBrokerEsObligatorio() throws Exception {
    // Lista vacía: es lo que envía un formulario donde nadie declaró ninguna.
    mvc.perform(registro(cuerpoCon("REG_FREE", "ana.ruiz", "ana@ejemplo.com", null, BROKER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-012"));

    // Una cuenta sin broker: la lista llega, pero incompleta.
    mvc.perform(registro(cuerpoCon("REG_FREE", "ana.ruiz", "ana@ejemplo.com", "12345678", null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-012"));

    // Y ninguno de los dos rechazos deja nada escrito.
    assertThat(cuantasPersonas()).isZero();
  }

  @Test
  @DisplayName("`CA-SP-614` — se declaran VARIAS cuentas en el mismo registro")
  void variasCuentasDeBroker() throws Exception {
    String otro =
        jdbc.queryForObject("SELECT id::text FROM brokers WHERE name = 'EXNOVA'", String.class);

    // Una persona puede operar con varios brokers, y este formulario es hoy la
    // única vía para declararlos: `RF-SP-053` sigue sin decidirse.
    mvc.perform(
            registro(
                plantillaConCuentas(
                    ("[{\"brokerId\":\"%s\",\"accountId\":\"111\"},"
                            + "{\"brokerId\":\"%s\",\"accountId\":\"222\"},"
                            + "{\"brokerId\":\"%s\",\"accountId\":\"333\"}]")
                        .formatted(BROKER, otro, BROKER))))
        .andExpect(status().isCreated());

    // Tres filas, y dos de ellas del MISMO broker: `RN-SP-038` acota el par
    // broker + identificador, no cuántas cuentas tiene alguien.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_brokers ub JOIN users u ON u.id = ub.user_id"
                    + " WHERE u.username = 'ana.ruiz'",
                Integer.class))
        .isEqualTo(3);
  }

  @Test
  @DisplayName("`VAL-014` — la misma cuenta repetida en la misma petición se rechaza")
  void cuentaRepetidaEnLaMismaPeticion() throws Exception {
    // El índice también la cazaría, y su mensaje diría «ya está declarada por
    // OTRA persona» — la otra persona sería ella misma, dos líneas más arriba
    // del mismo formulario.
    mvc.perform(
            registro(
                plantillaConCuentas(
                    ("[{\"brokerId\":\"%s\",\"accountId\":\"111\"},"
                            + "{\"brokerId\":\"%s\",\"accountId\":\"111\"}]")
                        .formatted(BROKER, BROKER))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-014"));

    assertThat(cuantasPersonas()).isZero();
  }

  @Test
  @DisplayName("si UNA de las cuentas choca, no queda NADA: ni la persona ni las anteriores")
  void unaCuentaQueChocaDeshaceElRegistroEntero() throws Exception {
    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "111")))
        .andExpect(status().isCreated());

    String otro =
        jdbc.queryForObject("SELECT id::text FROM brokers WHERE name = 'EXNOVA'", String.class);

    // La primera cuenta es libre y la segunda ya es de Ana: registrar a alguien
    // con la mitad de sus brokers declarados sería peor que no registrarlo,
    // porque nadie sabría cuál falta.
    mvc.perform(
            registro(
                plantillaConCuentas(
                    "beto.paz",
                    "beto@ejemplo.com",
                    ("[{\"brokerId\":\"%s\",\"accountId\":\"999\"},"
                            + "{\"brokerId\":\"%s\",\"accountId\":\"111\"}]")
                        .formatted(otro, BROKER))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-009"));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE username = 'beto.paz'", Integer.class))
        .isZero();
    // Y la cuenta libre de la petición fallida tampoco quedó.
    assertThat(jdbc.queryForObject("SELECT count(*) FROM user_brokers", Integer.class)).isOne();
  }

  @Test
  @DisplayName("`CA-SP-610` — la cuenta queda declarada, con el usuario del broker EN NULO")
  void laCuentaQuedaDeclarada() throws Exception {
    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isCreated());

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT ub.external_id, ub.broker_username FROM user_brokers ub"
                + " JOIN users u ON u.id = ub.user_id WHERE u.username = 'ana.ruiz'");

    assertThat(fila.get("external_id")).isEqualTo("12345678");
    // Nulo significa «el broker todavía no lo ha confirmado» (`RN-SP-040`): lo
    // rellenará el webhook de `RF-SP-054`.
    assertThat(fila.get("broker_username")).isNull();
  }

  @Test
  @DisplayName("`CA-SP-611` — un broker inexistente y uno inactivo se rechazan IGUAL")
  void brokerQueNoProcede() throws Exception {
    String inexistente = UUID.randomUUID().toString();
    jdbc.update("UPDATE brokers SET is_active = false WHERE id = ?::uuid", BROKER);

    String conInexistente =
        mvc.perform(
                registro(
                    cuerpoCon("REG_FREE", "ana.ruiz", "ana@ejemplo.com", "12345678", inexistente)))
            .andExpect(status().isUnprocessableEntity())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String conInactivo =
        mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
            .andExpect(status().isUnprocessableEntity())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // El mismo cuerpo salvo la correlación: distinguirlos permitiría averiguar
    // con qué brokers opera la plataforma probando identificadores.
    assertThat(normalizar(conInactivo)).isEqualTo(normalizar(conInexistente));
    assertThat(cuantasPersonas()).isZero();
  }

  @Test
  @DisplayName("`CA-SP-612` — una cuenta ya declarada se rechaza, y NO deja nada escrito")
  void cuentaYaDeclarada() throws Exception {
    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isCreated());

    // Aquí SÍ se dice qué pasó, al revés que con el documento repetido: quien
    // declara una cuenta de broker es su titular y necesita saber que alguien
    // se la atribuyó.
    mvc.perform(registro(cuerpo("beto.paz", "beto@ejemplo.com", "12345678")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-009"));

    // La transacción entera se deshace: ni cuenta, ni membresía, ni atribución.
    assertThat(cuantasPersonas()).isOne();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE username = 'beto.paz'", Integer.class))
        .isZero();
  }

  @Test
  @DisplayName("`CA-SP-613` — la misma cuenta en OTRO broker sí se admite")
  void mismaCuentaEnOtroBroker() throws Exception {
    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isCreated());

    // Lo único que no se repite es el par broker + identificador: dos brokers
    // numeran sus cuentas por su cuenta.
    String otro =
        jdbc.queryForObject("SELECT id::text FROM brokers WHERE name = 'EXNOVA'", String.class);

    mvc.perform(registro(cuerpoCon("REG_FREE", "beto.paz", "beto@ejemplo.com", "12345678", otro)))
        .andExpect(status().isCreated());
  }

  // ---------------------------------------------------------------------------
  // Los rechazos que no delatan
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-515` — producto inexistente, inactivo y retirado se rechazan IGUAL")
  void productoQueNoProcede() throws Exception {
    String inexistente = respuestaDe(cuerpoCon("NO_EXISTE", "a.b", "a@b.co", "1", BROKER));

    jdbc.update("UPDATE products SET status = 'INACTIVO' WHERE code = 'REG_FREE'");
    String inactivo = respuestaDe(cuerpo("a.b", "a@b.co", "1"));

    jdbc.update(
        "UPDATE products SET status = 'ACTIVO', deleted_at = now() WHERE code = 'REG_FREE'");
    String retirado = respuestaDe(cuerpo("a.b", "a@b.co", "1"));

    assertThat(normalizar(inactivo)).isEqualTo(normalizar(inexistente));
    assertThat(normalizar(retirado)).isEqualTo(normalizar(inexistente));
  }

  @Test
  @DisplayName("`CA-SP-516` — un producto de tipo BOT se rechaza")
  void elBotSeRechaza() throws Exception {
    jdbc.update(
        "UPDATE products SET type = 'BOT', source_membership_id = NULL,"
            + " target_membership_id = NULL WHERE code = 'REG_FREE'");

    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
  }

  // ---------------------------------------------------------------------------
  // El movimiento — `RN-SP-043`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-617` — el registro gratuito anota su venta, PENDIENTE y con pago gratuito")
  void elRegistroGratuitoAnotaSuVenta() throws Exception {
    String codigo =
        objeto(
            mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sale").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "sale");

    Map<String, Object> venta =
        jdbc.queryForMap(
            "SELECT m.code, m.status, pm.code AS metodo, m.total_amount,"
                + " c.username AS cliente, v.username AS vendedor"
                + " FROM movements m"
                + " JOIN payment_methods pm ON pm.id = m.payment_method_id"
                + " JOIN users c ON c.id = m.user_id"
                // El vendedor es de la línea (`RN-MV-003`, 16-09-2026); el alta
                // compra UN producto, de modo que hay una y solo una.
                + " JOIN movement_details d ON d.movement_id = m.id"
                + " JOIN users v ON v.id = d.seller_id"
                + " WHERE c.username = 'ana.ruiz'");

    assertThat(venta.get("code")).isEqualTo(codigo);
    // `RN-MV-004`: registrar NO concede nada. Que nazca pendiente es lo que
    // sostiene que la membresía comprada llegue al confirmar y no antes.
    assertThat(venta.get("status")).isEqualTo("PENDIENTE");
    // `RN-MV-022`: importe cero y pago gratuito son lo mismo. El formulario no
    // lo envía —no puede: el catálogo público no lo devuelve— y lo pone `MV`.
    assertThat(venta.get("metodo")).isEqualTo("GRATIS");
    assertThat(((java.math.BigDecimal) venta.get("total_amount")).signum()).isZero();
    // El vendedor NO se impone: `RN-MV-003` lo saca del superior que el registro
    // acaba de asignar, y aquí se comprueba que son el mismo.
    assertThat(venta.get("vendedor")).isEqualTo("reg-agente");
  }

  @Test
  @DisplayName("`CA-SP-618` — el enlace DE PAGO se admite: cuenta ACTIVA y membresía del SUELO")
  void elEnlaceDePagoSeAdmite() throws Exception {
    // Hasta el 09-09-2026 esto era `EX-004`: «ese producto exige un pago» y no
    // había con qué cobrarlo. Ahora el registro anota la venta.
    mvc.perform(registro(cuerpoCon("REG_ORO", "ana.ruiz", "ana@ejemplo.com", "12345678", BROKER)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.sale").isNotEmpty());

    // Quien paga no tiene ningún depósito que esperar: su cuenta nace operativa.
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM users WHERE username = 'ana.ruiz'", String.class))
        .isEqualTo("ACTIVO");

    // Y NO recibe lo comprado: `RN-SP-018` le da el SUELO, y la de ORO se la
    // concederá confirmar la venta (`RN-MV-020`). Concederla aquí sería premiar
    // un pago que nadie ha comprobado.
    Map<String, Object> nivel =
        jdbc.queryForMap(
            "SELECT ms.code, um.ends_at FROM user_memberships um"
                + " JOIN memberships ms ON ms.id = um.membership_id"
                + " JOIN users u ON u.id = um.user_id WHERE u.username = 'ana.ruiz'");

    assertThat(nivel.get("code")).isEqualTo("BECA");
    // Sin vigencia: el suelo no caduca. La del producto comprado sí, y llegará
    // con la confirmación.
    assertThat(nivel.get("ends_at")).isNull();

    Map<String, Object> venta =
        jdbc.queryForMap(
            "SELECT m.status, pm.code AS metodo, m.total_amount FROM movements m"
                + " JOIN payment_methods pm ON pm.id = m.payment_method_id"
                + " JOIN users c ON c.id = m.user_id WHERE c.username = 'ana.ruiz'");

    assertThat(venta.get("status")).isEqualTo("PENDIENTE");
    assertThat(venta.get("metodo")).isEqualTo("CREDIT_CARD");
    assertThat(((java.math.BigDecimal) venta.get("total_amount")).compareTo(CIEN)).isZero();
  }

  @Test
  @DisplayName("`CA-SP-620` — el bloque del movimiento y su tipo son obligatorios")
  void elMovimientoEsObligatorio() throws Exception {
    mvc.perform(registro(conMovimiento("null")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-015"));

    String producto =
        jdbc.queryForObject("SELECT id::text FROM products WHERE code = 'REG_FREE'", String.class);

    mvc.perform(registro(conMovimiento(movimientoCrudo(producto, null, "reg-agente", null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-017"));

    assertThat(cuantasPersonas()).isZero();
  }

  @Test
  @DisplayName("`CA-SP-621` — un tipo que no es VENTA se rechaza, y no deja NADA escrito")
  void elTipoQueNoEsVentaSeRechaza() throws Exception {
    String producto =
        jdbc.queryForObject("SELECT id::text FROM products WHERE code = 'REG_FREE'", String.class);

    // Hoy el catálogo solo tiene `VENTA`. El día que tenga más, mandar aquí un
    // `DEPOSITO` tiene que fallar y no colarse como venta.
    mvc.perform(registro(conMovimiento(movimientoCrudo(producto, null, "reg-agente", "DEPOSITO"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-010"));

    assertThat(cuantasPersonas()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movements", Integer.class)).isZero();
  }

  @Test
  @DisplayName("una venta rechazada deshace el registro ENTERO: ni persona, ni broker, ni nivel")
  void laVentaRechazadaDeshaceElRegistro() throws Exception {
    String producto =
        jdbc.queryForObject("SELECT id::text FROM products WHERE code = 'REG_FREE'", String.class);

    // La venta es lo ÚLTIMO que ocurre, con la persona, su membresía, su
    // atribución y su cuenta de broker ya escritas. Registrar a alguien cuya
    // venta no se pudo anotar es el estado que la transacción evita.
    // El método de pago sobra: el producto vale cero y `RN-MV-022` lo rechaza.
    mvc.perform(registro(conMovimiento(movimientoCrudo(producto, TARJETA, "reg-agente", "VENTA"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-MV-022"));

    assertThat(cuantasPersonas()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM user_brokers", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM user_memberships", Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movements", Integer.class)).isZero();
  }

  @Test
  @DisplayName("`CA-SP-518` — vendedor inexistente y sin rol VENDEDOR se rechazan IGUAL")
  void vendedorQueNoProcede() throws Exception {
    String inexistente = respuestaDe(cuerpoConVendedor("no-existe"));
    String noEsVendedor = respuestaDe(cuerpoConVendedor("reg-cliente"));

    // Probando nombres de usuario se averiguaría quién trabaja aquí.
    assertThat(normalizar(noEsVendedor)).isEqualTo(normalizar(inexistente));
  }

  @Test
  @DisplayName("`CA-SP-519` — un rechazo no deja NADA escrito")
  void elRechazoNoDejaNada() throws Exception {
    mvc.perform(registro(cuerpoCon("NO_EXISTE", "ana.ruiz", "ana@ejemplo.com", "1", BROKER)))
        .andExpect(status().isUnprocessableEntity());

    assertThat(cuantasPersonas()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM user_brokers", Integer.class)).isZero();
  }

  @Test
  @DisplayName("`CA-SP-520` — el nombre de usuario y el correo en uso SÍ dicen cuál chocó")
  void identidadesEnUso() throws Exception {
    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isCreated());

    mvc.perform(registro(cuerpo("ana.ruiz", "otra@ejemplo.com", "99999999")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].field").value("username"));

    mvc.perform(registro(cuerpo("otra.persona", "ana@ejemplo.com", "99999999")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].field").value("email"));
  }

  @Test
  @DisplayName("`CA-SP-583` — un país inexistente y uno inactivo se rechazan IGUAL")
  void paisQueNoProcede() throws Exception {
    String inventado = respuestaDe(cuerpoConPais("ZZZ"));

    String codigoReal =
        jdbc.queryForObject("SELECT code FROM countries ORDER BY code LIMIT 1", String.class);
    jdbc.update("UPDATE countries SET is_active = false WHERE code = ?", codigoReal);
    String inactivo = respuestaDe(cuerpoConPais(codigoReal));

    // El cuerpo no dice qué países existen.
    assertThat(normalizar(inactivo)).isEqualTo(normalizar(inventado));
    assertThat(inactivo).doesNotContain(codigoReal);
  }

  @Test
  @DisplayName("`CA-SP-601` — no existe abreviación que registre a un menor")
  void elMenorNoSePuedeExpresar() throws Exception {
    // `TI` es la tarjeta de identidad y NO está en el catálogo: se rechaza con
    // la misma respuesta que una abreviación inventada, porque lo que no está
    // no está.
    String tarjeta = respuestaDe(cuerpoConDocumento("TI"));
    String inventada = respuestaDe(cuerpoConDocumento("XX"));

    assertThat(normalizar(tarjeta)).isEqualTo(normalizar(inventada));
  }

  @Test
  @DisplayName("`CA-SP-681` — el formulario público NO admite el teléfono de la empresa")
  void elTelefonoDeLaEmpresaNoSePuedeEnviar() throws Exception {
    // El campo nace el 10-09-2026 para el resto del sistema y NO entra aquí:
    // quien se registra por un enlace es un cliente, y preguntarle por el
    // teléfono de una empresa que no tiene añadiría un campo que nadie
    // rellenaría. Puede ponerlo después desde su propio perfil.
    //
    // SE RECHAZA, NO SE IGNORA, y la diferencia importa: ignorarlo en silencio
    // haría creer a quien lo manda que quedó guardado. Es el mismo trato que ya
    // reciben aquí el documento y el país en `PATCH /api/v1/users/me`.
    String base = cuerpo("conempresa", "conempresa@factech.co", "1234567").trim();
    String cuerpo = "{\"companyPhone\":\"+576012345678\"," + base.substring(base.indexOf('{') + 1);

    mvc.perform(registro(cuerpo)).andExpect(status().isBadRequest());

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE username = 'conempresa'", Integer.class))
        .isZero();
  }

  // ---------------------------------------------------------------------------
  // Preparación
  // ---------------------------------------------------------------------------

  /**
   * Un administrador con lo justo para mirar el equipo y cambiar un estado (`CA-SP-712`,
   * `CA-SP-713`).
   */
  private static RequestPostProcessor administrador() {
    // La familia entera de users:read y users:update: desde RF-SP-060 el equipo es
    // users:read-team y el estado users:change-status (RN-SEG-014).
    return user(SUPERADMIN.toString())
        .authorities(
            () -> "users:read",
            () -> "users:read-team",
            () -> "users:update",
            () -> "users:change-status");
  }

  private MockHttpServletRequestBuilder registro(String cuerpo) {
    return post("/api/v1/auth/registration")
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private String respuestaDe(String cuerpo) throws Exception {
    return mvc.perform(registro(cuerpo)).andReturn().getResponse().getContentAsString();
  }

  /** Sin la correlación, que cambia en cada petición. */
  private static String normalizar(String cuerpo) {
    return cuerpo
        .replaceAll("\"correlationId\"\\s*:\\s*\"[^\"]*\"", "\"correlationId\":\"?\"")
        .replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "\"timestamp\":\"?\"");
  }

  private String cuerpo(String usuario, String correo, String cuenta) {
    return cuerpoCon("REG_FREE", usuario, correo, cuenta, BROKER);
  }

  private String cuerpoCon(
      String producto, String usuario, String correo, String cuenta, String broker) {
    return plantilla(producto, "reg-agente", usuario, correo, cuenta, broker, paisSembrado(), "CC");
  }

  // ---------------------------------------------------------------------------
  // El bloque del movimiento
  // ---------------------------------------------------------------------------

  /**
   * El movimiento que acompaña al registro, <b>derivado del producto del enlace</b>.
   *
   * <p>Se construye así y no con valores fijos porque los dos campos que lleva dependen del
   * producto: {@code productId} tiene que ser <b>el del enlace</b> (`VAL-016`) y el método de pago
   * <b>solo va cuando la venta tiene importe</b> (`RN-MV-022`) — el gratuito lo pone `MV`, y el
   * formulario ni siquiera puede conocerlo.
   */
  /** El cuerpo de siempre, con el bloque del movimiento escrito a mano. */
  private String conMovimiento(String movimientoJson) {
    return """
        {"firstName":"Ana","lastName":"Ruiz",
         "username":"ana.ruiz","email":"ana@ejemplo.com","password":"ClaveSegura2026!",
         "countryCode":"%s","documentType":"CC","documentNumber":"%s",
         "phone":"+573001234567",
         "addressLine1":null,"addressLine2":null,"city":null,
         "brokerAccounts":[{"brokerId":"%s","accountId":"12345678"}],
         "movement":%s}
        """
        .formatted(
            paisSembrado(),
            Integer.toString(Math.abs("ana.ruiz".hashCode())),
            BROKER,
            movimientoJson);
  }

  private static String movimientoCrudo(
      String productoId, String metodo, String vendedor, String tipo) {
    return "{\"productId\":"
        + comillas(productoId)
        + ",\"paymentMethodId\":"
        + comillas(metodo)
        + ",\"sellerUsername\":"
        + comillas(vendedor)
        + ",\"movementTypeCode\":"
        + comillas(tipo)
        + "}";
  }

  /** Un campo de primer nivel de la respuesta, sin traerse un lector de JSON entero. */
  private static String objeto(String cuerpo, String campo) {
    var m =
        java.util.regex.Pattern.compile("\"" + campo + "\"\\s*:\\s*\"([^\"]*)\"").matcher(cuerpo);
    assertThat(m.find()).isTrue();
    return m.group(1);
  }

  private String movimiento(String productoRef, String vendedor) {
    String id = idDeProducto(productoRef);
    return "{\"productId\":"
        + comillas(id)
        + ",\"paymentMethodId\":"
        + comillas(metodoDePagoPara(id))
        + ",\"sellerUsername\":\""
        + vendedor
        + "\",\"movementTypeCode\":\"VENTA\"}";
  }

  /**
   * El identificador del producto referido, venga por código o ya por identificador.
   *
   * <p>Una referencia que no corresponde a ningún producto —la de `CA-SP-515`— produce un
   * identificador inventado: esa petición se rechaza por `EX-001` <b>antes</b> de mirar el
   * movimiento, y lo que lleve aquí da igual mientras sea un UUID.
   */
  private String idDeProducto(String referencia) {
    if (esUuid(referencia)) {
      return referencia;
    }
    return jdbc
        .queryForList("SELECT id::text FROM products WHERE code = ?", String.class, referencia)
        .stream()
        .findFirst()
        .orElseGet(() -> UUID.randomUUID().toString());
  }

  /** Nulo si el producto vale cero, y `CREDIT_CARD` si tiene importe (`RN-MV-022`). */
  private String metodoDePagoPara(String productoId) {
    Boolean gratuito =
        jdbc
            .queryForList(
                "SELECT price = 0 FROM products WHERE id = ?::uuid", Boolean.class, productoId)
            .stream()
            .findFirst()
            .orElse(true);

    return Boolean.TRUE.equals(gratuito) ? null : TARJETA;
  }

  private static boolean esUuid(String valor) {
    try {
      UUID.fromString(valor);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static String comillas(String valor) {
    return valor == null ? "null" : "\"" + valor + "\"";
  }

  private String cuerpoConVendedor(String vendedor) {
    return plantilla(
        "REG_FREE",
        vendedor,
        "ana.ruiz",
        "ana@ejemplo.com",
        "12345678",
        BROKER,
        paisSembrado(),
        "CC");
  }

  private String cuerpoConPais(String pais) {
    return plantilla(
        "REG_FREE", "reg-agente", "ana.ruiz", "ana@ejemplo.com", "12345678", BROKER, pais, "CC");
  }

  private String cuerpoConDocumento(String abreviacion) {
    return plantilla(
        "REG_FREE",
        "reg-agente",
        "ana.ruiz",
        "ana@ejemplo.com",
        "12345678",
        BROKER,
        paisSembrado(),
        abreviacion);
  }

  private String plantilla(
      String producto,
      String vendedor,
      String usuario,
      String correo,
      String cuenta,
      String broker,
      String pais,
      String documento) {

    return """
        {"firstName":"Ana","lastName":"Ruiz",
         "username":"%s","email":"%s","password":"ClaveSegura2026!",
         "countryCode":"%s","documentType":"%s","documentNumber":"%s",
         "phone":"+573001234567",
         "addressLine1":null,"addressLine2":null,"city":null,
         "brokerAccounts":%s,
         "movement":%s}
        """
        .formatted(
            usuario,
            correo,
            pais,
            documento,
            // El número de documento se deriva del nombre de usuario para que
            // dos registros distintos no choquen por `uq_users_document`.
            Integer.toString(Math.abs(usuario.hashCode())),
            cuentas(broker, cuenta),
            movimiento(producto, vendedor));
  }

  private String plantillaConCuentas(String cuentasJson) {
    return plantillaConCuentas("ana.ruiz", "ana@ejemplo.com", cuentasJson);
  }

  /** El cuerpo con una lista de cuentas escrita a mano, para los casos de varias. */
  private String plantillaConCuentas(String usuario, String correo, String cuentasJson) {
    return """
        {"firstName":"Ana","lastName":"Ruiz",
         "username":"%s","email":"%s","password":"ClaveSegura2026!",
         "countryCode":"%s","documentType":"CC","documentNumber":"%s",
         "phone":"+573001234567",
         "addressLine1":null,"addressLine2":null,"city":null,
         "brokerAccounts":%s,
         "movement":%s}
        """
        .formatted(
            usuario,
            correo,
            paisSembrado(),
            Integer.toString(Math.abs(usuario.hashCode())),
            cuentasJson,
            movimiento("REG_FREE", "reg-agente"));
  }

  /**
   * La lista de cuentas, o vacía.
   *
   * <p>Un broker nulo produce una cuenta SIN broker —para probar `VAL-012`— y una cuenta nula
   * produce una lista vacía, que es lo que el registro rechaza cuando el enlace la exige.
   */
  private static String cuentas(String broker, String cuenta) {
    if (cuenta == null) {
      return "[]";
    }
    String id = broker == null ? "null" : "\"" + broker + "\"";
    return "[{\"brokerId\":" + id + ",\"accountId\":\"" + cuenta + "\"}]";
  }

  private String paisSembrado() {
    return jdbc.queryForObject(
        "SELECT code FROM countries WHERE is_active ORDER BY code LIMIT 1", String.class);
  }

  private int cuantasPersonas() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM users WHERE username IN ('ana.ruiz', 'beto.paz', 'a.b',"
            + " 'otra.persona')",
        Integer.class);
  }

  private UUID membresia(String codigo, String nombre, int nivel, UUID superior, String color) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, level, color, parent_membership_id)"
            + " VALUES (CAST(? AS uuid), ?, ?, ?, ?, CAST(? AS uuid))",
        id.toString(),
        codigo,
        nombre,
        nivel,
        color,
        superior == null ? null : superior.toString());
    return id;
  }

  private void producto(String codigo, UUID origen, UUID destino, Integer vigencia, String precio) {
    // AMBOS: el enlace vende por el canal hotlink (`RN-MV-007` desde el
    // 19-09-2026), y un producto solo de tienda no se vende por él.
    producto(codigo, origen, destino, vigencia, precio, "AMBOS");
  }

  private void producto(
      String codigo, UUID origen, UUID destino, Integer vigencia, String precio, String alcance) {
    jdbc.update(
        "INSERT INTO products (id, code, type, name, source_membership_id, target_membership_id,"
            + " price, currency_id, validity_days, status, scope, implementation)"
            + " VALUES (CAST(? AS uuid), ?, 'UPGRADE_MEMBRESIA', ?, CAST(? AS uuid),"
            + " CAST(? AS uuid), CAST(? AS numeric), CAST(? AS uuid), CAST(? AS integer), 'ACTIVO',"
            + " ?, 'AUTOMATICA')",
        UUID.randomUUID().toString(),
        codigo,
        "Producto " + codigo,
        origen.toString(),
        destino.toString(),
        precio,
        USD,
        vigencia,
        alcance);
  }

  private void vendedor(String usuario) {
    persona(usuario, AGENTE);
  }

  private void cliente(String usuario) {
    persona(usuario, CLIENTE);
  }

  private void persona(String usuario, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, username, email, first_name, last_name, password_hash, status,"
            + " country_id)"
            + " VALUES (CAST(? AS uuid), ?, ?, 'Nombre', 'Apellido', 'x', 'ACTIVO',"
            + " (SELECT id FROM countries ORDER BY code LIMIT 1))",
        id.toString(),
        usuario,
        usuario + "@nexus.test");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT CAST(? AS uuid), CAST(? AS uuid), role_type FROM roles WHERE id = CAST(? AS"
            + " uuid)",
        id.toString(),
        rol,
        rol);
  }

  private void limpiar() {
    // Los movimientos ANTES que los productos y las personas: sus claves
    // foráneas son RESTRICT a propósito, para que un borrado físico no se lleve
    // por delante la atribución de una venta. Desde el 09-09-2026 todo registro
    // deja uno, de modo que esta prueba ya los produce.
    // `client_sellers` ANTES que los movimientos: la fila REGISTRO cita la venta
    // del enlace (`first_movement_id`), y la clave foránea es RESTRICT.
    jdbc.update("DELETE FROM client_sellers");
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'MV'");
    jdbc.update("DELETE FROM user_brokers");
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'reg-%' OR username IN ('ana.ruiz', 'beto.paz', 'a.b', 'otra.persona'))");
    jdbc.update(
        "DELETE FROM users WHERE username LIKE 'reg-%' OR username IN ('ana.ruiz', 'beto.paz',"
            + " 'a.b', 'otra.persona')");
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM memberships");
    jdbc.update("UPDATE countries SET is_active = true");
    jdbc.update("UPDATE brokers SET is_active = true");
  }
}
