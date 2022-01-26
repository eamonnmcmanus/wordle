package com.github.eamonnmcmanus.wordle;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.lang.Integer.max;

import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author Éamonn McManus
 */
public class Wordle {
  private static final ScoreFactory DEFAULT_SCORE_FACTORY = Score::of;

  enum Mode {
    /** The normal Wordle mode, where any guess in the dictionary is allowed. */
    NORMAL,
    /**
     * The hard Wordle mode, where any letter that has been revealed must be used. If you have had
     * a green letter at a certain position then your guess must contain that letter at that
     * position. If you have had an ochre letter then that your guess must contain that letter, and
     * if you had the same letter twice with both occurrences ochre, then your guess must contain
     * the letter twice.
     */
    HARD,
    /**
     * A mode where all guesses must be consistent with previous scores. This is different from
     * {@link #HARD} because previous scores tell you other things: grey letters are not in the word
     * and ochre letters are not at the position where they were ochre. Any valid consistent-mode
     * guess is also a valid hard-mode guess, but the converse is not true.
     */
    CONSISTENT}

  private static final Mode DEFAULT_MODE = Mode.HARD;

  interface Guesser {
    ImmutableList<Integer> guesses(Wordle wordle);
  }

  private final ScoreFactory scoreFactory;
  private final ImmutableSet<Integer> consistentWords;
  private final ImmutableSet<Integer> allowedGuesses;

  Wordle(Dictionary dict, ScoreFactory scoreFactory, ScoreList scores, Mode mode) {
    this.scoreFactory = scoreFactory;
    this.consistentWords =
        dict.solutionWords().stream().filter(scores::consistentWith).collect(toImmutableSet());
    switch (mode) {
      case NORMAL:
        this.allowedGuesses = dict.guessWords();
        break;
      case HARD:
        this.allowedGuesses =
            dict.guessWords().stream().filter(scores::allowedInHardMode).collect(toImmutableSet());
        break;
      case CONSISTENT:
        this.allowedGuesses =
            dict.guessWords().stream().filter(scores::consistentWith).collect(toImmutableSet());
        break;
      default:
        throw new AssertionError(mode);
    }
  }

  static ImmutableList<Integer> knuthGuesses(Wordle wordle) {
    ArrayList<Integer> bestGuesses = new ArrayList<>();
    int bestMax = Integer.MAX_VALUE;
    boolean bestIsConsistent = false;
    for (Integer guess : wordle.allowedGuesses) {
      Multiset<Score> scoreCounts = HashMultiset.create();
      boolean guessIsConsistent = false;
      for (int actual : wordle.consistentWords) {
        scoreCounts.add(wordle.scoreFactory.score(guess, actual));
        if (actual == guess) {
          guessIsConsistent = true;
        }
      }
      int max = scoreCounts.entrySet().stream()
          .map(Multiset.Entry::getCount)
          .max(Integer::compare)
          .orElse(Integer.MAX_VALUE);
      if (max < bestMax || (max == bestMax && (guessIsConsistent || !bestIsConsistent))) {
        if (false) {
          System.out.printf("better: old best %s max %d, new best %s max %d\n",
              bestGuesses.stream().map(Dictionary::decode).toList(), bestMax, Dictionary.decode(guess), max);
        }
        bestGuesses.clear();
        bestGuesses.add(guess);
        bestMax = max;
        bestIsConsistent = guessIsConsistent;
      }
    }
    if (bestGuesses.isEmpty()) {
      throw new IllegalStateException("could not find a compatible word");
    }
    return ImmutableList.copyOf(bestGuesses);
  }

  static ImmutableList<Integer> irvingGuesses(Wordle wordle) {
    ArrayList<Integer> bestGuesses = new ArrayList<>();
    int bestSquareSum = Integer.MAX_VALUE;
    boolean bestIsConsistent = false;
    for (Integer guess : wordle.allowedGuesses) {
      Multiset<Score> scoreCounts = HashMultiset.create();
      boolean guessIsConsistent = false;
      for (int actual : wordle.consistentWords) {
        scoreCounts.add(wordle.scoreFactory.score(guess, actual));
        if (actual == guess) {
          guessIsConsistent = true;
        }
      }
      int squareSum = scoreCounts.entrySet().stream()
          .mapToInt(Multiset.Entry::getCount)
          .map(x -> x * x)
          .sum();
      if (squareSum < bestSquareSum || (squareSum == bestSquareSum && guessIsConsistent && !bestIsConsistent)) {
        if (false) {
          System.out.printf("better: old best %s sqsum %d, new best %s sqsum %d\n",
              bestGuesses.stream().map(Dictionary::decode).toList(), bestSquareSum, Dictionary.decode(guess), squareSum);
        }
        bestGuesses.clear();
        bestGuesses.add(guess);
        bestSquareSum = squareSum;
        bestIsConsistent = guessIsConsistent;
      } else if (squareSum == bestSquareSum && bestGuesses.size() < 10 && guessIsConsistent == bestIsConsistent) {
        bestGuesses.add(guess);
        if (false) {
          System.out.printf("same: guesses now %s\n", bestGuesses.stream().map(Dictionary::decode).toList());
        }
      }
    }
    if (bestGuesses.isEmpty()) {
      throw new IllegalStateException("could not find a compatible word");
    }
    return ImmutableList.copyOf(bestGuesses);
  }

