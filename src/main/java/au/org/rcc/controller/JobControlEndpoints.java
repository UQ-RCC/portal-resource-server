
package au.org.rcc.controller;


import au.org.massive.strudel_web.job_control.*;
import au.org.massive.strudel_web.job_control.TaskFactory.Task;
import au.org.massive.strudel_web.ssh.CertAuthInfo;
import au.org.massive.strudel_web.ssh.CertAuthManager;
import au.org.massive.strudel_web.ssh.SSHExecException;
import au.org.massive.strudel_web.vnc.GuacamoleSession;
import au.org.massive.strudel_web.vnc.GuacamoleSessionManager;
import au.org.rcc.miscs.ResourceServerSettings;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Endpoints for all the jobs
 * Based on Jrigby JobcontrolEndPoints class
 * @author hoangnguyen177
 *
 */
@Controller
public class JobControlEndpoints{

    private static final ResourceServerSettings settings = ResourceServerSettings.getInstance();
    private static final Logger logger = LogManager.getLogger(JobControlEndpoints.class);
    
    
    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST}, 
    				value = "/api/execute/{task}")
    @ResponseBody
    public TaskResult<List<Map<String, String>>> executeJob0( 
    						HttpServletRequest request,
    						HttpServletResponse response,
    						Authentication auth,
    						@PathVariable final String task) throws IOException, SSHExecException, SQLException, NoSuchTaskTypeException {
    	return executeJob(request, response, auth, task, null, 0);
    }

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST},
    				value = "/api/execute/{task}/on/{host}")
    @ResponseBody
    public TaskResult<List<Map<String, String>>> executeJob1( 
							HttpServletRequest request,
							HttpServletResponse response,
							Authentication auth,
    						@PathVariable final String task) throws IOException, SSHExecException, SQLException, NoSuchTaskTypeException {
        return executeJob(request, response, auth, task, null, 0);
    }

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST}, 
    				value = "/api/execute/{task}/in/{configuration}")
    @ResponseBody
    public TaskResult<List<Map<String, String>>> executeJob2(
							HttpServletRequest request,
							HttpServletResponse response,	
							Authentication auth,
				    		@PathVariable final String task, 
    						@PathVariable final String configuration) throws IOException, SSHExecException, SQLException, NoSuchTaskTypeException {
        return executeJob(request, response, auth, task, configuration, 0);
    }

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST}, 
    				value = "/api/execute/{task}/in/{configuration}/on/{host}")
    @ResponseBody
    public TaskResult<List<Map<String, String>>> executeJob(
    						 HttpServletRequest request,
    						 HttpServletResponse response,
    						 Authentication auth,
    						 @PathVariable final String task,
    						 @PathVariable final String configuration,
    						 @RequestParam(value="retries", defaultValue="0") Integer retries)throws IOException, SSHExecException, SQLException, NoSuchTaskTypeException {
        // check auth
    	OAuth2AuthenticationDetails oauthDetails = (OAuth2AuthenticationDetails) auth.getDetails();
        Map<String, Object> details = (Map<String, Object>) oauthDetails.getDecodedDetails();
        String username = details.get("username").toString();
        if(username == null) {
        	response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid username");
            return null;
        }
    	String host = settings.getRemoteHost();
    	ConfigurationRegistry systemConfigurations = settings.getSystemConfigurations();
        AbstractSystemConfiguration systemConfiguration = 
        		(configuration == null) ? systemConfigurations.getDefaultSystemConfiguration() : systemConfigurations.getSystemConfigurationById(configuration);
        if (systemConfiguration == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid configuration name");
            return null;
        }

        if(!request.getMethod().equalsIgnoreCase(systemConfiguration.findByTaskType(task).getHttpMethod())) {
        	response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method " + request.getMethod()+ " not allowed");
            return null;
        }
        Task remoteTask;
        try {
        	CertAuthInfo certAuth = CertAuthManager.getInstance().getCertAuth(username);
        	remoteTask = new TaskFactory(systemConfiguration).getInstance(task, certAuth, host);
        	Map<String, String> parameters = new HashMap<>();
        	for (String key : request.getParameterMap().keySet()) {
            	String value = request.getParameterMap().get(key)[0]; // Only one value is accepted
            	parameters.put(key, value);
        	}  
        	try {
        		TaskResult<List<Map<String, String>>> result = remoteTask.run(parameters);
            	logger.info("Successfully executed task \"" + task + "\" on \"" + host + "\" from configuration \"" + configuration + "\" for " + request.getUserPrincipal());
            	return result;
        	} catch (MissingRequiredTaskParametersException e) {
        		e.printStackTrace();
            	response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            	return null;
        	} catch (SSHExecException e1) {
            	// If this request fails, try using the default remote host
            	if (retries < 1 && (systemConfiguration.findByTaskType(task).getRemoteHost() !=null) 
            			&& !systemConfiguration.findByTaskType(task).getRemoteHost().isEmpty()) {
             	   return executeJob(request, response, auth, task, configuration, 1);
            	} else {
            		e1.printStackTrace();
                    response.sendError(HttpServletResponse.SC_NOT_MODIFIED, e1.getMessage());
                    return null;
            	}
        	}
        } catch (NoSuchTaskTypeException e) {
        	e.printStackTrace();
        	response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            return null;
        }
        catch(Exception e) {
        	e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            return null;
        }

    }

    /**
     * Starts a VNC tunnel for use with a Guacamole server
     *
     * @param desktopName       the name to assign to the desktop
     * @param vncPassword       the password of the vnc server
     * @param remoteHost        the host on which the vnc server is running
     * @param display           the display number assigned to the vnc server
     * @param viaGateway        a gateway through which the tunnel is created (optional, can be inferred if configurationName provided)
     * @param configurationName the name of the configuration used for this tunnel (optional, recommended)
     * @param request           the {@link HttpServletRequest} object injected from the {@link Context}
     * @param response          the {@link HttpServletResponse} object injected from the {@link Context}
     * @return a vnc session id and desktop name
     * @throws IOException thrown on network IO errors
     * @throws NoSuchProviderException 
     * @throws NoSuchAlgorithmException 
     * @throws SignatureException 
     * @throws InvalidKeyException 
     */
    @RequestMapping(method = RequestMethod.GET, value = "/api/startvnctunnel")
	@ResponseBody
    public Map<String, Object> startVncTunnel(
    		HttpServletRequest request,
			HttpServletResponse response,
			Authentication auth,
			@RequestParam(value="desktopname") String desktopName,
			@RequestParam(value="vncpassword") String vncPassword,
			@RequestParam(value="remotehost") String remoteHost,
			@RequestParam(value="display") int display,
			@RequestParam(value="via_gateway",required=false) String viaGateway,
			@RequestParam(value="configuration", defaultValue="") String configurationName) throws IOException, InvalidKeyException, SignatureException, NoSuchAlgorithmException, NoSuchProviderException {
    	OAuth2AuthenticationDetails oauthDetails = (OAuth2AuthenticationDetails) auth.getDetails();
        Map<String, Object> details = (Map<String, Object>) oauthDetails.getDecodedDetails();
        String username = details.get("username").toString();
        logger.info("@startVncTunnel:" + username);
        if(username == null) {
        	response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid username");
            return null;
        }
    	int remotePort = display + 5900;
        AbstractSystemConfiguration systemConfiguration = 
        	settings.getSystemConfigurations().getDefaultSystemConfiguration();
        if(configurationName != null && !configurationName.isEmpty())		
        	systemConfiguration = settings.getSystemConfigurations().getSystemConfigurationById(configurationName);
        if (viaGateway == null && (systemConfiguration == null || !systemConfiguration.isTunnelTerminatedOnLoginHost())) {
            viaGateway = remoteHost;
            remoteHost = "localhost";
        } else if (viaGateway == null) {
            viaGateway = systemConfiguration.getLoginHost();
        }
	logger.info("viagateway=" + viaGateway);
        GuacamoleSession guacSession = GuacamoleSessionManager.getInstance().startSession(desktopName, vncPassword, viaGateway, remoteHost, remotePort, username);
	logger.info("Done creating guacSession");

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("id", guacSession.getId());
        responseData.put("desktopName", desktopName);
        responseData.put("localPort", guacSession.getLocalPort());
        return responseData;
    }

    /**
     * Stops a guacamole VNC session
     *
     * @param guacSessionId id of the vnc tunnel session
     * @param request       the {@link HttpServletRequest} object injected from the {@link Context}
     * @param response      the {@link HttpServletResponse} object injected from the {@link Context}
     * @return a status message
     * @throws IOException thrown on network IO errors
     */
    @RequestMapping(method = RequestMethod.GET, value = "/api/stopvnctunnel")
	@ResponseBody
    public Map<String, String> stopVncTunnel(
    		HttpServletRequest request,
			HttpServletResponse response,
			Authentication auth,
			@RequestParam(value="id") int guacSessionId) throws IOException {
    	OAuth2AuthenticationDetails oauthDetails = (OAuth2AuthenticationDetails) auth.getDetails();
        Map<String, Object> details = (Map<String, Object>) oauthDetails.getDecodedDetails();
        String username = details.get("username").toString();
        logger.info("@stopVncTunnel:" + username);
        if(username == null) {
        	response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid username");
            return null;
        }
    	GuacamoleSession guacSession = null;
        for (GuacamoleSession s : GuacamoleSessionManager.getInstance().getGuacamoleSessionsSet(username)) {
            if (s.getId() == guacSessionId) {
                guacSession = s;
                break;
            }
        }
        Map<String, String> responseData = new HashMap<>();
        if (guacSession == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No active session found by supplied ID");
            return null;
        } else {
            GuacamoleSessionManager.getInstance().endSession(guacSession, username);
            responseData.put("message", "session deleted");
        }
        return responseData;
    }

    /**
     * Lists all active VNC sessions for the current user
     *
     * @param request  the {@link HttpServletRequest} object injected from the {@link Context}
     * @param response the {@link HttpServletResponse} object injected from the {@link Context}
     * @return a list of tunnels
     * @throws IOException thrown on network IO errors
     */
    @RequestMapping(method = RequestMethod.GET, value = "/api/listvnctunnels")
	@ResponseBody
    public List<Map<String, Object>> listVncTunnels(
    		HttpServletRequest request,
			HttpServletResponse response,
			Authentication auth) throws IOException {
    	OAuth2AuthenticationDetails oauthDetails = (OAuth2AuthenticationDetails) auth.getDetails();
        Map<String, Object> details = (Map<String, Object>) oauthDetails.getDecodedDetails();
        String username = details.get("username").toString();
        logger.info("@listVncTunnel:" + username);
        if(username == null) {
        	response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid username");
            return null;
        }
    	Set<GuacamoleSession> guacSessions = GuacamoleSessionManager.getInstance().getGuacamoleSessionsSet(username);
        List<Map<String, Object>> tunnels = new ArrayList<>(guacSessions.size());
        for (GuacamoleSession s : guacSessions) {
            Map<String, Object> tunnel = new HashMap<>();
            tunnels.add(tunnel);
            tunnel.put("id", s.getId());
            tunnel.put("desktopName", s.getName());
            tunnel.put("password", s.getPassword());
            tunnel.put("localPort", s.getLocalPort());
        }
        return tunnels;
    }
    
    @RequestMapping(method = RequestMethod.GET, value = "/api/configurations")
	@ResponseBody
    public String listConfigurations() {
        return settings.getSystemConfigurations().getSystemConfigurationAsJson();
    }

}
