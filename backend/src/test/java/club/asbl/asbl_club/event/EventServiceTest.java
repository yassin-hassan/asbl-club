package club.asbl.asbl_club.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.audit.AuditService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    EventRepository eventRepository;

    @Mock
    TicketCategoryRepository ticketCategoryRepository;

    @Mock
    AuditService auditService;

    @Mock
    ApplicationEventPublisher events;

    @InjectMocks
    EventService eventService;

    @Test
    void createEvent_savesADraftEvent() {
        Asbl asbl = mock(Asbl.class);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Instant startsAt = Instant.parse("2026-09-01T18:00:00Z");

        Event created = eventService.createEvent(asbl, "Concert", "Une soirée", startsAt, "Salle A", "PUBLIC");

        assertThat(created.getAsbl()).isSameAs(asbl);
        assertThat(created.getTitle()).isEqualTo("Concert");
        assertThat(created.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(created.getVisibility()).isEqualTo(EventVisibility.PUBLIC);
        verify(eventRepository).save(created);
    }
}
