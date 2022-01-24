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
  private static final int TRACE_CODE = Dictionary.encode("trace");
  private static final int SPRIG_CODE = Dictionary.encode("sprig");
  private static final int SHIRE_CODE = Dictionary.encode("shire");
  private static final int SHIRT_CODE = Dictionary.encode("shirt");

  private static final Dictionary DICT = Dictionary.create();

  @Test
  public void emptyConsistent() {
    assertThat(ScoreList.EMPTY.consistentWith(TANGY_CODE)).isTrue();
  }

  @Test
  public void consistent() {
    ScoreList scores = ScoreList.EMPTY
        .plus(ATONE_CODE, Score.of(ATONE_CODE, TANGY_CODE))
        .plus(LURID_CODE, Score.of(LURID_CODE, TANGY_CODE));
    assertThat(scores.consistentWith(TANGY_CODE)).isTrue();
    assertThat(scores.consistentWith(ANGST_CODE)).isFalse();
  }

  @Test
  public void allowedInHardMode() {
    ScoreList scores = ScoreList.EMPTY
        .plus(SPRIG_CODE, Score.of(SPRIG_CODE, SHIRT_CODE))
        .plus(SHIRE_CODE, Score.of(SHIRE_CODE, SHIRT_CODE));
    assertThat(scores.allowedInHardMode(SHIRT_CODE)).isTrue();
    assertThat(scores.allowedInHardMode(SPRIG_CODE)).isFalse();
  }

  @Test
  public void possibleBug() {
    ScoreList scores = ScoreList.EMPTY
        .plus(TRACE_CODE, Score.of(TRACE_CODE, SHIRE_CODE))
        .plus(SPRIG_CODE, Score.of(SPRIG_CODE, SHIRE_CODE));
    assertThat(scores.consistentWith(SHIRT_CODE)).isFalse();
    assertThat(scores.possible(DICT).stream().map(Dictionary::decode).toList())
        .containsExactly("shire");
  }
}