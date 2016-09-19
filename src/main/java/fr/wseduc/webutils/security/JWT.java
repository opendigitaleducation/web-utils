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

package fr.wseduc.webutils.security;


import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static fr.wseduc.webutils.Utils.isEmpty;
import static fr.wseduc.webutils.Utils.isNotEmpty;

public final class JWT {

	private static final Logger log = LoggerFactory.getLogger(JWT.class);
	private String secret;
	private HttpClient httpClient;
	private String certsPath;
	private final ConcurrentMap<String, PublicKey> certificates = new ConcurrentHashMap<>();
	private final List<Key> privateKeys = new ArrayList<>();

	private class Key {
		private final String kid;
		private final PrivateKey privateKey;

		private Key(String kid, PrivateKey privateKey) {
			this.kid = kid;
			this.privateKey = privateKey;
		}
	}

	private enum Algorithm {
		RS256("SHA256withRSA"), RS384("SHA384withRSA"), RS512("SHA512withRSA"), HS256("HmacSHA256");

		private final String algo;

		private Algorithm(String s) {
			algo = s;
		}

		public String getAlgo(){
			return algo;
		}

	}

	public JWT(Vertx vertx, URI certsUri) {
		this(vertx, null, certsUri);
	}

	public JWT(Vertx vertx, String secret, URI certsUri) {
		if (certsUri != null) {
			HttpClientOptions options = new HttpClientOptions()
					.setDefaultHost(certsUri.getHost())
					.setDefaultPort(certsUri.getPort())
					.setSsl("https".equals(certsUri.getScheme()))
					.setMaxPoolSize(4)
					.setKeepAlive(false);
			this.httpClient = vertx.createHttpClient(options);
			this.certsPath = certsUri.getPath();
			findCertificates(null);
		}
		this.secret = secret;
	}

	public JWT(final Vertx vertx, String keysPath) {
		httpClient = null;
		certsPath = null;
		loadPrivateKeys(vertx, keysPath);
	}

	public static void listCertificates(final Vertx vertx, String certsPath, final Handler<JsonObject> handler) {
		final JsonObject certs = new JsonObject();
		vertx.fileSystem().readDir(certsPath, ".*.crt", new Handler<AsyncResult<List<String>>>() {
			@Override
			public void handle(AsyncResult<List<String>> ar) {
				if (ar.succeeded()) {
					final AtomicInteger count = new AtomicInteger(ar.result().size());
					for (final String certificate: ar.result()) {
						vertx.fileSystem().readFile(certificate, new Handler<AsyncResult<Buffer>>() {
							@Override
							public void handle(AsyncResult<Buffer> asyncResult) {
								if (asyncResult.succeeded()) {
									int idx = certificate.lastIndexOf(File.separator);
									String crtName = (idx > -1) ? certificate.substring(idx + 1) : certificate;
									crtName = crtName.substring(0, crtName.lastIndexOf("."));
									certs.put(crtName, asyncResult.result().toString());
								} else {
									log.error("Error reading certificate : " + certificate, asyncResult.cause());
								}
								if (count.decrementAndGet() == 0) {
									handler.handle(certs);
								}
							}
						});
					}
				} else {
					log.error("Error load JWT private keys", ar.cause());
					handler.handle(certs);
				}
			}
		});
	}

