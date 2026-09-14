package com.factech.nexus.modules.system.users.application;

import java.util.UUID;

/**
 * La venta que acompaña a un registro por enlace, publicada <b>por `MV` para `SP`</b>
 * (`RF-SP-045`).
 *
 * <h2>Invierte la dirección, como {@link RegistrableProductLookup}, y por lo mismo</h2>
 *
 * <p>`SP` es la raíz del grafo (`modules.md` §7) y no consume de nadie: importar algo de `MV`
 * <b>compila y abre un ciclo</b>. Se declara aquí y lo implementa `MV`, que es de quien es la
 * venta.
 *
 * <h2>Y NO reimplementa la venta: la delega</h2>
 *
 * <p>El adaptador llama al caso de uso de `RF-MV-001`, con sus reglas enteras — que el producto se
 * le ofrezca a esa persona, que no la baje de nivel, que la moneda sea una sola, que el método de
 * pago exista y admita ese producto—. Escribir aquí una venta «simplificada» daría <b>dos
 * definiciones de lo que es vender</b>, y la segunda se quedaría atrás sin que nada fallara.
 *
 * <p><b>La venta nace {@code PENDIENTE} y eso no es configurable</b>: el agregado de `MV` no admite
 * construirse en otro estado. `RN-MV-004` dice que registrar <b>no concede nada</b> y `RN-MV-020`
 * que <b>confirmar sí</b> — de ahí sale que quien paga reciba su membresía al confirmarse el pago y
 * no al registrarse.
 */
public interface RegistrationSaleRegistrar {

  /**
   * Registra la venta del producto con el que alguien acaba de darse de alta.
   *
   * <p>Se llama <b>dentro de la transacción del registro</b>: si la venta se rechaza, no queda ni
   * la persona. Vender a alguien que no existe y registrar a alguien cuya venta no se pudo anotar
   * son los dos estados que esa transacción evita.
   *
   * @param movementTypeCode el tipo de movimiento, <b>por código</b>. Hoy el catálogo solo tiene
   *     {@code VENTA} y un registro no puede producir otra cosa; llega en la petición porque el
   *     formulario lo declara, y se verifica en lugar de asumirse
   * @return el código legible de la venta —{@code VTA-000123}—, para que la respuesta del registro
   *     pueda decir qué quedó anotado
   */
  String registerSale(
      UUID clientId,
      UUID productId,
      UUID paymentMethodId,
      String movementTypeCode,
      String sellerUsername);
}
