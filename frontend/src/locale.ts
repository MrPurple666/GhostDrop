import { Locale, messages } from './messages';

export type LocaleKey = Locale;
export type Copy = (typeof messages)['en-US'];

export function detectLocale(language: string): Locale {
  return language === 'pt-BR' ? 'pt-BR' : 'en-US';
}

export { messages };
