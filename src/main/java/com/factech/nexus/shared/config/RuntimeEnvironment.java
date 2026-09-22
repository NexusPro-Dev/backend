package com.factech.nexus.shared.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * El {@link Environment} de esta instancia, resuelto una sola vez al construir el contexto.
 *
 * <p><b>Falla el arranque si el valor no se entiende</b>, y lo hace aquí y no en el primer sitio
 * que pregunte: un valor equivocado tiene que descubrirse cuando el proceso levanta —donde alguien
 * está mirando— y no la primera vez que algo consulte el entorno, que puede ser nunca.
 *
 * <p>Se deja como un componente propio en lugar de repartir {@code @Value("${nexus.environment}")}
 * por ahí para que la traducción de la cadena a un valor del dominio ocurra <b>una vez</b>. Con la
 * cadena suelta, cada punto que la comparase tendría su propia idea de qué es «producción», y
 * bastaría con que uno de ellos escribiera la comparación al revés.
 */
@Component
public class RuntimeEnvironment {

  private static final Logger LOG = LoggerFactory.getLogger(RuntimeEnvironment.class);

  private final Environment entorno;

  public RuntimeEnvironment(@Value("${nexus.environment:}") String declarado) {
    this.entorno = Environment.desdeConfiguracion(declarado);
    LOG.info("Entorno de ejecución: {}", entorno.name().toLowerCase());
  }

  public Environment valor() {
    return entorno;
  }

  /** Si esta instancia es el sistema real. */
  public boolean esProduccion() {
    return entorno.esProduccion();
  }
}
