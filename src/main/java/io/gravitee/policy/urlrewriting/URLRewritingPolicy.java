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

import io.gravitee.common.http.HttpHeadersValues;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.api.stream.BufferedReadWriteStream;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.gateway.api.stream.SimpleReadWriteStream;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.annotations.OnResponse;
import io.gravitee.policy.api.annotations.OnResponseContent;
import io.gravitee.policy.urlrewriting.configuration.URLRewritingPolicyConfiguration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rewrites URL in the <code>Location</code>, <code>Content-Location</code> headers on HTTP
 * redirect responses.
 *
 * <p>
 * Similar to the [ProxyPassReverse setting in mod_proxy](http://httpd.apache.org/docs/current/mod/mod_proxy.html#proxypassreverse)
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class URLRewritingPolicy {

    /**
     * LOGGER
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(URLRewritingPolicy.class);

    private static final Pattern GROUP_NAME_PATTERN = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>");

    private static final String GROUP_ATTRIBUTE = "group";
    private static final String GROUP_NAME_ATTRIBUTE = "groupName";

    private final URLRewritingPolicyConfiguration configuration;
    private final Pattern configuredURLPattern;

    public URLRewritingPolicy(final URLRewritingPolicyConfiguration configuration) {
        this.configuration = configuration;
        this.configuredURLPattern = Pattern.compile(configuration.getFromRegex());
    }

    @OnResponse
    public void onResponse(Request request, Response response, ExecutionContext executionContext, PolicyChain policyChain) {
        if (configuration.isRewriteResponseHeaders()) {
            rewriteHeaders(response.headers(), executionContext);
        }

        if (configuration.isRewriteResponseBody()) {
            response.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
            response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeadersValues.TRANSFER_ENCODING_CHUNKED);
        }

        policyChain.doNext(request, response);
    }

    private void rewriteHeaders(HttpHeaders headers, ExecutionContext executionContext) {
        LOGGER.debug("Rewrite HTTP response headers");

        Set<String> names = new HashSet<>(headers.names());
        names.forEach(headerName -> {
            List<String> headerValues = headers.getAll(headerName);
            headers.remove(headerName);
            headerValues.forEach(headerValue -> {
                String rewrittenHeaderValue = rewrite(headerValue, executionContext);
                headers.add(headerName, rewrittenHeaderValue);
            });
        });
    }

    @OnResponseContent
    public ReadWriteStream onResponseContent(Request request, Response response, ExecutionContext executionContext) {
        if (configuration.isRewriteResponseBody()) {
            return new BufferedReadWriteStream() {
                private Buffer buffer;

                @Override
                public SimpleReadWriteStream<Buffer> write(Buffer content) {
                    if (buffer == null) {
                        buffer = Buffer.buffer();
                    }

                    buffer.appendBuffer(content);
                    return this;
                }

                @Override
                public void end() {
                    if (buffer != null) {
                        super.write(Buffer.buffer(rewrite(buffer.toString(), executionContext)));
                    }
                    super.end();
                }
            };
        }

        // Nothing to apply, return null. This policy will not be added to the stream chain.
        return null;
    }

    private String rewrite(String value, ExecutionContext executionContext) {
        StringBuilder sb = new StringBuilder();

        if (value != null && !value.isEmpty()) {
            // Compile pattern

            // Apply regex capture / replacement
            Matcher matcher = this.configuredURLPattern.matcher(value);
            int start = 0;

            boolean result = matcher.find();
            if (result) {
                do {
                    sb.append(value.substring(start, matcher.start()));

                    final String[] groups = new String[matcher.groupCount()];

                    for (int idx = 0; idx < matcher.groupCount(); idx++) {
                        groups[idx] = matcher.group(idx + 1);
                    }

                    executionContext.getTemplateEngine().getTemplateContext().setVariable(GROUP_ATTRIBUTE, groups);

                    // Extract capture group by name
                    Set<String> extractedGroupNames = getNamedGroupCandidates(this.configuredURLPattern.pattern());
                    Map<String, String> groupNames = extractedGroupNames
                        .stream()
                        .collect(Collectors.toMap(groupName -> groupName, matcher::group));
                    executionContext.getTemplateEngine().getTemplateContext().setVariable(GROUP_NAME_ATTRIBUTE, groupNames);

                    // Transform according to EL engine
                    sb.append(executionContext.getTemplateEngine().convert(configuration.getToReplacement()));

                    // Prepare next iteration
                    start = matcher.end();
                    result = matcher.find();
                } while (result);

                sb.append(value.substring(start, value.length()));
            } else {
                sb.append(value);
            }
        }

        return sb.toString();
    }

    private Set<String> getNamedGroupCandidates(String regex) {
        Set<String> namedGroups = new TreeSet<>();
        Matcher m = GROUP_NAME_PATTERN.matcher(regex);

        while (m.find()) {
            namedGroups.add(m.group(1));
        }

        return namedGroups;
    }
}
