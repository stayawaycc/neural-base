package com.neuralbase.news;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;

/**
 * 本机安全软件会破坏 GitHub/HuggingFace 的证书链验证(默认 cacerts 报 PKIX path building failed,
 * Windows Schannel 报 CRYPT_E_NO_REVOCATION_CHECK)。读取 Windows 系统根证书库构造 SSLContext 可绕过该问题;
 * 非 Windows 环境(如 Linux 容器)加载失败时回退系统默认 SSLContext。
 */
public final class WindowsTrustSslContext {

    public static SSLContext create() {
        try {
            KeyStore ks = KeyStore.getInstance("Windows-ROOT");
            ks.load(null, null);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx;
        } catch (Exception e) {
            return defaultContext();
        }
    }

    private static SSLContext defaultContext() {
        try {
            return SSLContext.getDefault();
        } catch (Exception e) {
            throw new IllegalStateException("无法构造 SSLContext", e);
        }
    }

    private WindowsTrustSslContext() {
    }
}
