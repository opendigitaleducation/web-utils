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


import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Base64;

public class Blowfish {

	public static String encrypt(String data, String key) throws GeneralSecurityException {
		byte[] keyData = key.getBytes();
		SecretKeySpec secretKeySpec = new SecretKeySpec(keyData, "Blowfish");
		Cipher cipher = Cipher.getInstance("Blowfish");
		cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
		byte[] b = cipher.doFinal(data.getBytes());
		return Base64.getEncoder().encodeToString(b);
	}

	public static String decrypt(String data, String key) throws GeneralSecurityException {
		byte[] keyData = key.getBytes();
		SecretKeySpec secretKeySpec = new SecretKeySpec(keyData, "Blowfish");
		Cipher cipher = Cipher.getInstance("Blowfish");
		cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
		byte[] b = cipher.doFinal(Base64.getDecoder().decode(data));
		return new String(b);
	}

}