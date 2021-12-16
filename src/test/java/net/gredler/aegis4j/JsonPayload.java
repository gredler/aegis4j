/* Copyright (c) 2021, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

/**
 * Playing with deserialization (polymorphic type handling) fire.
 */
public class JsonPayload {

    @JsonTypeInfo(use = Id.CLASS)
    public Object property;

}
