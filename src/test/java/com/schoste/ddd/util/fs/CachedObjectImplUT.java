package com.schoste.ddd.util.fs;

import java.util.ArrayList;

import com.schoste.ddd.util.CachedObject;
import com.schoste.ddd.util.CachedObjectUT;

public class CachedObjectImplUT extends CachedObjectUT
{
    @Override
    protected CachedObject<Integer> createCachedObj(Integer objToCache) throws Exception
    {
        return new CachedObjectImpl<Integer>(objToCache);
    }

    @Override
    protected CachedObject<String> createCachedObj(String objToCache) throws Exception
    {
        return new CachedObjectImpl<String>(objToCache);
    }

    @Override
    protected CachedObject<ArrayList<String>> createCachedObj(ArrayList<String> objToCache) throws Exception
    {
        return new CachedObjectImpl<ArrayList<String>>(objToCache);
    }
}
