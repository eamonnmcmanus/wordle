package com.github.eamonnmcmanus.wordle;

/**
 * @author emcmanus
 */
@FunctionalInterface
interface ScoreFactory {
  Score score(int attempt, int actual);
}
