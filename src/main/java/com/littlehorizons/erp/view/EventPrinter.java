package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.event.EventCalendar;
import com.littlehorizons.erp.controller.event.EventCalendarEntry;
import com.littlehorizons.erp.controller.event.EventResults;
import com.littlehorizons.erp.controller.event.EventSummary;
import com.littlehorizons.erp.controller.event.StudentEventHistory;
import com.littlehorizons.erp.model.entity.EventParticipation;
import com.littlehorizons.erp.model.entity.SchoolEvent;

import java.util.List;
import java.util.stream.Collectors;

/** Prints events, results and histories as tables. It only formats; every number comes already calculated. */
final class EventPrinter {

    private final ConsoleOutput output;

    EventPrinter(ConsoleOutput output) {
        this.output = output;
    }

    void calendar(EventCalendar calendar) {
        output.table(List.of("Fecha", "Evento", "Tipo", "Lugar", "Competencia", "Organizador", "Participantes", "Estado"),
                calendar.entries().stream().map(this::calendarRow).toList());
        EventSummary s = calendar.summary();
        output.info("Eventos: " + s.total() + " (" + s.upcoming() + " próximos, " + s.held() + " realizados, "
                + s.cancelled() + " cancelados). Participaciones registradas: " + s.participations() + ".");
        if (!s.byType().isEmpty()) {
            output.info("Por tipo (sin cancelados): " + s.byType().entrySet().stream()
                    .map(e -> e.getKey().getDisplayName() + " " + e.getValue())
                    .collect(Collectors.joining(" - ")));
        }
    }

    private List<String> calendarRow(EventCalendarEntry entry) {
        SchoolEvent event = entry.event();
        return List.of(event.getDate().toString(), event.getName(), event.getType().getDisplayName(),
                text(event.getPlace()), event.hasCompetition() ? "Sí" : "No", event.getOrganizer().getFullName(),
                String.valueOf(entry.participants()), entry.status().getDisplayName());
    }

    /** The participants of one event; the position column appears only when the event has a competition. */
    void results(EventResults results) {
        SchoolEvent event = results.event();
        output.info("«" + event.getName() + "» - " + event.getType().getDisplayName() + " - " + event.getDate()
                + (event.isActive() ? "" : " (cancelado)"));
        output.info(event.getDescription());
        output.info("Organizador: " + event.getOrganizer().getFullName() + " - Lugar: " + text(event.getPlace())
                + " - " + (event.hasCompetition() ? "Con competencia" : "Sin competencia"));
        if (event.hasCompetition()) {
            output.table(List.of("Posición", "Estudiante", "Documento"), results.participants().stream()
                    .map(p -> List.of(p.getPosition().getDisplayName(), p.getStudent().getFullName(),
                            p.getStudent().getDocumentNumber())).toList());
            output.info(results.participants().size() + " participante(s). " + positionCounts(results));
        } else {
            output.table(List.of("Estudiante", "Documento"), results.participants().stream()
                    .map(p -> List.of(p.getStudent().getFullName(), p.getStudent().getDocumentNumber())).toList());
            output.info(results.participants().size() + " participante(s).");
        }
    }

    private static String positionCounts(EventResults results) {
        return results.positionCounts().entrySet().stream()
                .map(e -> e.getKey().getDisplayName() + ": " + e.getValue())
                .collect(Collectors.joining(" - "));
    }

    void studentHistory(StudentEventHistory history) {
        output.table(List.of("Fecha", "Evento", "Tipo", "Posición"), history.participations().stream()
                .map(p -> List.of(p.getEvent().getDate().toString(), p.getEvent().getName(),
                        p.getEvent().getType().getDisplayName(), positionText(p))).toList());
        output.info(history.participations().size() + " evento(s); " + history.podiums() + " vez/veces en el podio.");
    }

    /** The data of one event, shown before editing it. */
    void detail(SchoolEvent event) {
        output.line("  Nombre:      " + event.getName());
        output.line("  Descripción: " + event.getDescription());
        output.line("  Tipo:        " + event.getType().getDisplayName());
        output.line("  Fecha:       " + event.getDate());
        output.line("  Lugar:       " + text(event.getPlace()));
        output.line("  Competencia: " + (event.hasCompetition() ? "Sí" : "No"));
        output.line("  Organizador: " + event.getOrganizer().getFullName());
    }

    /** A participant as a line of a menu: the name and the current position. */
    static String participantLabel(EventParticipation p) {
        return p.getStudent().getFullName() + (p.getPosition() == null ? "" : " - " + p.getPosition().getDisplayName());
    }

    private static String positionText(EventParticipation p) {
        return p.getPosition() == null ? "-" : p.getPosition().getDisplayName();
    }

    private static String text(String value) {
        return value == null ? "-" : value;
    }
}
