package com.mudhut.nudge.tasks.models

import com.mudhut.nudge.tasks.entities.TaskPriority
import com.mudhut.nudge.tasks.entities.TaskStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.LocalDateTime

data class AssigneeDto(
    val userId: Long,
    val name: String?,
    val avatarUrl: String?,
)

data class JobSummaryDto(
    val requestId: Long,
    val title: String,
    val requestedDate: LocalDateTime?,
    val status: String,
)

data class TaskResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val status: TaskStatus,
    val priority: TaskPriority,
    val dueDate: LocalDate?,
    val job: JobSummaryDto?,
    val assignees: List<AssigneeDto>,
    val createdAt: LocalDateTime?,
    val archivedAt: LocalDateTime? = null,
)

data class CreateTaskRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val title: String,
    val description: String? = null,
    val priority: TaskPriority? = null,
    val dueDate: LocalDate? = null,
    val jobRequestId: Long? = null,
    val assigneeIds: List<Long> = emptyList(),
)

data class UpdateTaskRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val title: String,
    val description: String? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueDate: LocalDate? = null,
    val jobRequestId: Long? = null,
    val assigneeIds: List<Long> = emptyList(),
)

data class ChangeStatusRequest(
    @field:NotNull
    val status: TaskStatus,
)