  static ImmutableList<Integer> neuwirthGuesses(Wordle wordle) {
    ArrayList<Integer> bestGuesses = new ArrayList<>();
    double bestEntropy = Double.NEGATIVE_INFINITY;
    boolean bestIsConsistent = false;
    for (Integer guess : wordle.allowedGuesses) {
      Multiset<Score> scoreCounts = HashMultiset.create();
      boolean guessIsConsistent = false;
      for (int actual : wordle.consistentWords) {
        scoreCounts.add(wordle.scoreFactory.score(guess, actual));
        if (actual == guess) {
          guessIsConsistent = true;
        }
      }
      // We want to maximize (Σ -p_i lg p_i) over all distinct scores, where p_i is the proportion
      // of consistent words that get score i, in other words k_i/N where k_i is the number of
      // consistent words that get score i and N is the number of consistent words. But we don't
      // actually need to divide by N or to use base-2 logarithms, since N is constant over the
      // values we are comparing, and of course lg x is a constant multiple of ln x. We're only
      // interested in knowing which guess gets the maximum value and constant terms won't change
      // that.
      double entropy = scoreCounts.entrySet().stream()
          .mapToDouble(Multiset.Entry::getCount)
          .map(p -> -p * Math.log(p))
          .sum();
      if (entropy > bestEntropy || (entropy == bestEntropy && guessIsConsistent && !bestIsConsistent)) {
        if (false) {
          System.out.printf("better: old best %s entropy %f, new best %s entropy %f\n",
              bestGuesses.stream().map(Dictionary::decode).toList(), bestEntropy, Dictionary.decode(guess), entropy);
        }
        bestGuesses.clear();
        bestGuesses.add(guess);
        bestEntropy = entropy;
        bestIsConsistent = guessIsConsistent;
      } else if (entropy == bestEntropy && bestGuesses.size() < 10 && guessIsConsistent == bestIsConsistent) {
        bestGuesses.add(guess);
        if (false) {
          System.out.printf("same: guesses now %s\n", bestGuesses.stream().map(Dictionary::decode).toList());
        }
      }
    }
    if (bestGuesses.isEmpty()) {
      throw new IllegalStateException("could not find a compatible word");
    }
    return ImmutableList.copyOf(bestGuesses);
  }

  private static ScoreList solve(Dictionary dict, ScoreFactory scoreFactory, Guesser guesser, int actual) {
    int startCode = Dictionary.encode("plaid");
    return solve(dict, scoreFactory, guesser, actual, ScoreList.EMPTY.plus(startCode, scoreFactory.score(startCode, actual)));
  }

  private static ScoreList solve(Dictionary dict, ScoreFactory scoreFactory, Guesser guesser, int actual, ScoreList scores) {
    if (scores.solved()) {
      return scores;
    }
    Wordle wordle = new Wordle(dict, scoreFactory, scores, DEFAULT_MODE);
    ImmutableList<Integer> guesses = guesser.guesses(wordle);
    Integer guess;
    if (false) {
      ImmutableSet<Integer> solutionWords = dict.solutionWords();
      guess =
        guesses.stream().filter(solutionWords::contains).findFirst().orElse(guesses.get(0));
    } else {
      guess = guesses.get(0);
    }
    if (scores.containsWord(guess)) {
      throw new IllegalStateException("With scores " + scores + ", guessed " + guess);
    }
    Score score = scoreFactory.score(guess, actual);
    return solve(dict, scoreFactory, guesser, actual, scores.plus(guess, score));
  }

