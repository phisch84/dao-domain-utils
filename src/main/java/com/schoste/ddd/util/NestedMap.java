package com.schoste.ddd.util;

/**
 * A nested map (a trie of maps): a sequence of keys is a path to a value, and a shorter sequence of keys (a prefix)
 * leads to the nested map of that level.
 *
 * @param <K> the class of the keys on every level
 * @param <V> the class of the values
 */
// TODO: methods are defined after the open design questions are answered (get/getSubMap, mixed levels, depth)
public interface NestedMap<K, V>
{

}
