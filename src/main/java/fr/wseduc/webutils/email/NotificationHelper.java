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

package fr.wseduc.webutils.email;

import java.util.ArrayList;
import java.util.List;

import fr.wseduc.webutils.I18n;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import fr.wseduc.webutils.http.Renders;

import static fr.wseduc.webutils.DefaultAsyncResult.handleAsyncError;

public abstract class NotificationHelper implements SendEmail {

	protected final Renders render;
	protected static final Logger log = LoggerFactory.getLogger(NotificationHelper.class);
	protected final String senderEmail;
	protected final String host;

	public NotificationHelper(Vertx vertx, JsonObject config) {
		this.render = new Renders(vertx, config);
		final Object encodedEmailConfig = vertx.sharedData().getLocalMap("server").get("emailConfig");

		String defaultMail = "noreply@one1d.fr";
		String defaultHost = "http://localhost:8009";

		if(encodedEmailConfig != null){
			JsonObject emailConfig = new JsonObject(encodedEmailConfig.toString());
			defaultMail = emailConfig.getString("email", defaultMail);
			defaultHost = emailConfig.getString("host", defaultHost);
		}

		this.senderEmail = config.getString("email", defaultMail);
		this.host = config.getString("host", defaultHost);
	}

	public void sendEmail(HttpServerRequest request, String to, String cc, String bcc,
			String subject, String templateBody, JsonObject templateParams,
			boolean translateSubject, final Handler<AsyncResult<Message<JsonObject>>> handler) {
		sendEmail(request, to, senderEmail, cc, bcc, subject, templateBody,
				templateParams, translateSubject, null, handler);
	}

	public void sendEmail(HttpServerRequest request, String to, String cc, String bcc,
				   String subject, JsonArray attachments, String templateBody, JsonObject templateParams,
				   boolean translateSubject, final Handler<AsyncResult<Message<JsonObject>>> handler) {
		sendEmail(request, to, senderEmail, cc, bcc, subject, attachments, templateBody,
				templateParams, translateSubject, null, handler);
	}

	public void sendEmail(HttpServerRequest request, String to, String cc, String bcc,
			String subject, String templateBody, JsonObject templateParams,
			boolean translateSubject, JsonArray headers, final Handler<AsyncResult<Message<JsonObject>>> handler) {
		sendEmail(request, to, senderEmail, cc, bcc, subject, templateBody,
				templateParams, translateSubject, headers, handler);
	}

	public void sendEmail(HttpServerRequest request, String to, String from, String cc, String bcc,
			String subject, String templateBody, JsonObject templateParams,
			boolean translateSubject, final Handler<AsyncResult<Message<JsonObject>>> handler) {
		sendEmail(request, to, from, cc, bcc, subject, templateBody,
				templateParams, translateSubject, null, handler);
	}

	public void sendEmail(HttpServerRequest request, String to, String from, String cc, String bcc,
			String subject, String templateBody, JsonObject templateParams,
			boolean translateSubject, JsonArray headers, final Handler<AsyncResult<Message<JsonObject>>> handler) {
		sendEmail(request, to, senderEmail, cc, bcc, subject, null, templateBody,
				templateParams, translateSubject, headers, handler);
	}

	public void sendEmail(HttpServerRequest request, String to, String from, String cc, String bcc,
				   String subject, JsonArray attachments, String templateBody, JsonObject templateParams,
				   boolean translateSubject, JsonArray headers, final Handler<AsyncResult<Message<JsonObject>>> handler) {
		ArrayList<Object> toList = null;
		ArrayList<Object> ccList = null;
		ArrayList<Object> bccList = null;

		if(to != null){
			toList = new ArrayList<Object>();
			toList.add(to);
		}
		if(cc != null){
			ccList = new ArrayList<Object>();
			ccList.add(cc);
		}
		if(bcc != null){
			bccList = new ArrayList<Object>();
			bccList.add(bcc);
		}

		sendEmail(request, toList, senderEmail, ccList, bccList, subject, attachments, templateBody,
				templateParams, translateSubject, headers, handler);
	}

	public void sendEmail(HttpServerRequest request, List<Object> to, List<Object> cc, List<Object> bcc,
			String subject, String templateBody, JsonObject templateParams,
			boolean translateSubject, final Handler<AsyncResult<Message<JsonObject>>> handler) {
		sendEmail(request, to, senderEmail, cc, bcc, subject, templateBody,
				templateParams, translateSubject, null, handler);
	}

	public void sendEmail(HttpServerRequest request, List<Object> to, String from, List<Object> cc, List<Object> bcc,
						  String subject, String templateBody, JsonObject templateParams,
						  boolean translateSubject, JsonArray headers, final Handler<AsyncResult<Message<JsonObject>>> handler) {
		sendEmail(request, to, from, cc, bcc, subject, null, templateBody,
				templateParams, translateSubject, headers, handler);
	}

	public void sendEmail(HttpServerRequest request, List<Object> to, String from, List<Object> cc, List<Object> bcc,
						  String subject, JsonArray attachments, String templateBody, JsonObject templateParams,
						  boolean translateSubject, JsonArray headers, final Handler<AsyncResult<Message<JsonObject>>> handler) {
		final JsonObject json = new JsonObject()
			.put("to", new JsonArray(to))
			.put("from", from);

		if(cc != null){
			json.put("cc", new JsonArray(cc));
		}
		if(bcc != null){
			json.put("bcc", new JsonArray(bcc));
		}

		if(attachments != null){
			JsonArray attList = new JsonArray();
			for(Object o : attachments) {
				if(!(o instanceof JsonObject)) continue;
				JsonObject att = (JsonObject)o;
				if(att.getString("name") == null || att.getString("content") == null) continue;
				attList.add(att);
			}
			json.put("attachments", attList);
		}

		if (translateSubject) {
			json.put("subject", I18n.getInstance().translate(
					subject, getHost(request), I18n.acceptLanguage(request)));
		} else {
			json.put("subject", subject);
		}

		if(headers != null){
			json.put("headers", headers);
		}

		Handler<String> mailHandler = body -> {
			if (body != null) {
					json.put("body", body);
					NotificationHelper.this.sendEmail(json, handler);
			} else {
				log.error("Message is null.");
				handleAsyncError("Message is null.", handler);
			}
		};

		if(templateParams != null){
			render.processTemplate(request, templateBody, templateParams, mailHandler);
		} else {
			mailHandler.handle(templateBody);
		}
	}

	protected abstract void sendEmail(JsonObject json, Handler<AsyncResult<Message<JsonObject>>> handler);

	public String getSenderEmail() {
		return senderEmail;
	}

	public String getHost(HttpServerRequest request) {
		if (request == null) {
			return host;
		}
		return Renders.getScheme(request) + "://" + Renders.getHost(request);
	}

}
