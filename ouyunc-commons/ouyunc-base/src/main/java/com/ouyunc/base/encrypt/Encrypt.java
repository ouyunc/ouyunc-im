package com.ouyunc.base.encrypt;


import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.utils.MD5Util;
import com.ouyunc.base.utils.ObjectUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;

/**
 * @Author fangzhenxun
 * @Description: 加密算法
 **/
public class Encrypt {

    private static final Logger log = LoggerFactory.getLogger(Encrypt.class);

    /** 对称加密密钥：系统属性 */
    public static final String SECRET_PROPERTY = "ouyunc.im.encrypt.secret";
    /** 对称加密密钥：环境变量 */
    public static final String SECRET_ENV = "OUYUNC_IM_ENCRYPT_SECRET";
    private static final String DEFAULT_SECRET = "ouyunc";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int PBE_ITERATION_COUNT = 10_000;
    private static final int PBE_SALT_LENGTH = 16;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Encrypt() {
    }

    /**
     * 对称加密算法：DES / 3DES / AES / SM4 / PBE / RC2 / RC4 / RC5 等。
     * <p>密钥配置见 {@link #SECRET_PROPERTY}、{@link #SECRET_ENV}。</p>
     */
    public enum SymmetryEncrypt {

        NONE(NumberConstant.NUMBER_0, "none", "不加密", null) {
            @Override
            public <T> byte[] encrypt(T t) {
                if (t instanceof byte[] bytes) {
                    return bytes;
                }
                return ObjectUtil.serialize(t);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T decrypt(byte[] bytes, Class<T> tClass) {
                if (tClass != null && tClass.isAssignableFrom(byte[].class)) {
                    return (T) bytes;
                }
                return ObjectUtil.deserialize(bytes);
            }
        },
        DES(NumberConstant.NUMBER_1, "des", "DES加密算法", CipherAlgorithm.DES),
        DES_3(NumberConstant.NUMBER_2, "3des", "3DES加密算法", CipherAlgorithm.DES_3),
        AES(NumberConstant.NUMBER_3, "aes", "AES加密算法", CipherAlgorithm.AES),
        SM1(NumberConstant.NUMBER_4, "sm1", "SM1加密算法", null) {
            @Override
            public <T> byte[] encrypt(T t) {
                unsupportedSm1();
                return new byte[0];
            }

            @Override
            public <T> T decrypt(byte[] bytes, Class<T> tClass) {
                unsupportedSm1();
                return null;
            }
        },
        SMS4(NumberConstant.NUMBER_5, "sms4", "SM4国密加密算法", CipherAlgorithm.SM4),
        PBE(NumberConstant.NUMBER_6, "pbe", "PBE加密算法", CipherAlgorithm.PBE),
        RC2(NumberConstant.NUMBER_7, "rc2", "RC2加密算法", CipherAlgorithm.RC2),
        RC4(NumberConstant.NUMBER_8, "rc4", "RC4加密算法", CipherAlgorithm.RC4),
        RC5(NumberConstant.NUMBER_9, "rc5", "RC5加密算法", CipherAlgorithm.RC5);

        private final byte value;
        private final String name;
        private final String description;
        private final CipherAlgorithm cipherAlgorithm;

        SymmetryEncrypt(byte value, String name, String description, CipherAlgorithm cipherAlgorithm) {
            this.value = value;
            this.name = name;
            this.description = description;
            this.cipherAlgorithm = cipherAlgorithm;
        }

        public byte getValue() {
            return value;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        /**
         * 根据字节数返回具体的加密方式
         */
        public static SymmetryEncrypt prototype(byte value) {
            for (SymmetryEncrypt encryptEnum : SymmetryEncrypt.values()) {
                if (encryptEnum.getValue() == value) {
                    return encryptEnum;
                }
            }
            throw new MessageException("找不到匹配的加密方式: " + value);
        }

        /**
         * 加密：入参为对象时先 JDK 序列化，为 {@code byte[]} 时直接加密字节。
         */
        public <T> byte[] encrypt(T t) {
            return symmetricEncrypt(toPlainBytes(t), cipherAlgorithm);
        }

        /**
         * 解密：目标类型为 {@code byte[].class} 时返回明文字节，否则 JDK 反序列化。
         */
        public <T> T decrypt(byte[] bytes, Class<T> tClass) {
            byte[] plain = symmetricDecrypt(bytes, cipherAlgorithm);
            return fromPlainBytes(plain, tClass);
        }
    }


    /**
     * 非对称 / 摘要类算法（签名、口令校验等场景）。
     */
    public enum AsymmetricEncrypt {

        NONE((byte) 0, "none", "没有加密算法") {
            @Override
            public String encrypt(String rawStr) {
                return rawStr;
            }

            @Override
            public boolean validate(String rawStr, String encodeStr) {
                return encodeStr != null && encodeStr.equals(rawStr);
            }
        },

        MD5((byte) 1, "MD5", "MD5加密算法") {
            @Override
            public String encrypt(String rawStr) {
                return MD5Util.md5(rawStr);
            }

            @Override
            public boolean validate(String rawStr, String encodeStr) {
                return encodeStr != null && encodeStr.equalsIgnoreCase(encrypt(rawStr));
            }
        },

        SHA1((byte) 2, "SHA1", "SHA-1摘要") {
            @Override
            public String encrypt(String rawStr) {
                return DigestUtils.sha1Hex(rawStr);
            }

            @Override
            public boolean validate(String rawStr, String encodeStr) {
                return encodeStr != null && encodeStr.equalsIgnoreCase(encrypt(rawStr));
            }
        },

        SHA256((byte) 3, "SHA256", "SHA-256摘要") {
            @Override
            public String encrypt(String rawStr) {
                return DigestUtils.sha256Hex(rawStr);
            }

            @Override
            public boolean validate(String rawStr, String encodeStr) {
                return encodeStr != null && encodeStr.equalsIgnoreCase(encrypt(rawStr));
            }
        };

        private final byte value;
        private final String name;
        private final String description;

        AsymmetricEncrypt(byte value, String name, String description) {
            this.value = value;
            this.name = name;
            this.description = description;
        }

        public byte getValue() {
            return value;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        /**
         * 根据字节数返回具体的加密方式；未匹配时返回 {@code null}。
         */
        public static AsymmetricEncrypt prototype(byte value) {
            for (AsymmetricEncrypt encryptEnum : AsymmetricEncrypt.values()) {
                if (encryptEnum.getValue() == value) {
                    return encryptEnum;
                }
            }
            return null;
        }

        /**
         * 对字符串做摘要/编码（命名沿用历史 {@code encrypt}）。
         */
        public abstract String encrypt(String rawStr);

        /**
         * 校验原始串与摘要是否一致（摘要比较忽略大小写）。
         */
        public abstract boolean validate(String rawStr, String encodeStr);
    }


    // ---------- 对称加解密实现 ----------

    private enum CipherAlgorithm {
        DES("DES/CBC/PKCS5Padding", 8, 8, null, false),
        DES_3("DESede/CBC/PKCS5Padding", 24, 8, null, false),
        AES("AES/CBC/PKCS5Padding", 16, 16, null, false),
        SM4("SM4/CBC/PKCS5Padding", 16, 16, BouncyCastleProvider.PROVIDER_NAME, false),
        PBE("PBEWithHmacSHA256AndAES_128", 0, 0, null, true),
        RC2("RC2/CBC/PKCS5Padding", 16, 8, null, false),
        RC4("ARCFOUR", 16, 0, null, false),
        RC5("RC5/CBC/PKCS5Padding", 16, 8, null, false);

        private final String transformation;
        private final int keyLength;
        private final int ivLength;
        private final String provider;
        private final boolean passwordBased;

        CipherAlgorithm(String transformation, int keyLength, int ivLength, String provider, boolean passwordBased) {
            this.transformation = transformation;
            this.keyLength = keyLength;
            this.ivLength = ivLength;
            this.provider = provider;
            this.passwordBased = passwordBased;
        }
    }

    private static byte[] toPlainBytes(Object input) {
        if (input == null) {
            return new byte[0];
        }
        if (input instanceof byte[] bytes) {
            return bytes;
        }
        byte[] serialized = ObjectUtil.serialize(input);
        if (serialized == null) {
            throw new MessageException("对称加密前序列化失败");
        }
        return serialized;
    }

    @SuppressWarnings("unchecked")
    private static <T> T fromPlainBytes(byte[] plain, Class<T> clazz) {
        if (clazz == null) {
            throw new MessageException("解密目标类型不能为空");
        }
        if (byte[].class.equals(clazz)) {
            return (T) plain;
        }
        T result = ObjectUtil.deserialize(plain);
        if (result == null) {
            throw new MessageException("对称解密后反序列化失败");
        }
        return result;
    }

    private static byte[] symmetricEncrypt(byte[] plain, CipherAlgorithm algorithm) {
        if (plain == null) {
            plain = new byte[0];
        }
        try {
            if (algorithm.passwordBased) {
                return encryptPbe(plain);
            }
            if (algorithm.ivLength == 0) {
                return encryptStream(plain, algorithm);
            }
            return encryptBlock(plain, algorithm);
        } catch (GeneralSecurityException e) {
            throw new MessageException("对称加密失败: " + algorithm.name() + ", " + e.getMessage());
        }
    }

    private static byte[] symmetricDecrypt(byte[] encrypted, CipherAlgorithm algorithm) {
        if (encrypted == null || encrypted.length == 0) {
            return new byte[0];
        }
        try {
            if (algorithm.passwordBased) {
                return decryptPbe(encrypted);
            }
            if (algorithm.ivLength == 0) {
                return decryptStream(encrypted, algorithm);
            }
            return decryptBlock(encrypted, algorithm);
        } catch (GeneralSecurityException e) {
            throw new MessageException("对称解密失败: " + algorithm.name() + ", " + e.getMessage());
        }
    }

    private static void unsupportedSm1() {
        throw new MessageException("SM1 需硬件密码模块支持，请使用 SMS4(SM4) 或 AES");
    }

    private static byte[] encryptBlock(byte[] plain, CipherAlgorithm algorithm) throws GeneralSecurityException {
        byte[] iv = randomBytes(algorithm.ivLength);
        Cipher cipher = createCipher(algorithm);
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(algorithm), new IvParameterSpec(iv));
        return concat(iv, cipher.doFinal(plain));
    }

    private static byte[] decryptBlock(byte[] encrypted, CipherAlgorithm algorithm) throws GeneralSecurityException {
        if (encrypted.length <= algorithm.ivLength) {
            throw new MessageException("密文长度非法: " + algorithm.name());
        }
        byte[] iv = Arrays.copyOfRange(encrypted, 0, algorithm.ivLength);
        byte[] cipherText = Arrays.copyOfRange(encrypted, algorithm.ivLength, encrypted.length);
        Cipher cipher = createCipher(algorithm);
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(algorithm), new IvParameterSpec(iv));
        return cipher.doFinal(cipherText);
    }