  private static void solveAll(Guesser guesser) {
    int starting = Dictionary.encode("plaid");
    Dictionary dict = Dictionary.create();
    int solutionCount = dict.solutionWords().size();
    long total = 0;
    int max = 0;
    int n = 0;
    long startTime = System.nanoTime();
    List<Integer> pessimal = new ArrayList<>();
    List<Integer> optimal = new ArrayList<>();
    for (int actual : dict.solutionWords()) {
      ScoreList initial = ScoreList.EMPTY.plus(starting, Score.of(starting, actual));
      ScoreList solved = solve(dict, DEFAULT_SCORE_FACTORY, guesser, actual, initial);
      System.out.println(solved);
      int size = solved.size();
      if (size >= 6) {
        pessimal.add(actual);
      }
      if (size <= 2) {
        optimal.add(actual);
      }
      total += size;
      max = max(max, size);
      n++;
      long elapsed = System.nanoTime() - startTime;
      double rate = elapsed / 1e9 / n;
      double eta = rate * (solutionCount - n);
      System.out.printf("\nword %s (%d/%d) length %d average %.3f total %d max %d elapsed %.1fs %.2fs per word ETA %ds\n\n",
          Dictionary.decode(actual),
          n,
          solutionCount,
          solved.size(),
          (double) total / n,
          total,
          max,
          elapsed / 1e9,
          rate,
          (long) eta);
    }
    System.out.printf("worst cases (%d): %s\n", pessimal.size(), pessimal.stream().map(Dictionary::decode).toList());
    System.out.printf("best cases (%d): %s\n", optimal.size(), optimal.stream().map(Dictionary::decode).toList());
  }

  record TotalAndMax(long total, int max) {}

  private static TotalAndMax solveAllStarting(Dictionary dict, ScoreFactory scoreFactory, Guesser guesser, int starting) {
    long total = 0;
    int max = 0;
    for (int actual : dict.solutionWords()) {
      ScoreList initial = ScoreList.EMPTY.plus(starting, scoreFactory.score(starting, actual));
      ScoreList solved = solve(dict, scoreFactory, guesser, actual, initial);
      int size = solved.size();
      max = Math.max(max, size);
      total += size;
    }
    return new TotalAndMax(total, max);
  }

  private record Result(int starting, TotalAndMax totalAndMax) {}

  private static final Result SENTINEL_RESULT = new Result(0, new TotalAndMax(0, 0));

