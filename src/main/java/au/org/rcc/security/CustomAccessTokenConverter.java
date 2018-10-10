package au.org.rcc.security;

import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import java.util.Map;
import java.util.Collection;

public class CustomAccessTokenConverter extends JwtAccessTokenConverter {

    @Override
    public OAuth2Authentication extractAuthentication(Map<String, ?> map) {
	    OAuth2Authentication oAuth2Authentication = super.extractAuthentication(map);
        oAuth2Authentication.setDetails(map);
        Collection<GrantedAuthority> authorities = (Collection<GrantedAuthority>) oAuth2Authentication.getOAuth2Request().getAuthorities();
        if (map.containsKey("realm_access")) {
            Map<String, Object> realm_access = (Map<String, Object>) map.get("realm_access");
            if(realm_access.containsKey("roles")) {
            	//System.out.println("Authority:" + realm_access.get("roles"));
		    	((Collection<String>) realm_access.get("roles")).forEach(r -> authorities.add(new SimpleGrantedAuthority(r.toUpperCase())));
            }
        }
	OAuth2Authentication newAuth = new OAuth2Authentication(oAuth2Authentication.getOAuth2Request(),oAuth2Authentication.getUserAuthentication());
	newAuth.setDetails(map);
	return newAuth;
    }



}
