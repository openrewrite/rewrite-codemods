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

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.quark.Quark;
import org.openrewrite.scheduling.WorkingDirectoryExecutionContextView;
import org.openrewrite.text.PlainText;
import org.openrewrite.tree.ParseError;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptyList;

@Value
@EqualsAndHashCode(callSuper = true)
public class ApplyCodemod extends ScanningRecipe<ApplyCodemod.Accumulator> {

    private static final String FIRST_CODEMOD = ApplyCodemod.class.getName() + ".FIRST_CODEMOD";
    private static final String PREVIOUS_CODEMOD = ApplyCodemod.class.getName() + ".PREVIOUS_CODEMOD";
    private static final String INIT_REPO_DIR = ApplyCodemod.class.getName() + ".INIT_REPO_DIR";

    @Option(displayName = "NPM package containing the codemod",
            description = "The codemod's NPM package name.",
            example = "@next/codemod")
    String npmPackage;

    @Option(displayName = "Codemod NPM package version",
            description = "The codemod's NPM package version (defaults to `latest`).",
            example = "14.0.3",
            required = false)
    @Nullable
    String npmPackageVersion;

    @Option(displayName = "Codemod transform",
            description = "Transform to be applied using `jscodeshift`.",
            example = "built-in-next-font",
            required = false)
    String transform;

    @Option(displayName = "Codemod command arguments",
            description = "Arguments which get passed to the codemod command.",
            example = "built-in-next-font, ${repoDir}, --force",
            required = false)
    @Nullable
    List<String> codemodArgs;

    @Option(displayName = "Codemod command template",
            description = "Template for the command to execute (defaults to `${nodeModules}/.bin/jscodeshift -t ${nodeModules}/${npmPackage}/transforms/${transform} ${repoDir} ${codemodArgs}`).",
            example = "${nodeModules}/.bin/jscodeshift -t ${nodeModules}/${npmPackage}/transforms/${transform} ${repoDir} ${codemodArgs}",
            required = false)
    @Nullable
    String codemodCommandTemplate;

    @Override
    public String getDisplayName() {
        return "Applies a codemod to all source files";
    }

