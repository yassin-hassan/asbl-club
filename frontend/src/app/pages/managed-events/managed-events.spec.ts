import { ManagedEventItem } from '../../api/generated';
import { sectionsOf } from './managed-events';

const event = (id: number, status: string, startsAt: string) =>
  ({ id, title: `#${id}`, status, startsAt, visibility: 'PUBLIC' }) as ManagedEventItem;

describe('sectionsOf', () => {
  const now = new Date('2026-10-10T12:00:00Z');

  it('puts upcoming events first (soonest first), then drafts, past (latest first) and cancelled', () => {
    const sections = sectionsOf([
      event(1, 'PUBLISHED', '2026-12-01T19:00:00Z'),
      event(2, 'PUBLISHED', '2026-11-01T19:00:00Z'),
      event(3, 'DRAFT', '2027-01-01T19:00:00Z'),
      event(4, 'PUBLISHED', '2026-06-01T19:00:00Z'),
      event(5, 'PUBLISHED', '2026-09-01T19:00:00Z'),
      event(6, 'CANCELLED', '2026-10-20T19:00:00Z'),
    ], now);
    expect(sections.map((s) => [s.key, s.events.map((e) => e.id)])).toEqual([
      ['upcoming', [2, 1]], ['drafts', [3]], ['past', [5, 4]], ['cancelled', [6]],
    ]);
  });

  it('leaves out empty sections: a plain member, who only gets upcoming events, sees a single list', () => {
    const sections = sectionsOf([event(1, 'PUBLISHED', '2026-12-01T19:00:00Z')], now);
    expect(sections.map((s) => s.key)).toEqual(['upcoming']);
  });
});
