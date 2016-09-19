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

package fr.wseduc.webutils.http;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.Server;

import static fr.wseduc.webutils.Utils.isNotEmpty;

public class Renders {

	protected static final Logger log = LoggerFactory.getLogger(Renders.class);
	protected String pathPrefix;
	private final I18n i18n;
	protected Vertx vertx;
	private static final ConcurrentMap<String, Template> templates = new ConcurrentHashMap<>();
	private List<HookProcess> hookRenderProcess;
	protected JsonObject config;

	public Renders(Vertx vertx, JsonObject config) {
		this.config = config;
		if (config != null) {
			this.pathPrefix = Server.getPathPrefix(config);
		}
		this.i18n = I18n.getInstance();
		this.vertx = vertx;
	}

	protected void setLambdaTemplateRequest(final HttpServerRequest request,
			Map<String, Object> ctx) {
		ctx.put("i18n", new Mustache.Lambda() {

			@Override
			public void execute(Template.Fragment frag, Writer out) throws IOException {
				String key = frag.execute();
				String text = i18n.translate(key, getHost(request), I18n.acceptLanguage(request));
				out.write(text);
			}
		});

		ctx.put("static", new Mustache.Lambda() {

			@Override
			public void execute(Template.Fragment frag, Writer out) throws IOException {
				String path = frag.execute();
				out.write(staticResource(request, config.getBoolean("ssl", false),
						null, pathPrefix + "/public", path));
			}
		});

		ctx.put("infra", new Mustache.Lambda() {

			@Override
			public void execute(Template.Fragment frag, Writer out) throws IOException {
				String path = frag.execute();
				out.write(staticResource(request, config.getBoolean("ssl", false),
						"8001", "/infra/public", path));
			}
		});

		ctx.put("formatBirthDate", new Mustache.Lambda() {
			@Override
			public void execute(Template.Fragment frag, Writer out) throws IOException {
				String date = frag.execute();
				if(date != null && date.trim().length() > 0){
					String[] splitted = date.split("-");
					if(splitted.length == 3){
						out.write(splitted[2] + "/" + splitted[1] + "/" + splitted[0]);
						return;
					}
				}
				out.write(date);
			}
		});
	}

	private String staticResource(HttpServerRequest request,
			boolean https, String infraPort, String publicDir, String path) {
		String host = Renders.getHost(request);
		String protocol = https ? "https://" : "http://";
		if (infraPort != null && request.headers().get("X-Forwarded-For") == null) {
			host = host.split(":")[0] + ":" + infraPort;
		}
		return protocol
				+ host
				+ ((publicDir != null && publicDir.startsWith("/")) ? publicDir : "/" + publicDir)
				+ "/" + path;
	}

	public void renderView(HttpServerRequest request) {
		renderView(request, new JsonObject());
	}

	/*
	 * Render a Mustache template : see http://mustache.github.com/mustache.5.html
	 * TODO : modularize
	 * TODO : isolate scope management
	 */
	public void renderView(HttpServerRequest request, JsonObject params) {
		renderView(request, params, null, null, 200);
	}

	public void renderView(HttpServerRequest request, JsonObject params, String resourceName, Reader r) {
		renderView(request, params, resourceName, r, 200);
	}

	public void renderView(final HttpServerRequest request, JsonObject params,
			String resourceName, Reader r, final int status) {
		processTemplate(request, params, resourceName, r, new Handler<Writer>() {
			@Override
			public void handle(final Writer writer) {
				if (writer != null) {
					request.response().putHeader("content-type", "text/html; charset=utf-8");
					request.response().setStatusCode(status);
					if (hookRenderProcess != null) {
						executeHandlersHookRender(request, new Handler<Void>() {
							@Override
							public void handle(Void v) {
								request.response().end(writer.toString());
							}
						});
					} else {
						request.response().end(writer.toString());
					}
				} else {
					renderError(request);
				}
			}
		});
	}

	@SuppressWarnings("unchecked")
	private void executeHandlersHookRender(final HttpServerRequest request, Handler<Void> endHandler) {
		final Handler[] handlers = new Handler[hookRenderProcess.size() + 1];
		handlers[handlers.length - 1] = endHandler;
		for (int i = hookRenderProcess.size() - 1; i >= 0; i--) {
			final int j = i;
			handlers[i] = new Handler<Void>() {
				@Override
				public void handle(Void v) {
					hookRenderProcess.get(j).execute(request, handlers[j + 1]);
				}
			};
		}
		handlers[0].handle(null);
	}

