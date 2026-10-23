package org.openedit.util;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.KeySpec;
import java.util.Date;
import java.util.Properties;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openedit.Data;
import org.openedit.OpenEditException;
import org.openedit.WebPageRequest;
import org.openedit.data.Searcher;
import org.openedit.data.SearcherManager;
import org.openedit.users.User;

public class StringEncryption
{
	public static final String TIMESTAMP = "tstamp";

	private static final Log log = LogFactory.getLog(StringEncryption.class);
	// public static final String DESEDE_ENCRYPTION_SCHEME = "DESede";
	public static final String DES_ENCRYPTION_SCHEME = "DES";
	// public static final String DEFAULT_ENCRYPTION_KEY = "This is a fairly long phrase used to
	// encrypt";
	public static final String DEFAULT_ENCRYPTION_KEY = "7939805759879766427939805759879766";

	private final Charset UTF8_CHARSET = Charset.forName("UTF-8");

	protected String fieldEncryptionKey;
	protected KeySpec fieldKeySpec;
	protected SecretKeyFactory fieldKeyFactory;

	protected ThreadLocal<Cipher> fieldDecodePool = new ThreadLocal<Cipher>();
	protected ThreadLocal<Cipher> fieldEncodePool = new ThreadLocal<Cipher>();

	protected SecretKey fieldSecretKey;
	protected File fieldRootDirectory;
	protected String fieldSecretKeyName = "userpassword";

	protected SearcherManager fieldSearcherManager;

	public SearcherManager getSearcherManager()
	{
		return fieldSearcherManager;
	}

	public void setSearcherManager(SearcherManager inSearcherManager)
	{
		fieldSearcherManager = inSearcherManager;
	}

	public StringEncryption() {}

	/*
	 * public StringEncryption( String encryptionKey ) { try { setEncryptionKey(encryptionKey); } catch
	 * (Exception e) { throw new OpenEditRuntimeException(e); }
	 * 
	 * }
	 * 
	 * public void setEncryptionKey(String inEncryptionKey) {
	 * 
	 * if ( inEncryptionKey == null ) throw new IllegalArgumentException( "encryption key was null" );
	 * if ( inEncryptionKey.trim().length() < 24 ) throw new IllegalArgumentException(
	 * "encryption key was less than 24 characters" ); fieldEncryptionKey = inEncryptionKey;
	 * setKeySpec(null); }
	 */

