/* Copyright (c) 2021, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

/**
 * Like {@link java.lang.Runnable}, but throws exceptions.
 */
@FunctionalInterface
public interface Task {
    void run() throws Exception;
}
