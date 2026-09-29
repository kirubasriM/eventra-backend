package com.eventra.backend.service;

import com.eventra.backend.entity.EventRegistration;
import com.eventra.backend.entity.Notification;
import com.eventra.backend.repository.EventRegistrationRepository;
import com.eventra.backend.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EventRegistrationRepository eventRegistrationRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               EventRegistrationRepository eventRegistrationRepository) {
        this.notificationRepository = notificationRepository;
        this.eventRegistrationRepository = eventRegistrationRepository;
    }

    public Notification createNotification(Notification notification) {
        if (notification.getCreatedAt() == null) {
            notification.setCreatedAt(LocalDateTime.now());
        }
        if (notification.getIsRead() == null) {
            notification.setIsRead(false);
        }
        return notificationRepository.save(notification);
    }

    public Notification sendNotification(Long userId, String title, String message, String type, Long relatedEventId) {
        if (userId == null) {
            return null;
        }
        Notification notification = new Notification(userId, title, message, type, relatedEventId);
        return notificationRepository.save(notification);
    }

    public List<Notification> getNotificationsForUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Optional<Notification> markAsRead(Long id) {
        return notificationRepository.findById(id).map(notification -> {
            notification.setIsRead(true);
            return notificationRepository.save(notification);
        });
    }

    public int markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndIsReadFalse(userId);
        for (Notification n : unread) {
            n.setIsRead(true);
        }
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    public boolean deleteNotification(Long id) {
        if (notificationRepository.existsById(id)) {
            notificationRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    public int notifyEventAttendees(Long eventId, String title, String message, String type) {
        List<EventRegistration> registrations = eventRegistrationRepository.findByEventId(eventId);
        int count = 0;
        for (EventRegistration reg : registrations) {
            if (reg.getUserId() != null) {
                sendNotification(reg.getUserId(), title, message, type, eventId);
                count++;
            }
        }
        return count;
    }
}
