package bitverify.crypto;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.encodings.OAEPEncoding;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;

public class Asymmetric {
	
	private static final int RSAKeySize = 4096;
	
	private static byte[] _encryptBytes(boolean isEncrypting, byte[] data, AsymmetricKeyParameter key) throws InvalidCipherTextException{
		RSAEngine engine = new RSAEngine();
		OAEPEncoding cipher = new OAEPEncoding(engine);
		cipher.init(isEncrypting, key);
		return cipher.processBlock(data, 0, data.length);
	}
	
	public static byte[] encryptBytes(byte[] data, AsymmetricKeyParameter key) throws InvalidCipherTextException, DataSizeException{
		if (data.length >= (RSAKeySize-384)/8+7){
			throw new DataSizeException("data too long to encrypt with RSA, use symmetric encryption instead");
		}
		return _encryptBytes(true, data, key);
	}
	
	public static byte[] decryptBytes(byte[] data, AsymmetricKeyParameter key) throws InvalidCipherTextException{
		return _encryptBytes(false, data, key);
	}
	
	public static String encryptHexString(String hexString, AsymmetricKeyParameter key) throws InvalidCipherTextException, DataSizeException{
		byte[] data = Hex.decode(hexString);
		data = encryptBytes(data,key);
		return Hex.toHexString(data);
	}
	
	public static String decryptHexString(String hexString, AsymmetricKeyParameter key) throws InvalidCipherTextException{
		byte[] data = Hex.decode(hexString);
		data = decryptBytes(data,key);
		return Hex.toHexString(data);
	}
	
	public static String encryptBase64String(String base64String, AsymmetricKeyParameter key) throws InvalidCipherTextException, DataSizeException{
		byte[] data = Base64.decode(base64String);
		data = encryptBytes(data,key);
		return Base64.toBase64String(data);
	}
	
	public static String decryptBase64String(String base64String, AsymmetricKeyParameter key) throws InvalidCipherTextException{
		byte[] data = Base64.decode(base64String);
		data = decryptBytes(data,key);
		return Base64.toBase64String(data);
	}
	
	/**
	 * Generates a strong new RSA key pair.
	 * Takes cca. 5-10 seconds.
	 */
	public static AsymmetricCipherKeyPair generateNewKeyPair(){
		
		SecureRandom randomNumberGenerator;
		try {
			randomNumberGenerator = SecureRandom.getInstance("SHA1PRNG");
		} catch (NoSuchAlgorithmException e) {
			randomNumberGenerator = new SecureRandom();
		}
		
		RSAKeyGenerationParameters keyParams = new RSAKeyGenerationParameters
				(
			        new BigInteger("10001", 16), //publicExponent
			        randomNumberGenerator, //prng
			        RSAKeySize, //strength
			        80 //certainty
			    );
		
		RSAKeyPairGenerator keyPairGenerator = new RSAKeyPairGenerator();
		keyPairGenerator.init(keyParams);
		
		AsymmetricCipherKeyPair keyPair = keyPairGenerator.generateKeyPair();
		return keyPair;
	}
	
	public static AsymmetricCipherKeyPair getKeyPairFromKeys(AsymmetricKeyParameter publicKey, AsymmetricKeyParameter privateKey){
		return new AsymmetricCipherKeyPair(publicKey, privateKey);
	}
	
	public static AsymmetricCipherKeyPair getKeyPairFromStringKeys(String publicKey, String privateKey) throws KeyDecodingException{
		return new AsymmetricCipherKeyPair(stringKeyToKey(publicKey), stringKeyToKey(privateKey));
	}
	
