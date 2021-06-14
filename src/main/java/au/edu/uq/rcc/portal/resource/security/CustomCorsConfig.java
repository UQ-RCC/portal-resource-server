/*
 * RCC Portals Resource Server
 * https://github.com/UQ-RCC/portal-resource-server
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2020 The University of Queensland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.edu.uq.rcc.portal.resource.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * All this to work around https://github.com/spring-projects/spring-framework/issues/16381
 *
 * <pre>
 * cors:
 *   allowed-origin-patterns:
 *   - '*'
 *   max-age: 3600
 * </pre>
 */
@ConfigurationProperties(prefix = "cors")
@ConstructorBinding
public class CustomCorsConfig {
    public final List<String> allowedOriginPatterns;
    public final Duration maxAge;

    public CustomCorsConfig(List<String> allowedOriginPatterns, Long maxAge) {
        if(allowedOriginPatterns == null) {
            //noinspection unchecked
            allowedOriginPatterns = (List<String>)Collections.EMPTY_LIST;
        }

        if(maxAge == null)
            maxAge = 3600L;

        this.allowedOriginPatterns = Collections.unmodifiableList(allowedOriginPatterns);
        this.maxAge = Duration.ofSeconds(maxAge);
    }
}
