# aegis4j

Avoid the NEXT Log4Shell vulnerability!

The Java platform has accrued a number of features over the years. Some of these features are no longer commonly used,
but their existence remains a security liability, providing attackers with a diverse toolkit to leverage against
Java-based systems.

It is possible to eliminate some of this attack surface area by creating custom JVM images with
[jlink](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jlink.html), but this is not always feasible or desired.
Another option is to use the [--limit-modules](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html) command
line parameter when running your application, but this is a relatively coarse tool that cannot be used to disable
individual features like serialization or native process execution.

A third option is aegis4j, a Java agent which patches key system classes to completely disable a number of standard
Java features, including JNDI, RMI, native process execution, Java serialization and the built-in JDK HTTP server.

### Download

The aegis4j JAR is available in the [Maven Central](https://repo1.maven.org/maven2/net/gredler/aegis4j/1.0/) repository.

### Usage: Attach at Application Startup

To attach at application startup, add the agent to your java command line:

`java -cp <classpath> -javaagent:aegis4j-1.0.jar <main-class> <arguments>`

### Usage: Attach to a Running Application

To attach to a running application, run the following command:

`java -jar aegis4j-1.0.jar <application-pid>`

The application process ID, or PID, can usually be determined by running the `jps` command.

### Configuration

You can optionally provide a list of features to block or unblock from the following list:

- `jndi`: block or unblock all JNDI functionality (`javax.naming.*`)
- `rmi`: block or unblock all RMI functionality (`java.rmi.*`)
- `process`: block or unblock all process execution functionality (`Runtime.exec()`, `ProcessBuilder`)
- `httpserver`: block or unblock all use of the JDK HTTP server (`com.sun.net.httpserver.*`)
- `serialization`: block or unblock all Java serialization (`ObjectInputStream`, `ObjectOutputStream`)

If no feature list is provided, all of the features above are blocked across the entire VM, for example:

- `java -cp "./*" -javaagent:aegis4j-1.0.jar com.foo.Main` (at startup)
- `java -jar aegis4j-1.0.jar 11979` (after startup)

If a `block` list is provided, only the features specified in the list are blocked. Any features omitted from the list are not blocked, for example:

- `java -cp "./*" -javaagent:aegis4j-1.0.jar=block=jndi,rmi,process com.foo.Main` (at startup)
- `java -jar aegis4j-1.0.jar 11979 block=jndi,rmi,process` (after startup)

If an `unblock` list is provided, all of the features listed above are blocked **except** for the features on the `unblock` list, for example:

- `java -cp "./*" -javaagent:aegis4j-1.0.jar=unblock=serialization com.foo.Main` (at startup)
- `java -jar aegis4j-1.0.jar 11979 unblock=serialization` (after startup)

### Compatibility

The aegis4j Java agent is compatible with JDK 11 and newer.

### Building

To build aegis4j, run `gradlew build`.

### Digging Deeper

Class modifications are performed using [Javassist](https://www.javassist.org/). The specific class modifications performed are
configured in the [mods.properties](src/main/resources/net/gredler/aegis4j/mods.properties) file. Some of the tests validate the
agent against actual vulnerabilities (e.g.
[CVE-2015-7501](src/test/java/net/gredler/aegis4j/CVE_2015_7501.java),
[CVE-2019-17531](src/test/java/net/gredler/aegis4j/CVE_2019_17531.java),
[CVE-2021-44228](src/test/java/net/gredler/aegis4j/CVE_2021_44228.java)).
The tests are run with the `jdk.attach.allowAttachSelf=true` system property, so that the agent can be attached and tested
locally. Tests are also run in individual VM instances, so that the class modifications performed in one test do not affect other
tests.

### Related Work

[log4j-jndi-be-gone](https://github.com/nccgroup/log4j-jndi-be-gone): A Java agent which patches the Log4Shell vulnerability (CVE-2021-44228)

[Log4jHotPatch](https://github.com/corretto/hotpatch-for-apache-log4j2/): A similar Java agent from the Amazon Corretto team

[ysoserial](https://github.com/frohoff/ysoserial): A proof-of-concept tool for generating Java serialization vulnerability payloads

[NotSoSerial](https://github.com/kantega/notsoserial): A Java agent which attempts to mitigate serialization vulnerabilities by selectively blocking serialization attempts
