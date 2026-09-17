package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.domain.repository.MembershipCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * La membresía gratuita existe y se llama {@code BECA} (`RF-SP-045` · `T-10`, `CL-005`).
 *
 * <h2>Por qué una comprobación al arrancar y no un {@code if} en el caso de uso</h2>
 *
 * <p><b>Cuál es la membresía gratuita se decide por su CÓDIGO</b>, y fue una decisión del
 * responsable del proyecto el 01-09-2026: se eligió la convención sobre una columna explícita. Lo
 * que esa elección cuesta es exactamente esto — si alguien renombra la membresía, <b>nada
 * falla</b>: el registro por enlace empieza a rechazar con `EX-004` («ese producto exige un pago»)
 * todos los enlaces, para siempre, y el síntoma que llega es «el registro no funciona».
 *
 * <p>Esta comprobación convierte ese fallo silencioso en <b>un arranque que no ocurre</b>, que es
 * la única forma de enterarse el mismo día. Es la mitigación que la propia decisión declaró, y el
 * mismo patrón que {@code CurrencyCatalogStartupCheck} aplica a la moneda por defecto.
 *
 * <p><b>Vive en el módulo y no en {@code shared/config}</b>: la regla de `architecture.md` §5.3
 * prohíbe que la infraestructura transversal dependa de un módulo de negocio. Lo que la dispara es
 * implementar {@link ApplicationRunner}, no el paquete donde viva.
 */
@Component
public class FloorMembershipStartupCheck implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(FloorMembershipStartupCheck.class);

  /** El mismo literal que usa {@link RegisterClientByLinkService}. */
  private static final String MEMBRESIA_GRATUITA = "BECA";

  private final MembershipCatalog membresias;

  public FloorMembershipStartupCheck(MembershipCatalog membresias) {
    this.membresias = membresias;
  }

  @Override
  public void run(ApplicationArguments args) {
    String codigo;
    try {
      // El SUELO de la cadena, que es la que `RN-SP-018` reparte a toda persona.
      codigo = membresias.floor().code();
    } catch (RuntimeException catalogoVacio) {
      // EL CATÁLOGO VACÍO NO ES LO QUE ESTA COMPROBACIÓN VIGILA, y por eso no
      // tumba el arranque: en un entorno real no puede ocurrir —`V46` la siembra
      // y la guarda de `V57` aborta si falta—, y donde sí ocurre es entre dos
      // clases de la suite que vacían `memberships` para sembrar la suya. Fallar
      // ahí rompería decenas de pruebas por una condición que no es la vigilada.
      LOG.warn(
          "No hay membresía de arranque que verificar. En un entorno real esto no puede pasar:"
              + " `V57` aborta si falta. Se continúa sin comprobar el código.");
      return;
    }

    if (!MEMBRESIA_GRATUITA.equalsIgnoreCase(codigo)) {
      throw new IllegalStateException(
          "La membresía del suelo de la cadena se llama '"
              + codigo
              + "' y no '"
              + MEMBRESIA_GRATUITA
              + "'. El registro por enlace (RF-SP-045) decide por ese código cuál es la membresía"
              + " gratuita: con otro nombre rechazaría TODOS los enlaces con «ese producto exige un"
              + " pago», sin que nada más fallara.");
    }
    LOG.info("Membresía gratuita verificada: el suelo de la cadena es '{}'.", codigo);
  }
}
