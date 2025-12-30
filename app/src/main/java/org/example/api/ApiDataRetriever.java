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
    private final String apiBaseUrl;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String token;
    private final ObjectMapper objectMapper;
    private final int perPage;

    public final String owner;
    public final String repo;

    public ApiDataRetriever(String repo, String owner, String token, int perPage, String baseUrl) {
        if (perPage < 1 || perPage > 100) {
            throw new IllegalArgumentException("perPage must be between 1 and 100");
        }
        this.repo = repo;
        this.owner = owner;
        this.token = token;
        this.perPage = perPage;
        this.apiBaseUrl = baseUrl;
        this.objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ApiDataRetriever(String repo, String owner, String token) {
        this(repo, owner, token, 100, "https://api.github.com");
    }

    public List<Workflow> getWorkflows() throws Exception {
        String url = String.format("%s/repos/%s/%s/actions/workflows", apiBaseUrl, owner, repo);
        return executePaginatedRequest(url, Map.of(), WorkflowsDataContract.class);
    }

    public List<WorkflowRun> getWorkflowRunsFrom(String fromDate) throws Exception {
        String url = String.format("%s/repos/%s/%s/actions/runs", apiBaseUrl, owner, repo);
        return executePaginatedRequest(url, Map.of("created", ">=" + fromDate), WorkflowRunsDataContract.class);
    }

    public List<WorkflowJob> getJobsForWorkflowRun(long runId) throws Exception {
        String url = String.format("%s/repos/%s/%s/actions/runs/%d/jobs", apiBaseUrl, owner, repo, runId);
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
            String queryString = buildQueryString(queryParams, page);
            URI uri = URI.create(baseUrl + "?" + queryString);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28") // Good practice to lock version
                    .build();

            String jsonBody = executeRequest(request).body();
            T data = objectMapper.readValue(jsonBody, responseType);

            List<U> pageItems = data.getItems();
            allItems.addAll(pageItems);

            if (pageItems.size() < perPage || allItems.size() >= data.getTotalCount()) {
                break;
            }
            page++;
        }
        return allItems;
    }

    private String buildQueryString(Map<String, String> filters, int page) {
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
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "GitHub API error: " + response.statusCode() + " | Body: " + response.body()
            );
        }
        return response;
    }
}