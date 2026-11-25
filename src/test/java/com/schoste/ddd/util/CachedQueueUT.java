package com.schoste.ddd.util;

import static com.schoste.ddd.util.TestHelpers.list;
import static com.schoste.ddd.util.TestHelpers.serializeAndDeserialize;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * Contract test for every implementation of {@link CachedQueue}: a subclass per implementation creates the queues
 * ({@link #createCachedQueue()}) and adds its implementation-specific tests.
 * Tests which need the internals of {@link AbstractCachedQueue} (e.g. to simulate garbage collection) are skipped for
 * implementations which don't derive from it.
 */
abstract public class CachedQueueUT
{
    /**
     * Creates a new, empty queue of the implementation under test.
     *
     * @param <E> the class of the elements
     * @return the new queue
     * @throws Exception if the queue can't be created
     */
    abstract protected <E extends Serializable> CachedQueue<E> createCachedQueue() throws Exception;

    /**
     * Returns the {@link CachedObject}s which hold the elements, in the queue's order.
     * Only applies to implementations which derive from {@link AbstractCachedQueue}; for others, the calling test is skipped.
     *
     * @param queue the queue
     * @return a copy of the list of the cached elements
     */
    protected static List<CachedObject<?>> cachedElementsOf(CachedQueue<?> queue)
    {
        Assume.assumeTrue(queue instanceof AbstractCachedQueue);

        AbstractCachedQueue<?> abstractQueue = (AbstractCachedQueue<?>) queue;

        abstractQueue.lock.lock();
        try
        {
            return new ArrayList<CachedObject<?>>(abstractQueue.cachedElements);
        }
        finally
        {
            abstractQueue.lock.unlock();
        }
    }

    /**
     * Simulates that the GC collected all elements of the queue which are not referenced elsewhere,
     * by clearing the weak references of their {@link AbstractCachedObject}s.
     * For implementations which don't use them, the calling test is skipped.
     *
     * @param queue the queue whose elements are "collected"
     */
    protected static void simulateGC(CachedQueue<?> queue)
    {
        for (CachedObject<?> cachedElement : cachedElementsOf(queue))
        {
            Assume.assumeTrue(cachedElement instanceof AbstractCachedObject);

            ((AbstractCachedObject<?>) cachedElement).objRef.clear();
        }
    }

    /**
     * Checks whether an element is currently in memory (loaded) or only in the storage.
     *
     * @param cachedElement the cached element, from {@link #cachedElementsOf(CachedQueue)}
     * @return true if the element is in memory
     */
    protected static boolean isLoaded(CachedObject<?> cachedElement)
    {
        Assume.assumeTrue(cachedElement instanceof AbstractCachedObject);

        return ((AbstractCachedObject<?>) cachedElement).objRef.get() != null;
    }

    /**
     * Asserts that offer/poll work first in, first out, and that poll returns null for an empty queue.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testOfferAndPollAreFifo() throws Exception
    {
        try (CachedQueue<String> queue = this.createCachedQueue())
        {
            Assert.assertTrue(queue.offer("a"));
            Assert.assertTrue(queue.offer("b"));
            Assert.assertTrue(queue.add("c"));

            Assert.assertEquals("a", queue.poll());
            Assert.assertEquals("b", queue.poll());
            Assert.assertEquals("c", queue.poll());
            Assert.assertNull(queue.poll());
        }
    }

    /**
     * Asserts that peek returns the head without removing it, and null for an empty queue.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testPeekDoesntRemove() throws Exception
    {
        try (CachedQueue<String> queue = this.createCachedQueue())
        {
            Assert.assertNull(queue.peek());

            queue.offer("a");
            queue.offer("b");

            Assert.assertEquals("a", queue.peek());
            Assert.assertEquals("a", queue.element());
            Assert.assertEquals(2, queue.size());
        }
    }

    /**
     * Asserts that remove() and element() throw {@link NoSuchElementException} for an empty queue,
     * and that remove() takes the head.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testRemoveAndElementOnEmptyQueue() throws Exception
    {
        try (CachedQueue<String> queue = this.createCachedQueue())
        {
            Assert.assertThrows(NoSuchElementException.class, queue::remove);
            Assert.assertThrows(NoSuchElementException.class, queue::element);

            queue.add("a");

            Assert.assertEquals("a", queue.remove());
            Assert.assertTrue(queue.isEmpty());
        }
    }

    /**
     * Asserts that null elements are rejected with {@link NullPointerException} and the queue stays empty.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testNullElementsAreRejected() throws Exception
    {
        try (CachedQueue<String> queue = this.createCachedQueue())
        {
            Assert.assertThrows(NullPointerException.class, () -> queue.offer(null));
            Assert.assertThrows(NullPointerException.class, () -> queue.add(null));
            Assert.assertTrue(queue.isEmpty());
        }
    }

    /**
     * Asserts that size() and isEmpty() follow adding and taking elements.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testSizeAndIsEmpty() throws Exception
    {
        try (CachedQueue<String> queue = this.createCachedQueue())
        {
            Assert.assertEquals(0, queue.size());
            Assert.assertTrue(queue.isEmpty());

            queue.offer("a");
            queue.offer("b");

            Assert.assertEquals(2, queue.size());
            Assert.assertFalse(queue.isEmpty());

            queue.poll();

            Assert.assertEquals(1, queue.size());
        }
    }

    /**
     * Asserts that once an element was garbage collected, peek and poll return an equal copy loaded from the storage.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testReturnsCopyAfterGC() throws Exception
    {
        try (CachedQueue<ArrayList<String>> queue = this.createCachedQueue())
        {
            ArrayList<String> element = list("1");

            queue.offer(element);
            simulateGC(queue);

            ArrayList<String> peeked = queue.peek();

            Assert.assertEquals(element, peeked);
            Assert.assertNotSame(element, peeked);
            Assert.assertEquals(element, queue.poll());
        }
    }

    /**
     * Asserts that commit() saves changes to the elements in memory, so they survive garbage collection.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testCommitSavesChanges() throws Exception
    {
        try (CachedQueue<ArrayList<String>> queue = this.createCachedQueue())
        {
            ArrayList<String> first = list("1");
            ArrayList<String> second = list("2");

            queue.offer(first);
            queue.offer(second);
            first.add("changed");
            second.add("changed");
            queue.commit();
            simulateGC(queue);

            Assert.assertEquals(list("1", "changed"), queue.poll());
            Assert.assertEquals(list("2", "changed"), queue.poll());
        }
    }

    /**
     * Asserts that changes which were not saved are lost once the element was garbage collected.
     * This also shows that {@link #simulateGC(CachedQueue)} works for the other tests.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testUnsavedChangesAreLostAfterGC() throws Exception
    {
        try (CachedQueue<ArrayList<String>> queue = this.createCachedQueue())
        {
            ArrayList<String> element = list("1");

            queue.offer(element);
            element.add("not saved");
            simulateGC(queue);

            Assert.assertEquals(list("1"), queue.peek());
        }
    }

    /**
     * Asserts that poll and remove() close the taken element, which frees its storage.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testPollAndRemoveCloseElement() throws Exception
    {
        try (CachedQueue<String> queue = this.createCachedQueue())
        {
            queue.offer("a");
            queue.offer("b");

            List<CachedObject<?>> cachedElements = cachedElementsOf(queue);

            queue.poll();
            queue.remove();

            CachedHashMapUT.assertClosed(cachedElements.get(0));
            CachedHashMapUT.assertClosed(cachedElements.get(1));
        }
    }

    /**
     * Asserts that remove(Object) removes the first equal element, closes it, and keeps the order of the others.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testRemoveObjectClosesElement() throws Exception
    {
        try (CachedQueue<String> queue = this.createCachedQueue())
        {
            queue.offer("a");
            queue.offer("b");
            queue.offer("c");

            CachedObject<?> removed = cachedElementsOf(queue).get(1);

            Assert.assertTrue(queue.remove("b"));
            Assert.assertFalse(queue.remove("x"));

            CachedHashMapUT.assertClosed(removed);
            Assert.assertEquals(Arrays.asList("a", "c"), new ArrayList<>(queue));
        }
    }

    /**
     * Asserts that removing through the iterator (also via removeIf) closes the removed elements.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testIteratorRemoveClosesElement() throws Exception
    {
        try (CachedQueue<String> queue = this.createCachedQueue())
        {
            queue.offer("a");
            queue.offer("b");
            queue.offer("c");

            List<CachedObject<?>> cachedElements = cachedElementsOf(queue);
            Iterator<String> elements = queue.iterator();

            Assert.assertEquals("a", elements.next());
            elements.remove();

            Assert.assertTrue(queue.removeIf("c"::equals));

            CachedHashMapUT.assertClosed(cachedElements.get(0));
            CachedHashMapUT.assertClosed(cachedElements.get(2));
            Assert.assertEquals(Arrays.asList("b"), new ArrayList<>(queue));
        }
    }

    /**
     * Asserts that clear() empties the queue and closes all elements.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testClearClosesAllElements() throws Exception
    {
        try (CachedQueue<String> queue = this.createCachedQueue())
        {
            queue.offer("a");
            queue.offer("b");

            List<CachedObject<?>> cachedElements = cachedElementsOf(queue);

            queue.clear();

            Assert.assertTrue(queue.isEmpty());

            for (CachedObject<?> cachedElement : cachedElements) CachedHashMapUT.assertClosed(cachedElement);
        }
    }

    /**
     * Asserts that the methods which need all elements see them in the queue's order:
     * iterating, toArray, contains and toString.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testMethodsWhichNeedAllElements() throws Exception
    {
        try (CachedQueue<String> queue = this.createCachedQueue())
        {
            queue.offer("a");
            queue.offer("b");
            queue.offer("c");
            simulateGC(queue);

            List<String> iterated = new ArrayList<>();

            for (String element : queue) iterated.add(element);

            Assert.assertEquals(Arrays.asList("a", "b", "c"), iterated);
            Assert.assertArrayEquals(new Object[] { "a", "b", "c" }, queue.toArray());
            Assert.assertTrue(queue.contains("b"));
            Assert.assertFalse(queue.contains("x"));
            Assert.assertEquals("[a, b, c]", queue.toString());
        }
    }

    /**
     * Asserts that an iterator is weakly consistent: it returns the elements which were in the queue when it was
     * created, skips elements removed in the meantime, doesn't return elements added in the meantime, and never
     * throws {@link java.util.ConcurrentModificationException}.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testIteratorIsWeaklyConsistent() throws Exception
    {
        try (CachedQueue<String> queue = this.createCachedQueue())
        {
            queue.offer("a");
            queue.offer("b");
            queue.offer("c");

            Iterator<String> elements = queue.iterator();

            queue.poll();
            queue.remove("c");
            queue.offer("d");

            List<String> iterated = new ArrayList<>();

            while (elements.hasNext()) iterated.add(elements.next());

            Assert.assertEquals(Arrays.asList("b"), iterated);
            Assert.assertThrows(NoSuchElementException.class, elements::next);
        }
    }

    /**
     * Asserts that size() and isEmpty() don't load any element from the storage.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testSizeDoesntLoadElements() throws Exception
    {
        try (CachedQueue<String> queue = this.createCachedQueue())
        {
            queue.offer("a");
            queue.offer("b");
            simulateGC(queue);

            Assert.assertEquals(2, queue.size());
            Assert.assertFalse(queue.isEmpty());

            for (CachedObject<?> cachedElement : cachedElementsOf(queue)) Assert.assertFalse(isLoaded(cachedElement));
        }
    }

    /**
     * Asserts that the queue keeps no references to the elements which iterating returned, so the GC can collect them.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testElementsAreNotRetained() throws Exception
    {
        try (CachedQueue<ArrayList<String>> queue = this.createCachedQueue())
        {
            for (int i = 0; i < 10; i++) queue.offer(list("element" + i));

            simulateGC(queue);

            List<WeakReference<ArrayList<String>>> returned = new ArrayList<>();

            for (ArrayList<String> element : queue) returned.add(new WeakReference<>(element));

            Assert.assertEquals(10, returned.size());

            for (int i = 0; (i < 100) && returned.stream().anyMatch(ref -> ref.get() != null); i++) System.gc();

            Assert.assertTrue("the queue kept references to its elements", returned.stream().allMatch(ref -> ref.get() == null));
        }
    }

    /**
     * Asserts that equals and hashCode are identity (like {@link java.util.ArrayDeque}):
     * two queues with the same elements are not equal.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testEqualsIsIdentity() throws Exception
    {
        try (CachedQueue<String> queue = this.createCachedQueue();
             CachedQueue<String> other = this.createCachedQueue())
        {
            queue.offer("a");
            other.offer("a");

            Assert.assertEquals(queue, queue);
            Assert.assertNotEquals(queue, other);
            Assert.assertEquals(System.identityHashCode(queue), queue.hashCode());
        }
    }

    /**
     * Asserts that close() closes all elements and empties the queue, which can be used again afterwards.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testCloseClosesAllElements() throws Exception
    {
        try (CachedQueue<String> queue = this.createCachedQueue())
        {
            queue.offer("a");
            queue.offer("b");

            List<CachedObject<?>> cachedElements = cachedElementsOf(queue);

            queue.close();

            Assert.assertTrue(queue.isEmpty());

            for (CachedObject<?> cachedElement : cachedElements) CachedHashMapUT.assertClosed(cachedElement);

            queue.offer("c");

            Assert.assertEquals("c", queue.poll());
        }
    }

    /**
     * Asserts that a deserialized queue has the elements in their order with the state they had at the last commit,
     * and that the original queue keeps its elements.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testSerializeAndDeserialize() throws Exception
    {
        try (CachedQueue<ArrayList<String>> queue = this.createCachedQueue())
        {
            ArrayList<String> first = list("1");

            queue.offer(first);
            queue.offer(list("2"));
            first.add("committed");
            queue.commit();

            CachedQueue<ArrayList<String>> copy = serializeAndDeserialize(queue);

            Assert.assertNotSame(queue, copy);
            Assert.assertEquals(2, copy.size());
            Assert.assertEquals(Arrays.asList(list("1", "committed"), list("2")), new ArrayList<>(copy));
            Assert.assertEquals(Arrays.asList(list("1", "committed"), list("2")), new ArrayList<>(queue));

            copy.offer(list("3"));

            Assert.assertEquals(3, copy.size());
            Assert.assertEquals(2, queue.size());
        }
    }

    /**
     * Asserts that a clone has the elements in their order, with their current state, and its own cached objects and
     * instances.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testClone() throws Exception
    {
        try (CachedQueue<ArrayList<String>> queue = this.createCachedQueue())
        {
            ArrayList<String> first = list("1");

            queue.offer(first);
            queue.offer(list("2"));
            first.add("not committed");

            try (CachedQueue<ArrayList<String>> clone = (CachedQueue<ArrayList<String>>) queue.clone())
            {
                Assert.assertNotSame(queue, clone);
                Assert.assertEquals(Arrays.asList(list("1", "not committed"), list("2")), new ArrayList<>(clone));
                Assert.assertNotSame(first, clone.peek());

                List<CachedObject<?>> original = cachedElementsOf(queue);
                List<CachedObject<?>> cloned = cachedElementsOf(clone);

                for (int i = 0; i < original.size(); i++) Assert.assertNotSame(original.get(i), cloned.get(i));
            }
        }
    }

    /**
     * Asserts that a clone is independent of the original: taking from or adding to one doesn't change the other,
     * and closing the clone doesn't close the original's elements.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testCloneIsIndependent() throws Exception
    {
        try (CachedQueue<String> queue = this.createCachedQueue())
        {
            queue.offer("a");
            queue.offer("b");

            CachedQueue<String> clone = (CachedQueue<String>) queue.clone();

            Assert.assertEquals("a", clone.poll());
            clone.offer("c");
            queue.offer("d");

            Assert.assertEquals(Arrays.asList("b", "c"), new ArrayList<>(clone));

            clone.close();

            Assert.assertEquals(Arrays.asList("a", "b", "d"), new ArrayList<>(queue));
        }
    }

    /**
     * Asserts that the queue is thread-safe: several producers offer and several consumers poll at the same time, while
     * another thread keeps iterating, peeking and asking for the size. Every element must be polled exactly once, no
     * thread may throw (iterators never throw {@link java.util.ConcurrentModificationException}), and the queue must be
     * empty at the end. The test doesn't depend on timing; it only checks the results.
     *
     * @throws Exception re-throws every exception, also of the threads
     */
    @Test
    public void testConcurrentOfferPollAndIteration() throws Exception
    {
        final int producers = 4;
        final int elementsPerProducer = 250;
        final int consumers = 4;

        try (CachedQueue<String> queue = this.createCachedQueue())
        {
            final CountDownLatch start = new CountDownLatch(1);
            final AtomicBoolean producersDone = new AtomicBoolean();
            final AtomicBoolean consumersDone = new AtomicBoolean();
            final ConcurrentHashMap<String, Integer> received = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newFixedThreadPool(producers + consumers + 1);

            try
            {
                List<Future<?>> producerResults = new ArrayList<>();
                List<Future<?>> consumerResults = new ArrayList<>();

                for (int p = 0; p < producers; p++)
                {
                    final int producer = p;

                    producerResults.add(executor.submit(() ->
                    {
                        start.await();

                        for (int i = 0; i < elementsPerProducer; i++) queue.offer(producer + "-" + i);

                        return null;
                    }));
                }
                for (int c = 0; c < consumers; c++)
                {
                    consumerResults.add(executor.submit(() ->
                    {
                        start.await();

                        while (!Thread.currentThread().isInterrupted())
                        {
                            String element = queue.poll();

                            if (element != null) received.merge(element, 1, Integer::sum);
                            else if (producersDone.get() && queue.isEmpty()) return null;
                        }
                        return null;
                    }));
                }

                Future<?> observer = executor.submit(() ->
                {
                    start.await();

                    while (!consumersDone.get() && !Thread.currentThread().isInterrupted())
                    {
                        for (String element : queue) Assert.assertNotNull(element);

                        queue.peek();
                        Assert.assertTrue(queue.size() >= 0);
                    }
                    return null;
                });

                start.countDown();

                for (Future<?> result : producerResults) result.get(60, TimeUnit.SECONDS);

                producersDone.set(true);

                for (Future<?> result : consumerResults) result.get(60, TimeUnit.SECONDS);

                consumersDone.set(true);
                observer.get(60, TimeUnit.SECONDS);
            }
            finally
            {
                // the threads stop when interrupted, so an exception of the try block isn't hidden by waiting here
                executor.shutdownNow();
                executor.awaitTermination(60, TimeUnit.SECONDS);
            }

            Assert.assertEquals(producers * elementsPerProducer, received.size());

            for (Integer count : received.values()) Assert.assertEquals(Integer.valueOf(1), count);

            Assert.assertTrue(queue.isEmpty());
        }
    }
}
