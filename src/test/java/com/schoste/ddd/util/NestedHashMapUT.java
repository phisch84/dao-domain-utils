package com.schoste.ddd.util;

import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;

/**
 * Runs the contract tests of {@link com.schoste.ddd.util.NestedMapUT} for {@link com.schoste.ddd.util.NestedHashMap},
 * and adds the tests which are specific to it.
 */
public class NestedHashMapUT extends NestedMapUT
{
    @Override
    protected NestedMap<String, String> createNestedMap() throws Exception
    {
        return new NestedHashMap<String, String>();
    }

    /**
     * Asserts that a clone shares the value instances with the original map, like {@link java.util.HashMap#clone()}.
     *
     * @throws Exception re-throws every exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCloneSharesValueInstances() throws Exception
    {
        NestedHashMap<String, ArrayList<String>> map = new NestedHashMap<>();
        ArrayList<String> value = new ArrayList<>();

        map.put("a", "b", value);

        NestedHashMap<String, ArrayList<String>> clone = (NestedHashMap<String, ArrayList<String>>) map.clone();

        Assert.assertSame(value, clone.get("a", "b"));
    }
}
