import { makeStateKey, type StateKey } from '@angular/core';
import type { Translation } from '@jsverse/transloco';

/**
 * El servidor deja el idioma que acaba de renderizar en el estado transferido y
 * el navegador lo recoge de ahi. Sin esto, la primera pintura viene traducida
 * pero la hidratacion llega sin texto hasta que responde /i18n/<idioma>.json, y
 * el resultado es un parpadeo de claves crudas.
 */
export function translationStateKey(language: string): StateKey<Translation> {
  return makeStateKey<Translation>(`sendik.i18n.${language}`);
}
