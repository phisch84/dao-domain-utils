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

    static private String initializeCacheFileFolder(String storagePath) throws IllegalArgumentException, IllegalStateException, Exception
    {
        if (storagePath == null) throw new IllegalArgumentException("storagePath");

        Path _storagePath = Paths.get(storagePath);

        if (!Files.exists(_storagePath)) Files.createDirectories(_storagePath);
        if (!Files.isDirectory(_storagePath)) throw new IllegalArgumentException("storagePath");

        return _storagePath.toString();
    }

    /**
     * Creates a new cache file in the folder and writes the object into it.
     * The file is created atomically ({@link Files#createFile}), so concurrent callers,
     * also in other JVMs, never get the same file. Creating it also checks that the folder is writable.
     */
    static private <T> File initializeCacheFile(String storagePath, T obj) throws IllegalArgumentException, Exception
    {
        if (obj == null) throw new IllegalArgumentException();

        long cacheFileName = System.currentTimeMillis();
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
        }

        cacheFilesToDeleteOnExit.add(cacheFile.getAbsolutePath());

        try (FileOutputStream fos = new FileOutputStream(cacheFile);
             ObjectOutputStream oos = new ObjectOutputStream(fos))
        {
            oos.writeObject(obj);
        }
        catch (Exception e)
        {
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

    /**    (non-Javadoc)
     *
     * @see com.schoste.ddd.util.AbstractCachedObject#save(java.io.Serializable)
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
        catch (Exception e)
        {
            throw e;
        }
    }

    /**    (non-Javadoc)
     *
     * @see com.schoste.ddd.util.AbstractCachedObject#load()
     */
    @Override
    @SuppressWarnings("unchecked")
    protected T load() throws Exception
    {
        if ((this.cacheFile == null) || !this.cacheFile.exists()) throw new IllegalStateException();

        try(FileInputStream fis = new FileInputStream(this.cacheFile);
            ObjectInputStream ois = new ObjectInputStream(fis))
        {
            T obj = (T) ois.readObject();

            return obj;
        }
        catch (Exception ex)
        {
            throw ex;
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

        String pathToCacheFile = CachedObjectImpl.initializeCacheFileFolder(storagePath);

        this.cacheFile = CachedObjectImpl.initializeCacheFile(pathToCacheFile, object);
        this.cacheFilePath = this.cacheFile.getAbsolutePath();
        this.objRef = new WeakReference<T>(object);
        this.objHashCode = object.hashCode();
    }

    /**    (non-Javadoc)
     *
     * @see java.io.Closeable#close()
     */
    @Override
    public void close() throws IOException
    {
        synchronized (this.objRefLock)
        {
            this.objRef = null;
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
     * @throws CloneNotSupportedException thrown if cloning failed. The stack trace of the original exception is written to {@link System#err}
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
            e.printStackTrace(System.err);

            throw new CloneNotSupportedException();
        }
    }

    /**    (non-Javadoc)
     *
     * @see java.lang.Object#equals(java.lang.Object)
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
            e.printStackTrace(System.err);
        }

        return false;
    }
}
