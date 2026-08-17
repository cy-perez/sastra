import { type ApplicationConfig, mergeApplicationConfig } from '@angular/core';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';

import { appConfig } from './app.config';
import { provideAppConfigFromTransferState } from './core/config/app-config.providers';
import { provideActiveLocaleFromDocument, provideI18n } from './core/i18n/i18n.providers';
import { HttpTranslationLoader } from './core/i18n/translation-loaders';

const browserConfig: ApplicationConfig = {
  providers: [
    provideClientHydration(withEventReplay()),
    provideAppConfigFromTransferState(),
    provideActiveLocaleFromDocument(),
    provideI18n(HttpTranslationLoader),
  ],
};

export const config = mergeApplicationConfig(appConfig, browserConfig);
