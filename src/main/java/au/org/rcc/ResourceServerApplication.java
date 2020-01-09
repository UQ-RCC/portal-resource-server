package au.org.rcc;

import au.org.rcc.miscs.SecuritySettings;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletComponentScan;

import java.io.IOException;
import java.security.Security;

@ServletComponentScan
@SpringBootApplication
@EnableAutoConfiguration(exclude = {DataSourceTransactionManagerAutoConfiguration.class, DataSourceAutoConfiguration.class})
@EnableConfigurationProperties
public class ResourceServerApplication {
	
	
    public static void main(String[] args) {
    	Options options = new Options();
        // security config file
    	Option securityConfig = new Option("s", "securityconf", true, "Security Config");
    	securityConfig.setRequired(true);
        options.addOption(securityConfig);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("utility-name", options);
            System.exit(1);
            return;
        }
        String securityConfigFile = cmd.getOptionValue("securityconf", "ssh_authz_server.properties");
    	SecuritySettings securitySettings = SecuritySettings.getInstance();
    	try {
			securitySettings.readConfig(securityConfigFile);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
			return;
		}

		Security.addProvider(new BouncyCastleProvider());
        SpringApplication.run(ResourceServerApplication.class, args);
    }
}
