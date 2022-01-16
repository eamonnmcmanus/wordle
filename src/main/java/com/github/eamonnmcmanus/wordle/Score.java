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

  static Score of(String attempt, String actual) {
    assert attempt.length() == 5;
    assert actual.length() == 5;
    assert Colour.GREY.ordinal() == 0;
    // We encode each word in an int, 5 bits per letter, 'a' is 1. Then we knock one letter out of
    // the encoded forms whenever we have a match, by overwriting the bits with 0.
    // It would be even faster if we used these encoded forms everywhere instead of strings, but
    // probably not enough faster to justify the resulting code illegibility.
    int slots = 0;
    int attemptCode = encode(attempt);
    int actualCode = encode(actual);
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

  private static int encode(String s) {
    int code = 0;
    for (int i = 0, shift = 0; i < 5; i++, shift += 5) {
      int c = s.charAt(i) - 'a' + 1;
      code |= c << shift;
    }
    return code;
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
