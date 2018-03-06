/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.busmods;

import fr.wseduc.webutils.data.FileResolver;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Base helper class for Java modules which use the event bus.<p>
 * You don't have to use this class but it contains some useful functionality.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BusModBase extends AbstractVerticle {

	protected EventBus eb;
	protected JsonObject config;
	protected static final Logger logger = LoggerFactory.getLogger(BusModBase.class);


	/**
	 * Start the busmod
	 */
	public void start() {
		eb = vertx.eventBus();
		config = config();
		FileResolver.getInstance().setBasePath(config);
	}

	protected void sendOK(Message<JsonObject> message) {
		sendOK(message, null);
	}

	protected void sendStatus(String status, Message<JsonObject> message) {
		sendStatus(status, message, null);
	}

	protected void sendStatus(String status, Message<JsonObject> message, JsonObject json) {
		if (json == null) {
			json = new JsonObject();
		}
		json.put("status", status);
		message.reply(json);
	}

	protected void sendOK(Message<JsonObject> message, JsonObject json) {
		sendStatus("ok", message, json);
	}

	protected void sendError(Message<JsonObject> message, String error) {
		sendError(message, error, null);
	}

	protected void sendError(Message<JsonObject> message, String error, Exception e) {
		logger.error(error, e);
		JsonObject json = new JsonObject().put("status", "error").put("message", error);
		message.reply(json);
	}

	protected String getMandatoryString(String field, Message<JsonObject> message) {
		String val = message.body().getString(field);
		if (val == null) {
			sendError(message, field + " must be specified");
		}
		return val;
	}

	protected JsonObject getMandatoryObject(String field, Message<JsonObject> message) {
		JsonObject val = message.body().getJsonObject(field);
		if (val == null) {
			sendError(message, field + " must be specified");
		}
		return val;
	}

	protected boolean getOptionalBooleanConfig(String fieldName, boolean defaultValue) {
		Boolean b = config.getBoolean(fieldName);
		return b == null ? defaultValue : b.booleanValue();
	}

	protected String getOptionalStringConfig(String fieldName, String defaultValue) {
		String s = config.getString(fieldName);
		return s == null ? defaultValue : s;
	}

	protected int getOptionalIntConfig(String fieldName, int defaultValue) {
		return config.getInteger(fieldName, defaultValue);
	}

	protected long getOptionalLongConfig(String fieldName, long defaultValue) {
		return config.getLong(fieldName, defaultValue);
	}

	protected JsonObject getOptionalObjectConfig(String fieldName, JsonObject defaultValue) {
		return config.getJsonObject(fieldName, defaultValue);
	}

	protected JsonArray getOptionalArrayConfig(String fieldName, JsonArray defaultValue) {
		return config.getJsonArray(fieldName, defaultValue);
	}

	protected boolean getMandatoryBooleanConfig(String fieldName) {
		Boolean b = config.getBoolean(fieldName);
		if (b == null) {
			throw new IllegalArgumentException(fieldName + " must be specified in config for busmod");
		}
		return b;
	}

	protected String getMandatoryStringConfig(String fieldName) {
		String s = config.getString(fieldName);
		if (s == null) {
			throw new IllegalArgumentException(fieldName + " must be specified in config for busmod");
		}
		return s;
	}

	protected int getMandatoryIntConfig(String fieldName) {
		Integer i = config.getInteger(fieldName);
		if (i == null) {
			throw new IllegalArgumentException(fieldName + " must be specified in config for busmod");
		}
		return i;
	}

	protected long getMandatoryLongConfig(String fieldName) {
		Long l = config.getLong(fieldName);
		if (l == null) {
			throw new IllegalArgumentException(fieldName + " must be specified in config for busmod");
		}
		return l;
	}

}
