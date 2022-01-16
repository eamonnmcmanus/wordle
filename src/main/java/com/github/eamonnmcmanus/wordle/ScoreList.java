package com.github.eamonnmcmanus.wordle;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;

/**
 * @author Éamonn McManus
 */
abstract class ScoreList {
  static final ScoreList EMPTY = new ScoreList() {
    @Override
    boolean consistentWith(String word) {
      return true;
    }

    @Override
    public String toString() {
      return "";
    }

    @Override
    boolean solved() {
      return false;
    }

    @Override
    int size() {
      return 0;
    }

    @Override
    boolean containsWord(String word) {
      return false;
    }
  };

  ScoreList plus(String guess, Score guessScore) {
    ScoreList next = this;
    return new ScoreList() {
      @Override
      boolean consistentWith(String actual) {
        return Score.of(guess, actual).equals(guessScore) && next.consistentWith(actual);
      }

      @Override
      public String toString() {
        return next.toString() + " " + guess + ":" + guessScore;
      }

      @Override
      boolean solved() {
        return guessScore.equals(Score.SOLVED);
      }

      @Override
      int size() {
        return next.size() + 1;
      }

      @Override
      boolean containsWord(String word) {
        return word.equals(guess) || next.containsWord(word);
      }
    };
  }

  abstract boolean consistentWith(String word);

  abstract boolean solved();

  abstract int size();

  abstract boolean containsWord(String word);

  ImmutableSet<String> possible(Dictionary dict) {
    return dict.guessWords().stream().filter(this::consistentWith).collect(toImmutableSet());
  }
}