	public void processTemplate(HttpServerRequest request, String template, JsonObject params,
			final Handler<String> handler) {
		processTemplate(request, params, template, null, new Handler<Writer>() {
			@Override
			public void handle(Writer w) {
				if (w != null) {
					handler.handle(w.toString());
				} else {
					handler.handle(null);
				}
			}
		});
	}

	public void processTemplate(final HttpServerRequest request,
			JsonObject p, String resourceName, Reader r, final Handler<Writer> handler) {
		final JsonObject params = (p == null) ? new JsonObject() : p;
		getTemplate(request, resourceName, r, new Handler<Template>() {

			@Override
			public void handle(Template t) {
				if (t != null) {
					try {
						Writer writer = new StringWriter();
						Map<String, Object> ctx = params.getMap();
						setLambdaTemplateRequest(request, ctx);
						t.execute(ctx, writer);
						handler.handle(writer);
					} catch (Exception e) {
						log.error(e.getMessage(), e);
						handler.handle(null);
					}
				} else {
					handler.handle(null);
				}
			}
		});
	}

	private void getTemplate(HttpServerRequest request, String resourceName,
			Reader r, final Handler<Template> handler) {
		String path;
		if (resourceName != null && r != null && !resourceName.trim().isEmpty()) {
			Mustache.Compiler compiler = Mustache.compiler().defaultValue("");
			handler.handle(compiler.compile(r));
			return;
		} else if (resourceName != null && !resourceName.trim().isEmpty()) {
			path = "view/" + resourceName;
		} else {
			String template = request.path().substring(pathPrefix.length());
			if (template.trim().isEmpty()) {
				template = pathPrefix.substring(1);
			}
			path = "view/" + template + ".html";
		}
		if (!"dev".equals(config.getString("mode")) && templates.containsKey(path)) {
			handler.handle(templates.get(path));
		} else {
			final String p = path;
			vertx.fileSystem().readFile(p, new Handler<AsyncResult<Buffer>>() {
				@Override
				public void handle(AsyncResult<Buffer> ar) {
					if (ar.succeeded()) {
						Mustache.Compiler compiler = Mustache.compiler().defaultValue("");
						Template template = compiler.compile(ar.result().toString("UTF-8"));
						if("dev".equals(config.getString("mode"))) {
							templates.put(p, template);
						} else {
							templates.putIfAbsent(p, template);
						}
						handler.handle(template);
					} else {
						handler.handle(null);
					}
				}
			});
		}
	}

	public static void ok(HttpServerRequest request) {
		request.response().end();
	}

	public static void created(HttpServerRequest request) {
		request.response().setStatusCode(201).setStatusMessage("Created").end();
	}

	public static void noContent(HttpServerRequest request) {
		request.response().setStatusCode(204).setStatusMessage("No Content").end();
	}

	public static void badRequest(HttpServerRequest request) {
		request.response().setStatusCode(400).setStatusMessage("Bad Request").end();
	}

	public static void badRequest(HttpServerRequest request, String message) {
		request.response().putHeader("content-type", "application/json");
		request.response().putHeader("Cache-Control", "no-cache, must-revalidate");
		request.response().putHeader("Expires", "-1");
		request.response().setStatusCode(400).setStatusMessage("Bad Request").end(
				new JsonObject().put("error", message).encode());
	}

	public static void unauthorized(HttpServerRequest request) {
		request.response().setStatusCode(401).setStatusMessage("Unauthorized").end();
	}

	public static void unauthorized(HttpServerRequest request, String message) {
		request.response().putHeader("content-type", "application/json");
		request.response().putHeader("Cache-Control", "no-cache, must-revalidate");
		request.response().putHeader("Expires", "-1");
		request.response().setStatusCode(401).setStatusMessage("Unauthorized").end(
				new JsonObject().put("error", message).encode());
	}

	public static void forbidden(HttpServerRequest request) {
		request.response().setStatusCode(403).setStatusMessage("Forbidden").end();
	}

	public static void forbidden(HttpServerRequest request, String message) {
		request.response().putHeader("content-type", "application/json");
		request.response().putHeader("Cache-Control", "no-cache, must-revalidate");
		request.response().putHeader("Expires", "-1");
		request.response().setStatusCode(403).setStatusMessage("Forbidden").end(
				new JsonObject().put("error", message).encode());
	}

	public static void notFound(HttpServerRequest request) {
		request.response().setStatusCode(404).setStatusMessage("Not Found").end();
	}

