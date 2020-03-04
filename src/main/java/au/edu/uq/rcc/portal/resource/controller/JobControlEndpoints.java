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
package au.edu.uq.rcc.portal.resource.controller;


import au.edu.uq.rcc.portal.resource.ssh.CertAuthInfo;
import au.edu.uq.rcc.portal.resource.ssh.CertAuthManager;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoints for all the jobs
 * Based on Jrigby JobcontrolEndPoints class
 *
 * @author hoangnguyen177
 */
@Controller
public class JobControlEndpoints {
	private static final Logger logger = LogManager.getLogger(JobControlEndpoints.class);

	@Autowired
	private CertAuthManager certAuthManager;

	@Autowired
	private GuacamoleSessionManager guacamoleSessionManager;

	@Autowired
	private ConfigurationRegistry systemConfigurations;

	@Autowired
	private ResourceServerSettings resourceServerSettings;

	@Autowired
	private ObjectMapper mapper;

	@RequestMapping(method = {RequestMethod.GET, RequestMethod.POST},
			value = "/api/execute/{task}")
	@ResponseBody
	public ResponseEntity<TaskResult<List<Map<String, String>>>> executeJob0(
			HttpServletRequest request,
			Authentication auth,
			@PathVariable final String task) throws NoSuchTaskTypeException {
		return executeJob(request, auth, task, null, 0);
	}

	@RequestMapping(method = {RequestMethod.GET, RequestMethod.POST},
			value = "/api/execute/{task}/on/{host}")
	@ResponseBody
	public ResponseEntity<TaskResult<List<Map<String, String>>>> executeJob1(
			HttpServletRequest request,
			Authentication auth,
			@PathVariable final String task) throws NoSuchTaskTypeException {
		return executeJob(request, auth, task, null, 0);
	}

	@RequestMapping(method = {RequestMethod.GET, RequestMethod.POST},
			value = "/api/execute/{task}/in/{configuration}")
	@ResponseBody
	public ResponseEntity<TaskResult<List<Map<String, String>>>> executeJob2(
			HttpServletRequest request,
			Authentication auth,
			@PathVariable final String task,
			@PathVariable final String configuration) throws NoSuchTaskTypeException {
		return executeJob(request, auth, task, configuration, 0);
	}