  // Try every possible solution against every possible starting word. This takes days.
  private static void parallelSolve(Dictionary dict, Guesser guesser)
      throws IOException, InterruptedException, ExecutionException {
    ScoreFactory scoreFactory = DEFAULT_SCORE_FACTORY; // new ScoreCache(dict);
    Path output = Paths.get(StandardSystemProperty.USER_HOME.value() + "/wordlestart.txt");
    ImmutableSet<Integer> existing;
    if (Files.exists(output)) {
      existing =
          Files.lines(output)
              .map(s -> {
                int space = s.indexOf(' ');
                return Dictionary.encode(s.substring(0, space));
              })
              .collect(toImmutableSet());
      if (!existing.isEmpty()) {
        System.out.printf(
            "existing %s..%s\n",
            Dictionary.decode(Iterables.getFirst(existing, 0)),
            Dictionary.decode(Iterables.getLast(existing)));
      }
    } else {
      existing = ImmutableSet.of();
    }
    int nThreads = 10;
    ImmutableSet<Integer> startWords = dict.solutionWords();
    startWords = ImmutableSet.copyOf(Sets.difference(startWords, existing));
    if (false) {
      List<Integer> reversed = new ArrayList<>(startWords);
      Collections.reverse(reversed);
      startWords = ImmutableSet.copyOf(reversed);
    }
    int count = startWords.size();
    BlockingQueue<Integer> wordsToSolve = new ArrayBlockingQueue<>(count);
    wordsToSolve.addAll(startWords);
    BlockingQueue<Result> results = new ArrayBlockingQueue<>(count + nThreads);
    ExecutorService executor = Executors.newFixedThreadPool(nThreads);
    Runnable task = () -> {
      Integer word;
      while ((word = wordsToSolve.poll()) != null) {
        TotalAndMax totalAndMax = solveAllStarting(dict, scoreFactory, guesser, word);
        results.add(new Result(word, totalAndMax));
      }
      results.add(SENTINEL_RESULT);
    };
    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < nThreads; i++) {
      futures.add(executor.submit(task));
    }
    long startTime = System.nanoTime();
    try (PrintWriter writer =
        new PrintWriter(Files.newBufferedWriter(output, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
      int remainingThreads = nThreads;
      int done = 0;
      while (remainingThreads > 0) {
        Result result = results.take();
        if (result.equals(SENTINEL_RESULT)) {
          --remainingThreads;
        } else {
          done++;
          long elapsed = System.nanoTime() - startTime;
          writer.printf(
              "%s %d %d %ds %.1fs per\n",
              Dictionary.decode(result.starting),
              result.totalAndMax.total,
              result.totalAndMax.max,
              elapsed / 1_000_000_000, elapsed / 1e9 / done);
          writer.flush();
        }
      }
    }
    for (Future<?> future : futures) {
      future.get();
    }
    executor.shutdown();
  }

  static void compare() {
    Guesser knuth = Wordle::knuthGuesses;
    Guesser irving = Wordle::irvingGuesses;
    Dictionary dict = Dictionary.create();
    int knuthBetter = 0;
    int knuthMuchBetter = 0;
    int irvingBetter = 0;
    int irvingMuchBetter = 0;
    for (int actual : dict.solutionWords()) {
      ScoreList knuthList = solve(dict, DEFAULT_SCORE_FACTORY, knuth, actual);
      ScoreList irvingList = solve(dict, DEFAULT_SCORE_FACTORY, irving, actual);
      int cmp = irvingList.size() - knuthList.size();
      if (cmp != 0) {
        System.out.printf("For %s:\n  knuth  %s\n  irving %s\n\n", Dictionary.decode(actual), knuthList, irvingList);
        if (cmp < 0) {
          irvingBetter++;
          if (cmp < -1) {
            irvingMuchBetter++;
          }
        } else {
          knuthBetter++;
          if (cmp > 1) {
            knuthMuchBetter++;
          }
        }
      }
    }
    System.out.printf("Of %d words, irving better %d much better %d, knuth better %d much better %d\n",
        dict.solutionWords().size(), irvingBetter, irvingMuchBetter, knuthBetter, knuthMuchBetter);
  }

  record Match(int matches, int exactMatches) {
    static Match of(Score score) {
      return new Match(score.matches(), score.exactMatches());
    }

    Match plus(Match that) {
      return new Match(this.matches + that.matches, this.exactMatches + that.exactMatches);
    }
  }

  // What's the initial guess that gets the most hits on average? Meaning, the most letters that are
  // at least ochre and ideally green.
  static void bestHumanGuess() {
    Comparator<Match> comparator = Comparator.<Match>comparingInt(m -> m.matches).thenComparingInt(m -> m.exactMatches);
    Dictionary dict = Dictionary.create();
    Match best = new Match(0, 0);
    List<String> bestWords = new ArrayList<>();
    for (int guess : dict.guessWords()) {
      Match total = new Match(0, 0);
      for (int actual : dict.solutionWords()) {
        total = total.plus(Match.of(Score.of(guess, actual)));
      }
      int cmp = comparator.compare(total, best);
      if (cmp >= 0) {
        if (cmp > 0) {
          bestWords.clear();
          best = total;
        }
        bestWords.add(Dictionary.decode(guess));
      }
    }
    System.out.printf("best words %s with score %s\n", bestWords, best);
  }

  static void hardSolve(String actual) {
    Dictionary dict = Dictionary.create();
    String initial = "leant";
    ScoreList initialScore = ScoreList.EMPTY.plus(initial, Score.of(initial, actual));
    ImmutableList<Integer> consistentSolutions =
        dict.solutionWords().stream().filter(initialScore::consistentWith).collect(toImmutableList());
    ImmutableList<Integer> consistentGuesses =
        dict.guessWords().stream().filter(initialScore::consistentWith).collect(toImmutableList());
  }

  public static void main(String[] args) throws Exception {
    Guesser guesser = Wordle::irvingGuesses;
    if (false) {
      parallelSolve(Dictionary.create(), guesser);
      return;
    }
    if (true) {
      solveAll(guesser);
      return;
    }
    if (true) {
      System.out.println(solve(Dictionary.create(), DEFAULT_SCORE_FACTORY, guesser, Dictionary.encode("knoll")));
      return;
    }
    if (false) {
      compare();
      return;
    }
    if (false) {
      bestHumanGuess();
      return;
    }
    if (args.length % 2 == 1) {
      System.err.println("Arguments must alternate word and score");
      System.exit(1);
    }
    ScoreList scores = ScoreList.EMPTY;
    Dictionary dict = Dictionary.create();
    for (int i = 0; i < args.length; i += 2) {
      String guess = args[i];
      checkArgument(guess.length() == 5);
      int guessCode = Dictionary.encode(guess);
      if (!dict.guessWords().contains(guessCode)) {
        System.err.printf("Guess %s is not in the dictionary\n", guess);
        System.exit(1);
      }
      Score score = Score.parse(args[i + 1]);
      scores = scores.plus(guessCode, score);
    }
    System.out.println("starting scores: " + scores);
    ImmutableSet<Integer> possible = scores.possible(dict);
    System.out.printf("%d possible solution%s %s\n", possible.size(), possible.size() == 1 ? "" : "s",
        possible.size() < 20 ? possible.stream().map(Dictionary::decode).toList() : "");
    Wordle wordle = new Wordle(dict, DEFAULT_SCORE_FACTORY, scores, DEFAULT_MODE);
    System.out.printf(
        "guesses: %s\n",
        guesser.guesses(wordle).stream()
            .map(i -> Dictionary.decode(i) + (dict.solutionWords().contains(i) ? "*" : ""))
            .collect(toImmutableList()));
  }
}
