package com.factech.nexus.modules.system.users.domain.repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Lo único que este módulo necesita saber del catálogo de tipos de documento (`RN-SP-035`).
 *
 * <p>Gemelo de {@link AssignableCountry}, y todo lo que aquel documenta vale aquí: es un puerto
 * estrecho de `SP` hacia su propio catálogo y <b>no el repositorio de `RF-SP-051`</b>, porque el
 * alta de una persona no lee el catálogo entero para comprobar una fila.
 *
 * <p>Resuelve <b>por identificador y por abreviación</b>: `RF-SP-024` y `RF-SP-027` reciben el
 * identificador —el criterio de sus cuerpos, que no mezclan dos espacios de identificación— y
 * `RF-SP-045` recibe la <b>abreviación</b>, porque es un formulario público al que no se le pide
 * conocer identificadores internos.
 *
 * <p><b>Y aquí está la parte que no se ve: este puerto NO comprueba la mayoría de edad, y no porque
 * se le haya olvidado.</b> El catálogo al que consulta <b>solo contiene documentos de adulto</b>
 * (`RN-SP-035`, `V70`), de modo que un tipo que no acredite mayoría de edad sencillamente no está —
 * y este puerto lo reporta como inexistente, igual que una abreviación inventada. No hay ninguna
 * rama que decir «este es de menor»: la regla vive en el contenido de la tabla, no en el código que
 * la lee.
 */
public interface AssignableDocumentType {

  /** El tipo de documento del catálogo, o vacío si el identificador no designa ninguno. */
  Optional<DocumentTypeRef> find(UUID documentTypeId);

  /**
   * El tipo por su abreviación, para el registro público de `RF-SP-045`.
   *
   * <p><b>Normaliza a mayúsculas y recorta antes de buscar</b>, con el mismo criterio que {@code
   * AssignableCountry.findByCode}: un formulario público recibe {@code cc} y {@code Cc}, y {@code
   * ck_document_types_abbreviation_format} solo admite mayúsculas.
   */
  Optional<DocumentTypeRef> findByAbbreviation(String abbreviation);

  /**
   * Un tipo del catálogo, con lo justo para verificarlo y nombrarlo en una respuesta.
   *
   * <p>{@code active} viaja en el registro y no se filtra en la consulta, por lo mismo que en
   * {@link AssignableCountry}: quien llama tiene que poder separar «no existe» —{@code 422}— de
   * «está inactivo» —{@code 409}—, y un {@code findActiveById} devolvería vacío en los dos casos.
   */
  record DocumentTypeRef(UUID id, String abbreviation, String name, boolean active) {}
}
