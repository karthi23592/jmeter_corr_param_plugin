package com.performance.jmeter.correlation.util;

import com.performance.jmeter.correlation.model.ConfigurationStatus;
import com.performance.jmeter.correlation.model.ElementStatus;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Persists manual override statuses to a .cpstatus file alongside the JMX test plan.
 * Format: Java Properties file with keys like:
 *   /ThreadGroup/Sampler.correlation = NOT_APPLICABLE
 *   /ThreadGroup/Sampler.parameterization = CONFIGURED
 */
public class OverridePersistence {

    private static final String SUFFIX = ".cpstatus";
    private static final String CORR_SUFFIX = ".correlation";
    private static final String PARAM_SUFFIX = ".parameterization";

    public static void save(String jmxFilePath, ElementStatus root) {
        if (jmxFilePath == null || jmxFilePath.isEmpty()) return;

        File statusFile = getStatusFile(jmxFilePath);
        Properties props = new Properties();
        collectOverrides(root, props);

        try (OutputStream out = new FileOutputStream(statusFile)) {
            props.store(out, "Correlation & Parameterization Status - Manual Overrides");
        } catch (IOException e) {
            System.err.println("Failed to save status file: " + e.getMessage());
        }
    }

    public static void load(String jmxFilePath, ElementStatus root) {
        if (jmxFilePath == null || jmxFilePath.isEmpty()) return;

        File statusFile = getStatusFile(jmxFilePath);
        if (!statusFile.exists()) return;

        Properties props = new Properties();
        try (InputStream in = new FileInputStream(statusFile)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("Failed to load status file: " + e.getMessage());
            return;
        }

        Map<String, ConfigurationStatus> corrOverrides = new HashMap<>();
        Map<String, ConfigurationStatus> paramOverrides = new HashMap<>();

        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            ConfigurationStatus status = parseStatus(value);
            if (status == null) continue;

            if (key.endsWith(CORR_SUFFIX)) {
                String path = key.substring(0, key.length() - CORR_SUFFIX.length());
                corrOverrides.put(path, status);
            } else if (key.endsWith(PARAM_SUFFIX)) {
                String path = key.substring(0, key.length() - PARAM_SUFFIX.length());
                paramOverrides.put(path, status);
            }
        }

        applyOverrides(root, corrOverrides, paramOverrides);
    }

    public static File getStatusFile(String jmxFilePath) {
        return new File(jmxFilePath + SUFFIX);
    }

    private static void collectOverrides(ElementStatus element, Properties props) {
        if (element.getManualCorrelationOverride() != null) {
            props.setProperty(element.getPath() + CORR_SUFFIX,
                    element.getManualCorrelationOverride().name());
        }
        if (element.getManualParameterizationOverride() != null) {
            props.setProperty(element.getPath() + PARAM_SUFFIX,
                    element.getManualParameterizationOverride().name());
        }
        for (ElementStatus child : element.getChildren()) {
            collectOverrides(child, props);
        }
    }

    private static void applyOverrides(ElementStatus element,
                                       Map<String, ConfigurationStatus> corrOverrides,
                                       Map<String, ConfigurationStatus> paramOverrides) {
        String path = element.getPath();

        if (corrOverrides.containsKey(path)) {
            element.setManualCorrelationOverride(corrOverrides.get(path));
        }
        if (paramOverrides.containsKey(path)) {
            element.setManualParameterizationOverride(paramOverrides.get(path));
        }

        for (ElementStatus child : element.getChildren()) {
            applyOverrides(child, corrOverrides, paramOverrides);
        }
    }

    private static ConfigurationStatus parseStatus(String value) {
        if (value == null) return null;
        try {
            return ConfigurationStatus.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
