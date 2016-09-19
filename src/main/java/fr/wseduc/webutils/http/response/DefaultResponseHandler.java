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

package fr.wseduc.webutils.http.response;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class DefaultResponseHandler {

	private DefaultResponseHandler() {}

	public static Handler<Either<String, JsonObject>> defaultResponseHandler(
			final HttpServerRequest request) {
		return defaultResponseHandler(request, 200);
	}

	public static Handler<Either<String, JsonObject>> defaultResponseHandler(
			final HttpServerRequest request, final int successCode) {
		return new Handler<Either<String, JsonObject>>() {
			@Override
			public void handle(Either<String, JsonObject> event) {
				if (event.isRight()) {
					Renders.renderJson(request, event.right().getValue(), successCode);
				} else {
					JsonObject error = new JsonObject()
							.put("error", event.left().getValue());
					Renders.renderJson(request, error, 400);
				}
			}
		};
	}

	public static Handler<Either<String, JsonArray>> arrayResponseHandler(
			final HttpServerRequest request) {
		return new Handler<Either<String, JsonArray>>() {
			@Override
			public void handle(Either<String, JsonArray> event) {
				if (event.isRight()) {
					Renders.renderJson(request, event.right().getValue());
				} else {
					JsonObject error = new JsonObject()
							.put("error", event.left().getValue());
					Renders.renderJson(request, error, 400);
				}
			}
		};
	}

}

