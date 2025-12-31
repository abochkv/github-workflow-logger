package org.example.logic;

import org.example.api.ApiDataRetriever;
import org.example.db.Repository;
import org.example.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class WorkflowLoggerTest {

    @Mock
    private ApiDataRetriever mockApi;
    @Mock private Repository mockRepo;

    private ByteArrayOutputStream outputBuffer;
    private WorkflowLogger logger;

    @BeforeEach
    void setUp() throws Exception {
        outputBuffer = new ByteArrayOutputStream();
        PrintStream testOut = new PrintStream(outputBuffer);

        // Mock the workflow lookup for the constructor
        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setName("Main CI");
        when(mockApi.getWorkflows()).thenReturn(List.of(workflow));

        logger = new WorkflowLogger(mockApi, mockRepo, testOut);

        // FIX: Mock the initialization call to set the internal timestamps
        // This sets 'oldestNotCompletedJobTimestamp' so it isn't null
        when(mockApi.getWorkflowRunsFrom(anyString())).thenReturn(Collections.emptyList());
        logger.handleNewRepository();

        // Clear the buffer so setup logs don't interfere with test assertions
        outputBuffer.reset();
    }

    @Test
    void testCheckForChanges_DoesNotRepeatLogsForUnchangedJobs() throws Exception {
        // Arrange: First poll finds a run
        WorkflowRun run = createMockRun(123L, Status.IN_PROGRESS, null);
        when(mockApi.getWorkflowRunsFrom(any())).thenReturn(List.of(run));
        when(mockApi.getJobsForWorkflowRun(123L)).thenReturn(List.of(createMockJob(456L, Status.IN_PROGRESS)));

        logger.checkForChanges();
        outputBuffer.reset(); // Clear buffer after first poll

        // Act: Second poll finds the exact same data
        logger.checkForChanges();

        // Assert: Output should be empty because no status changed
        assertTrue(outputBuffer.toString().isEmpty(), "Should not log unchanged jobs twice");
    }

    @Test
    void testCheckForChanges_LogsFailureSummaryOnCompletion() throws Exception {
        // 1. Setup a COMPLETED run with a FAILURE conclusion
        WorkflowRun run = createMockRun(123L, Status.COMPLETED, Conclusion.FAILURE);
        run.setUpdatedAt(OffsetDateTime.now().plusMinutes(1)); // Must be AFTER setup timestamp

        WorkflowJob failedJob = createMockJob(456L, Status.COMPLETED);
        failedJob.setConclusion(Conclusion.FAILURE);
        failedJob.setCompletedAt(OffsetDateTime.now().plusMinutes(1));

        when(mockApi.getWorkflowRunsFrom(anyString())).thenReturn(List.of(run));
        when(mockApi.getJobsForWorkflowRun(123L)).thenReturn(List.of(failedJob));

        // 2. Act
        logger.checkForChanges();

        // 3. Assert
        String output = outputBuffer.toString();
        assertTrue(output.contains("FAILURE SUMMARY"), "Output was: " + output);
        assertTrue(output.contains("[Job FAILURE]"), "Output was: " + output);
    }

    // Helper Methods to generate data with current timestamps
    private WorkflowRun createMockRun(Long id, Status status, Conclusion conclusion) {
        WorkflowRun run = new WorkflowRun();
        run.setId(id);
        run.setWorkflowId(1L);
        // Ensure these aren't null because the logger calls .toString() or .equals()
        run.setStatus(status != null ? status : Status.QUEUED);
        run.setConclusion(conclusion != null ? conclusion : Conclusion.SUCCESS);
        run.setName("Build and Test");
        run.setHeadBranch("main");
        run.setHeadSha("abc1234");

        // Ensure timestamps exist for the logger's comparison logic
        OffsetDateTime now = OffsetDateTime.now();
        run.setCreatedAt(now.minusMinutes(5));
        run.setUpdatedAt(now);
        return run;
    }

    private WorkflowJob createMockJob(Long id, Status status) {
        WorkflowJob job = new WorkflowJob();
        job.setId(id);
        job.setName("Unit Tests");
        job.setStatus(status != null ? status : Status.QUEUED);
        job.setConclusion(Conclusion.SUCCESS); // Default

        OffsetDateTime now = OffsetDateTime.now();
        job.setCreatedAt(now.minusMinutes(2));
        job.setStartedAt(now.minusMinutes(1));
        job.setCompletedAt(null); // Explicitly null if IN_PROGRESS
        job.setSteps(new ArrayList<>());
        return job;
    }
}