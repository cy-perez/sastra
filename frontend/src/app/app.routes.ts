import type { Routes } from '@angular/router';

/**
 * Toda ruta se carga de forma diferida y declara su titulo y su descripcion
 * como claves de Transloco: TranslatedTitleStrategy las resuelve durante el
 * renderizado en servidor.
 *
 * Las direcciones van en espanol, una sola por ruta. Al cambiar a ingles cambia
 * el texto, no la direccion: el trafico de busqueda vendra de Colombia y el
 * ingles existe por accesibilidad, no por posicionamiento.
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
    path: 'registro',
    title: 'meta.register.title',
    data: { descriptionKey: 'meta.register.description' },
    loadComponent: () =>
      import('./features/auth/presentation/register-page').then((m) => m.RegisterPage),
  },
  {
    path: 'verificar-correo',
    title: 'meta.verify.title',
    data: { descriptionKey: 'meta.verify.description' },
    loadComponent: () =>
      import('./features/auth/presentation/verify-email-page').then((m) => m.VerifyEmailPage),
  },
  {
    path: '**',
    title: 'meta.notFound.title',
    data: { descriptionKey: 'meta.notFound.description' },
    loadComponent: () =>
      import('./features/not-found/presentation/not-found-page').then((m) => m.NotFoundPage),
  },
];
