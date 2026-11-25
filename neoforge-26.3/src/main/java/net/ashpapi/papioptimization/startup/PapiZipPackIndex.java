package net.ashpapi.papioptimization.startup;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.io.File;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.slf4j.Logger;

public final class PapiZipPackIndex {

    private final ObjectOpenHashSet<String> allFiles;
    private final Set<String>[] namespacesByType;
    private final Map<String, List<String>>[] pathsByTypeAndNamespace;

    @SuppressWarnings("unchecked")
    private PapiZipPackIndex(
            ObjectOpenHashSet<String> allFiles,
            Set<String>[] namespacesByType,
            Map<String, List<String>>[] pathsByTypeAndNamespace
    ) {
        this.allFiles = allFiles;
        this.namespacesByType = namespacesByType;
        this.pathsByTypeAndNamespace = pathsByTypeAndNamespace;
    }

    @SuppressWarnings("unchecked")
    public static PapiZipPackIndex build(ZipFile zipFile, String rawPrefix, File sourceFile, Logger logger) {
        PackType[] types = PackType.values();
        int typeCount = types.length;
        String[] typePrefixes = new String[typeCount];
        Set<String>[] nsSets = new Set[typeCount];
        Set<String>[] warnedInvalidNs = new Set[typeCount];
        Map<String, List<String>>[] nsFileMaps = new Map[typeCount];

        for (int t = 0; t < typeCount; t++) {
            String dir = types[t].getDirectory() + "/";
            typePrefixes[t] = rawPrefix.isEmpty() ? dir : (rawPrefix + "/" + dir);
            nsSets[t] = new LinkedHashSet<>();
            warnedInvalidNs[t] = new ObjectOpenHashSet<>();
            nsFileMaps[t] = new Object2ObjectOpenHashMap<>();
        }

        ObjectOpenHashSet<String> files = new ObjectOpenHashSet<>(Math.max(16, zipFile.size()));
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            boolean isDir = entry.isDirectory();
            if (!isDir) {
                files.add(name);
            }
            for (int t = 0; t < typeCount; t++) {
                String tp = typePrefixes[t];
                if (!name.startsWith(tp)) {
                    continue;
                }
                int nsStart = tp.length();
                int slashIdx = name.indexOf('/', nsStart);
                String ns = slashIdx == -1 ? name.substring(nsStart) : name.substring(nsStart, slashIdx);
                if (ns.isEmpty()) {
                    continue;
                }
                if (!Identifier.isValidNamespace(ns)) {
                    if (warnedInvalidNs[t].add(ns)) {
                        logger.warn("Non [a-z0-9_.-] character in namespace {} in pack {}, ignoring", ns, sourceFile);
                    }
                    continue;
                }
                nsSets[t].add(ns);
                if (!isDir && slashIdx != -1 && slashIdx + 1 < name.length()) {
                    String relPath = name.substring(slashIdx + 1);
                    nsFileMaps[t].computeIfAbsent(ns, k -> new ObjectArrayList<>()).add(relPath);
                }
            }
        }

        Set<String>[] frozenNs = new Set[typeCount];
        for (int t = 0; t < typeCount; t++) {
            frozenNs[t] = Set.copyOf(nsSets[t]);
        }
        return new PapiZipPackIndex(files, frozenNs, nsFileMaps);
    }

    public boolean containsFile(String fullEntryPath) {
        return this.allFiles.contains(fullEntryPath);
    }

    public Set<String> getNamespaces(PackType type) {
        int ord = type.ordinal();
        if (Integer.compareUnsigned(ord, this.namespacesByType.length) < 0) {
            return this.namespacesByType[ord];
        }
        return Collections.emptySet();
    }

    public void listResources(
            ZipFile zipFile,
            String rawPrefix,
            PackType type,
            String namespace,
            String pathPrefix,
            PackResources.ResourceOutput output,
            Logger logger
    ) {
        int ord = type.ordinal();
        if (Integer.compareUnsigned(ord, this.pathsByTypeAndNamespace.length) >= 0) {
            return;
        }
        List<String> relPaths = this.pathsByTypeAndNamespace[ord].get(namespace);
        if (relPaths == null || relPaths.isEmpty()) {
            return;
        }
        String normalizedFilter = pathPrefix.isEmpty() || pathPrefix.endsWith("/") ? pathPrefix : (pathPrefix + "/");
        boolean matchAll = pathPrefix.isEmpty();
        String entryBase = (rawPrefix.isEmpty() ? "" : (rawPrefix + "/")) + type.getDirectory() + "/" + namespace + "/";
        for (int i = 0, n = relPaths.size(); i < n; i++) {
            String rel = relPaths.get(i);
            if (!matchAll && !rel.startsWith(normalizedFilter) && !rel.equals(pathPrefix)) {
                continue;
            }
            Identifier id = Identifier.tryBuild(namespace, rel);
            if (id != null) {
                ZipEntry entry = zipFile.getEntry(entryBase + rel);
                if (entry != null && !entry.isDirectory()) {
                    output.accept(id, IoSupplier.create(zipFile, entry));
                }
            } else {
                logger.warn("Invalid path in datapack: {}:{}, ignoring", namespace, rel);
            }
        }
    }
}
