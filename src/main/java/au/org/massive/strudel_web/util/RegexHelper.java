package au.org.massive.strudel_web.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;

/**
 * Some regex helper utilities
 *
 * @author jrigby
 */
public class RegexHelper {
    private RegexHelper() {

    }

    private final static Pattern SPECIAL_REGEX_CHARS = Pattern.compile("[{}()\\[\\].+*?^$\\\\|]");

    public static String escapeRegexSpecialCharacters(String input) {
        return SPECIAL_REGEX_CHARS.matcher(input).replaceAll("\\\\$0");
    }

    public static Set<String> getNamedGroupCandidates(String regex) {
        Set<String> namedGroups = new HashSet<>();

        Matcher m = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>").matcher(regex);

        while (m.find()) {
            namedGroups.add(m.group(1));
        }

        return namedGroups;
    }

    public static String processRegexForEachLineJson(String regex, String input) {
        Gson gson = new Gson();
        return gson.toJson(processRegexForEachLine(regex, input));
    }

    public static String processRegexJson(String regex, String input) {
        Gson gson = new Gson();
        return gson.toJson(processRegex(regex, input));
    }

    public static List<Map<String, String>> processRegexForEachLine(String regex, String input) {
        List<Map<String, String>> output = new LinkedList<>();
        BufferedReader br = new BufferedReader(new StringReader(input));
        String line;
        try {
            while ((line = br.readLine()) != null) {
                List<Map<String, String>> lineMatches = processRegex(regex, line);
                if (lineMatches.size() > 0) {
                    output.addAll(lineMatches);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return output;
    }

    public static List<Map<String, String>> processRegex(String regex, String input) {
        return processRegex(regex, input, getNamedGroupCandidates(regex));
    }

    private static List<Map<String, String>> processRegex(String regex, String input, Set<String> namedGroups) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(input);
        List<Map<String, String>> results = new LinkedList<>();
        while (m.find()) {
            Map<String, String> outputMap = new HashMap<>();
            for (String groupName : namedGroups) {
                try {
                    String match = m.group(groupName);
                    outputMap.put(groupName, match);
                } catch (IllegalArgumentException e) {
                    // do nothing when the group doesn't exist
                }
            }
            results.add(outputMap);
        }
        return results;
    }

}
