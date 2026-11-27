package com.schoste.ddd.util.fs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.schoste.ddd.util.AbstractCachedObject;

/**
 * Implements {@link com.schoste.ddd.util.AbstractCachedObject}.
 * The objects are cached by serializing them into a temporary file on the file system.
 * The file is deleted by {@link #close()}, or when the JVM exits if the instance was not closed.
 * <p>
 * Each cache file is created atomically, so instances may be created concurrently in the same folder,
 * also by several JVMs. A deserialized instance shares the cache file with the original but not its lock;
 * see the thread safety rules of {@link com.schoste.ddd.util.CachedObject}.
 * </p>
 *
 * @param <T> the class of the object to cache
 */
public class CachedObjectImpl<T extends Serializable> extends AbstractCachedObject<T>
{
    /**
     * Only the path to the cache file and the hash code of the cached object are serialized, not the cached object itself.
     */
    @SuppressWarnings("unused")
    static private final ObjectStreamField[] serialPersistentFields =
    {
        new ObjectStreamField("cacheFilePath", String.class),
        new ObjectStreamField("objHashCode", Integer.class),
    };

    /**
     * Absolute paths of the cache files created by this JVM which were not closed yet.
     * A shutdown hook deletes them when the JVM exits.
     * Unlike with {@link File#deleteOnExit()}, {@link #close()} removes the path again,
     * so the set does not grow with every instance ever created.
     */
    static final Set<String> cacheFilesToDeleteOnExit = ConcurrentHashMap.newKeySet();

    static
    {
        Thread deleteCacheFiles = new Thread(() ->
        {
            for (String cacheFilePath : cacheFilesToDeleteOnExit) new File(cacheFilePath).delete();
        }, CachedObjectImpl.class.getSimpleName() + "-deleteCacheFiles");

        try
        {
            Runtime.getRuntime().addShutdownHook(deleteCacheFiles);
        }
        catch (IllegalStateException e)
        {
            // the JVM is already shutting down; File#deleteOnExit() would silently ignore this as well
        }
    }

    /**
     * How often creating a cache file may fail with {@link AccessDeniedException} before the constructor gives up
     * (see {@link #initializeCacheFile(String, Object)}).
     */
    static final int MAX_ACCESS_DENIED_RETRIES = 100;

    /**
     * Creates the folder if it doesn't exist and checks that it is a folder.
     * It returns nothing on purpose: callers use their storage path as is, so no mutant of mutation testing (PIT) can
     * replace the folder with an empty path, which would put cache files into the working directory.
     */
    static private void initializeCacheFileFolder(String storagePath) throws IllegalArgumentException, IllegalStateException, Exception
    {
        if (storagePath == null) throw new IllegalArgumentException("storagePath");

        Path _storagePath = Paths.get(storagePath);

        if (!Files.exists(_storagePath)) Files.createDirectories(_storagePath);
        if (!Files.isDirectory(_storagePath)) throw new IllegalArgumentException("storagePath");
    }

    /**
     * Creates a new cache file in the folder and writes the object into it.
     * The file is created atomically ({@link Files#createFile}), so concurrent callers,
     * also in other JVMs, never get the same file. Creating it also checks that the folder is writable.
     * If the name is taken, the next one is tried. On Windows, creating a file also fails with
     * {@link AccessDeniedException} while another thread deletes a file of the same name, so that is retried with the
     * next name too, but at most {@link #MAX_ACCESS_DENIED_RETRIES} times: a folder which denies writing denies every name.
     * If writing fails (e.g. the object isn't serializable), the file is deleted again and the failure is thrown.
     */
    static private <T> File initializeCacheFile(String storagePath, T obj) throws IllegalArgumentException, Exception
    {
        if (obj == null) throw new IllegalArgumentException();

        long cacheFileName = System.currentTimeMillis();
        int accessDenied = 0;
        File cacheFile = null;

        while (cacheFile == null)
        {
            Path candidate = Paths.get(storagePath, Long.toString(cacheFileName));

            try
            {
                cacheFile = Files.createFile(candidate).toFile();
            }
            catch (FileAlreadyExistsException e)
            {
                cacheFileName++;
            }
            catch (AccessDeniedException e)
            {
                if (++accessDenied > MAX_ACCESS_DENIED_RETRIES) throw e;

                cacheFileName++;
            }
        }

        cacheFilesToDeleteOnExit.add(cacheFile.getAbsolutePath());

        try (FileOutputStream fos = new FileOutputStream(cacheFile);
             ObjectOutputStream oos = new ObjectOutputStream(fos))
        {
            oos.writeObject(obj);
        }
        catch (Exception e)
        {
            // the streams are closed here already, so the file can be deleted (also on Windows)
            try
            {
                Files.deleteIfExists(cacheFile.toPath());
                cacheFilesToDeleteOnExit.remove(cacheFile.getAbsolutePath());
            }
            catch (IOException deleteFailure)
            {
                // stays registered, so the shutdown hook deletes it
                e.addSuppressed(deleteFailure);
            }
            throw e;
        }

        return cacheFile;
    }

