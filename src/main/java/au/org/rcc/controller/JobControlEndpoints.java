
package au.org.rcc.controller;


import au.org.massive.strudel_web.job_control.AbstractSystemConfiguration;
import au.org.massive.strudel_web.job_control.ConfigurationRegistry;
import au.org.massive.strudel_web.job_control.MissingRequiredTaskParametersException;
import au.org.massive.strudel_web.job_control.NoSuchTaskTypeException;
import au.org.massive.strudel_web.job_control.TaskFactory;
import au.org.massive.strudel_web.job_control.TaskFactory.Task;
import au.org.massive.strudel_web.job_control.TaskResult;
import au.org.massive.strudel_web.ssh.SSHExecException;
import au.org.massive.strudel_web.vnc.GuacamoleSession;
import au.org.massive.strudel_web.vnc.GuacamoleSessionManager;
import au.org.rcc.miscs.ResourceServerSettings;
import au.org.rcc.ssh.CertAuthInfo;
import au.org.rcc.ssh.CertAuthManager;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
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
    private static final Logger logger = LogManager.getLogger(JobControlEndpoints.class);

    @Autowired
	private CertAuthManager certAuthManager;

    @Autowired
	private GuacamoleSessionManager guacamoleSessionManager;

    @Autowired
	private ConfigurationRegistry systemConfigurations;

    @Autowired
    private ResourceServerSettings resourceServerSettings;

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST},
    				value = "/api/execute/{task}")
    @ResponseBody
    public TaskResult<List<Map<String, String>>> executeJob0(
    						HttpServletRequest request,
    						HttpServletResponse response,
    						Authentication auth,
							@PathVariable final String task) throws IOException, NoSuchTaskTypeException {
    	return executeJob(request, response, auth, task, null, 0);
    }

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST},
    				value = "/api/execute/{task}/on/{host}")
    @ResponseBody
    public TaskResult<List<Map<String, String>>> executeJob1(
							HttpServletRequest request,
							HttpServletResponse response,
							Authentication auth,
							@PathVariable final String task) throws IOException, NoSuchTaskTypeException {
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
							@PathVariable final String configuration) throws IOException, NoSuchTaskTypeException {
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
							 @RequestParam(value="retries", defaultValue="0") Integer retries)throws IOException, NoSuchTaskTypeException {
		Jwt jwt = ((JwtAuthenticationToken)auth).getToken();
		String username = jwt.getClaimAsString("preferred_username");
		if(username == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid username");
			return null;
		}

		String host = resourceServerSettings.getRemoteHost();
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

		Map<String, String> parameters = new HashMap<>();
		for (String key : request.getParameterMap().keySet()) {
			String value = request.getParameterMap().get(key)[0]; // Only one value is accepted
			parameters.put(key, value);
		}
		parameters.remove("access_token");

		Map<String, Object> logMap = new HashMap<>();
		logMap.put("timestamp", Instant.now().toString());
		logMap.put("type", "request");

		{
			Map<String, List<String>> headers = new HashMap<>();

			Enumeration<String> hnames = request.getHeaderNames();
			while(hnames.hasMoreElements()) {
				String name = hnames.nextElement();
				if("authorization".equalsIgnoreCase(name)) {
					continue;
				}
				headers.put(name, Collections.list(request.getHeaders(name)));
			}

			Map<String, Object> tmpMap = new HashMap<>();
			tmpMap.put("uri", request.getRequestURI());
			tmpMap.put("parameters", parameters);
			tmpMap.put("headers", headers);

			logMap.put("request", tmpMap);
		}

		Map<String, Object> logMapResponse = new HashMap<>();
		logMapResponse.put("code", HttpServletResponse.SC_OK);
		logMapResponse.put("message", "OK");

    	logMap.put("response", logMapResponse);

        Task remoteTask;
        try {
			CertAuthInfo certAuth = certAuthManager.getCertAuth(username);
			remoteTask = new TaskFactory(systemConfiguration, resourceServerSettings.getTmpDir()).getInstance(task, certAuth, host);

        	try {
        		TaskResult<List<Map<String, String>>> result = remoteTask.run(parameters);
            	logger.info("Successfully executed task \"" + task + "\" on \"" + host + "\" from configuration \"" + configuration + "\" for " + request.getUserPrincipal());

				{
					Map<String, Object> rmap = new HashMap<>();
					if(result.hasUserMessages()) {
						rmap.put("user_messages", result.getUserMessages());
					} else {
						rmap.put("user_messages", Collections.EMPTY_LIST);
					}

					rmap.put("command_result", result.getCommandResult());

					logMap.put("result", rmap);
				}
            	return result;
        	} catch (MissingRequiredTaskParametersException e) {
        		e.printStackTrace();
				logMapResponse.put("code", HttpServletResponse.SC_BAD_REQUEST);
				logMapResponse.put("message", e.getMessage());
            	response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            	return null;
        	} catch (SSHExecException e1) {
            	// If this request fails, try using the default remote host
				if (retries < 1 && (systemConfiguration.findByTaskType(task).getRemoteHost() !=null)
            			&& !systemConfiguration.findByTaskType(task).getRemoteHost().isEmpty()) {
             	   return executeJob(request, response, auth, task, configuration, 1);
            	} else {
            		e1.printStackTrace();
					logMapResponse.put("code", HttpServletResponse.SC_NOT_MODIFIED);
					logMapResponse.put("message", e1.getMessage());
                    response.sendError(HttpServletResponse.SC_NOT_MODIFIED, e1.getMessage());
                    return null;
            	}
        	}
        } catch (NoSuchTaskTypeException e) {
        	e.printStackTrace();
			logMapResponse.put("code", HttpServletResponse.SC_NOT_FOUND);
			logMapResponse.put("message", e.getMessage());
        	response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            return null;
        }
        catch(Exception e) {
        	e.printStackTrace();
			logMapResponse.put("code", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			logMapResponse.put("message", e.getMessage());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            return null;
        } finally {
			logger.info("AUDIT: {}", new GsonBuilder()
					.enableComplexMapKeySerialization()
					.create()
					.toJson(logMap)
			);
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
     * @param request           the {@link HttpServletRequest} object
     * @param response          the {@link HttpServletResponse} object
     * @return a vnc session id and desktop name
     * @throws IOException thrown on network IO errors
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
			@RequestParam(value="configuration", defaultValue="") String configurationName) throws IOException, GeneralSecurityException {

		Jwt jwt = ((JwtAuthenticationToken)auth).getToken();
		String username = jwt.getClaimAsString("preferred_username");
		if(username == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid username");
			return null;
		}

    	int remotePort = display + 5900;
        AbstractSystemConfiguration systemConfiguration = systemConfigurations.getDefaultSystemConfiguration();
        if(configurationName != null && !configurationName.isEmpty())
			systemConfiguration = systemConfigurations.getSystemConfigurationById(configurationName);
        if (viaGateway == null && (systemConfiguration == null || !systemConfiguration.isTunnelTerminatedOnLoginHost())) {
            viaGateway = remoteHost;
            remoteHost = "localhost";
        } else if (viaGateway == null) {
            viaGateway = systemConfiguration.getLoginHost();
        }
	logger.info("viagateway=" + viaGateway);
        GuacamoleSession guacSession = guacamoleSessionManager.startSession(desktopName, vncPassword, viaGateway, remoteHost, remotePort, username);
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
     * @param request       the {@link HttpServletRequest} object
     * @param response      the {@link HttpServletResponse} object
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

		Jwt jwt = ((JwtAuthenticationToken)auth).getToken();
		String username = jwt.getClaimAsString("preferred_username");
		if(username == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid username");
			return null;
		}

    	GuacamoleSession guacSession = null;
		for (GuacamoleSession s : guacamoleSessionManager.getGuacamoleSessionsSet(username)) {
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
			guacamoleSessionManager.endSession(guacSession, username);
            responseData.put("message", "session deleted");
        }
        return responseData;
    }

    /**
     * Lists all active VNC sessions for the current user
     *
     * @param request  the {@link HttpServletRequest} object
     * @param response the {@link HttpServletResponse} object
     * @return a list of tunnels
     * @throws IOException thrown on network IO errors
     */
    @RequestMapping(method = RequestMethod.GET, value = "/api/listvnctunnels")
	@ResponseBody
    public List<Map<String, Object>> listVncTunnels(
    		HttpServletRequest request,
			HttpServletResponse response,
			Authentication auth) throws IOException {

		Jwt jwt = ((JwtAuthenticationToken)auth).getToken();
		String username = jwt.getClaimAsString("preferred_username");
		if(username == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid username");
			return null;
		}

		logger.info("@listVncTunnel:" + username);

		Set<GuacamoleSession> guacSessions = guacamoleSessionManager.getGuacamoleSessionsSet(username);
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
        return systemConfigurations.getSystemConfigurationAsJson();
    }

}
