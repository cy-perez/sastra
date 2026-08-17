import type { EnvironmentProviders, Provider } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { APP_CONFIG, type AppConfig } from './app/core/config/app-config';
import { provideActiveLocaleFromDocument, provideI18n } from './app/core/i18n/i18n.providers';
import { BundledTranslationLoader } from './app/core/i18n/translation-loaders';

/**
 * Entorno comun de las pruebas de componente. Se usan las traducciones reales,
 * no unas de mentira: una prueba que pasa con "clave.de.texto" en pantalla no
 * demuestra que la clave exista.
 *
 * Ninguna prueba sale a la red: el cliente HTTP es siempre el de pruebas.
 */
const testConfig: AppConfig = {
  apiBaseUrl: 'https://api.pruebas.sastra.co/api/v1',
  defaultLocale: 'es',
  availableLocales: ['es', 'en'],
  enableDevtools: false,
  sentryDsn: null,
};

const providers: (Provider | EnvironmentProviders)[] = [
  { provide: APP_CONFIG, useValue: testConfig },
  provideHttpClient(),
  provideHttpClientTesting(),
  provideActiveLocaleFromDocument(),
  provideI18n(BundledTranslationLoader),
];

export default providers;
