package com.github.eamonnmcmanus.wordle;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;

/**
 * @author Éamonn McManus
 */
class Dictionary {
  private static final String GUESS_WORDS = "/wordledict";
  private static final String SOLUTION_WORDS = "/wordlewords";

  private final ImmutableSet<Integer> guessWords;
  private final ImmutableSet<Integer> solutionWords;

  private Dictionary(ImmutableSet<String> guessWords, ImmutableSet<String> solutionWords) {
    if (!guessWords.containsAll(solutionWords)) {
      throw new IllegalArgumentException(
          "Missing words: " + Sets.difference(solutionWords, guessWords));
    }
    this.guessWords = guessWords.stream().map(Dictionary::encode).collect(toImmutableSet());
    this.solutionWords = solutionWords.stream().map(Dictionary::encode).collect(toImmutableSet());
  }

  ImmutableSet<Integer> guessWords() {
    return guessWords;
  }

  ImmutableSet<Integer> solutionWords() {
    return solutionWords;
  }

  static Dictionary create() {
    return create(
        Dictionary.class.getResource(GUESS_WORDS),
        Dictionary.class.getResource(SOLUTION_WORDS));
  }

  static Dictionary create(URL guessWordsUrl, URL solutionWordsUrl) {
    ImmutableSet<String> guessWords = readWords(guessWordsUrl);
    ImmutableSet<String> solutionWords = readWords(solutionWordsUrl);
    return new Dictionary(guessWords, solutionWords);
  }

  static ImmutableSet<String> readWords(URL url) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
      return reader.lines()
          .filter(s -> s.length() == 5)
          .filter(s -> allLowerCase(s))
          .collect(toImmutableSet());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static boolean allLowerCase(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if ('a' > c || 'z' < c) {
        return false;
      }
    }
    return true;
  }

  static int encode(String s) {
    int code = 0;
    for (int i = 0, shift = 0; i < 5; i++, shift += 5) {
      int c = s.charAt(i) - 'a' + 1;
      code |= c << shift;
    }
    return code;
  }

  static String decode(int code) {
    char[] chars = new char[5];
    for (int i = 0, shift = 0; i < 5; i++, shift += 5) {
      int c = (code >> shift) & 31;
      chars[i] = (char) (c + 'a' - 1);
    }
    return new String(chars);
  }
}
