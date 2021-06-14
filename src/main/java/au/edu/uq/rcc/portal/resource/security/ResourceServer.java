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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.resource.IssuerUriCondition;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;


@Configuration
public class ResourceServer extends WebSecurityConfigurerAdapter {
	@Override
	public void configure(HttpSecurity http) throws Exception {
		//http.authorizeRequests().anyRequest().permitAll();
		http = http.cors().and();

		http.authorizeRequests().antMatchers("/api/configurations").permitAll();
		http.authorizeRequests().requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll();

		JwtAuthenticationConverter jc = new JwtAuthenticationConverter();
		jc.setJwtGrantedAuthoritiesConverter(jwt -> {
			JwtGrantedAuthoritiesConverter cvt = new JwtGrantedAuthoritiesConverter();
			ArrayList<GrantedAuthority> authorities = new ArrayList<>(cvt.convert(jwt));

			Map<String, Object> realmAccess = jwt.getClaim("realm_access");
			Object _roles = realmAccess.get("roles");
			if(_roles != null) {
				((Collection<?>)_roles).stream()
						.map(s -> new SimpleGrantedAuthority(s.toString().toUpperCase()))
						.forEach(authorities::add);
			}
			return authorities;
		});

		http.authorizeRequests().anyRequest().hasAuthority("USER")
				.and()
				.oauth2ResourceServer()
				.jwt()
				.jwtAuthenticationConverter(jc);
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(@Autowired CustomCorsConfig corsConfig) {
		/* See this answer: https://stackoverflow.com/a/41000211 */
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOriginPatterns(corsConfig.allowedOriginPatterns);
		cors.setAllowedMethods(Arrays.asList("POST", "GET", "OPTIONS"));
		cors.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
		cors.setMaxAge(corsConfig.maxAge);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", cors);
		return source;
	}

	/* All of this is edited from OAuth2ResourceServerJwtConfiguration */

	@Value("${spring.security.oauth2.resourceserver.jwt.audience-id}")
	private String audience;

	@Bean
	@ConditionalOnProperty(name = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri")
	JwtDecoder jwtDecoderByJwkKeySetUri(OAuth2ResourceServerProperties properties) {
		OAuth2ResourceServerProperties.Jwt jwtProperties = properties.getJwt();

		NimbusJwtDecoder nimbusJwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwtProperties.getJwkSetUri())
				.jwsAlgorithm(SignatureAlgorithm.from(jwtProperties.getJwsAlgorithm())).build();

		List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
		String issuerUri = jwtProperties.getIssuerUri();
		if(issuerUri != null) {
			validators.add(JwtValidators.createDefaultWithIssuer(issuerUri));
		}

		if(audience != null) {
			final OAuth2Error error = new OAuth2Error("access_denied", String.format(
					"Invalid token does not contain resource id (%s)", audience
			), null);

			validators.add(jwt -> {
				if(!jwt.getAudience().contains(audience)) {
					return OAuth2TokenValidatorResult.failure(error);
				}
				return OAuth2TokenValidatorResult.success();
			});
		}

		nimbusJwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));

		return nimbusJwtDecoder;
	}

	@Bean
	@Conditional(IssuerUriCondition.class)
	JwtDecoder jwtDecoderByIssuerUri(OAuth2ResourceServerProperties properties) {
		/*
		 * Deliberately stopping this:
		 * - I don't want the auth server being down to stop this from starting
		 * - There's no way to add the audience validator in here
		 *   (as JwtDecoderProviderConfigurationUtils) is package private.
		 */
		throw new UnsupportedOperationException("Refusing to allow start without spring.security.oauth2.resourceserver.jwt.jwk-set-uri");
	}
}

