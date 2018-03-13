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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonUtils {

	public static Map<String, Object> convertMap(io.vertx.core.json.JsonObject json) {
		final Map<String, Object> converted = new LinkedHashMap<>(json.size());
		for (Map.Entry<String, Object> entry : json.getMap().entrySet()) {
			Object obj = entry.getValue();
			if (obj instanceof io.vertx.core.json.JsonObject) {
				io.vertx.core.json.JsonObject jm = (io.vertx.core.json.JsonObject) obj;
				converted.put(entry.getKey(), convertMap(jm));
			} else if (obj instanceof io.vertx.core.json.JsonArray) {
				io.vertx.core.json.JsonArray list = (io.vertx.core.json.JsonArray) obj;
				converted.put(entry.getKey(), convertList(list));
			} else {
				converted.put(entry.getKey(), obj);
			}
		}
		return converted;
	}

	public static List<Object> convertList(io.vertx.core.json.JsonArray list) {
		final List<Object> arr = new ArrayList<>(list.size());
		for (Object obj : list) {
			if (obj instanceof io.vertx.core.json.JsonObject) {
				arr.add(convertMap((io.vertx.core.json.JsonObject) obj));
			} else if (obj instanceof io.vertx.core.json.JsonArray) {
				arr.add(convertList((JsonArray) obj));
			} else {
				arr.add(obj);
			}
		}
		return arr;
	}

}
