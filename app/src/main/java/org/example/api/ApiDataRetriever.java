package org.example.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.Workflow;
import org.example.model.WorkflowJob;
import org.example.model.WorkflowRun;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        // Useful to prevent crashing if GitHub adds new fields you don't know about
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ApiDataRetriever(String repo, String owner, String token) {
        this(repo, owner, token, 100);
    }

    public WorkflowRun getWorkflowRunById(long id) throws Exception {
        // 1. Construct the specific URL for a single run
        String url = String.format("%s/repos/%s/%s/actions/runs/%d", API_BASE_URL, owner, repo, id);

        // 2. Build the request (no query params like 'page' needed here)
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

        // 3. Execute and map directly to the Model (not a DataContract)
        String jsonBody = executeRequest(request).body();
        return objectMapper.readValue(jsonBody, WorkflowRun.class);
    }

    public List<Workflow> getWorkflows() throws Exception {
        String url = String.format("%s/repos/%s/%s/actions/workflows", API_BASE_URL, owner, repo);
        return executePaginatedRequest(url, Map.of(), WorkflowsDataContract.class);
    }

    public List<WorkflowRun> getQueuedWorkflowRuns() throws Exception {
        String url = String.format("%s/repos/%s/%s/actions/runs", API_BASE_URL, owner, repo);
        return executePaginatedRequest(url, Map.of("status", "queued"), WorkflowRunsDataContract.class);
    }

    public List<WorkflowRun> getActiveWorkflowRuns() throws Exception {
        String url = String.format("%s/repos/%s/%s/actions/runs", API_BASE_URL, owner, repo);
        return executePaginatedRequest(url, Map.of("status", "in_progress"), WorkflowRunsDataContract.class);
    }

    public List<WorkflowRun> getWorkflowRunsFrom(String fromDate) throws Exception {
        String url = String.format("%s/repos/%s/%s/actions/runs", API_BASE_URL, owner, repo);
        return executePaginatedRequest(url, Map.of("created", ">=" + fromDate), WorkflowRunsDataContract.class);
    }

    public List<WorkflowJob> getJobsForWorkflowRun(long runId) throws Exception {
        String url = String.format("%s/repos/%s/%s/actions/runs/%d/jobs", API_BASE_URL, owner, repo, runId);
        return executePaginatedRequest(url, Map.of(), WorkflowRunJobsDataContract.class);
    }

    private <T extends CountableDataContract<U>, U> List<U> executePaginatedRequest(
            String baseUrl,
            Map<String, String> queryParams,
            Class<T> responseType
    ) throws Exception {

        int page = 1;
        List<U> allItems = new ArrayList<>();

        while (true) {
            // 1. Construct the URI with Query Params manually
            String queryString = buildQueryString(queryParams, page);
            URI uri = URI.create(baseUrl + "?" + queryString);

            // 2. Build a FRESH request for every page
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28") // Good practice to lock version
                    .build();

            // 3. Execute
            String jsonBody = executeRequest(request).body();
            T data = objectMapper.readValue(jsonBody, responseType);

            List<U> pageItems = data.getItems();
            allItems.addAll(pageItems);

            // 4. Pagination Termination Logic
            if (pageItems.size() < perPage || allItems.size() >= data.getTotalCount()) {
                break;
            }
            page++;
        }
        return allItems;
    }

    private String buildQueryString(Map<String, String> filters, int page) {
        // Base params
        Map<String, String> params = new HashMap<>(filters);
        params.put("page", String.valueOf(page));
        params.put("per_page", String.valueOf(perPage));

        return params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private HttpResponse<String> executeRequest(HttpRequest request) throws Exception {
        System.out.println(request.uri() + " ... ");
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "GitHub API error: " + response.statusCode() + " | Body: " + response.body()
            );
        }
        return response;
    }
}