package com.github.eamonnmcmanus.wordle;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/**
 * @author emcmanus
 */
public class ScoreListTest {
  private static final int ANGST_CODE = Dictionary.encode("angst");
  private static final int ATONE_CODE = Dictionary.encode("atone");
  private static final int LURID_CODE = Dictionary.encode("lurid");
  private static final int TANGY_CODE = Dictionary.encode("tangy");

  @Test
  public void testEmpty() {
    assertThat(ScoreList.EMPTY.consistentWith(TANGY_CODE)).isTrue();
  }

  @Test
  public void testConsistent() {
    ScoreList scores = ScoreList.EMPTY
        .plus(ATONE_CODE, Score.of(ATONE_CODE, TANGY_CODE))
        .plus(LURID_CODE, Score.of(LURID_CODE, TANGY_CODE));
    assertThat(scores.consistentWith(TANGY_CODE)).isTrue();
    assertThat(scores.consistentWith(ANGST_CODE)).isFalse();
  }
}