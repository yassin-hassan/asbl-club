import en from '../../../public/i18n/en.json';
import fr from '../../../public/i18n/fr.json';
import nl from '../../../public/i18n/nl.json';

// Every language must have exactly the same keys: a key missing in one language would show up as a raw
// key (or silently fall back to French) for those users.
function keysOf(value: object, prefix = ''): string[] {
  return Object.entries(value).flatMap(([key, child]) =>
    typeof child === 'object' && child !== null ? keysOf(child, `${prefix}${key}.`) : [`${prefix}${key}`],
  );
}

describe('translation files', () => {
  it('have the same keys in French, Dutch and English', () => {
    const french = keysOf(fr).sort();
    expect(keysOf(nl).sort()).toEqual(french);
    expect(keysOf(en).sort()).toEqual(french);
  });
});
