package com.culturalevents.venues.application;

import java.util.UUID;

/** Visão mínima de um local para outras capabilities ({@code id} interno, {@code publicId} da API). */
public record VenueRef(long id, UUID publicId, long cityId, String name, String slug) {
}
