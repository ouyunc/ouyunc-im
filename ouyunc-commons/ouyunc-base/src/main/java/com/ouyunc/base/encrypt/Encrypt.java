package com.ouyunc.base.encrypt;


import com.ouyunc.base.utils.MD5Util;
import com.ouyunc.base.utils.ObjectUtil;

/**
 * @Author fangzhenxun
 * @Description: 加密算法
 **/
public class Encrypt {
    /**
     * @Author fangzhenxun
     * @Description: 对称加密算法，DES\3DES\AES\SM1\SMS4\PBE\RC2\RC4\RC5
     **/
    public enum SymmetryEncrypt {

        NONE((byte)0, "none", "不加密"){
            @Override
            public <T> byte[] encrypt(T t) {
                if (t instanceof byte[]) {
                    return (byte[])t;
                }
                return ObjectUtil.serialize(t);
            }

            @Override
            public <T> T decrypt(byte[] bytes, Class<T> tClass) {
                if (tClass.isAssignableFrom(byte[].class)) {
                    return (T)bytes;
                }
                return ObjectUtil.deserialize(bytes);
            }
        },
        DES((byte)1, "des", "DES加密算法"){
            @Override
            public <T> byte[] encrypt(T t) {
                return new byte[0];
            }

            @Override
            public <T> T decrypt(byte[] bytes, Class<T> tClass) {
                return null;
            }
        },
        DES_3((byte)2, "3des", "3DES加密算法"){
            @Override
            public <T> byte[] encrypt(T t) {
                return new byte[0];
            }

            @Override
            public <T> T decrypt(byte[] bytes, Class<T> tClass) {
                return null;
            }
        },
        AES((byte)3, "aes", "AES加密算法"){
            @Override
            public <T> byte[] encrypt(T t) {
                return new byte[0];
            }

            @Override
            public <T> T decrypt(byte[] bytes, Class<T> tClass) {
                return null;
            }
        },
        SM1((byte)4, "sm1", "SM1加密算法"){
            @Override
            public <T> byte[] encrypt(T t) {
                return new byte[0];
            }

            @Override
            public <T> T decrypt(byte[] bytes, Class<T> tClass) {
                return null;
            }
        },
        SMS4((byte)5, "sms4", "SMS4加密算法"){
            @Override
            public <T> byte[] encrypt(T t) {
                return new byte[0];
            }

            @Override
            public <T> T decrypt(byte[] bytes, Class<T> tClass) {
                return null;
            }
        },
        PBE((byte)6, "pbe", "PBE加密算法"){
            @Override
            public <T> byte[] encrypt(T t) {
                return new byte[0];
            }

            @Override
            public <T> T decrypt(byte[] bytes, Class<T> tClass) {
                return null;
            }
        },
        RC2((byte)7, "rc2", "RC2加密算法"){
            @Override
            public <T> byte[] encrypt(T t) {
                return new byte[0];
            }

            @Override
            public <T> T decrypt(byte[] bytes, Class<T> tClass) {
                return null;
            }
        },
        RC4((byte)8, "rc4", "RC4加密算法"){
            @Override
            public <T> byte[] encrypt(T t) {
                return new byte[0];
            }

            @Override
            public <T> T decrypt(byte[] bytes, Class<T> tClass) {
                return null;
            }
        },
        RC5((byte)9, "rc5", "RC5加密算法"){
            @Override
            public <T> byte[] encrypt(T t) {
                return new byte[0];
            }

            @Override
            public <T> T decrypt(byte[] bytes, Class<T> tClass) {
                return null;
            }
        };

        private byte value;
        private String name;
        private String description;

        SymmetryEncrypt(byte value, String name, String description) {
            this.value = value;
            this.name = name;
            this.description = description;
        }

        public byte getValue() {
            return value;
        }

        public void setValue(byte value) {
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        /**
         * @Author fzx
         * @Description 根据字节数返回具体的加密方式
         */
        public static SymmetryEncrypt prototype(byte value) {
            for (SymmetryEncrypt encryptEnum : SymmetryEncrypt.values()) {
                if (encryptEnum.getValue() == value) {
                    return encryptEnum;
                }
            }
            return null;
        }

        /**
         * @Author fzx
         * @Description 加密
         */
        public abstract<T> byte[] encrypt(T t);


        /**
         * @Author fzx
         * @Description 解密
         */
        public abstract<T> T decrypt(byte[] bytes, Class<T> tClass);
    }



    /**
     * @Author fzx
     * @Description 非对称加密算法
     */
    public enum AsymmetricEncrypt {

        NONE((byte)0, "none", "没有加密算法") {
            @Override
            public String encrypt(String rawStr) {
                return rawStr;
            }

            @Override
            public boolean validate(String rawStr, String encodeStr) {
                return encodeStr.equals(encrypt(rawStr));
            }
        },

        MD5((byte)1, "MD5", "MD5加密算法") {
            @Override
            public String encrypt(String rawStr) {
                return MD5Util.md5(rawStr);
            }

            @Override
            public boolean validate(String rawStr, String encodeStr) {
                return encodeStr.equals(encrypt(rawStr));
            }
        };


        private byte value;
        private String name;
        private String description;

        AsymmetricEncrypt(byte value, String name, String description) {
            this.value = value;
            this.name = name;
            this.description = description;
        }

        public byte getValue() {
            return value;
        }

        public void setValue(byte value) {
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        /**
         * @Author fzx
         * @Description 根据字节数返回具体的加密方式
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
         * @Author fzx
         * @Description 加密
         * @return byte[]
         */
        public abstract String encrypt(String rawStr);

       /**
        * @Author fzx
        * @Description
        * @param rawStr 原始值
        * @param encodeStr 待比对值
        * @return boolean
        */
        public abstract boolean validate(String rawStr, String encodeStr);
    }
}