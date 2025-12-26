package org.example.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.Workflow;
import org.example.model.WorkflowJob;
import org.example.model.WorkflowRun;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class ApiDataRetriever {
    private static final String API_BASE_URL = "https://api.github.com";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String repo;
    private final String token;
    private final String owner;
    private final ObjectMapper objectMapper;
    private final int perPage;

    public ApiDataRetriever(String repo, String owner, String token, int perPage) {
        if (perPage < 1 || perPage > 100) {
            throw new IllegalArgumentException("perPage must be between 1 and 100");
        }
        this.repo = repo;
        this.owner = owner;
        this.token = token;
        this.perPage = perPage;
        this.objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    public ApiDataRetriever(String repo, String owner, String token) {
        this(repo, owner, token, 100);
    }

    public List<Workflow> getWorkflows() throws Exception {
        final String workflowsLink = String.format("%s/repos/%s/%s/actions/workflows", API_BASE_URL, owner, repo);
        HttpRequest.Builder builder = getPaginatedRequestBuilder(workflowsLink);
        return executePaginatedRequest(builder, WorkflowsDataContract.class);
    }

    public List<WorkflowRun> getQueuedWorkflowRuns() throws Exception {
        final String workflowLink = String.format("%s/repos/%s/%s/actions/runs", API_BASE_URL, owner, repo);
        HttpRequest.Builder builder = getPaginatedRequestBuilder(workflowLink).header("status", "queued");
        return executePaginatedRequest(builder, WorkflowRunsDataContract.class);
    }

    public List<WorkflowRun> getActiveWorkflowRuns() throws Exception {
        final String workflowLink = String.format("%s/repos/%s/%s/actions/runs", API_BASE_URL, owner, repo);
        HttpRequest.Builder builder = getPaginatedRequestBuilder(workflowLink).header("status", "in_progress");
        return executePaginatedRequest(builder, WorkflowRunsDataContract.class);
    }

    public List<WorkflowRun> getWorkflowRunsFrom(String from) throws Exception {
        final String workflowLink = String.format("%s/repos/%s/%s/actions/runs", API_BASE_URL, owner, repo);
        HttpRequest.Builder builder = getPaginatedRequestBuilder(workflowLink).header("created", ">="+from);
        return executePaginatedRequest(builder, WorkflowRunsDataContract.class);
    }

    public List<WorkflowJob> getJobsForWorkflowRun(long runId) throws Exception {
        final String jobLink = String.format("%s/repos/%s/%s/actions/runs/%d/jobs", API_BASE_URL, owner, repo, runId);
        HttpRequest.Builder builder = getBaseRequestBuilder(jobLink);
        return executePaginatedRequest(builder, WorkflowRunJobsDataContract.class);
    }

    private <T extends CountableDataContract<U>, U> List<U> executePaginatedRequest(HttpRequest.Builder builder, Class<T> requestAs)
            throws Exception {
        int page = 1;
        int currentCount;
        List<U> items = new ArrayList<>();
        do {
            HttpRequest request = builder.header("page", String.valueOf(page)).build();

            CountableDataContract<U> data = objectMapper.readValue(
                    executeRequest(request).body(), requestAs);

            currentCount = data.getTotalCount();
            items.addAll(data.getItems());
            page++;
        } while (currentCount == perPage);
        return items;
    }

    private HttpResponse<String> executeRequest(HttpRequest request) throws Exception {
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "GitHub API error: " + response.statusCode() + "\n" + response.body()
            );
        }

        return response;
    }

    private HttpRequest.Builder getBaseRequestBuilder(String link) {
        return HttpRequest.newBuilder()
                .uri(URI.create(link))
                .GET()
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json");
    }

    private HttpRequest.Builder getPaginatedRequestBuilder(String link) {
        return getBaseRequestBuilder(link)
                .header("per_page", String.valueOf(perPage));
    }
}
