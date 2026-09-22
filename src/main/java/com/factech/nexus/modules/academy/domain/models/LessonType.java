package com.factech.nexus.modules.academy.domain.models;

/**
 * Lo que es una lección (RN-AC-016): un video —su contenido es una URL— o un texto —su contenido es
 * Markdown que el backend guarda y devuelve sin interpretar—. El tipo manda sobre el contenido, y
 * se corrige: el contenido de la misma petición, o el que ya hay, se valida contra el tipo
 * resultante.
 */
public enum LessonType {
  VIDEO,
  TEXTO
}
