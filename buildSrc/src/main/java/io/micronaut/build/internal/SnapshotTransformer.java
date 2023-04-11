package io.micronaut.build.internal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class SnapshotTransformer {
    private static final String MANAGED_MICRONAUT = "managed-micronaut";
    private SnapshotTransformer() {
    }
    private static final String SNAPSHOT = "-SNAPSHOT\"";
    private static final String MILESTONE = "-M";
    private static final String RELEASE_CANDIDATE = "-R";

    /**
     *
     * @param file libs.versions.toml file
     * @return File Contentents with replacements
     * @throws IOException Exception while reading the file
     */
    public static String replaceManagedMicronautVersionToSnapshots(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            while (line != null) {
                lines.add(snapshot(line));
                line = reader.readLine();
            }
        }
        return String.join("\n", lines);
    }


    public static String snapshot(String version) {
        if (!version.startsWith(MANAGED_MICRONAUT)) {
            return version;
        }
        if (version.contains(SNAPSHOT)) {
            return version;
        }
        if (version.contains(MILESTONE)) {
            return version.substring(0, version.indexOf(MILESTONE)) + SNAPSHOT;
        }
        if (version.contains(RELEASE_CANDIDATE)) {
            return version.substring(0, version.indexOf(RELEASE_CANDIDATE)) + SNAPSHOT;
        }
        String arr[] = version.split("\\.");
        if (arr.length == 3) {
            try {
                int patch = arr[2].contains("\"") ?
                    Integer.valueOf(arr[2].substring(0, arr[2].indexOf("\""))) :
                    Integer.valueOf(arr[2]);
                patch++;
                return arr[0] + "." + arr[1]  + "." + patch + SNAPSHOT;
            } catch (NumberFormatException e) {
                return version;
            }
        }
        return version;
    }
}
