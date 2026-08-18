import type { Route, Routes } from '@angular/router';

import { DOCUMENTOS_LEGALES, RUTAS_LEGALES } from './core/routes/legal-routes';
import { legalContentResolver } from './features/legal/application/legal-content.resolver';

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
    path: 'ingresar',
    title: 'meta.login.title',
    data: { descriptionKey: 'meta.login.description' },
    loadComponent: () => import('./features/auth/presentation/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'recuperar-contrasena',
    title: 'meta.forgot.title',
    data: { descriptionKey: 'meta.forgot.description' },
    loadComponent: () =>
      import('./features/auth/presentation/forgot-password-page').then((m) => m.ForgotPasswordPage),
  },
  {
    // La ruta la conoce tambien el backend, que la monta en el enlace del correo:
    // MAIL_PASSWORD_RESET_PATH. Si cambia aqui, cambia alli.
    path: 'restablecer-contrasena',
    title: 'meta.reset.title',
    data: { descriptionKey: 'meta.reset.description' },
    loadComponent: () =>
      import('./features/auth/presentation/reset-password-page').then((m) => m.ResetPasswordPage),
  },
  {
    // Tambien la conoce el backend, que la monta en el enlace del correo:
    // MAIL_EMAIL_CHANGE_PATH. Si cambia aqui, cambia alli.
    path: 'confirmar-correo-nuevo',
    title: 'meta.confirmEmail.title',
    data: { descriptionKey: 'meta.confirmEmail.description' },
    loadComponent: () =>
      import('./features/auth/presentation/confirm-email-change-page').then(
        (m) => m.ConfirmEmailChangePage,
      ),
  },
  {
    path: 'mi-cuenta',
    title: 'meta.account.title',
    data: { descriptionKey: 'meta.account.description' },
    loadComponent: () =>
      import('./features/auth/presentation/account-page').then((m) => m.AccountPage),
  },
  {
    path: 'verificar-correo',
    title: 'meta.verify.title',
    data: { descriptionKey: 'meta.verify.description' },
    loadComponent: () =>
      import('./features/auth/presentation/verify-email-page').then((m) => m.VerifyEmailPage),
  },
  // Los tres documentos legales comparten componente y resolutor: lo unico que
  // cambia es cual es y como se llama. El resolutor trae el texto antes de
  // renderizar, asi que viaja dentro del HTML que sirve el servidor.
  ...documentosLegales(),
  {
    path: '**',
    title: 'meta.notFound.title',
    data: { descriptionKey: 'meta.notFound.description' },
    loadComponent: () =>
      import('./features/not-found/presentation/not-found-page').then((m) => m.NotFoundPage),
  },
];

/**
 * Una ruta por documento legal, todas iguales salvo el nombre.
 *
 * <p>Se generan en vez de escribirse tres veces para que agregar un documento sea
 * agregarlo al tipo y a las traducciones, y no copiar un bloque que se acabaria
 * separando del resto. Las direcciones salen de RUTAS, que es tambien de donde
 * las toman los enlaces del registro y del pie: asi no puede haber una ruta que
 * exista y un enlace que apunte a otra parte.
 */
function documentosLegales(): Routes {
  return DOCUMENTOS_LEGALES.map((id): Route => ({
    // Sin la barra inicial: RUTAS_LEGALES la lleva porque sirven para routerLink.
    path: RUTAS_LEGALES[id].slice(1),
    title: `meta.legal.${id}.title`,
    data: { descriptionKey: `meta.legal.${id}.description`, documento: id },
    resolve: { contenido: legalContentResolver },
    loadComponent: () =>
      import('./features/legal/presentation/legal-page').then((m) => m.LegalPage),
  }));
}
