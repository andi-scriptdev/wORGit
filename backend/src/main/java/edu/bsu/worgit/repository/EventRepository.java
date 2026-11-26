package edu.bsu.worgit.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import edu.bsu.worgit.domain.Rows;

public interface EventRepository {

    /** Events of an organization on or after the given date, soonest first. */
    List<Rows.Event> upcomingEvents(UUID orgId, LocalDate from);
}
