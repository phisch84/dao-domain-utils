package com.schoste.ddd.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helpers which don't depend on the class under test, shared by all test classes (also those in sub-packages).
 * Helpers which need the internals of a class (e.g. {@code simulateGC}) stay in its contract test.
 */
public final class TestHelpers
{
    private TestHelpers() { }

    /**
     * Creates a mutable list, e.g. as a value which tests can change.
     *
     * @param values the elements
     * @return a new {@link ArrayList} with the elements
     */
    public static ArrayList<String> list(String... values)
    {
        return new ArrayList<>(Arrays.asList(values));
    }

    /**
     * Creates a set, e.g. to compare with {@code keySet()}.
     *
     * @param keys the elements
     * @return a new {@link HashSet} with the elements
     */
    public static Set<String> set(String... keys)
    {
        return new HashSet<>(Arrays.asList(keys));
    }

    /**
     * Serializes an object into a byte array and deserializes it again.
     *
     * @param <T> the class of the object
     * @param obj the object to serialize
     * @return the deserialized copy
     * @throws Exception re-throws every exception
     */
    @SuppressWarnings("unchecked")
    public static <T> T serializeAndDeserialize(T obj) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream oos = new ObjectOutputStream(bytes))
        {
            oos.writeObject(obj);
        }

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
        {
            return (T) ois.readObject();
        }
    }

    /**
     * Counts the files and folders directly in a folder, e.g. the cache files in a storage path.
     *
     * @param folder the folder
     * @return the number of entries in the folder
     */
    public static int countFiles(File folder)
    {
        return folder.list().length;
    }

    /**
     * Deletes a folder with everything in it, e.g. the temporary folder of a test in its {@code finally} block.
     * Does nothing if the folder doesn't exist.
     *
     * @param folder the folder to delete
     * @throws IOException if a file could not be deleted
     */
    public static void deleteRecursively(File folder) throws IOException
    {
        Path root = folder.toPath();

        if (!Files.exists(root)) return;

        List<Path> paths;

        try (Stream<Path> walk = Files.walk(root))
        {
            // deepest paths first, so every folder is empty when it is deleted
            paths = walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        }
        for (Path path : paths) Files.delete(path);
    }
}
