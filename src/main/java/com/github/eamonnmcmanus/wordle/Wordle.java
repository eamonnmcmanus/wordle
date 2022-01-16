package com.github.eamonnmcmanus.wordle;

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
  private final Dictionary dict;
  private final ScoreList scores;
  private final ImmutableSet<String> consistentWords;

  Wordle(Dictionary dict, ScoreList scores) {
    this.dict = dict;
    this.scores = scores;
    this.consistentWords =
        dict.solutionWords().stream().filter(scores::consistentWith).collect(toImmutableSet());
  }

  ImmutableList<String> guesses() {
    ArrayList<String> bestGuesses = new ArrayList<>();
    int bestMax = Integer.MAX_VALUE;
    int bestCount = 0;
    boolean bestIsConsistent = false;
    for (String guess : dict.guessWords()) {
      Multiset<Score> scoreCounts = HashMultiset.create();
      consistentWords.forEach(actual -> scoreCounts.add(Score.of(guess, actual)));
      int max = scoreCounts.entrySet().stream()
          .map(Multiset.Entry::getCount)
          .max(Integer::compare)
          .orElse(Integer.MAX_VALUE);
      int count = scoreCounts.size();
      if (max < bestMax || (max == bestMax && count > bestCount)) {
        if (false) {
          System.out.printf("better: old best %s max %d count %d, new best %s max %d count %d\n",
              bestGuesses, bestMax, bestCount, guess, max, count);
        }
        bestGuesses.clear();
        bestGuesses.add(guess);
        bestCount = count;
        bestMax = max;
        bestIsConsistent = scores.consistentWith(guess);
      } else if (max == bestMax) {
        if (!bestIsConsistent && consistentWords.contains(guess)) {
          if (false) {
            System.out.printf("consistent: old best %s max %d count %d, new best %s max %d count %d\n",
                bestGuesses, bestMax, bestCount, guess, max, count);
          }
          bestGuesses.clear();
          bestGuesses.add(guess);
          bestIsConsistent = true;
        } else if (count == bestCount && bestGuesses.size() < 10) {
          bestGuesses.add(guess);
          if (false) {
            System.out.printf("same: guesses now %s\n", bestGuesses);
          }
        }
      }
    }
    if (bestGuesses.isEmpty()) {
      throw new IllegalStateException("could not find a compatible word");
    }
    return ImmutableList.copyOf(bestGuesses);
  }

  private static ScoreList solve(Dictionary dict, String actual) {
    return solve(dict, actual, ScoreList.EMPTY);
  }

  private static ScoreList solve(Dictionary dict, String actual, ScoreList scores) {
    if (scores.solved()) {
      return scores;
    }
    String guess = new Wordle(dict, scores).guesses().get(0);
    if (scores.containsWord(guess)) {
      throw new IllegalStateException("With scores " + scores + ", guessed " + guess);
    }
    Score score = Score.of(guess, actual);
    return solve(dict, actual, scores.plus(guess, score));
  }

  private static void solveAll() {
    String starting = "raise";
    Dictionary dict = Dictionary.create();
    int solutionCount = dict.solutionWords().size();
    long total = 0;
    int max = 0;
    int n = 0;
    long startTime = System.nanoTime();
    for (String actual : dict.solutionWords()) {
      ScoreList initial = ScoreList.EMPTY.plus(starting, Score.of(starting, actual));
      ScoreList solved = solve(dict, actual, initial);
      System.out.println(solved);
      int size = solved.size();
      total += size;
      max = max(max, size);
      n++;
      long elapsed = System.nanoTime() - startTime;
      double rate = (double) elapsed / 1e9 / n;
      double eta = rate * (solutionCount - n);
      System.out.printf("\nword %s (%d/%d) length %d average %.3f max %d elapsed %.1fs %.2fs per word ETA %ds\n\n",
          actual, n, solutionCount, solved.size(), (double) total / n, max, elapsed / 1e9, rate, (long) eta);
    }
  }

  private static long solveAllStarting(Dictionary dict, String starting) {
    long total = 0;
    for (String actual : dict.solutionWords()) {
      ScoreList initial = ScoreList.EMPTY.plus(starting, Score.of(starting, actual));
      ScoreList solved = solve(dict, actual, initial);
      total += solved.size();
    }
    return total;
  }

  private record Result(String starting, long total) {}

  private static final Result SENTINEL_RESULT = new Result("", 0);

  private static void parallelSolve(Dictionary dict)
      throws IOException, InterruptedException, ExecutionException {
    Path output = Paths.get(StandardSystemProperty.USER_HOME.value() + "/wordlestart.txt");
    ImmutableSet<String> existing;
    if (Files.exists(output)) {
      existing =
          Files.lines(output)
              .map(s -> {
                int space = s.indexOf(' ');
                return s.substring(0, space);
              })
              .collect(toImmutableSet());
      if (!existing.isEmpty()) {
        System.out.printf(
            "existing %s..%s\n", Iterables.getFirst(existing, "?"), Iterables.getLast(existing));
      }
    } else {
      existing = ImmutableSet.of();
    }
    int nThreads = 10;
    ImmutableSet<String> startWords = dict.solutionWords(); // could also be dict.guessWords()
    startWords = ImmutableSet.copyOf(Sets.difference(startWords, existing));
    int count = startWords.size();
    BlockingQueue<String> wordsToSolve = new ArrayBlockingQueue<>(count);
    wordsToSolve.addAll(startWords);
    BlockingQueue<Result> results = new ArrayBlockingQueue<>(count + nThreads);
    ExecutorService executor = Executors.newFixedThreadPool(nThreads);
    Runnable task = () -> {
      String word;
      while ((word = wordsToSolve.poll()) != null) {
        long total = solveAllStarting(dict, word);
        results.add(new Result(word, total));
      }
      results.add(SENTINEL_RESULT);
    };
    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < nThreads; i++) {
      futures.add(executor.submit(task));
    }
    long startTime = System.nanoTime();
    try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output, StandardOpenOption.APPEND))) {
      int remainingThreads = nThreads;
      int done = 0;
      while (remainingThreads > 0) {
        Result result = results.take();
        if (result.equals(SENTINEL_RESULT)) {
          --remainingThreads;
        } else {
          done++;
          long elapsed = System.nanoTime() - startTime;
          writer.printf("%s %d %ds %.1fs per\n", result.starting, result.total, elapsed / 1_000_000_000, elapsed / 1e9 / done);
          writer.flush();
        }
      }
    }
    for (Future<?> future : futures) {
      future.get();
    }
    executor.shutdown();
  }

  public static void main(String[] args) throws Exception {
    if (false) {
      parallelSolve(Dictionary.create());
      return;
    }
    if (true) {
      solveAll();
      return;
    }
    if (true) {
      System.out.println(solve(Dictionary.create(), "panic"));
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
      if (!dict.guessWords().contains(guess)) {
        System.err.printf("Guess %s is not in the dictionary\n", guess);
        System.exit(1);
      }
      Score score = Score.parse(args[i + 1]);
      scores = scores.plus(guess, score);
    }
    System.out.println("starting scores: " + scores);
    Wordle wordle = new Wordle(dict, scores);
    System.out.printf("guesses: %s\n", wordle.guesses());
  }
}
