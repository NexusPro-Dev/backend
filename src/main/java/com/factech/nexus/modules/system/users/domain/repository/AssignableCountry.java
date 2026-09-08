package com.factech.nexus.modules.system.users.domain.repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Lo único que este módulo necesita saber del catálogo de países (`RN-SP-034`).
 *
 * <p>Un puerto deliberadamente estrecho, igual que {@link MembershipCatalog}: asignar un país a una
 * persona no requiere conocer el agregado {@code Country} ni su ciclo de vida. Declararlo así
 * impide que la lógica de personas empiece a razonar sobre el catálogo, que es competencia de
 * `RF-SP-020` a `RF-SP-022`.
 *
 * <p><b>Resuelve por identificador y por código, y no son dos puertos.</b> `RF-SP-024` y
 * `RF-SP-027` reciben el identificador —el criterio de sus cuerpos, que no mezclan dos espacios de
 * identificación—; `RF-SP-045` recibe el <b>código alfa-3</b>, porque es un formulario público al
 * que no se le pide conocer identificadores internos y porque `RN-SP-009` hace que ese código no
 * cambie jamás. Partirlo en dos puertos dejaría la misma regla escrita dos veces.
 *
 * <p><b>El estado activo viaja en el registro y NO se filtra en la consulta</b>, y esa es la
 * decisión que gobierna este puerto. Un {@code findActiveById} devolvería vacío en los dos casos
 * que hay que distinguir —«no existe» y «está inactivo»— y `RF-SP-024` `EX-009` los responde con
 * códigos distintos: {@code 422} el primero, porque es una referencia que no resuelve, y {@code
 * 409} el segundo, porque resuelve y una regla de negocio lo rechaza. El cliente los corrige de
 * forma distinta, de modo que el puerto tiene que poder decir cuál de los dos es.
 */
public interface AssignableCountry {

  /** El país del catálogo, o vacío si el identificador no designa ninguno. */
  Optional<CountryRef> find(UUID countryId);

  /**
   * El país por su código ISO 3166-1 alfa-3, para el registro público de `RF-SP-045`.
   *
   * <p><b>Normaliza a mayúsculas y recorta antes de buscar.</b> Un formulario público recibe {@code
   * col} y {@code Col}, y {@code ck_countries_code_format} solo admite mayúsculas: rechazar por la
   * caja sería rechazar por algo que el sistema puede arreglar sin ambigüedad, que es el mismo
   * trato que el correo recibe en el alta.
   */
  Optional<CountryRef> findByCode(String code);

  /**
   * Un país del catálogo, con lo justo para verificarlo y para nombrarlo en una respuesta.
   *
   * <p>{@code active} está aquí y no en el nombre del método por lo dicho arriba: quien llama tiene
   * que poder separar «no existe» de «está inactivo».
   */
  record CountryRef(UUID id, String code, String name, boolean active) {}
}
