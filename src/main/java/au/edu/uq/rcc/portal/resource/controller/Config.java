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

import au.edu.uq.rcc.portal.resource.ssh.CertAuthManager;
import au.org.massive.strudel_web.job_control.ConfigurationRegistry;
import au.org.massive.strudel_web.job_control.InvalidJsonConfigurationException;
import au.org.massive.strudel_web.job_control.StrudelDesktopConfigurationAdapter;
import au.org.massive.strudel_web.vnc.GuacamoleSessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

@Configuration
public class Config {
	@Bean(name = "nimrodDataSource")
	@ConfigurationProperties(prefix = "nimrod.datasource")
	public DataSource nimrodDataSource() {
		return DataSourceBuilder.create().build();
	}

	@Bean(name = "nimrodDb")
	@Autowired
	public JdbcTemplate nimrodJdbcTemplate(@Qualifier("nimrodDataSource") DataSource ds) {
		return new JdbcTemplate(ds);
	}

	@Bean
	public GuacamoleSessionManager guacamoleSessionManager(ResourceServerSettings settings, CertAuthManager certAuthManager, ThreadPoolTaskExecutor taskExecutor) {
		return new GuacamoleSessionManager(settings.getTmpDir(), certAuthManager, taskExecutor.getThreadPoolExecutor());
	}

	@Bean
	public ConfigurationRegistry configurationRegistry(@Value("${resource-server.jsonfile}") String jsonFile) throws InvalidJsonConfigurationException, IOException {
		String json = new String(Files.readAllBytes(Paths.get(jsonFile)), StandardCharsets.UTF_8);
		ConfigurationRegistry registry = new ConfigurationRegistry();
		StrudelDesktopConfigurationAdapter strudelConfig = new StrudelDesktopConfigurationAdapter("", json);
		for(String configId : strudelConfig.keySet()) {
			registry.addSystemConfiguration(configId, strudelConfig.get(configId));
		}
		return registry;
	}
}
