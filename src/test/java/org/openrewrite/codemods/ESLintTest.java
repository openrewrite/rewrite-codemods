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
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.test.SourceSpecs.text;

@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
public class ESLintTest implements RewriteTest {

    @Test
    void formatStatement() {
        rewriteRun(
          spec -> spec.recipe(new ESLint(null, null, null, null, null, null, null, List.of("eslint:recommended"), null, null, null)),
          text(
            //language=js
            """
              console.log('foo')
              """,
            """
              ~~('console' is not defined.
                          
              Disallow the use of undeclared variables unless mentioned in `/*global */` comments
                          
              Rule: no-undef, Severity: ERROR)~~>console.log('foo')
              """,
            spec -> spec.path("src/Foo.js")
          )
        );
    }

    @Test
    void multiple() {
        rewriteRun(
          spec -> spec.recipe(new ESLint(null, null, null, null, null, null, null, List.of("eslint:recommended"), null, null, null)),
          text(
            //language=js
            """
              console.log('foo')
              console.log('bar')
              """,
            """
              ~~('console' is not defined.
                            
              Disallow the use of undeclared variables unless mentioned in `/*global */` comments
                            
              Rule: no-undef, Severity: ERROR)~~>console.log('foo')
              ~~('console' is not defined.
                            
              Disallow the use of undeclared variables unless mentioned in `/*global */` comments
                            
              Rule: no-undef, Severity: ERROR)~~>console.log('bar')
              """,
            spec -> spec.path("src/Foo.js")
              .afterRecipe(text -> {
                  assertThat(text.getSnippets()).satisfiesExactly(
                    snippet -> {
                        assertThat(snippet.getText()).isEqualTo("console");
                        assertThat(snippet.getMarkers().getMarkers()).satisfiesExactly(
                          m -> assertThat(((SearchResult) m).getDescription()).contains("'console' is not defined.")
                        );
                    },
                    snippet -> {
                        assertThat(snippet.getText()).isEqualTo(".log('foo')\n");
                        assertThat(snippet.getMarkers().getMarkers()).isEmpty();
                    },
                    snippet -> {
                        assertThat(snippet.getText()).isEqualTo("console");
                        assertThat(snippet.getMarkers().getMarkers()).satisfiesExactly(
                          m -> assertThat(((SearchResult) m).getDescription()).contains("'console' is not defined.")
                        );
                    },
                    snippet -> {
                        assertThat(snippet.getText()).isEqualTo(".log('bar')");
                        assertThat(snippet.getMarkers().getMarkers()).isEmpty();
                    }
                  );
              })
          )
        );
    }

    @Test
    void configFile() {
        rewriteRun(
          spec -> spec.recipe(new ESLint(List.of("**/*.js"), null, null, null, null, null, null, null, null, null, """
            {
                "rules": {
                    "eqeqeq": "error",
                }
            }
            """)),
          text(
            //language=js
            """
              2 == 42;
              """,
            """
              2 ~~(Expected '===' and instead saw '=='.
                            
              Require the use of `===` and `!==`
                            
              Rule: eqeqeq, Severity: ERROR)~~>== 42;
              """,
            spec -> spec.path("src/Foo.js")
          )
        );
    }

    @Test
    void unicorn() {
        rewriteRun(
          spec -> spec.recipe(new ESLint(List.of("**/*.jsx"), null, null, null, null, null, null, null, null, true, """
            {
              "root": true,
              "parser": "@typescript-eslint/parser",
              "plugins": ["unicorn"],
              "rules": {
                "unicorn/better-regex": 2
              },
              "globals": {
                "browser": true,
                "node": true
              }
            }
            """)),
          text(
            //language=js
            """
              const regex = /[0-9]/;
              """,
            """
              const regex = /\\d/;
              """,
            spec -> spec.path("src/Foo.jsx")
          )
        );
    }

    @Test
    void reactJsx() {
        rewriteRun(
          spec -> spec.recipe(new ESLint(List.of("**/*.jsx"), null, null, null, null, null, null, null, null, true, """
            {
              "root": true,
              "parser": "@typescript-eslint/parser",
              "plugins": ["react"],
              "rules": {
                "react/jsx-props-no-multi-spaces": 2
              },
              "globals": {
                "browser": true,
                "node": true
              }
            }
            """)),
          text(
            //language=js
            """
              <App too    spacy />
              """,
            """
              <App too spacy />
              """,
            spec -> spec.path("src/Foo.jsx")
          )
        );
    }

    @Test
    void reactTsx() {
        rewriteRun(
          spec -> spec.recipe(new ESLint(List.of("**/*.tsx"), "", List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), true, """
            {
              "root": true,
              "parser": "@typescript-eslint/parser",
              "plugins": ["react"],
              "rules": {
                "react/jsx-sort-props": 2
              },
              "globals": {
                "browser": true,
                "node": true
              }
            }
            """)),
          text(
            //language=js
            """
              <Hello lastName="Smith" firstName="John" />
              """,
            """
              <Hello firstName="John" lastName="Smith" />
              """,
            spec -> spec.path("src/Foo.tsx")
          )
        );
    }
}