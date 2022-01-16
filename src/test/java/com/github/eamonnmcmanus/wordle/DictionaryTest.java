package com.github.eamonnmcmanus.wordle;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.junit.Test;

public class DictionaryTest {
  @Test
  public void testDictionary() {
    Dictionary dict = Dictionary.create();
    ImmutableSet<String> words = dict.guessWords();
    assertThat(words.size()).isAtLeast(1000);
    String first = Iterables.getFirst(words, "");
    assertThat(first).hasLength(5);
    assertThat(first).startsWith("a");
    assertThat(first.chars().allMatch(c -> 'a' <= c && c <= 'z')).isTrue();
    String last = Iterables.getLast(words);
    assertThat(last).hasLength(5);
    assertThat(last).startsWith("z");
    assertThat(last.chars().allMatch(c -> 'a' <= c && c <= 'z')).isTrue();
  }
}
