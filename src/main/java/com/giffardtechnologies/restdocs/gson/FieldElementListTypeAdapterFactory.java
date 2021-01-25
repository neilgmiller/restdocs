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

import com.giffardtechnologies.restdocs.domain.FieldElementList;
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
import java.util.ArrayList;

public final class FieldElementListTypeAdapterFactory implements TypeAdapterFactory {

	@SuppressWarnings("unchecked")
	@Nullable
	@Override
	public <R> TypeAdapter<R> create(Gson gson, TypeToken<R> type) {
		if (type.getRawType() != FieldElementList.class) {
			return null;
		}

		TypeAdapter<ArrayList<FieldListElement>> delegateAdapter = gson.getDelegateAdapter(FieldElementListTypeAdapterFactory.this,
		                                                                                   new TypeToken<>() {});

		return (TypeAdapter<R>) createAdapter(delegateAdapter);
	}

	private TypeAdapter<FieldElementList> createAdapter(TypeAdapter<ArrayList<FieldListElement>> delegateAdapter) {
		return new TypeAdapter<>() {
			@Override
			public FieldElementList read(JsonReader in) throws IOException {
				FieldElementList fieldElementList = new FieldElementList();
				fieldElementList.setFieldListElements(delegateAdapter.read(in));

				return fieldElementList;
			}

			@Override
			public void write(JsonWriter out, FieldElementList value) throws IOException {
				delegateAdapter.write(out, value.getFieldListElements());
			}
		};
	}
}