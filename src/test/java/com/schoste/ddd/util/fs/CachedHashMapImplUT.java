package com.schoste.ddd.util.fs;

import java.util.ArrayList;

import com.schoste.ddd.util.CachedHashMap;
import com.schoste.ddd.util.CachedHashMapUT;

public class CachedHashMapImplUT extends CachedHashMapUT
{
    @Override
    protected CachedHashMap<String, ArrayList<String>> createCachedMap() throws Exception
    {
        return new CachedHashMapImpl<String, ArrayList<String>>();
    }
}
