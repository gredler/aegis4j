/* Copyright (c) 2021, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javassist.ByteArrayClassPath;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;

/**
 * Disables rarely-used platform features like JNDI and RMI so that they cannot be exploited by attackers.
 * See the {@code mods.properties} file for the actual class modification configuration.
 */
public final class Patcher implements ClassFileTransformer {

    private final Map< String, List< Modification > > modifications; // class name -> modifications

    /**
     * Creates a new class patcher which blocks the specified features.
     *
     * @param block the features to block
     */
    public Patcher(Set< String > block) {
        modifications = loadModifications(block);
    }

    /**
     * Registers a new patcher on the specified instrumentation instance and triggers class transformation.
     *
     * @param instr the instrumentation instance to add a new patcher to
     * @param block the features to block
     */
    public static void start(Instrumentation instr, Set< String > block) {

        Patcher patcher = new Patcher(block);
        instr.addTransformer(patcher, true);

        for (String className : patcher.modifications.keySet()) {
            try {
                System.out.println("Aegis4j patching " + className + "...");
                Class< ? > clazz = Class.forName(className);
                instr.retransformClasses(clazz);
            } catch (ClassNotFoundException | UnmodifiableClassException e) {
                e.printStackTrace();
            }
        }

        System.setProperty("aegis4j.blocked.features", String.join(",", block));
    }

    @Override
    public byte[] transform(Module module, ClassLoader loader, String className, Class< ? > clazz, ProtectionDomain domain, byte[] classBytes) {
        return transform(loader, className, clazz, domain, classBytes);
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class< ? > clazz, ProtectionDomain domain, byte[] classBytes) {
        return patch(className.replace('/', '.'), classBytes);
    }

    private byte[] patch(String className, byte[] classBytes) {

        List< Modification > mods = modifications.get(className);
        if (mods == null || mods.isEmpty()) {
            return null;
        }

        ClassPool pool = new ClassPool();
        pool.appendClassPath(new ByteArrayClassPath(className, classBytes));
        pool.appendClassPath(new LoaderClassPath(ClassLoader.getPlatformClassLoader()));
        pool.appendClassPath(new LoaderClassPath(getClass().getClassLoader()));

        try {
            CtClass clazz = pool.get(className);
            for (Modification mod : mods) {
                if (mod.isConstructor()) {
                    for (CtConstructor constructor : clazz.getConstructors()) {
                        constructor.setBody(mod.newBody);
                    }
                } else {
                    CtMethod[] methods = mod.isAll() ? clazz.getDeclaredMethods() : clazz.getDeclaredMethods(mod.methodName);
                    if (methods.length == 0) {
                        System.err.println("ERROR: Method not found: " + className + "." + mod.methodName);
                    }
                    for (CtMethod method : methods) {
                        method.setBody(mod.newBody);
                    }
                }
            }
            return clazz.toBytecode();
        } catch (NotFoundException | CannotCompileException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Map< String, List< Modification > > loadModifications(Set< String > block) {

        Properties props = new Properties();
        try {
            props.load(AegisAgent.class.getResourceAsStream("mods.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        List< Modification > mods = new ArrayList<>();
        for (String key : props.stringPropertyNames()) {
            int first = key.indexOf('.');
            int last = key.lastIndexOf('.');
            String feature = key.substring(0, first).toLowerCase();
            if (block.contains(feature)) {
                String className = key.substring(first + 1, last);
                String methodName = key.substring(last + 1);
                String newBody = props.getProperty(key);
                Modification mod = new Modification(className, methodName, newBody);
                mods.add(mod);
            }
        }

        return Collections.unmodifiableMap(new TreeMap<>(
            mods.stream().collect(Collectors.groupingBy(mod -> mod.className, Collectors.toUnmodifiableList()))
        ));
    }

    private static final class Modification {

        public final String className;
        public final String methodName;
        public final String newBody;

        public Modification(String className, String methodName, String newBody) {
            this.className = className;
            this.methodName = methodName;
            this.newBody = newBody;
        }

        public boolean isConstructor() {
            return className.substring(className.lastIndexOf('.') + 1).equals(methodName);
        }

        public boolean isAll() {
            return "*".equals(methodName);
        }
    }
}
