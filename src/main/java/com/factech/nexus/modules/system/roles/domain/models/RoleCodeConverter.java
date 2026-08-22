package com.factech.nexus.modules.system.roles.domain.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persiste {@link RoleCode} como la columna {@code varchar(50)} que declara el esquema.
 *
 * <p>Es lo que permite que el agregado hable de códigos y no de cadenas sin que la tabla cambie.
 * {@code autoApply = false}: se aplica con {@code @Convert} donde corresponde, para que declarar un
 * {@code RoleCode} en cualquier otro sitio no arrastre una conversión implícita.
 *
 * <p><b>La lectura no valida.</b> Al traer una fila de la base de datos se construye el objeto de
 * valor por su constructor, que sí valida — y eso es deliberado: si una fila incumpliera {@code
 * ck_roles_code_format} habría un defecto de integridad que conviene que salte al leerlo, no que se
 * propague en silencio.
 */
@Converter(autoApply = false)
public class RoleCodeConverter implements AttributeConverter<RoleCode, String> {

  @Override
  public String convertToDatabaseColumn(RoleCode codigo) {
    return codigo == null ? null : codigo.value();
  }

  @Override
  public RoleCode convertToEntityAttribute(String valor) {
    return valor == null ? null : new RoleCode(valor);
  }
}
