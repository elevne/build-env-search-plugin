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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
        ConcurrentLinkedQueue<BuildSearchResult> resultQueue = new ConcurrentLinkedQueue<BuildSearchResult>();
        AtomicInteger totalFound = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(THREAD_POOL_SIZE, Math.max(1, allJobs.size()))
        );

        final String searchKey = envKey;
        final String searchValue = envValue;
        final int perJobLimit = maxBuildsPerJob;
        final int resultLimit = maxResults;

        for (final Object jobObj : allJobs) {
            final Job<?, ?> job = (Job<?, ?>) jobObj;
            executor.submit(new Runnable() {
                public void run() {
                    if (totalFound.get() >= resultLimit) {
                        return;
                    }
                    int count = 0;
                    for (Run<?, ?> run : job.getBuilds()) {
                        if (count >= perJobLimit || totalFound.get() >= resultLimit) {
                            break;
                        }
                        count++;

                        try {
                            String matched = findEnvValue(run, searchKey);
                            if (searchValue.equals(matched)) {
                                resultQueue.add(new BuildSearchResult(run));
                                totalFound.incrementAndGet();
                            }
                        } catch (Exception e) {
                            LOGGER.log(Level.FINE, "Failed to read env vars from " + run.getFullDisplayName(), e);
                        }
                    }
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Search interrupted", e);
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }

        List<BuildSearchResult> results = new ArrayList<BuildSearchResult>(resultQueue);
        Collections.sort(results, new java.util.Comparator<BuildSearchResult>() {
            public int compare(BuildSearchResult a, BuildSearchResult b) {
                return Long.compare(b.getTimestamp(), a.getTimestamp());
            }
        });

        if (results.size() > resultLimit) {
            return results.subList(0, resultLimit);
        }
        return results;
    }

    private String findEnvValue(Run<?, ?> run, String envKey) {
        // 1. Check ParametersAction (build parameters) — fast, in-memory
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
}
