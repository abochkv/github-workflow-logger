package org.example.logic;

import org.example.api.ApiDataRetriever;
import org.example.db.Repository;
import org.example.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

class WorkflowLoggerTest {

    private static final Logger log = LoggerFactory.getLogger(WorkflowLoggerTest.class);
    @Mock
    private ApiDataRetriever apiMock;

    private MockedStatic<Repository> repositoryMock;
    private WorkflowLogger logger;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Mock static Repository methods
        repositoryMock = mockStatic(Repository.class);

        // Mock initial workflow fetch (constructor logic)
        Workflow wf = new Workflow();
        wf.setId(1L);
        wf.setName("Test Workflow");
        when(apiMock.getWorkflows()).thenReturn(List.of(wf));

        // Create logger with the mocked API
        logger = new WorkflowLogger(apiMock);
    }

    @AfterEach
    void tearDown() {
        repositoryMock.close();
    }

    @Test
    void testHandleNewRepository_FetchesActiveAndQueued() throws Exception {
        // Setup API responses
        WorkflowRun activeRun = createRun(100, Status.IN_PROGRESS);
        WorkflowJob job = createJob(500, Status.IN_PROGRESS);

        when(apiMock.getQueuedWorkflowRuns()).thenReturn(Collections.emptyList());
        when(apiMock.getActiveWorkflowRuns()).thenReturn(List.of(activeRun));
        when(apiMock.getJobsForWorkflowRun(100)).thenReturn(List.of(job));

        // Execute
        logger.handleNewRepository();

        // Verify API was called
        verify(apiMock, times(1)).getActiveWorkflowRuns();
        verify(apiMock, times(1)).getJobsForWorkflowRun(100);
    }

    @Test
    void testCheckForChanges_DetectsNewRun() throws Exception {
        // 1. Setup: No active runs initially
        when(apiMock.getQueuedWorkflowRuns()).thenReturn(Collections.emptyList());
        when(apiMock.getActiveWorkflowRuns()).thenReturn(Collections.emptyList());

        // Let's simulate the state transition:
        // First call returns nothing
        logger.handleNewRepository();

        // Second call returns a NEW active run
        WorkflowRun newRun = createRun(200, Status.IN_PROGRESS);
        WorkflowJob newJob = createJob(501, Status.QUEUED);

        when(apiMock.getActiveWorkflowRuns()).thenReturn(List.of(newRun));
        when(apiMock.getJobsForWorkflowRun(200)).thenReturn(List.of(newJob));

        logger.checkForChanges();

        // Verify we fetched jobs for the new run
        verify(apiMock).getJobsForWorkflowRun(200);
    }

    // Helper methods to create dummy data
    private WorkflowRun createRun(long id, Status status) {
        WorkflowRun r = new WorkflowRun();
        r.setId(id);
        r.setWorkflowId(1L);
        r.setStatus(status);
        r.setName("Test Run");
        return r;
    }

    private WorkflowJob createJob(long id, Status status) {
        WorkflowJob j = new WorkflowJob();
        j.setId(id);
        j.setStatus(status);
        j.setName("Test Job");
        return j;
    }
}