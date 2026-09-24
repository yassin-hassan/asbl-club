import { TranslocoTestingModule } from '@jsverse/transloco';
import en from '../../../public/i18n/en.json';
import { LANGUAGES } from './language';

// For component tests: the real English translations, loaded synchronously, so templates render real text.
export function translationsForTests() {
  return TranslocoTestingModule.forRoot({
    langs: { en },
    translocoConfig: { availableLangs: [...LANGUAGES], defaultLang: 'en' },
    preloadLangs: true,
  });
}
