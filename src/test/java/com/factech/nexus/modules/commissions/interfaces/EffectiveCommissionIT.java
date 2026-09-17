package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.DIRECTOR;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.MANAGER;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * La resolución de la comisión efectiva (`RF-CM-005`).
 *
 * <p><b>Aquí es donde el rediseño se ve entero.</b> La precedencia pasó de cuatro grados a dos, y
 * sobre todo <b>la ausencia cambió de significado</b>: sin una tasa de rol <b>de ese producto</b>
 * no se paga nada, donde antes una tasa sin producto habría pagado como tarifa por omisión.
 *
 * <p>Desde el 15-09-2026 la tasa de rol nace con su producto (`RN-CM-021`), y «asociada» en estas
 * pruebas quiere decir «registrada sobre él»: es la misma resolución, leída de {@code
 * commission_rates.product_id} en vez de la tabla de asociación que `V94` retiró.
 */
@AutoConfigureMockMvc
class EffectiveCommissionIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID vendedora;
  private UUID producto;

  @BeforeEach
  void preparar() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
    vendedora = CommissionFixtures.sembrarPersonaConRol(jdbc, "vendedora", MANAGER);
    producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_A");
  }

  @Test
  @DisplayName("`CA-CM-122` — la personalizada NO gana donde no está asociada: manda la del rol")
  void laPersonalizadaNoSaleDeSusProductos() throws Exception {
    // Es la prueba de la enmienda, y la que habría fallado el 10-09-2026: hasta
    // entonces la rama de la persona no miraba el producto, de modo que esta
    // consulta habría devuelto PERSONALIZADA y 18.00.
    UUID otroProducto = CommissionFixtures.sembrarProducto(jdbc, "BOT_B");

    UUID delRol = CommissionFixtures.sembrarTasaDeRol(jdbc, otroProducto, MANAGER, "10.00");

    personalizadaSobre(vendedora, producto, "18.00", "2026-01-01", null);

    mvc.perform(efectiva(vendedora, otroProducto, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("RESUELTA"))
        .andExpect(jsonPath("$.source").value("ROL"))
        .andExpect(jsonPath("$.value").value(10.00));
  }

  @Test
  @DisplayName(
      "`CA-CM-153` — una persona con excepción en DOS productos son dos tasas, y cada producto"
          + " resuelve la suya — reescrito el 16-09-2026")
  void dosProductosDosTasas() throws Exception {
    // Hasta el 16-09-2026 (`CA-CM-123`) una sola tasa personalizada se asociaba
    // a dos productos y regía en los dos. Hoy la tasa nace con su producto
    // (`RN-CM-021`): la misma persona con excepción en dos productos tiene dos
    // filas, y la resolución lee `user_commission_rates.product_id`.
    UUID otroProducto = CommissionFixtures.sembrarProducto(jdbc, "BOT_B");

    UUID enA = personalizadaSobre(vendedora, producto, "18.00", "2026-01-01", null);
    UUID enB = personalizadaSobre(vendedora, otroProducto, "25.00", "2026-01-01", null);

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.source").value("PERSONALIZADA"))
        .andExpect(jsonPath("$.rateId").value(enA.toString()))
        .andExpect(jsonPath("$.value").value(18.00));
    mvc.perform(efectiva(vendedora, otroProducto, "2026-05-01"))
        .andExpect(jsonPath("$.rateId").value(enB.toString()))
        .andExpect(jsonPath("$.value").value(25.00));
  }

  @Test
  @DisplayName(
      "`CA-CM-153` — la personalizada de OTRO producto no gana aquí: sin tasa sobre este, sin"
          + " tarifa")
  void laPersonalizadaEsDeSuProducto() throws Exception {
    // Hasta el 16-09-2026 (`CA-CM-124`) esta prueba sembraba una personalizada
    // SIN ASOCIAR y afirmaba que no pagaba nada. Esa tasa ya no puede existir;
    // lo que se afirma es lo mismo desde el otro lado: la excepción es de un
    // producto y no alcanza a los demás.
    UUID otroProducto = CommissionFixtures.sembrarProducto(jdbc, "BOT_B");
    personalizadaSobre(vendedora, otroProducto, "18.00", "2026-01-01", null);

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("SIN_TARIFA"));
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
  }

  @Test
  @DisplayName("resuelve la tasa del rol cuando está ASOCIADA a ese producto")
  void resuelvePorElRol() throws Exception {
    UUID tasa = CommissionFixtures.sembrarTasaDeRol(jdbc, producto, MANAGER, "10.00");

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("RESUELTA"))
        .andExpect(jsonPath("$.source").value("ROL"))
        .andExpect(jsonPath("$.value").value(10.00))
        .andExpect(jsonPath("$.rateId").value(tasa.toString()))
        // Las de rol no tienen vigencia, y estos nulos lo dicen.
        .andExpect(jsonPath("$.validFrom").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName(
      "`RN-CM-012` — SIN TASA SOBRE EL PRODUCTO NO SE PAGA NADA, donde antes era la tarifa de todos")
  void laTasaSinAsociarNoPaga() throws Exception {
    // El rol es el correcto y el producto existe... y nadie registró una tasa
    // sobre él. Hasta el 15-09-2026 esta prueba sembraba una tasa SIN producto
    // para clavar que no pagaba; hoy esa tasa no puede existir (`RN-CM-021`), y
    // lo que se clava es lo mismo desde el otro lado.
    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("SIN_TARIFA"))
        // NULO Y PRESENTE, nunca cero: cero es «no comisiona», que es una
        // decisión declarada, y la ausencia es que nadie la tomó.
        .andExpect(jsonPath("$.value").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("la asociación de OTRO producto no sirve para este")
  void laAsociacionEsPorProducto() throws Exception {
    UUID otro = CommissionFixtures.sembrarProducto(jdbc, "BOT_B");
    UUID tasa = CommissionFixtures.sembrarTasaDeRol(jdbc, otro, MANAGER, "10.00");

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("SIN_TARIFA"));
  }

  @Test
  @DisplayName("la asociación de OTRO rol no sirve para esta persona")
  void laAsociacionEsPorRol() throws Exception {
    UUID tasa = CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "4.00");

    // La vendedora es MANAGER: el producto paga, pero no a ella.
    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("SIN_TARIFA"));
  }

  @Test
  @DisplayName("`RN-CM-004` — la personalizada GANA, y sin mirar el producto")
  void laPersonalizadaGana() throws Exception {
    UUID delRol = CommissionFixtures.sembrarTasaDeRol(jdbc, producto, MANAGER, "10.00");

    UUID personal = personalizadaSobre(vendedora, producto, "18.00", "2026-01-01", null);

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("RESUELTA"))
        .andExpect(jsonPath("$.source").value("PERSONALIZADA"))
        .andExpect(jsonPath("$.value").value(18.00))
        .andExpect(jsonPath("$.rateId").value(personal.toString()));
  }

  @Test
  @DisplayName("la personalizada gana incluso sobre un producto SIN asociación")
  void laPersonalizadaIgnoraElProducto() throws Exception {
    personalizadaSobre(vendedora, producto, "18.00", "2026-01-01", null);

    // El producto no paga a nadie POR ROL, y ella cobra igual: su excepción no
    // necesita que exista una tasa de rol debajo.
    //
    // Lo que SÍ necesita desde el 11-09-2026 es estar ASOCIADA a este producto:
    // ver `CA-CM-122`, que es la mitad que antes no se podía escribir.
    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("RESUELTA"))
        .andExpect(jsonPath("$.source").value("PERSONALIZADA"));
  }

  @Test
  @DisplayName("la personalizada VENCIDA deja de ganar, y vuelve a mandar la del rol")
  void laPersonalizadaVencida() throws Exception {
    UUID delRol = CommissionFixtures.sembrarTasaDeRol(jdbc, producto, MANAGER, "10.00");
    personalizadaSobre(vendedora, producto, "18.00", "2026-01-01", "2026-03-31");

    mvc.perform(efectiva(vendedora, producto, "2026-02-15"))
        .andExpect(jsonPath("$.source").value("PERSONALIZADA"));

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.source").value("ROL"))
        .andExpect(jsonPath("$.value").value(10.00));
  }

  @Test
  @DisplayName("QUIEN NO VENDE PUEDE COBRAR su personalizada: es lo que costó quitarle el rol")
  void laPersonalizadaSobreviveAlRol() throws Exception {
    UUID ajena = CommissionFixtures.sembrarPersonaConRol(jdbc, "ajena", null);
    personalizadaSobre(ajena, producto, "18.00", "2026-01-01", null);

    // No porta rol vendedor, y aun así RESUELVE. Hasta el 01-09-2026 la tarifa
    // decía «esta persona, EN ESTE ROL» y esto habría sido NO_COMISIONA.
    // `cm.md` §5.3 lo declara: la tasa sobrevive a que su titular deje de
    // vender, y no se queda inerte — cobra.
    mvc.perform(efectiva(ajena, producto, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("RESUELTA"))
        .andExpect(jsonPath("$.source").value("PERSONALIZADA"))
        // Y `roleId` llega nulo con `RESUELTA`, que no es incoherencia sino la
        // forma de verse esta consecuencia.
        .andExpect(jsonPath("$.roleId").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("sin rol vendedor y sin personalizada, NO_COMISIONA")
  void noComisiona() throws Exception {
    UUID ajena = CommissionFixtures.sembrarPersonaConRol(jdbc, "ajena", null);

    mvc.perform(efectiva(ajena, producto, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("NO_COMISIONA"))
        .andExpect(jsonPath("$.value").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.roleId").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("el porcentaje CERO resuelve, y no es lo mismo que no tener tasa")
  void elCeroResuelve() throws Exception {
    UUID tasa = CommissionFixtures.sembrarTasaDeRol(jdbc, producto, MANAGER, "0.00");

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("RESUELTA"))
        .andExpect(jsonPath("$.value").value(0));
  }

  @Test
  @DisplayName("una tasa RETIRADA deja de resolver: el producto deja de pagar a ese rol")
  void laRetiradaNoResuelve() throws Exception {
    UUID tasa = CommissionFixtures.sembrarTasaDeRol(jdbc, producto, MANAGER, "10.00");
    jdbc.update(
        "UPDATE commission_rates SET deleted_at = now() WHERE id = CAST(? AS uuid)",
        tasa.toString());

    // Es exactamente el estado que `RF-CM-004` se niega a producir: el producto
    // deja de comisionar y nada lo indica. Aquí se siembra a mano para dejar
    // constancia de por qué esa negativa existe.
    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("SIN_TARIFA"));
  }

  @Test
  @DisplayName("un producto RETIRADO se resuelve con normalidad")
  void elProductoRetiradoResuelve() throws Exception {
    UUID retirado = CommissionFixtures.sembrarProducto(jdbc, "BOT_Z", true);
    UUID tasa = CommissionFixtures.sembrarTasaDeRol(jdbc, retirado, MANAGER, "10.00");

    // Preguntar qué se pagaba por algo que ya no se vende es legítimo: es la
    // consulta que una liquidación atrasada necesita.
    mvc.perform(efectiva(vendedora, retirado, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("RESUELTA"));
  }

  @Test
  @DisplayName("sin `onDate` se resuelve con la fecha de hoy")
  void sinFecha() throws Exception {
    personalizadaSobre(vendedora, producto, "18.00", "2020-01-01", null);

    mvc.perform(
            get("/api/v1/commissions/effective")
                .param("userId", vendedora.toString())
                .param("productId", producto.toString())
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("RESUELTA"))
        .andExpect(jsonPath("$.onDate").exists());
  }

  @Test
  @DisplayName("la persona y el producto inexistentes se distinguen con 422")
  void inexistentes() throws Exception {
    mvc.perform(efectiva(UUID.randomUUID(), producto, "2026-05-01"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));

    mvc.perform(efectiva(vendedora, UUID.randomUUID(), "2026-05-01"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
  }

  @Test
  @DisplayName("resolver exige commissions:read")
  void exigeElPermiso() throws Exception {
    mvc.perform(
            get("/api/v1/commissions/effective")
                .param("userId", vendedora.toString())
                .param("productId", producto.toString())
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:create")))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // El valor fijo (`cm.md` v0.7.0)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-CM-100 · resuelve un IMPORTE FIJO, con la forma junto al valor")
  void resuelveUnImporteFijo() throws Exception {
    UUID tasa = CommissionFixtures.sembrarTasaDeRol(jdbc, producto, MANAGER, "FIJO", "10000");

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("RESUELTA"))
        .andExpect(jsonPath("$.rateType").value("FIJO"))
        // UN solo campo de valor, al revés que en el catálogo. Con dos, el nulo
        // tendría dos causas y la distinción entre lo pensado y lo olvidado
        // dejaría de poder escribirse.
        .andExpect(jsonPath("$.value").value(10000))
        .andExpect(jsonPath("$.percentage").doesNotExist())
        .andExpect(jsonPath("$.fixedAmount").doesNotExist());
  }

  @Test
  @DisplayName("CA-CM-101 · la PRECEDENCIA no cambia con las formas, en las dos direcciones")
  void laPrecedenciaSobreviveALasFormas() throws Exception {
    // EL CRITERIO QUE PROTEGE LO VIEJO DE LO NUEVO. Las pruebas de precedencia
    // que ya existen usan LA MISMA FORMA en las dos ramas, porque cuando se
    // escribieron solo había una: si al añadir las columnas alguien invirtiera
    // las ramas del `UNION ALL`, TODAS SEGUIRÍAN PASANDO y la consulta
    // devolvería la tasa equivocada con una cifra plausible.
    //
    // Aquí las formas se cruzan a propósito.

    // Personalizada en PORCENTAJE contra rol en FIJO: gana la personalizada.
    UUID deRol = CommissionFixtures.sembrarTasaDeRol(jdbc, producto, MANAGER, "FIJO", "10000");
    personalizadaSobre(vendedora, producto, "PORCENTAJE", "18.00", "2026-01-01", null);

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.source").value("PERSONALIZADA"))
        .andExpect(jsonPath("$.rateType").value("PORCENTAJE"))
        .andExpect(jsonPath("$.value").value(18.00));

    // Y al revés: personalizada en FIJO contra rol en PORCENTAJE.
    jdbc.update("DELETE FROM user_commission_rates");
    jdbc.update("DELETE FROM commission_rates");
    UUID enPorcentaje =
        CommissionFixtures.sembrarTasaDeRol(jdbc, producto, MANAGER, "PORCENTAJE", "10.00");
    personalizadaSobre(vendedora, producto, "FIJO", "5000", "2026-01-01", null);

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.source").value("PERSONALIZADA"))
        .andExpect(jsonPath("$.rateType").value("FIJO"))
        .andExpect(jsonPath("$.value").value(5000));
  }

  @Test
  @DisplayName("CA-CM-102 · sin tasa, la forma y el valor llegan NULOS Y PRESENTES")
  void sinTasaElNuloSigueTeniendoUnaSolaCausa() throws Exception {
    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("SIN_TARIFA"))
        .andExpect(jsonPath("$.rateType").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.value").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("CA-CM-103 · el CERO en importe fijo resuelve, y se distingue de no tener tasa")
  void elCeroEnImporteFijo() throws Exception {
    UUID tasa = CommissionFixtures.sembrarTasaDeRol(jdbc, producto, MANAGER, "FIJO", "0");

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        // RESUELTA con cero: alguien DECIDIÓ que esto no comisiona. Es lo
        // contrario de `sinTasaElNuloSigueTeniendoUnaSolaCausa`, donde nadie lo
        // decidió — y devolver cero allí haría indistinguible lo pensado de lo
        // olvidado.
        .andExpect(jsonPath("$.outcome").value("RESUELTA"))
        .andExpect(jsonPath("$.rateType").value("FIJO"))
        .andExpect(jsonPath("$.value").value(0));
  }

  @Test
  @DisplayName(
      "CA-CM-104 · un importe fijo se devuelve SIN MONEDA, y eso es lo que hay que vigilar")
  void elImporteFijoNoLlevaMoneda() throws Exception {
    // ESTA PRUEBA AFIRMA QUE EL SISTEMA NO DICE ALGO QUE PODRÍA DECIR. La
    // consulta RECIBE el producto: es el primer y único punto donde un importe
    // fijo y su moneda existen a la vez. Se preguntó al responsable del proyecto
    // el 02-09-2026 y se decidió que no la devuelva — hacerlo empezaría a
    // mezclar la tarifa con la venta (`cm.md` §1.4).
    //
    // La consecuencia es `RN-CM-017`: la misma tasa personalizada rige sobre
    // TODO el catálogo (`RN-CM-014`) y su importe se interpreta en tantas
    // monedas como haya. Eso NO SE PRUEBA CON DOS PRODUCTOS, y se intentó: la
    // sentencia de resolución no toca `products` ni `currencies`, de modo que
    // dos respuestas idénticas están garantizadas por construcción y comparar
    // dos monedas no verificaba nada. Lo único capaz de fallar es la línea de
    // abajo, el día que alguien revierta la decisión.
    personalizadaSobre(vendedora, producto, "FIJO", "10000", "2026-01-01", null);

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rateType").value("FIJO"))
        .andExpect(jsonPath("$.value").value(10000))
        .andExpect(jsonPath("$.currency").doesNotExist());
  }

  /**
   * Una tasa personalizada <b>sobre el producto</b> donde debe regir.
   *
   * <p>Desde el 16-09-2026 la tasa nace con él (`RN-CM-021`); del 11-09-2026 al 16-09-2026 había
   * que asociarla después, y una prueba que se saltara ese paso estaba comprobando que no se pagaba
   * nada.
   */
  private UUID personalizadaSobre(
      UUID persona, UUID enProducto, String porcentaje, String desde, String hasta) {
    return CommissionFixtures.sembrarTasaPersonal(
        jdbc, persona, enProducto, porcentaje, desde, hasta);
  }

  /** La misma, en la forma que se pida. */
  private UUID personalizadaSobre(
      UUID persona, UUID enProducto, String forma, String valor, String desde, String hasta) {
    return CommissionFixtures.sembrarTasaPersonal(
        jdbc, persona, enProducto, forma, valor, desde, hasta);
  }

  private MockHttpServletRequestBuilder efectiva(UUID persona, UUID producto, String fecha) {
    return get("/api/v1/commissions/effective")
        .param("userId", persona.toString())
        .param("productId", producto.toString())
        .param("onDate", fecha)
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read"));
  }
}
