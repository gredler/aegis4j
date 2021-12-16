/* Copyright (c) 2021, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import static net.gredler.aegis4j.AegisAgent.toBlockList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.StubNotFoundException;
import java.rmi.activation.ActivationGroup;
import java.rmi.registry.LocateRegistry;
import java.util.List;
import java.util.Set;

import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.NoInitialContextException;
import javax.naming.ldap.LdapName;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.spi.HttpServerProvider;

/**
 * Tests {@link AegisAgent}.
 */
public class AegisAgentTest {

    @BeforeAll
    public static void installAgent() throws Exception {
        TestUtils.installAgent();
    }

    @Test
    public void testParseBlockList() throws Exception {
        assertEquals(Set.of("jndi", "rmi", "process", "httpserver", "serialization"), toBlockList(""));
        assertEquals(Set.of("jndi", "rmi", "process", "httpserver", "serialization"), toBlockList("   "));
        assertEquals(Set.of("jndi", "rmi", "process", "httpserver", "serialization"), toBlockList("blahblah"));
        assertEquals(Set.of("jndi", "rmi", "process", "httpserver", "serialization"), toBlockList("foo=bar"));
        assertEquals(Set.of("jndi", "rmi", "process", "httpserver", "serialization"), toBlockList("unblock=incorrect"));
        assertEquals(Set.of("jndi", "rmi", "process", "httpserver"), toBlockList("unblock=serialization"));
        assertEquals(Set.of("jndi", "rmi", "httpserver"), toBlockList("unblock=serialization,process"));
        assertEquals(Set.of("jndi", "rmi", "httpserver"), toBlockList("UNbloCk=SERIALIZATION,Process"));
        assertEquals(Set.of("jndi", "rmi", "httpserver"), toBlockList(" unblock\t=    serialization      , process\t"));
        assertEquals(Set.of("jndi", "rmi", "httpserver"), toBlockList("unblock=serialization,process,incorrect1,incorrect2"));
        assertEquals(Set.of(), toBlockList("unblock=jndi,rmi,process,httpserver,serialization"));
        assertEquals(Set.of("jndi"), toBlockList("block=jndi"));
        assertEquals(Set.of("jndi", "rmi", "process"), toBlockList("block=jndi,rmi,process"));
        assertEquals(Set.of("jndi", "rmi", "process"), toBlockList("block = jndi\t, rmi ,\nprocess"));
        assertEquals(Set.of("jndi", "rmi", "process"), toBlockList("BLOck = JNDI\t, rmi ,\nProcESs"));
    }

    @Test
    public void testJndi() throws Exception {

        String string = "foo";
        Name name = new LdapName("cn=foo");
        Object object = new Object();
        InitialContext initialContext = new InitialContext();

        assertThrowsNICE(() -> initialContext.lookup(string));
        assertThrowsNICE(() -> initialContext.lookup(name));
        assertThrowsNICE(() -> initialContext.bind(string, object));
        assertThrowsNICE(() -> initialContext.bind(name, object));
        assertThrowsNICE(() -> initialContext.rebind(string, object));
        assertThrowsNICE(() -> initialContext.rebind(name, object));
        assertThrowsNICE(() -> initialContext.unbind(string));
        assertThrowsNICE(() -> initialContext.unbind(name));
        assertThrowsNICE(() -> initialContext.rename(string, string));
        assertThrowsNICE(() -> initialContext.rename(name, name));
        assertThrowsNICE(() -> initialContext.list(string));
        assertThrowsNICE(() -> initialContext.list(name));
        assertThrowsNICE(() -> initialContext.listBindings(string));
        assertThrowsNICE(() -> initialContext.listBindings(name));
        assertThrowsNICE(() -> initialContext.destroySubcontext(string));
        assertThrowsNICE(() -> initialContext.destroySubcontext(name));
        assertThrowsNICE(() -> initialContext.createSubcontext(string));
        assertThrowsNICE(() -> initialContext.createSubcontext(name));
        assertThrowsNICE(() -> initialContext.lookupLink(string));
        assertThrowsNICE(() -> initialContext.lookupLink(name));
        assertThrowsNICE(() -> initialContext.getNameParser(string));
        assertThrowsNICE(() -> initialContext.getNameParser(name));
        assertThrowsNICE(() -> initialContext.addToEnvironment(string, object));
        assertThrowsNICE(() -> initialContext.removeFromEnvironment(string));
        assertThrowsNICE(() -> initialContext.getEnvironment());
        assertThrowsNICE(() -> initialContext.getNameInNamespace());
    }

