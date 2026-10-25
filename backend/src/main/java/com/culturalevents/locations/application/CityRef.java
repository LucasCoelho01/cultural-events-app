package com.culturalevents.locations.application;

/** Visão mínima de uma cidade para outras capabilities, que só precisam do id e do estado de ativação. */
public record CityRef(long id, boolean active) {
}
