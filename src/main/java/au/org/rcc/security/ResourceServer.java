package au.org.rcc.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;


@Configuration
public class ResourceServer extends WebSecurityConfigurerAdapter {
	@Override
	public void configure(HttpSecurity http) throws Exception {
		//http.authorizeRequests().anyRequest().permitAll();

		http.authorizeRequests().antMatchers("/api/configurations").permitAll();

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
}

