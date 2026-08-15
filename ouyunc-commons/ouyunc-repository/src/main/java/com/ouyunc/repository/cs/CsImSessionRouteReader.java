package com.ouyunc.repository.cs;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Redis Hash field → {@link CsImSessionRoute}（IM 专用，不依赖 CS POJO）。 */
public final class CsImSessionRouteReader {

    private CsImSessionRouteReader() {}

    public static CsImSessionRoute read(List<String> fields, List<Object> values) {
        if (fields == null || values == null || fields.isEmpty()) {
            return null;
        }
        Map<String, String> map = new HashMap<>(fields.size());
        for (int i = 0; i < fields.size(); i++) {
            if (i >= values.size() || values.get(i) == null) {
                continue;
            }
            map.put(fields.get(i), values.get(i).toString());
        }
        return fromMap(map);
    }

    private static CsImSessionRoute fromMap(Map<String, String> map) {
        if (map.isEmpty()) {
            return null;
        }
        String ticketId = map.get(CsImSessionRouteFields.TICKET_ID);
        String sessionId = map.get(CsImSessionRouteFields.SESSION_ID);
        String userId = map.get(CsImSessionRouteFields.USER_ID);
        String serviceIdentity = map.get(CsImSessionRouteFields.SERVICE_IDENTITY);
        String assigneeId = blankToNull(map.get(CsImSessionRouteFields.ASSIGNEE_ID));
        Integer status = parseInt(map.get(CsImSessionRouteFields.STATUS));
        Integer agentType = parseInt(map.get(CsImSessionRouteFields.AGENT_TYPE));
        Long epoch = parseLong(map.get(CsImSessionRouteFields.EPOCH));
        if (StringUtils.isAnyBlank(ticketId, sessionId, userId, serviceIdentity, assigneeId)
                || status == null
                || !CsAgentType.isKnown(agentType)
                || epoch == null
                || epoch < 1L) {
            return null;
        }
        return new CsImSessionRoute(
                ticketId,
                sessionId,
                userId,
                serviceIdentity,
                assigneeId,
                status,
                map.get(CsImSessionRouteFields.CHANNEL),
                agentType,
                epoch);
    }

    private static String blankToNull(String raw) {
        return StringUtils.isBlank(raw) ? null : raw;
    }

    public static Integer parseInt(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static Long parseLong(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
