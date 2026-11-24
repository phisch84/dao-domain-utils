package com.schoste.ddd.util;

/**
 * Runs the contract tests of {@link com.schoste.ddd.util.NestedMapUT} for {@link com.schoste.ddd.util.NestedHashMap}.
 */
public class NestedHashMapUT extends NestedMapUT
{
    @Override
    protected NestedMap<String, String> createNestedMap() throws Exception
    {
        return new NestedHashMap<String, String>();
    }
}
