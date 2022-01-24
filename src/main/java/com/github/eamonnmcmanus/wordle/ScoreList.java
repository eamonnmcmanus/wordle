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
    boolean allowedInHardMode(int word) {
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
      boolean allowedInHardMode(int newGuess) {
        int greenMask = guessScore.greenMask();
        // The green-scored letters must occur in the same places in the new guess.
        if ((guess & greenMask) != (newGuess & greenMask)) {
          return false;
        }
        // Each ochre-scored letter must occur once in the new guess. It won't be in the same place
        // (because then it would have scored green). We remove it from a copy of the new guess, so
        // that if there are two ochre Ls for example they won't be satisfied by a single L in the
        // new guess.
        // TODO: apply techniques from Knuth 4A to find the letter faster.
        int updatedNewGuess = newGuess & ~greenMask;
        int ochreMask = guessScore.ochreMask();
        while (ochreMask != 0) {
          int shift = Integer.numberOfTrailingZeros(ochreMask);
          int letter = (guess >> shift) & 31; // what we got the ochre score for
          ochreMask &= ~(31 << shift);
          boolean found = false;
          for (int i = 0; i < 25; i += 5) {
            if (((updatedNewGuess >> i) & 31) == letter) {
              found = true;
              updatedNewGuess &= ~(31 << i);
              break;
            }
          }
          if (!found) {
            return false;
          }
        }
        return next.allowedInHardMode(newGuess);
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

  abstract boolean allowedInHardMode(int word);

  abstract boolean solved();

  abstract int size();

  abstract boolean containsWord(int word);

  ImmutableSet<Integer> possible(Dictionary dict) {
    return dict.solutionWords().stream().filter(this::consistentWith).collect(toImmutableSet());
  }
}
