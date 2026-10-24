package com.culturalevents.locations.infrastructure;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.culturalevents.locations.domain.State;
import com.culturalevents.locations.domain.StateRepository;

@Repository
class StateRepositoryAdapter implements StateRepository {

    private final SpringDataStateRepository delegate;

    StateRepositoryAdapter(SpringDataStateRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<State> findAllOrderedByName() {
        return delegate.findAllByOrderByNameAsc();
    }
}
