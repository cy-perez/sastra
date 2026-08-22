import type { Type } from '@angular/core';
import type { Route, Routes } from '@angular/router';

import {
  PAGINAS_DE_CONTENIDO,
  RUTAS_CONTENIDO,
  type ContentPageId,
} from './core/routes/content-routes';
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
    // HU-002. Direccion en espanol, como el resto del sitio.
    //
    // **Todavia no hay enlace a esta pantalla desde ninguna parte**, y es deliberado: el
    // backend responde 404 en estas rutas mientras FEATURE_SELLER_VERIFICATION este
    // apagada, y HU-004 y HU-005 prohiben dejar enlaces a algo que no funciona. El punto
    // de entrada entra cuando la bandera se encienda.
    path: 'verificacion-de-vendedor',
    title: 'meta.sellerVerification.title',
    data: { descriptionKey: 'meta.sellerVerification.description' },
    loadComponent: () =>
      import('./features/seller-verification/presentation/verification-page').then(
        (m) => m.VerificationPage,
      ),
  },
  {
    path: 'verificar-correo',
    title: 'meta.verify.title',
    data: { descriptionKey: 'meta.verify.description' },
    loadComponent: () =>
      import('./features/auth/presentation/verify-email-page').then((m) => m.VerifyEmailPage),
  },
  // Las cuatro paginas informativas de HU-005. Al contrario que las legales, no
  // comparten componente: cada una dice algo distinto y tiene su propia forma.
  // Lo que si comparten es de donde sale su direccion.
  ...paginasDeContenido(),
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
 * Una ruta por pagina informativa.
 *
 * <p>Se generan desde {@link RUTAS_CONTENIDO} por el mismo motivo que las
 * legales: las direcciones las comparten la tabla de rutas, la navegacion de la
 * cabecera y el pie, y saliendo todas de un sitio no puede existir un enlace que
 * apunte a una ruta que no esta (criterio 25).
 *
 * <p>El componente si es propio de cada una, asi que el `loadComponent` se
 * resuelve con un `switch` y no con una plantilla de ruta: son cuatro paginas
 * distintas, no cuatro instancias de la misma.
 */
function paginasDeContenido(): Routes {
  return PAGINAS_DE_CONTENIDO.map((id): Route => ({
    // Sin la barra inicial: RUTAS_CONTENIDO la lleva porque sirven para routerLink.
    path: RUTAS_CONTENIDO[id].slice(1),
    title: `meta.${id}.title`,
    data: { descriptionKey: `meta.${id}.description` },
    loadComponent: () => cargarPaginaDeContenido(id),
  }));
}

/**
 * El `switch` es a proposito: un `import()` con una ruta calculada no lo puede
 * analizar el empaquetador, y las cuatro paginas acabarian en el paquete inicial
 * en vez de en su propio fragmento diferido.
 */
function cargarPaginaDeContenido(id: ContentPageId): Promise<Type<unknown>> {
  switch (id) {
    case 'howItWorks':
      return import('./features/content/presentation/how-it-works-page').then(
        (m) => m.HowItWorksPage,
      );
    case 'about':
      return import('./features/content/presentation/about-page').then((m) => m.AboutPage);
    case 'faq':
      return import('./features/content/presentation/faq-page').then((m) => m.FaqPage);
    case 'contact':
      return import('./features/content/presentation/contact-page').then((m) => m.ContactPage);
  }
}

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
