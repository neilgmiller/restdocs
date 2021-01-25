/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.giffardtechnologies.restdocs.gson;

import com.giffardtechnologies.restdocs.domain.FieldListElement;
import com.giffardtechnologies.restdocs.domain.FieldListIncludeElement;
import com.giffardtechnologies.restdocs.domain.type.Field;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.Streams;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public final class FieldListElementTypeAdapterFactory implements TypeAdapterFactory {

	@SuppressWarnings("unchecked")
	@Nullable
	@Override
	public <R> TypeAdapter<R> create(Gson gson, TypeToken<R> type) {
		if (type.getRawType() != FieldListElement.class) {
			return null;
		}

		return (TypeAdapter<R>) createAdapter(gson);
	}

	private TypeAdapter<FieldListElement> createAdapter(Gson gson) {
		return new TypeAdapter<>() {
			@Override
			public FieldListElement read(JsonReader in) {
				JsonElement jsonElement = Streams.parse(in);
				JsonElement labelJsonElement = jsonElement.getAsJsonObject().get("include");

				TypeAdapter<? extends FieldListElement> delegate;
				if (labelJsonElement == null) {
					delegate = gson.getDelegateAdapter(FieldListElementTypeAdapterFactory.this, TypeToken.get(Field.class));
				} else {
					delegate = gson.getDelegateAdapter(FieldListElementTypeAdapterFactory.this, TypeToken.get(
							FieldListIncludeElement.class));
				}

				if (delegate == null) {
					throw new JsonParseException("cannot deserialize");
				}
				return delegate.fromJsonTree(jsonElement);
			}

			@Override
			public void write(JsonWriter out, FieldListElement value) throws IOException {
				JsonObject jsonObject;
				if (value instanceof Field) {
					TypeAdapter<Field> delegate;
					delegate = gson.getDelegateAdapter(FieldListElementTypeAdapterFactory.this, TypeToken.get(Field.class));
					jsonObject = delegate.toJsonTree((Field) value).getAsJsonObject();
				} else if (value instanceof FieldListIncludeElement) {
					TypeAdapter<FieldListIncludeElement> delegate;
					delegate = gson.getDelegateAdapter(FieldListElementTypeAdapterFactory.this,
					                                   TypeToken.get(FieldListIncludeElement.class));
					jsonObject = delegate.toJsonTree((FieldListIncludeElement) value).getAsJsonObject();
				} else {
					throw new JsonParseException("cannot serialize " + value.getClass().getName());
				}

				Streams.write(jsonObject, out);
			}
		};
	}
}