/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

/**
 * Like {@link java.lang.Runnable}, but throws exceptions.
 */
@FunctionalInterface
public interface Task {
    void run() throws Exception;
}
