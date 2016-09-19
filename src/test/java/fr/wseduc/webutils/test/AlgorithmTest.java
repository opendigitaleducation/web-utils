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

package fr.wseduc.webutils.test;

import fr.wseduc.webutils.data.ZLib;
import fr.wseduc.webutils.security.Blowfish;
import fr.wseduc.webutils.security.JWT;
import fr.wseduc.webutils.security.Md5;
import fr.wseduc.webutils.security.Sha256;
import org.junit.Test;
import io.vertx.core.json.JsonObject;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

import static org.junit.Assert.assertEquals;

public class AlgorithmTest {

	@Test
	public void hashMd5() throws NoSuchAlgorithmException {
		assertEquals("0127f712fc008f857e77a2f3f179c710", Md5.hash("Javarmi.com"));
	}

	@Test
	public void blowfishTest() throws GeneralSecurityException {
		final String data = "Lorem ipsum";
		final String key = "key-1234";
		String encryptedData = Blowfish.encrypt(data, key);
		String decryptedData = Blowfish.decrypt(encryptedData, key);
		assertEquals(data, decryptedData);
	}

	@Test
	public void hashSha256() throws NoSuchAlgorithmException {
		assertEquals("278cb091126f9b2eebdf8c008b53ec592e190e5b417a1f2fb5e5d7faf1d0b874", Sha256.hash("Javarmi.com"));
	}

	@Test
	public void decodeBase64UrlSafe() throws UnsupportedEncodingException {
		String decodedHeader = JWT.base64Decode("eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6InJlc3RvY29sbGVnZS1kZW1hdC52YWxk\nb2lzZS5mciJ9");
		String decodedPayload = JWT.base64Decode("eyJzdWIiOiI1NTU0ODIzNjAyIiwiZW1haWwiOiJpc2FiZWxsZS5hb2J6eW9AYWMtdmVyc2FpbGxl\ncy5mciIsIm5hbWUiOiJNYWRhbWUgUG9sb25pbyIsImlzcyI6Imh0dHBzOm9uZSIsImF1ZCI6InRl\nc3RvaWMiLCJpYXQiOjE0NzMxNDYyNTAsImV4cCI6MTQ3MzE0OTg1MH0=");
		System.out.println(decodedHeader);
		System.out.println(decodedPayload);
	}

	@Test
	public void verifyJWT() throws CertificateException, FileNotFoundException {
		final String token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6InJlc3RvY29sbGVnZS1kZW1hdC52YWxk\nb2lzZS5mciJ9.eyJzdWIiOiI1NTU0ODIzNjAyIiwiZW1haWwiOiJpc2FiZWxsZS5hb2J6eW9AYWMtdmVyc2FpbGxl\ncy5mciIsIm5hbWUiOiJNYWRhbWUgUG9sb25pbyIsImlzcyI6Imh0dHBzOm9uZSIsImF1ZCI6InRl\nc3RvaWMiLCJpYXQiOjE0NzMxNDYyNTAsImV4cCI6MTQ3MzE0OTg1MH0=.3a3EdflZf2hFbDUuQ0Dpnb1ihjYR1kMZJq7L6ip3jTcJSQ8WdyI9FMS7kWN9QDrD0qraFB_miYfd\nNyqBewuGzD1LvM2_bfHpm1AhzqNofQpH621q41ir3eua0wV8ouj_dxCUrayqTRfkerPZEpXUJ9VR\n3EWOFGGNdg6q9ptXlCNGMek2Gh1luK5JyfMpOK-sENSWRk-S_iuJ3xaZYiLtkVSRYDYakHaCFTNZ\n8DF3Oih7lVhfNKNWt5tqCyTdzI4-HiNNrLqjWrzc55kazdRUU3OXNscsC0XFxPNPSNoQN0zHseIr\nWqFdf0tDEc0xPzNuLSsudLTvB67RFrRnU1x0ifA5WSTeTgkHei4FAnKEdOFtrx-zRsCk7ka20VWk\nek659HDEWrkgZSuUqrqWVOG8RvVwdBsk_Pb-D4knV5qwH_JQCjj3QnRmm6wVUe1q8vUPCK8bkvKn\ngAs4cb3XtaAFD6oX1NF9qXKIgfA7fJCXZY-BtbYf_YnYFHV058DzUEz1Nr6xR6GVMBU61GwW9vz0\nmN3t4Al1h8dh2EPOPNCxA1LubFrxHS9wTnzpPjD5XlPFEE00yZaW-Z7410seSgRRvMgruzJdpONL\nzWI6af7AsC6FJBGfrZUYftC-0kBw165goV0P1DYS4pYQLEwWsXt_BRDybuijNUYUAx0n2gNnkAc=";
		final String crt = "/home/dboissin/Docs/certificats/capdemat/restocollege-demat.valdoise.fr/restocollege-demat.valdoise.fr.crt";
		CertificateFactory f = CertificateFactory.getInstance("X.509");
		Certificate certificate = f.generateCertificate(new FileInputStream(crt));
		PublicKey p = certificate.getPublicKey();
		JsonObject j = JWT.verifyAndGet(token, p);
		assertEquals("{\"sub\":\"5554823602\",\"email\":\"isabelle.aobzyo@ac-versailles.fr\",\"name\":\"Madame Polonio\",\"iss\":\"https:one\",\"aud\":\"testoic\",\"iat\":1473146250,\"exp\":1473149850}", j.encode());
	}

	@Test
	public void verifyJWTHS256() {
		final String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.TJVA95OrM7E2cBab30RMHrHDcEfxjoYZgeFONFh7HgQ";
		JsonObject j = JWT.verifyAndGet(token, "secret");
		assertEquals("{\"sub\":\"1234567890\",\"name\":\"John Doe\",\"admin\":true}", j.encode());
	}

	@Test
	public void deflateAndEncodeTest() throws IOException {
		final String content = "<test>bla</test>";
		assertEquals("sylJLS6xS8pJtNEHswA=", ZLib.deflateAndEncode(content));
	}

}
