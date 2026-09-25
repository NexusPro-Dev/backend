package com.factech.nexus.shared.images;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que devuelven las operaciones de la portada —del producto y, desde `RF-PM-028`, del paquete—:
 * qué imagen había, y el diff.
 *
 * <p>Nació anidado en {@link Product} y salió a tipo propio cuando el paquete ganó portada: un
 * paquete no debe devolver un tipo llamado {@code Product.CambioDePortada}. El diff tiene siempre
 * la misma forma, {@code cover_image_id} con antes y después, y por eso se arma aquí y no en cada
 * agregado.
 *
 * @param anterior la imagen que dejó de ser portada, o nulo si no había; es la que el caso de uso
 *     tiene que borrar <b>después</b> de volcar el cambio, por la clave foránea
 * @param cambios el diff de auditoría: {@code cover_image_id} con antes y después, o vacío si no
 *     cambió nada
 */
public record CambioDePortada(UUID anterior, Map<String, Object> cambios) {

  /** No había portada: nada que borrar, nada que auditar. */
  public static CambioDePortada ninguno() {
    return new CambioDePortada(null, Map.of());
  }

  /** La portada pasó de {@code antes} a {@code despues}; cualquiera de los dos puede ser nulo. */
  public static CambioDePortada de(UUID antes, UUID despues) {
    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put(
        "cover_image_id",
        Map.of(
            "before", antes == null ? "" : antes.toString(),
            "after", despues == null ? "" : despues.toString()));
    return new CambioDePortada(antes, cambios);
  }

  public boolean huboCambio() {
    return !cambios.isEmpty();
  }
}
