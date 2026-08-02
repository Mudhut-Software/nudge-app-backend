package com.mudhut.nudge.tasks.controllers

import tools.jackson.databind.ObjectMapper
import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.tasks.entities.TaskPriority
import com.mudhut.nudge.tasks.entities.TaskStatus
import com.mudhut.nudge.tasks.models.CreateTaskRequest
import com.mudhut.nudge.tasks.models.TaskResponse
import com.mudhut.nudge.tasks.models.UpdateTaskRequest
import com.mudhut.nudge.tasks.services.TaskService
import com.mudhut.nudge.users.services.NudgeUserDetailsService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@WebMvcTest(TaskController::class)
@Import(SecurityConfig::class, PassThroughJwtFilterConfig::class, JsonAuthenticationEntryPoint::class, JsonAccessDeniedHandler::class)
@AutoConfigureMockMvc
class TaskControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @MockitoBean private lateinit var taskService: TaskService
    @MockitoBean private lateinit var userDetailsService: NudgeUserDetailsService

    private fun <T> anyObject(): T {
        Mockito.any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    private fun <T> eqObject(value: T): T {
        Mockito.eq(value)
        return value
    }

    private fun response() = TaskResponse(
        id = 1L, title = "Prep kit", description = null, status = TaskStatus.TODO,
        priority = TaskPriority.MEDIUM, dueDate = null, job = null, assignees = emptyList(),
        createdAt = null,
    )

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `GET lists tasks`() {
        `when`(taskService.list(anyString(), eq(1L), isNull(), isNull(), isNull(), eq(false)))
            .thenReturn(listOf(response()))

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/businesses/1/tasks"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].title").value("Prep kit"))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `POST creates a task`() {
        `when`(taskService.create(anyString(), eq(1L), anyObject()))
            .thenReturn(response())

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/businesses/1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateTaskRequest(title = "Prep kit"))),
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.title").value("Prep kit"))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `POST create accepts inline subtasks`() {
        `when`(taskService.create(anyString(), eq(1L), anyObject()))
            .thenReturn(response())

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/businesses/1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    mapOf("title" to "Deep clean", "subtasks" to listOf(mapOf("title" to "buy mops"))),
                )),
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.title").value("Prep kit"))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `POST create rejects a blank inline subtask title`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/businesses/1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    mapOf("title" to "Deep clean", "subtasks" to listOf(mapOf("title" to ""))),
                )),
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
        org.mockito.Mockito.verify(taskService, org.mockito.Mockito.never())
            .create(anyString(), org.mockito.ArgumentMatchers.anyLong(), anyObject())
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `PUT replaces a task`() {
        `when`(taskService.update(anyString(), eq(1L), eq(1L), anyObject()))
            .thenReturn(response())

        mockMvc.perform(
            MockMvcRequestBuilders.put("/api/v1/businesses/1/tasks/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(UpdateTaskRequest(title = "Prep kit"))),
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.title").value("Prep kit"))
    }

    @Test
    fun `GET is unauthorized without auth`() {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/businesses/1/tasks"))
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `PUT with a blank title returns 400`() {
        val body = mapOf(
            "title" to "",
            "description" to null,
            "priority" to "MEDIUM",
            "dueDate" to null,
            "jobRequestId" to null,
            "assigneeIds" to emptyList<Long>(),
        )

        mockMvc.perform(
            MockMvcRequestBuilders.put("/api/v1/businesses/1/tasks/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)

        Mockito.verify(taskService, Mockito.never())
            .update(anyString(), anyLong(), anyLong(), anyObject())
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `PATCH status routes and returns the updated task`() {
        `when`(taskService.changeStatus(anyString(), eq(1L), eq(1L), eqObject(TaskStatus.DONE)))
            .thenReturn(response())

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/api/v1/businesses/1/tasks/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("status" to "DONE"))),
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.title").value("Prep kit"))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `DELETE removes a task`() {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/businesses/1/tasks/1"))
            .andExpect(MockMvcResultMatchers.status().isNoContent)

        Mockito.verify(taskService).delete("mgr@test.com", 1L, 1L)
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `GET binds populated query params`() {
        `when`(taskService.list(anyString(), eq(1L), eq(TaskStatus.TODO), eq(5L), eq(100L), eq(false)))
            .thenReturn(listOf(response()))

        mockMvc.perform(
            MockMvcRequestBuilders.get("/api/v1/businesses/1/tasks")
                .param("status", "TODO")
                .param("assigneeId", "5")
                .param("jobRequestId", "100"),
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].title").value("Prep kit"))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `GET passes archived=true through`() {
        `when`(taskService.list(anyString(), eq(1L), isNull(), isNull(), isNull(), eq(true)))
            .thenReturn(listOf(response()))

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/businesses/1/tasks?archived=true"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].title").value("Prep kit"))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `PATCH archive routes`() {
        `when`(taskService.archive(anyString(), eq(1L), eq(1L))).thenReturn(response())

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/businesses/1/tasks/1/archive"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(1))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `PATCH unarchive routes`() {
        `when`(taskService.unarchive(anyString(), eq(1L), eq(1L))).thenReturn(response())

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/businesses/1/tasks/1/unarchive"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(1))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `PATCH archive-done returns the count`() {
        `when`(taskService.archiveDone(anyString(), eq(1L))).thenReturn(3)

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/businesses/1/tasks/archive-done"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.archived").value(3))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `POST subtask routes and validates blank title`() {
        `when`(taskService.addSubtask(anyString(), eq(1L), eq(1L), eqObject("buy supplies")))
            .thenReturn(response())

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/businesses/1/tasks/1/subtasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("title" to "buy supplies"))),
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(1))

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/businesses/1/tasks/1/subtasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("title" to ""))),
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
        org.mockito.Mockito.verify(taskService, org.mockito.Mockito.never())
            .addSubtask(anyString(), eq(1L), eq(1L), eqObject(""))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `PATCH subtask toggles done`() {
        `when`(taskService.toggleSubtask(anyString(), eq(1L), eq(1L), eq(21L), eq(true)))
            .thenReturn(response())

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/api/v1/businesses/1/tasks/1/subtasks/21")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("done" to true))),
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(1))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `PATCH subtask without a done flag returns 400`() {
        mockMvc.perform(
            MockMvcRequestBuilders.patch("/api/v1/businesses/1/tasks/1/subtasks/21")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptyMap<String, Any>())),
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)

        Mockito.verify(taskService, Mockito.never())
            .toggleSubtask(anyString(), anyLong(), anyLong(), anyLong(), Mockito.anyBoolean())
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `DELETE subtask routes`() {
        `when`(taskService.deleteSubtask(anyString(), eq(1L), eq(1L), eq(21L))).thenReturn(response())

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/businesses/1/tasks/1/subtasks/21"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(1))
    }
}
