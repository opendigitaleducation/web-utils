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

import fr.wseduc.webutils.template.TemplateProcessor;
import fr.wseduc.webutils.template.lambdas.FormatBirthDateLambda;
import fr.wseduc.webutils.template.lambdas.I18nLambda;
import fr.wseduc.webutils.template.lambdas.InfraLambda;
import fr.wseduc.webutils.template.lambdas.StaticLambda;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;

import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.Server;

import static fr.wseduc.webutils.Utils.isNotEmpty;

public class Renders {

	protected static final Logger log = LoggerFactory.getLogger(Renders.class);
	protected String pathPrefix;
	protected Vertx vertx;
	private List<HookProcess> hookRenderProcess;
	protected JsonObject config;
	protected String staticHost;
	protected TemplateProcessor templateProcessor;
	protected static final List<String> allowedHosts = new ArrayList<>();

	public Renders(Vertx vertx, JsonObject config) {
		this.config = config;
		if (config != null) {
			this.pathPrefix = Server.getPathPrefix(config);
		}
		this.vertx = vertx;
		if (vertx != null) {
			this.templateProcessor = new TemplateProcessor(vertx, "view/", false);
			this.templateProcessor.setLambda("formatBirthDate", new FormatBirthDateLambda());
		}
	}

	protected void init(Vertx vertx, JsonObject config)
	{
		this.vertx = vertx;
		this.config = config;
		if (pathPrefix == null) {
			this.pathPrefix = Server.getPathPrefix(config);
		}

		LocalMap<Object, Object> server = vertx.sharedData().getLocalMap("server");
		this.staticHost = (String) server.get("static-host");

		if (templateProcessor == null && vertx != null) {
			this.templateProcessor = new TemplateProcessor(vertx, "view/", false);
			this.templateProcessor.setLambda("formatBirthDate", new FormatBirthDateLambda());
		}
	}

	protected void setLambdaTemplateRequest(final HttpServerRequest request)
	{
		String host = Renders.getHost(request);
		if(host == null) // This can happen for forged requests
			host = "";
		String sttcHost = this.staticHost != null ? this.staticHost : host;
		this.templateProcessor.setLambda("i18n",
			new I18nLambda(I18n.acceptLanguage(request), host));
		this.templateProcessor.setLambda("static",
			new StaticLambda(config.getBoolean("ssl", sttcHost.startsWith("https")), sttcHost, this.pathPrefix + "/public"));
		this.templateProcessor.setLambda("infra",
			new InfraLambda(config.getBoolean("ssl", sttcHost.startsWith("https")), sttcHost, "/infra/public", request.headers().get("X-Forwarded-For") == null));
	}

	public void renderView(HttpServerRequest request) {
		renderView(request, new JsonObject());
	}

	/*
	 * Render a Mustache template : see http://mustache.github.com/mustache.5.html
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
		final Handler<Void>[] handlers = new Handler[hookRenderProcess.size() + 1];
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

	public void processTemplate(HttpServerRequest request, String template, JsonObject params, final Handler<String> handler)
	{
		this.setLambdaTemplateRequest(request);
		this.templateProcessor.escapeHTML(true).processTemplate(this.genTemplateName(template, request), params, handler);
	}

	public void processTemplate(final HttpServerRequest request, JsonObject p, String resourceName, Reader r, final Handler<Writer> handler)
	{
		this.setLambdaTemplateRequest(request);
		this.templateProcessor.escapeHTML(true).processTemplate(this.genTemplateName(resourceName, request), p, r, handler);
	}

	public void processTemplate(final HttpServerRequest request, JsonObject p, String resourceName, boolean escapeHTML, final Handler<String> handler)
	{
		this.setLambdaTemplateRequest(request);
		this.templateProcessor.escapeHTML(escapeHTML).processTemplate(this.genTemplateName(resourceName, request), p, handler);
	}

	private String genTemplateName(final String resourceName, final HttpServerRequest request)
	{
		if (resourceName != null && !resourceName.trim().isEmpty())
			return resourceName;
		else
		{
			String template = request.path().substring(pathPrefix.length());
			if (template.trim().isEmpty()) {
				template = pathPrefix.substring(1);
			}
			return template + ".html";
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
		final String proto = request.headers().get("X-Forwarded-Proto");
		if (proto != null && !proto.trim().isEmpty()) {
			return proto;
		}
		String scheme = null;
		final String absoluteUri = request.absoluteURI();
		if (absoluteUri != null) {
			try {
				scheme = new URI(absoluteUri).getScheme();
			} catch (URISyntaxException e) {
				log.error("Invalid uri", e);
			}
		}
		if (scheme == null) {
			scheme = "http";
		}
		return scheme;
	}

	public static String getHost(HttpServerRequest request) {
		String host = request.headers().get("X-Forwarded-Host");
		if (host == null || host.trim().isEmpty()) {
			host = request.headers().get("Host");
		}
		if (!allowedHosts.isEmpty()) {
			if (allowedHosts.contains(host)) {
				return host;
			} else {
				return allowedHosts.get(0);
			}
		}
		return host;
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

	public static List<String> getAllowedHosts() {
		return allowedHosts;
	}

}
