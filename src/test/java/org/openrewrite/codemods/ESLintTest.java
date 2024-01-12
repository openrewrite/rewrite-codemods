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

import static org.openrewrite.test.SourceSpecs.text;

public class ESLintTest implements RewriteTest {

    @Test
    @DisabledIfEnvironmentVariable(named = "CI", matches = "true")
    void formatStatement() {
        rewriteRun(
          spec -> spec.recipe(new ESLint(null, null)),
          text(
            //language=js
            """
            console.log('foo')
            """,
            """
            ~~('console'~~(Parsing error: ')' expected.)~~> is not defined.)~~>console.log("foo");
            
            """,
            spec -> spec.path("src/Foo.js")
          )
        );
    }
}
