package com.eventra.backend.repository;

import com.eventra.backend.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByStatus(String status);

    List<Event> findByCreatedBy(Long createdBy);

    List<Event> findByCreatedByOrderByIdDesc(Long createdBy);

    List<Event> findByCategoryIgnoreCase(String category);

    @Query("SELECT e FROM Event e WHERE " +
           "(e.title IS NOT NULL AND LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) OR " +
           "(e.category IS NOT NULL AND LOWER(e.category) LIKE LOWER(CONCAT('%', :keyword, '%'))) OR " +
           "(e.city IS NOT NULL AND LOWER(e.city) LIKE LOWER(CONCAT('%', :keyword, '%'))) OR " +
           "(e.college IS NOT NULL AND LOWER(e.college) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Event> searchEvents(@Param("keyword") String keyword);

    @Query("SELECT e FROM Event e WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           " LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(e.category) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " (e.city IS NOT NULL AND LOWER(e.city) LIKE LOWER(CONCAT('%', :keyword, '%'))) OR " +
           " (e.college IS NOT NULL AND LOWER(e.college) LIKE LOWER(CONCAT('%', :keyword, '%')))) AND " +
           "(:category IS NULL OR :category = '' OR LOWER(:category) = 'all' OR LOWER(e.category) = LOWER(:category) OR LOWER(e.category) LIKE LOWER(CONCAT('%', :category, '%'))) AND " +
           "(:city IS NULL OR :city = '' OR LOWER(:city) = 'all' OR (e.city IS NOT NULL AND LOWER(e.city) = LOWER(:city))) AND " +
           "(:eventDate IS NULL OR e.eventDate = :eventDate)")
    List<Event> filterEvents(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("city") String city,
            @Param("eventDate") LocalDate eventDate);

    @Query("SELECT DISTINCT e.city FROM Event e WHERE e.city IS NOT NULL AND TRIM(e.city) != '' ORDER BY e.city ASC")
    List<String> findDistinctCities();
}
