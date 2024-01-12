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

import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

public class ESLintMessages extends DataTable<ESLintMessages.Row> {

    public ESLintMessages(Recipe recipe) {
        super(recipe,
                "ESLint messages",
                "Errors and warnings as reported by ESLint.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Source Path", description = "The source path of the file.")
        String sourcePath;
        @Column(displayName = "Rule ID", description = "ESLint Rule ID.")
        String ruleId;
        @Column(displayName = "Severity", description = "Either `Warning` or `Error`.")
        Severity severity;
        @Column(displayName = "Fatal", description = "Is this a fatal error (like a parse error).")
        boolean fatal;
        @Column(displayName = "Message", description = "The message created by the rule.")
        String message;
        @Column(displayName = "Line", description = "Line in source file this message pertains to.")
        int line;
        @Column(displayName = "Column", description = "Column in source file this message pertains to.")
        int column;
    }

    public enum Severity {
        Warning,
        Error;

        public static Severity of(int severity) {
            return severity == 1 ? Warning : Error;
        }
    }
}