    @Override
    public String getDescription() {
        return "Applies a codemod represented by an NPM package to all source files.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        Path directory = createDirectory(ctx, "codemods-repo");
        if (ctx.getMessage(INIT_REPO_DIR) == null) {
            ctx.putMessage(INIT_REPO_DIR, directory);
            ctx.putMessage(FIRST_CODEMOD, ctx.getCycleDetails().getRecipePosition());
        }
        return new Accumulator(directory);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile && !(tree instanceof Quark) && !(tree instanceof ParseError) &&
                    !tree.getClass().getName().equals("org.openrewrite.java.tree.J$CompilationUnit")) {
                    SourceFile sourceFile = (SourceFile) tree;
                    String fileName = sourceFile.getSourcePath().getFileName().toString();
                    if (fileName.indexOf('.') > 0) {
                        String extension = fileName.substring(fileName.lastIndexOf('.') + 1);
                        acc.extensionCounts.computeIfAbsent(extension, e -> new AtomicInteger(0)).incrementAndGet();
                    }

                    // only extract initial source files for first codemod recipe
                    if (Objects.equals(ctx.getMessage(FIRST_CODEMOD), ctx.getCycleDetails().getRecipePosition())) {
                        // FIXME filter out more source types; possibly only write plain text, json, and yaml?
                        acc.writeSource(sourceFile);
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        List<String> command = codemodCommand(acc, ctx);

        Path previous = ctx.getMessage(PREVIOUS_CODEMOD);
        if (previous != null && !Objects.equals(ctx.getMessage(FIRST_CODEMOD), ctx.getCycleDetails().getRecipePosition())) {
            acc.copyFromPrevious(previous);
        }

        runCodemod(acc.getDirectory(), command);
        ctx.putMessage(PREVIOUS_CODEMOD, acc.getDirectory());

        // FIXME check for generated files
        return emptyList();
    }

    private List<String> codemodCommand(Accumulator acc, ExecutionContext ctx) {
        Path nodeModules = NodeModules.getNodeModulesDir(ctx);

        List<String> command = new ArrayList<>();
        command.add("node");
        String template = Optional.ofNullable(codemodCommandTemplate).orElse("${nodeModules}/.bin/jscodeshift -t ${nodeModules}/${npmPackage}/transforms/${transform} ${repoDir} ${codemodArgs}");
        for (String part : template.split(" ")) {
            part = part.trim();
            part = part.replace("${nodeModules}", nodeModules.toString());
            part = part.replace("${npmPackage}", npmPackage);
            part = part.replace("${transform}", transform);
            part = part.replace("${repoDir}", ".");
            part = part.replace("${parser}", acc.parser());
            int argsIdx = part.indexOf("${codemodArgs}");
            if (argsIdx != -1) {
                String prefix = part.substring(0, argsIdx);
                if (!prefix.isEmpty()) {
                    command.add(prefix);
                }
                for (String arg : Optional.ofNullable(codemodArgs).orElse(emptyList())) {
                    arg = arg.replace("${repoDir}", ".");
                    command.add(arg);
                }
                String suffix = part.substring(argsIdx + "${codemodArgs}".length());
                if (!suffix.isEmpty()) {
                    command.add(suffix);
                }
            } else {
                command.add(part);
            }
        }
        return command;
    }

    private static void runCodemod(Path dir, List<String> command) {
        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command(command);
            builder.directory(dir.toFile());
            // FIXME do something more meaningful with the output (including error handling)
            File nullFile = new File((System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null"));
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(nullFile));
            builder.redirectError(ProcessBuilder.Redirect.appendTo(nullFile));
            Process process = builder.start();
            process.waitFor();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path createDirectory(ExecutionContext ctx, String prefix) {
        WorkingDirectoryExecutionContextView view = WorkingDirectoryExecutionContextView.view(ctx);
        return Optional.of(view.getWorkingDirectory())
                .map(d -> d.resolve(prefix))
                .map(d -> {
                    try {
                        return Files.createDirectory(d);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .orElseThrow(() -> new IllegalStateException("Failed to create working directory for " + prefix));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    SourceFile sourceFile = (SourceFile) tree;
                    if (acc.wasModified(sourceFile)) {
                        // TODO parse sources like JSON where parser doesn't require an environment
                        return new PlainText(
                                tree.getId(),
                                sourceFile.getSourcePath(),
                                sourceFile.getMarkers(),
                                sourceFile.getCharset() != null ? sourceFile.getCharset().name() : null,
                                sourceFile.isCharsetBomMarked(),
                                sourceFile.getFileAttributes(),
                                null,
                                acc.content(sourceFile),
                                emptyList()
                        );
                    }
                }
                return tree;
            }
        };
    }

    @Data
    public static class Accumulator {
        final Path directory;
        final Map<Path, Long> modificationTimestamps = new HashMap<>();
        final Map<String, AtomicInteger> extensionCounts = new HashMap<>();

        private void copyFromPrevious(Path previous) {
            try {
                Files.walkFileTree(previous, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Path target = directory.resolve(previous.relativize(dir));
                        if (!target.equals(directory)) {
                            Files.createDirectory(target);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        try {
                            Path target = directory.resolve(previous.relativize(file));
                            Files.copy(file, target);
                            modificationTimestamps.put(target, Files.getLastModifiedTime(target).toMillis());
                        } catch (NoSuchFileException ignore) {
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public String parser() {
            if (extensionCounts.containsKey("tsx")) {
                return "tsx";
            } else if (extensionCounts.containsKey("ts")) {
                return "ts";
            } else {
                return "babel";
            }
        }

        public void writeSource(SourceFile tree) {
            try {
                Path path = resolvedPath(tree);
                Files.createDirectories(path.getParent());
                Path written = Files.write(path, tree.printAllAsBytes());
                modificationTimestamps.put(written, Files.getLastModifiedTime(written).toMillis());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public boolean wasModified(SourceFile tree) {
            Path path = resolvedPath(tree);
            Long before = modificationTimestamps.get(path);
            try {
                if (before == null) return false;
                return Files.getLastModifiedTime(path).toMillis() > before;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public String content(SourceFile tree) {
            try {
                Path path = resolvedPath(tree);
                return tree.getCharset() != null ? new String(Files.readAllBytes(path), tree.getCharset()) :
                        new String(Files.readAllBytes(path));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private Path resolvedPath(SourceFile tree) {
            return directory.resolve(tree.getSourcePath());
        }
    }
}
