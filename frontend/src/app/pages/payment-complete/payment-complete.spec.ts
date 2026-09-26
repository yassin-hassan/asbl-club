import { outcomeOf } from './payment-complete';

// What the "payment complete" page shows for each booking status the server reports.
describe('outcomeOf', () => {
  it('keeps waiting while Stripe has not answered yet', () => {
    expect(outcomeOf('RESERVED')).toBe('checking');
    // The event was cancelled while the person was paying: a refund is on its way.
    expect(outcomeOf('CANCELLED')).toBe('checking');
    // The booking ran out of time while the person was paying: same, a refund is on its way.
    expect(outcomeOf('EXPIRED')).toBe('checking');
  });

  it('confirms a paid booking', () => {
    expect(outcomeOf('PAID')).toBe('confirmed');
  });

  it('says so when the payment was refunded, instead of "your seat is booked"', () => {
    expect(outcomeOf('REFUNDED')).toBe('refunded');
  });
});
