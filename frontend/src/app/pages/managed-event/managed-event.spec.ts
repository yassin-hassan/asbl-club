import { seatsOf } from './managed-event';

describe('seatsOf', () => {
  it('splits the taken seats into paid and being paid, and counts the free ones', () => {
    expect(seatsOf({ totalSeats: 120, soldSeats: 34, pendingSeats: 3 })).toEqual({ paid: 31, pending: 3, free: 86 });
  });

  it('shows a sold-out category with nothing pending', () => {
    expect(seatsOf({ totalSeats: 50, soldSeats: 50, pendingSeats: 0 })).toEqual({ paid: 50, pending: 0, free: 0 });
  });
});
