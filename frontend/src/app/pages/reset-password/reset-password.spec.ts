import { tokenFromFragment } from './reset-password';

describe('tokenFromFragment', () => {
  it('reads the token from the part of the link after #', () => {
    expect(tokenFromFragment('token=abc-DEF_123')).toBe('abc-DEF_123');
  });

  it('finds nothing when the link has no token', () => {
    expect(tokenFromFragment(null)).toBeNull();
    expect(tokenFromFragment('')).toBeNull();
    expect(tokenFromFragment('other=1')).toBeNull();
  });
});
