package club.asbl.asbl_club.rss;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.EventFeedItem;
import club.asbl.asbl_club.event.EventService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
class RssController {

    private final EventService eventService;
    private final AsblService asblService;

    RssController(EventService eventService, AsblService asblService) {
        this.eventService = eventService;
        this.asblService = asblService;
    }

    @GetMapping(value = "/events/rss", produces = "application/rss+xml; charset=UTF-8")
    String globalFeed() {
        String base = baseUrl();
        List<RssItem> items = eventService.publicFeed().stream()
                .map(event -> toItem(base, event))
                .toList();
        return RssFeed.render("asbl.club, public events", base + "/events/rss",
                "Public events across all associations", items);
    }

    @GetMapping(value = "/asbls/{slug}/events/rss", produces = "application/rss+xml; charset=UTF-8")
    String asblFeed(@PathVariable String slug) {
        Asbl asbl = asblService.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String base = baseUrl();
        List<RssItem> items = eventService.publicFeedOf(asbl).stream()
                .map(event -> toItem(base, event))
                .toList();
        return RssFeed.render(asbl.getDenomination() + ", public events",
                base + "/asbls/" + slug + "/events/rss",
                "Public events of " + asbl.getDenomination(), items);
    }

    private static String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    private static RssItem toItem(String base, EventFeedItem event) {
        return new RssItem(event.title(), base + "/events/" + event.id(), event.description(), event.startsAt());
    }
}
