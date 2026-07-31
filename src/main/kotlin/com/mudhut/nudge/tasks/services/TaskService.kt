package com.mudhut.nudge.tasks.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.tasks.entities.Task
import com.mudhut.nudge.tasks.entities.TaskAssignee
import com.mudhut.nudge.tasks.entities.TaskStatus
import com.mudhut.nudge.tasks.entities.TaskSubtask
import com.mudhut.nudge.tasks.models.AssigneeDto
import com.mudhut.nudge.tasks.models.CreateTaskRequest
import com.mudhut.nudge.tasks.models.JobSummaryDto
import com.mudhut.nudge.tasks.models.SubtaskDto
import com.mudhut.nudge.tasks.models.TaskResponse
import com.mudhut.nudge.tasks.models.UpdateTaskRequest
import com.mudhut.nudge.tasks.repositories.TaskRepository
import com.mudhut.nudge.tasks.spi.JobSummary
import com.mudhut.nudge.tasks.spi.JobSummaryQuery
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class TaskService(
    private val taskRepository: TaskRepository,
    private val businessService: BusinessService,
    private val businessMemberRepository: BusinessMemberRepository,
    private val userRepository: UserRepository,
    private val jobSummaryQuery: JobSummaryQuery,
) {

    @Transactional(readOnly = true)
    fun list(
        email: String,
        businessId: Long,
        status: TaskStatus?,
        assigneeId: Long?,
        jobRequestId: Long?,
        archived: Boolean,
    ): List<TaskResponse> {
        businessService.requireRole(businessId, email, BusinessRole.STAFF)
        val tasks = taskRepository.findFiltered(businessId, status, assigneeId, jobRequestId, archived)
        val summaries = jobSummaryQuery.summaries(businessId, tasks.mapNotNull { it.jobRequestId }.toSet())
        return tasks.map { toResponse(it, summaries) }
    }

    @Transactional
    fun create(email: String, businessId: Long, req: CreateTaskRequest): TaskResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        validateAssignees(businessId, req.assigneeIds)
        validateJob(businessId, req.jobRequestId)

        val creator = userRepository.findByEmail(email)
            .orElseThrow { EntityNotFoundException("User not found") }
        val task = Task(
            business = Business(id = businessId),
            title = req.title,
            description = req.description,
            priority = req.priority ?: com.mudhut.nudge.tasks.entities.TaskPriority.MEDIUM,
            dueDate = req.dueDate,
            jobRequestId = req.jobRequestId,
            createdBy = creator,
        )
        setAssignees(task, req.assigneeIds)
        return saveAndMap(businessId, task)
    }

    @Transactional
    fun update(email: String, businessId: Long, taskId: Long, req: UpdateTaskRequest): TaskResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val task = taskRepository.findByIdAndBusinessId(taskId, businessId)
            .orElseThrow { EntityNotFoundException("Task not found") }

        require(req.title.isNotBlank()) { "Title must not be blank" }
        task.title = req.title
        task.description = req.description
        task.priority = req.priority
        task.dueDate = req.dueDate
        if (req.jobRequestId != null) {
            validateJob(businessId, req.jobRequestId)
        }
        task.jobRequestId = req.jobRequestId
        validateAssignees(businessId, req.assigneeIds)
        setAssignees(task, req.assigneeIds)

        return saveAndMap(businessId, task)
    }

    @Transactional
    fun changeStatus(email: String, businessId: Long, taskId: Long, status: TaskStatus): TaskResponse {
        businessService.requireRole(businessId, email, BusinessRole.STAFF)
        val task = taskRepository.findByIdAndBusinessId(taskId, businessId)
            .orElseThrow { EntityNotFoundException("Task not found") }

        requireManagerOrAssignee(businessId, email, task)

        task.status = status
        return saveAndMap(businessId, task)
    }

    @Transactional
    fun delete(email: String, businessId: Long, taskId: Long) {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val task = taskRepository.findByIdAndBusinessId(taskId, businessId)
            .orElseThrow { EntityNotFoundException("Task not found") }
        taskRepository.delete(task)
    }

    @Transactional
    fun addSubtask(email: String, businessId: Long, taskId: Long, title: String): TaskResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val task = taskRepository.findByIdAndBusinessId(taskId, businessId)
            .orElseThrow { EntityNotFoundException("Task not found") }
        task.subtasks.add(TaskSubtask(task = task, title = title))
        return saveAndMap(businessId, task)
    }

    @Transactional
    fun toggleSubtask(email: String, businessId: Long, taskId: Long, subtaskId: Long, done: Boolean): TaskResponse {
        businessService.requireRole(businessId, email, BusinessRole.STAFF)
        val task = taskRepository.findByIdAndBusinessId(taskId, businessId)
            .orElseThrow { EntityNotFoundException("Task not found") }
        requireManagerOrAssignee(businessId, email, task)
        val subtask = task.subtasks.find { it.id == subtaskId }
            ?: throw EntityNotFoundException("Subtask not found")
        subtask.done = done
        return saveAndMap(businessId, task)
    }

    @Transactional
    fun deleteSubtask(email: String, businessId: Long, taskId: Long, subtaskId: Long): TaskResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val task = taskRepository.findByIdAndBusinessId(taskId, businessId)
            .orElseThrow { EntityNotFoundException("Task not found") }
        val subtask = task.subtasks.find { it.id == subtaskId }
            ?: throw EntityNotFoundException("Subtask not found")
        task.subtasks.remove(subtask)
        return saveAndMap(businessId, task)
    }

    @Transactional
    fun archive(email: String, businessId: Long, taskId: Long): TaskResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val task = taskRepository.findByIdAndBusinessId(taskId, businessId)
            .orElseThrow { EntityNotFoundException("Task not found") }
        require(task.status == TaskStatus.DONE) { "Only DONE tasks can be archived" }
        require(task.archivedAt == null) { "Task is already archived" }
        task.archivedAt = LocalDateTime.now()
        return saveAndMap(businessId, task)
    }

    @Transactional
    fun unarchive(email: String, businessId: Long, taskId: Long): TaskResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val task = taskRepository.findByIdAndBusinessId(taskId, businessId)
            .orElseThrow { EntityNotFoundException("Task not found") }
        require(task.archivedAt != null) { "Task is not archived" }
        task.archivedAt = null
        return saveAndMap(businessId, task)
    }

    @Transactional
    fun archiveDone(email: String, businessId: Long): Int {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val done = taskRepository.findAllByBusinessIdAndStatusAndArchivedAtIsNull(businessId, TaskStatus.DONE)
        val now = LocalDateTime.now()
        done.forEach { it.archivedAt = now }
        taskRepository.saveAll(done)
        return done.size
    }

    private fun canManage(businessId: Long, email: String): Boolean =
        try {
            businessService.requireRole(businessId, email, BusinessRole.MANAGER)
            true
        } catch (_: BusinessAccessDeniedException) {
            false
        }

    private fun isAssignee(task: Task, email: String): Boolean {
        val user = userRepository.findByEmail(email).orElse(null) ?: return false
        return task.assignees.any { it.user?.id == user.id }
    }

    private fun requireManagerOrAssignee(businessId: Long, email: String, task: Task) {
        if (!canManage(businessId, email) && !isAssignee(task, email)) {
            throw BusinessAccessDeniedException("You can only modify tasks assigned to you")
        }
    }

    private fun saveAndMap(businessId: Long, task: Task): TaskResponse {
        val saved = taskRepository.save(task)
        val summaries = jobSummaryQuery.summaries(businessId, listOfNotNull(saved.jobRequestId).toSet())
        return toResponse(saved, summaries)
    }

    // --- shared helpers (also used by Task 4) ---

    internal fun validateAssignees(businessId: Long, assigneeIds: List<Long>) {
        assigneeIds.distinct().forEach { userId ->
            val member = businessMemberRepository.findByBusinessIdAndUserId(businessId, userId)
                .orElseThrow { IllegalArgumentException("User $userId is not a member of this business") }
            require(member.isActive) { "User $userId is not an active member" }
        }
    }

    internal fun validateJob(businessId: Long, jobRequestId: Long?) {
        if (jobRequestId == null) return
        val found = jobSummaryQuery.summaries(businessId, setOf(jobRequestId)).containsKey(jobRequestId)
        require(found) { "Job $jobRequestId does not belong to this business" }
    }

    internal fun setAssignees(task: Task, assigneeIds: List<Long>) {
        val desiredIds = assigneeIds.distinct().toSet()
        // Diff instead of clear-and-recreate: Hibernate flushes new inserts before orphan-removal
        // deletes, so clearing then re-adding an id that's still desired would insert a duplicate
        // (task_id, user_id) row before the delete for the old row runs, violating the unique constraint.
        task.assignees.removeIf { it.user?.id !in desiredIds }
        val existingIds = task.assignees.mapNotNull { it.user?.id }.toSet()
        val missingIds = desiredIds.filterNot { it in existingIds }
        if (missingIds.isNotEmpty()) {
            val users = userRepository.findAllById(missingIds)
            users.forEach { user -> task.assignees.add(TaskAssignee(task = task, user = user)) }
        }
    }

    internal fun toResponse(task: Task, summaries: Map<Long, JobSummary>): TaskResponse {
        val job = task.jobRequestId?.let { summaries[it] }?.let {
            JobSummaryDto(it.requestId, it.title, it.requestedDate, it.status)
        }
        return TaskResponse(
            id = task.id!!,
            title = task.title!!,
            description = task.description,
            status = task.status,
            priority = task.priority,
            dueDate = task.dueDate,
            job = job,
            assignees = task.assignees.map {
                AssigneeDto(it.user!!.id!!, it.user!!.username, it.user!!.avatarUrl)
            },
            createdAt = task.createdAt,
            archivedAt = task.archivedAt,
            subtasks = task.subtasks.map { SubtaskDto(it.id!!, it.title!!, it.done) },
        )
    }
}
