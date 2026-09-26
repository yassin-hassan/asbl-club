import { MyBooking } from '../../api/generated';
import { splitByDate } from './my-bookings';

const booking = (id: number, startsAt: string) => ({ id, startsAt }) as MyBooking;

describe('splitByDate', () => {
  const now = new Date(2026, 11, 1, 21, 30); // 1 December 2026, 21:30 local time

  it('keeps an event upcoming the whole day it happens, so the ticket is still there at the door', () => {
    const earlierToday = booking(1, new Date(2026, 11, 1, 19, 0).toISOString());
    expect(splitByDate([earlierToday], now).upcoming).toEqual([earlierToday]);
  });

  it('puts earlier days in the past, most recent first; upcoming ones soonest first', () => {
    const lastYear = booking(1, new Date(2025, 5, 1).toISOString());
    const lastMonth = booking(2, new Date(2026, 10, 1).toISOString());
    const tomorrow = booking(3, new Date(2026, 11, 2).toISOString());
    const nextYear = booking(4, new Date(2027, 0, 5).toISOString());

    const { upcoming, past } = splitByDate([lastYear, lastMonth, tomorrow, nextYear], now);

    expect(upcoming.map((b) => b.id)).toEqual([3, 4]);
    expect(past.map((b) => b.id)).toEqual([2, 1]);
  });
});
