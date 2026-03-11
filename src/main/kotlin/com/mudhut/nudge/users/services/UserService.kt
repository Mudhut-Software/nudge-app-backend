package com.mudhut.nudge.users.services

import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository
) {

    fun getUserById(id: Long): User =
        userRepository.findById(id)
            .orElseThrow { EntityNotFoundException("User not found with id: $id") }

    fun getAllUsers(): List<User> = userRepository.findAll()

    fun updateUser(id: Long, userDetails: User): User {
        val user = getUserById(id)
        return userRepository.save(user)
    }

    fun deleteUser(id: Long) {
        val user = getUserById(id)
        userRepository.delete(user)
    }
}
