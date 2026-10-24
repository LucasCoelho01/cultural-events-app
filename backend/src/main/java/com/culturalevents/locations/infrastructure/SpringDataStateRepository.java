package com.culturalevents.locations.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.culturalevents.locations.domain.State;

interface SpringDataStateRepository extends JpaRepository<State, String> {

    List<State> findAllByOrderByNameAsc();
}
