package com.factech.nexus.modules.system.brokers.domain.service;

import com.factech.nexus.modules.system.brokers.application.TeamBrokerAccountItem;
import com.factech.nexus.modules.system.brokers.domain.models.UserBrokerStatus;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountQueryRepository;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import com.factech.nexus.shared.security.CurrentActor;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las cuentas de broker del equipo directo del actor (`RF-SP-056`).
 *
 * <p><b>Es la misma pregunta que `RF-SP-055`, hecha del otro lado.</b> Aquel responde «¿qué cuentas
 * tiene esta persona?» y este «¿cómo va mi equipo?» — y son dos porque recorrer el equipo cliente a
 * cliente costaría <b>una llamada por persona</b> para pintar una sola lista.
 *
 * <p><b>Más estrecho que su hermano, y por eso no necesita su {@code 404}</b>: aquí el actor <b>no
 * puede nombrar a nadie</b>. El conjunto de datos lo determina el sistema a partir de quién
 * pregunta, de modo que no hay recurso ajeno que pedir ni existencia que ocultar. De ahí también
 * que no exija ningún permiso: no hay nada que acotar que la estructura no acote ya (`RN-SP-046`).
 *
 * <p><b>{@code broker-accounts:read} NO gobierna esta lectura.</b> Quien lo tenga no ve por aquí el
 * equipo de otro: para eso está `RF-SP-055`, persona a persona. Un {@code ?supervisorId=} sería un
 * requerimiento distinto y nadie lo ha pedido.
 *
 * <p><b>Un solo nivel</b>, como `RF-SP-042` y por el mismo motivo: el árbol descendente publicaría
 * la estructura entera de la empresa por una lectura de cuentas de broker.
 *
 * <p><b>Quien no tiene equipo recibe la página vacía</b>, no {@code 404} ni {@code 403}. «No tengo
 * a nadie a cargo» es una respuesta legítima, con el criterio que `RF-SP-042` ya aplica a quien no
 * pertenece a la fuerza comercial.
 */
@Service
public class GetTeamBrokerAccountsService {

  private final BrokerAccountQueryRepository cuentas;
  private final CurrentActor actor;
  private final Pagination paginacion;

  public GetTeamBrokerAccountsService(
      BrokerAccountQueryRepository cuentas, CurrentActor actor, Pagination paginacion) {
    this.cuentas = cuentas;
    this.actor = actor;
    this.paginacion = paginacion;
  }

  /**
   * El conteo y la página, <b>en la misma transacción de solo lectura</b>.
   *
   * <p>Separados, una reasignación simultánea daría un total que no cuadra con las filas.
   */
  @Transactional(readOnly = true)
  public PageResponse<TeamBrokerAccountItem> ofMyTeam(
      String estado, UUID brokerId, Integer pagina, Integer tamano) {

    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));

    Pagination.Slice trozo = paginacion.resolver(pagina, tamano);
    UserBrokerStatus filtro = filtroDeEstado(estado);

    int total = cuentas.countByTeamOf(quien, filtro, brokerId);
    List<TeamBrokerAccountItem> pagina1 =
        cuentas.findByTeamOf(quien, filtro, brokerId, trozo.offset(), trozo.size());

    return PageResponse.de(pagina1, total, trozo.page(), trozo.size());
  }

  /**
   * Un estado inválido es {@code 400}, y un {@code brokerId} inexistente es la página vacía.
   *
   * <p>La asimetría es deliberada. Un identificador que no designa nada es <b>una pregunta legítima
   * con respuesta vacía</b> —criterio de `RF-SP-025` y del filtro por roles de `RF-SP-042`—,
   * mientras que {@code ?status=depositado} es <b>una pregunta mal escrita</b>: devolver la página
   * vacía la haría indistinguible de «no hay ninguna en ese estado», y quien la lea concluirá que
   * su equipo no ha depositado cuando lo que pasa es que escribió mal el filtro.
   */
  private static UserBrokerStatus filtroDeEstado(String estado) {
    if (estado == null || estado.isBlank()) {
      return null;
    }
    return UserBrokerStatus.de(estado)
        .orElseThrow(
            () ->
                new ValidationException(
                    "VAL-001",
                    "El estado debe ser REGISTER o FIRST_DEPOSIT.",
                    List.of(
                        new FieldError(
                            "status", "VAL-001", "El estado debe ser REGISTER o FIRST_DEPOSIT."))));
  }
}
