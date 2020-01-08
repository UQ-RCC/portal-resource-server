package au.org.rcc;
import java.nio.file.Paths;
import java.security.Security;

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

import au.org.rcc.miscs.ResourceServerSettings;
import au.org.rcc.miscs.SecuritySettings;

import org.springframework.boot.web.servlet.ServletComponentScan;

@ServletComponentScan
@SpringBootApplication
@EnableAutoConfiguration(exclude = {DataSourceTransactionManagerAutoConfiguration.class, DataSourceAutoConfiguration.class})
@EnableConfigurationProperties
public class ResourceServerApplication {
	
	
    public static void main(String[] args){
    	Options options = new Options();
    	// json file
    	Option tempDir = new Option("t", "tempdir", true, "Temporary dir");
    	tempDir.setRequired(false);
        options.addOption(tempDir);
    	// json file
    	Option jsonFile = new Option("f", "jsonfile", true, "config json file");
    	jsonFile.setRequired(true);
        options.addOption(jsonFile);
        // remote host
    	Option remoteHost = new Option("h", "remotehost", true, "Remote Host");
    	remoteHost.setRequired(true);
        options.addOption(remoteHost);
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
        String jsonFileValue = cmd.getOptionValue("jsonfile", "configuration.json");
        String remoteHostValue = cmd.getOptionValue("remotehost", "localhost");
        String securityConfigFile = cmd.getOptionValue("securityconf", "ssh_authz_server.properties");
    	String tempDirStr = cmd.getOptionValue("tempdir", System.getProperty("java.io.tmpdir"));
        ResourceServerSettings rsSettings = ResourceServerSettings.getInstance();
    	SecuritySettings securitySettings = SecuritySettings.getInstance();
    	try {
			rsSettings.setJsonConfigFile(jsonFileValue);
			securitySettings.readConfig(securityConfigFile);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
			return;
		}

    	rsSettings.setRemoteHost(remoteHostValue);
    	rsSettings.setTempDir(Paths.get(tempDirStr));

		Security.addProvider(new BouncyCastleProvider());
        SpringApplication.run(ResourceServerApplication.class, args);
    }
}
