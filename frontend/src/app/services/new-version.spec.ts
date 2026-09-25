import { NavigationError } from '@angular/router';
import { reloadForNewVersion } from './new-version';

describe('reloadForNewVersion', () => {
  const missingChunk = (url: string) =>
    new NavigationError(1, url, new TypeError('Failed to fetch dynamically imported module: https://site/chunk-X.js'));

  beforeEach(() => sessionStorage.clear());

  it('reloads the page the user was going to when its code is missing (a deploy happened)', () => {
    const reloads: string[] = [];

    expect(reloadForNewVersion(missingChunk('/asbls/club-demo/members'), (url) => reloads.push(url), 100_000)).toBe(true);
    expect(reloads).toEqual(['/asbls/club-demo/members']);
  });

  it('does not reload again straight away, so a really broken file cannot cause a reload loop', () => {
    const reloads: string[] = [];
    reloadForNewVersion(missingChunk('/a'), (url) => reloads.push(url), 100_000);

    expect(reloadForNewVersion(missingChunk('/a'), (url) => reloads.push(url), 105_000)).toBe(false);
    expect(reloadForNewVersion(missingChunk('/a'), (url) => reloads.push(url), 120_000)).toBe(true);
    expect(reloads).toEqual(['/a', '/a']);
  });

  it('leaves other navigation errors alone', () => {
    const reloads: string[] = [];
    const other = new NavigationError(1, '/x', new Error('Cannot match any routes'));

    expect(reloadForNewVersion(other, (url) => reloads.push(url))).toBe(false);
    expect(reloads).toEqual([]);
  });
});
