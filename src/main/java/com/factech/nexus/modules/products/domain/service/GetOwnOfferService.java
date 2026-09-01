package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.OfferItem;
import com.factech.nexus.modules.products.application.OfferResponse;
import com.factech.nexus.modules.products.application.ProductResponse;
import com.factech.nexus.modules.products.domain.models.ProductType;
import com.factech.nexus.modules.products.domain.repository.ProductQueryRepository;
import com.factech.nexus.modules.products.domain.repository.ProductQueryRepository.ProductRow;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup.CurrentMembershipView;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.security.CurrentActor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que quien consulta puede comprar hoy (`RF-PM-007`).
 *
 * <h2>El actor sale del token, y no hay por dónde indicar otro</h2>
 *
 * <p>Este método <b>no recibe ningún parámetro</b>, y esa ausencia es la implementación de
 * `CA-PM-066`: no existe un identificador de persona que ignorar, de modo que enviarlo por la
 * consulta no cambia nada porque no hay nada que lo lea. Admitir uno convertiría esta lectura en
 * «qué puede comprar fulano», que es una pregunta sobre un tercero y que hoy nadie ha decidido
 * quién puede hacer.
 *
 * <p>Es el mismo criterio que `RF-SP-039` aplicó al perfil propio, y por eso tampoco exige permiso:
 * pedir `products:read` daría a cada cliente el catálogo administrativo entero —lo inactivo, lo
 * retirado y el motivo del retiro— para que pudiera ver tres líneas.
 *
 * <h2>La regla vive aquí o no vive</h2>
 *
 * <p>Filtrar el catálogo en el navegador sería repetir este cálculo en cada pantalla que muestre
 * productos, y <b>la que se quedara atrás no fallaría: ofrecería de más</b>. Ofrecerle a alguien un
 * upgrade hacia el nivel que ya tiene es cobrarle por nada, y no hay ningún sitio donde eso falle
 * después.
 *
 * <h2>Y «vigente» no se calcula aquí</h2>
 *
 * <p>El puerto de `SP` devuelve la membresía <b>ya evaluada</b> (**D-25**): si venció, devuelve
 * vacío. Rehacer esa comparación en `PM` es el defecto que no falla —resultados plausibles durante
 * meses, y solo visibles en el borde—, y de que se respete depende `FA-003` entero.
 */
@Service
public class GetOwnOfferService {

  private final ProductQueryRepository consultas;
  private final CurrentMembershipLookup membresias;
  private final CurrentActor actor;

  public GetOwnOfferService(
      ProductQueryRepository consultas, CurrentMembershipLookup membresias, CurrentActor actor) {
    this.consultas = consultas;
    this.membresias = membresias;
    this.actor = actor;
  }

  /**
   * Dos lecturas y ninguna escritura.
   *
   * <p>{@code readOnly} no es una anotación de adorno: declara ante el motor que esta transacción
   * no escribe, y deja escrito para quien la lea que la oferta <b>no reserva nada</b>. Que un
   * producto aparezca aquí no promete que siga disponible al comprarlo.
   */
  @Transactional(readOnly = true)
  public OfferResponse offer() {
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));

    Optional<CurrentMembershipView> actual = membresias.currentMembershipOf(quien);

    // Nulo NO es «sin filtro»: es «no hay peldaño desde el que subir», y la
    // consulta lo traduce en cero upgrades y todos los bots (`FA-001`,
    // `FA-003`). Quien no tiene nivel no lo obtiene comprando un salto, sino
    // recibiendo un rol de consumidor (`RN-SP-018`).
    Integer nivel = actual.map(CurrentMembershipView::level).orElse(null);

    List<OfferItem> upgrades = new ArrayList<>();
    List<OfferItem> bots = new ArrayList<>();

    // Se separa por tipo SIN reordenar: la sentencia ya devolvió los upgrades
    // por nivel de destino y los bots por fecha de alta (`CA-PM-078`), y volver
    // a ordenar aquí sería una segunda copia de ese criterio.
    for (ProductRow fila : consultas.findOffer(nivel)) {
      OfferItem producto = OfferItem.from(fila);
      if (producto.type() == ProductType.UPGRADE_MEMBRESIA) {
        upgrades.add(producto);
      } else {
        bots.add(producto);
      }
    }

    return OfferResponse.de(
        actual.map(GetOwnOfferService::referencia).orElse(null), upgrades, bots);
  }

  /**
   * El nivel desde el que mira quien consulta.
   *
   * <p>Se proyecta a la <b>misma</b> referencia que usan el destino de un upgrade y las respuestas
   * del catálogo: el frontend compara «desde dónde estoy» con «hacia dónde va este producto», y dos
   * formas del mismo dato le obligarían a escribir dos lectores para compararlos.
   */
  private static ProductResponse.MembershipRef referencia(CurrentMembershipView membresia) {
    return new ProductResponse.MembershipRef(
        membresia.id(), membresia.code(), membresia.name(), membresia.level());
  }
}
