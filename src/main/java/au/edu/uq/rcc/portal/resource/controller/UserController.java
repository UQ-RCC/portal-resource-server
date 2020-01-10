package au.edu.uq.rcc.portal.resource.controller;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class UserController {
	@RequestMapping(method = RequestMethod.GET, value = "/api/user")
	@ResponseBody
	public Map<String, Object> getExtraInfo(JwtAuthenticationToken jwtToken) {
		return jwtToken.getTokenAttributes();
	}
}
