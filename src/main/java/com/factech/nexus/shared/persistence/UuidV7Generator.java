package com.factech.nexus.shared.persistence;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Generación de identificadores UUID v7 (`RF-SP-001` · `T-05`).
 *
 * <p><b>La clave primaria se genera en la aplicación, nunca en la base de datos</b> (Art. V.11,
 * {@code architecture.md} §6.3). El motivo práctico es que el caso de uso necesita el identificador
 * <i>antes</i> de confirmar la transacción: la fila de {@code audit_change_log} lleva el {@code
 * entity_id} del rol y la cabecera {@code Location} de la respuesta lo lleva en la URL. Con {@code
 * gen_random_uuid()} habría que insertar, releer y solo entonces poder auditar.
 *
 * <p><b>Por qué v7 y no v4.</b> Un UUID v7 lleva la marca de tiempo en sus bits más significativos,
 * de modo que los identificadores generados en secuencia son monótonamente crecientes. Un índice
 * B-tree sobre una clave así inserta siempre al final; con v4, cada inserción cae en una hoja al
 * azar y el índice se fragmenta. La contrapartida —el instante de creación es deducible del
 * identificador— es irrelevante aquí: {@code created_at} ya lo publica.
 *
 * <p><b>Es un componente y no un método estático</b> para que quien lo use lo reciba por
 * constructor y una prueba pueda sustituirlo. Un {@code UUID.randomUUID()} esparcido por el código
 * no se puede fijar, y sin poder fijarlo las pruebas que comparan identificadores no son
 * deterministas.
 */
@Component
public class UuidV7Generator {

  /**
   * El generador de la biblioteca es seguro para uso concurrente y mantiene el contador que
   * garantiza la monotonía dentro del mismo milisegundo. Se conserva una sola instancia: crear uno
   * por llamada reiniciaría ese contador y dos identificadores del mismo milisegundo podrían salir
   * desordenados.
   */
  private final TimeBasedEpochGenerator generator = Generators.timeBasedEpochGenerator();

  /**
   * Devuelve un identificador nuevo, versión 7 y variante RFC 9562.
   *
   * @return identificador único y creciente en el tiempo
   */
  public UUID next() {
    return generator.generate();
  }
}
