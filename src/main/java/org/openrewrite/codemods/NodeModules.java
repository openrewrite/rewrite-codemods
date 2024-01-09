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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.openrewrite.ExecutionContext;
import org.openrewrite.scheduling.WorkingDirectoryExecutionContextView;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

class NodeModules {
    private static final String NODE_MODULES_KEY = ApplyCodemod.class.getName() + ".NODE_MODULES";

    static Path init(ExecutionContext ctx) {
        Path nodeModules = ctx.getMessage(NODE_MODULES_KEY);
        if (nodeModules == null) {
            ctx.putMessage(NODE_MODULES_KEY, nodeModules = extractNodeModules(() -> {
                try {
                    WorkingDirectoryExecutionContextView view = WorkingDirectoryExecutionContextView.view(ctx);
                    return Files.createDirectory(view.getWorkingDirectory().resolve("codemods-npm"));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).resolve("node_modules"));
        }
        return nodeModules;
    }

    private static Path extractNodeModules(Supplier<Path> dir) {
        try {
            Path result = extractResources("codemods", dir);
            recreateBinSymlinks(result);
            return result;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path extractResources(String resource, String dir, ExecutionContext ctx) {
        return extractResources(resource, () -> {
            try {
                WorkingDirectoryExecutionContextView view = WorkingDirectoryExecutionContextView.view(ctx);
                return Files.createDirectory(view.getWorkingDirectory().resolve(dir));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static Path extractResources(String resource, Supplier<Path> dir) {
        try {
            URI uri = Objects.requireNonNull(NodeModules.class.getClassLoader().getResource(resource)).toURI();
            if ("jar".equals(uri.getScheme())) {
                try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap(), null)) {
                    Path codemodsPath = fileSystem.getPath("/" + resource);
                    Path target = dir.get();
                    copyRecursively(codemodsPath, target);
                    return target;
                }
            } else if ("file".equals(uri.getScheme())) {
                return Paths.get(uri);
            } else {
                throw new IllegalArgumentException("Unsupported scheme: " + uri.getScheme());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void copyRecursively(Path sourceDir, Path targetDir) throws IOException, InterruptedException {
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            stream.forEach(source -> {
                try {
                    // IMPORTANT: `toString()` call here is required as paths have different file systems
                    Files.copy(source, targetDir.resolve(sourceDir.relativize(source).toString()), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            });
        }
    }

    /**
     * The `node_modules/.bin` directory contains symlinks (corresponding to the `bin` key in the package's `package.json`)
     * that point to the package's corresponding script. These must exist in order for the codemod to work properly.
     * <p>
     * Since Gradle won't preserve relative symlinks properly (see https://github.com/gradle/gradle/issues/3982) and
     * jar files cannot contain symlinks, these must be recreated manually.
     */
    private static void recreateBinSymlinks(Path target) throws IOException, InterruptedException {
        Path binDir = target.resolve("node_modules/.bin");

        // delete any existing files
        try (Stream<Path> files = Files.list(binDir)) {
            files.forEach(f -> {
                try {
                    Files.delete(f);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        // recreate symlinks from `package.json`
        try (Stream<Path> packageJsonFiles = Files.walk(target.resolve("node_modules"), 2)
                .filter(p -> p.getFileName().toString().equals("package.json"))) {
            ObjectMapper mapper = new ObjectMapper();
            packageJsonFiles.forEach(p -> {
                try {
                    JsonNode jsonNode = mapper.readTree(p.toFile());
                    JsonNode bin = jsonNode.get("bin");
                    if (bin instanceof ObjectNode) {
                        for (Iterator<Map.Entry<String, JsonNode>> it = bin.fields(); it.hasNext(); ) {
                            Map.Entry<String, JsonNode> entry = it.next();
                            if (entry.getValue() instanceof TextNode) {
                                Path binScript = p.resolveSibling(entry.getValue().asText());
                                if (Files.exists(binScript)) {
                                    //noinspection ResultOfMethodCallIgnored
                                    binScript.toFile().setExecutable(true);
                                    Path symlink = binDir.resolve(entry.getKey());
                                    Files.createSymbolicLink(symlink, binDir.relativize(binScript));
                                }
                            }
                        }
                    } else if (bin instanceof TextNode) {
                        Path binScript = p.resolveSibling(bin.asText());
                        if (Files.exists(binScript)) {
                            Path symlink = binDir.resolve(bin.asText());
                            Files.createSymbolicLink(symlink, binDir.relativize(binScript));
                        }
                    }
                } catch (IOException ignore) {
                }
            });
        }
    }
}
