package org.example.api;

import org.example.model.Workflow;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiDataRetrieverTest {

    private ApiDataRetriever api;

    @BeforeEach
    void setUp() {
        api = spy(new ApiDataRetriever(
                "repo",
                "owner",
                "token",
                2,
                "http://localhost"
        ));
    }

    @Test
    void getWorkflows_handlesPagination() throws Exception {
        HttpResponse<String> page1 = mockResponse("""
            { "total_count": 3, "workflows": [
                { "id": 1, "name": "A" },
                { "id": 2, "name": "B" }
            ]}
        """);

        HttpResponse<String> page2 = mockResponse("""
            { "total_count": 3, "workflows": [
                { "id": 3, "name": "C" }
            ]}
        """);

        doReturn(page1)
                .doReturn(page2)
                .when(api).executeRequest(any(HttpRequest.class));

        List<Workflow> workflows = api.getWorkflows();

        assertEquals(3, workflows.size());
    }

    @Test
    void getWorkflows_throwsOnHttpError() throws Exception {
        ApiDataRetriever api = spy(new ApiDataRetriever(
                "repo", "owner", "token", 100, "http://localhost"
        ));

        doThrow(new RuntimeException("GitHub API error: 403"))
                .when(api).executeRequest(any());

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                api::getWorkflows
        );

        assertTrue(ex.getMessage().contains("GitHub API error"));
    }


    private HttpResponse<String> mockResponse(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        return response;
    }
}



