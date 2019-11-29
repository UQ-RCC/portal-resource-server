package au.org.rcc.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {
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
}
