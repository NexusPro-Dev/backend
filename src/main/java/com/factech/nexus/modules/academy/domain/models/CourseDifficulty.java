package com.factech.nexus.modules.academy.domain.models;

/**
 * La dificultad de un curso (`RN-AC-007`): <b>una etiqueta para que el alumno elija</b>, no un
 * orden ni una cadena. No hay «superior a», y un curso no exige haber visto los de la dificultad
 * anterior — la recomendación de un previo es otra cosa y tampoco es un candado (`RN-AC-011`).
 */
public enum CourseDifficulty {
  PRINCIPIANTE,
  INTERMEDIO,
  AVANZADO
}
