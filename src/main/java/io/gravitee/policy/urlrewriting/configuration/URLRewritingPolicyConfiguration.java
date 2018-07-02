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
package io.gravitee.policy.urlrewriting.configuration;

import io.gravitee.policy.api.PolicyConfiguration;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class URLRewritingPolicyConfiguration implements PolicyConfiguration {

    private boolean rewriteResponseHeaders;

    private boolean rewriteResponseBody;

    private String fromRegex;

    private String toReplacement;

    public boolean isRewriteResponseHeaders() {
        return rewriteResponseHeaders;
    }

    public void setRewriteResponseHeaders(boolean rewriteResponseHeaders) {
        this.rewriteResponseHeaders = rewriteResponseHeaders;
    }

    public boolean isRewriteResponseBody() {
        return rewriteResponseBody;
    }

    public void setRewriteResponseBody(boolean rewriteResponseBody) {
        this.rewriteResponseBody = rewriteResponseBody;
    }

    public String getFromRegex() {
        return fromRegex;
    }

    public void setFromRegex(String fromRegex) {
        this.fromRegex = fromRegex;
    }

    public String getToReplacement() {
        return toReplacement;
    }

    public void setToReplacement(String toReplacement) {
        this.toReplacement = toReplacement;
    }
}
