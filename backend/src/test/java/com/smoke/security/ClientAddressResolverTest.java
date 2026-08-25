package com.smoke.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientAddressResolverTest {

    @Test
    void usesForwardedAddressOnlyWhenConfiguredAsTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.20.0.4");
        request.addHeader("X-Forwarded-For", "203.0.113.8, 172.20.0.2");
        ClientAddressResolver resolver = new ClientAddressResolver();

        assertEquals("172.20.0.4", resolver.resolve(request));
        ReflectionTestUtils.setField(resolver, "trustForwardedHeaders", true);
        assertEquals("203.0.113.8", resolver.resolve(request));
    }
}
