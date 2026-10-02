package com.factech.nexus.modules.system.users.application;

import java.util.Optional;
import java.util.UUID;

/**
 * <b>El titular de una cuenta de cobro</b>: lo que `SP` publica de una persona para que `MV` sepa a
 * nombre de quién se paga (`RN-MV-055`; `RF-MV-035` · `plan.md` §3).
 *
 * <p>El titular de una cuenta de cobro <b>es siempre su dueño</b>, y su nombre y su documento no se
 * escriben en la cuenta: se leen de aquí cada vez, de modo que si administración corrige el
 * documento, las cuentas lo reflejan. <b>El retiro sí lo copia</b> (`RN-MV-056`), por esta misma
 * lectura, en el instante en que se pide.
 *
 * <p><b>Una interfaz por pregunta</b> (`architecture.md` §15.2): {@link ClientCatalog} es de la
 * venta, y el documento no le hace falta a nadie que venda.
 */
public interface PayoutHolderLookup {

  /**
   * La persona, si existe y no está eliminada.
   *
   * @param id identificador de la persona; un valor nulo devuelve vacío en lugar de fallar
   */
  Optional<HolderView> holderOf(UUID id);

  /**
   * @param documentType la <b>abreviatura</b> del tipo de documento (`CC`, `CE`), o nula si la
   *     persona no tiene documento
   * @param documentNumber nulo exactamente cuando {@code documentType} lo es ({@code
   *     ck_users_document_pair})
   */
  record HolderView(
      UUID id,
      String firstName,
      String lastName,
      UUID countryId,
      String documentType,
      String documentNumber) {

    public String fullName() {
      return firstName + " " + lastName;
    }

    public boolean hasDocument() {
      return documentNumber != null;
    }
  }
}
