import { matchesSearch } from './asbl-members';

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
