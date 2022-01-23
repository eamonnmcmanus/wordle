package com.github.eamonnmcmanus.wordle;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * @author Éamonn McManus
 */
class ScoreCache implements ScoreFactory {
  private final ImmutableMap<Long, Score> scoreCache;

  ScoreCache(Dictionary dict) {
    this.scoreCache = makeScoreCache(dict);
  }

  private static long mapKey(int attempt, int actual) {
    return ((long) attempt << 32) | actual;
  }

  private static ImmutableMap<Long, Score> makeScoreCache(Dictionary dict) {
    ImmutableSet<Integer> guessWords = dict.guessWords();
    ImmutableSet<Integer> solutionWords = dict.solutionWords();
    ImmutableMap.Builder<Long, Score> builder = ImmutableMap.builder();
    for (int attempt : guessWords) {
      for (int actual : solutionWords) {
        builder.put(mapKey(attempt, actual), Score.of(attempt, actual));
      }
    }
    return builder.buildOrThrow();
  }

  @Override
  public Score score(int attempt, int actual) {
    return scoreCache.get(mapKey(attempt, actual));
  }
}
