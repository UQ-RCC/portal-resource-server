package au.org.rcc.security;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.RemoteTokenServices;

import au.org.rcc.miscs.SecuritySettings;


@EnableAutoConfiguration
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableResourceServer
@Configuration
public class ResourceServer extends ResourceServerConfigurerAdapter {
	@Override
	public void configure(HttpSecurity http) throws Exception {
//		http.requestMatchers().antMatchers("/api/**")
//		.and().authorizeRequests().anyRequest().access("hasAuthority('USER')");
		http
		.authorizeRequests()
		.antMatchers("/api/configurations").permitAll()
		.anyRequest().access(SecuritySettings.getInstance().getAuthorityCheck());
//		http.authorizeRequests().anyRequest().permitAll();
	}
    @Override
    public void configure(ResourceServerSecurityConfigurer resources) throws Exception {
        //injecting a custom token converter in order to extract custom properties from id-portens token-info service
        RemoteTokenServices remoteTokenServices = new RemoteTokenServices();
        remoteTokenServices.setCheckTokenEndpointUrl(SecuritySettings.getInstance().getTokenInfoUri());
        remoteTokenServices.setAccessTokenConverter(new CustomAccessTokenConverter());
        remoteTokenServices.setClientSecret(SecuritySettings.getInstance().getClientSecret());
        remoteTokenServices.setClientId(SecuritySettings.getInstance().getClientId());
        resources.resourceId(SecuritySettings.getInstance().getResourceId());
        resources.tokenServices(remoteTokenServices);
    }
}

