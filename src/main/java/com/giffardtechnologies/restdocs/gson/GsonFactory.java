/*
 * ===========================================================================
 * Copyright (c) 2016-2018, Allego Corporation, MA USA
 *
 * This file and its contents are proprietary and confidential to and the sole
 * intellectual property of Allego Corporation.  Any use, reproduction,
 * redistribution or modification of this file is prohibited except as
 * explicitly defined by written license agreement with Allego Corporation.
 * ===========================================================================
 */
package com.giffardtechnologies.restdocs.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.giffardtechnologies.json.gson.BooleanDeserializer;
import org.giffardtechnologies.json.gson.LowercaseEnumTypeAdapterFactory;

/**
 * @author ngm
 *
 */
public class GsonFactory {

    /**
     * Create a {@link Gson} instance for serialization of request parameters and deserialization of responses.
     *
     * @return a Gson instance
     */
    public static Gson getGson()
    {
        return getGsonBuilder().create();
    }

    /**
     * Returns a {@link GsonBuilder} already configured for serialization of request parameters and deserialization of responses. This
     * allows further configuration to be performed before creating a {@link Gson} object.
     *
     * @return a preconfigured GsonBuilder
     */
    public static GsonBuilder getGsonBuilder() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapterFactory(new LowercaseEnumTypeAdapterFactory(true))
                   .registerTypeAdapterFactory(new FieldListElementTypeAdapterFactory())
                   .registerTypeAdapterFactory(new FieldElementListTypeAdapterFactory())
                   .registerTypeAdapter(boolean.class, new BooleanDeserializer())
                   .setPrettyPrinting();
        return gsonBuilder;
    }
}
