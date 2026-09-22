package com.factech.nexus.modules.products.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Los tres estados de un campo de `PATCH` (`RF-PM-004` · `T-01`).
 *
 * <p><b>Es la tarea que ya falló una vez en este proyecto</b>, en `RF-SP-027`, y falló <b>en
 * silencio</b>: con {@code Optional}, Jackson entrega {@code Optional.empty()} tanto para el campo
 * ausente como para el nulo explícito, y los dos estados que hay que separar se funden en uno. El
 * síntoma no se parecía a la causa — enviar solo el nombre rechazaba la petición por «apellido
 * vacío».
 *
 * <p>Si estos tres estados no se distinguen, <b>todo lo demás se construye sobre arena</b>: la
 * descripción no podría vaciarse y el nombre podría vaciarse sin querer.
 */
class UpdateProductRequestTest {

  private final ObjectMapper json = new ObjectMapper();

  @Test
  @DisplayName("AUSENTE — el campo que no viene en el cuerpo no está presente")
  void ausente() throws Exception {
    UpdateProductRequest peticion = leer("{\"name\":\"Ascenso a Oro\"}");

    assertThat(peticion.name().presente()).isTrue();
    assertThat(peticion.description().presente()).as("no venía: no se toca").isFalse();
    assertThat(peticion.price().presente()).isFalse();
    assertThat(peticion.currencyId().presente()).isFalse();
    assertThat(peticion.validityDays().presente()).isFalse();
  }

  @Test
  @DisplayName("PRESENTE Y NULO — el nulo explícito es una orden, no una ausencia")
  void presenteYNulo() throws Exception {
    UpdateProductRequest peticion = leer("{\"description\":null,\"validityDays\":null}");

    assertThat(peticion.description().presente()).isTrue();
    assertThat(peticion.description().valor()).isNull();
    assertThat(peticion.validityDays().presente()).isTrue();
    assertThat(peticion.validityDays().valor()).isNull();
    // Y lo que no vino sigue sin venir: es la distinción entera en una prueba.
    assertThat(peticion.name().presente()).isFalse();
  }

  @Test
  @DisplayName("PRESENTE CON VALOR — y del tipo que le toca, no del nodo JSON en crudo")
  void presenteConValor() throws Exception {
    UUID moneda = UUID.randomUUID();
    UpdateProductRequest peticion =
        leer(
            """
            {"name":"Ascenso a Oro","description":"Sube de nivel.","price":49.99,
             "currencyId":"%s","validityDays":30}
            """
                .formatted(moneda));

    assertThat(peticion.name().valor()).isEqualTo("Ascenso a Oro");
    assertThat(peticion.description().valor()).isEqualTo("Sube de nivel.");
    // El deserializador genérico resuelve el tipo interno por contexto: sin eso
    // el precio llegaría como nodo JSON y el identificador como texto.
    assertThat(peticion.price().valor()).isEqualByComparingTo(new BigDecimal("49.99"));
    assertThat(peticion.currencyId().valor()).isEqualTo(moneda);
    assertThat(peticion.validityDays().valor()).isEqualTo(30);
  }

  @Test
  @DisplayName("el cuerpo vacío deja los ocho campos ausentes, sin un solo nulo suelto")
  void cuerpoVacio() throws Exception {
    UpdateProductRequest peticion = leer("{}");

    assertThat(peticion.informaAlgo()).isFalse();
    assertThat(peticion.traeInmutables()).isFalse();
    // Ninguno es nulo: el constructor compacto los convierte en «ausente», y
    // eso es lo que permite que el resto del código no compruebe nulos.
    assertThat(peticion.name()).isNotNull();
    assertThat(peticion.type()).isNotNull();
  }

  @Test
  @DisplayName("`VAL-006` — los tres inmutables se detectan aunque lleguen en nulo")
  void detectaLosInmutables() throws Exception {
    assertThat(leer("{\"type\":\"BOT\"}").traeInmutables()).isTrue();
    assertThat(leer("{\"code\":\"OTRO\"}").traeInmutables()).isTrue();
    assertThat(leer("{\"targetMembershipId\":null}").traeInmutables())
        .as("enviarlo en nulo también es intentar cambiarlo")
        .isTrue();
  }

  @Test
  @DisplayName("`informaAlgo` distingue «no envió nada» de «envió un vaciado»")
  void informaAlgo() throws Exception {
    assertThat(leer("{}").informaAlgo()).isFalse();
    // Vaciar la descripción SÍ es informar algo: es una orden de borrado.
    assertThat(leer("{\"description\":null}").informaAlgo()).isTrue();
  }

  private UpdateProductRequest leer(String cuerpo) throws Exception {
    return json.readValue(cuerpo, UpdateProductRequest.class);
  }
}
