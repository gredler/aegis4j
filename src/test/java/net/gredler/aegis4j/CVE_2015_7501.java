/* Copyright (c) 2021, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import static net.gredler.aegis4j.TestUtils.OWNED;
import static net.gredler.aegis4j.TestUtils.installAgent;
import static net.gredler.aegis4j.TestUtils.toBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.PriorityQueue;

import org.apache.commons.collections4.FunctorException;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.collections4.comparators.TransformingComparator;
import org.apache.commons.collections4.functors.ChainedTransformer;
import org.apache.commons.collections4.functors.ConstantTransformer;
import org.apache.commons.collections4.functors.InvokerTransformer;
import org.junit.jupiter.api.Test;

/**
 * Tests mitigation of CVE-2015-7501, both at the process execution level and at the serialization level.
 * We test using a {@link PriorityQueue} with a custom {@link Comparator}. The comparator is actually a
 * {@link ChainedTransformer} which uses reflection to invoke {@link Runtime#exec(String)}. The comparator
 * magic is invoked when the queue needs to be sorted, which happens when items are added to the queue
 * and also as a last step when the queue is deserialized.
 *
 * @see <a href="https://nvd.nist.gov/vuln/detail/CVE-2015-7501"></a>
 * @see <a href="https://github.com/frohoff/ysoserial/blob/master/src/main/java/ysoserial/payloads/CommonsCollections6.java">Exploit POC</a>
 */
public class CVE_2015_7501 {

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void test() throws Exception {

        Path temp = Files.createTempFile("aegis4j-", ".tmp");
        temp.toFile().deleteOnExit();
        String path = temp.toAbsolutePath().toString();

        boolean windows = System.getProperty("os.name").toLowerCase().contains("windows");
        String cmd = windows ? "cmd.exe /c echo " + OWNED + ">" + path
                             : "echo " + OWNED + " > " + path;

        Transformer transformerChain = new ChainedTransformer(new Transformer[] {
            new ConstantTransformer(Runtime.class),
            new InvokerTransformer("getMethod", new Class[] { String.class, Class[].class }, new Object[] { "getRuntime", new Class[0] }),
            new InvokerTransformer("invoke", new Class[] { Object.class, Object[].class }, new Object[] { null, new Object[0] }),
            new InvokerTransformer("exec", new Class[] { String.class }, new Object[] { cmd }),
            new ConstantTransformer(1)
        });

        // trigger directly, verify owned
        PriorityQueue queue = new PriorityQueue(2, new TransformingComparator(transformerChain));
        queue.add(1);
        queue.add(1);
        Thread.sleep(500); // wait for file changes to sync
        assertEquals(OWNED + "\r\n", Files.readString(temp), path);

        // reset
        Files.write(temp, new byte[0]);
        assertEquals("", Files.readString(temp), path);

        // trigger via deserialization, verify owned again
        byte[] serialized = toBytes(queue);
        new ObjectInputStream(new ByteArrayInputStream(serialized)).readObject();
        Thread.sleep(500); // wait for file changes to sync
        assertEquals(OWNED + "\r\n", Files.readString(temp), path);

        // reset
        Files.write(temp, new byte[0]);
        assertEquals("", Files.readString(temp), path);

        // install aegis4j agent
        installAgent();

        // trigger again directly, verify not owned
        try {
            PriorityQueue queue2 = new PriorityQueue(2, new TransformingComparator(transformerChain));
            queue2.add(1);
            queue2.add(1);
            fail("Exception expected");
        } catch (FunctorException e) {
            Thread.sleep(500); // wait for file changes to sync
            assertEquals("", Files.readString(temp), path);
            assertEquals("Process execution blocked by aegis4j", e.getCause().getCause().getMessage());
        }

        // trigger again via deserialization, verify not owned
        try {
            new ObjectInputStream(new ByteArrayInputStream(serialized)).readObject();
            fail("Exception expected");
        } catch (RuntimeException e) {
            Thread.sleep(1_000); // wait for file changes to sync
            assertEquals("", Files.readString(temp), path);
            assertEquals("Java deserialization blocked by aegis4j", e.getMessage());
        }
    }
}
