package au.org.rcc.controller;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class UserController {
    @RequestMapping(method = RequestMethod.GET, value = "/api/user")
    @ResponseBody
    public Map<String, Object> getExtraInfo(HttpServletRequest request,
			 HttpServletResponse response, Authentication auth) {
    	String username = request.getUserPrincipal().getName().toString();
    	System.out.println("username>>>" + username);
       	OAuth2AuthenticationDetails oauthDetails = (OAuth2AuthenticationDetails) auth.getDetails();
        Map<String, Object> details = (Map<String, Object>) oauthDetails.getDecodedDetails();
        System.out.println("username>>>" + details.get("username"));
        return details;
    }
}
