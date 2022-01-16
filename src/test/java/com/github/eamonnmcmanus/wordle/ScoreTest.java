package com.github.eamonnmcmanus.wordle;

import com.google.common.truth.Expect;
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
}