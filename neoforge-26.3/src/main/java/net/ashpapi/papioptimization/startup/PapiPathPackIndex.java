package net.ashpapi.papioptimization.startup;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class PapiPathPackIndex {

    private final Set<String> namespaces;
    private final Map<String, ObjectOpenHashSet<String>> filesSetByNamespace;
    private final Map<String, List<FileEntry>> filesListByNamespace;

    private record FileEntry(String relPath, Path fullPath) {
    }

    private PapiPathPackIndex(
            Set<String> namespaces,
            Map<String, ObjectOpenHashSet<String>> filesSetByNamespace,
            Map<String, List<FileEntry>> filesListByNamespace
    ) {
        this.namespaces = namespaces;
        this.filesSetByNamespace = filesSetByNamespace;
        this.filesListByNamespace = filesListByNamespace;
    }

    public static @Nullable PapiPathPackIndex buildForType(Path root, PackType type, Logger logger) {
        Path typeDir = root.resolve(type.getDirectory());
        Set<String> nsSet = new LinkedHashSet<>();
        Map<String, ObjectOpenHashSet<String>> setMap = new Object2ObjectOpenHashMap<>();
        Map<String, List<FileEntry>> listMap = new Object2ObjectOpenHashMap<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(typeDir)) {
            for (Path nsPath : stream) {
                if (!Files.isDirectory(nsPath)) {
                    continue;
                }
                Path fileName = nsPath.getFileName();
                if (fileName == null) {
                    continue;
                }
                String ns = sanitizeName(fileName.toString());
                if (!Identifier.isValidNamespace(ns)) {
                    logger.warn("Non [a-z0-9_.-] character in namespace {} in pack directory {}, ignoring", ns, typeDir);
                    continue;
                }
                nsSet.add(ns);
                ObjectOpenHashSet<String> relSet = new ObjectOpenHashSet<>();
                ObjectArrayList<FileEntry> relList = new ObjectArrayList<>();
                walkNamespace(nsPath, relSet, relList);
                setMap.put(ns, relSet);
                listMap.put(ns, relList);
            }
        } catch (NoSuchFileException | NotDirectoryException e) {
            return new PapiPathPackIndex(Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap());
        } catch (IOException e) {
            return null;
        }
        return new PapiPathPackIndex(Set.copyOf(nsSet), setMap, listMap);
    }

    private static void walkNamespace(
            Path nsRoot,
            ObjectOpenHashSet<String> relSet,
            ObjectArrayList<FileEntry> relList
    ) throws IOException {
        Files.walkFileTree(nsRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }
                if (SharedConstants.IS_RUNNING_IN_IDE) {
                    Path fn = file.getFileName();
                    if (fn != null && ".ds_store".equalsIgnoreCase(fn.toString())) {
                        return FileVisitResult.CONTINUE;
                    }
                }
                Path rel = nsRoot.relativize(file);
                int nameCount = rel.getNameCount();
                if (nameCount <= 0) {
                    return FileVisitResult.CONTINUE;
                }
                String joined;
                if (nameCount == 1) {
                    joined = sanitizeName(rel.getName(0).toString());
                } else {
                    StringBuilder sb = new StringBuilder(32);
                    for (int i = 0; i < nameCount; i++) {
                        if (i > 0) {
                            sb.append('/');
                        }
                        sb.append(sanitizeName(rel.getName(i).toString()));
                    }
                    joined = sb.toString();
                }
                relSet.add(joined);
                relList.add(new FileEntry(joined, file));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String sanitizeName(String raw) {
        int len = raw.length();
        if (len > 0 && (raw.charAt(len - 1) == '/' || raw.charAt(len - 1) == '\\')) {
            return raw.substring(0, len - 1);
        }
        return raw;
    }

    public Set<String> getNamespaces() {
        return this.namespaces;
    }

    public boolean mayContain(String namespace, String path) {
        ObjectOpenHashSet<String> set = this.filesSetByNamespace.get(namespace);
        return set != null && set.contains(path);
    }

    public boolean listResources(
            String namespace,
            String pathPrefix,
            PackResources.ResourceOutput output,
            Logger logger
    ) {
        List<FileEntry> entries = this.filesListByNamespace.get(namespace);
        if (entries == null || entries.isEmpty()) {
            if (!pathPrefix.isEmpty()) {
                FileUtil.decomposePath(pathPrefix).ifError(err -> logger.error("Invalid path {}: {}", pathPrefix, err.message()));
            }
            return true;
        }
        if (FileUtil.decomposePath(pathPrefix).isError()) {
            return false;
        }
        String normalizedFilter = pathPrefix.isEmpty() || pathPrefix.endsWith("/") ? pathPrefix : (pathPrefix + "/");
        boolean matchAll = pathPrefix.isEmpty();
        for (int i = 0, n = entries.size(); i < n; i++) {
            FileEntry fe = entries.get(i);
            String rel = fe.relPath();
            if (!matchAll && !rel.startsWith(normalizedFilter)) {
                continue;
            }
            Identifier id = Identifier.tryBuild(namespace, rel);
            if (id == null) {
                Util.logAndPauseIfInIde("Invalid path in pack: " + namespace + ":" + rel + ", ignoring");
            } else if (PathPackResources.validatePath(fe.fullPath())) {
                output.accept(id, IoSupplier.create(fe.fullPath()));
            }
        }
        return true;
    }
}
