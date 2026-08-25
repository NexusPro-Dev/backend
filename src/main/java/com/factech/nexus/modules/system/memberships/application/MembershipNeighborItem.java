package com.factech.nexus.modules.system.memberships.application;

import java.util.UUID;

/**
 * Un vecino inmediato en el detalle de una membresía (`RF-SP-018`).
 *
 * <p><b>Llega solo hasta el primer grado, y no trae sus propios vecinos.</b> Anidarlos convertiría
 * la respuesta en la cadena completa por un camino distinto, y su profundidad dependería de dónde
 * estuviera la membresía consultada. La cadena entera la trae `RF-SP-017` en una sola llamada.
 *
 * <p>Trae {@code level} porque es lo que permite leer la posición sin comparar identificadores. En
 * una cadena bien formada el de la superior es siempre {@code level - 1} y el de la hija {@code
 * level + 1}; devolverlos igualmente es lo que hace la incoherencia visible en lugar de invisible.
 */
public record MembershipNeighborItem(UUID id, String code, String name, int level) {}
