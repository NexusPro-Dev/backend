package com.factech.nexus.modules.system.memberships.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Lo que `SP` publica de sus membresías para que otro módulo pueda consultarlas (**D-25**).
 *
 * <p><b>La declara el módulo dueño del dato y la importa el consumidor</b>, que es lo que dice
 * `modules.md` §2 —«otros módulos lo consumen por interfaz publicada»—. La dependencia apunta del
 * consumidor al proveedor y `SP` no se entera de quién la usa. La inversión de dependencia —que el
 * consumidor declarase el puerto y `SP` lo implementara— se descartó porque aquí produce lo
 * contrario de lo que promete: el módulo raíz pasaría a importar una interfaz del que depende de
 * él, que es el ciclo que §7 prohíbe, disfrazado.
 *
 * <p><b>Devuelve un modelo de lectura y nunca la entidad.</b> Devolver {@code Membership} filtraría
 * JPA al otro módulo y le daría, de paso, con qué escribir.
 *
 * <p><b>La ausencia es {@link Optional#empty()}, no una excepción.</b> Que una membresía no exista
 * es una respuesta legítima a una consulta, y qué {@code 4xx} produce lo decide quien tiene el
 * contrato HTTP, que es el consumidor. Lanzar desde aquí le obligaría a capturarla para traducirla,
 * o se le escaparía como {@code 500}.
 *
 * <p>Ver `architecture.md` §15.2.
 */
public interface MembershipCatalog {

  /**
   * La membresía, si existe.
   *
   * @param id identificador de la membresía; un valor nulo devuelve vacío en lugar de fallar
   */
  Optional<MembershipView> find(UUID id);

  /**
   * Lo que cruza la frontera: datos planos, sin comportamiento.
   *
   * <p>{@code level} viaja porque es lo único que permite decidir «hacia arriba» sin conocer la
   * estructura de la cadena. Crece hacia abajo: {@code 1} es la cima (`requirements/sp.md` §10.4),
   * de modo que <b>nivel superior es número menor</b>.
   */
  record MembershipView(UUID id, String code, String name, int level) {}
}
