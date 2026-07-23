package club.asbl.asbl_club.event;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
class EventController {

    private final EventService eventService;
    private final AsblService asblService;
    private final UserService userService;
    private final MembershipService membershipService;

    EventController(EventService eventService, AsblService asblService, UserService userService,
            MembershipService membershipService) {
        this.eventService = eventService;
        this.asblService = asblService;
        this.userService = userService;
        this.membershipService = membershipService;
    }

    @GetMapping("/asbls/{slug}/events")
    String list(@PathVariable String slug, Model model, Authentication authentication) {
        User user = userService.getByEmail(authentication.getName());
        Asbl asbl = resolveForMember(slug, user);
        model.addAttribute("asbl", asbl);
        model.addAttribute("events", eventService.eventsOf(asbl));
        model.addAttribute("isAdmin", membershipService.isAdmin(user, asbl));
        return "event/list";
    }

    @GetMapping("/asbls/{slug}/events/new")
    String showForm(@PathVariable String slug, Model model, Authentication authentication) {
        User user = userService.getByEmail(authentication.getName());
        Asbl asbl = resolveForAdmin(slug, user);
        model.addAttribute("asbl", asbl);
        model.addAttribute("form", new CreateEventForm("", "", null, "", "PUBLIC"));
        return "event/create";
    }

    @PostMapping("/asbls/{slug}/events")
    String create(@PathVariable String slug, @Valid @ModelAttribute("form") CreateEventForm form,
            BindingResult bindingResult, Model model, Authentication authentication) {
        User user = userService.getByEmail(authentication.getName());
        Asbl asbl = resolveForAdmin(slug, user);
        if (bindingResult.hasErrors()) {
            model.addAttribute("asbl", asbl);
            return "event/create";
        }
        Instant startsAt = form.startsAt().atZone(ZoneId.systemDefault()).toInstant();
        eventService.createEvent(asbl, form.title(), form.description(), startsAt, form.location(), form.visibility());
        return "redirect:/asbls/" + slug + "/events";
    }

    @GetMapping("/asbls/{slug}/events/{eventId}")
    String detail(@PathVariable String slug, @PathVariable Long eventId, Model model, Authentication authentication) {
        User user = userService.getByEmail(authentication.getName());
        Asbl asbl = resolveForMember(slug, user);
        Event event = eventService.findEvent(asbl, eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("asbl", asbl);
        model.addAttribute("event", event);
        model.addAttribute("tickets", eventService.ticketCategoriesOf(event));
        model.addAttribute("isAdmin", membershipService.isAdmin(user, asbl));
        model.addAttribute("ticketForm", new AddTicketCategoryForm("", null, null));
        return "event/detail";
    }

    @PostMapping("/asbls/{slug}/events/{eventId}/tickets")
    String addTicket(@PathVariable String slug, @PathVariable Long eventId,
            @Valid @ModelAttribute("ticketForm") AddTicketCategoryForm ticketForm, BindingResult bindingResult,
            Model model, Authentication authentication) {
        User user = userService.getByEmail(authentication.getName());
        Asbl asbl = resolveForAdmin(slug, user);
        Event event = eventService.findEvent(asbl, eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (bindingResult.hasErrors()) {
            model.addAttribute("asbl", asbl);
            model.addAttribute("event", event);
            model.addAttribute("tickets", eventService.ticketCategoriesOf(event));
            model.addAttribute("isAdmin", true);
            return "event/detail";
        }
        eventService.addTicketCategory(event, ticketForm.label(), ticketForm.price(), ticketForm.totalSeats());
        return "redirect:/asbls/" + slug + "/events/" + eventId;
    }

    private Asbl resolveForMember(String slug, User user) {
        Asbl asbl = asblService.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!membershipService.isMember(user, asbl)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return asbl;
    }

    private Asbl resolveForAdmin(String slug, User user) {
        Asbl asbl = asblService.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!membershipService.isAdmin(user, asbl)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return asbl;
    }
}