    /**
     * Writes the path to the cache file and the hash code of the cached object.
     *
     * @serialData the absolute path to the cache file ({@code cacheFilePath})
     *             and the hash code of the cached object, obtained when the cached instance was created ({@code objHashCode})
     * @param out the stream to write to
     * @throws IOException if writing to the stream failed
     */
    private void writeObject(ObjectOutputStream out) throws IOException
    {
        ObjectOutputStream.PutField fields = out.putFields();

        fields.put("cacheFilePath", this.cacheFilePath);
        fields.put("objHashCode", this.objHashCode);

        out.writeFields();
    }

    /**
     * Reads the path to the cache file and the hash code of the cached object, and loads the cached object from the cache file.
     * If the stream contains no hash code (it was written by a version which did not serialize it),
     * the hash code of the loaded object is used.
     *
     * @param in the stream to read from
     * @throws IOException if reading from the stream failed
     * @throws ClassNotFoundException if the class of a serialized object could not be found
     * @throws Exception re-throws every exception of loading the cached object
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException, Exception
    {
        ObjectInputStream.GetField fields = in.readFields();

        this.cacheFilePath = (String) fields.get("cacheFilePath", null);
        this.cacheFile = new File(this.cacheFilePath);

        T obj = this.load();
        Integer hashCode = (Integer) fields.get("objHashCode", null);

        this.objRef = new WeakReference<T>(obj);
        this.objRefLock = new Object();
        this.objHashCode = (hashCode != null) ? hashCode : obj.hashCode();
    }

    /**
     * Absolute path to the cache file
     */
    protected String cacheFilePath;

    /**
     * The cache file as {@link java.io.File} object.
     */
    protected transient File cacheFile;

    /**
     * Serializes the object into the cache file, replacing its content.
     *
     * @param obj the object to save, not null
     * @throws IllegalArgumentException if obj is null
     * @throws Exception re-throws every exception of writing the file (e.g. {@link java.io.NotSerializableException})
     */
    @Override
    protected void save(T obj) throws Exception
    {
        if (obj == null) throw new IllegalArgumentException();

        try (FileOutputStream fos = new FileOutputStream(this.cacheFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos))
        {
            oos.writeObject(obj);
        }
    }

    /**
     * Deserializes the object from the cache file.
     *
     * @return a new instance of the object, as it was saved last
     * @throws IllegalStateException if there is no cache file (the instance was closed, not initialized,
     *         or the file was deleted)
     * @throws Exception re-throws every exception of reading the file
     */
    @Override
    @SuppressWarnings("unchecked")
    protected T load() throws Exception
    {
        if ((this.cacheFile == null) || !this.cacheFile.exists()) throw new IllegalStateException();

        try (FileInputStream fis = new FileInputStream(this.cacheFile);
            ObjectInputStream ois = new ObjectInputStream(fis))
        {
            return (T) ois.readObject();
        }
    }

    /**
     * Constructor for serialization and deserializion.
     * When used for any other purpose, the created instance is expect not work and its method will throw {@link java.lang.IllegalStateException}
     */
    public CachedObjectImpl() { }

