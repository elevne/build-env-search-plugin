package io.jenkins.plugins.envsearch;

import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EnvSearchService {

    private static final Logger LOGGER = Logger.getLogger(EnvSearchService.class.getName());
    private static final int DEFAULT_MAX_BUILDS_PER_JOB = 50;
    private static final int DEFAULT_MAX_RESULTS = 500;
    private static final int SEARCH_TIMEOUT_SECONDS = 30;
    private static final int THREAD_POOL_SIZE = 8;

    public List<BuildSearchResult> search(String envKey, String envValue, int maxBuildsPerJob) {
        return search(envKey, envValue, maxBuildsPerJob, DEFAULT_MAX_RESULTS);
    }

    public List<BuildSearchResult> search(String envKey, String envValue, int maxBuildsPerJob, int maxResults) {
        if (envKey == null || envKey.trim().isEmpty() || envValue == null || envValue.trim().isEmpty()) {
            return Collections.emptyList();
        }

        if (maxBuildsPerJob <= 0) {
            maxBuildsPerJob = DEFAULT_MAX_BUILDS_PER_JOB;
        }
        if (maxResults <= 0) {
            maxResults = DEFAULT_MAX_RESULTS;
        }

        @SuppressWarnings("rawtypes")
        List allJobs = Jenkins.get().getAllItems(Job.class);

        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(THREAD_POOL_SIZE, Math.max(1, allJobs.size()))
        );

        final String searchKey = envKey;
        final String searchValue = envValue;
        final int perJobLimit = maxBuildsPerJob;
        final int resultLimit = maxResults;

        // Create tasks for each job
        List<Callable<List<BuildSearchResult>>> tasks = new ArrayList<Callable<List<BuildSearchResult>>>();
        for (Object jobObj : allJobs) {
            final Job<?, ?> job = (Job<?, ?>) jobObj;
            tasks.add(new Callable<List<BuildSearchResult>>() {
                public List<BuildSearchResult> call() {
                    List<BuildSearchResult> localResults = new ArrayList<BuildSearchResult>();
                    int count = 0;

                    for (Run<?, ?> run : job.getBuilds()) {
                        if (count >= perJobLimit || localResults.size() >= resultLimit) {
                            break;
                        }
                        count++;

                        try {
                            String matched = findEnvValueInline(run, searchKey);
                            if (searchValue.equals(matched)) {
                                localResults.add(new BuildSearchResult(run));
                            }
                        } catch (Exception e) {
                            LOGGER.log(Level.FINE, "Failed to read env vars from " + run.getFullDisplayName(), e);
                        }
                    }

                    return localResults;
                }

                private String findEnvValueInline(Run<?, ?> run, String envKey) {
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

                    return null;
                }
            });
        }

        // Execute all tasks and collect results
        List<BuildSearchResult> allResults = new ArrayList<BuildSearchResult>();
        try {
            List<Future<List<BuildSearchResult>>> futures = executor.invokeAll(tasks, SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            for (Future<List<BuildSearchResult>> future : futures) {
                try {
                    if (!future.isCancelled()) {
                        List<BuildSearchResult> result = future.get();
                        if (result != null) {
                            allResults.addAll(result);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Failed to get result from future", e);
                }
            }
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Search interrupted", e);
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }

        // Sort by timestamp descending (newest first)
        Collections.sort(allResults, new java.util.Comparator<BuildSearchResult>() {
            public int compare(BuildSearchResult a, BuildSearchResult b) {
                return Long.compare(b.getTimestamp(), a.getTimestamp());
            }
        });

        // Limit results
        if (allResults.size() > resultLimit) {
            return allResults.subList(0, resultLimit);
        }
        return allResults;
    }
}