    private static byte[] encryptStream(byte[] plain, CipherAlgorithm algorithm) throws GeneralSecurityException {
        Cipher cipher = createCipher(algorithm);
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(algorithm));
        return cipher.doFinal(plain);
    }

    private static byte[] decryptStream(byte[] encrypted, CipherAlgorithm algorithm) throws GeneralSecurityException {
        Cipher cipher = createCipher(algorithm);
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(algorithm));
        return cipher.doFinal(encrypted);
    }

    private static byte[] encryptPbe(byte[] plain) throws GeneralSecurityException {
        byte[] salt = randomBytes(PBE_SALT_LENGTH);
        SecretKey key = derivePbeKey(resolveSecret().toCharArray());
        Cipher cipher = Cipher.getInstance(CipherAlgorithm.PBE.transformation);
        cipher.init(Cipher.ENCRYPT_MODE, key, new PBEParameterSpec(salt, PBE_ITERATION_COUNT));
        return concat(salt, cipher.doFinal(plain));
    }

    private static byte[] decryptPbe(byte[] encrypted) throws GeneralSecurityException {
        if (encrypted.length <= PBE_SALT_LENGTH) {
            throw new MessageException("PBE 密文长度非法");
        }
        byte[] salt = Arrays.copyOfRange(encrypted, 0, PBE_SALT_LENGTH);
        byte[] cipherText = Arrays.copyOfRange(encrypted, PBE_SALT_LENGTH, encrypted.length);
        SecretKey key = derivePbeKey(resolveSecret().toCharArray());
        Cipher cipher = Cipher.getInstance(CipherAlgorithm.PBE.transformation);
        cipher.init(Cipher.DECRYPT_MODE, key, new PBEParameterSpec(salt, PBE_ITERATION_COUNT));
        return cipher.doFinal(cipherText);
    }

    private static Cipher createCipher(CipherAlgorithm algorithm) throws GeneralSecurityException {
        if (algorithm.provider != null) {
            return Cipher.getInstance(algorithm.transformation, algorithm.provider);
        }
        return Cipher.getInstance(algorithm.transformation);
    }

    private static SecretKey deriveKey(CipherAlgorithm algorithm) throws GeneralSecurityException {
        byte[] keyBytes = deriveKeyBytes(resolveSecret(), algorithm.keyLength);
        return new SecretKeySpec(keyBytes, keyAlgorithmName(algorithm.transformation));
    }

    private static SecretKey derivePbeKey(char[] password) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(CipherAlgorithm.PBE.transformation);
        return factory.generateSecret(spec);
    }

    private static String keyAlgorithmName(String transformation) {
        int slash = transformation.indexOf('/');
        return slash > 0 ? transformation.substring(0, slash) : transformation;
    }

    private static byte[] deriveKeyBytes(String secret, int keyLength) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            if (keyLength <= digest.length) {
                return Arrays.copyOf(digest, keyLength);
            }
            byte[] key = new byte[keyLength];
            for (int i = 0; i < keyLength; i++) {
                key[i] = digest[i % digest.length];
            }
            return key;
        } catch (GeneralSecurityException e) {
            throw new MessageException("派生密钥失败: " + e.getMessage());
        }
    }

    private static String resolveSecret() {
        String secret = System.getProperty(SECRET_PROPERTY);
        if (secret == null || secret.isBlank()) {
            secret = System.getenv(SECRET_ENV);
        }
        if (secret == null || secret.isBlank()) {
            secret = DEFAULT_SECRET;
            log.debug("未配置 {} / {}，使用内置默认密钥（生产环境请务必覆盖）", SECRET_PROPERTY, SECRET_ENV);
        }
        return secret;
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
