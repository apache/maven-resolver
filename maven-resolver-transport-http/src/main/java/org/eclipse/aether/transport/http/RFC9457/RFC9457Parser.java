/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.transport.http.RFC9457;

import java.lang.reflect.Type;
import java.net.URI;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public class RFC9457Parser {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(RFC9457Payload.class, new RFC9457PayloadAdapter())
            .create();

    public static RFC9457Payload parse(String data) {
        return GSON.fromJson(data, RFC9457Payload.class);
    }

    private static class RFC9457PayloadAdapter implements JsonDeserializer<RFC9457Payload> {
        @Override
        public RFC9457Payload deserialize(
                final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject asJsonObject = json.getAsJsonObject();
            URI type = parseNullableURI(asJsonObject, "type", "about:blank");
            Integer status = parseStatus(asJsonObject);
            String title = parseNullableString(asJsonObject, "title");
            String detail = parseNullableString(asJsonObject, "detail");
            URI instance = parseNullableURI(asJsonObject, "instance", null);
            return new RFC9457Payload(type, status, title, detail, instance);
        }
    }

    private static Integer parseStatus(JsonObject jsonObject) {
        return jsonObject.get("status") == null || jsonObject.get("status").isJsonNull()
                ? null
                : jsonObject.get("status").getAsInt();
    }

    private static String parseNullableString(JsonObject jsonObject, String key) {
        return jsonObject.get(key) == null || jsonObject.get(key).isJsonNull()
                ? null
                : jsonObject.get(key).getAsString();
    }

    private static URI parseNullableURI(JsonObject jsonObject, String key, String defaultValue) {
        return !jsonObject.has(key) || jsonObject.get(key).isJsonNull()
                ? defaultValue != null ? URI.create(defaultValue) : null
                : URI.create(jsonObject.get(key).getAsString());
    }
}
