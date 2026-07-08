package com.ouyunc.repository.support;

/**
 * 默认仓库各子模块单例（由 {@link com.ouyunc.repository.DefaultRepository} 委托）。
 */
public final class RepositorySupports {

    public static final RepositoryInfrastructure INFRA = RepositoryInfrastructure.createDefault();

    public static final MessageMqPublisherSupport MQ = new MessageMqPublisherSupport(INFRA);
    public static final QosRepositorySupport QOS = new QosRepositorySupport(INFRA);
    public static final SessionMessagePersistenceSupport SESSION = new SessionMessagePersistenceSupport(INFRA);
    public static final SessionIndexSupport SESSION_INDEX = new SessionIndexSupport(INFRA.stringRedisTemplate);
    public static final MessagePacketQuerySupport MESSAGE_PACKET_QUERY =
            new MessagePacketQuerySupport(INFRA.redisTemplate, INFRA.mongoTemplate, INFRA.jdbcClient);
    public static final SessionLastMessageSupport SESSION_LAST_MESSAGE =
            new SessionLastMessageSupport(INFRA.stringRedisTemplate, MESSAGE_PACKET_QUERY, SESSION);
    public static final SpecialMessageLoader SPECIAL_MESSAGE_LOADER =
            new SpecialMessageLoader(MESSAGE_PACKET_QUERY);
    public static final WithdrawMessageSupport WITHDRAW =
            new WithdrawMessageSupport(SPECIAL_MESSAGE_LOADER, INFRA.redisTemplate);
    public static final UnreadIndexSupport UNREAD = new UnreadIndexSupport(INFRA);
    public static final ReadReceiptSupport READ_RECEIPT =
            new ReadReceiptSupport(SPECIAL_MESSAGE_LOADER, INFRA.stringRedisTemplate, INFRA.mongoTemplate, INFRA.jdbcClient,
                    UNREAD);
    public static final ReactiveMessageOperationSupport REACTIVE_OPERATION = new ReactiveMessageOperationSupport();
    public static final GroupMembershipSupport GROUP = new GroupMembershipSupport(INFRA, SESSION);
    public static final FriendRepositorySupport FRIEND = new FriendRepositorySupport(INFRA, SESSION);
    public static final UserRepositorySupport USER = new UserRepositorySupport(INFRA);
    public static final DeliveryChannelSupport DELIVERY_CHANNEL =
            new DeliveryChannelSupport(FRIEND, GROUP, MQ);
    public static final CsImSessionRouteSupport CS_IM_SESSION_ROUTE =
            new CsImSessionRouteSupport(INFRA.stringRedisTemplate);
    public static final CsTicketLastMessageSupport CS_TICKET_LAST_MESSAGE =
            new CsTicketLastMessageSupport(INFRA.stringRedisTemplate, MESSAGE_PACKET_QUERY);
    public static final CsTicketUnreadSupport CS_TICKET_UNREAD =
            new CsTicketUnreadSupport(INFRA.stringRedisTemplate);
    public static final CsTicketMessagePersistenceSupport CS_TICKET_MESSAGE =
            new CsTicketMessagePersistenceSupport(SESSION, CS_TICKET_UNREAD);
    public static final CsTicketReadReceiptSupport CS_TICKET_READ_RECEIPT =
            new CsTicketReadReceiptSupport(SPECIAL_MESSAGE_LOADER, INFRA.stringRedisTemplate, CS_TICKET_UNREAD,
                    INFRA.mongoTemplate, INFRA.jdbcClient);

    private RepositorySupports() {
    }
}
