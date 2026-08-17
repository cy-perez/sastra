import type { Routes } from '@angular/router';

/**
 * Toda ruta se carga de forma diferida y declara su titulo y su descripcion
 * como claves de Transloco: TranslatedTitleStrategy las resuelve durante el
 * renderizado en servidor.
 */
export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'meta.home.title',
    data: { descriptionKey: 'meta.home.description' },
    loadComponent: () => import('./features/home/presentation/home-page').then((m) => m.HomePage),
  },
  {
    path: '**',
    title: 'meta.notFound.title',
    data: { descriptionKey: 'meta.notFound.description' },
    loadComponent: () =>
      import('./features/not-found/presentation/not-found-page').then((m) => m.NotFoundPage),
  },
];
