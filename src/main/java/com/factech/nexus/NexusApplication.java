package com.factech.nexus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del backend de NEXUS.
 *
 * <p>La estructura del código se organiza por módulo de negocio bajo {@code modules}, con la
 * infraestructura transversal en {@code shared}. Las reglas de dependencia entre capas están en
 * {@code docs/architecture.md} §5.2 y las verifica una prueba de ArchUnit, no la disciplina.
 */
@SpringBootApplication
@org.springframework.boot.context.properties.ConfigurationPropertiesScan
public class NexusApplication {

  public static void main(String[] args) {
    SpringApplication.run(NexusApplication.class, args);
  }
}
