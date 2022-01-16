package com.github.eamonnmcmanus.wordle;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/**
 * @author emcmanus
 */
public class ScoreListTest {
  @Test
  public void testEmpty() {
    assertThat(ScoreList.EMPTY.consistentWith("tangy")).isTrue();
  }

  @Test
  public void testConsistent() {
    ScoreList scores = ScoreList.EMPTY
        .plus("atone", Score.of("atone", "tangy"))
        .plus("lurid", Score.of("lurid", "tangy"));
    assertThat(scores.consistentWith("tangy")).isTrue();
    assertThat(scores.consistentWith("angst")).isFalse();
  }
}