	@RequestMapping(method = {RequestMethod.GET, RequestMethod.POST},
			value = "/api/execute/{task}/in/{configuration}/on/{host}")
	@ResponseBody
	public ResponseEntity<TaskResult<List<Map<String, String>>>> executeJob(
			HttpServletRequest request,
			Authentication auth,
			@PathVariable final String task,
			@PathVariable final String configuration,
			@RequestParam(value = "retries", defaultValue = "0") Integer retries) throws NoSuchTaskTypeException {
		Jwt jwt = ((JwtAuthenticationToken)auth).getToken();
		String username = jwt.getClaimAsString("preferred_username");
		if(username == null) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		String host = resourceServerSettings.getRemoteHost();
		AbstractSystemConfiguration systemConfiguration =
				(configuration == null) ? systemConfigurations.getDefaultSystemConfiguration() : systemConfigurations.getSystemConfigurationById(configuration);
		if(systemConfiguration == null) {
			return ResponseEntity.badRequest().build();
		}

		if(!request.getMethod().equalsIgnoreCase(systemConfiguration.findByTaskType(task).getHttpMethod())) {
			return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
		}

		Map<String, String> parameters = new HashMap<>();
		request.getParameterMap().forEach((k, v) -> parameters.put(k, v[0]));
		parameters.remove("access_token");

		ObjectNode logMap = mapper.createObjectNode()
				.put("timestamp", Instant.now().toString())
				.put("type", "request");

		{
			ObjectNode headers = mapper.createObjectNode();
			Collections.list(request.getHeaderNames())
					.forEach(h -> headers.set(h, mapper.valueToTree(Collections.list(request.getHeaders(h)))));
			headers.remove("authorization");

			ObjectNode request2 = mapper.createObjectNode();
			request2.put("uri", request.getRequestURI());
			request2.set("parameters", mapper.valueToTree(parameters));
			request2.set("headers", headers);

			logMap.set("request", request2);
		}

		ObjectNode ron = mapper.createObjectNode();
		ron.put("code", HttpServletResponse.SC_OK);
		ron.put("message", "OK");
		logMap.set("response", ron);

		Task remoteTask;
		try {
			CertAuthInfo certAuth = certAuthManager.getCertAuth(username);
			remoteTask = new TaskFactory(systemConfiguration, resourceServerSettings.getTmpDir()).getInstance(task, certAuth, host);

			try {
				TaskResult<List<Map<String, String>>> result = remoteTask.run(parameters);
				logger.info("Successfully executed task \"" + task + "\" on \"" + host + "\" from configuration \"" + configuration + "\" for " + request.getUserPrincipal());

				{
					ObjectNode rmap = mapper.createObjectNode();
					if(result.hasUserMessages()) {
						rmap.set("user_messages", mapper.valueToTree(result.getUserMessages()));
					} else {
						rmap.set("user_messages", mapper.valueToTree(Collections.EMPTY_LIST));
					}

					rmap.set("command_result", mapper.valueToTree(result.getCommandResult()));

					logMap.set("result", rmap);
				}
				return ResponseEntity.ok(result);
			} catch(MissingRequiredTaskParametersException e) {
				ron.put("code", HttpServletResponse.SC_BAD_REQUEST);
				ron.put("message", e.getMessage());
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
			} catch(SSHExecException e1) {
				// If this request fails, try using the default remote host
				if(retries < 1 && (systemConfiguration.findByTaskType(task).getRemoteHost() != null)
						&& !systemConfiguration.findByTaskType(task).getRemoteHost().isEmpty()) {
					return executeJob(request, auth, task, configuration, 1);
				} else {
					ron.put("code", HttpServletResponse.SC_NOT_MODIFIED);
					ron.put("message", e1.getMessage());

					return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
				}
			}
		} catch(NoSuchTaskTypeException e) {
			ron.put("code", HttpServletResponse.SC_NOT_FOUND);
			ron.put("message", e.getMessage());
			return ResponseEntity.notFound().build();
		} catch(Exception e) {
			ron.put("code", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			ron.put("message", e.getMessage());
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		} finally {
			logger.info("AUDIT: {}", logMap.toString());
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
	 * @return a vnc session id and desktop name
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/api/startvnctunnel")
	@ResponseBody
	public ResponseEntity<ObjectNode> startVncTunnel(
			HttpServletRequest request,
			Authentication auth,
			@RequestParam(value = "desktopname") String desktopName,
			@RequestParam(value = "vncpassword") String vncPassword,
			@RequestParam(value = "remotehost") String remoteHost,
			@RequestParam(value = "display") int display,
			@RequestParam(value = "via_gateway", required = false) String viaGateway,
			@RequestParam(value = "configuration", defaultValue = "") String configurationName) throws GeneralSecurityException {

		Jwt jwt = ((JwtAuthenticationToken)auth).getToken();
		String username = jwt.getClaimAsString("preferred_username");
		if(username == null) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		int remotePort = display + 5900;
		AbstractSystemConfiguration systemConfiguration = systemConfigurations.getDefaultSystemConfiguration();
		if(configurationName != null && !configurationName.isEmpty())
			systemConfiguration = systemConfigurations.getSystemConfigurationById(configurationName);
		if(viaGateway == null && (systemConfiguration == null || !systemConfiguration.isTunnelTerminatedOnLoginHost())) {
			viaGateway = remoteHost;
			remoteHost = "localhost";
		} else if(viaGateway == null) {
			viaGateway = systemConfiguration.getLoginHost();
		}
		logger.info("viagateway=" + viaGateway);
		GuacamoleSession guacSession = guacamoleSessionManager.startSession(desktopName, vncPassword, viaGateway, remoteHost, remotePort, username);
		logger.info("Done creating guacSession");

		return ResponseEntity.ok(mapper.createObjectNode()
				.put("id", guacSession.getId())
				.put("desktopName", desktopName)
				.put("localPort", guacSession.getLocalPort())
		);
	}

	/**
	 * Stops a guacamole VNC session
	 *
	 * @param guacSessionId id of the vnc tunnel session
	 * @param request       the {@link HttpServletRequest} object
	 * @return a status message
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/api/stopvnctunnel")
	@ResponseBody
	public ResponseEntity<ObjectNode> stopVncTunnel(
			HttpServletRequest request,
			Authentication auth,
			@RequestParam(value = "id") int guacSessionId) {

		Jwt jwt = ((JwtAuthenticationToken)auth).getToken();
		String username = jwt.getClaimAsString("preferred_username");
		if(username == null) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		GuacamoleSession guacSession = null;
		for(GuacamoleSession s : guacamoleSessionManager.getGuacamoleSessionsSet(username)) {
			if(s.getId() == guacSessionId) {
				guacSession = s;
				break;
			}
		}

		ObjectNode responseData = mapper.createObjectNode();
		if(guacSession == null) {
			return ResponseEntity.badRequest().build();
		} else {
			guacamoleSessionManager.endSession(guacSession, username);
			responseData.put("message", "session deleted");
		}
		return ResponseEntity.ok(responseData);
	}

	/**
	 * Lists all active VNC sessions for the current user
	 *
	 * @param request  the {@link HttpServletRequest} object
	 * @return a list of tunnels
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/api/listvnctunnels")
	@ResponseBody
	public ResponseEntity<ArrayNode> listVncTunnels(HttpServletRequest request, Authentication auth) {

		Jwt jwt = ((JwtAuthenticationToken)auth).getToken();
		String username = jwt.getClaimAsString("preferred_username");
		if(username == null) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		logger.info("@listVncTunnel:" + username);

		ArrayNode tunnels = mapper.createArrayNode();
		guacamoleSessionManager.getGuacamoleSessionsSet(username).forEach(s -> tunnels.add(mapper.createObjectNode()
				.put("id", s.getId())
				.put("desktopName", s.getName())
				.put("password", s.getPassword())
				.put("localPort", s.getLocalPort())
		));

		return ResponseEntity.ok(tunnels);
	}

	@RequestMapping(method = RequestMethod.GET, value = "/api/configurations")
	@ResponseBody
	public String listConfigurations() {
		return systemConfigurations.getSystemConfigurationAsJson();
	}

}
