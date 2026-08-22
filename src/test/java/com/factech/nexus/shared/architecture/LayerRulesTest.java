package com.factech.nexus.shared.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reglas de dependencia entre capas (`architecture.md` §5.2, `RF-SP-001` · `T-20`).
 *
 * <p>Sin esta prueba, las reglas de capa dependen de la disciplina de cada revisión — y una
 * dependencia prohibida se cuela el día que alguien tiene prisa.
 *
 * <p><b>Alcance recortado respecto de lo que pedía `plan.md`.</b> `T-20` se escribió cuando §5.2
 * exigía que {@code domain} no conociera ningún framework, y pedía que esta prueba fallara si
 * {@code domain} importaba JPA. El 22-08-2026 §5.1 cambió y sitúa las entidades JPA en {@code
 * domain/models} y los adaptadores de persistencia en {@code domain/repository}, declarando de
 * forma expresa que los planes de `RF-SP-001` a `RF-SP-009` quedaron sobre la disposición anterior
 * y que <i>«o se reescriben esas tareas, o la regla de ArchUnit se acota a lo que esta sección
 * permite»</i>. Se toma la segunda vía, que es la que el documento transversal admite y la que
 * sigue el código ya implantado de `RF-SP-010`.
 *
 * <p>Lo que se conserva es lo que sigue siendo cierto y sigue importando: el modelo de dominio no
 * conoce HTTP ni el contenedor de Spring, y los controladores no alcanzan la persistencia.
 */
class LayerRulesTest {

  private static JavaClasses clases;

  @BeforeAll
  static void importar() {
    clases =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.factech.nexus");
  }

  @Test
  @DisplayName("domain/models no conoce HTTP: ni el servlet ni el web de Spring")
  void elDominioNoConoceHttp() {
    // Una regla de negocio que dependiera del servlet no podría probarse sin
    // levantar un contenedor, y acabaría decidiendo códigos de estado — que es
    // competencia exclusiva del manejador global.
    noClasses()
        .that()
        .resideInAPackage("..domain.models..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("jakarta.servlet..", "org.springframework.web..")
        .because(
            "el modelo de dominio no decide códigos de estado ni conoce el transporte"
                + " (architecture.md §5.2)")
        .check(clases);
  }

  @Test
  @DisplayName("domain/models no depende del contenedor de Spring")
  void elDominioNoDependeDelContenedor() {
    // Se excluyen los subpaquetes de Spring que sí admite la disposición
    // vigente: ninguno. Las entidades usan jakarta.persistence, que §5.1
    // permite desde el 22-08-2026, pero no beans ni estereotipos.
    noClasses()
        .that()
        .resideInAPackage("..domain.models..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework.stereotype..",
            "org.springframework.beans..",
            "org.springframework.context..",
            "org.springframework.security..")
        .because("el agregado se construye con su fábrica, no lo inyecta nadie")
        .check(clases);
  }

  @Test
  @DisplayName("interfaces no alcanza domain/repository")
  void losControladoresNoTocanLaPersistencia() {
    // La tabla de §5.2 lo dice de forma explícita: `interfaces` puede depender
    // de `application` y de `domain/service`, nunca de `domain/repository`.
    // Un controlador que consulta el repositorio se salta la transacción y la
    // auditoría del caso de uso.
    noClasses()
        .that()
        .resideInAPackage("..interfaces..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..domain.repository..")
        .because("el controlador orquesta casos de uso, no persistencia (architecture.md §5.2)")
        .check(clases);
  }

  @Test
  @DisplayName("application no depende de la persistencia ni del transporte")
  void laCapaSinDependencias() {
    // `application` es el lenguaje común entre `interfaces` y `domain`: que no
    // dependa de ninguno de los dos es lo que le permite serlo. Las
    // anotaciones de Bean Validation y de Jackson sí viven aquí, porque §5.1
    // sitúa en esta capa los DTO de la API.
    noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "jakarta.persistence..", "org.hibernate..", "jakarta.servlet..", "..interfaces..")
        .because(
            "application no conoce ni la base de datos ni el transporte (architecture.md §5.2)")
        .check(clases);
  }

  @Test
  @DisplayName("shared no depende de ningún módulo de negocio")
  void loCompartidoNoConoceALosModulos() {
    // Si `shared` conociera un módulo, dejaría de ser transversal: el módulo
    // que se incorporara después heredaría una dependencia hacia otro que no
    // le corresponde (architecture.md §5.1, §5.3).
    noClasses()
        .that()
        .resideInAPackage("com.factech.nexus.shared..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.factech.nexus.modules..")
        .because("la infraestructura transversal no puede depender de quien la usa")
        .check(clases);
  }
}
