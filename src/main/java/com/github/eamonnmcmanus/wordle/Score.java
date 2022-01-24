package com.github.eamonnmcmanus.wordle;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;

/**
 * @author Éamonn McManus
 */
// raise:-+--- bunty:--+/+ abuzz:/---- aalii:-+---
class Score {
  private enum Colour {
    GREY("-"), // letter does not occur in word, or is already accounted for in an earlier occurrence
    OCHRE("/"), // letter occurs in the word but not at the given position
    GREEN("+"); // letter occurs in the word at the same position

    private final String string;

    private Colour(String string) {
      this.string = string;
    }

    @Override
    public String toString() {
      return string;
    }

    static final Colour[] values = values();
  }

  private static final ImmutableMap<Character, Colour> CHAR_TO_COLOUR =
      ImmutableMap.of('-', Colour.GREY, '/', Colour.OCHRE, '+', Colour.GREEN);

  static final Score SOLVED = parse("+++++");

  // bits 0 and 1 are the score for the first letter, 2 and 3 for the second, etc.
  private final int slots;

  Score(int slots) {
    this.slots = slots;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Score that && this.slots == that.slots;
  }

  @Override
  public int hashCode() {
    return slots;
  }

  @Override
  public String toString() {
    char[] chars = new char[5];
    for (int i = 0; i < 5; i++) {
      int code = (slots >> (i * 2)) & 3;
      chars[i] = Colour.values[code].toString().charAt(0);
    }
    return new String(chars);
  }

  int matches() {
    int count = 0;
    for (int i = 0; i < 5; i++) {
      int code = (slots >> (i * 2)) & 3;
      if (code > 0) {
        count++;
      }
    }
    return count;
  }

  int exactMatches() {
    int count = 0;
    for (int i = 0; i < 5; i++) {
      int code = (slots >> (i * 2)) & 3;
      if (code == Colour.GREEN.ordinal()) {
        count++;
      }
    }
    return count;
  }

  // TODO: apply techniques from Knuth 4A (p152) here.
  int greenMask() {
    int mask = 0;
    for (int i = 0; i < 5; i++) {
      int code = (slots >> (i * 2)) & 3;
      if (code == Colour.GREEN.ordinal()) {
        mask |= 31 << (i * 5);
      }
    }
    return mask;
  }

  int ochreMask() {
    int mask = 0;
    for (int i = 0; i < 5; i++) {
      int code = (slots >> (i * 2)) & 3;
      if (code == Colour.OCHRE.ordinal()) {
        mask |= 31 << (i * 5);
      }
    }
    return mask;
  }

  static Score of(String attempt, String actual) {
    assert attempt.length() == 5;
    assert actual.length() == 5;
    assert Colour.GREY.ordinal() == 0;
    return of(Dictionary.encode(attempt), Dictionary.encode(actual));
  }

  static Score of(int attemptCode, int actualCode) {
    int slots = 0;
    for (int i = 0, shift = 0; i < 5; i++, shift += 5) {
      int attemptC = (attemptCode >> shift) & 31;
      int actualC = (actualCode >> shift) & 31;
      if (attemptC == actualC) {
        slots |= Colour.GREEN.ordinal() << (i * 2);
        int mask = 31 << shift;
        attemptCode &= ~mask;
        actualCode &= ~mask;
      }
    }
    for (int attemptI = 0, attemptShift = 0;
        attemptI < 5 && attemptCode != 0;
        attemptI++, attemptShift += 5) {
      int attemptC = (attemptCode >> attemptShift) & 31;
      for (int actualI = 0, actualShift = 0;
          actualI < 5;
          actualI++, actualShift += 5) {
        int actualC = (actualCode >> actualShift) & 31;
        if (attemptC == actualC && attemptC != 0) {
          slots |= Colour.OCHRE.ordinal() << (attemptI * 2);
          attemptCode &= ~(31 << attemptShift);
          actualCode &= ~(31 << actualShift);
          break;
        }
      }
    }
    return new Score(slots);
  }

  static Score parse(String s) {
    checkArgument(s.length() == 5, "should have length 5, not %s: %s", s.length(), s);
    int slots = 0;
    for (int i = 0; i < 5; i++) {
      Colour colour = CHAR_TO_COLOUR.get(s.charAt(i));
      slots |= colour.ordinal() << (i * 2);
    }
    return new Score(slots);
  }
}
