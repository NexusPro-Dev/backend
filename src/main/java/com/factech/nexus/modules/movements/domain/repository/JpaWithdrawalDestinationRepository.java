package com.factech.nexus.modules.movements.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Adaptador de la copia del destino de un retiro. */
@Repository
public class JpaWithdrawalDestinationRepository implements WithdrawalDestinationRepository {

  private final EntityManager em;

  public JpaWithdrawalDestinationRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public void insert(UUID movementId, DestinationRow d) {
    em.createNativeQuery(
            """
            INSERT INTO withdrawal_destinations (movement_id, payout_account_id,
                institution_code, institution_name, institution_kind, account_type, number,
                holder_name, holder_document_type, holder_document_number)
            VALUES (:movimiento, :cuenta, :codigo, :nombre, :tipo, :tipoCuenta, :numero,
                    :titular, :tipoDocumento, :documento)
            """)
        .setParameter("movimiento", movementId)
        .setParameter("cuenta", d.payoutAccountId())
        .setParameter("codigo", d.institutionCode())
        .setParameter("nombre", d.institutionName())
        .setParameter("tipo", d.institutionKind())
        .setParameter("tipoCuenta", d.accountType())
        .setParameter("numero", d.number())
        .setParameter("titular", d.holderName())
        .setParameter("tipoDocumento", d.holderDocumentType())
        .setParameter("documento", d.holderDocumentNumber())
        .executeUpdate();
  }

  @Override
  public Optional<DestinationRow> findByMovement(UUID movementId) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT payout_account_id AS cuenta, institution_code AS codigo,
                       institution_name AS nombre, institution_kind AS tipo,
                       account_type AS tipo_cuenta, number AS numero, holder_name AS titular,
                       holder_document_type AS tipo_documento,
                       holder_document_number AS documento
                  FROM withdrawal_destinations
                 WHERE movement_id = :movimiento
                """,
                Tuple.class)
            .setParameter("movimiento", movementId)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            f ->
                new DestinationRow(
                    (UUID) f.get("cuenta"),
                    (String) f.get("codigo"),
                    (String) f.get("nombre"),
                    (String) f.get("tipo"),
                    (String) f.get("tipo_cuenta"),
                    (String) f.get("numero"),
                    (String) f.get("titular"),
                    (String) f.get("tipo_documento"),
                    (String) f.get("documento")));
  }
}