	/**
	 * Convert AsymmetricKeyParameter to string in PKCS#8 format.
	 * Returned string can be saved on disk with .der extension.
	 */
	public static String keyToStringKey(AsymmetricKeyParameter key) throws KeyDecodingException{
		String codePrefix, codePostfix, key_multiline;
		try {
			if (key.isPrivate()){
				PrivateKeyInfo privKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(key);
				byte[] key_bytes = privKeyInfo.getEncoded(); //to get PKCS#8
				//byte[] key_bytes = privKeyInfo.parsePrivateKey().toASN1Primitive().getEncoded(); //to get PKCS#1
				key_multiline = convertKeyToMultiLineFromBytes(key_bytes);
				codePrefix = "-----BEGIN RSA PRIVATE KEY-----\n";
				codePostfix = "\n-----END RSA PRIVATE KEY-----";
			} else {
				SubjectPublicKeyInfo pubKeyInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(key);
				byte[] key_bytes = pubKeyInfo.getEncoded(); //to get PKCS#8
				//byte[] key_bytes = pubKeyInfo.parsePublicKey().toASN1Primitive().getEncoded(); //to get PKCS#1
				key_multiline = convertKeyToMultiLineFromBytes(key_bytes);
				codePrefix = "-----BEGIN RSA PUBLIC KEY-----\n"; 
				codePostfix = "\n-----END RSA PUBLIC KEY-----";
			}
		} catch (IOException e){
			throw new KeyDecodingException();
		}
		return (codePrefix + key_multiline + codePostfix);
	}
	
	private static String cutStringKeyHeaders(String key) throws KeyDecodingException{
		int cutStart=0, cutEnd=0;
		if (key.startsWith("-----BEGIN RSA PRIVATE KEY-----\n")){
			cutStart = new String("-----BEGIN RSA PRIVATE KEY-----\n").length();
			cutEnd = new String("\n-----END RSA PRIVATE KEY-----").length();
		} else if (key.startsWith("-----BEGIN RSA PUBLIC KEY-----\n")) {
			cutStart = new String("-----BEGIN RSA PUBLIC KEY-----\n").length();
			cutEnd = new String("\n-----END RSA PUBLIC KEY-----").length();
		} else {
			throw new KeyDecodingException("invalid key headers");
		}
		return key.substring(cutStart, key.length()-cutEnd);
	}
	
	private static String convertKeyToMultiLineFromBytes(byte[] key_bytes){
		String key_singleline = Base64.toBase64String(key_bytes);
		String key_multiline = ""; 
		for (int c=0; c<key_singleline.length(); c+=64){
			int endindex = Math.min(c+64, key_singleline.length());
			key_multiline += key_singleline.substring(c, endindex) + "\n";
		}
		return key_multiline.substring(0, key_multiline.length() - 1);
	}
	
	public static AsymmetricKeyParameter stringKeyToKey(String key) throws KeyDecodingException{
		try {
			if (key.startsWith("-----BEGIN RSA PRIVATE KEY-----\n")){
				return PrivateKeyFactory.createKey(	Base64.decode(cutStringKeyHeaders(key)) );
			} else if (key.startsWith("-----BEGIN RSA PUBLIC KEY-----\n")) {
				return PublicKeyFactory.createKey(	Base64.decode(cutStringKeyHeaders(key)) );
			} else {
				throw new KeyDecodingException("invalid key headers: " + key.substring(0,10) + "...");
			}
		} catch (IOException e){
			throw new KeyDecodingException();
		}
	}
	
	public static byte[] stringKeyToByteKey(String key) {
		return key.getBytes(StandardCharsets.UTF_8);
	}
	
	public static String byteKeyToStringKey(byte[] key) {
		return new String(key, StandardCharsets.UTF_8);
	}
	
	public static byte[] keyToByteKey(AsymmetricKeyParameter key) throws KeyDecodingException{
		return stringKeyToByteKey( keyToStringKey(key) );
	}
	
	public static AsymmetricKeyParameter byteKeyToKey(byte[] key) throws KeyDecodingException{
		return stringKeyToKey( byteKeyToStringKey(key) );
	}
	
	public static boolean isValidKey(String key){
		try {
			stringKeyToKey(key);
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	public static boolean isValidKey(byte[] key){
		try {
			isValidKey( byteKeyToStringKey(key) );
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	public static void main(String args[]) throws KeyDecodingException{
		AsymmetricCipherKeyPair keyPair = generateNewKeyPair();
		System.out.println( keyToStringKey(keyPair.getPublic()) );
	}
	
}
