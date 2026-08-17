package com.neuralbase.news;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsTrustSslContextTest {

    @Test
    void createsContextWithoutThrowing() {
        // Windows 上读系统根证书库;非 Windows 回退系统默认。两种路径都必须不抛。
        SSLContext ctx = WindowsTrustSslContext.create();
        assertThat(ctx).isNotNull();
        assertThat(ctx.getProtocol()).isNotBlank();
    }
}
