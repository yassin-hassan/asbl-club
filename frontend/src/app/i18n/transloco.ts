import { HttpClient } from '@angular/common/http';
import { EnvironmentProviders, Injectable, inject, isDevMode } from '@angular/core';
import { Translation, TranslocoLoader, provideTransloco } from '@jsverse/transloco';
import { LANGUAGES } from './language';

// Translations are plain JSON files served with the app (public/i18n/{fr,nl,en}.json), fetched on demand:
// one build for all languages, switching language needs no page reload.
@Injectable({ providedIn: 'root' })
class JsonTranslationLoader implements TranslocoLoader {
  private http = inject(HttpClient);

  getTranslation(language: string) {
    return this.http.get<Translation>(`/i18n/${language}.json`);
  }
}

export function provideTranslations(): EnvironmentProviders[] {
  return provideTransloco({
    config: {
      availableLangs: [...LANGUAGES],
      defaultLang: 'fr',
      fallbackLang: 'fr',
      missingHandler: { useFallbackTranslation: true },
      reRenderOnLangChange: true,
      prodMode: !isDevMode(),
    },
    loader: JsonTranslationLoader,
  });
}
