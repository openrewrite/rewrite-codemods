/*
 * Copyright 2023 the original author or authors.
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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.test.SourceSpecs.text;

public class NextJsCodemodsTest implements RewriteTest {

    @Test
    @Disabled("Requires NodeJS to be installed")
    void builtInNextFont() {
        rewriteRun(
          spec -> spec.recipeFromResource("/META-INF/rewrite/nextjs.yml", "org.openrewrite.codemods.nextjs.v13.2.BuiltInNextFont"),
          text(
            //language=js
            """
            import { Fira_Code } from "@next/font/google"
            
            const firaCode = Fira_Code({
                weight: "500",
                subsets: ["latin"]
            })
            """,
            //language=js
            """
            import { Fira_Code } from "next/font/google"
            
            const firaCode = Fira_Code({
                weight: "500",
                subsets: ["latin"]
            })
            """,
            spec -> spec.path("src/components/Code.js")
          )
        );
    }
}
