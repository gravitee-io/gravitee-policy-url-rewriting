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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.configuration.GatewayConfigurationBuilder;
import io.gravitee.definition.model.Api;
import io.gravitee.definition.model.ExecutionMode;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.policy.urlrewriting.configuration.URLRewritingPolicyConfiguration;
import io.vertx.reactivex.ext.web.client.WebClient;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@GatewayTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class URLRewritingPolicyIntegrationTest extends AbstractPolicyTest<URLRewritingPolicy, URLRewritingPolicyConfiguration> {

    @Override
    protected void configureGateway(GatewayConfigurationBuilder gatewayConfigurationBuilder) {
        super.configureGateway(gatewayConfigurationBuilder);
        gatewayConfigurationBuilder.set("api.jupiterMode.enabled", "false");
    }

    @Override
    public void configureApi(Api api) {
        api.setExecutionMode(ExecutionMode.V3);
    }

    @Test
    @DeployApi("/apis/api.json")
    void should_rewrite_headers(WebClient client) {
        wiremock.stubFor(
            post("/team")
                .willReturn(
                    ok("{ \"aUrl\": \"http://test.com/body\" }")
                        .withHeader(
                            HttpHeaderNames.SET_COOKIE,
                            "SID=ABAN12398123NJHJZEHDK123012039301U93274923U4KADNZKN; Path=http://test.com/header1"
                        )
                        .withHeader(HttpHeaderNames.SET_COOKIE, "JSESSIONID=123456789; Path=https://test.com/header2")
                )
        );

        client
            .post("/test")
            .rxSend()
            .test()
            .awaitDone(5, TimeUnit.SECONDS)
            .assertValue(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.headers().getAll(HttpHeaderNames.SET_COOKIE))
                    .isEqualTo(
                        List.of(
                            "SID=ABAN12398123NJHJZEHDK123012039301U93274923U4KADNZKN; Path=https://apis.gravitee.io/header1",
                            "JSESSIONID=123456789; Path=https://apis.gravitee.io/header2"
                        )
                    );
                return true;
            })
            .assertComplete()
            .assertNoErrors();
    }

    @DeployApi("/apis/api.json")
    @ParameterizedTest
    @MethodSource("provideParameters")
    void should_rewrite_body(String backendResponse, String expectedBody, WebClient client) {
        wiremock.stubFor(post("/team").willReturn(ok(backendResponse)));

        client
            .post("/test")
            .rxSend()
            .map(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                return response.body();
            })
            .test()
            .awaitDone(5, TimeUnit.SECONDS)
            .assertValue(body -> {
                assertThat(body).hasToString(expectedBody);
                return true;
            })
            .assertComplete()
            .assertNoErrors();
    }

    static Stream<Arguments> provideParameters() {
        return Stream.of(
            Arguments.of(
                "{ \"aUrl\": \"http://test.com/body\", \"a2ndUrl\": \"http://test.com/body2\" }",
                "{ \"aUrl\": \"https://apis.gravitee.io/body\", \"a2ndUrl\": \"https://apis.gravitee.io/body2\" }"
            ),
            Arguments.of(
                "{ \"aUrl\": \"http://test.com/body\", \n \"a2ndUrl\": \"http://test.com/body2\" }",
                "{ \"aUrl\": \"https://apis.gravitee.io/body\", \n \"a2ndUrl\": \"https://apis.gravitee.io/body2\" }"
            ),
            Arguments.of(
                "{ \"aUrl\": \"http://test.com/body/some-path?param1=1&param2=test\", \"a2ndUrl\": \"http://test.com/body2\" }",
                "{ \"aUrl\": \"https://apis.gravitee.io/body/some-path?param1=1&param2=test\", \"a2ndUrl\": \"https://apis.gravitee.io/body2\" }"
            ),
            Arguments.of(
                "{ \"aUrl\": \"http://test.com/body\", \n \"a2ndUrl\": \"http://test.com/body2\", \"a3rdUrl\": \"http://test.com/body3\", \"anotherValue\": \"test\" }",
                "{ \"aUrl\": \"https://apis.gravitee.io/body\", \n \"a2ndUrl\": \"https://apis.gravitee.io/body2\", \"a3rdUrl\": \"https://apis.gravitee.io/body3\", \"anotherValue\": \"test\" }"
            )
        );
    }
}
