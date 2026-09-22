package com.factech.nexus.modules.academy.domain.models;

/**
 * El estado de un curso, de un módulo y de una lección (`RN-AC-008`): <b>lo que alguien
 * decidió</b>, y no lo que la fila tiene.
 *
 * <p>Los tres nacen {@code INACTIVO} y se publican con su cambio de estado, que exige contenido
 * (`RN-AC-009`); lo que después se vacía no desactiva nada — deja de ofrecerse, y eso lo dice
 * {@link CourseOfferability} en cada lectura (`RN-AC-015`). Un solo enumerado para las tres
 * entidades porque los dos valores significan lo mismo en las tres.
 */
public enum CourseStatus {

  /** Se publica, si además se ofrece. Solo el cambio de estado pone una fila aquí. */
  ACTIVO,

  /** Existe y no se enseña. El estado en el que nace todo. */
  INACTIVO
}
