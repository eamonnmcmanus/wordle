package com.github.eamonnmcmanus.wordle;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.junit.Test;

public class DictionaryTest {
  @Test
  public void testDictionary() {
    Dictionary dict = Dictionary.create();
    ImmutableSet<Integer> words = dict.guessWords();
    assertThat(words.size()).isAtLeast(1000);
    String first = Dictionary.decode(Iterables.getFirst(words, 0));
    assertThat(first).hasLength(5);
    assertThat(first).startsWith("a");
    assertThat(first.chars().allMatch(c -> 'a' <= c && c <= 'z')).isTrue();
    String last = Dictionary.decode(Iterables.getLast(words));
    assertThat(last).hasLength(5);
    assertThat(last).startsWith("z");
    assertThat(last.chars().allMatch(c -> 'a' <= c && c <= 'z')).isTrue();
  }

  private static final int ABIDE_CODE = 1 | (2 << 5) | (9 << 10) | (4 << 15) | (5 << 20);

  @Test
  public void encode() {
    assertThat(Dictionary.encode("abide")).isEqualTo(ABIDE_CODE);
  }

  @Test
  public void decode() {
    assertThat(Dictionary.decode(ABIDE_CODE)).isEqualTo("abide");
  }
}
