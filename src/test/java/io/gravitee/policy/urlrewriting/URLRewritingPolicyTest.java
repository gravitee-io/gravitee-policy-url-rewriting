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

import io.gravitee.common.http.HttpHeaders;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.gateway.el.SpelTemplateEngine;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.urlrewriting.configuration.URLRewritingPolicyConfiguration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class URLRewritingPolicyTest {

    private URLRewritingPolicy urlRewritingPolicy;

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

    @Before
    public void init() {
        urlRewritingPolicy = new URLRewritingPolicy(configuration);
    }

    @Test
    public void test_shouldNotRewriteHeaders() {
        // Prepare
        final HttpHeaders headers = new HttpHeaders();
        when(response.headers()).thenReturn(headers);

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
        final HttpHeaders headers = new HttpHeaders();
        headers.setAll(new HashMap<String, String>() {
            {
                put(HttpHeaders.LOCATION, "https://localgateway/mypath");
            }
        });

        when(response.headers()).thenReturn(headers);

        when(configuration.isRewriteResponseHeaders()).thenReturn(true);
        when(configuration.getFromRegex()).thenReturn("https?://[^\\/]*\\/((.*|\\/*))");
        when(configuration.getToReplacement()).thenReturn("https://apis.gravitee.io/{#group[1]}");

        // Prepare context
        when(executionContext.getTemplateEngine()).thenReturn(new SpelTemplateEngine());

        // Execute policy
        urlRewritingPolicy.onResponse(request, response, executionContext, policyChain);

        // Check results
        Assert.assertEquals("https://apis.gravitee.io/mypath", response.headers().getFirst(HttpHeaders.LOCATION));
        verify(policyChain).doNext(any(Request.class), any(Response.class));
    }

    @Test
    public void test_rewriteResponse_disabled() {
        // Prepare
        final HttpHeaders headers = new HttpHeaders();

        when(response.headers()).thenReturn(headers);

        when(configuration.isRewriteResponseBody()).thenReturn(false);
        when(configuration.getFromRegex()).thenReturn("https?://[^\\/]*\\/((.*|\\/*))");
        when(configuration.getToReplacement()).thenReturn("https://apis.gravitee.io/{#group[1]}");

        // Prepare context
        when(executionContext.getTemplateEngine()).thenReturn(new SpelTemplateEngine());

        // Execute policy
        ReadWriteStream stream = urlRewritingPolicy.onResponseContent(request, response, executionContext);

        // Check results
        Assert.assertNull(stream);
    }

    @Test
    public void test_rewriteResponse_noRewriting() {
        // Prepare
        final HttpHeaders headers = new HttpHeaders();

        when(response.headers()).thenReturn(headers);

        when(configuration.isRewriteResponseBody()).thenReturn(true);
        when(configuration.getFromRegex()).thenReturn("https?://[^\\/]*\\/((.*|\\/*))");
        when(configuration.getToReplacement()).thenReturn("https://apis.gravitee.io/{#group[1]}");

        // Prepare context
        when(executionContext.getTemplateEngine()).thenReturn(new SpelTemplateEngine());

        // Execute policy
        Buffer buffer = Buffer.buffer("{\"name\":1}");
        ReadWriteStream stream = urlRewritingPolicy.onResponseContent(request, response, executionContext);
        stream.write(buffer);
        stream.end();

        // Check results
        Assert.assertNotNull(stream);
    }

    @Test
    public void test_rewriteResponse_singleMatch() {
        // Prepare
        final HttpHeaders headers = new HttpHeaders();

        when(response.headers()).thenReturn(headers);

        when(configuration.isRewriteResponseBody()).thenReturn(true);
        when(configuration.getFromRegex()).thenReturn("https?://[^\\/]*\\/((.*|\\/*))");
        when(configuration.getToReplacement()).thenReturn("https://apis.gravitee.io/{#group[1]}");

        // Prepare context
        when(executionContext.getTemplateEngine()).thenReturn(new SpelTemplateEngine());

        // Execute policy
        Buffer buffer = Buffer.buffer("{\"link\":\"http://localhost:8082/mypath/toto\"}");
        ReadWriteStream stream = urlRewritingPolicy.onResponseContent(request, response, executionContext);
        stream.write(buffer);
        stream.end();

        // Check results
        Assert.assertNotNull(stream);
    }

    @Test
    public void test_rewriteResponse_multipleMatches() {
        // Prepare
        final HttpHeaders headers = new HttpHeaders();

        when(response.headers()).thenReturn(headers);

        when(configuration.isRewriteResponseBody()).thenReturn(true);
        when(configuration.getFromRegex()).thenReturn("https?:\\/\\/[^\\/]*\\/(([a-zA-Z\\/]*|\\/*))");
        when(configuration.getToReplacement()).thenReturn("https://apis.gravitee.io/{#group[1]}");

        // Prepare context
        when(executionContext.getTemplateEngine()).thenReturn(new SpelTemplateEngine());

        // Execute policy
        Buffer buffer = Buffer.buffer("{\"links\":[\"http://localhost:8082/mypath/toto\", \"http://localhost:8082/mypath/tata\"]}");
        ReadWriteStream stream = urlRewritingPolicy.onResponseContent(request, response, executionContext);
        stream.write(buffer);
        stream.end();

        // Check results
        Assert.assertNotNull(stream);
    }

    @Test
    public void shouldRewriteEmptyBody() {
        // Prepare
        final HttpHeaders headers = new HttpHeaders();
        when(response.headers()).thenReturn(headers);
        when(configuration.isRewriteResponseBody()).thenReturn(true);
        when(configuration.getFromRegex()).thenReturn("https?://[^\\/]*\\/((.*|\\/*))");
        when(configuration.getToReplacement()).thenReturn("https://apis.gravitee.io/{#group[1]}");

        // Prepare context
        when(executionContext.getTemplateEngine()).thenReturn(new SpelTemplateEngine());

        // Execute policy
        final ReadWriteStream stream = urlRewritingPolicy.onResponseContent(request, response, executionContext);
        stream.end();

        // Check results
        Assert.assertNotNull(stream);
    }
}
