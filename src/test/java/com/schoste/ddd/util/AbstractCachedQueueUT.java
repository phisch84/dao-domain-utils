package com.schoste.ddd.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests how {@link AbstractCachedQueue} handles storage errors, and elements which another thread removes while they
 * are being loaded or saved. The elements are wrapped in {@link FailingCachedObject}s (see {@link TestQueue}); a
 * {@link FailingCachedObject#beforeNextAccess} hook simulates the other thread. The behaviour without errors is tested
 * by the contract test {@link CachedQueueUT}. The test queues are not closed, because they keep their elements in
 * memory only.
 */
@SuppressWarnings("resource")
public class AbstractCachedQueueUT
{
    /**
     * A queue which wraps every element in a {@link FailingCachedObject} and remembers all of them.
     */
    static class TestQueue extends AbstractCachedQueue<String>
    {
        private static final long serialVersionUID = 1L;

        /**
         * If not null, {@link #createCachedObject(String)} throws it.
         */
        Exception failOnCreate;

        /**
         * Passed to {@link FailingCachedObject#failOnCommit} of every new cached object.
         */
        Exception failOnCommit;

        /**
         * Passed to {@link FailingCachedObject#failOnClose} of every new cached object.
         */
        IOException failOnClose;

        /**
         * Every cached object this queue created, in order.
         */
        final List<FailingCachedObject<String>> created = new ArrayList<>();

        @Override
        protected CachedObject<String> createCachedObject(String element) throws Exception
        {
            if (this.failOnCreate != null) throw this.failOnCreate;

            FailingCachedObject<String> cachedElement = new FailingCachedObject<>(element);

            cachedElement.failOnCommit = this.failOnCommit;
            cachedElement.failOnClose = this.failOnClose;
            this.created.add(cachedElement);

            return cachedElement;
        }

        /**
         * Creates a queue with the elements, in their order.
         *
         * @param elements the elements
         * @return the queue
         */
        static TestQueue of(String... elements)
        {
            TestQueue queue = new TestQueue();

            for (String element : elements) queue.offer(element);

            return queue;
        }
    }

    /**
     * Asserts that offer saves the new element once (after wrapping it).
     */
    @Test
    public void testOfferSavesNewElement()
    {
        TestQueue queue = TestQueue.of("a");

        Assert.assertEquals(1, queue.created.get(0).commitCalls);
    }

    /**
     * Asserts that if the element can't be wrapped, offer throws {@link IllegalStateException} with the original
     * exception as cause and leaves the queue unchanged.
     */
    @Test
    public void testOfferWhenCreatingFails()
    {
        TestQueue queue = TestQueue.of("a");
        IOException failure = new IOException("injected");

        queue.failOnCreate = failure;

        Assert.assertSame(failure, Assert.assertThrows(IllegalStateException.class, () -> queue.offer("b")).getCause());
        Assert.assertEquals(Arrays.asList("a"), new ArrayList<>(queue));
    }

    /**
     * Asserts that if saving the new element fails, offer closes it (frees its storage), throws
     * {@link IllegalStateException} with the original exception as cause, and leaves the queue unchanged.
     */
    @Test
    public void testOfferWhenCommitFailsClosesNewElement()
    {
        TestQueue queue = TestQueue.of("a");
        IOException failure = new IOException("injected");

        queue.failOnCommit = failure;

        Assert.assertSame(failure, Assert.assertThrows(IllegalStateException.class, () -> queue.offer("b")).getCause());
        Assert.assertTrue("the new element was not closed", queue.created.get(1).closed);
        Assert.assertEquals(Arrays.asList("a"), new ArrayList<>(queue));
    }

    /**
     * Asserts that if saving the new element fails and closing it fails too, the failure of closing is added to the
     * failure of saving as suppressed exception.
     */
    @Test
    public void testOfferWhenCommitAndCloseFailAddsSuppressed()
    {
        TestQueue queue = new TestQueue();
        IOException commitFailure = new IOException("injected on commit");
        IOException closeFailure = new IOException("injected on close");

        queue.failOnCommit = commitFailure;
        queue.failOnClose = closeFailure;

        Assert.assertSame(commitFailure, Assert.assertThrows(IllegalStateException.class, () -> queue.offer("a")).getCause());
        Assert.assertArrayEquals(new Throwable[] { closeFailure }, commitFailure.getSuppressed());
        Assert.assertTrue(queue.isEmpty());
    }

    /**
     * Asserts that if the head can't be loaded, poll still removes and closes it, and throws a checked failure as
     * {@link IllegalStateException} with it as cause, and an unchecked failure as it is.
     */
    @Test
    public void testPollWhenLoadingFailsStillRemovesAndCloses()
    {
        TestQueue queue = TestQueue.of("a", "b");
        IOException checkedFailure = new IOException("injected");
        IllegalArgumentException uncheckedFailure = new IllegalArgumentException("injected");

        queue.created.get(0).failOnGet = checkedFailure;
        queue.created.get(1).failOnGet = uncheckedFailure;

        Assert.assertSame(checkedFailure, Assert.assertThrows(IllegalStateException.class, queue::poll).getCause());
        Assert.assertSame(uncheckedFailure, Assert.assertThrows(IllegalArgumentException.class, queue::poll));
        Assert.assertTrue(queue.created.get(0).closed);
        Assert.assertTrue(queue.created.get(1).closed);
        Assert.assertTrue(queue.isEmpty());
    }

    /**
     * Asserts that if the head can't be loaded because of a storage error, peek throws it (as
     * {@link IllegalStateException}) and the head stays in the queue.
     */
    @Test
    public void testPeekWhenLoadingFails()
    {
        TestQueue queue = TestQueue.of("a");
        IOException failure = new IOException("injected");

        queue.created.get(0).failOnGet = failure;

        Assert.assertSame(failure, Assert.assertThrows(IllegalStateException.class, queue::peek).getCause());
        Assert.assertEquals(1, queue.size());
        Assert.assertFalse(queue.created.get(0).closed);
    }

    /**
     * Asserts that if another thread takes the head just while peek loads it, peek returns the new head.
     */
    @Test
    public void testPeekRetriesIfTheHeadIsTakenMeanwhile()
    {
        TestQueue queue = TestQueue.of("a", "b");

        queue.created.get(0).beforeNextAccess = queue::poll;

        Assert.assertEquals("b", queue.peek());
        Assert.assertEquals(1, queue.size());
    }

    /**
     * Asserts that an iterator skips an element which another thread removes just while the iterator loads it.
     */
    @Test
    public void testIteratorSkipsElementRemovedWhileLoading()
    {
        TestQueue queue = TestQueue.of("a", "b", "c");

        queue.created.get(0).beforeNextAccess = queue::poll;

        List<String> iterated = new ArrayList<>();

        for (String element : queue) iterated.add(element);

        Assert.assertEquals(Arrays.asList("b", "c"), iterated);
    }

    /**
     * Asserts that an iterator throws a storage error of an element which is still in the queue
     * (as {@link IllegalStateException} with it as cause) instead of skipping the element.
     */
    @Test
    public void testIteratorThrowsStorageError()
    {
        TestQueue queue = TestQueue.of("a", "b");
        IOException failure = new IOException("injected");

        queue.created.get(1).failOnGet = failure;

        Iterator<String> elements = queue.iterator();

        Assert.assertEquals("a", elements.next());
        Assert.assertSame(failure, Assert.assertThrows(IllegalStateException.class, elements::hasNext).getCause());
    }

    /**
     * Asserts that the iterator's remove() does nothing if another thread removed the element in the meantime:
     * it doesn't close it a second time and doesn't remove another element.
     */
    @Test
    public void testIteratorRemoveOfElementRemovedMeanwhile()
    {
        TestQueue queue = TestQueue.of("a", "b");
        Iterator<String> elements = queue.iterator();

        Assert.assertEquals("a", elements.next());
        Assert.assertEquals("a", queue.poll());

        elements.remove();

        Assert.assertEquals(1, queue.created.get(0).closeCalls);
        Assert.assertEquals(Arrays.asList("b"), new ArrayList<>(queue));
        Assert.assertThrows(IllegalStateException.class, elements::remove);
    }

    /**
     * Asserts that commit tries to save every element although saving some fails, and throws the first failure with
     * the other ones as suppressed exceptions.
     */
    @Test
    public void testCommitTriesAllElementsAndThrowsFirstFailure()
    {
        TestQueue queue = TestQueue.of("a", "b", "c");
        IOException failureA = new IOException("injected a");
        IOException failureB = new IOException("injected b");

        queue.created.get(0).failOnCommit = failureA;
        queue.created.get(1).failOnCommit = failureB;

        IOException thrown = Assert.assertThrows(IOException.class, queue::commit);

        Assert.assertSame(failureA, thrown);
        Assert.assertArrayEquals(new Throwable[] { failureB }, thrown.getSuppressed());

        // one commit by offer, one by commit()
        for (FailingCachedObject<String> cachedElement : queue.created) Assert.assertEquals(2, cachedElement.commitCalls);
    }

    /**
     * Asserts that commit ignores an element which another thread removes (and closes) just while it is being saved,
     * but throws the failure of an element which is closed although it is still in the queue.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testCommitIgnoresElementRemovedMeanwhile() throws Exception
    {
        TestQueue queue = TestQueue.of("a", "b");

        queue.created.get(0).beforeNextAccess = queue::poll;
        queue.commit();

        Assert.assertEquals(2, queue.created.get(1).commitCalls);

        IllegalStateException failureB = new IllegalStateException("injected b");
        IllegalStateException failureC = new IllegalStateException("injected c");

        queue.offer("c");
        queue.created.get(1).failOnCommit = failureB;
        queue.created.get(2).failOnCommit = failureC;

        IllegalStateException thrown = Assert.assertThrows(IllegalStateException.class, queue::commit);

        Assert.assertSame(failureB, thrown);
        Assert.assertArrayEquals(new Throwable[] { failureC }, thrown.getSuppressed());
    }

    /**
     * Asserts that close tries to close every element although closing some fails, empties the queue,
     * and throws the first failure with the other ones as suppressed exceptions.
     */
    @Test
    public void testCloseTriesAllElementsAndThrowsFirstFailure()
    {
        TestQueue queue = TestQueue.of("a", "b", "c");
        IOException failureA = new IOException("injected a");
        IOException failureB = new IOException("injected b");

        queue.created.get(0).failOnClose = failureA;
        queue.created.get(1).failOnClose = failureB;

        IOException thrown = Assert.assertThrows(IOException.class, queue::close);

        Assert.assertSame(failureA, thrown);
        Assert.assertArrayEquals(new Throwable[] { failureB }, thrown.getSuppressed());
        Assert.assertTrue(queue.created.get(2).closed);
        Assert.assertTrue(queue.isEmpty());
    }

    /**
     * Asserts that clear empties the queue although closing an element fails, and throws the failure as
     * {@link IllegalStateException} with it as cause.
     */
    @Test
    public void testClearWhenClosingFails()
    {
        TestQueue queue = TestQueue.of("a");
        IOException failure = new IOException("injected");

        queue.created.get(0).failOnClose = failure;

        Assert.assertSame(failure, Assert.assertThrows(IllegalStateException.class, queue::clear).getCause());
        Assert.assertTrue(queue.isEmpty());
    }

    /**
     * Asserts that if cloning an element fails, clone closes the elements it already cloned, throws
     * {@link CloneNotSupportedException} with the original exception as cause, and leaves the original unchanged.
     */
    @Test
    public void testCloneWhenCloningAnElementFailsClosesClonedElements()
    {
        TestQueue queue = TestQueue.of("a", "b");
        CloneNotSupportedException failure = new CloneNotSupportedException("injected");

        queue.created.get(1).failOnClone = failure;

        Assert.assertSame(failure, Assert.assertThrows(CloneNotSupportedException.class, queue::clone).getCause());
        Assert.assertEquals(1, queue.created.get(0).clones.size());
        Assert.assertTrue("the cloned element was not closed", queue.created.get(0).clones.get(0).closed);
        Assert.assertFalse(queue.created.get(0).closed);
        Assert.assertEquals(Arrays.asList("a", "b"), new ArrayList<>(queue));
    }

    /**
     * Asserts that if cloning an element fails and closing the elements already cloned fails too, the failure of
     * closing is added to the failure of cloning (the cause) as suppressed exception.
     */
    @Test
    public void testCloneWhenCloningAndClosingFailAddsSuppressed()
    {
        TestQueue queue = TestQueue.of("a", "b");
        CloneNotSupportedException cloneFailure = new CloneNotSupportedException("injected on clone");
        IOException closeFailure = new IOException("injected on close");

        queue.created.get(0).failOnCloseOfClones = closeFailure;
        queue.created.get(1).failOnClone = cloneFailure;

        Assert.assertSame(cloneFailure, Assert.assertThrows(CloneNotSupportedException.class, queue::clone).getCause());
        Assert.assertArrayEquals(new Throwable[] { closeFailure }, cloneFailure.getSuppressed());
    }

    /**
     * Asserts that clone skips an element which another thread removes (and closes) just while it is being cloned,
     * like an iterator does.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testCloneSkipsElementRemovedMeanwhile() throws Exception
    {
        TestQueue queue = TestQueue.of("a", "b");

        queue.created.get(0).beforeNextAccess = queue::poll;

        AbstractCachedQueue<?> clone = (AbstractCachedQueue<?>) queue.clone();

        Assert.assertEquals(Arrays.asList("b"), new ArrayList<>(clone));
        Assert.assertEquals(Arrays.asList("b"), new ArrayList<>(queue));
    }

    /**
     * Asserts that every method releases the lock before it returns, also on the paths for elements removed by another
     * thread meanwhile. A method which keeps the lock would block every other thread forever; checking
     * {@link java.util.concurrent.locks.ReentrantLock#isLocked()} after each call finds that without threads or timing.
     *
     * @throws Exception re-throws every exception
     */
    @Test
    public void testEveryMethodReleasesTheLock() throws Exception
    {
        TestQueue queue = TestQueue.of("a", "b", "c", "d");
        List<String> notReleased = new ArrayList<>();
        String[] names =
        {
            "offer", "size", "peek", "poll", "iterator", "iterator.remove", "commit", "clone", "serialization",
            "peek of a head taken meanwhile", "clear",
        };
        Runnable[] calls =
        {
            () -> queue.offer("e"),
            queue::size,
            queue::peek,
            queue::poll,
            () -> queue.iterator().next(),
            () -> { Iterator<String> elements = queue.iterator(); elements.next(); elements.remove(); },
            () -> { try { queue.commit(); } catch (Exception e) { throw new IllegalStateException(e); } },
            () -> { try { queue.clone(); } catch (CloneNotSupportedException e) { throw new IllegalStateException(e); } },
            () -> { try { TestHelpers.serializeAndDeserialize(queue); } catch (Exception e) { throw new IllegalStateException(e); } },
            () ->
            {
                // the head is taken while peek loads it, so peek checks (with the lock) whether it's still in the queue
                ((FailingCachedObject<String>) queue.cachedElements.peekFirst()).beforeNextAccess = queue::poll;
                queue.peek();
            },
            queue::clear,
        };

        for (int i = 0; i < calls.length; i++)
        {
            calls[i].run();

            if (queue.lock.isLocked()) notReleased.add(names[i]);
        }

        Assert.assertEquals("methods which keep the lock", new ArrayList<String>(), notReleased);
    }
}
