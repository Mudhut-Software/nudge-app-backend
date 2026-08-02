package com.mudhut.nudge.tasks.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessMember
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.tasks.entities.Task
import com.mudhut.nudge.tasks.entities.TaskAssignee
import com.mudhut.nudge.tasks.entities.TaskStatus
import com.mudhut.nudge.tasks.entities.TaskSubtask
import com.mudhut.nudge.tasks.models.CreateTaskRequest
import com.mudhut.nudge.tasks.repositories.TaskRepository
import com.mudhut.nudge.tasks.spi.JobSummary
import com.mudhut.nudge.tasks.spi.JobSummaryQuery
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anySet
import org.mockito.ArgumentMatchers.eq
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class TaskServiceTest {

    @Mock private lateinit var taskRepository: TaskRepository
    @Mock private lateinit var businessService: BusinessService
    @Mock private lateinit var businessMemberRepository: BusinessMemberRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var jobSummaryQuery: JobSummaryQuery

    @InjectMocks private lateinit var service: TaskService

    private fun member(userId: Long) = BusinessMember(
        id = userId, user = User(id = userId), business = Business(id = 1L),
        role = BusinessRole.STAFF, isActive = true,
    )

    @Test
    fun `create persists task with validated assignees and job link`() {
        `when`(userRepository.findByEmail("mgr@test.com"))
            .thenReturn(Optional.of(User(id = 1L, username = "Mgr")))
        `when`(businessMemberRepository.findByBusinessIdAndUserId(1L, 5L))
            .thenReturn(Optional.of(member(5L)))
        `when`(userRepository.findAllById(listOf(5L))).thenReturn(listOf(User(id = 5L, username = "Sam")))
        `when`(jobSummaryQuery.summaries(1L, setOf(100L)))
            .thenReturn(mapOf(100L to JobSummary(100L, "Deep clean", null, "CONFIRMED")))
        `when`(taskRepository.save(any(Task::class.java))).thenAnswer {
            (it.arguments[0] as Task).apply { id = 1L }
        }

        val req = CreateTaskRequest(title = "Prep kit", jobRequestId = 100L, assigneeIds = listOf(5L))
        val result = service.create("mgr@test.com", 1L, req)

        assertEquals("Prep kit", result.title)
        assertEquals(TaskStatus.TODO, result.status)
        assertEquals(100L, result.job?.requestId)
        assertEquals(listOf(5L), result.assignees.map { it.userId })
    }

    @Test
    fun `create rejects an assignee who is not an active member`() {
        `when`(businessMemberRepository.findByBusinessIdAndUserId(1L, 9L)).thenReturn(Optional.empty())

        val req = CreateTaskRequest(title = "x", assigneeIds = listOf(9L))
        assertThrows<IllegalArgumentException> { service.create("mgr@test.com", 1L, req) }
    }

    @Test
    fun `create rejects a job that does not belong to the business`() {
        `when`(jobSummaryQuery.summaries(1L, setOf(404L))).thenReturn(emptyMap())

        val req = CreateTaskRequest(title = "x", jobRequestId = 404L)
        assertThrows<IllegalArgumentException> { service.create("mgr@test.com", 1L, req) }
    }

    @Test
    fun `create requires MANAGER`() {
        `when`(businessService.requireRole(1L, "staff@test.com", BusinessRole.MANAGER))
            .thenThrow(RuntimeException("denied"))

        assertThrows<RuntimeException> {
            service.create("staff@test.com", 1L, CreateTaskRequest(title = "x"))
        }
    }

    @Test
    fun `list returns mapped tasks with batched job summaries`() {
        val task = Task(id = 1L, business = Business(id = 1L), title = "A", jobRequestId = 100L,
            createdBy = User(id = 5L, username = "Sam"))
        task.assignees.add(TaskAssignee(id = 1L, task = task, user = User(id = 5L, username = "Sam")))
        `when`(taskRepository.findFiltered(1L, null, null, null, false)).thenReturn(listOf(task))
        `when`(jobSummaryQuery.summaries(1L, setOf(100L)))
            .thenReturn(mapOf(100L to JobSummary(100L, "Deep clean", null, "CONFIRMED")))

        val result = service.list("any@test.com", 1L, null, null, null, false)

        assertEquals(1, result.size)
        assertEquals("Deep clean", result[0].job?.title)
        assertEquals("Sam", result[0].assignees[0].name)
    }

    @Test
    fun `list requires STAFF membership`() {
        org.mockito.Mockito.doThrow(com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException("no"))
            .`when`(businessService).requireRole(1L, "stranger@test.com", BusinessRole.STAFF)

        assertThrows<com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException> {
            service.list("stranger@test.com", 1L, null, null, null, false)
        }

        org.mockito.Mockito.verify(taskRepository, org.mockito.Mockito.never())
            .findFiltered(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(),
            )
    }

    @Test
    fun `changeStatus allowed for MANAGER`() {
        val task = Task(id = 7L, business = Business(id = 1L), title = "A",
            createdBy = User(id = 1L, username = "M"))
        `when`(taskRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(task))
        `when`(taskRepository.save(any(Task::class.java))).thenAnswer { it.arguments[0] as Task }
        `when`(jobSummaryQuery.summaries(eq(1L), anySet())).thenReturn(emptyMap())

        val result = service.changeStatus("mgr@test.com", 1L, 7L, TaskStatus.DONE)

        assertEquals(TaskStatus.DONE, result.status)
    }

    @Test
    fun `changeStatus allowed for a STAFF assignee`() {
        val staff = User(id = 5L, username = "Sam")
        val task = Task(id = 7L, business = Business(id = 1L), title = "A",
            createdBy = User(id = 1L, username = "M"))
        task.assignees.add(TaskAssignee(id = 1L, task = task, user = staff))
        org.mockito.Mockito.doNothing().`when`(businessService)
            .requireRole(1L, "sam@test.com", BusinessRole.STAFF)
        `when`(businessService.requireRole(1L, "sam@test.com", BusinessRole.MANAGER))
            .thenThrow(com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException("no"))
        `when`(taskRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(task))
        `when`(userRepository.findByEmail("sam@test.com")).thenReturn(Optional.of(staff))
        `when`(taskRepository.save(any(Task::class.java))).thenAnswer { it.arguments[0] as Task }
        `when`(jobSummaryQuery.summaries(eq(1L), anySet())).thenReturn(emptyMap())

        val result = service.changeStatus("sam@test.com", 1L, 7L, TaskStatus.IN_PROGRESS)

        assertEquals(TaskStatus.IN_PROGRESS, result.status)
    }

    @Test
    fun `changeStatus denied for a non-assignee STAFF`() {
        val task = Task(id = 7L, business = Business(id = 1L), title = "A",
            createdBy = User(id = 1L, username = "M"))
        org.mockito.Mockito.doNothing().`when`(businessService)
            .requireRole(1L, "other@test.com", BusinessRole.STAFF)
        `when`(businessService.requireRole(1L, "other@test.com", BusinessRole.MANAGER))
            .thenThrow(com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException("no"))
        `when`(taskRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(task))
        `when`(userRepository.findByEmail("other@test.com"))
            .thenReturn(Optional.of(User(id = 8L, username = "Other")))

        assertThrows<com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException> {
            service.changeStatus("other@test.com", 1L, 7L, TaskStatus.DONE)
        }
    }

    @Test
    fun `update replaces the assignee set`() {
        val task = Task(id = 7L, business = Business(id = 1L), title = "Old",
            createdBy = User(id = 1L, username = "M"))
        task.assignees.add(TaskAssignee(id = 1L, task = task, user = User(id = 5L, username = "Sam")))
        `when`(taskRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(task))
        `when`(businessMemberRepository.findByBusinessIdAndUserId(1L, 6L)).thenReturn(Optional.of(member(6L)))
        `when`(userRepository.findAllById(listOf(6L))).thenReturn(listOf(User(id = 6L, username = "Bea")))
        `when`(taskRepository.save(any(Task::class.java))).thenAnswer { it.arguments[0] as Task }
        `when`(jobSummaryQuery.summaries(eq(1L), anySet())).thenReturn(emptyMap())

        val result = service.update("mgr@test.com", 1L, 7L,
            com.mudhut.nudge.tasks.models.UpdateTaskRequest(title = "New", assigneeIds = listOf(6L)))

        assertEquals("New", result.title)
        assertEquals(listOf(6L), result.assignees.map { it.userId })
    }

    @Test
    fun `update is a full replace, clearing description, dueDate, jobRequestId, and assignees absent from the request`() {
        val task = Task(
            id = 7L, business = Business(id = 1L), title = "Old",
            description = "Old description",
            dueDate = java.time.LocalDate.of(2026, 1, 1),
            jobRequestId = 100L,
            createdBy = User(id = 1L, username = "M"),
        )
        task.assignees.add(TaskAssignee(id = 1L, task = task, user = User(id = 5L, username = "Sam")))
        `when`(taskRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(task))
        `when`(taskRepository.save(any(Task::class.java))).thenAnswer { it.arguments[0] as Task }
        `when`(jobSummaryQuery.summaries(eq(1L), anySet())).thenReturn(emptyMap())

        // A conventional replace payload only sends title; description/dueDate/jobRequestId/assigneeIds
        // are left at their DTO defaults (null/empty), which must clear the previously-set values.
        val result = service.update(
            "mgr@test.com", 1L, 7L,
            com.mudhut.nudge.tasks.models.UpdateTaskRequest(title = "Old"),
        )

        assertEquals("Old", result.title)
        assertEquals(null, result.description)
        assertEquals(null, result.dueDate)
        assertEquals(null, result.job)
        assertEquals(emptyList<Long>(), result.assignees.map { it.userId })
    }

    @Test
    fun `update keeps existing assignee rows when the new set overlaps`() {
        val task = Task(id = 7L, business = Business(id = 1L), title = "Old",
            createdBy = User(id = 1L, username = "M"))
        val existingAssigneeForUser5 = TaskAssignee(id = 1L, task = task, user = User(id = 5L, username = "Sam"))
        task.assignees.add(existingAssigneeForUser5)
        task.assignees.add(TaskAssignee(id = 2L, task = task, user = User(id = 6L, username = "Bea")))
        `when`(taskRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(task))
        `when`(businessMemberRepository.findByBusinessIdAndUserId(1L, 5L)).thenReturn(Optional.of(member(5L)))
        `when`(businessMemberRepository.findByBusinessIdAndUserId(1L, 7L)).thenReturn(Optional.of(member(7L)))
        `when`(userRepository.findAllById(listOf(7L))).thenReturn(listOf(User(id = 7L, username = "Lee")))
        `when`(taskRepository.save(any(Task::class.java))).thenAnswer { it.arguments[0] as Task }
        `when`(jobSummaryQuery.summaries(eq(1L), anySet())).thenReturn(emptyMap())

        val result = service.update("mgr@test.com", 1L, 7L,
            com.mudhut.nudge.tasks.models.UpdateTaskRequest(title = "New", assigneeIds = listOf(5L, 7L)))

        assertEquals(setOf(5L, 7L), result.assignees.map { it.userId }.toSet())
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).findAllById(listOf(5L))
        assert(task.assignees.contains(existingAssigneeForUser5)) {
            "expected the original user-5 TaskAssignee instance to survive the update untouched"
        }
    }

    @Test
    fun `update rejects a blank title`() {
        val task = Task(id = 7L, business = Business(id = 1L), title = "Old",
            createdBy = User(id = 1L, username = "M"))
        `when`(taskRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(task))

        assertThrows<IllegalArgumentException> {
            service.update("mgr@test.com", 1L, 7L,
                com.mudhut.nudge.tasks.models.UpdateTaskRequest(title = "   "))
        }
    }

    @Test
    fun `update rejects a job that does not belong to the business`() {
        val task = Task(id = 7L, business = Business(id = 1L), title = "Old",
            createdBy = User(id = 1L, username = "M"))
        `when`(taskRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(task))
        `when`(jobSummaryQuery.summaries(1L, setOf(404L))).thenReturn(emptyMap())

        assertThrows<IllegalArgumentException> {
            service.update("mgr@test.com", 1L, 7L,
                com.mudhut.nudge.tasks.models.UpdateTaskRequest(title = "New", jobRequestId = 404L))
        }
    }

    @Test
    fun `changeStatus denied for a non-member before the task is loaded`() {
        `when`(businessService.requireRole(1L, "stranger@test.com", BusinessRole.STAFF))
            .thenThrow(com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException("no"))

        assertThrows<com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException> {
            service.changeStatus("stranger@test.com", 1L, 7L, TaskStatus.DONE)
        }

        org.mockito.Mockito.verify(taskRepository, org.mockito.Mockito.never())
            .findByIdAndBusinessId(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong())
    }

    @Test
    fun `delete requires MANAGER`() {
        `when`(businessService.requireRole(1L, "staff@test.com", BusinessRole.MANAGER))
            .thenThrow(com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException("no"))

        assertThrows<com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException> {
            service.delete("staff@test.com", 1L, 7L)
        }
    }

    @Test
    fun `archive stamps archivedAt on a DONE task`() {
        val task = Task(id = 7L, business = Business(id = 1L), title = "A",
            status = TaskStatus.DONE, createdBy = User(id = 1L, username = "M"))
        `when`(taskRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(task))
        `when`(taskRepository.save(any(Task::class.java))).thenAnswer { it.arguments[0] as Task }
        `when`(jobSummaryQuery.summaries(eq(1L), anySet())).thenReturn(emptyMap())

        val result = service.archive("mgr@test.com", 1L, 7L)

        org.junit.jupiter.api.Assertions.assertNotNull(result.archivedAt)
    }

    @Test
    fun `archive rejects a task that is not DONE`() {
        val task = Task(id = 7L, business = Business(id = 1L), title = "A",
            status = TaskStatus.IN_PROGRESS, createdBy = User(id = 1L, username = "M"))
        `when`(taskRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(task))

        assertThrows<IllegalArgumentException> { service.archive("mgr@test.com", 1L, 7L) }
    }

    @Test
    fun `archive rejects an already-archived task`() {
        val task = Task(id = 7L, business = Business(id = 1L), title = "A",
            status = TaskStatus.DONE, createdBy = User(id = 1L, username = "M"))
        task.archivedAt = java.time.LocalDateTime.now()
        `when`(taskRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(task))

        assertThrows<IllegalArgumentException> { service.archive("mgr@test.com", 1L, 7L) }
    }

    @Test
    fun `archive requires MANAGER`() {
        `when`(businessService.requireRole(1L, "staff@test.com", BusinessRole.MANAGER))
            .thenThrow(com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException("no"))

        assertThrows<com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException> {
            service.archive("staff@test.com", 1L, 7L)
        }
    }

    @Test
    fun `unarchive clears archivedAt`() {
        val task = Task(id = 7L, business = Business(id = 1L), title = "A",
            status = TaskStatus.DONE, createdBy = User(id = 1L, username = "M"))
        task.archivedAt = java.time.LocalDateTime.now()
        `when`(taskRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(task))
        `when`(taskRepository.save(any(Task::class.java))).thenAnswer { it.arguments[0] as Task }
        `when`(jobSummaryQuery.summaries(eq(1L), anySet())).thenReturn(emptyMap())

        val result = service.unarchive("mgr@test.com", 1L, 7L)

        org.junit.jupiter.api.Assertions.assertNull(result.archivedAt)
    }

    @Test
    fun `unarchive rejects a task that is not archived`() {
        val task = Task(id = 7L, business = Business(id = 1L), title = "A",
            status = TaskStatus.DONE, createdBy = User(id = 1L, username = "M"))
        `when`(taskRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(task))

        assertThrows<IllegalArgumentException> { service.unarchive("mgr@test.com", 1L, 7L) }
    }

    @Test
    fun `archiveDone archives every live DONE task and returns the count`() {
        val t1 = Task(id = 1L, business = Business(id = 1L), title = "A",
            status = TaskStatus.DONE, createdBy = User(id = 1L, username = "M"))
        val t2 = Task(id = 2L, business = Business(id = 1L), title = "B",
            status = TaskStatus.DONE, createdBy = User(id = 1L, username = "M"))
        `when`(taskRepository.findAllByBusinessIdAndStatusAndArchivedAtIsNull(1L, TaskStatus.DONE))
            .thenReturn(listOf(t1, t2))
        `when`(taskRepository.saveAll(org.mockito.ArgumentMatchers.anyList<Task>()))
            .thenAnswer { it.arguments[0] }

        val count = service.archiveDone("mgr@test.com", 1L)

        assertEquals(2, count)
        org.junit.jupiter.api.Assertions.assertNotNull(t1.archivedAt)
        org.junit.jupiter.api.Assertions.assertNotNull(t2.archivedAt)
    }

    private fun taskWithSubtask(done: Boolean = false): Task {
        val task = Task(id = 7L, business = Business(id = 1L), title = "A",
            createdBy = User(id = 1L, username = "M"))
        task.subtasks.add(TaskSubtask(id = 21L, task = task, title = "step one", done = done))
        return task
    }

    @Test
    fun `addSubtask appends and returns the subtask in the response`() {
        val task = Task(id = 7L, business = Business(id = 1L), title = "A",
            createdBy = User(id = 1L, username = "M"))
        `when`(taskRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(task))
        // Real IDENTITY-strategy saves assign a generated id synchronously; simulate that here so
        // the mapped SubtaskDto (whose id is non-nullable) has something to read.
        `when`(taskRepository.save(any(Task::class.java))).thenAnswer {
            val saved = it.arguments[0] as Task
            saved.subtasks.filter { s -> s.id == null }.forEach { s -> s.id = 21L }
            saved
        }
        `when`(jobSummaryQuery.summaries(eq(1L), anySet())).thenReturn(emptyMap())

        val result = service.addSubtask("mgr@test.com", 1L, 7L, "buy supplies")

        assertEquals(listOf("buy supplies"), result.subtasks.map { it.title })
        assertEquals(listOf(false), result.subtasks.map { it.done })
    }

    @Test
    fun `addSubtask requires MANAGER`() {
        `when`(businessService.requireRole(1L, "staff@test.com", BusinessRole.MANAGER))
            .thenThrow(com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException("no"))

        assertThrows<com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException> {
            service.addSubtask("staff@test.com", 1L, 7L, "x")
        }
    }

    @Test
    fun `toggleSubtask allowed for a STAFF assignee`() {
        val staff = User(id = 5L, username = "Sam")
        val task = taskWithSubtask()
        task.assignees.add(TaskAssignee(id = 1L, task = task, user = staff))
        org.mockito.Mockito.doNothing().`when`(businessService)
            .requireRole(1L, "sam@test.com", BusinessRole.STAFF)
        `when`(businessService.requireRole(1L, "sam@test.com", BusinessRole.MANAGER))
            .thenThrow(com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException("no"))
        `when`(taskRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(task))
        `when`(userRepository.findByEmail("sam@test.com")).thenReturn(Optional.of(staff))
        `when`(taskRepository.save(any(Task::class.java))).thenAnswer { it.arguments[0] as Task }
        `when`(jobSummaryQuery.summaries(eq(1L), anySet())).thenReturn(emptyMap())

        val result = service.toggleSubtask("sam@test.com", 1L, 7L, 21L, true)

        assertEquals(listOf(true), result.subtasks.map { it.done })
    }

    @Test
    fun `toggleSubtask denied for a non-assignee STAFF`() {
        val task = taskWithSubtask()
        org.mockito.Mockito.doNothing().`when`(businessService)
            .requireRole(1L, "other@test.com", BusinessRole.STAFF)
        `when`(businessService.requireRole(1L, "other@test.com", BusinessRole.MANAGER))
            .thenThrow(com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException("no"))
        `when`(taskRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(task))
        `when`(userRepository.findByEmail("other@test.com"))
            .thenReturn(Optional.of(User(id = 8L, username = "Other")))

        assertThrows<com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException> {
            service.toggleSubtask("other@test.com", 1L, 7L, 21L, true)
        }
    }

    @Test
    fun `toggleSubtask denied for a non-member before the task is loaded`() {
        `when`(businessService.requireRole(1L, "stranger@test.com", BusinessRole.STAFF))
            .thenThrow(com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException("no"))

        assertThrows<com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException> {
            service.toggleSubtask("stranger@test.com", 1L, 7L, 21L, true)
        }

        org.mockito.Mockito.verify(taskRepository, org.mockito.Mockito.never())
            .findByIdAndBusinessId(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong())
    }

    @Test
    fun `toggleSubtask 404s for a subtask that is not on the task`() {
        val task = taskWithSubtask()
        `when`(taskRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(task))

        assertThrows<jakarta.persistence.EntityNotFoundException> {
            service.toggleSubtask("mgr@test.com", 1L, 7L, 999L, true)
        }
    }

    @Test
    fun `create persists inline subtasks in order`() {
        `when`(userRepository.findByEmail("mgr@test.com"))
            .thenReturn(Optional.of(User(id = 1L, username = "Mgr")))
        `when`(taskRepository.save(any(Task::class.java))).thenAnswer {
            (it.arguments[0] as Task).apply {
                id = 1L
                subtasks.forEachIndexed { i, s -> s.id = (i + 1).toLong() }
            }
        }
        `when`(jobSummaryQuery.summaries(eq(1L), anySet())).thenReturn(emptyMap())

        val req = CreateTaskRequest(
            title = "Deep clean",
            subtasks = listOf(
                com.mudhut.nudge.tasks.models.CreateSubtaskRequest("buy mops"),
                com.mudhut.nudge.tasks.models.CreateSubtaskRequest("mix solution"),
            ),
        )
        val result = service.create("mgr@test.com", 1L, req)

        assertEquals(listOf("buy mops", "mix solution"), result.subtasks.map { it.title })
        assertEquals(listOf(false, false), result.subtasks.map { it.done })
    }

    @Test
    fun `deleteSubtask removes the row and requires MANAGER`() {
        val task = taskWithSubtask()
        `when`(taskRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(task))
        `when`(taskRepository.save(any(Task::class.java))).thenAnswer { it.arguments[0] as Task }
        `when`(jobSummaryQuery.summaries(eq(1L), anySet())).thenReturn(emptyMap())

        val result = service.deleteSubtask("mgr@test.com", 1L, 7L, 21L)

        assertEquals(emptyList<Long>(), result.subtasks.map { it.id })

        `when`(businessService.requireRole(1L, "staff@test.com", BusinessRole.MANAGER))
            .thenThrow(com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException("no"))
        assertThrows<com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException> {
            service.deleteSubtask("staff@test.com", 1L, 7L, 21L)
        }
    }
}
