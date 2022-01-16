package com.github.eamonnmcmanus.wordle;

import static com.google.common.base.Preconditions.checkArgument;
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

  private final ImmutableSet<String> guessWords;
  private final ImmutableSet<String> solutionWords;

  private Dictionary(ImmutableSet<String> guessWords, ImmutableSet<String> solutionWords) {
    if (!guessWords.containsAll(solutionWords)) {
      throw new IllegalArgumentException(
          "Missing words: " + Sets.difference(solutionWords, guessWords));
    }
    this.guessWords = guessWords;
    this.solutionWords = solutionWords;
  }

  ImmutableSet<String> guessWords() {
    return guessWords;
  }

  ImmutableSet<String> solutionWords() {
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
}
