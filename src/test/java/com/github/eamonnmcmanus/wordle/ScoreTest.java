package com.github.eamonnmcmanus.wordle;

import static com.google.common.collect.ImmutableMultiset.toImmutableMultiset;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.truth.Expect;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author emcmanus
 */
public class ScoreTest {
  @Rule public final Expect expect = Expect.create();

  @Test
  public void score() {
    expect.that(Score.of("atone", "atone").toString()).isEqualTo("+++++");
    expect.that(Score.of("enota", "atone").toString()).isEqualTo("//+//");
    expect.that(Score.of("natty", "tangy").toString()).isEqualTo("/+/-+");
    expect.that(Score.of("natty", "tanay").toString()).isEqualTo("/+/-+");
    expect.that(Score.of("natay", "tangy").toString()).isEqualTo("/+/-+");
    expect.that(Score.of("natty", "tanny").toString()).isEqualTo("/+/-+");
    expect.that(Score.of("natty", "tanty").toString()).isEqualTo("/+/++");
    expect.that(Score.of("natty", "natyt").toString()).isEqualTo("+++//");
    expect.that(Score.of("aahed", "drama").toString()).isEqualTo("//--/");
  }

  @Test
  public void greenMask() {
    expect.that(Score.of("atone", "atone").greenMask()).isEqualTo(31 | (31 << 5) | (31 << 10) | (31 << 15) | (31 << 20));
    expect.that(Score.of("atone", "enota").greenMask()).isEqualTo(31 << 10);
    expect.that(Score.of("atone", "eaton").greenMask()).isEqualTo(0);
    expect.that(Score.of("natty", "tangy").greenMask()).isEqualTo((31 << 5) | (31 << 20));
  }

  @Test
  public void ochreMask() {
    expect.that(Score.of("atone", "atone").ochreMask()).isEqualTo(0);
    expect.that(Score.of("atone", "enota").ochreMask()).isEqualTo(31 | (31 << 5) | (31 << 15) | (31 << 20));
    expect.that(Score.of("atone", "eaton").ochreMask()).isEqualTo(31 | (31 << 5) | (31 << 10) | (31 << 15) | (31 << 20));
    expect.that(Score.of("natty", "tangy").ochreMask()).isEqualTo(31 | (31 << 10));
  }

  // Experiments for a blog post
  @Test @Ignore
  public void credo() {
    Dictionary dict = Dictionary.create();
    int traceCode = Dictionary.encode("trace");
    Score traceScore = Score.of("trace", "creep");
    List<String> consistent = dict.solutionWords().stream()
        .filter(word -> Score.of(traceCode, word).equals(traceScore))
        .map(Dictionary::decode)
        .toList();
    expect.that(consistent).isEmpty();
    Score credoScore = Score.of("credo", "creep");
    Multimap<Score, String> scoreMap = HashMultimap.create();
    for (String word : consistent) {
      scoreMap.put(Score.of("credo", word), word);
    }
    expect.that(scoreMap).isEmpty();
    List<String> best = new ArrayList<>();
    next:
    for (String word : dict.guessWords().stream().map(Dictionary::decode).toList()) {
      Set<Score> scores = new HashSet<>();
      for (String hidden : consistent) {
        if (!scores.add(Score.of(word, hidden))) {
          continue next;
        }
      }
      best.add(word);
    }
    expect.that(best).hasSize(0);
    scoreMap.clear();
    for (String word : consistent) {
      scoreMap.put(Score.of("plied", word), word);
    }
    expect.that(scoreMap).isEmpty();
  }
}