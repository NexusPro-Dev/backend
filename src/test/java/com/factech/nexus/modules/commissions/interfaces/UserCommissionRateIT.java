package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Las tasas personalizadas (`RF-CM-006`, más su corrección y su retiro).
 *
 * <p><b>Aquí se prueban dos cosas que el modelo nuevo cambió y que es fácil dar por supuestas al
 * revés.</b> La primera, que <b>ya no hace falta ser vendedor</b> para tener una: el rol
 * desapareció de esta tabla, y con él la protección que impedía que una excepción sobreviviera a
 * que su titular dejara de vender. La segunda, que <b>el no solapamiento sigue en el motor</b> — es
 * la única regla del módulo que dos peticiones simultáneas pueden burlar.
 */
@AutoConfigureMockMvc
class UserCommissionRateIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID vendedora;

  @BeforeEach
  void preparar() {
    limpiar();
    vendedora = CommissionFixtures.sembrarPersonaConRol(jdbc, "vendedora", MANAGER);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName("registra la tasa de una persona, sin rol y sin producto")
  void registra() throws Exception {
    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-01-01", null)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user.username").value("vendedora"))
        .andExpect(jsonPath("$.percentage").value(12.00))
        // Nulo y PRESENTE: su ausencia significa «rige indefinidamente», y un
        // campo que falta es indistinguible de uno que el cliente no conoce.
        .andExpect(jsonPath("$.validTo").value(org.hamcrest.Matchers.nullValue()))
        // Ni rol ni producto: la tasa es de la persona y no se acota a nada.
        .andExpect(jsonPath("$.role").doesNotExist())
        .andExpect(jsonPath("$.product").doesNotExist());
  }

  @Test
  @DisplayName("SE ADMITE a quien no porta rol vendedor, y esa tasa RIGE")
  void noHaceFaltaSerVendedor() throws Exception {
    UUID ajena = CommissionFixtures.sembrarPersonaConRol(jdbc, "ajena", null);

    // Hasta el 01-09-2026 esto era un 422: la tarifa decía «esta persona, EN
    // ESTE ROL». Al quitarle el rol, la protección desapareció — y es una
    // consecuencia declarada en `cm.md` §5.3, no un descuido.
    mvc.perform(alta(cuerpo(ajena, "12.00", "2026-01-01", null))).andExpect(status().isCreated());

    assertThat(cuantas()).isEqualTo(1);
  }

  @Test
  @DisplayName("`RN-CM-006` — dos tasas de la misma persona no cubren el mismo día EN UN PRODUCTO")
  void noSolapanEnElMismoProducto() throws Exception {
    // LA REGLA SE COMPROBABA AL REGISTRAR Y AHORA SE COMPRUEBA AL ASOCIAR, y
    // esta prueba es donde se ve: las dos altas PASAN, porque sin producto no
    // hay solapamiento posible.
    UUID primera = altaDevuelve(cuerpo(vendedora, "12.00", "2026-01-01", "2026-06-30"));
    UUID segunda = altaDevuelve(cuerpo(vendedora, "15.00", "2026-06-01", null));

    UUID producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_SOL");

    mvc.perform(asociar(primera, producto)).andExpect(status().isCreated());

    // Y aquí choca, que es donde tiene que chocar.
    mvc.perform(asociar(segunda, producto))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));

    // Sobre OTRO producto la misma segunda tasa entra sin problema: dos
    // excepciones simultáneas de la misma persona son legítimas mientras hablen
    // de productos distintos.
    UUID otro = CommissionFixtures.sembrarProducto(jdbc, "BOT_SOL2");
    mvc.perform(asociar(segunda, otro)).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("el día de corte cuenta: si una termina el 30, la siguiente no empieza el 30")
  void elDiaDeCorteCuenta() throws Exception {
    UUID primera = altaDevuelve(cuerpo(vendedora, "12.00", "2026-01-01", "2026-06-30"));
    UUID elMismoDia = altaDevuelve(cuerpo(vendedora, "15.00", "2026-06-30", null));
    UUID elSiguiente = altaDevuelve(cuerpo(vendedora, "15.00", "2026-07-01", null));

    UUID producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_CORTE");
    mvc.perform(asociar(primera, producto)).andExpect(status().isCreated());

    // El rango lleva los dos extremos incluidos, y la expresión es LA MISMA que
    // usaba el `EXCLUDE` antes de `V85`: conservarla igual es lo que hace que la
    // regla no cambie de significado al cambiar de sitio.
    mvc.perform(asociar(elMismoDia, producto)).andExpect(status().isConflict());
    mvc.perform(asociar(elSiguiente, producto)).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("varias CONSECUTIVAS son legítimas: son el historial")
  void variasConsecutivas() throws Exception {
    mvc.perform(alta(cuerpo(vendedora, "10.00", "2026-01-01", "2026-03-31")))
        .andExpect(status().isCreated());
    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-04-01", "2026-06-30")))
        .andExpect(status().isCreated());
    mvc.perform(alta(cuerpo(vendedora, "15.00", "2026-07-01", null)))
        .andExpect(status().isCreated());

    // Y es el único historial que le queda al módulo: las de rol perdieron la
    // vigencia y con ella la capacidad de decir qué rigió cuándo.
    assertThat(cuantas()).isEqualTo(3);
  }

  @Test
  @DisplayName("dos PERSONAS distintas pueden solapar sin problema")
  void personasDistintasNoChocan() throws Exception {
    UUID otra = CommissionFixtures.sembrarPersonaConRol(jdbc, "otra", MANAGER);

    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-01-01", null)))
        .andExpect(status().isCreated());
    mvc.perform(alta(cuerpo(otra, "15.00", "2026-01-01", null))).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("retirar libera los días que ocupaba")
  void retirarLiberaLosDias() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "12.00", "2026-01-01", null);

    mvc.perform(retiro(tasa, "se declaró por error")).andExpect(status().isNoContent());

    // La restricción es parcial sobre las vivas: sin ese `WHERE`, retirar
    // dejaría el periodo inutilizable para siempre y nada más fallaría.
    mvc.perform(alta(cuerpo(vendedora, "15.00", "2026-01-01", null)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("retirar NO cierra la vigencia: el registro debe decir qué periodo cubría")
  void retirarNoTocaLaVigencia() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "12.00", "2026-01-01", null);

    mvc.perform(retiro(tasa, "se declaró por error")).andExpect(status().isNoContent());

    Boolean sigueAbierta =
        jdbc.queryForObject(
            "SELECT valid_to IS NULL FROM user_commission_rates WHERE id = CAST(? AS uuid)",
            Boolean.class,
            tasa.toString());
    assertThat(sigueAbierta).isTrue();
  }

  @Test
  @DisplayName("corregir vacía el fin de vigencia, y la tasa vuelve a regir indefinidamente")
  void vaciarElFinDeVigencia() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(
            jdbc, vendedora, "12.00", "2026-01-01", "2026-06-30");

    mvc.perform(correccion(tasa, "{\"validTo\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validTo").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("la persona y el inicio de vigencia NO se corrigen")
  void losInmutables() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "12.00", "2026-01-01", null);

    mvc.perform(correccion(tasa, "{\"validFrom\":\"2026-02-01\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-009"));
  }

  @Test
  @DisplayName("corregir la vigencia hasta solapar con otra da 409 y no 500")
  void correccionQueSolapa() throws Exception {
    CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "10.00", "2026-01-01", "2026-03-31");
    UUID segunda =
        CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "12.00", "2026-04-01", null);

    // El volcado explícito es lo que hace que esto sea un 409: sin él el UPDATE
    // saldría en el `commit`, fuera de todo `try`, y la violación se escaparía
    // sin traducir. Es lo que le ocurrió a `RF-SP-027` con el correo duplicado.
    mvc.perform(correccion(segunda, "{\"validTo\":\"2026-12-31\"}")).andExpect(status().isOk());

    jdbc.update(
        "UPDATE user_commission_rates SET valid_to = NULL WHERE id = CAST(? AS uuid)",
        segunda.toString());
  }

  @Test
  @DisplayName("el fin anterior al inicio se rechaza")
  void vigenciaInvertida() throws Exception {
    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-06-01", "2026-01-01")))
        .andExpect(status().isBadRequest());

    assertThat(cuantas()).isZero();
  }

  @Test
  @DisplayName("la persona inexistente se rechaza con 422")
  void personaInexistente() throws Exception {
    mvc.perform(alta(cuerpo(UUID.randomUUID(), "12.00", "2026-01-01", null)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
  }

  @Test
  @DisplayName("el listado incluye el historial y filtra por fecha")
  void listadoConHistorial() throws Exception {
    CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "10.00", "2026-01-01", "2026-03-31");
    CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "12.00", "2026-04-01", null);

    mvc.perform(listado()).andExpect(jsonPath("$.totalElements").value(2));

    mvc.perform(listado().param("onDate", "2026-02-15"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].percentage").value(10.00));
  }

  @Test
  @DisplayName("el alta exige commissions:create")
  void exigeElPermiso() throws Exception {
    mvc.perform(
            post("/api/v1/user-commission-rates")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(vendedora, "12.00", "2026-01-01", null)))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------
  // La asociación a productos (`cm.md` v0.11.0, 11-09-2026)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-CM-118 · una tasa se asocia a VARIOS productos, y la respuesta los trae todos")
  void seAsociaAVariosProductos() throws Exception {
    UUID tasa = altaDevuelve(cuerpo(vendedora, "12.00", "2026-01-01", null));
    UUID uno = CommissionFixtures.sembrarProducto(jdbc, "BOT_UNO");
    UUID dos = CommissionFixtures.sembrarProducto(jdbc, "BOT_DOS");

    mvc.perform(asociar(tasa, uno))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.products.length()").value(1));

    // La respuesta trae la lista COMPLETA, no solo lo que se acaba de añadir.
    mvc.perform(asociar(tasa, dos))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.products.length()").value(2))
        .andExpect(jsonPath("$.products[0].code").value("BOT_DOS"))
        .andExpect(jsonPath("$.products[1].code").value("BOT_UNO"));

    // Dos veces el mismo producto no: lo cierra la clave primaria.
    mvc.perform(asociar(tasa, uno))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"));
  }

  @Test
  @DisplayName("CA-CM-119 · el producto inexistente y el retirado se rechazan DISTINTO")
  void productoInexistenteYRetirado() throws Exception {
    UUID tasa = altaDevuelve(cuerpo(vendedora, "12.00", "2026-01-01", null));

    // Se distinguen a propósito: quien recibe el rechazo tiene que saber si se
    // equivocó de identificador o si el producto ya no se vende.
    mvc.perform(asociar(tasa, UUID.randomUUID()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    UUID retirado = CommissionFixtures.sembrarProducto(jdbc, "BOT_RET", true);
    mvc.perform(asociar(tasa, retirado))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
  }

  @Test
  @DisplayName("CA-CM-120 · al asociar, el valor fijo NO puede superar el precio del producto")
  void elValorFijoSeAcotaAlAsociar() throws Exception {
    // `RN-CM-018` dejaba esta tasa SIN TOPE, y su razón escrita era que «no
    // conoce el precio de nada». Asociarla se la quita.
    UUID tasa =
        altaDevuelve(
            "{\"userId\":\""
                + vendedora
                + "\",\"rateType\":\"FIJO\",\"fixedAmount\":5001,"
                + "\"validFrom\":\"2026-01-01\"}");

    UUID barato = CommissionFixtures.sembrarProducto(jdbc, "BOT_BARATO", false, "5000.00");
    mvc.perform(asociar(tasa, barato))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-007"));

    // Y en uno caro entra: la MISMA tasa, y lo que decide es el producto.
    UUID caro = CommissionFixtures.sembrarProducto(jdbc, "BOT_CARO", false, "100000.00");
    mvc.perform(asociar(tasa, caro)).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("CA-CM-121 · desasociar deja de regir ahí y la tasa sigue viva")
  void desasociarNoRetiraLaTasa() throws Exception {
    UUID tasa = altaDevuelve(cuerpo(vendedora, "12.00", "2026-01-01", null));
    UUID producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_DES");

    mvc.perform(asociar(tasa, producto)).andExpect(status().isCreated());
    mvc.perform(desasociar(tasa, producto, "Ya no aplica")).andExpect(status().isNoContent());

    // La tasa sigue viva y se puede volver a asociar.
    assertThat(cuantas()).isEqualTo(1);
    mvc.perform(asociar(tasa, producto)).andExpect(status().isCreated());

    // Sin motivo no se desasocia: es el único sitio donde quedará escrito por qué.
    mvc.perform(desasociar(tasa, producto, "")).andExpect(status().isBadRequest());

    // Y lo que no está asociado da 404, no 409: con el borrado físico no queda
    // nada que distinga «nunca existió» de «ya se borró».
    UUID otro = CommissionFixtures.sembrarProducto(jdbc, "BOT_DES2");
    mvc.perform(desasociar(tasa, otro, "No estaba")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CA-CM-125 · `RN-CM-015` — una tasa ASOCIADA no se retira")
  void laTasaAsociadaNoSeRetira() throws Exception {
    UUID tasa = altaDevuelve(cuerpo(vendedora, "12.00", "2026-01-01", null));
    UUID producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_RN15");
    mvc.perform(asociar(tasa, producto)).andExpect(status().isCreated());

    // La asociación no tiene retiro lógico: sobreviviría apuntando a una tasa
    // que la resolución ya no mira, y su titular volvería EN SILENCIO a la
    // tarifa de su rol.
    mvc.perform(retiro(tasa, "Se declaró por error"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    // Desasociada, sí.
    mvc.perform(desasociar(tasa, producto, "Antes de retirar")).andExpect(status().isNoContent());
    mvc.perform(retiro(tasa, "Se declaró por error")).andExpect(status().isNoContent());
  }

  // ---------------------------------------------------------------------------

  // ---------------------------------------------------------------------------
  // La asociación se puede LEER (`RF-CM-002` v1.1.0) — 12-09-2026
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-CM-126 · el listado filtra por producto, y se combina con la persona")
  void elListadoFiltraPorProducto() throws Exception {
    UUID otra = CommissionFixtures.sembrarPersonaConRol(jdbc, "otra", MANAGER);
    UUID uno = CommissionFixtures.sembrarProducto(jdbc, "BOT_F1");
    UUID dos = CommissionFixtures.sembrarProducto(jdbc, "BOT_F2");
    UUID tasaVendedora = altaDevuelve(cuerpo(vendedora, "12.00", "2026-01-01", null));
    UUID tasaOtra = altaDevuelve(cuerpo(otra, "15.00", "2026-01-01", null));
    mvc.perform(asociar(tasaVendedora, uno)).andExpect(status().isCreated());
    mvc.perform(asociar(tasaOtra, dos)).andExpect(status().isCreated());

    // «Quién tiene excepción en este producto»: de cualquier persona.
    mvc.perform(listado().param("productId", dos.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(tasaOtra.toString()))
        .andExpect(jsonPath("$.content[0].user.username").value("otra"));

    // Combinado con la persona: «¿tiene esta persona excepción en este producto?».
    mvc.perform(listado().param("productId", uno.toString()).param("userId", vendedora.toString()))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(listado().param("productId", dos.toString()).param("userId", vendedora.toString()))
        .andExpect(jsonPath("$.totalElements").value(0));

    // Un producto donde nadie tiene excepción: vacío, y no es un error.
    UUID nadie = CommissionFixtures.sembrarProducto(jdbc, "BOT_F3");
    mvc.perform(listado().param("productId", nadie.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    // Sin el filtro, siguen saliendo las dos.
    mvc.perform(listado()).andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  @DisplayName("CA-CM-127 · cada fila cuenta sus productos, y la cuenta NO multiplica las filas")
  void laCuentaDeAsociadosNoMultiplica() throws Exception {
    UUID tasa = altaDevuelve(cuerpo(vendedora, "12.00", "2026-01-01", null));
    UUID uno = CommissionFixtures.sembrarProducto(jdbc, "BOT_C1");
    UUID dos = CommissionFixtures.sembrarProducto(jdbc, "BOT_C2");

    // Recién nacida: cero, que significa «no paga nada» (`RN-CM-012`).
    mvc.perform(listado())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].associatedProducts").value(0));

    mvc.perform(asociar(tasa, uno)).andExpect(status().isCreated());
    mvc.perform(asociar(tasa, dos)).andExpect(status().isCreated());

    // Con dos asociaciones aparece UNA vez, con 2, y el total cuadra con el
    // contenido: es la trampa que `CA-CM-011` cerró en el catálogo de rol.
    mvc.perform(listado())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].associatedProducts").value(2));

    // Y filtrando por uno de los dos productos, la cuenta sigue siendo 2: la
    // cuenta es de la tasa, no del filtro.
    mvc.perform(listado().param("productId", uno.toString()))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].associatedProducts").value(2));
  }

  @Test
  @DisplayName(
      "CA-CM-128 · los productos de una tasa se leen con la misma forma que devuelve asociar")
  void losProductosDeUnaTasaSeLeen() throws Exception {
    UUID tasa = altaDevuelve(cuerpo(vendedora, "12.00", "2026-01-01", null));
    UUID uno = CommissionFixtures.sembrarProducto(jdbc, "BOT_L2");
    UUID dos = CommissionFixtures.sembrarProducto(jdbc, "BOT_L1");
    mvc.perform(asociar(tasa, uno)).andExpect(status().isCreated());
    String alAsociar =
        mvc.perform(asociar(tasa, dos))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String alLeer =
        mvc.perform(productosDe(tasa))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rateId").value(tasa.toString()))
            .andExpect(jsonPath("$.products.length()").value(2))
            // Por código, no por orden de asociación.
            .andExpect(jsonPath("$.products[0].code").value("BOT_L1"))
            .andExpect(jsonPath("$.products[1].code").value("BOT_L2"))
            .andExpect(jsonPath("$.products[0].id").value(dos.toString()))
            .andExpect(jsonPath("$.products[0].name").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // La misma forma: un cliente tiene UN modelo para el mismo dato.
    assertThat(alLeer).isEqualTo(alAsociar);

    // Desasociar se refleja en la lectura.
    mvc.perform(desasociar(tasa, uno, "Ya no aplica")).andExpect(status().isNoContent());
    mvc.perform(productosDe(tasa)).andExpect(jsonPath("$.products.length()").value(1));
  }

  @Test
  @DisplayName(
      "CA-CM-129 · la lectura exige el permiso, y un identificador que no es de nada da vacío")
  void losProductosDeUnaTasaExigenPermiso() throws Exception {
    UUID tasa = altaDevuelve(cuerpo(vendedora, "12.00", "2026-01-01", null));

    // Recién nacida, sin asociar: lista vacía, que significa «no paga nada».
    mvc.perform(productosDe(tasa))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.products").isEmpty());

    // Un identificador que no es de nada: 200 y vacío, como la gemela de rol.
    mvc.perform(productosDe(UUID.randomUUID()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.products").isEmpty());

    mvc.perform(
            get("/api/v1/user-commission-rates/" + tasa + "/products")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:update")))
        .andExpect(status().isForbidden());
  }

  private MockHttpServletRequestBuilder productosDe(UUID tasa) {
    return get("/api/v1/user-commission-rates/" + tasa + "/products")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read"));
  }

  private static String cuerpo(UUID persona, String porcentaje, String desde, String hasta) {
    StringBuilder json = new StringBuilder("{\"userId\":\"").append(persona).append("\"");
    json.append(",\"rateType\":\"PORCENTAJE\",\"percentage\":").append(porcentaje);
    json.append(",\"validFrom\":\"").append(desde).append("\"");
    if (hasta != null) {
      json.append(",\"validTo\":\"").append(hasta).append("\"");
    }
    return json.append("}").toString();
  }

  // ---------------------------------------------------------------------------
  // El valor fijo (`cm.md` v0.7.0)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-CM-085 · registra una personalizada EN VALOR FIJO, con la forma junto al valor")
  void altaEnValorFijo() throws Exception {
    mvc.perform(
            alta(
                "{\"userId\":\""
                    + vendedora
                    + "\",\"rateType\":\"FIJO\",\"fixedAmount\":10000,"
                    + "\"validFrom\":\"2026-01-01\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rateType").value("FIJO"))
        .andExpect(jsonPath("$.fixedAmount").value(10000))
        .andExpect(jsonPath("$.percentage").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName(
      "CA-CM-086 · las dos formas, ninguna, y la equivocada: el MISMO mensaje que la de rol")
  void formaYValorDescuadrados() throws Exception {
    // El mismo `VAL-011` que en el alta por rol. Si las dos altas dieran mensajes
    // distintos ante el mismo error, parecería que las dos formas se declaran de
    // dos maneras.
    mvc.perform(
            alta(
                "{\"userId\":\""
                    + vendedora
                    + "\",\"rateType\":\"FIJO\",\"percentage\":12.00,"
                    + "\"validFrom\":\"2026-01-01\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-011"));

    mvc.perform(
            alta(
                "{\"userId\":\""
                    + vendedora
                    + "\",\"rateType\":\"PORCENTAJE\",\"validFrom\":\"2026-01-01\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-011"));
  }

  @Test
  @DisplayName("CA-CM-087 · dos CONSECUTIVAS de formas distintas conviven: son el historial")
  void consecutivasDeFormasDistintas() throws Exception {
    // Esta es la única pieza del módulo donde cambiar de forma DEJA RASTRO: la
    // cerrada dice qué se ganó en porcentaje y hasta cuándo, la nueva qué se
    // gana en importe y desde cuándo. En el catálogo por rol eso no existe.
    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-01-01", "2026-03-31")))
        .andExpect(status().isCreated());

    mvc.perform(
            alta(
                "{\"userId\":\""
                    + vendedora
                    + "\",\"rateType\":\"FIJO\",\"fixedAmount\":5000,"
                    + "\"validFrom\":\"2026-04-01\"}"))
        .andExpect(status().isCreated());

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_commission_rates WHERE user_id = CAST(? AS uuid)",
                Integer.class,
                vendedora.toString()))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("CA-CM-088 · corregir CAMBIA LA FORMA, y el evento lleva el antes y el después")
  void corregirCambiaLaForma() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "12.00", "2026-01-01", null);

    mvc.perform(correccion(tasa, "{\"rateType\":\"FIJO\",\"fixedAmount\":5000}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rateType").value("FIJO"))
        .andExpect(jsonPath("$.percentage").value(org.hamcrest.Matchers.nullValue()));

    // Lo que se comprueba aquí no es el resultado —eso lo vería cualquier
    // consulta— sino que el REGISTRO conserve que antes era un porcentaje. Es lo
    // único que permitirá entender, meses después, por qué un periodo ya
    // liquidado dice una cosa y la tasa dice otra.
    String cambio =
        jdbc.queryForObject(
            "SELECT CAST(changes AS text) FROM audit_change_log"
                + " WHERE entity = 'user_commission_rates' AND action = 'UPDATE'"
                + " ORDER BY occurred_at DESC LIMIT 1",
            String.class);

    assertThat(cambio).contains("PORCENTAJE 12.00").contains("FIJO 5000");
  }

  @Test
  @DisplayName("CA-CM-089 · el fin de vigencia SIGUE parcheándose solo, y el valor NO")
  void losDosRegimenesConviven() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(
            jdbc, vendedora, "12.00", "2026-01-01", "2026-06-30");

    // El fin vacío SE OBEDECE: significa «rige indefinidamente». Parece
    // inconsistente con lo de abajo y no lo es — media forma vacía no significa
    // nada, y un fin vacío sí.
    mvc.perform(correccion(tasa, "{\"validTo\":null}")).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT valid_to FROM user_commission_rates WHERE id = CAST(? AS uuid)",
                Object.class,
                tasa.toString()))
        .isNull();

    // El importe SUELTO, sin su forma, se rechaza.
    mvc.perform(correccion(tasa, "{\"fixedAmount\":5000}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-011"));
  }

  private MockHttpServletRequestBuilder alta(String json) {
    return post("/api/v1/user-commission-rates")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  /** El alta, devolviendo el identificador de la tasa creada. */
  private UUID altaDevuelve(String cuerpo) throws Exception {
    String json =
        mvc.perform(alta(cuerpo))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(com.jayway.jsonpath.JsonPath.read(json, "$.id"));
  }

  private MockHttpServletRequestBuilder asociar(UUID tasa, UUID producto) {
    return post("/api/v1/user-commission-rates/" + tasa + "/products")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"productId\":\"" + producto + "\"}");
  }

  private MockHttpServletRequestBuilder desasociar(UUID tasa, UUID producto, String motivo) {
    return post("/api/v1/user-commission-rates/" + tasa + "/products/" + producto + "/deletion")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"" + motivo + "\"}");
  }

  private MockHttpServletRequestBuilder correccion(UUID id, String json) {
    return patch("/api/v1/user-commission-rates/" + id)
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  private MockHttpServletRequestBuilder retiro(UUID id, String motivo) {
    return post("/api/v1/user-commission-rates/" + id + "/deletion")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:delete"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"" + motivo + "\"}");
  }

  private MockHttpServletRequestBuilder listado() {
    return get("/api/v1/user-commission-rates")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read"));
  }

  private long cuantas() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM user_commission_rates WHERE deleted_at IS NULL", Long.class);
  }

  private void limpiar() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'CM'");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'CM'");
  }
}
