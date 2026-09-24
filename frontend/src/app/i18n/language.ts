import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { Observable } from 'rxjs';

export const LANGUAGES = ['fr', 'nl', 'en'] as const;
export type Language = (typeof LANGUAGES)[number];
const STORAGE_KEY = 'language';

// The single owner of "which language is the app in": the translation library, <html lang>, the API
// (via the language interceptor) and dates all follow it.
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private transloco = inject(TranslocoService);
  private document = inject(DOCUMENT);

  private readonly current = signal<Language>('fr');
  readonly active = this.current.asReadonly();

  // Runs before the first render, so the page never flashes untranslated keys.
  init(): Observable<unknown> {
    this.apply(this.preferred());
    return this.transloco.load(this.current());
  }

  use(language: Language): void {
    this.apply(language);
    try {
      localStorage.setItem(STORAGE_KEY, language); // a convenience; the app works without it
    } catch {
      // storage blocked (private mode…): the choice just isn't remembered
    }
  }

  private apply(language: Language): void {
    this.current.set(language);
    this.transloco.setActiveLang(language);
    this.document.documentElement.lang = language; // screen readers and browsers use it
  }

  // The user's earlier choice, else the browser's language if we have it, else French (as the server pages).
  private preferred(): Language {
    let stored: string | null = null;
    try {
      stored = localStorage.getItem(STORAGE_KEY);
    } catch {
      // storage blocked
    }
    const browser = this.document.defaultView?.navigator.language.slice(0, 2);
    return [stored, browser].find(isLanguage) ?? 'fr';
  }
}

function isLanguage(value: string | null | undefined): value is Language {
  return LANGUAGES.includes(value as Language);
}
