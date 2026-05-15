package com.globaleyes.crawler.service.acled;

import com.globaleyes.crawler.pojo.entity.acled.AcledEvent;
import com.globaleyes.crawler.repository.acled.AcledEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ACLED数据服务
 * 负责数据保存和删除操作
 *
 * @author ACLED Integration Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcledDataService {

    private final AcledEventRepository eventRepository;

    /**
     * 批量保存事件（优化版本）
     */
    @Transactional
    public int[] saveEvents(List<AcledEvent> events) {
        if (events == null || events.isEmpty()) {
            return new int[]{0, 0};
        }

        List<String> eventIds = events.stream()
                .map(AcledEvent::getEventIdCnty)
                .toList();

        Set<String> existingIds = eventRepository.findExistingIdsByEventIdCntyIn(eventIds);

        Map<String, AcledEvent> existingEventMap = new java.util.HashMap<>();
        if (!existingIds.isEmpty()) {
            List<AcledEvent> existingEvents = eventRepository.findByEventIdCntyIn(existingIds);
            existingEventMap = existingEvents.stream()
                    .collect(Collectors.toMap(AcledEvent::getEventIdCnty, Function.identity()));
        }

        List<AcledEvent> toInsert = new ArrayList<>();
        List<AcledEvent> toUpdate = new ArrayList<>();

        for (AcledEvent event : events) {
            String eventId = event.getEventIdCnty();
            if (existingIds.contains(eventId)) {
                AcledEvent existing = existingEventMap.get(eventId);
                if (existing != null) {
                    event.setId(existing.getId());
                    event.setCreatedAt(existing.getCreatedAt());
                    event.setUpdatedAt(LocalDateTime.now());
                    toUpdate.add(event);
                }
            } else {
                toInsert.add(event);
            }
        }

        int inserted = 0;
        int updated = 0;

        if (!toInsert.isEmpty()) {
            eventRepository.saveAll(toInsert);
            inserted = toInsert.size();
        }

        if (!toUpdate.isEmpty()) {
            eventRepository.saveAll(toUpdate);
            updated = toUpdate.size();
        }

        log.info("Saved events: inserted={}, updated={}", inserted, updated);
        return new int[]{inserted, updated};
    }

    /**
     * 批量删除事件
     */
    @Transactional
    public int deleteEvents(List<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return 0;
        }
        eventRepository.deleteByEventIdCntyIn(eventIds);
        return eventIds.size();
    }
}