    /**
     * Creates a new instance for an object to cache
     *
     * @param object the instance to the object to cache
     * @throws IllegalArgumentException thrown if object is null
     * @throws IllegalStateException thrown if the cache file could not be created for whatever reason
     * @throws Exception re-throws all other exceptions
     */
    public CachedObjectImpl(T object) throws IllegalArgumentException, IllegalStateException, Exception
    {
        this(object, System.getProperty("java.io.tmpdir"));
    }

    /**
     * Creates a new instance for an object to cache
     * and allows to specify the folder where to store the cache file.
     *
     * @param object the instance to the object to cache
     * @param storagePath the folder of the cache file
     * @throws IllegalArgumentException thrown if object is null or if the storage path is either null or doesn't exist as directory
     * @throws IllegalStateException thrown if the cache file could not be created for whatever reason
     * @throws Exception re-throws all other exceptions
     */
    public CachedObjectImpl(T object, String storagePath) throws IllegalArgumentException, IllegalStateException, Exception
    {
        if (object == null) throw new IllegalArgumentException("object");
        if (storagePath == null) throw new IllegalArgumentException("storagePath");

        CachedObjectImpl.initializeCacheFileFolder(storagePath);

        this.cacheFile = CachedObjectImpl.initializeCacheFile(storagePath, object);
        this.cacheFilePath = this.cacheFile.getAbsolutePath();
        this.objRef = new WeakReference<T>(object);
        this.objHashCode = object.hashCode();
    }

    /**
     * Deletes the cache file; afterwards {@link #get()} and {@link #commit()} throw {@link IllegalStateException}.
     * Does nothing if the instance was already closed or was never initialized (see {@link #CachedObjectImpl()}).
     *
     * @see java.io.Closeable#close()
     * @throws IOException never thrown by this implementation; declared by {@link java.io.Closeable#close()}
     */
    @Override
    public void close() throws IOException
    {
        synchronized (this.objRefLock)
        {
            this.objRef = null;

            if (this.cacheFile == null) return;

            this.cacheFile.delete();

            cacheFilesToDeleteOnExit.remove(this.cacheFilePath);
        }
    }

    /**
     * Creates a clone from the current state of the cached object (see {@link #get()}),
     * including changes which were not committed yet.
     * The clone writes a copy of the object into its own cache file in the same folder and holds its own instance of the object,
     * so changes to the object of one instance do not affect the other.
     *
     * @see java.lang.Object#clone()
     * @return a clone of this instance
     * @throws CloneNotSupportedException thrown if cloning failed (e.g. because the instance was closed),
     *         with the original exception as cause and its message
     */
    @Override
    public Object clone() throws CloneNotSupportedException
    {
        try
        {
            String storagePath = this.cacheFile.getParent();
            T objToClone = this.get();
            CachedObjectImpl<T> clone = new CachedObjectImpl<>(objToClone, storagePath);

            // the constructor references objToClone, which is the object of this instance;
            // the clone must reference its own copy, which is loaded from its cache file
            clone.objRef = new WeakReference<T>(clone.load());

            return clone;
        }
        catch (Exception e)
        {
            CloneNotSupportedException failure = new CloneNotSupportedException(e.getMessage());

            failure.initCause(e);

            throw failure;
        }
    }

    /**
     * Compares the cached objects of both instances (see {@link com.schoste.ddd.util.CachedObject#equals(Object)}).
     * Returns false, without writing anything, if one of the objects can't be read (e.g. an instance was closed).
     *
     * @see java.lang.Object#equals(java.lang.Object)
     * @param other the object to compare to
     * @return true if other is a cached object whose object is equal to the object of this instance
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object other)
    {
        if (other == null) return false;

        try
        {
            if (other instanceof AbstractCachedObject)
            {
                AbstractCachedObject<T> otherCache = (AbstractCachedObject<T>) other;
                T otherObj = otherCache.get();
                T thisObj = this.get();

                return thisObj.equals(otherObj);
            }
        }
        catch (Exception e)
        {
            // an object which can't be read is not equal to anything; the result already says so
        }

        return false;
    }
}
