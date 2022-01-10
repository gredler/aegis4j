/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.function.Executable;

import com.sun.tools.attach.VirtualMachine;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPException;

/**
 * Base test class which makes it easy to set up an embedded LDAP server, trigger an LDAP-related
 * vulnerability, enable the Java agent, and then verify that the vulnerability is no longer exploitable.
 */
public final class TestUtils {

    public static final String OWNED = "owned";

    private TestUtils() {}

    public static void testLdap(Executable setup, Executable trigger, Class< ? > ldapPayload, boolean expectException) throws Throwable {

        setup.execute();
        InMemoryDirectoryServer ldapServer = createLdapServer(8181, "dc=foo", ldapPayload);
        assertNull(System.getProperty(OWNED));

        trigger.execute();
        assertTrue(Boolean.valueOf(System.getProperty(OWNED)));

        System.clearProperty(OWNED);
        assertNull(System.getProperty(OWNED));

        installAgent(null);

        try {
            trigger.execute();
            assertFalse(expectException);
            assertNull(System.getProperty(OWNED));
        } catch (Exception e) {
            assertTrue(expectException);
            assertNull(System.getProperty(OWNED));
            assertTrue(e.getMessage().contains("JNDI context creation blocked by aegis4j"));
        }

        ldapServer.shutDown(true);
    }

    // https://docs.oracle.com/javase/jndi/tutorial/objects/representation/ldap.html
    // https://www.blackhat.com/docs/us-16/materials/us-16-Munoz-A-Journey-From-JNDI-LDAP-Manipulation-To-RCE.pdf
    private static InMemoryDirectoryServer createLdapServer(int port, String partitionSuffix, Class< ? > payload)
        throws IOException, LDAPException, InstantiationException, ReflectiveOperationException {

        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(partitionSuffix);
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("LDAP", port));
        config.setSchema(null);

        Entry entry = new Entry(new DN(partitionSuffix));
        entry.addAttribute("objectClass", "javaNamingReference");
        entry.addAttribute("javaClassName", "Test");
        entry.addAttribute("javaCodeBase", "http://localhost/");
        entry.addAttribute("javaSerializedData", toBytes(payload.getDeclaredConstructor().newInstance()));

        InMemoryDirectoryServer directoryServer = new InMemoryDirectoryServer(config);
        directoryServer.add(entry);
        directoryServer.startListening();
        return directoryServer;
    }

    private static String createAgentJar(Class< ? > clazz) throws IOException {
        Path jar = Files.createTempFile("aegis4j-", ".jar");
        jar.toFile().deleteOnExit();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Agent-Class", clazz.getName());
        manifest.getMainAttributes().putValue("Premain-Class", clazz.getName());
        manifest.getMainAttributes().putValue("Can-Redefine-Classes", "true");
        manifest.getMainAttributes().putValue("Can-Retransform-Classes", "true");
        manifest.getMainAttributes().putValue("Can-Set-Native-Method-Prefix", "false");
        try (OutputStream os = Files.newOutputStream(jar); JarOutputStream jos = new JarOutputStream(os, manifest)) {
            JarEntry entry = new JarEntry(clazz.getName().replace('.', '/') + ".class");
            entry.setTime(System.currentTimeMillis());
            jos.putNextEntry(entry);
            jos.write(toBytes(clazz));
            jos.closeEntry();
        }
        return jar.toAbsolutePath().toString();
    }

    private static byte[] toBytes(Class< ? > clazz) throws IOException {
        String path = clazz.getName().replace('.', '/') + ".class";
        InputStream stream = clazz.getClassLoader().getResourceAsStream(path);
        return stream.readAllBytes();
    }

    public static byte[] toBytes(Object object) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(object);
            out.flush();
            return bos.toByteArray();
        }
    }

    /**
     * Requires {@code -Djdk.attach.allowAttachSelf=true} on the command line.
     */
    public static void installAgent(String options) throws Exception {
        long pid = ProcessHandle.current().pid();
        VirtualMachine jvm = VirtualMachine.attach(String.valueOf(pid));
        jvm.loadAgent(createAgentJar(AegisAgent.class), options);
        jvm.detach();
    }
}
