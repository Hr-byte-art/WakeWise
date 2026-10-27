package com.wjh.wakewise.common;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SecretCipher 纯函数行为断言：AES-GCM 往返、防篡改、IV 随机性。 */
class SecretCipherTest {

    private final SecretCipher cipher = new SecretCipher("test-enc-key");

    @Test
    void roundTripsAsciiAndChinese() {
        assertThat(cipher.decrypt(cipher.encrypt("amap-key-0123456789"))).isEqualTo("amap-key-0123456789");
        // 密钥可能含任意可见字符与 Unicode，中文/emoji 往返必须无损
        assertThat(cipher.decrypt(cipher.encrypt("sk-密钥-🔐-with spaces"))).isEqualTo("sk-密钥-🔐-with spaces");
    }

    @Test
    void samePlaintextYieldsDifferentCiphertext() {
        // GCM 随机 IV：同明文两次密文不同（库中不可比对密文猜测是否同 key），但均可解回
        String a = cipher.encrypt("same-secret");
        String b = cipher.encrypt("same-secret");
        assertThat(a).isNotEqualTo(b);
        assertThat(cipher.decrypt(a)).isEqualTo("same-secret");
        assertThat(cipher.decrypt(b)).isEqualTo("same-secret");
    }

    @Test
    void rejectsTamperedCiphertext() {
        String enc = cipher.encrypt("some-api-key");
        char[] chars = enc.toCharArray();
        // 篡改 base64 首字符（落在 IV 上）——GCM 认证标签校验必须失败
        chars[0] = chars[0] == 'A' ? 'B' : 'A';
        assertThatThrownBy(() -> cipher.decrypt(new String(chars)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedInput() {
        // 非 base64 输入直接判非法
        assertThatThrownBy(() -> cipher.decrypt("not-base64!!!"))
                .isInstanceOf(IllegalArgumentException.class);
        // 过短输入（不足 IV 长度）判非法
        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(new byte[8])))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void differentKeysCannotDecryptEachOther() {
        SecretCipher other = new SecretCipher("another-key");
        // 密钥不匹配 → GCM 认证失败，调用方回落服务端默认
        assertThatThrownBy(() -> cipher.decrypt(other.encrypt("cross-key")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
