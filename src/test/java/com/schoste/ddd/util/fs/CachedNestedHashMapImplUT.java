package com.schoste.ddd.util.fs;

import com.schoste.ddd.util.NestedMap;
import com.schoste.ddd.util.NestedMapUT;

/**
 * Runs the contract tests of {@link com.schoste.ddd.util.NestedMapUT}
 * for {@link com.schoste.ddd.util.fs.CachedNestedHashMapImpl}.
 */
public class CachedNestedHashMapImplUT extends NestedMapUT
{
    @Override
    protected NestedMap<String, String> createNestedMap() throws Exception
    {
        return new CachedNestedHashMapImpl<String, String>();
    }
}
