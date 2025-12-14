package com.ktb.chatapp.repository;

import com.ktb.chatapp.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);

    /**
     * Batch lookup for users by IDs.
     * Solves N+1 problem by fetching multiple users in a single query using MongoDB $in operator.
     *
     * @param userIds collection of user IDs to fetch
     * @return list of users found (may be less than requested if some IDs don't exist)
     */
    List<User> findByIdIn(Collection<String> userIds);
}