    @Test
    public void testRmi() throws Exception {
        assertThrowsSNFE(() -> LocateRegistry.getRegistry(9090));
        assertThrowsSNFE(() -> LocateRegistry.getRegistry("foo"));
        assertThrowsSNFE(() -> LocateRegistry.getRegistry("foo", 9090));
        assertThrowsSNFE(() -> LocateRegistry.getRegistry("foo", 9090, null));
        assertThrowsSNFE(() -> LocateRegistry.createRegistry(9090));
        assertThrowsSNFE(() -> LocateRegistry.createRegistry(9090, null, null));
        assertThrowsSNFE(() -> ActivationGroup.getSystem());
    }

    @Test
    public void testProcess() throws Exception {

        Runtime runtime = Runtime.getRuntime();
        String string = "foo";
        String[] array = new String[] { "foo" };
        File file = new File(".");

        assertThrowsIOE(() -> runtime.exec(string));
        assertThrowsIOE(() -> runtime.exec(array));
        assertThrowsIOE(() -> runtime.exec(string, array));
        assertThrowsIOE(() -> runtime.exec(array, array));
        assertThrowsIOE(() -> runtime.exec(string, array, file));
        assertThrowsIOE(() -> runtime.exec(array, array, file));

        assertThrowsIOE(() -> new ProcessBuilder(string).start());
        assertThrowsIOE(() -> new ProcessBuilder(array).start());
        assertThrowsIOE(() -> new ProcessBuilder(List.of()).start());
        assertThrowsIOE(() -> ProcessBuilder.startPipeline(List.of()));
    }

    @Test
    public void testHttpServer() throws Exception {
        assertThrowsRE(() -> HttpServer.create(), "HTTP server provider lookup blocked by aegis4j");
        assertThrowsRE(() -> HttpServer.create(null, 0), "HTTP server provider lookup blocked by aegis4j");
        assertThrowsRE(() -> HttpServerProvider.provider(), "HTTP server provider lookup blocked by aegis4j");
    }

    @Test
    public void testSerialization() throws Exception {

        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        assertThrowsRE(() -> new ObjectInputStream(bais), "Java deserialization blocked by aegis4j");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertThrowsRE(() -> new ObjectOutputStream(baos), "Java serialization blocked by aegis4j");
    }

    private static void assertThrowsNICE(Task task) throws Exception {
        assertThrows(task, NoInitialContextException.class, "JNDI context creation blocked by aegis4j");
    }

    private static void assertThrowsSNFE(Task task) throws Exception {
        assertThrows(task, StubNotFoundException.class, "RMI registry creation blocked by aegis4j");
    }

    private static void assertThrowsIOE(Task task) throws Exception {
        assertThrows(task, IOException.class, "Process execution blocked by aegis4j");
    }

    private static void assertThrowsRE(Task task, String msg) throws Exception {
        assertThrows(task, RuntimeException.class, msg);
    }

    private static void assertThrows(Task task, Class< ? extends Exception > exceptionType, String msg) throws Exception {
        try {
            task.run();
            fail("Exception expected");
        } catch (Exception e) {
            Throwable root = getRootCause(e);
            assertInstanceOf(exceptionType, root);
            assertEquals(msg, root.getMessage());
        }
    }

    private static Throwable getRootCause(Exception e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}
