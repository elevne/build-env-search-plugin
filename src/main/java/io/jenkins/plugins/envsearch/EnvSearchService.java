package io.jenkins.plugins.envsearch;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EnvSearchService {

    private static final Logger LOGGER = Logger.getLogger(EnvSearchService.class.getName());
    private static final int DEFAULT_MAX_BUILDS = 50;

    public List<BuildSearchResult> search(String envKey, String envValue, int maxBuildsPerJob) {
        if (envKey == null || envKey.isBlank() || envValue == null || envValue.isBlank()) {
            return List.of();
        }

        if (maxBuildsPerJob <= 0) {
            maxBuildsPerJob = DEFAULT_MAX_BUILDS;
        }

        List<BuildSearchResult> results = new ArrayList<>();
        Jenkins jenkins = Jenkins.get();

        for (Job<?, ?> job : jenkins.getAllItems(Job.class)) {
            int count = 0;
            for (Run<?, ?> run : job.getBuilds()) {
                if (count >= maxBuildsPerJob) {
                    break;
                }
                count++;

                try {
                    String matched = findEnvValue(run, envKey);
                    if (envValue.equals(matched)) {
                        results.add(new BuildSearchResult(run));
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Failed to read env vars from " + run.getFullDisplayName(), e);
                }
            }
        }

        return results;
    }

    private String findEnvValue(Run<?, ?> run, String envKey) {
        // 1. Check ParametersAction (build parameters)
        ParametersAction paramsAction = run.getAction(ParametersAction.class);
        if (paramsAction != null) {
            ParameterValue param = paramsAction.getParameter(envKey);
            if (param != null) {
                Object value = param.getValue();
                if (value != null) {
                    return value.toString();
                }
            }
        }

        // 2. Check EnvironmentContributingAction instances (Gerrit Trigger, etc.)
        for (EnvironmentContributingAction action : run.getActions(EnvironmentContributingAction.class)) {
            EnvVars envVars = new EnvVars();
            action.buildEnvironment(run, envVars);
            String value = envVars.get(envKey);
            if (value != null) {
                return value;
            }
        }

        // 3. For AbstractBuild, try getEnvironment() as a fallback
        if (run instanceof AbstractBuild) {
            try {
                EnvVars env = ((AbstractBuild<?, ?>) run).getEnvironment(hudson.model.TaskListener.NULL);
                String value = env.get(envKey);
                if (value != null) {
                    return value;
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Failed to get environment from AbstractBuild: " + run.getFullDisplayName(), e);
            }
        }

        return null;
    }
}
