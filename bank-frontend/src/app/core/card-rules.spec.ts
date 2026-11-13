import { checkExpiry, detectBrand, formatExpiry, formatPan, luhn, validPan } from './card-rules';

describe('card-rules', () => {
  it('prihvata ispravne test kartice po Lunovoj formuli', () => {
    expect(validPan('4111111111111111')).toBe(true);
    expect(validPan('5555555555554444')).toBe(true);
    expect(validPan('9891000000000011')).toBe(true);
  });

  it('odbija pogresan kontrolni broj i duzinu', () => {
    expect(luhn('4111111111111112')).toBe(false);
    expect(validPan('411111111111')).toBe(false);
    expect(validPan('41111111111111111111')).toBe(false);
  });

  it('prepoznaje brend kartice', () => {
    expect(detectBrand('4111')).toBe('VISA');
    expect(detectBrand('5555')).toBe('MASTERCARD');
    expect(detectBrand('2221')).toBe('MASTERCARD');
    expect(detectBrand('9891')).toBe('DINACARD');
    expect(detectBrand('3782')).toBeNull();
  });

  it('formatira unos', () => {
    expect(formatPan('4111111111111111')).toBe('4111 1111 1111 1111');
    expect(formatExpiry('0828')).toBe('08/28');
    expect(formatExpiry('08')).toBe('08');
  });

  it('proverava datum isteka MM/YY', () => {
    const today = new Date(2026, 10, 15); // novembar 2026.
    expect(checkExpiry('11/26', today).ok).toBe(true);
    expect(checkExpiry('10/26', today)).toEqual({ ok: false, message: 'Kartica je istekla' });
    expect(checkExpiry('13/28', today)).toEqual({ ok: false, message: 'Mesec mora biti 01–12' });
    expect(checkExpiry('1/28', today)).toEqual({ ok: false, message: 'Format MM/YY' });
  });
});
