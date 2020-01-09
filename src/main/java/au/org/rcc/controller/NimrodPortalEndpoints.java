package au.org.rcc.controller;

import au.org.massive.strudel_web.ssh.ForkedSSHClient;
import au.org.massive.strudel_web.ssh.SSHExecException;
import au.org.massive.strudel_web.util.UnsupportedKeyException;
import au.org.rcc.ResourceServerApplication;
import au.org.rcc.miscs.ResourceServerSettings;
import au.org.rcc.ssh.CertAuthInfo;
import au.org.rcc.ssh.CertAuthManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.jinjava.Jinjava;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Controller
@ConfigurationProperties(prefix = "nimrod.remote")
@EnableConfigurationProperties
public class NimrodPortalEndpoints {
	private static final Logger LOGGER = LogManager.getLogger(NimrodPortalEndpoints.class);

	@Autowired
	private ResourceServerSettings resourceServerSettings;

	@Autowired
	private CertAuthManager certAuthManager;

	private final Jinjava jinJava;
	private final String nimrodIniTemplate;
	private final String setupIniTemplate;

	public NimrodPortalEndpoints() {
		this.jinJava = new Jinjava();

		try(InputStream is = ResourceServerApplication.class.getResourceAsStream("nimrod.ini.j2")) {
			nimrodIniTemplate = IOUtils.toString(is, StandardCharsets.UTF_8);
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}

		try(InputStream is = ResourceServerApplication.class.getResourceAsStream("nimrod-setup.ini.j2")) {
			setupIniTemplate = IOUtils.toString(is, StandardCharsets.UTF_8);
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}

	}

	@Autowired
	@Qualifier("nimrodDb")
	private JdbcTemplate jdbc;

	@Autowired
	@Qualifier("rabbitRest")
	private RestTemplate rabbitRest;

	@Value("${nimrod.rabbitmq.api}")
	private URI rabbitApi;

	@Value("${nimrod.rabbitmq.user}")
	private String rabbitUser;

	@Value("${nimrod.rabbitmq.cacert")
	private Path rabbitCacert;

	@Value("${nimrod.rabbitmq.password}")
	private String rabbitPassword;

	@Autowired
	@Qualifier("pgbuilder")
	private UriBuilder postgresUriBuilder;

	@Autowired
	@Qualifier("rabbitbuilder")
	private UriBuilder rabbitUriBuilder;

	private Map<String, String> remoteVars;

	@RequestMapping(method = {RequestMethod.GET, RequestMethod.PUT}, value = "/api/user/{username}")
	@ResponseBody
	public void provisionUser(
			HttpServletRequest request,
			HttpServletResponse response,
			JwtAuthenticationToken jwtToken,
			@PathVariable String username
	) throws IOException, GeneralSecurityException, SSHExecException, UnsupportedKeyException {

		if(!username.equals(jwtToken.getToken().getClaimAsString("preferred_username"))) {
			response.sendError(HttpStatus.SC_FORBIDDEN);
			return;
		}

		SqlRowSet rs = jdbc.queryForRowSet("SELECT pg_password, amqp_password FROM portal_create_user(?)", username);
		if(!rs.next()) {
			/* Will never happen. */
			response.sendError(HttpStatus.SC_SERVICE_UNAVAILABLE);
			return;
		}

		String pgPass = rs.getString("pg_password");
		String amqpPass = rs.getString("amqp_password");

		try {
			int code = addRabbitUser(username, amqpPass).getStatusCodeValue();
			if(code != HttpStatus.SC_CREATED && code != HttpStatus.SC_NO_CONTENT) {
				response.sendError(HttpStatus.SC_SERVICE_UNAVAILABLE);
				return;
			}

			code = addRabbitVHost(username).getStatusCodeValue();
			if(code != HttpStatus.SC_CREATED && code != HttpStatus.SC_NO_CONTENT) {
				response.sendError(HttpStatus.SC_SERVICE_UNAVAILABLE);
				return;
			}

			code = addRabbitPermissions(username, username, ".*", ".*", ".*").getStatusCodeValue();
			if(code != HttpStatus.SC_CREATED && code != HttpStatus.SC_NO_CONTENT) {
				response.sendError(HttpStatus.SC_SERVICE_UNAVAILABLE);
				return;
			}
		} catch(HttpStatusCodeException e) {
			//TODO: Log
			response.sendError(HttpStatus.SC_SERVICE_UNAVAILABLE);
			return;
		}

		Map<String, Object> vars = new HashMap<>(remoteVars);
		vars.put("username", username);
		vars.put("pg_username", username);
		vars.put("pg_password", pgPass);
		vars.put("amqp_username", username);
		vars.put("amqp_password", amqpPass);
		vars.put("amqp_routing_key", username);
		vars.put("jdbc_url", postgresUriBuilder.build(vars));
		vars.put("amqp_url", rabbitUriBuilder.build(vars));

		String nimrodIni = jinJava.render(nimrodIniTemplate, vars);
		String nimrodSetupIni = jinJava.render(setupIniTemplate, vars);

		CertAuthInfo certAuth = certAuthManager.getCertAuth(username);

		// FIXME: Add actual upload functionality to the client
		ForkedSSHClient ssh = new ForkedSSHClient(certAuth, resourceServerSettings.getRemoteHost(), resourceServerSettings.getTmpDir());
		ssh.exec(new String[]{"sh", "-c", "mkdir -p ~/.config/nimrod && cat > ~/.config/nimrod/nimrod-portal.ini"}, nimrodIni.getBytes(StandardCharsets.UTF_8));
		ssh.exec(new String[]{"sh", "-c", "mkdir -p ~/.config/nimrod && cat > ~/.config/nimrod/nimrod-portal-setup.ini"}, nimrodSetupIni.getBytes(StandardCharsets.UTF_8));

		response.sendError(HttpStatus.SC_NO_CONTENT);
	}

