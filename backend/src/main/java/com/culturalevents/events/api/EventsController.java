package com.culturalevents.events.api;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.culturalevents.events.application.EventView;
import com.culturalevents.events.application.EventsService;
import com.culturalevents.events.domain.EventStatus;
import com.culturalevents.shared.api.EventsApi;
import com.culturalevents.shared.api.model.Event;
import com.culturalevents.shared.api.model.EventInput;
import com.culturalevents.shared.api.model.EventPage;
import com.culturalevents.shared.api.model.EventUpdateInput;
import com.culturalevents.shared.pagination.PageMetadataMapper;

@RestController
class EventsController implements EventsApi {

    private final EventsService service;

    EventsController(EventsService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<EventPage> listEvents(String city, String category, OffsetDateTime from, OffsetDateTime to,
            com.culturalevents.shared.api.model.EventStatus status, String q, Integer page, Integer size) {
        EventStatus domainStatus = status == null ? null : EventStatus.valueOf(status.name());
        Page<EventView> result = service.list(city, category, from, to, domainStatus, q, page, size);
        EventPage body = new EventPage(
                result.getContent().stream().map(EventsMapper::toDto).toList(),
                PageMetadataMapper.from(result));
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<Event> createEvent(EventInput eventInput) {
        EventView view = service.create(EventsMapper.toCommand(eventInput));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(view.event().getPublicId()).toUri();
        return ResponseEntity.created(location).body(EventsMapper.toDto(view));
    }

    @Override
    public ResponseEntity<Event> getEvent(UUID id) {
        return ResponseEntity.ok(EventsMapper.toDto(service.get(id)));
    }

    @Override
    public ResponseEntity<Event> getPublishedEvent(String uf, String slug, String eventSlug) {
        return ResponseEntity.ok(EventsMapper.toDto(service.getPublished(uf, slug, eventSlug)));
    }

    @Override
    public ResponseEntity<Event> replaceEvent(UUID id, EventUpdateInput eventUpdateInput) {
        return ResponseEntity.ok(EventsMapper.toDto(service.replace(id, EventsMapper.toCommand(eventUpdateInput))));
    }

    @Override
    public ResponseEntity<Void> deleteEvent(UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
