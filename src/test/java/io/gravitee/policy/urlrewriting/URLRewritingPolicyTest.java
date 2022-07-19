/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.urlrewriting;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.urlrewriting.configuration.URLRewritingPolicyConfiguration;
import io.gravitee.policy.v3.URLRewritingPolicyV3;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class URLRewritingPolicyTest {

    private URLRewritingPolicyV3 urlRewritingPolicy;

    @Mock
    private Request request;

    @Mock
    private Response response;

    @Mock
    private PolicyChain policyChain;

    @Mock
    private ExecutionContext executionContext;

    @Mock
    private URLRewritingPolicyConfiguration configuration;

    @BeforeEach
    public void init() {
        urlRewritingPolicy = new URLRewritingPolicyV3(configuration);
    }

    @Test
    public void test_shouldNotRewriteHeaders() {
        // Prepare
        when(configuration.isRewriteResponseHeaders()).thenReturn(false);

        // Execute policy
        urlRewritingPolicy.onResponse(request, response, executionContext, policyChain);

        // Check results
        verify(response, never()).headers();
        verify(policyChain).doNext(any(Request.class), any(Response.class));
    }

    @Test
    public void test_rewriteHeaders() {
        // Prepare
        final HttpHeaders headers = HttpHeaders.create().set(HttpHeaderNames.LOCATION, "https://localgateway/mypath");

        when(response.headers()).thenReturn(headers);

        when(configuration.isRewriteResponseHeaders()).thenReturn(true);
        when(configuration.getFromRegex()).thenReturn("https?://[^\\/]*\\/((.*|\\/*))");
        when(configuration.getToReplacement()).thenReturn("https://apis.gravitee.io/{#group[1]}");

        // Prepare context
        when(executionContext.getTemplateEngine()).thenReturn(TemplateEngine.templateEngine());

        // Execute policy
        urlRewritingPolicy.onResponse(request, response, executionContext, policyChain);

        // Check results
        Assertions.assertEquals("https://apis.gravitee.io/mypath", response.headers().get(HttpHeaderNames.LOCATION));
        verify(policyChain).doNext(any(Request.class), any(Response.class));
    }

    @Test
    public void test_rewriteResponse_disabled() {
        // Prepare
        when(configuration.isRewriteResponseBody()).thenReturn(false);

        // Execute policy
        ReadWriteStream stream = urlRewritingPolicy.onResponseContent(request, response, executionContext);

        // Check results
        Assertions.assertNull(stream);
    }

    @Test
    public void test_rewriteResponse_noRewriting() {
        // Prepare
        when(configuration.isRewriteResponseBody()).thenReturn(true);
        when(configuration.getFromRegex()).thenReturn("https?://[^\\/]*\\/((.*|\\/*))");

        // Execute policy
        Buffer buffer = Buffer.buffer("{\"name\":1}");
        ReadWriteStream stream = urlRewritingPolicy.onResponseContent(request, response, executionContext);
        stream.write(buffer);
        stream.end();

        // Check results
        Assertions.assertNotNull(stream);
    }

    @Test
    public void test_rewriteResponse_singleMatch() {
        // Prepare
        when(configuration.isRewriteResponseBody()).thenReturn(true);
        when(configuration.getFromRegex()).thenReturn("https?://[^\\/]*\\/((.*|\\/*))");
        when(configuration.getToReplacement()).thenReturn("https://apis.gravitee.io/{#group[1]}");

        // Prepare context
        when(executionContext.getTemplateEngine()).thenReturn(TemplateEngine.templateEngine());

        // Execute policy
        Buffer buffer = Buffer.buffer("{\"link\":\"http://localhost:8082/mypath/toto\"}");
        ReadWriteStream stream = urlRewritingPolicy.onResponseContent(request, response, executionContext);
        stream.write(buffer);
        stream.end();

        // Check results
        Assertions.assertNotNull(stream);
    }

    @Test
    public void test_rewriteResponse_multipleMatches() {
        // Prepare
        when(configuration.isRewriteResponseBody()).thenReturn(true);
        when(configuration.getFromRegex()).thenReturn("https?:\\/\\/[^\\/]*\\/(([a-zA-Z\\/]*|\\/*))");
        when(configuration.getToReplacement()).thenReturn("https://apis.gravitee.io/{#group[1]}");

        // Prepare context
        when(executionContext.getTemplateEngine()).thenReturn(TemplateEngine.templateEngine());

        // Execute policy
        Buffer buffer = Buffer.buffer("{\"links\":[\"http://localhost:8082/mypath/toto\", \"http://localhost:8082/mypath/tata\"]}");
        ReadWriteStream stream = urlRewritingPolicy.onResponseContent(request, response, executionContext);
        stream.write(buffer);
        stream.end();

        // Check results
        Assertions.assertNotNull(stream);
    }

    @Test
    public void shouldRewriteEmptyBody() {
        // Prepare
        when(configuration.isRewriteResponseBody()).thenReturn(true);

        // Execute policy
        final ReadWriteStream stream = urlRewritingPolicy.onResponseContent(request, response, executionContext);
        stream.end();

        // Check results
        Assertions.assertNotNull(stream);
    }
}
