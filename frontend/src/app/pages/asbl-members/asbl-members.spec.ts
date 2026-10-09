import { matchesSearch, tabsFor } from './asbl-members';

describe('matchesSearch', () => {
  const member = 'Élise Van den Broeck elise.vandenbroeck@demo.asbl.club';

  it('finds a member by any part of the name or the email, whatever the case and accents', () => {
    expect(matchesSearch(member, 'elise')).toBe(true);
    expect(matchesSearch(member, 'BROECK')).toBe(true);
    expect(matchesSearch(member, 'demo.asbl')).toBe(true);
  });

  it('needs every word typed, in any order', () => {
    expect(matchesSearch(member, 'van elise')).toBe(true);
    expect(matchesSearch(member, 'elise dubois')).toBe(false);
  });

  it('matches everyone when the search is empty', () => {
    expect(matchesSearch(member, '')).toBe(true);
    expect(matchesSearch(member, '   ')).toBe(true);
  });
});

describe('tabsFor', () => {
  it('gives administrators every tab, the finances last', () => {
    expect(tabsFor('ADMIN')).toEqual(['members', 'requests', 'invitations', 'dues', 'finances']);
  });

  it('gives treasurers the members, the dues and the finances', () => {
    expect(tabsFor('TREASURER')).toEqual(['members', 'dues', 'finances']);
  });

  it('gives no tabs to the other roles, nor before the page has loaded', () => {
    expect(tabsFor('VIEWER')).toEqual([]);
    expect(tabsFor('MEMBER')).toEqual([]);
    expect(tabsFor(undefined)).toEqual([]);
  });
});
