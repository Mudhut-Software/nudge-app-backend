package com.mudhut.nudge.users.services.helpers

import com.mudhut.nudge.users.repositories.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class NudgeUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByEmail(username)
            .orElseThrow { UsernameNotFoundException("User not found with username: $username") }

        val authorities = listOf(
            SimpleGrantedAuthority("ROLE_${user.role?.name ?: "BASIC_USER"}")
        )

        return org.springframework.security.core.userdetails.User(
            user.email,
            user.password,
            authorities
        )
    }
}
