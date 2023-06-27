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

import static org.mockito.Mockito.*;

import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.urlrewriting.configuration.URLRewritingPolicyConfiguration;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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
        Assert.assertEquals("https://apis.gravitee.io/mypath", response.headers().get(HttpHeaderNames.LOCATION));
        verify(policyChain).doNext(any(Request.class), any(Response.class));
    }

    @Test
    public void test_rewriteHeadersWithMultipleValues() {
        // Prepare
        final HttpHeaders headers = HttpHeaders
            .create()
            .add(HttpHeaderNames.SET_COOKIE, "SID=ABAN12398123NJHJZEHDK123012039301U93274923U4KADNZKN; Path=/test")
            .add(HttpHeaderNames.SET_COOKIE, "JSESSIONID=123456789; Path=/test");

        when(response.headers()).thenReturn(headers);

        when(configuration.isRewriteResponseHeaders()).thenReturn(true);
        when(configuration.getFromRegex()).thenReturn("Path=/test");
        when(configuration.getToReplacement()).thenReturn("Path=/updated-path");

        when(executionContext.getTemplateEngine()).thenReturn(TemplateEngine.templateEngine());

        urlRewritingPolicy.onResponse(request, response, executionContext, policyChain);

        Assert.assertEquals(
            List.of(
                "SID=ABAN12398123NJHJZEHDK123012039301U93274923U4KADNZKN; Path=/updated-path",
                "JSESSIONID=123456789; Path=/updated-path"
            ),
            response.headers().getAll(HttpHeaderNames.SET_COOKIE)
        );
        verify(policyChain).doNext(any(Request.class), any(Response.class));
    }

    @Test
    public void test_rewriteResponse_disabled() {
        // Prepare
        when(configuration.isRewriteResponseBody()).thenReturn(false);

        // Execute policy
        ReadWriteStream stream = urlRewritingPolicy.onResponseContent(request, response, executionContext);

        // Check results
        Assert.assertNull(stream);
    }

    @Test
    public void shouldRewriteEmptyBody() {
        // Prepare
        when(configuration.isRewriteResponseBody()).thenReturn(true);

        // Execute policy
        final ReadWriteStream stream = urlRewritingPolicy.onResponseContent(request, response, executionContext);
        stream.end();

        // Check results
        Assert.assertNotNull(stream);
    }
}