	private ResponseEntity<Void> addRabbitUser(String username, String password) throws JsonProcessingException {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		headers.setBasicAuth(rabbitUser, rabbitPassword, StandardCharsets.UTF_8);

		ObjectMapper mapper = new ObjectMapper();
		String payload = mapper.writeValueAsString(mapper.createObjectNode()
				.put("password", password)
				.put("tags", ""));

		URI uri = UriComponentsBuilder.fromUri(rabbitApi)
				.pathSegment("api", "users", username)
				.build(new Object[0]);

		return rabbitRest.exchange(uri, HttpMethod.PUT, new HttpEntity<>(payload, headers), Void.class);
	}

	private ResponseEntity<Void> addRabbitVHost(String vhost) {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		headers.setBasicAuth(rabbitUser, rabbitPassword, StandardCharsets.UTF_8);

		URI uri = UriComponentsBuilder.fromUri(rabbitApi)
				.pathSegment("api", "vhosts", vhost)
				.build(new Object[0]);

		return rabbitRest.exchange(uri, HttpMethod.PUT, new HttpEntity<>(headers), Void.class);
	}

	private ResponseEntity<Void> addRabbitPermissions(String vhost, String username, String configure, String read, String write) throws JsonProcessingException {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		headers.setBasicAuth(rabbitUser, rabbitPassword, StandardCharsets.UTF_8);

		ObjectMapper mapper = new ObjectMapper();
		String payload = mapper.writeValueAsString(mapper.createObjectNode()
				.put("configure", configure)
				.put("read", read)
				.put("write", write));

		URI uri = UriComponentsBuilder.fromUri(rabbitApi)
				.pathSegment("api", "permissions", vhost, username)
				.build(new Object[0]);

		return rabbitRest.exchange(uri, HttpMethod.PUT, new HttpEntity<>(payload, headers), Void.class);
	}

	public static Certificate[] readX509Certificates(String path) throws IOException, CertificateException {
		if(path == null || path.isEmpty()) {
			return new Certificate[0];
		}

		return readX509Certificates(Paths.get(path));
	}

	public static Certificate[] readX509Certificates(Path path) throws IOException, CertificateException {
		try(InputStream is = Files.newInputStream(path)) {
			return readX509Certificates(is);
		}
	}

	public static Certificate[] readX509Certificates(InputStream is) throws CertificateException {
		return CertificateFactory.getInstance("X.509").generateCertificates(is).stream().toArray(Certificate[]::new);
	}

	@Bean
	@Qualifier("rabbitRest")
	public RestTemplate restTemplate(@Value("${nimrod.rabbitmq.cacert}") String cacert) throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException, UnrecoverableKeyException {

		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		ks.load(null, null);

		Certificate[] certs = readX509Certificates(Paths.get(cacert));
		for(int i = 0; i < certs.length; ++i) {
			String name = String.format("nimrod%d", i);
			ks.setCertificateEntry(name, certs[i]);
		}

		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(ks, null);

		TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		tmf.init(ks);

		SSLContext ctx = new SSLContextBuilder()
				.loadTrustMaterial(ks, null)
				.build();

		HttpClient httpClient = HttpClients.custom()
				//.setSSLSocketFactory(new SSLConnectionSocketFactory(ctx))
				.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
				.setSSLContext(ctx)
				.build();

		return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
	}

	@Bean
	@Qualifier("pgbuilder")
	public UriBuilder createPostgresUriBuilder(@Value("${nimrod.remote.postgres-uritemplate}") String s) {
		return new DefaultUriBuilderFactory().uriString(s);
	}

	@Bean
	@Qualifier("rabbitbuilder")
	public UriBuilder createRabbitUriBuilder(@Value("${nimrod.remote.rabbit-uritemplate}") String s) {
		return new DefaultUriBuilderFactory().uriString(s);
	}

	/* Needed so Spring can set it. */
	public void setVars(Map<String, String> vars) {
		this.remoteVars = vars;
	}
}
