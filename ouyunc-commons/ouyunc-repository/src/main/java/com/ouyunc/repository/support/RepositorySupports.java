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
    public static final SpecialMessageLoader SPECIAL_MESSAGE_LOADER =
            new SpecialMessageLoader(MESSAGE_PACKET_QUERY);
    public static final WithdrawMessageSupport WITHDRAW =
            new WithdrawMessageSupport(SPECIAL_MESSAGE_LOADER, INFRA.redisTemplate);
    public static final ReadReceiptSupport READ_RECEIPT =
            new ReadReceiptSupport(SPECIAL_MESSAGE_LOADER, INFRA.redisTemplate, INFRA.mongoTemplate, INFRA.jdbcClient);
    public static final ReactiveMessageOperationSupport REACTIVE_OPERATION = new ReactiveMessageOperationSupport();
    public static final GroupMembershipSupport GROUP = new GroupMembershipSupport(INFRA, SESSION);
    public static final FriendRepositorySupport FRIEND = new FriendRepositorySupport(INFRA, SESSION);
    public static final UserRepositorySupport USER = new UserRepositorySupport(INFRA);

    private RepositorySupports() {
    }
}
