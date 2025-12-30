package org.example.api;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.example.model.Conclusion;
import org.example.model.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@WireMockTest
class ApiDataRetrieverTest {

    private ApiDataRetriever api;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        // Point the API to the local WireMock server
        String baseUrl = wmRuntimeInfo.getHttpBaseUrl();
        api = new ApiDataRetriever("test-repo", "test-owner", "fake-token", 1, baseUrl);
    }

    @Test
    void testGetWorkflowRunById_ParsesCorrectly() throws Exception {
        // 1. Stub the API response
        stubFor(get(urlPathEqualTo("/repos/test-owner/test-repo/actions/runs/123"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "id": 123,
                                "name": "CI Build",
                                "status": "completed",
                                "conclusion": "success"
                            }
                        """)));

        // 2. Execute
        WorkflowRun run = api.getWorkflowRunById(123);

        // 3. Verify
        assertNotNull(run);
        assertEquals(123, run.getId());
        assertEquals("CI Build", run.getName());
        assertEquals(Conclusion.SUCCESS, run.getConclusion());
    }

    @Test
    void testPagination_FetchesAllPages() throws Exception {
        // Page 1: Returns 1 item, Total count 2
        stubFor(get(urlPathMatching(".*actions/runs"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson("""
                    {
                        "total_count": 2,
                        "workflow_runs": [ { "id": 1, "name": "Run 1" } ]
                    }
                """)));

        // Page 2: Returns 1 item
        stubFor(get(urlPathMatching(".*actions/runs"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(okJson("""
                    {
                        "total_count": 2,
                        "workflow_runs": [ { "id": 2, "name": "Run 2" } ]
                    }
                """)));

        List<WorkflowRun> runs = api.getQueuedWorkflowRuns();

        assertEquals(2, runs.size(), "Should have fetched 2 runs across 2 pages");
        assertEquals(1, runs.get(0).getId());
        assertEquals(2, runs.get(1).getId());
    }
}