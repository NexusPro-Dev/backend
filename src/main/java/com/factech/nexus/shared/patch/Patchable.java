package com.factech.nexus.shared.patch;

/**
 * Un campo de {@code PATCH} con sus <b>tres</b> estados.
 *
 * <p>Un `PATCH` necesita distinguir tres cosas que un {@code String} no puede expresar:
 *
 * <ul>
 *   <li><b>Ausente</b> — el campo no venía en el cuerpo: no se toca.
 *   <li><b>Presente y nulo</b> — el cliente envió {@code null} de forma explícita.
 *   <li><b>Presente con valor</b>.
 * </ul>
 *
 * <p>Sin los tres, «no lo envié» y «envíalo a nulo» se confunden, y la operación acaba borrando lo
 * que nadie pidió borrar o ignorando lo que sí se pidió.
 *
 * <p><b>Por qué no basta {@code Optional}.</b> Se intentó, y falla en silencio: al deserializar un
 * <i>record</i>, Jackson entrega {@code Optional.empty()} tanto para el campo <b>ausente</b> como
 * para el nulo explícito, de modo que los dos estados que hacía falta separar se funden en uno. El
 * síntoma fue concreto: enviar solo el nombre rechazaba la petición por «apellido vacío».
 *
 * <p>Qué se hace con el estado <b>presente y nulo</b> lo decide cada requerimiento: donde la
 * columna admite nulo es una orden de borrado, y donde no lo admite —como aquí— es un {@code 400}.
 * El tipo solo transporta la distinción.
 */
public final class Patchable<T> {

  private static final Patchable<?> AUSENTE = new Patchable<>(false, null);

  private final boolean presente;
  private final T valor;

  private Patchable(boolean presente, T valor) {
    this.presente = presente;
    this.valor = valor;
  }

  @SuppressWarnings("unchecked")
  public static <T> Patchable<T> ausente() {
    return (Patchable<T>) AUSENTE;
  }

  /** El campo venía en el cuerpo, con este valor — que puede ser nulo. */
  public static <T> Patchable<T> de(T valor) {
    return new Patchable<>(true, valor);
  }

  /** ¿Venía en el cuerpo? */
  public boolean presente() {
    return presente;
  }

  /** El valor enviado, o nulo si no venía o si se envió nulo. */
  public T valor() {
    return valor;
  }
}
