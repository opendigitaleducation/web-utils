/*
 * Copyright © WebServices pour l'Éducation, 2014
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.wseduc.webutils.collections;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;

import java.time.Instant;
import java.util.Map;

import static fr.wseduc.webutils.Utils.getOrElse;

public class JsonObject extends io.vertx.core.json.JsonObject {

	public JsonObject(String json) {
		super(json);
	}

	public JsonObject() {
		super();
	}

	public JsonObject(Map<String, Object> map) {
		super(map);
	}

	public JsonObject(Buffer buf) {
		super(buf);
	}

	@Override
	public String getString(String key, String def) {
		return getOrElse(super.getString(key), def);
	}

	@Override
	public Integer getInteger(String key, Integer def) {
		return getOrElse(super.getInteger(key), def);
	}

	@Override
	public Long getLong(String key, Long def) {
		return getOrElse(super.getLong(key), def);
	}

	@Override
	public Double getDouble(String key, Double def) {
		return getOrElse(super.getDouble(key), def);
	}

	@Override
	public Float getFloat(String key, Float def) {
		return getOrElse(super.getFloat(key), def);
	}

	@Override
	public Boolean getBoolean(String key, Boolean def) {
		return getOrElse(super.getBoolean(key), def);
	}

	@Override
	public io.vertx.core.json.JsonObject getJsonObject(String key, io.vertx.core.json.JsonObject def) {
		return getOrElse(super.getJsonObject(key), def);
	}

	@Override
	public JsonArray getJsonArray(String key, JsonArray def) {
		return getOrElse(super.getJsonArray(key), def);
	}

	@Override
	public byte[] getBinary(String key, byte[] def) {
		return getOrElse(super.getBinary(key), def);
	}

	@Override
	public Instant getInstant(String key, Instant def) {
		return getOrElse(super.getInstant(key), def);
	}

	@Override
	public Object getValue(String key, Object def) {
		return getOrElse(super.getValue(key), def);
	}

}