	public static void notFound(HttpServerRequest request, String message) {
		request.response().putHeader("content-type", "application/json");
		request.response().putHeader("Cache-Control", "no-cache, must-revalidate");
		request.response().putHeader("Expires", "-1");
		request.response().setStatusCode(404).setStatusMessage("Not Found").end(
				new JsonObject().put("error", message).encode());
	}

	public static void conflict(HttpServerRequest request) {
		request.response().setStatusCode(409).setStatusMessage("Conflict").end();
	}

	public static void conflict(HttpServerRequest request, String message) {
		request.response().putHeader("content-type", "application/json");
		request.response().putHeader("Cache-Control", "no-cache, must-revalidate");
		request.response().putHeader("Expires", "-1");
		request.response().setStatusCode(409).setStatusMessage("Conflict").end(
				new JsonObject().put("error", message).encode());
	}

	public static void notModified(HttpServerRequest request) {
		notModified(request, null);
	}

	public static void notModified(HttpServerRequest request, String fileId) {
		if (fileId != null && !fileId.trim().isEmpty()) {
			request.response().headers().add("ETag", fileId);
		}
		request.response().setStatusCode(304).setStatusMessage("Not Modified").end();
	}

	public static void renderError(HttpServerRequest request, JsonObject error) {
		request.response().putHeader("content-type", "application/json");
		request.response().putHeader("Cache-Control", "no-cache, must-revalidate");
		request.response().putHeader("Expires", "-1");
		request.response().setStatusCode(500).setStatusMessage("Internal Server Error");
		if (error != null) {
			request.response().end(error.encode());
		} else {
			request.response().end();
		}
	}

	public static void renderError(HttpServerRequest request) {
		renderError(request, null);
	}

	public static void renderJson(HttpServerRequest request, JsonObject jo, int status) {
		request.response().putHeader("content-type", "application/json");
		request.response().putHeader("Cache-Control", "no-cache, must-revalidate");
		request.response().putHeader("Expires", "-1");
		request.response().setStatusCode(status);
		request.response().end(jo.encode());
	}

	public static void renderJson(HttpServerRequest request, JsonObject jo) {
		renderJson(request, jo, 200);
	}

	public static void renderJson(HttpServerRequest request, JsonArray jo) {
		request.response().putHeader("content-type", "application/json");
		request.response().putHeader("Cache-Control", "no-cache, must-revalidate");
		request.response().putHeader("Expires", "-1");
		request.response().end(jo.encode());
	}

	public static void redirect(HttpServerRequest request, String location) {
		redirect(request, getScheme(request) + "://" + getHost(request), location);
	}

	public static void redirect(HttpServerRequest request, String host, String location) {
		if (host == null || !host.startsWith("http")) {
			redirect(request, (host != null ? host : "") + location);
			return;
		}
		request.response().setStatusCode(302);
		request.response().putHeader("Location", host + location);
		request.response().end();
	}

	public static void redirectPermanent(HttpServerRequest request, String location) {
		redirectPermanent(request, getScheme(request) + "://" + getHost(request), location);
	}

	public static void redirectPermanent(HttpServerRequest request, String host, String location) {
		request.response().setStatusCode(301);
		request.response().putHeader("Location", host + location);
		request.response().end();
	}

	public static String getScheme(HttpServerRequest request) {
		String proto = request.headers().get("X-Forwarded-Proto");
		if (proto != null && !proto.trim().isEmpty()) {
			return proto;
		}
		String scheme = null;
		try {
			scheme = new URI(request.absoluteURI()).getScheme();
		} catch (URISyntaxException e) {
			log.error("Invalid uri", e);
		}
		if (scheme == null) {
			scheme = "http";
		}
		return scheme;
	}

	public static String getHost(HttpServerRequest request) {
		String host = request.headers().get("X-Forwarded-Host");
		if (host != null && !host.trim().isEmpty()) {
			return host;
		}
		return request.headers().get("Host");
	}

	public static String getIp(HttpServerRequest request) {
		String ip = request.headers().get("X-Forwarded-For");
		if (isNotEmpty(ip)) {
			return ip;
		}
		return request.remoteAddress().host();
	}

	public void addHookRenderProcess(HookProcess hookRenderProcess) {
		if (this.hookRenderProcess == null) {
			this.hookRenderProcess = new ArrayList<>();
		}
		this.hookRenderProcess.add(hookRenderProcess);
	}

}
