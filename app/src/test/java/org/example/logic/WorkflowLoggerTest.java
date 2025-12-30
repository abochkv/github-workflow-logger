package org.example.logic;

import org.example.api.ApiDataRetriever;
import org.example.db.Repository;
import org.example.model.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import org.mockito.MockedStatic;

class WorkflowLoggerTest {

    @Mock
    ApiDataRetriever api;

    WorkflowLogger logger;

    MockedStatic<Repository> repositoryMock;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        repositoryMock = mockStatic(Repository.class);
        repositoryMock.when(() ->
                Repository.updateTimestamp(any(), any(), any(), any())
        ).thenAnswer(inv -> null);

        Workflow wf = mock(Workflow.class);
        when(wf.getId()).thenReturn(1L);
        when(wf.getName()).thenReturn("CI");

        when(api.getWorkflows()).thenReturn(List.of(wf));

        logger = spy(new WorkflowLogger(api));
    }

    @AfterEach
    void tearDown() {
        repositoryMock.close();
    }


    @Test
    void handleNewRepository_fetchesWorkflowsAndUpdatesTimestamp() throws Exception {
        WorkflowRun run = mockWorkflowRun(
                10L,
                Status.IN_PROGRESS,
                OffsetDateTime.now().minusHours(1)
        );

        when(api.getWorkflowRunsFrom(anyString()))
                .thenReturn(List.of(run));

        logger.handleNewRepository();

        verify(api).getWorkflowRunsFrom(anyString());
        repositoryMock.verify(() ->
                Repository.updateTimestamp(any(), any(), any(), any())
        );
    }


    @Test
    void handleExistingRepository_completedWorkflow_printsAndCaches() throws Exception {
        WorkflowRun completedRun = mockWorkflowRun(
                11L,
                Status.COMPLETED,
                OffsetDateTime.now().minusMinutes(10)
        );
        when(completedRun.getConclusion()).thenReturn(Conclusion.SUCCESS);

        when(api.getWorkflowRunsFrom(anyString()))
                .thenReturn(List.of(completedRun));
        when(api.getJobsForWorkflowRun(11L))
                .thenReturn(List.of());

        logger.handleExistingRepository(
                OffsetDateTime.now().minusDays(1).toString(),
                OffsetDateTime.now().minusDays(2).toString()
        );

        verify(api).getJobsForWorkflowRun(11L);
    }


    private WorkflowRun mockWorkflowRun(long id, Status status, OffsetDateTime createdAt) {
        WorkflowRun run = mock(WorkflowRun.class);
        when(run.getId()).thenReturn(id);
        when(run.getStatus()).thenReturn(status);
        when(run.getCreatedAt()).thenReturn(createdAt);
        when(run.getUpdatedAt()).thenReturn(createdAt.plusMinutes(1));
        when(run.getWorkflowId()).thenReturn(1L);
        when(run.getName()).thenReturn("Build");
        when(run.getHeadBranch()).thenReturn("main");
        when(run.getHeadSha()).thenReturn("abc123");
        return run;
    }
}


