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

package fr.wseduc.webutils;

import java.io.IOException;
import java.util.*;

import fr.wseduc.vertx.eventbus.EventBusWrapperFactory;
import fr.wseduc.webutils.data.FileResolver;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.http.Binding;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.logging.Tracer;
import fr.wseduc.webutils.logging.TracerFactory;
import fr.wseduc.webutils.request.filter.Filter;
import fr.wseduc.webutils.request.filter.SecurityHandler;
import io.vertx.core.*;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileProps;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;

import fr.wseduc.webutils.http.StaticResource;
import fr.wseduc.webutils.request.CookieHelper;
import fr.wseduc.webutils.security.SecuredAction;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;
import org.vertx.java.core.http.RouteMatcher;

public abstract class Server extends AbstractVerticle {

	protected static final Logger log = LoggerFactory.getLogger(Server.class);
	public JsonObject config;
	public RouteMatcher rm;
	public Tracer trace;
	private I18n i18n;
	protected Map<String, SecuredAction> securedActions;
	protected Set<Binding> securedUriBinding = new HashSet<>();
	private LocalMap<String, String> staticRessources;
	private boolean dev;

	@Override
	public void start() throws Exception {
		super.start();
		config = config();
		FileResolver.getInstance().setBasePath(config);
		rm = new RouteMatcher();
		trace = TracerFactory.getTracer(this.getClass().getSimpleName());
		i18n = I18n.getInstance();
		i18n.init(vertx);
		CookieHelper.getInstance().init((String) vertx
				.sharedData().getLocalMap("server").get("signKey"), log);
		staticRessources = vertx.sharedData().getLocalMap("staticRessources");
		dev = "dev".equals(config.getString("mode"));

		log.info("Verticle: " + this.getClass().getSimpleName() + " starts on port: " + config.getInteger("port"));

		final String prefix = getPathPrefix(config);
		// Serve public static resource like img, css, js. By convention in /public directory
		// Dummy impl
		rm.getWithRegEx(prefix.replaceAll("\\/", "\\/") + "\\/public\\/.+",
				new Handler<HttpServerRequest>() {
			public void handle(final HttpServerRequest request) {
				if (dev) {
					request.response().sendFile("." + request.path().substring(prefix.length()));
				} else {
					if (staticRessources.get(request.uri()) != null) {
						StaticResource.serveRessource(request,
								"." + request.path().substring(prefix.length()),
								staticRessources.get(request.uri()), dev);
					} else {
						vertx.fileSystem().props("." + request.path().substring(prefix.length()),
								new Handler<AsyncResult<FileProps>>(){
							@Override
							public void handle(AsyncResult<FileProps> af) {
								if (af.succeeded()) {
									String lastModified = StaticResource.formatDate(af.result().lastModifiedTime());
									staticRessources.put(request.uri(), lastModified);
									StaticResource.serveRessource(request,
											"." + request.path().substring(prefix.length()),
											lastModified, dev);
								} else {
									request.response().sendFile("." + request.path().substring(prefix.length()));
								}
							}
						});
					}
				}
			}
		});

		rm.get(prefix + "/i18n", new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest request) {
				i18nMessages(request);
			}
		});

		rm.get(prefix + "/languages", new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest request) {
				Controller.renderJson(request, i18n.getLanguages(Renders.getHost(request)));
			}
		});

		rm.get(prefix + "/monitoring", new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest event) {
				Controller.renderJson(event, new JsonObject().put("test", "ok"));
			}
		});

		try {
			final String appName = config.getString("app-name", this.getClass().getSimpleName());
			JsonObject application = new JsonObject()
			.put("name", appName)
			.put("displayName", config.getString("app-displayName", appName.toLowerCase()))
			.put("icon", config.getString("app-icon", ""))
			.put("address", config.getString("app-address", ""))
			.put("display", config.getBoolean("display", true))
			.put("prefix", getPathPrefix(config));
			JsonArray actions = StartupUtils.loadSecuredActions(vertx);
			securedActions = StartupUtils.securedActionsToMap(actions);
			log.info("secureaction loaded : " + actions.encode());
			if (config.getString("integration-mode","BUS").equals("HTTP")) {
				StartupUtils.sendStartup(application, actions, vertx,
						config.getInteger("app-registry.port", 8012));
			} else {
				StartupUtils.sendStartup(application, actions,
						Server.getEventBus(vertx),
						config.getString("app-registry.address", "wse.app.registry"), vertx);
			}
		} catch (IOException e) {
			log.error("Error application not registred.", e);
		}
		vertx.createHttpServer().requestHandler(rm).listen(config.getInteger("port"));
	}

	protected void i18nMessages(HttpServerRequest request) {
		Controller.renderJson(request, i18n.load(
				I18n.acceptLanguage(request), Renders.getHost(request)));
	}

	/**
	 * @deprecated Use request.formAttributes() instead
	 * @param request http request
	 * @param handler receive attributes
	 */
	public void bodyToParams(final HttpServerRequest request, final Handler<MultiMap> handler) {
		request.setExpectMultipart(true);
		request.endHandler(new Handler<Void>() {
			@Override
			public void handle(Void v) {
				handler.handle(request.formAttributes());
			}
		});
	}

	public static String getPathPrefix(JsonObject config) {
		String path = config.getString("path-prefix");
		if (path == null) {
			String verticle = config.getString("main");
			if (verticle != null && !verticle.trim().isEmpty() && verticle.contains(".")) {
				path = verticle.substring(verticle.lastIndexOf('.') + 1).toLowerCase();
			}
		}
		if ("".equals(path) || "/".equals(path)) {
			return "";
		}
		return "/" + path;
	}

	public static EventBus getEventBus(Vertx vertx) {
//		ServiceLoader<EventBusWrapperFactory> factory = ServiceLoader
//				.load(EventBusWrapperFactory.class);
//		if (factory.iterator().hasNext()) {
//			return factory.iterator().next().getEventBus(vertx);
//		}
		return vertx.eventBus();
	}

	protected Server addController(BaseController controller) {
		log.info("add controller");
		controller.init(vertx, config, rm, securedActions);
		securedUriBinding.addAll(controller.securedUriBinding());
		return this;
	}

	protected Server clearFilters() {
		SecurityHandler.clearFilters();
		return this;
	}

	protected Server addFilter(Filter filter) {
		SecurityHandler.addFilter(filter);
		return this;
	}

}