	private void loadPrivateKeys(final Vertx vertx, String keysPath) {
		vertx.fileSystem().readDir(keysPath, ".*.pk8", new Handler<AsyncResult<List<String>>>() {
			@Override
			public void handle(AsyncResult<List<String>> ar) {
				if (ar.succeeded()) {
					for (final String privateKey: ar.result()) {
						vertx.fileSystem().readFile(privateKey, new Handler<AsyncResult<Buffer>>() {
							@Override
							public void handle(AsyncResult<Buffer> asyncResult) {
								if (asyncResult.succeeded()) {
									int idx = privateKey.lastIndexOf(File.separator);
									String keyName = (idx > -1) ? privateKey.substring(idx + 1) : privateKey;
									keyName = keyName.substring(0, keyName.lastIndexOf("."));
									KeySpec privateKeySpec = new PKCS8EncodedKeySpec(asyncResult.result().getBytes());
									try {
										privateKeys.add(new Key(keyName,
												KeyFactory.getInstance("RSA").generatePrivate(privateKeySpec)));
									} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
										log.error("Error loading private key : " + privateKey, e);
									}
								} else {
									log.error("Error reading private key : " + privateKey, asyncResult.cause());
								}
							}
						});
					}
				} else {
					log.error("Error load JWT private keys", ar.cause());
				}
			}
		});
	}

	private void findCertificates(final Handler<Void> handler) {
		httpClient.getNow(certsPath, new Handler<HttpClientResponse>() {
			@Override
			public void handle(HttpClientResponse response) {
				if (response.statusCode() == 200) {
					response.bodyHandler(new Handler<Buffer>() {
						@Override
						public void handle(Buffer buffer) {
							JsonObject c =  new JsonObject(buffer.toString("UTF-8"));
							try {
								CertificateFactory f = CertificateFactory.getInstance("X.509");
								for (String a : c.fieldNames()) {
									String cert = c.getString(a);
									if (cert != null) {
										try {
											Certificate certificate = f.generateCertificate(
													new ByteArrayInputStream(cert.getBytes("UTF-8")));
											PublicKey p = certificate.getPublicKey();
											certificates.putIfAbsent(a, p);
										} catch (CertificateException | UnsupportedEncodingException e) {
											log.error(e.getMessage(), e);
										}
									}
								}
							} catch (CertificateException e) {
								log.error(e.getMessage(), e);
							} finally {
								if (handler != null) {
									handler.handle(null);
								}
							}
						}
					});
				} else if (handler != null) {
					handler.handle(null);
				}
			}
		});
	}

	public static String base64Decode(String s) throws UnsupportedEncodingException {
		return new String(base64DecodeToByte(s), "UTF-8");
	}

	public static byte[] base64DecodeToByte(String s) {
		int repeat = 4 - (s.length() % 4);
		StringBuilder b = new StringBuilder("");
		for (int i = 0; i < repeat; i++) {
			b.append("=");
		}
		return Base64.getUrlDecoder().decode(s + b.toString());
	}

	public static String base64Encode(String s) throws UnsupportedEncodingException {
		return base64Encode(s.getBytes("UTF-8"));
	}

	public static String base64Encode(byte[] bytes) throws UnsupportedEncodingException {
		return Base64.getUrlEncoder().encodeToString(bytes);
	}

	public void verifyAndGet(final String token, final Handler<JsonObject> handler) {
		String[] t = token.split("\\.");
		if (t.length != 3) {
			handler.handle(null);
			return;
		}
		JsonObject header;
		try {
			header = new JsonObject(base64Decode(t[0]));
		} catch (UnsupportedEncodingException e) {
			log.error(e.getMessage(), e);
			handler.handle(null);
			return;
		}
		switch (Algorithm.valueOf(header.getString("alg"))) {
			case RS256:
			case RS384:
			case RS512:
				final String kid = header.getString("kid");
				if (kid != null) {
					PublicKey publicKey = certificates.get(kid);
					if (publicKey == null) {
						findCertificates(new Handler<Void>() {
							@Override
							public void handle(Void v) {
								handler.handle(verifyAndGet(token, certificates.get(kid)));
							}
						});
					} else {
						handler.handle(verifyAndGet(token, publicKey));
					}
				} else {
					log.error("missing key id");
					handler.handle(null);
				}
			break;
			case HS256:
				handler.handle(verifyAndGet(token, secret));
				break;
			default:
				log.error("Unsupported signature algorithm.");
		}

	}

	public static JsonObject verifyAndGet(String token, PublicKey publicKey) {
		log.debug(token);
		String[] t = token.split("\\.");
		if (t.length != 3 || publicKey == null) {
			return null;
		}
		try {
			JsonObject header = new JsonObject(base64Decode(t[0]));
			JsonObject payload = new JsonObject(base64Decode(t[1]));
			byte[] signature = base64DecodeToByte(t[2]);
			Signature sign = Signature.getInstance(Algorithm.valueOf(header.getString("alg")).getAlgo());
			sign.initVerify(publicKey);
			sign.update((t[0] + "." + t[1]).getBytes("UTF-8"));
			if (sign.verify(signature)) {
				return payload;
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}

	public static JsonObject verifyAndGet(String token, String secret) {
		log.debug(token);
		final String[] t = token.split("\\.");
		if (t.length != 3 || isEmpty(secret)) {
			return null;
		}
		try {
			final JsonObject header = new JsonObject(base64Decode(t[0]));
			final JsonObject payload = new JsonObject(base64Decode(t[1]));
			final String algo = Algorithm.valueOf(header.getString("alg")).getAlgo();
			final SecretKeySpec signingKey = new SecretKeySpec(secret.getBytes(), algo);
			final Mac mac = Mac.getInstance(algo);
			mac.init(signingKey);
			byte[] signed = mac.doFinal((t[0] + "." + t[1]).getBytes("UTF-8"));
			byte[] signature = base64DecodeToByte(t[2]);
			if (Arrays.equals(signature, signed)) {
				return payload;
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}

	public String encodeAndSign(JsonObject payload) throws Exception {
		final Key k = privateKeys.get(0);
		return encodeAndSign(payload, k.kid, k.privateKey);
	}

	public static String encodeAndSign(JsonObject payload, String kid, PrivateKey privateKey) throws Exception {
		final JsonObject header = new JsonObject().put("typ", "JWT").put("alg", "RS256");
		if (isNotEmpty(kid)) {
			header.put("kid", kid);
		}
		final StringBuilder sb = new StringBuilder();
		sb.append(base64Encode(header.encode())).append(".").append(base64Encode(payload.encode()));
		Signature sign = Signature.getInstance("SHA256withRSA");
		sign.initSign(privateKey);
		sign.update(sb.toString().getBytes("UTF-8"));
		sb.append(".").append(base64Encode(sign.sign()));
		return sb.toString();
	}

}
