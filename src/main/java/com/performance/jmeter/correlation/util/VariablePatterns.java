package com.performance.jmeter.correlation.util;

import java.util.regex.Pattern;

/**
  Centralized repository for all variable-related regex patterns.
  This prevents pattern duplication and ensures consistent variable detection
  across all components of the plugin.
 
  Key patterns support:
  - Underscore-prefixed variables (${_token}, ${_userId})
  - Standard JMeter variables (${varName})
  - JSR223 script patterns (vars.get, vars.put)
 */
public class VariablePatterns {
    /**
      Matches JMeter variable usage: ${variableName}
      Supports all characters except closing brace, including underscore prefix.
      Examples:
      - ${userId} → captures "userId"
      - ${_token} → captures "_token"
      - ${_sessionId} → captures "_sessionId"
      - ${my_var_123} → captures "my_var_123"
     
      Pattern: \$\{([^}]+)\}
      Group 1: variable name
     */
    public static final Pattern VARIABLE_USAGE = Pattern.compile("\\$\\{([^}]+)}");
    /**
      Matches vars.put() calls in JSR223/BeanShell scripts.
      Captures the variable name being set.
      Examples:
      - vars.put("userId", value) → captures "userId"
      - vars.put('_token', value) → captures "_token"
      - vars.put( "_sessionId" , value) → captures "_sessionId"
      Pattern: vars\.put\s*\(\s*["']([^"']+)["']
      Group 1: variable name
     */
    public static final Pattern VARS_PUT = Pattern.compile("vars\\.put\\s*\\(\\s*[\"']([^\"']+)[\"']");

    /**
      Matches vars.get() calls in JSR223/BeanShell scripts.
      Captures the variable name being read.
      Examples:
      - vars.get("userId") → captures "userId"
      - vars.get('_token') → captures "_token"
      - vars.get( "_sessionId" ) → captures "_sessionId"
      Pattern: vars\.get\s*\(\s*["']([^"']+)["']
      Group 1: variable name
     */
    public static final Pattern VARS_GET = Pattern.compile("vars\\.get\\s*\\(\\s*[\"']([^\"']+)[\"']");
    /**
      Matches JMeter function calls.
      Used to detect parameterization via functions like ${__Random()}, ${__UUID()}, etc.
      Examples:
      - ${__Random(1,100)} → matches
      - ${__UUID()} → matches
      - ${__time(yyyy-MM-dd)} → matches
      Pattern: \$\{__(?:Random|RandomString|UUID|time|timeShift|...)
     */
    public static final Pattern JMETER_FUNCTION = Pattern.compile(
        "\\$\\{__(?:Random|RandomString|UUID|time|timeShift|threadNum|machineIP|" +
        "property|P|RandomDate|counter|intSum|longSum|V|eval|char|unescape)\\s*\\("
    );

    // Private constructor to prevent instantiation
    private VariablePatterns() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
      Checks if a string contains any JMeter variable references.
      @param text The text to check
      @return true if contains ${...} patterns
     */
    public static boolean containsVariables(String text) {
        return text != null && VARIABLE_USAGE.matcher(text).find();
    }

    /**
      Checks if a string contains vars.put() calls.
      @param script The script text to check
      @return true if contains vars.put() calls
     */
    public static boolean containsVarsPut(String script) {
        return script != null && VARS_PUT.matcher(script).find();
    }

    /**
       Checks if a string contains vars.get() calls.
      @param script The script text to check
      @return true if contains vars.get() calls
     */
    public static boolean containsVarsGet(String script) {
        return script != null && VARS_GET.matcher(script).find();
    }
}
