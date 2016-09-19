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

import fr.wseduc.webutils.DefaultAsyncResult;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PersistantBuffer {

	private static final Logger log = LoggerFactory.getLogger(PersistantBuffer.class);
	private Buffer buffer;
	private final Vertx vertx;
	private final String filePath;
	private long length = 0l;
	private long writeLength = 0l;
	private int persistanceThreshold = 5 * 1024 * 1024;
	private Handler<Throwable> exceptionHandler;
	private boolean persisted = false;
	private boolean lock = false;
	private Buffer tmp = Buffer.buffer();
	private Set<Handler<AsyncResult<Buffer>>> waitGet = new HashSet<>();
	private AsyncFile f;

	public PersistantBuffer(Vertx vertx) {
		this(vertx, Buffer.buffer());
	}

	public PersistantBuffer(Vertx vertx, Buffer buffer) {
		this(vertx, buffer, UUID.randomUUID().toString());
	}

	public PersistantBuffer(Vertx vertx, Buffer buffer, String id) {
		this(vertx, buffer, id, System.getProperty("java.io.tmpdir"));
	}

	public PersistantBuffer(Vertx vertx, Buffer buffer, String id, String destination) {
		this.buffer = buffer;
		this.vertx = vertx;
		this.filePath = destination + File.separator + PersistantBuffer.class.getSimpleName() + "-" + id;
		this.length = buffer.length();
		if (buffer.length() > persistanceThreshold) {
			persist(new Handler<AsyncResult<Void>>() {
				@Override
				public void handle(AsyncResult<Void> ar) {
					if (ar.failed()) {
						log.error("Error persisting buffer", ar.cause());
					}
				}
			});
		}
	}

	public void persist() {
		persist(null);
	}

	@Override
	protected void finalize() throws Throwable {
		removeFile();
		super.finalize();
	}

	public void persist(final Handler<AsyncResult<Void>> handler) {
		final Handler<AsyncResult<Void>> h = new Handler<AsyncResult<Void>>() {
			@Override
			public void handle(AsyncResult<Void> ar) {
				writeLength += buffer.length();
				buffer = tmp;
				lock = false;
				tmp = Buffer.buffer();
				if (handler != null) {
					handler.handle(ar);
				}
				if (ar.failed() && exceptionHandler != null) {
					exceptionHandler.handle(ar.cause());
				}
				if (waitGet.size() > 0) {
					Set<Handler<AsyncResult<Buffer>>> twg = waitGet;
					waitGet = new HashSet<>();
					getBuffer(twg);
				}
			}
		};

		if (persisted) {
			if (f != null) {
				writeFile(h);
			} else {
				OpenOptions options = new OpenOptions().setRead(false).setCreateNew(false).setWrite(true);
				vertx.fileSystem().open(filePath, options, new Handler<AsyncResult<AsyncFile>>() {
					@Override
					public void handle(AsyncResult<AsyncFile> ar) {
						if (ar.succeeded()) {
							f = ar.result();
							writeFile(h);
						} else {
							if (handler != null) {
								handler.handle(new DefaultAsyncResult<Void>(ar.cause()));
							}
							if (exceptionHandler != null) {
								exceptionHandler.handle(ar.cause());
							}
						}
					}
				});
			}
		} else {
			lock = true;
			persisted = true;
			vertx.fileSystem().writeFile(filePath, buffer, h);
		}
	}

	private void writeFile(final Handler<AsyncResult<Void>> h) {
		f.write(buffer, writeLength, new Handler<AsyncResult<Void>>() {
			@Override
			public void handle(AsyncResult<Void> voidAsyncResult) {
				f.flush();
				h.handle(voidAsyncResult);
			}
		});
	}

	public void appendBuffer(Buffer b) {
		length += buffer.length();
		if (lock) {
			tmp.appendBuffer(b);
			return;
		}

		buffer.appendBuffer(b);
		if (buffer.length() > persistanceThreshold) {
			persist();
		}
	}

	public void getBuffer(final Set<Handler<AsyncResult<Buffer>>> handlers) {
		if (lock) {
			waitGet.addAll(handlers);
			return;
		}
		vertx.fileSystem().readFile(filePath, new Handler<AsyncResult<Buffer>>() {
			@Override
			public void handle(AsyncResult<Buffer> asyncResult) {
				final AsyncResult<Buffer> ar;
				if (asyncResult.succeeded()) {
					ar = new DefaultAsyncResult<>(asyncResult.result().appendBuffer(buffer));
				} else {
					ar = asyncResult;
				}
				for (Handler<AsyncResult<Buffer>> handler: handlers) {
					handler.handle(ar);
				}
			}
		});
	}

	public void getBuffer(final Handler<AsyncResult<Buffer>> handler) {
		if (lock) {
			waitGet.add(handler);
			return;
		}
		if (persisted) {
			vertx.fileSystem().readFile(filePath, new Handler<AsyncResult<Buffer>>() {
				@Override
				public void handle(AsyncResult<Buffer> asyncResult) {
					if (asyncResult.succeeded()) {
						handler.handle(new DefaultAsyncResult<>(asyncResult.result().appendBuffer(buffer)));
					} else {
						handler.handle(asyncResult);
					}
				}
			});
		} else {
			handler.handle(new DefaultAsyncResult<>(buffer));
		}
	}

	public long length() {
		return length;
	}

	public void clear() {
		removeFile();
		buffer = Buffer.buffer();
		tmp = Buffer.buffer();
		length = 0;
		writeLength = 0;
		persisted = false;
		lock = false;
		waitGet.clear();
	}

	private void removeFile() {
		if (f != null) {
			f.close(new Handler<AsyncResult<Void>>() {
				@Override
				public void handle(AsyncResult<Void> voidAsyncResult) {
					if (voidAsyncResult.failed()) {
						log.error("Error closing buffer file.");
					}
					f = null;
					vertx.fileSystem().delete(filePath, new Handler<AsyncResult<Void>>() {
						@Override
						public void handle(AsyncResult<Void> ar) {
							if (ar.failed()) {
								log.error("Error removing buffer.", ar.cause());
							}
						}
					});
				}
			});
		} else if (persisted) {
			vertx.fileSystem().delete(filePath, new Handler<AsyncResult<Void>>() {
				@Override
				public void handle(AsyncResult<Void> ar) {
					if (ar.failed()) {
						log.error("Error removing buffer.", ar.cause());
					}
				}
			});
		}
	}

	public void close(final Handler<AsyncResult<Void>> handler) {
		if (f != null) {
			f.close(new Handler<AsyncResult<Void>>() {
				@Override
				public void handle(AsyncResult<Void> voidAsyncResult) {
					f = null;
					if (handler != null) {
						handler.handle(voidAsyncResult);
					}
				}
			});
		}
	}

	public void exceptionHandler(Handler<Throwable> exceptionHandler) {
		this.exceptionHandler = exceptionHandler;
	}

	public int getPersistanceThreshold() {
		return persistanceThreshold;
	}

	public void setPersistanceThreshold(int persistanceThreshold) {
		this.persistanceThreshold = persistanceThreshold;
	}

}
