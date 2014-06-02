package fr.wseduc.webutils.http;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Container;

import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.Server;

public class Renders {

	protected static final Logger log = LoggerFactory.getLogger(Renders.class);
	protected String pathPrefix;
	protected Container container;
	private final I18n i18n;
	protected Vertx vertx;
	private static final ConcurrentMap<String, Template> templates = new ConcurrentHashMap<>();

	public Renders(Vertx vertx, Container container) {
		this.container = container;
		if (container != null) {
			this.pathPrefix = Server.getPathPrefix(container.config());
		}
		this.i18n = I18n.getInstance();
		this.vertx = vertx;
	}

	private void setLambdaTemplateRequest(final HttpServerRequest request,
			Map<String, Object> ctx) {
		ctx.put("i18n", new Mustache.Lambda() {

			@Override
			public void execute(Template.Fragment frag, Writer out) throws IOException {
				String key = frag.execute();
				String text = i18n.translate(key, request.headers().get("Accept-Language"));
				out.write(text);
			}
		});

		ctx.put("static", new Mustache.Lambda() {

			@Override
			public void execute(Template.Fragment frag, Writer out) throws IOException {
				String path = frag.execute();
				out.write(staticResource(request, container.config().getBoolean("ssl", false),
						null, pathPrefix + "/public", path));
			}
		});

		ctx.put("infra", new Mustache.Lambda() {

			@Override
			public void execute(Template.Fragment frag, Writer out) throws IOException {
				String path = frag.execute();
				out.write(staticResource(request, container.config().getBoolean("ssl", false),
						"8001", "/infra/public", path));
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
	 * TODO : isolate sscope management 
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
			public void handle(Writer writer) {
				if (writer != null) {
				request.response().putHeader("content-type", "text/html");
				request.response().setStatusCode(status);
				request.response().end(writer.toString());
				} else {
					renderError(request);
				}
			}
		});
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

	private void processTemplate(final HttpServerRequest request,
			JsonObject p, String resourceName, Reader r, final Handler<Writer> handler) {
		final JsonObject params = (p == null) ? new JsonObject() : p;
		getTemplate(request, resourceName, r, new Handler<Template>() {

			@Override
			public void handle(Template t) {
				if (t != null) {
					try {
						Writer writer = new StringWriter();
						Map<String, Object> ctx = params.toMap();
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
		if (!"dev".equals(container.config().getString("mode")) && templates.containsKey(path)) {
			handler.handle(templates.get(path));
		} else {
			final String p = path;
			vertx.fileSystem().readFile(p, new Handler<AsyncResult<Buffer>>() {
				@Override
				public void handle(AsyncResult<Buffer> ar) {
					if (ar.succeeded()) {
						Mustache.Compiler compiler = Mustache.compiler().defaultValue("");
						Template template = compiler.compile(ar.result().toString("UTF-8"));
						if("dev".equals(container.config().getString("mode"))) {
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

	public static void badRequest(HttpServerRequest request) {
		request.response().setStatusCode(400).end();
	}

	public static void unauthorized(HttpServerRequest request) {
		request.response().setStatusCode(401).end();
	}

	public static void notFound(HttpServerRequest request) {
		request.response().setStatusCode(404).end();
	}

	public static void notModified(HttpServerRequest request) {
		notModified(request, null);
	}

	public static void notModified(HttpServerRequest request, String fileId) {
		if (fileId != null && !fileId.trim().isEmpty()) {
			request.response().headers().add("ETag", fileId);
		}
		request.response().setStatusCode(304).end();
	}

	public static void renderError(HttpServerRequest request, JsonObject error) {
		request.response().setStatusCode(500);
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
		request.response().putHeader("content-type", "text/json");
		request.response().setStatusCode(status);
		request.response().end(jo.encode());
	}

	public static void renderJson(HttpServerRequest request, JsonObject jo) {
		renderJson(request, jo, 200);
	}

	public static void renderJson(HttpServerRequest request, JsonArray jo) {
		request.response().putHeader("content-type", "text/json");
		request.response().end(jo.encode());
	}

	public static void redirect(HttpServerRequest request, String location) {
		redirect(request, getScheme(request) + "://" + getHost(request), location);
	}

	public static void redirect(HttpServerRequest request, String host, String location) {
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
		String scheme = request.absoluteURI().getScheme();
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
}