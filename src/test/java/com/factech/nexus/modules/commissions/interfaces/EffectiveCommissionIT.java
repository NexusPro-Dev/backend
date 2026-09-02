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
 * sobre todo <b>la ausencia cambió de significado</b>: una tasa de rol que existe en el catálogo y
 * no está asociada al producto <b>no paga nada</b>, donde antes habría pagado como tarifa por
 * omisión.
 *
 * <p>La prueba de {@code laTasaSinAsociarNoPaga} es la que clava esa inversión. Si alguien la
 * deshiciera, el sistema empezaría a pagar por productos que nadie configuró — y no fallaría.
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

  @AfterEach
  void devolverElEstadoASuSitio() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
  }

  @Test
  @DisplayName("resuelve la tasa del rol cuando está ASOCIADA a ese producto")
  void resuelvePorElRol() throws Exception {
    UUID tasa = CommissionFixtures.sembrarTasaDeRol(jdbc, MANAGER, "10.00");
    CommissionFixtures.asociar(jdbc, tasa, producto, MANAGER);

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("RESUELTA"))
        .andExpect(jsonPath("$.source").value("ROL"))
        .andExpect(jsonPath("$.percentage").value(10.00))
        .andExpect(jsonPath("$.rateId").value(tasa.toString()))
        // Las de rol no tienen vigencia, y estos nulos lo dicen.
        .andExpect(jsonPath("$.validFrom").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("`RN-CM-012` — LA TASA SIN ASOCIAR NO PAGA NADA, donde antes era la tarifa de todos")
  void laTasaSinAsociarNoPaga() throws Exception {
    // Existe, es del rol correcto, tiene porcentaje... y nadie la asoció.
    CommissionFixtures.sembrarTasaDeRol(jdbc, MANAGER, "10.00");

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("SIN_TARIFA"))
        // NULO Y PRESENTE, nunca cero: cero es «no comisiona», que es una
        // decisión declarada, y la ausencia es que nadie la tomó.
        .andExpect(jsonPath("$.percentage").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("la asociación de OTRO producto no sirve para este")
  void laAsociacionEsPorProducto() throws Exception {
    UUID otro = CommissionFixtures.sembrarProducto(jdbc, "BOT_B");
    UUID tasa = CommissionFixtures.sembrarTasaDeRol(jdbc, MANAGER, "10.00");
    CommissionFixtures.asociar(jdbc, tasa, otro, MANAGER);

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("SIN_TARIFA"));
  }

  @Test
  @DisplayName("la asociación de OTRO rol no sirve para esta persona")
  void laAsociacionEsPorRol() throws Exception {
    UUID tasa = CommissionFixtures.sembrarTasaDeRol(jdbc, DIRECTOR, "4.00");
    CommissionFixtures.asociar(jdbc, tasa, producto, DIRECTOR);

    // La vendedora es MANAGER: el producto paga, pero no a ella.
    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("SIN_TARIFA"));
  }

  @Test
  @DisplayName("`RN-CM-004` — la personalizada GANA, y sin mirar el producto")
  void laPersonalizadaGana() throws Exception {
    UUID delRol = CommissionFixtures.sembrarTasaDeRol(jdbc, MANAGER, "10.00");
    CommissionFixtures.asociar(jdbc, delRol, producto, MANAGER);

    UUID personal =
        CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "18.00", "2026-01-01", null);

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("RESUELTA"))
        .andExpect(jsonPath("$.source").value("PERSONALIZADA"))
        .andExpect(jsonPath("$.percentage").value(18.00))
        .andExpect(jsonPath("$.rateId").value(personal.toString()));
  }

  @Test
  @DisplayName("la personalizada gana incluso sobre un producto SIN asociación")
  void laPersonalizadaIgnoraElProducto() throws Exception {
    CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "18.00", "2026-01-01", null);

    // El producto no paga a nadie, y ella cobra igual: gana lo mismo venda lo
    // que venda.
    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("RESUELTA"))
        .andExpect(jsonPath("$.source").value("PERSONALIZADA"));
  }

  @Test
  @DisplayName("la personalizada VENCIDA deja de ganar, y vuelve a mandar la del rol")
  void laPersonalizadaVencida() throws Exception {
    UUID delRol = CommissionFixtures.sembrarTasaDeRol(jdbc, MANAGER, "10.00");
    CommissionFixtures.asociar(jdbc, delRol, producto, MANAGER);
    CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "18.00", "2026-01-01", "2026-03-31");

    mvc.perform(efectiva(vendedora, producto, "2026-02-15"))
        .andExpect(jsonPath("$.source").value("PERSONALIZADA"));

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.source").value("ROL"))
        .andExpect(jsonPath("$.percentage").value(10.00));
  }

  @Test
  @DisplayName("QUIEN NO VENDE PUEDE COBRAR su personalizada: es lo que costó quitarle el rol")
  void laPersonalizadaSobreviveAlRol() throws Exception {
    UUID ajena = CommissionFixtures.sembrarPersonaConRol(jdbc, "ajena", null);
    CommissionFixtures.sembrarTasaPersonal(jdbc, ajena, "18.00", "2026-01-01", null);

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
        .andExpect(jsonPath("$.percentage").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.roleId").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("el porcentaje CERO resuelve, y no es lo mismo que no tener tasa")
  void elCeroResuelve() throws Exception {
    UUID tasa = CommissionFixtures.sembrarTasaDeRol(jdbc, MANAGER, "0.00");
    CommissionFixtures.asociar(jdbc, tasa, producto, MANAGER);

    mvc.perform(efectiva(vendedora, producto, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("RESUELTA"))
        .andExpect(jsonPath("$.percentage").value(0));
  }

  @Test
  @DisplayName("una tasa RETIRADA deja de resolver aunque su asociación exista")
  void laRetiradaNoResuelve() throws Exception {
    UUID tasa = CommissionFixtures.sembrarTasaDeRol(jdbc, MANAGER, "10.00");
    CommissionFixtures.asociar(jdbc, tasa, producto, MANAGER);
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
    UUID tasa = CommissionFixtures.sembrarTasaDeRol(jdbc, MANAGER, "10.00");
    CommissionFixtures.asociar(jdbc, tasa, retirado, MANAGER);

    // Preguntar qué se pagaba por algo que ya no se vende es legítimo: es la
    // consulta que una liquidación atrasada necesita.
    mvc.perform(efectiva(vendedora, retirado, "2026-05-01"))
        .andExpect(jsonPath("$.outcome").value("RESUELTA"));
  }

  @Test
  @DisplayName("sin `onDate` se resuelve con la fecha de hoy")
  void sinFecha() throws Exception {
    CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "18.00", "2020-01-01", null);

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

  private MockHttpServletRequestBuilder efectiva(UUID persona, UUID producto, String fecha) {
    return get("/api/v1/commissions/effective")
        .param("userId", persona.toString())
        .param("productId", producto.toString())
        .param("onDate", fecha)
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read"));
  }
}
