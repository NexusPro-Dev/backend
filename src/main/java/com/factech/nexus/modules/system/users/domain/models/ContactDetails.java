package com.factech.nexus.modules.system.users.domain.models;

import com.factech.nexus.shared.patch.Patchable;
import java.util.Optional;

/**
 * Los datos de contacto de una persona: dos teléfonos y la dirección (`RN-SP-037`).
 *
 * <p><b>Es el único sitio del dominio donde un nulo puede ser una orden</b>, y esa es la razón de
 * que exista como tipo en lugar de pasar cinco cadenas sueltas. El resto del sistema tiene una
 * regla simple y cómoda —el nulo explícito se rechaza, porque las columnas son {@code NOT NULL}— y
 * aquí deja de valer: la dirección, el complemento, la ciudad y el teléfono de la empresa son
 * <b>opcionales</b>, y «ya no vive ahí» o «ya no tengo ese número» son hechos que hay que poder
 * registrar.
 *
 * <h2>Los dos teléfonos NO comparten tipo, y esa es la decisión de esta clase</h2>
 *
 * <p>{@code phone} —el personal— es {@link Optional}: `RN-SP-037` lo hace <b>obligatorio</b>, de
 * modo que su nulo explícito se rechaza en el DTO y aquí solo llega como «no se envió». Modelarlo
 * como {@link Patchable} habría sugerido que se puede vaciar.
 *
 * <p>{@code companyPhone} —el de la empresa, desde el 10-09-2026— es {@link Patchable}, y con él
 * <b>un teléfono acepta por primera vez el nulo explícito</b>. Es la comprobación de que la línea
 * que separa las dos familias es la correcta: comparte tipo de dato, forma y validación con el
 * personal, y sin embargo cae del otro lado — porque <b>lo que decide no es qué dato es, sino si la
 * regla lo exige</b>. Con {@link Optional} el vaciado sería inexpresable, y `RF-SP-027` y
 * `RF-SP-044` no podrían borrarlo nunca una vez escrito.
 *
 * <p><b>Los dos se normalizan al construirse</b> —fuera espacios, guiones, puntos y paréntesis—,
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
    Patchable<String> companyPhone,
    Patchable<String> addressLine1,
    Patchable<String> addressLine2,
    Patchable<String> city) {

  public ContactDetails {
    phone = phone == null ? Optional.empty() : phone.map(ContactDetails::normalizarTelefono);
    companyPhone = normalizar(companyPhone);
    addressLine1 = recortar(addressLine1);
    addressLine2 = recortar(addressLine2);
    city = recortar(city);
  }

  /** El contacto que no informa nada: el de un alta que no declaró ninguno de los cinco. */
  public static ContactDetails ausente() {
    return new ContactDetails(
        Optional.empty(),
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.ausente());
  }

  /** ¿Se envió alguno de los cinco, con el valor que sea? */
  public boolean informaAlgo() {
    return phone.isPresent()
        || companyPhone.presente()
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
   * El teléfono de la empresa, normalizado, con el blanco convertido en <b>nulo explícito</b>.
   *
   * <p>Es la misma normalización del personal montada sobre {@link Patchable}, y el blanco recibe
   * el trato que ya reciben la dirección y la ciudad: enviar un teléfono de solo espacios es, en la
   * práctica, pedir que se borre. {@link #normalizarTelefono} ya devuelve nulo cuando no queda
   * ningún dígito, de modo que {@code "---"} también se entiende como un borrado y no llega al
   * motor a chocar con {@code ck_users_company_phone_format}.
   */
  private static Patchable<String> normalizar(Patchable<String> campo) {
    if (campo == null || !campo.presente() || campo.valor() == null) {
      return campo == null ? Patchable.ausente() : campo;
    }
    return Patchable.de(normalizarTelefono(campo.valor()));
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