	/**
	 * Computes RFC 2104-compliant HMAC signature. * @param data The data to be signed.
	 * 
	 * @param key The signing key.
	 * @return The Base64-encoded RFC 2104-compliant HMAC signature.
	 * @throws java.security.SignatureException when signature generation fails
	 */
	public String calculateRFC2104HMAC(String privatekey, String data)
	{
		String HMAC_SHA1_ALGORITHM = "HmacSHA1";
		// key = percent-encode(consumer_secret)+'&'+percent-encode(token_secret)
		// + "&"
		String key = privatekey + "&"; // No token

		byte[] result;
		try
		{
			// get an hmac_sha1 key from the raw key bytes
			SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(UTF8_CHARSET), HMAC_SHA1_ALGORITHM);

			// get an hmac_sha1 Mac instance and initialize with the signing key
			Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
			mac.init(signingKey);

			// compute the hmac on input data bytes
			byte[] rawHmac = mac.doFinal(data.getBytes(UTF8_CHARSET));

			// base64-encode the hmac
			org.apache.commons.codec.binary.Base64 base64encoder = new org.apache.commons.codec.binary.Base64();

			result = base64encoder.encode(rawHmac);
			return new String(result, UTF8_CHARSET);
		}
		catch (Exception e)
		{
			throw new OpenEditException("Failed to generate HMAC : " + e.getMessage(), e);
		}
	}

	public String getEncryptionKey()
	{
		if (fieldEncryptionKey == null)
		{
			//read .env file
			File envFile = new File(getRootDirectory(), "../.env");
			if (envFile.exists())
			{
				try (FileInputStream envReader = new FileInputStream(envFile))
				{
					Properties envProps = new Properties();
					envProps.load(envReader);
					String envKey = envProps.getProperty("PRIVATEKEY");
					if (envKey != null)
					{
						fieldEncryptionKey = envKey;
					}
				}
				catch (Throwable ex)
				{
					throw new OpenEditException(ex);
				}
			}
			
			if (fieldEncryptionKey == null)
			{
				fieldEncryptionKey = getEncryptionKey("autologincookiekey"); // 7939805759879766427939805759879766
			}
			if (fieldEncryptionKey == null)
			{
				fieldEncryptionKey = DEFAULT_ENCRYPTION_KEY;
			}
		}
		return fieldEncryptionKey;
	}

	public synchronized String encrypt(String unencryptedString) throws OpenEditException
	{
		if (unencryptedString.startsWith("DES:"))
		{
			return unencryptedString;
		}
		if (unencryptedString == null || unencryptedString.trim().length() == 0)
		{
			throw new IllegalArgumentException("unencrypted string was null or empty");
		}
		try
		{
			byte[] cleartext = unencryptedString.getBytes(UTF8_CHARSET);
			Cipher cipher = getEncodeCipher(); // Not thread safe

			byte[] ciphertext = cipher.doFinal(cleartext);

			Base64 base64encoder = new Base64();
			return "DES:" + new String(base64encoder.encode(ciphertext), UTF8_CHARSET);
		}
		catch (Exception e)
		{
			throw new OpenEditException(e);
		}
	}

	public SecretKey getSecretKey() throws Exception
	{
		if (fieldSecretKey == null)
		{
			fieldSecretKey = getKeyFactory().generateSecret(getKeySpec());
		}
		return fieldSecretKey;

	}

	public synchronized String decrypt(String encryptedString) throws OpenEditException
	{
		if (encryptedString == null || encryptedString.trim().length() <= 0)
			throw new IllegalArgumentException("encrypted string was null or empty");

		try
		{
			if (encryptedString.startsWith("DES:"))
			{
				encryptedString = encryptedString.substring(4);
			}
			if (encryptedString.startsWith("DES"))
			{
				encryptedString = encryptedString.substring(3);
			}
			byte[] ciphertext = decodeKey(encryptedString);

			return bytes2String(ciphertext);
		}
		catch (Exception e)
		{
			// throw new OpenEditException( e );
			log.error("Could not decrypt:" + encryptedString, e);
			return null;
		}
	}

	protected byte[] decodeKey(String encryptedString) throws Exception
	{
		Base64 base64decoder = new Base64();
		byte[] cleartext = base64decoder.decode(encryptedString.getBytes(UTF8_CHARSET));
		Cipher cipher = getDecodeCipher();
		byte[] ciphertext = null;
		try
		{
			ciphertext = cipher.doFinal(cleartext);
		}
		catch (Exception ex)
		{
			Cipher oldCipher = Cipher.getInstance(DES_ENCRYPTION_SCHEME);
			byte[] keyAsBytes = DEFAULT_ENCRYPTION_KEY.getBytes(UTF8_CHARSET);
			DESKeySpec oldSpec = new DESKeySpec(keyAsBytes);
			oldCipher.init(Cipher.DECRYPT_MODE, getKeyFactory().generateSecret(oldSpec));
			ciphertext = oldCipher.doFinal(cleartext);
		}
		return ciphertext;
	}

	// This seems wrong. Only works for ASCII. Why not just use new String().getBytes()?
	public static String bytes2String(byte[] bytes)
	{
		StringBuffer stringBuffer = new StringBuffer();
		for (int i = 0; i < bytes.length; i++)
		{
			stringBuffer.append((char) bytes[i]);
		}
		return stringBuffer.toString();
	}

	public Cipher getDecodeCipher()
	{
		try
		{
			Cipher w = fieldDecodePool.get();
			if (w == null)
			{
				w = Cipher.getInstance(DES_ENCRYPTION_SCHEME);
				w.init(Cipher.DECRYPT_MODE, getSecretKey());
				fieldDecodePool.set(w);
			}
			return w;
		}
		catch (Exception ex)
		{
			throw new OpenEditException(ex);
		}
	}

	public Cipher getEncodeCipher()
	{
		try
		{
			Cipher w = fieldEncodePool.get();
			if (w == null)
			{
				w = Cipher.getInstance(DES_ENCRYPTION_SCHEME);
				w.init(Cipher.ENCRYPT_MODE, getSecretKey());
				fieldEncodePool.set(w);
			}
			return w;
		}
		catch (Exception ex)
		{
			throw new OpenEditException(ex);
		}
	}

	public SecretKeyFactory getKeyFactory() throws Exception
	{
		if (fieldKeyFactory == null)
		{
			fieldKeyFactory = SecretKeyFactory.getInstance(DES_ENCRYPTION_SCHEME);
		}
		return fieldKeyFactory;
	}

	public void setKeyFactory(SecretKeyFactory inKeyFactory)
	{
		fieldKeyFactory = inKeyFactory;
	}

	public KeySpec getKeySpec() throws Exception
	{
		if (fieldKeySpec == null)
		{
			byte[] keyAsBytes = getEncryptionKey().getBytes(UTF8_CHARSET);

			fieldKeySpec = new DESKeySpec(keyAsBytes);

		}
		return fieldKeySpec;
	}

	public void setKeySpec(KeySpec inKeySpec)
	{
		fieldKeySpec = inKeySpec;
	}

	public File getRootDirectory()
	{
		return fieldRootDirectory;
	}

	public void setRootDirectory(File inRootDirectory)
	{
		fieldRootDirectory = inRootDirectory;
	}

	public String getSecretKeyName()
	{
		return fieldSecretKeyName;
	}

	public void setSecretKeyName(String inSecretKeyName)
	{
		fieldSecretKeyName = inSecretKeyName;
	}

	public String decryptIfNeeded(String inPassword)
	{
		if (inPassword != null)
		{
			if (inPassword.startsWith("DES"))
			{
				return decrypt(inPassword);
			}
		}
		return inPassword;
	}

	public String asMd5(String key)
	{
		try
		{
			MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
			digest.update(key.getBytes());
			byte[] md5 = digest.digest();

			StringBuffer hexString = new StringBuffer();
			for (int i = 0; i < md5.length; i++)
			{
				if (md5[i] <= 16)
				{
					hexString.append("0");
				}
				hexString.append(Integer.toHexString(0xFF & md5[i]));
			}
			return hexString.toString();
		}
		catch (Exception ex)
		{
			throw new OpenEditException(ex);
		}
	}

	// Remove all cookies for the user. This is used when they log out. We want to make sure they are
	// really logged out and not just lose the session cookie
	public void removeAllCookies(WebPageRequest inReq)
	{
		HttpServletRequest req = inReq.getRequest();
		if (req != null)
		{
			Cookie[] cookies = req.getCookies();
			if (cookies != null)
			{
				HttpServletResponse res = inReq.getResponse();
				if (res != null)
				{
					for (Cookie cookie : cookies)
					{
						cookie.setMaxAge(0);
						cookie.setPath("/"); // http://www.unix.org.ua/orelly/java-ent/servlet/ch07_04.htm
						res.addCookie(cookie);
					}
				}
			}
		}
	}

	public void removeCookie(WebPageRequest inReq, String key)
	{
		HttpServletResponse res = inReq.getResponse();
		if (res != null)
		{
			Cookie cookie = new Cookie(key, "none");
			cookie.setMaxAge(0);
			cookie.setPath("/"); // http://www.unix.org.ua/orelly/java-ent/servlet/ch07_04.htm
			res.addCookie(cookie);

		}
	}

	// TODO: Cache this
	public String getEncryptionKey(String inType)
	{
		if (fieldSearcherManager == null)
		{
			return null;
		}
		String key = (String) getSearcherManager().getCacheManager().get("getEncryptionKey", inType);
		if (key == null)
		{
			Searcher searcher = getSearcherManager().getSearcher("system", "systemsettings");
			Data found = searcher.query().match("id", inType).searchOne();

			if (found != null)
			{
				key = found.get("value");
			}
			getSearcherManager().getCacheManager().put("getEncryptionKey", inType, key);
		}
		return key;
	}

	public String randomCharacterString()
	{
		return java.util.UUID.randomUUID().toString().replace("-", "");
	}

	// ---------------------------------------------------------------------------------------------
	// Public/private key support (SSH style). The client keeps the private key and signs a
	// challenge; the server only ever stores the public key and verifies the signature.
	// ---------------------------------------------------------------------------------------------

	public static final String KEY_ALGORITHM = "Ed25519";
	public static final String ACCESS_TOKEN_MARKER = "keyexp";
	public static final long SIGNATURE_WINDOW_MILLIS = 5L * 60L * 1000L;
	public static final long ACCESS_TOKEN_LIFETIME_MILLIS = 7L * 24L * 60L * 60L * 1000L;

	protected final java.util.concurrent.ConcurrentHashMap<String, Long> fieldUsedNonces = new java.util.concurrent.ConcurrentHashMap<String, Long>();

	/**
	 * @return {publicKey, privateKey} as unpadded base64url strings (X.509 and PKCS#8 encodings)
	 */
	public String[] generateKeyPair()
	{
		try
		{
			java.security.KeyPair pair = java.security.KeyPairGenerator.getInstance(KEY_ALGORITHM).generateKeyPair();
			java.util.Base64.Encoder enc = java.util.Base64.getUrlEncoder().withoutPadding();
			return new String[] { enc.encodeToString(pair.getPublic().getEncoded()), enc.encodeToString(pair.getPrivate().getEncoded()) };
		}
		catch (Exception e)
		{
			throw new OpenEditException("Failed to generate key pair: " + e.getMessage(), e);
		}
	}

	/**
	 * Public keys are base64url X.509 (SubjectPublicKeyInfo). Ed25519 is preferred; ECDSA P-256 is accepted for
	 * browsers whose WebCrypto lacks Ed25519.
	 */
	protected java.security.PublicKey decodePublicKey(String inPublicKey) throws Exception
	{
		byte[] der = java.util.Base64.getUrlDecoder().decode(inPublicKey);
		try
		{
			return java.security.KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(new java.security.spec.X509EncodedKeySpec(der));
		}
		catch (java.security.spec.InvalidKeySpecException notEd25519)
		{
			java.security.PublicKey key = java.security.KeyFactory.getInstance("EC").generatePublic(new java.security.spec.X509EncodedKeySpec(der));
			java.security.spec.ECParameterSpec params = ((java.security.interfaces.ECPublicKey) key).getParams();
			if (params.getCurve().getField().getFieldSize() != 256)
			{
				throw new java.security.spec.InvalidKeySpecException("Only P-256 is supported");
			}
			return key;
		}
	}

	/**
	 * @return "Ed25519" or "P-256", worked out from the key itself; null if it is not a supported public key
	 */
	public String getPublicKeyAlgorithm(String inPublicKey)
	{
		try
		{
			return "EC".equals(decodePublicKey(inPublicKey).getAlgorithm()) ? "P-256" : KEY_ALGORITHM;
		}
		catch (Exception e)
		{
			return null;
		}
	}

	public boolean isValidPublicKey(String inPublicKey)
	{
		try
		{
			decodePublicKey(inPublicKey);
			return true;
		}
		catch (Exception e)
		{
			return false;
		}
	}

	/**
	 * Client side helper (and used by tests): sign data with an Ed25519 private key from generateKeyPair.
	 */
	public String sign(String inPrivateKey, String inData)
	{
		try
		{
			java.security.KeyFactory factory = java.security.KeyFactory.getInstance(KEY_ALGORITHM);
			java.security.PrivateKey key = factory.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(java.util.Base64.getUrlDecoder().decode(inPrivateKey)));
			java.security.Signature sig = java.security.Signature.getInstance(KEY_ALGORITHM);
			sig.initSign(key);
			sig.update(inData.getBytes(UTF8_CHARSET));
			return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign());
		}
		catch (Exception e)
		{
			throw new OpenEditException("Failed to sign: " + e.getMessage(), e);
		}
	}

	/**
	 * @return true only if inSignature is a valid signature of inData by the private key matching inPublicKey.
	 *         Ed25519 signatures are the raw 64 bytes; P-256 signatures are WebCrypto's raw r||s (IEEE P1363).
	 */
	public boolean verifySignature(String inPublicKey, String inData, String inSignature)
	{
		if (inPublicKey == null || inData == null || inSignature == null)
		{
			return false;
		}
		try
		{
			java.security.PublicKey key = decodePublicKey(inPublicKey);
			java.security.Signature sig = java.security.Signature.getInstance("EC".equals(key.getAlgorithm()) ? "SHA256withECDSAinP1363Format" : KEY_ALGORITHM);
			sig.initVerify(key);
			sig.update(inData.getBytes(UTF8_CHARSET));
			return sig.verify(java.util.Base64.getUrlDecoder().decode(inSignature));
		}
		catch (Exception e)
		{
			log.info("Signature verification failed: " + e.getMessage());
			return false;
		}
	}

	/**
	 * The exact string a client must sign to request a token.
	 */
	public String createSignedMessage(String inUserId, String inKeyId, String inTimestamp, String inNonce)
	{
		return inUserId + "\n" + inKeyId + "\n" + inTimestamp + "\n" + inNonce;
	}

	/**
	 * Verifies a signed login request: timestamp inside the allowed window, signature valid for the
	 * registered public key, and nonce never seen before. The nonce is only consumed once the signature
	 * is valid, so garbage requests cannot fill the replay cache.
	 */
	public boolean verifyKeyLogin(String inUserId, String inKeyId, String inPublicKey, String inTimestamp, String inNonce, String inSignature)
	{
		if (inUserId == null || inKeyId == null || inPublicKey == null || inTimestamp == null || inNonce == null || inSignature == null || inNonce.length() < 16 || inNonce.length() > 128)
		{
			return false;
		}
		long now = System.currentTimeMillis();
		long ts;
		try
		{
			ts = Long.parseLong(inTimestamp);
		}
		catch (NumberFormatException e)
		{
			return false;
		}
		if (Math.abs(now - ts) > SIGNATURE_WINDOW_MILLIS)
		{
			log.info("Key login timestamp outside allowed window for " + inUserId);
			return false;
		}
		if (!verifySignature(inPublicKey, createSignedMessage(inUserId, inKeyId, inTimestamp, inNonce), inSignature))
		{
			return false;
		}
		fieldUsedNonces.values().removeIf(t -> now - t > 2 * SIGNATURE_WINDOW_MILLIS);
		if (fieldUsedNonces.putIfAbsent(inKeyId + ":" + inNonce, now) != null)
		{
			log.info("Key login nonce replayed for " + inUserId);
			return false;
		}
		return true;
	}

	protected String hmacAccessToken(String inUserId, String inKeyId, String inExpiry, String inPublicKey)
	{
		try
		{
			// Bound to the public key as well, so a replaced key can never validate an old token
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(getEncryptionKey().getBytes(UTF8_CHARSET), "HmacSHA256"));
			mac.update((inUserId + "\n" + inKeyId + "\n" + inExpiry + "\n" + inPublicKey).getBytes(UTF8_CHARSET));
			return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal());
		}
		catch (Exception e)
		{
			throw new OpenEditException(e);
		}
	}

	/**
	 * Stateless bearer token: {userid}keyexp{keyid}.{expiresAtMillis}.{hmac}
	 */
	public String createAccessToken(String inUserId, String inKeyId, String inPublicKey, long inLifetimeMillis)
	{
		String expiry = String.valueOf(System.currentTimeMillis() + inLifetimeMillis);
		return inUserId + ACCESS_TOKEN_MARKER + inKeyId + "." + expiry + "." + hmacAccessToken(inUserId, inKeyId, expiry, inPublicKey);
	}

	// ---------------------------------------------------------------------------------------------
	// Application passwords: a random secret for tools that cannot sign (curl, scripts). Shown once;
	// only its SHA-256 is stored. 256 bits of randomness, so a fast hash is enough. Format:
	// emeapp.{keyid}.{secret}
	// ---------------------------------------------------------------------------------------------

	public static final String APP_PASSWORD_PREFIX = "emeapp.";

	public String generateAppSecret()
	{
		byte[] bytes = new byte[32];
		new java.security.SecureRandom().nextBytes(bytes);
		return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public String createAppPassword(String inKeyId, String inSecret)
	{
		return APP_PASSWORD_PREFIX + inKeyId + "." + inSecret;
	}

	public boolean isAppPassword(String inToken)
	{
		return inToken != null && inToken.startsWith(APP_PASSWORD_PREFIX);
	}

	/**
	 * @return {keyid, secret} or null if malformed
	 */
	public String[] parseAppPassword(String inToken)
	{
		if (!isAppPassword(inToken))
		{
			return null;
		}
		String[] parts = inToken.substring(APP_PASSWORD_PREFIX.length()).split("\\.");
		return parts.length == 2 ? parts : null;
	}

	public boolean verifyAppSecret(String inSecret, String inStoredHash)
	{
		if (inSecret == null || inStoredHash == null)
		{
			return false;
		}
		return MessageDigest.isEqual(getSha256(inSecret).getBytes(UTF8_CHARSET), inStoredHash.getBytes(UTF8_CHARSET));
	}

	public boolean isAccessToken(String inToken)
	{
		return inToken != null && inToken.contains(ACCESS_TOKEN_MARKER);
	}

	/**
	 * @return {userid, keyid, expiry, hmac} or null if the token is malformed
	 */
	public String[] parseAccessToken(String inToken)
	{
		if (!isAccessToken(inToken))
		{
			return null;
		}
		int index = inToken.lastIndexOf(ACCESS_TOKEN_MARKER);
		String[] rest = inToken.substring(index + ACCESS_TOKEN_MARKER.length()).split("\\.");
		if (index <= 0 || rest.length != 3)
		{
			return null;
		}
		return new String[] { inToken.substring(0, index), rest[0], rest[1], rest[2] };
	}

	/**
	 * Checks expiry and HMAC. The caller must already have loaded the key record named in the token and
	 * confirmed it is enabled and belongs to the user.
	 */
	public boolean verifyAccessToken(String[] inParsed, String inPublicKey)
	{
		if (inParsed == null || inPublicKey == null)
		{
			return false;
		}
		try
		{
			if (System.currentTimeMillis() > Long.parseLong(inParsed[2]))
			{
				return false;
			}
		}
		catch (NumberFormatException e)
		{
			return false;
		}
		byte[] expected = hmacAccessToken(inParsed[0], inParsed[1], inParsed[2], inPublicKey).getBytes(UTF8_CHARSET);
		return MessageDigest.isEqual(expected, inParsed[3].getBytes(UTF8_CHARSET));
	}

	public String generateHashFromString(String inInput, int length)
	{
		try
		{
			// Use MD5 to hash the URL
			MessageDigest digest = MessageDigest.getInstance("MD5");
			digest.update(inInput.getBytes(UTF8_CHARSET));
			byte[] hashBytes = digest.digest();

			// Convert the hash to a hex string
			StringBuilder hashString = new StringBuilder();
			for (byte b : hashBytes)
			{
				hashString.append(String.format("%02x", b));
			}

			// Return the shortened hash
			return hashString.substring(0, Math.min(length, hashString.length()));
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new OpenEditException("Failed to generate hash for URL: " + e.getMessage(), e);
		}
	}

	public String getSha256(String input)
	{
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] encodedHash = digest.digest(input.getBytes(UTF8_CHARSET));

			StringBuilder hexString = new StringBuilder();
			for (byte b : encodedHash)
			{
				String hex = Integer.toHexString(0xff & b);
				if (hex.length() == 1)
					hexString.append('0');
				hexString.append(hex);
			}
			return hexString.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new OpenEditException("SHA-256 hashing failed: " + e.getMessage(), e);
		}
	}

}
