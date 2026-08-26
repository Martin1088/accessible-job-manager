import { containsName, isSameName, nameKeys } from './company-name-match';

describe('company name matching', () => {

  it('ignores case, surrounding and repeated whitespace', () => {
    expect(isSameName('Acme GmbH', '  acme   gmbh ')).toBeTrue();
  });

  it('ignores punctuation and hyphenation', () => {
    expect(isSameName('ABC-Tech', 'ABC Tech')).toBeTrue();
    expect(isSameName('Müller & Söhne', 'Müller Söhne')).toBeTrue();
  });

  it('matches an umlaut against both of its spellings', () => {
    expect(isSameName('Müller & Söhne', 'Mueller & Soehne')).toBeTrue();
    expect(isSameName('Müller & Söhne', 'Muller Sohne')).toBeTrue();
    expect(isSameName('Mueller GmbH', 'Müller GmbH')).toBeTrue();
  });

  it('matches ß against ss', () => {
    expect(isSameName('Straßburger AG', 'Strassburger AG')).toBeTrue();
  });

  it('matches accented letters against their plain form', () => {
    expect(isSameName("L'Oréal", "L'Oreal")).toBeTrue();
    expect(isSameName('Nestlé', 'nestle')).toBeTrue();
  });

  it('drops trademark symbols instead of folding them into letters', () => {
    expect(nameKeys('3M™')).toEqual(['3m']);
    expect(isSameName('3M™', '3M')).toBeTrue();
  });

  it('keeps two legal forms of the same house apart', () => {
    expect(isSameName('Meyer GmbH', 'Meyer AG')).toBeFalse();
  });

  it('keeps different companies apart', () => {
    expect(isSameName('Acme GmbH', 'Globex')).toBeFalse();
  });

  it('has no keys for a blank name, so a blank field matches nothing', () => {
    expect(nameKeys('   ')).toEqual([]);
    expect(nameKeys(undefined)).toEqual([]);
    expect(isSameName('', 'Acme')).toBeFalse();
  });

  it('produces one key when a name spells the same either way', () => {
    expect(nameKeys('Acme GmbH')).toEqual(['acme gmbh']);
  });

  describe('partial match', () => {
    it('finds a company from the start of its name', () => {
      expect(containsName('Acme GmbH', 'acme')).toBeTrue();
    });

    it('finds a company from a word inside its name', () => {
      expect(containsName('Deutsche Bahn AG', 'bahn')).toBeTrue();
    });

    it('finds an umlaut company from either spelling of the fragment', () => {
      expect(containsName('Müller & Söhne', 'mueller')).toBeTrue();
      expect(containsName('Müller & Söhne', 'muller')).toBeTrue();
    });

    it('does not match an unrelated fragment', () => {
      expect(containsName('Acme GmbH', 'globex')).toBeFalse();
    });
  });
});
