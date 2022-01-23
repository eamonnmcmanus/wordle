package com.github.eamonnmcmanus.wordle;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;

/**
 * @author Éamonn McManus
 */
abstract class ScoreList {
  static final ScoreList EMPTY = new ScoreList() {
    @Override
    boolean consistentWith(int word) {
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
    boolean containsWord(int word) {
      return false;
    }
  };

  ScoreList plus(int guess, Score guessScore) {
    ScoreList next = this;
    return new ScoreList() {
      @Override
      boolean consistentWith(int actual) {
        return Score.of(guess, actual).equals(guessScore) && next.consistentWith(actual);
      }

      @Override
      public String toString() {
        return next.toString() + " " + Dictionary.decode(guess) + ":" + guessScore;
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
      boolean containsWord(int word) {
        return word == guess || next.containsWord(word);
      }
    };
  }

  ScoreList plus(String guess, Score guessScore) {
    return plus(Dictionary.encode(guess), guessScore);
  }

  abstract boolean consistentWith(int word);

  abstract boolean solved();

  abstract int size();

  abstract boolean containsWord(int word);

  ImmutableSet<Integer> possible(Dictionary dict) {
    return dict.solutionWords().stream().filter(this::consistentWith).collect(toImmutableSet());
  }
}
