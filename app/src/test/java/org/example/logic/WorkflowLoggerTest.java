//package org.example.logic;
//
//import org.example.api.ApiDataRetriever;
//import org.example.db.Database;
//import org.example.db.Repository;
//import org.example.model.*;
//import org.junit.jupiter.api.*;
//import org.mockito.MockedStatic;
//
//import java.io.ByteArrayOutputStream;
//import java.io.PrintStream;
//import java.time.OffsetDateTime;
//import java.util.List;
//import java.util.Map;
//
//import static org.junit.jupiter.api.Assertions.assertTrue;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.*;
//
//class WorkflowLoggerTest {
//
//    private ApiDataRetriever api;
//    private WorkflowLogger logger;
//    private ByteArrayOutputStream outContent;
//    private MockedStatic<Repository> repositoryMock;
//    private MockedStatic<Database> databaseMock;
//
//    @BeforeEach
//    void setUp() throws Exception {
//        databaseMock = mockStatic(Database.class);
//        databaseMock.when(Database::getConnection).thenAnswer(invocation -> mock(Connection.class));
//
//        // Mock Repository.updateTimestamp as before
//        repositoryMock = mockStatic(Repository.class);
//        repositoryMock.when(() -> Repository.updateTimestamp(anyString(), anyString(), any(), any()))
//                .thenAnswer(invocation -> null);
//    }
//
//    @AfterEach
//    void tearDown() {
//        repositoryMock.close();
//        System.setOut(System.out);
//    }
//
//    @Test
//    void handleNewRepository_fetchesWorkflowRunsAndUpdatesTimestamp() throws Exception {
//        WorkflowRun run = new WorkflowRun();
//        run.setId(100L);
//        run.setWorkflowId(1L);
//        run.setName("Run1");
//        run.setStatus(Status.IN_PROGRESS);
//        run.setCreatedAt(OffsetDateTime.now().minusHours(1));
//        run.setUpdatedAt(OffsetDateTime.now());
//
//        when(api.getWorkflowRunsFrom(anyString())).thenReturn(List.of(run));
//        when(api.getJobsForWorkflowRun(run.getId())).thenReturn(List.of());
//
//        logger.handleNewRepository();
//
//        String output = outContent.toString();
//        assertTrue(output.contains("TestWorkflow"));
//        assertTrue(output.contains("Run1"));
//    }
//
//    @Test
//    void checkForChanges_detectsNewInProgressRunAndLogs() throws Exception {
//        WorkflowRun newRun = new WorkflowRun();
//        newRun.setId(200L);
//        newRun.setWorkflowId(1L);
//        newRun.setName("NewRun");
//        newRun.setStatus(Status.IN_PROGRESS);
//        newRun.setCreatedAt(OffsetDateTime.now().minusMinutes(30));
//        newRun.setUpdatedAt(OffsetDateTime.now());
//
//        WorkflowJob job = new WorkflowJob();
//        job.setId(2L);
//        job.setName("Job2");
//        job.setStatus(Status.IN_PROGRESS);
//        job.setCreatedAt(OffsetDateTime.now().minusMinutes(25));
//        job.setStartedAt(OffsetDateTime.now().minusMinutes(24));
//
//        when(api.getWorkflowRunsFrom(anyString())).thenReturn(List.of(newRun));
//        when(api.getJobsForWorkflowRun(newRun.getId())).thenReturn(List.of(job));
//
//        logger.checkForChanges();
//
//        String output = outContent.toString();
//        assertTrue(output.contains("NewRun"));
//        assertTrue(output.contains("Job2"));
//    }
//
//    @Test
//    void handleExistingRepository_completedWorkflow_printsAndCaches() throws Exception {
//        WorkflowRun completedRun = new WorkflowRun();
//        completedRun.setId(101L);
//        completedRun.setWorkflowId(1L);
//        completedRun.setName("CompletedRun");
//        completedRun.setStatus(Status.COMPLETED);
//        completedRun.setConclusion(Conclusion.FAILURE);
//        completedRun.setCreatedAt(OffsetDateTime.now().minusHours(2));
//        completedRun.setUpdatedAt(OffsetDateTime.now().minusHours(1));
//
//        WorkflowJob job = new WorkflowJob();
//        job.setId(1L);
//        job.setName("Job1");
//        job.setStatus(Status.COMPLETED);
//        job.setConclusion(Conclusion.FAILURE);
//        job.setCreatedAt(OffsetDateTime.now().minusHours(2));
//        job.setStartedAt(OffsetDateTime.now().minusHours(1));
//        job.setCompletedAt(OffsetDateTime.now());
//
//        when(api.getWorkflowRunsFrom(anyString())).thenReturn(List.of(completedRun));
//        when(api.getJobsForWorkflowRun(completedRun.getId())).thenReturn(List.of(job));
//
//        OffsetDateTime past = OffsetDateTime.now().minusDays(1);
//        logger.handleExistingRepository(past.toString(), past.toString());
//
//        String output = outContent.toString();
//        assertTrue(output.contains("CompletedRun"));
//        assertTrue(output.contains("Job1"));
//        assertTrue(output.contains("FAILURE"));
//    }
//
//    @Test
//    void handleExistingRepository_queuedWorkflow_printsAndCaches() throws Exception {
//        WorkflowRun queuedRun = new WorkflowRun();
//        queuedRun.setId(300L);
//        queuedRun.setWorkflowId(1L);
//        queuedRun.setName("QueuedRun");
//        queuedRun.setStatus(Status.QUEUED);
//        queuedRun.setCreatedAt(OffsetDateTime.now().minusMinutes(10));
//
//        when(api.getWorkflowRunsFrom(anyString())).thenReturn(List.of(queuedRun));
//
//        OffsetDateTime past = OffsetDateTime.now().minusDays(1);
//        logger.handleExistingRepository(past.toString(), past.toString());
//
//        String output = outContent.toString();
//        assertTrue(output.contains("QueuedRun"));
//    }
//}
