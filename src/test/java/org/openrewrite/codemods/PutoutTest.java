/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.codemods;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.openrewrite.test.RewriteTest;

import java.util.Set;

import static org.openrewrite.test.SourceSpecs.text;

@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
public class PutoutTest implements RewriteTest {

    @Test
    void noConfig() {
        rewriteRun(
          spec -> spec.recipe(new Putout(null)),
          text(
            //language=js
            """
              function notUsed() {}
              export function used() {}
              """,
            """
              export function used() {}

              """,
            spec -> spec.path("src/Foo.js")
          )
        );
    }

    @Test
    void withRules() {
        rewriteRun(
          spec -> spec.recipe(new Putout(Set.of("conditions/merge-if-statements"))),
          text(
            //language=js
            """
              function notUsed() {}
              export function used() {}
                            
              if (a > b)
                  if (b < c)
                      console.log('hi');
              """,
            """
              function notUsed() {}
                            
              export function used() {}
              if (a > b && b < c)
                  console.log('hi');
                  
              """,
            spec -> spec.path("src/Foo.js")
          )
        );
    }

    @Test
    void jsx() {
        rewriteRun(
          spec -> spec.recipe(new Putout(null)),
          text(
            //language=js
            """
              export function Test() {
                  const greet = 'Hello World';
                  const unused = 'unused';
                  return (
                      <div>
                          <h1>{greet}</h1>
                      </div>
                  );
              }
              """,
            """
              export function Test() {
                  const greet = 'Hello World';
                 \s
                  return (
                      <div>
                          <h1>{greet}</h1>
                      </div>
                  );
              }
                            
              """,
            spec -> spec.path("src/Foo.jsx")
          )
        );
    }
}