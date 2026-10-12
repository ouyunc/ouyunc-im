package com.ouyunc.message.cs;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.enums.MessageFromToTypeEnum;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.repository.cs.CsAgentType;
import com.ouyunc.repository.cs.CsImSessionRoute;
import com.ouyunc.repository.cs.CsMessageScopeHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CsTicketUnreadSideTest {

    @Test
    void visitorMessageCountsOnStaffWatermark() {
        CsImSessionRoute route = route();
        Message message = message("visitor-1", MessageFromToTypeEnum.CS_VISITOR.getType());
        assertEquals(CacheConstant.CS_TICKET_UNREAD_STAFF, CsMessageScopeHelper.incomingUnreadSide(message, route));
        assertEquals(CacheConstant.CS_TICKET_UNREAD_VISITOR, CsMessageScopeHelper.authorReadSide(message, route));
    }

    @Test
    void anyAgentOrRobotSharesStaffWatermark() {
        CsImSessionRoute route = route();
        Message agent = message("agent-b", MessageFromToTypeEnum.CS_AGENT.getType());
        Message robot = message("robot-1", MessageFromToTypeEnum.BOT.getType());
        assertEquals(CacheConstant.CS_TICKET_UNREAD_VISITOR, CsMessageScopeHelper.incomingUnreadSide(agent, route));
        assertEquals(CacheConstant.CS_TICKET_UNREAD_STAFF, CsMessageScopeHelper.authorReadSide(agent, route));
        assertEquals(CacheConstant.CS_TICKET_UNREAD_STAFF, CsMessageScopeHelper.authorReadSide(robot, route));
    }

    @Test
    void systemNoticeIsNotUnread() {
        CsImSessionRoute route = route();
        Message system = message("system", MessageFromToTypeEnum.SYSTEM.getType());
        assertNull(CsMessageScopeHelper.incomingUnreadSide(system, route));
        assertNull(CsMessageScopeHelper.authorReadSide(system, route));
    }

    private static Message message(String from, int fromType) {
        Message message = new Message();
        message.setFrom(from);
        message.setFromType(fromType);
        return message;
    }

    private static CsImSessionRoute route() {
        return new CsImSessionRoute(
                "100", "s", "visitor-1", "entry", "agent-a", 1, "im", "im",
                CsAgentType.HUMAN, 1L);
    }
}
