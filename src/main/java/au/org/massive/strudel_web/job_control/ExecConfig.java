package au.org.massive.strudel_web.job_control;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExecConfig {
	private final List<String> command;
	private final Set<String> parameters;
	private final String legacyPattern;

	public ExecConfig(String legacyPattern) {
		this.command = new ArrayList<>();
		this.parameters = getCommandPatternFields(legacyPattern);
		this.legacyPattern = legacyPattern;
	}

	public ExecConfig(Collection<String> command, Collection<String> parameters) {
		this.command = new ArrayList<>(command);
		this.parameters = new LinkedHashSet<>(parameters);
		this.legacyPattern = null;
	}

	public List<String> getCommand() {
		return Collections.unmodifiableList(command);
	}

	public Set<String> getParameters() {
		return Collections.unmodifiableSet(parameters);
	}

	public String getLegacyPattern() {
		return this.legacyPattern;
	}

	private static Set<String> getCommandPatternFields(String cmdPattern) {
		Pattern pattern = Pattern.compile("\\$\\{([a-zA-Z]+)\\}");
		Matcher matcher = pattern.matcher(cmdPattern);
		LinkedHashSet<String> fields = new LinkedHashSet<>();
		while(matcher.find()) {
			fields.add(matcher.group(1));
		}
		return fields;
	}
}
