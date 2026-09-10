package com.factech.nexus.modules.system.users.domain.models;

import com.factech.nexus.shared.patch.Patchable;
import java.util.Optional;

/**
 * Los datos de contacto de una persona: teléfono y dirección (`RN-SP-037`).
 *
 * <p><b>Es el único sitio del dominio donde un nulo puede ser una orden</b>, y esa es la razón de
 * que exista como tipo en lugar de pasar cuatro cadenas sueltas. El resto del sistema tiene una
 * regla simple y cómoda —el nulo explícito se rechaza, porque las columnas son {@code NOT NULL}— y
 * aquí deja de valer: la dirección, el complemento y la ciudad son <b>opcionales</b>, y «ya no vive
 * ahí» es un hecho que hay que poder registrar.
 *
 * <p><b>El teléfono no comparte ese trato, y por eso no comparte tipo.</b> Es {@link Optional} y no
 * {@link Patchable}: `RN-SP-037` lo hace obligatorio, de modo que su nulo explícito se rechaza en
 * el DTO y aquí solo llega como «no se envió». Modelarlo igual que los otros tres habría sugerido
 * que se puede vaciar.
 *
 * <p><b>El teléfono se normaliza al construirse</b> —fuera espacios, guiones, puntos y paréntesis—,
 * igual que {@link Email}: la forma normalizada <b>es</b> el valor. Sin eso, {@code +57 300 123 45
 * 67} y {@code +573001234567} serían dos teléfonos distintos y {@code ck_users_phone_format}
 * rechazaría el primero.
 *
 * <p>Las tres líneas de dirección solo se <b>recortan</b>. No se comparan con nada ni participan en
 * ninguna restricción de forma: transformarlas más sería cambiar en silencio lo que la persona
 * escribió.
 */
public record ContactDetails(
    Optional<String> phone,
    Patchable<String> addressLine1,
    Patchable<String> addressLine2,
    Patchable<String> city) {

  public ContactDetails {
    phone = phone == null ? Optional.empty() : phone.map(ContactDetails::normalizarTelefono);
    addressLine1 = recortar(addressLine1);
    addressLine2 = recortar(addressLine2);
    city = recortar(city);
  }

  /** El contacto que no informa nada: el de un alta que no declaró ninguno de los cuatro. */
  public static ContactDetails ausente() {
    return new ContactDetails(
        Optional.empty(), Patchable.ausente(), Patchable.ausente(), Patchable.ausente());
  }

  /** ¿Se envió alguno de los cuatro, con el valor que sea? */
  public boolean informaAlgo() {
    return phone.isPresent()
        || addressLine1.presente()
        || addressLine2.presente()
        || city.presente();
  }

  /**
   * Fuera todo lo que no sea dígito, salvo un {@code +} inicial.
   *
   * <p>Un formulario recibe {@code (300) 123-4567} y {@code +57 300 123 4567}; el esquema admite
   * dígitos con un {@code +} opcional. Rechazar por la puntuación sería rechazar por algo que el
   * sistema puede arreglar sin ambigüedad — el mismo trato que el correo recibe en el alta.
   */
  private static String normalizarTelefono(String bruto) {
    if (bruto == null) {
      return null;
    }
    String limpio = bruto.trim();
    boolean internacional = limpio.startsWith("+");
    String digitos = limpio.replaceAll("[^0-9]", "");
    if (digitos.isEmpty()) {
      return null;
    }
    return internacional ? "+" + digitos : digitos;
  }

  /**
   * Recorta el valor si vino, y convierte el blanco en <b>nulo explícito</b>.
   *
   * <p>Es deliberado y no una comodidad: enviar una dirección de solo espacios es, en la práctica,
   * pedir que se borre. Tratarlo como valor dejaría pasar el blanco al motor, donde {@code
   * ck_users_contacto_not_blank} lo rechazaría con un {@code 500} sobre algo que aquí se entiende
   * perfectamente.
   */
  private static Patchable<String> recortar(Patchable<String> campo) {
    if (campo == null) {
      return Patchable.ausente();
    }
    if (!campo.presente() || campo.valor() == null) {
      return campo == null ? Patchable.ausente() : campo;
    }
    String contenido = campo.valor().trim();
    return Patchable.de(contenido.isEmpty() ? null : contenido);
  }
}
