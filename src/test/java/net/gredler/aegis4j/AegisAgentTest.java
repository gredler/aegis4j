/* Copyright (c) 2021, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import static net.gredler.aegis4j.AegisAgent.toBlockList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link AegisAgent}.
 */
public class AegisAgentTest {

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
}
