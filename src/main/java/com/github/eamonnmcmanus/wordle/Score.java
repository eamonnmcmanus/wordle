package com.github.eamonnmcmanus.wordle;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

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

  private static ImmutableMap<Character, Colour> CHAR_TO_COLOUR =
      ImmutableMap.of('-', Colour.GREY, '/', Colour.OCHRE, '+', Colour.GREEN);

  static final Score SOLVED = parse("+++++");

  static final Score NOTHING = parse("-----");


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
    int attemptBits = charBits(attempt);
    int actualBits = charBits(actual);
    if ((attemptBits & actualBits) == 0) {
      return NOTHING;
    }
    int slots = 0;
    var attemptMap = map(attempt);
    var actualMap = map(actual);
    for (int i = 4; i >= 0; i--) {
      int entry = attemptMap[i];
      char c = (char) (entry >>> 16);
      if ((actualBits & charBit(c)) != 0) {
        int index = entry & 0xffff;
        int actualIndex = indexOf(actualMap, c, index);
        if (actualIndex >= 0) {
          slots |= Colour.GREEN.ordinal() << (i * 2);
          removeAt(attemptMap, i);
          removeAt(actualMap, actualIndex);
        }
      }
    }
    int entry;
    for (int i = 0; (entry = attemptMap[i]) != 0; i++) {
      char c = (char) (entry >>> 16);
      if ((actualBits & charBit(c)) != 0) {
        int actualIndex = indexOf(actualMap, c);
        if (actualIndex >= 0) {
          int index = entry & 0xffff;
          slots |= Colour.OCHRE.ordinal() << (index * 2);
          removeAt(actualMap, actualIndex);
        }
      }
    }
    return new Score(slots);
  }

  private static int charBit(char c) {
    int shift = c - 'a';
    return 1 << shift;
  }

  private static int charBits(String s) {
    int bits = 0;
    for (int i = 0; i < s.length(); i++) {
      bits |= charBit(s.charAt(i));
    }
    return bits;
  }

  private static int[] map(String s) {
    int stop = s.length();
    int[] positions = new int[stop + 1];
    for (int i = 0; i < stop; i++) {
      positions[i] = (s.charAt(i) << 16) | i;
    }
    positions[stop] = 0;
    return positions;
  }

  private static void removeAt(int[] positions, int i) {
    for (int j = i; positions[j] != 0; j++) {
      positions[j] = positions[j + 1];
    }
  }

  private static int indexOf(int[] positions, char c, int index) {
    int code = (c << 16) | index;
    for (int i = 0; positions[i] != 0; i++) {
      if (positions[i] == code) {
        return i;
      }
    }
    return -1;
  }

  private static int indexOf(int[] positions, char c) {
    for (int i = 0; positions[i] != 0; i++) {
      if (positions[i] >>> 16 == c) {
        return i;
      }
    }
    return -1;
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
