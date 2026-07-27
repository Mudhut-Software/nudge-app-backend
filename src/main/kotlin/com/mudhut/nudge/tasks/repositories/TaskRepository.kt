package com.mudhut.nudge.tasks.repositories

import com.mudhut.nudge.tasks.entities.Task
import com.mudhut.nudge.tasks.entities.TaskStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface TaskRepository : JpaRepository<Task, Long> {

    @Query(
        """
        SELECT DISTINCT t FROM Task t
        LEFT JOIN t.assignees a
        WHERE t.business.id = :businessId
          AND (:status IS NULL OR t.status = :status)
          AND (:assigneeId IS NULL OR a.user.id = :assigneeId)
          AND (:jobRequestId IS NULL OR t.jobRequestId = :jobRequestId)
        """
    )
    fun findFiltered(
        @Param("businessId") businessId: Long,
        @Param("status") status: TaskStatus?,
        @Param("assigneeId") assigneeId: Long?,
        @Param("jobRequestId") jobRequestId: Long?,
    ): List<Task>

    fun findByIdAndBusinessId(id: Long, businessId: Long): Optional<Task>
}
