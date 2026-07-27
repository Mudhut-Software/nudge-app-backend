package com.mudhut.nudge.tasks.controllers

import com.mudhut.nudge.tasks.entities.TaskStatus
import com.mudhut.nudge.tasks.models.ChangeStatusRequest
import com.mudhut.nudge.tasks.models.CreateTaskRequest
import com.mudhut.nudge.tasks.models.TaskResponse
import com.mudhut.nudge.tasks.models.UpdateTaskRequest
import com.mudhut.nudge.tasks.services.TaskService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/tasks")
class TaskController(
    private val taskService: TaskService,
) {

    @GetMapping
    fun list(
        @PathVariable businessId: Long,
        @RequestParam(required = false) status: TaskStatus?,
        @RequestParam(required = false) assigneeId: Long?,
        @RequestParam(required = false) jobRequestId: Long?,
        authentication: Authentication,
    ): ResponseEntity<List<TaskResponse>> =
        ResponseEntity.ok(taskService.list(authentication.name, businessId, status, assigneeId, jobRequestId))

    @PostMapping
    fun create(
        @PathVariable businessId: Long,
        @Valid @RequestBody request: CreateTaskRequest,
        authentication: Authentication,
    ): ResponseEntity<TaskResponse> =
        ResponseEntity.ok(taskService.create(authentication.name, businessId, request))

    /**
     * Full replace of the task's editable fields — clients must send the complete desired state;
     * omitted optional fields are cleared.
     */
    @PutMapping("/{taskId}")
    fun update(
        @PathVariable businessId: Long,
        @PathVariable taskId: Long,
        @Valid @RequestBody request: UpdateTaskRequest,
        authentication: Authentication,
    ): ResponseEntity<TaskResponse> =
        ResponseEntity.ok(taskService.update(authentication.name, businessId, taskId, request))

    @PatchMapping("/{taskId}/status")
    fun changeStatus(
        @PathVariable businessId: Long,
        @PathVariable taskId: Long,
        @Valid @RequestBody request: ChangeStatusRequest,
        authentication: Authentication,
    ): ResponseEntity<TaskResponse> =
        ResponseEntity.ok(taskService.changeStatus(authentication.name, businessId, taskId, request.status))

    @DeleteMapping("/{taskId}")
    fun delete(
        @PathVariable businessId: Long,
        @PathVariable taskId: Long,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        taskService.delete(authentication.name, businessId, taskId)
        return ResponseEntity.noContent().build()
    }
}
