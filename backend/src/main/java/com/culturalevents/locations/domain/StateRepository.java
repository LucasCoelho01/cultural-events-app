package com.culturalevents.locations.domain;

import java.util.List;

public interface StateRepository {

    List<State> findAllOrderedByName();
}
