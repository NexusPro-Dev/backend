package com.factech.nexus.modules.system.users.application;

import com.factech.nexus.modules.system.users.domain.repository.ClientSellerRepository.SellerClientRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un cliente de la cartera de un vendedor (`RF-SP-061` §6.2), fila de la página que devuelven
 * {@code GET /users/me/clients} y {@code GET /users/{id}/clients}.
 *
 * <p><b>Con {@code id} y con {@code status}, al contrario que {@link
 * ClientSellersResponse.SellerItem} y por la razón inversa</b>: desde la cartera se abre la ficha
 * del cliente (`RF-SP-026`) y sin identificador no hay enlace —lo pidió el frontend el 21-09-2026—;
 * y una cartera se trabaja, de modo que hay que distinguir al que se registró y todavía no depositó
 * ({@code FTD_PENDIENTE}, `RN-SP-026`) del activo y del desactivado. Lo que se publica es lo que el
 * vendedor ya ve de esa persona en su detalle: ni correo, ni roles, ni membresía.
 *
 * <p>{@code principal} se deriva de {@code origin} y se publica igualmente, por lo mismo que en
 * `RF-SP-059`: es una regla de negocio y no se reparte entre los consumidores del contrato.
 */
public record SellerClientItem(
    @Schema(description = "Identificador del cliente, con el que se abre su ficha (`RF-SP-026`).")
        UUID id,
    String username,
    String firstName,
    String lastName,
    @Schema(
            description =
                "Estado del cliente, el mismo que publica `GET /api/v1/users`. `FTD_PENDIENTE`"
                    + " es quien se registró por el enlace y aún no depositó.",
            allowableValues = {"ACTIVO", "FTD_PENDIENTE", "INACTIVO", "BLOQUEADO"})
        String status,
    @Schema(
            description =
                "`REGISTRO` si el vendedor lo registró —es su principal—, `HOTLINK` si le vendió"
                    + " por su enlace.",
            allowableValues = {"REGISTRO", "HOTLINK"})
        String origin,
    @Schema(
            description =
                "`true` solo en la fila `REGISTRO`: el vendedor es el principal de este cliente.")
        boolean principal,
    @Schema(
            description =
                "Desde cuándo es su cliente. En las filas que `V20` trajo desde"
                    + " `user_supervisors`, desde cuándo colgaba de él allí.")
        OffsetDateTime linkedAt) {

  public static SellerClientItem de(SellerClientRow fila) {
    return new SellerClientItem(
        fila.clientId(),
        fila.username(),
        fila.firstName(),
        fila.lastName(),
        fila.status(),
        fila.origin(),
        fila.esPrincipal(),
        fila.linkedAt());
  }
}
