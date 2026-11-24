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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.schoste.ddd.util.AbstractCachedObject;

/**
 * Implements {@see com.schoste.ddd.util.AbstractCachedObject}.
 * The objects are cached by serializing them into a temporary file on the file system, which is supposed to be deleted on exit (see {@see File#deleteOnExit()})
 * 
 * @param <T> the class of the object to cache
 */
public class CachedObjectImpl<T extends Serializable> extends AbstractCachedObject<T>
{
    @SuppressWarnings("unused")
    static private final ObjectStreamField[] serialPersistentFields = 
    {
        new ObjectStreamField("cacheFilePath", String.class),
    };

    static private String initializeCacheFileFolder(String storagePath) throws IllegalArgumentException, IllegalStateException, Exception
    {
        if (storagePath == null) throw new IllegalArgumentException("storagePath");

        Path _storagePath = Paths.get(storagePath);

        if (!Files.exists(_storagePath)) Files.createDirectories(_storagePath);
        if (!Files.isDirectory(_storagePath)) throw new IllegalArgumentException("storagePath");

        Path testFile = Paths.get(_storagePath.toString(), AbstractCachedObject.class.getSimpleName());

        Files.createFile(testFile);

        if (!Files.exists(testFile)) throw new IllegalStateException();

        Files.delete(testFile);

        if (Files.exists(testFile)) throw new IllegalStateException();

        return _storagePath.toString();
    }

    static private <T> File initializeCacheFile(String storagePath, T obj) throws IllegalArgumentException, Exception
    {
        if (obj == null) throw new IllegalArgumentException();

        long cacheFileName = System.currentTimeMillis();

        while (Files.exists(Paths.get(storagePath.toString(), Long.toString(cacheFileName)))) cacheFileName++;

        File cacheFile = Paths.get(storagePath.toString(), String.valueOf(cacheFileName)).toFile();
             cacheFile.deleteOnExit();

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

    private void writeObject(ObjectOutputStream out) throws IOException 
    {
        ObjectOutputStream.PutField fields = out.putFields();

        fields.put("cacheFilePath", this.cacheFilePath);
    
        out.writeFields();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException, Exception
    {
        ObjectInputStream.GetField fields = in.readFields();

        this.cacheFilePath = (String) fields.get("cacheFilePath", null);
        this.cacheFile = new File(this.cacheFilePath);
        this.objRef = new WeakReference<T>(this.load());
        this.objRefLock = new Object();
    }

    /**
     * Absolute path to the cache file
     */
    protected String cacheFilePath;

    /**
     * The cache file as {@see java.io.File} object.
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
     * When used for any other purpose, the created instance is expect not work and its method will throw {@see java.lang.IllegalStateException}
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
        }
    }

    /**    (non-Javadoc)
     * 
     * @see java.lang.Object#clone()
     * @return a clone of this instance
     * @throws CloneNotSupportedException thrown if cloning failed. The stack trace of the original exception is written to {@link System.err}
     */
    @Override 
    public Object clone() throws CloneNotSupportedException
    {
        try
        {
            String storagePath = this.cacheFile.getParent();
            T objToClone = this.load();
            CachedObjectImpl<T> clone = new CachedObjectImpl<>(objToClone, storagePath);

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
