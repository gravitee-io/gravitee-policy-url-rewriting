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
import io.gravitee.el.TemplateContext;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.jupiter.api.context.RequestExecutionContext;
import io.gravitee.gateway.jupiter.api.policy.Policy;
import io.gravitee.policy.urlrewriting.configuration.URLRewritingPolicyConfiguration;
import io.gravitee.policy.v3.URLRewritingPolicyV3;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
public class URLRewritingPolicy extends URLRewritingPolicyV3 implements Policy {

    private Pattern fromRegexPattern;
    private Set<String> extractedGroupNames;

    public URLRewritingPolicy(final URLRewritingPolicyConfiguration configuration) {
        super(configuration);
    }

    @Override
    public String id() {
        return "url-rewriting";
    }

    @Override
    public Completable onResponse(RequestExecutionContext ctx) {
        return Completable.defer(() -> {
            if (configuration.isRewriteResponseHeaders()) {
                rewriteHeaders(ctx);
            }

            if (configuration.isRewriteResponseBody()) {
                final HttpHeaders headers = ctx.response().headers();
                headers.remove(HttpHeaderNames.CONTENT_LENGTH);
                headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeadersValues.TRANSFER_ENCODING_CHUNKED);

                return ctx.response().onBody(upstream -> upstream.map(buffer -> rewrite(buffer, ctx)));
            }

            return Completable.complete();
        });
    }

    private Buffer rewrite(Buffer buffer, RequestExecutionContext ctx) {
        return Buffer.buffer(rewrite(buffer.toString(), ctx));
    }

    private void rewriteHeaders(RequestExecutionContext ctx) {
        final HttpHeaders headers = ctx.response().headers();
        headers.names().forEach(header -> headers.set(header, rewrite(headers.get(header), ctx)));
    }

    private Maybe<String> rewriteRx(String value, RequestExecutionContext ctx) {
        if (value != null && !value.isEmpty()) {
            // Apply regex capture / replacement
            final Matcher matcher = fromRegexPattern.matcher(value);

            if (!matcher.find()) {
                return Maybe.just(value);
            }
        }

        return null;
    }

    private String rewrite(String value, RequestExecutionContext ctx) {
        StringBuilder sb = new StringBuilder();

        if (value != null && !value.isEmpty()) {
            // Apply regex capture / replacement
            final Matcher matcher = getFromRegexPattern().matcher(value);
            int start = 0;

            boolean result = matcher.find();
            if (result) {
                do {
                    sb.append(value, start, matcher.start());

                    final String[] groups = new String[matcher.groupCount()];

                    for (int idx = 0; idx < matcher.groupCount(); idx++) {
                        groups[idx] = matcher.group(idx + 1);
                    }

                    final TemplateEngine templateEngine = ctx.getTemplateEngine();
                    final TemplateContext templateContext = templateEngine.getTemplateContext();

                    templateContext.setVariable(GROUP_ATTRIBUTE, groups);

                    // Extract capture group by name
                    Map<String, String> groupNames = getExtractedGroupNames()
                        .stream()
                        .collect(Collectors.toMap(groupName -> groupName, matcher::group));
                    templateContext.setVariable(GROUP_NAME_ATTRIBUTE, groupNames);

                    // Transform according to EL engine
                    sb.append(templateEngine.convert(configuration.getToReplacement()));

                    // Prepare next iteration
                    start = matcher.end();
                    result = matcher.find();
                } while (result);

                sb.append(value.substring(start));
            } else {
                sb.append(value);
            }
        }

        return sb.toString();
    }

    public Pattern getFromRegexPattern() {
        if (fromRegexPattern == null) {
            this.fromRegexPattern = Pattern.compile(configuration.getFromRegex());
        }

        return fromRegexPattern;
    }

    public Set<String> getExtractedGroupNames() {
        if (extractedGroupNames == null) {
            this.extractedGroupNames = getNamedGroupCandidates(getFromRegexPattern().pattern());
        }

        return extractedGroupNames;
    }
}
