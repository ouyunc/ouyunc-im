/**
 * 客服静态页 Mock API（与 ouyunc-cs-web 接口形状对齐）
 * 后续联调时将 MOCK_ENABLED 设为 false 并配置 API_BASE / WS_URL 即可。
 */
(function (global) {
  'use strict';

  const MOCK_ENABLED =
    typeof location !== 'undefined' && new URLSearchParams(location.search).get('mock') === '1';
  const API_BASE =
    (typeof localStorage !== 'undefined' && localStorage.getItem('cs_api_base')) || '/api/cs';
  const DEFAULT_APP_KEY = 'ouyunc';
  const DEFAULT_MERCHANT_ID = 'merchant-default';
  const DEFAULT_ENTRY_CODE = 'web';
  const MOCK_DELAY_MS = 400;

  /** @type {Map<string, { agentId?: string, inQueue?: boolean, queuePosition?: number, enqueueAt?: number, skill?: string, message?: string }>} */
  const customerState = new Map();

  /** @type {Map<string, 'online'|'away'|'dnd'|'offline'>} */
  const agentStatus = new Map([
    ['agent-001', 'online'],
    ['agent-002', 'online'],
    ['agent-003', 'away'],
  ]);

  const agents = [
    { agentId: 'agent-001', agentName: '坐席一', jobNo: 'NO001', skills: ['GENERAL', 'REFUND'], agentLocale: 'zh', maxConcurrent: 5, email: 'agent001@example.com' },
    { agentId: 'agent-002', agentName: '坐席二', jobNo: 'NO002', skills: ['GENERAL', 'TECH_SUPPORT'], agentLocale: 'zh', maxConcurrent: 5, email: 'agent002@example.com' },
    { agentId: 'agent-003', agentName: '坐席三', jobNo: 'NO003', skills: ['GENERAL'], agentLocale: 'zh', maxConcurrent: 3, email: 'agent003@example.com' },
  ];

  const ADMIN_CONFIG_KEY = 'cs_admin_config';
  const AGENT_PROFILE_KEY = 'cs_agent_profiles';
  const QUICK_REPLY_KEY = 'cs_agent_quick_replies';

  /** @type {Array<{id:string,code:string,name:string,description:string,enabled:boolean,agentIds:string[]}>} */
  let skillGroups = [
    { id: 'sk-general', code: 'GENERAL', name: '通用咨询', description: '产品、价格、功能咨询', enabled: true, agentIds: ['agent-001', 'agent-002', 'agent-003'] },
    { id: 'sk-refund', code: 'REFUND', name: '售后退款', description: '退款、退货、投诉处理', enabled: true, agentIds: ['agent-001'] },
    { id: 'sk-tech', code: 'TECH_SUPPORT', name: '技术支持', description: '部署、API、故障排查', enabled: true, agentIds: ['agent-002'] },
  ];

  /** @type {Array<{id:string,name:string,timezone:string,days:number[],startTime:string,endTime:string,agentIds:string[],enabled:boolean}>} */
  let schedules = [
    { id: 'sch-1', name: '工作日白班', timezone: 'Asia/Shanghai', days: [1, 2, 3, 4, 5], startTime: '09:00', endTime: '18:00', agentIds: ['agent-001', 'agent-002'], enabled: true },
    { id: 'sch-2', name: '周末值守', timezone: 'Asia/Shanghai', days: [6, 0], startTime: '10:00', endTime: '16:00', agentIds: ['agent-003'], enabled: true },
  ];

  let routingRules = {
    strategy: 'SKILL_THEN_LEAST_LOAD',
    skillMatchRequired: true,
    allowOverflowToGeneral: true,
    maxQueueWaitSeconds: 300,
    maxConcurrentPerAgent: 5,
    vipPriority: true,
    vipQueueBoost: 2,
    stickyAgent: false,
    stickyHours: 24,
    offlineFallback: 'QUEUE',
  };

  let generalConfig = {
    appKey: DEFAULT_APP_KEY,
    businessHours: { start: '09:00', end: '22:00', timezone: 'Asia/Shanghai' },
    queueTimeoutSeconds: 300,
    queueMessage: '当前坐席繁忙，请稍候…',
    welcomeMessage: '您好，欢迎咨询偶遇云客服，请问有什么可以帮您？',
    offlineMessage: '非工作时间，请留言或发送邮件至 support@example.com',
    satisfactionEnabled: true,
    satisfactionPrompt: '请为本次服务打分（1-5）',
    sessionAutoCloseMinutes: 30,
    transferEnabled: false,
  };

  function readJsonStorage(key, fallback) {
    try {
      const raw = localStorage.getItem(key);
      return raw ? JSON.parse(raw) : fallback;
    } catch (_) {
      return fallback;
    }
  }

  function writeJsonStorage(key, value) {
    localStorage.setItem(key, JSON.stringify(value));
  }

  function loadAdminConfig() {
    const saved = readJsonStorage(ADMIN_CONFIG_KEY, null);
    if (saved) {
      if (saved.generalConfig) generalConfig = { ...generalConfig, ...saved.generalConfig };
      if (saved.routingRules) routingRules = { ...routingRules, ...saved.routingRules };
      if (saved.skillGroups) skillGroups = saved.skillGroups;
      if (saved.schedules) schedules = saved.schedules;
    }
  }

  function saveAdminConfig() {
    writeJsonStorage(ADMIN_CONFIG_KEY, { generalConfig, routingRules, skillGroups, schedules });
  }

  loadAdminConfig();

  function defaultAgentProfile(agentId) {
    const a = agents.find((x) => x.agentId === agentId) || { agentId, agentName: agentId, agentLocale: 'zh' };
    return {
      agentId,
      displayName: a.agentName,
      email: a.email || agentId + '@example.com',
      phone: '138****' + String(agentId).slice(-4),
      avatar: '',
      signature: '专业、耐心、高效',
      agentLocale: a.agentLocale || 'zh',
      notifySound: true,
      notifyDesktop: true,
      notifyNewSession: true,
      autoTranslateIn: true,
      autoTranslateOut: true,
    };
  }

  function getAgentProfile(agentId) {
    const all = readJsonStorage(AGENT_PROFILE_KEY, {});
    return { ...defaultAgentProfile(agentId), ...(all[agentId] || {}) };
  }

  function saveAgentProfile(agentId, patch) {
    const all = readJsonStorage(AGENT_PROFILE_KEY, {});
    all[agentId] = { ...getAgentProfile(agentId), ...patch, agentId };
    writeJsonStorage(AGENT_PROFILE_KEY, all);
    const idx = agents.findIndex((a) => a.agentId === agentId);
    if (idx >= 0 && patch.displayName) agents[idx].agentName = patch.displayName;
    return all[agentId];
  }

  function defaultQuickReplies(agentId) {
    const name = (agents.find((a) => a.agentId === agentId) || {}).agentName || '客服';
    return [
      { id: 'qr-1', category: 'greeting', shortcut: '/hi', title: '开场问候', content: '您好，我是' + name + '，很高兴为您服务，请问有什么可以帮您？', sort: 1 },
      { id: 'qr-2', category: 'greeting', shortcut: '/wait', title: '请稍候', content: '请稍候，我帮您查询一下。', sort: 2 },
      { id: 'qr-3', category: 'closing', shortcut: '/bye', title: '结束语', content: '感谢您的咨询，祝您生活愉快！如有其他问题欢迎随时联系我们。', sort: 3 },
      { id: 'qr-4', category: 'faq', shortcut: '/refund', title: '退款说明', content: '退款一般 3-7 个工作日原路退回，银行卡可能多 1-2 个工作日。', sort: 4 },
      { id: 'qr-5', category: 'faq', shortcut: '/order', title: '索要订单号', content: '请您提供一下订单号，我立即为您查询处理进度。', sort: 5 },
    ];
  }

  function readQuickReplies(agentId) {
    const all = readJsonStorage(QUICK_REPLY_KEY, {});
    return all[agentId] || defaultQuickReplies(agentId);
  }

  function writeQuickReplies(agentId, list) {
    const all = readJsonStorage(QUICK_REPLY_KEY, {});
    all[agentId] = list;
    writeJsonStorage(QUICK_REPLY_KEY, all);
    return list;
  }

  const QUICK_REPLY_CATEGORY_LABELS = { greeting: '问候', closing: '结束', faq: '常见问题', other: '其他' };

  const LANG_LABELS = { zh: '中文', en: 'English', ja: '日本語', ko: '한국어', es: 'Español' };

  /** 双向 Mock 翻译表（联调时替换为真实翻译服务） */
  const MOCK_TRANSLATE_PAIRS = {
    'en|zh': {
      'I would like to know about your product pricing plans.': '我想了解一下产品套餐价格',
      'How much is the Pro plan?': '专业版多少钱？',
      'What is included in the Enterprise plan?': '企业版包含哪些内容？',
      'Thank you for your help!': '谢谢您的帮助！',
    },
    'zh|en': {
      '我想了解一下产品套餐价格': 'I would like to know about your product pricing plans.',
      '专业版多少钱？': 'How much is the Pro plan?',
      '您好，我是坐席一，我们有基础版/专业版/企业版三档套餐。': 'Hello, I am Agent 1. We offer Basic, Pro, and Enterprise plans.',
      '专业版年费 ¥19,800，含 50 坐席与 API 接入。': 'The Pro plan is ¥19,800/year, including 50 agents and API access.',
      '请提供订单号，我帮您查询。': 'Please provide your order number and I will check for you.',
      '我的订单退款什么时候到账？': 'When will my order refund arrive?',
      '已为您创建跟进工单': 'A follow-up ticket has been created for you',
    },
    'ja|zh': {
      'プロプランの料金を教えてください。': '专业版多少钱？',
      '返金はいつ届きますか？': '退款什么时候到账？',
    },
    'zh|ja': {
      '专业版多少钱？': 'プロプランの料金を教えてください。',
      '请提供订单号，我帮您查询。': '注文番号を教えていただければ確認いたします。',
    },
    'ko|zh': {
      '환불은 언제 되나요?': '退款什么时候到账？',
    },
    'zh|ko': {
      '请提供订单号，我帮您查询。': '주문 번호를 알려주시면 확인해 드리겠습니다.',
    },
  };

  function normalizeLang(code) {
    if (!code) return 'zh';
    const c = String(code).toLowerCase();
    if (c.startsWith('zh')) return 'zh';
    if (c.startsWith('en')) return 'en';
    if (c.startsWith('ja')) return 'ja';
    if (c.startsWith('ko')) return 'ko';
    if (c.startsWith('es')) return 'es';
    return c.slice(0, 2);
  }

  function detectLang(text) {
    const t = String(text || '');
    if (/[\u3040-\u30ff]/.test(t)) return 'ja';
    if (/[\uac00-\ud7af]/.test(t)) return 'ko';
    if (/[\u4e00-\u9fff]/.test(t)) return 'zh';
    if (/[a-zA-Z]/.test(t)) return 'en';
    return 'zh';
  }

  function mockTranslateText(text, sourceLang, targetLang) {
    const from = normalizeLang(sourceLang);
    const to = normalizeLang(targetLang);
    if (!text || from === to) {
      return { text: text || '', sourceLang: from, targetLang: to, provider: 'noop' };
    }
    const key = from + '|' + to;
    const table = MOCK_TRANSLATE_PAIRS[key];
    if (table && table[text]) {
      return { text: table[text], sourceLang: from, targetLang: to, provider: 'mock-dict' };
    }
    const label = LANG_LABELS[to] || to;
    return {
      text: '[' + label + '] ' + text,
      sourceLang: from,
      targetLang: to,
      provider: 'mock-fallback',
    };
  }

  /** @type {Array<{id:string,customerId:string,customerName:string,preview:string,skill:string,channel:string,startTime:string,sessionId:string,messages:Array}>} */
  let activeSessions = [
    {
      id: 'ticket-1001',
      customerId: 'visitor-001',
      customerName: 'John (US)',
      customerLocale: 'en',
      preview: 'I would like to know about your product pricing plans.',
      skill: 'GENERAL',
      channel: 'WEB',
      startTime: '10:23',
      sessionId: 'sessionId(visitor-001,agent-001)',
      messages: [
        { dir: 'in', text: 'I would like to know about your product pricing plans.', time: '10:23', lang: 'en' },
        { dir: 'out', text: 'Hello, I am Agent 1. We offer Basic, Pro, and Enterprise plans.', time: '10:24', lang: 'en', originalText: '您好，我是坐席一，我们有基础版/专业版/企业版三档套餐。', originalLang: 'zh' },
        { dir: 'in', text: 'How much is the Pro plan?', time: '10:25', lang: 'en' },
      ],
    },
    {
      id: 'ticket-1002',
      customerId: 'user-18888888888',
      customerName: '用户188****8888',
      customerLocale: 'zh',
      preview: '订单退款什么时候到账',
      skill: 'REFUND',
      channel: 'APP',
      startTime: '10:15',
      sessionId: 'sessionId(user-18888888888,agent-001)',
      messages: [
        { dir: 'in', text: '我的订单退款什么时候到账？', time: '10:15', lang: 'zh' },
        { dir: 'out', text: '请提供订单号，我帮您查询。', time: '10:16', lang: 'zh' },
      ],
    },
    {
      id: 'ticket-1003',
      customerId: 'visitor-jp-001',
      customerName: '田中さん',
      customerLocale: 'ja',
      preview: 'プロプランの料金を教えてください。',
      skill: 'GENERAL',
      channel: 'WEB',
      startTime: '11:02',
      sessionId: 'sessionId(visitor-jp-001,agent-001)',
      messages: [
        { dir: 'in', text: 'プロプランの料金を教えてください。', time: '11:02', lang: 'ja' },
      ],
    },
  ];

  let hostedSessions = [];

  /** @type {Array<{customerId:string,customerName:string,preview:string,skill:string,waitSeconds:number}>} */
  let queueList = [
    { customerId: 'queue-001', customerName: '排队用户A', preview: '服务器连接失败', skill: 'TECH_SUPPORT', waitSeconds: 45 },
    { customerId: 'queue-002', customerName: '排队用户B', preview: '想咨询发票', skill: 'GENERAL', waitSeconds: 32 },
    { customerId: 'queue-003', customerName: '排队用户C', preview: '退款进度', skill: 'REFUND', waitSeconds: 18 },
  ];

  let routeCallCount = 0;
  let todayServedCount = 18;
  let workOrderSeq = 1003;
  let lobbyOnlineSecondsBase = 8100;

  const AGENT_SESSION_STORAGE_KEY = 'cs_agent_session';

  function readSignedInSession() {
    try {
      const raw = localStorage.getItem(AGENT_SESSION_STORAGE_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (_) {
      return null;
    }
  }

  function writeSignedInSession(session) {
    if (session) localStorage.setItem(AGENT_SESSION_STORAGE_KEY, JSON.stringify(session));
    else localStorage.removeItem(AGENT_SESSION_STORAGE_KEY);
  }

  function formatDuration(seconds) {
    const s = Math.max(0, Math.floor(seconds));
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    if (h > 0) return h + 'h ' + m + 'm';
    return m + 'm';
  }

  function buildAgentDashboard(agentId) {
    const agent = agents.find((a) => a.agentId === agentId) || { agentId, agentName: agentId, jobNo: '—', skills: [] };
    const status = agentStatus.get(agentId) || 'offline';
    const sess = readSignedInSession();
    let onlineDurationSeconds = lobbyOnlineSecondsBase;
    if (sess && sess.agentId === agentId && sess.signedInAt) {
      onlineDurationSeconds = lobbyOnlineSecondsBase + Math.floor((Date.now() - sess.signedInAt) / 1000);
    }
    const myActive = activeSessions.filter((s) => s.sessionId && s.sessionId.includes(agentId));
    const woToday = workOrders.length;
    const recent = [
      ...activeSessions.slice(0, 3).map((s) => ({
        id: s.id,
        customerName: s.customerName,
        preview: s.preview,
        status: '进行中',
        time: s.startTime,
      })),
      {
        id: 'ticket-0998',
        customerName: '用户188****8888',
        preview: '申请退款',
        status: '已关闭',
        time: '06-20 09:35',
      },
    ].slice(0, 5);
    return {
      agent: {
        ...agent,
        status,
        statusLabel: { online: '在线', away: '暂离', dnd: '勿扰', offline: '离线' }[status] || status,
        skillsLabel: (agent.skills || []).map((sk) => normalizeSkill(sk)),
      },
      stats: {
        todayServed: todayServedCount,
        activeCount: activeSessions.length,
        myActiveCount: myActive.length,
        queueCount: queueList.length,
        satisfactionAvg: 4.6,
        onlineDurationSeconds,
        onlineDurationLabel: formatDuration(onlineDurationSeconds),
        workOrderCountToday: woToday,
        avgFirstResponseSeconds: 28,
        avgFirstResponseLabel: '28s',
      },
      recentSessions: recent,
      announcements: [
        { id: 1, title: '退款类进线请先核对订单号', time: '今天 09:00' },
        { id: 2, title: '专业版价格以知识库 kb-3 为准', time: '昨天 18:00' },
      ],
    };
  }

  /** @type {Array<{id:string,consultationTicketId:string,customerId:string,title:string,type:string,priority:string,status:string,description:string,createdAt:string}>} */
  let workOrders = [
    {
      id: 'WO-2024-1001',
      consultationTicketId: 'ticket-1002',
      customerId: 'user-18888888888',
      title: '退款到账跟进',
      type: 'REFUND',
      priority: 'high',
      status: 'processing',
      description: '客户咨询退款进度，需财务确认',
      createdAt: '今天 09:40',
    },
  ];

  const kbArticles = [
    { id: 'kb-1', title: '退款一般 3-7 个工作日到账', snippet: '原路退回；银行卡可能多 1-2 个工作日。', category: 'knowledge' },
    { id: 'kb-2', title: '如何查询退款状态', snippet: 'APP → 我的订单 → 退款详情，或提供订单号由坐席查询。', category: 'knowledge' },
    { id: 'kb-3', title: '专业版套餐说明', snippet: '专业版含 50 坐席、API 接入、工单模块，年费 ¥19,800。', category: 'knowledge' },
    { id: 'kb-4', title: '企业版与专业版差异', snippet: '企业版额外支持 SSO、专属部署、SLA 99.9%。', category: 'knowledge' },
    { id: 'kb-5', title: '服务器连接失败排查', snippet: '检查网络、防火墙 6003 端口、appKey 与签名是否正确。', category: 'knowledge' },
  ];

  const customerOrders = {
    'visitor-001': [{ id: 'ORD-88201', title: '专业版试用订单', status: '已支付', amount: '¥0 试用' }],
    'user-18888888888': [{ id: 'ORD-77102', title: '基础版年费', status: '退款中', amount: '¥3,600' }],
    'queue-001': [],
    'queue-002': [{ id: 'ORD-99001', title: '发票申请', status: '待开票', amount: '¥12,000' }],
  };

  const productDocs = [
    { id: 'doc-1', title: 'IM 接入指南 v6.5', snippet: 'WebSocket /ws，登录 scope，messageType 说明。', category: 'doc' },
    { id: 'doc-2', title: '客服路由配置手册', snippet: '技能队列、allocate-lock、enqueue-on-failure 参数。', category: 'doc' },
  ];

  /** 咨询单状态：1-进行中 2-已关闭 3-已转接 */
  const TICKET_STATUS = { 1: '进行中', 2: '已关闭', 3: '已转接' };

  /** record_type：1-首次接入 2-转接转入 3-转出 4-关单 */
  const RECORD_TYPE = { 1: '首次接入', 2: '转接转入', 3: '转出', 4: '关单' };

  /** 全量咨询单（含历史进线，与 activeSessions 中进行中单对应） */
  const consultationTickets = [
    {
      id: 'ticket-1001', userId: 'visitor-001', status: 1, skill: 'GENERAL', channel: 'WEB',
      assigneeId: 'agent-001', assigneeName: '坐席一', startTime: '今天 10:23', endTime: null,
      queueWaitSeconds: 28, workOrderCount: 0, followUpPending: false, isRobot: false,
      preview: '产品套餐价格咨询', satisfaction: null, remark: null,
    },
    {
      id: 'ticket-1002', userId: 'user-18888888888', status: 1, skill: 'REFUND', channel: 'APP',
      assigneeId: 'agent-001', assigneeName: '坐席一', startTime: '今天 10:15', endTime: null,
      queueWaitSeconds: 0, workOrderCount: 1, followUpPending: true, isRobot: false,
      preview: '退款到账咨询', satisfaction: null, remark: '已建工单 WO-2024-1001，待财务回复',
    },
    {
      id: 'ticket-1003', userId: 'visitor-jp-001', status: 1, skill: 'GENERAL', channel: 'WEB',
      assigneeId: 'agent-001', assigneeName: '坐席一', startTime: '今天 11:02', endTime: null,
      queueWaitSeconds: 12, workOrderCount: 0, followUpPending: false, isRobot: false,
      preview: '专业版价格', satisfaction: null, remark: null,
    },
    {
      id: 'ticket-0901', userId: 'visitor-001', status: 2, skill: 'GENERAL', channel: 'WEB',
      assigneeId: 'agent-002', assigneeName: '坐席二', startTime: '06-18 14:20', endTime: '06-18 14:45',
      queueWaitSeconds: 15, workOrderCount: 0, followUpPending: false, isRobot: false,
      preview: '试用版功能咨询', satisfaction: 5, remark: '客户满意，未续费',
    },
    {
      id: 'ticket-0850', userId: 'visitor-001', status: 2, skill: 'TECH_SUPPORT', channel: 'APP',
      assigneeId: 'agent-002', assigneeName: '坐席二', startTime: '05-22 16:08', endTime: '05-22 16:30',
      queueWaitSeconds: 62, workOrderCount: 1, followUpPending: false, isRobot: false,
      preview: 'WebSocket 连接失败', satisfaction: 4, remark: '已指引检查端口与签名',
    },
    {
      id: 'ticket-0998', userId: 'user-18888888888', status: 2, skill: 'REFUND', channel: 'APP',
      assigneeId: 'agent-001', assigneeName: '坐席一', startTime: '06-20 09:10', endTime: '06-20 09:35',
      queueWaitSeconds: 0, workOrderCount: 1, followUpPending: false, isRobot: false,
      preview: '申请退款', satisfaction: 4, remark: '转接后完成，工单已关闭',
    },
    {
      id: 'ticket-0970', userId: 'user-18888888888', status: 2, skill: 'GENERAL', channel: 'H5',
      assigneeId: 'agent-003', assigneeName: '坐席三', startTime: '06-05 11:00', endTime: '06-05 11:12',
      queueWaitSeconds: 8, workOrderCount: 0, followUpPending: false, isRobot: false,
      preview: '发票开具咨询', satisfaction: 5, remark: null,
    },
  ];

  /** 咨询单流转明细 ticket_id → logs */
  const ticketLogs = {
    'ticket-1001': [
      { id: 'lg-1001-1', recordType: 1, time: '10:23', assigneeId: 'agent-001', assigneeName: '坐席一', remark: 'WEB 进线，排队 28 秒后接入' },
    ],
    'ticket-1002': [
      { id: 'lg-1002-1', recordType: 1, time: '10:15', assigneeId: 'agent-001', assigneeName: '坐席一', remark: 'APP 进线，直连' },
    ],
    'ticket-1003': [
      { id: 'lg-1003-1', recordType: 1, time: '11:02', assigneeId: 'agent-001', assigneeName: '坐席一', remark: 'WEB 进线，排队 12 秒' },
    ],
    'ticket-0901': [
      { id: 'lg-0901-1', recordType: 1, time: '06-18 14:20', assigneeId: 'agent-002', assigneeName: '坐席二', remark: '首次接入' },
      { id: 'lg-0901-2', recordType: 4, time: '06-18 14:45', assigneeId: 'agent-002', assigneeName: '坐席二', satisfaction: 5, remark: '解答试用问题后关单' },
    ],
    'ticket-0850': [
      { id: 'lg-0850-1', recordType: 1, time: '05-22 16:08', assigneeId: 'agent-002', assigneeName: '坐席二', remark: '排队 62 秒' },
      { id: 'lg-0850-2', recordType: 4, time: '05-22 16:30', assigneeId: 'agent-002', assigneeName: '坐席二', satisfaction: 4, remark: '技术问题已解决' },
    ],
    'ticket-0998': [
      { id: 'lg-0998-1', recordType: 1, time: '06-20 09:10', assigneeId: 'agent-003', assigneeName: '坐席三', remark: '售后进线' },
      { id: 'lg-0998-2', recordType: 3, time: '06-20 09:22', assigneeId: 'agent-003', assigneeName: '坐席三', fromAssigneeName: '坐席三', toAssigneeName: '坐席一', remark: '需售后专员跟进退款细节' },
      { id: 'lg-0998-3', recordType: 2, time: '06-20 09:22', assigneeId: 'agent-001', assigneeName: '坐席一', fromAssigneeName: '坐席三', toAssigneeName: '坐席一', remark: '转入' },
      { id: 'lg-0998-4', recordType: 4, time: '06-20 09:35', assigneeId: 'agent-001', assigneeName: '坐席一', satisfaction: 4, remark: '已告知预计到账时间' },
    ],
    'ticket-0970': [
      { id: 'lg-0970-1', recordType: 1, time: '06-05 11:00', assigneeId: 'agent-003', assigneeName: '坐席三', remark: 'H5 进线' },
      { id: 'lg-0970-2', recordType: 4, time: '06-05 11:12', assigneeId: 'agent-003', assigneeName: '坐席三', satisfaction: 5, remark: '发票流程已说明' },
    ],
  };

  const customerNames = {
    'visitor-001': 'John (US)',
    'user-18888888888': '用户188****8888',
    'visitor-jp-001': '田中さん',
  };

  /**
   * 咨询单聊天记录（已关闭或历史段）；进行中会话优先读 activeSessions.messages
   * segmentIndex：与流转中 recordType 1/2 的接待段一一对应（0=首接，1=转入后…）
   */
  const ticketMessages = {
    'ticket-0901': [
      { dir: 'in', text: '试用版有哪些功能限制？', time: '14:20', segmentIndex: 0 },
      { dir: 'out', text: '试用版支持 5 坐席、基础 IM，不含 API 与工单模块。', time: '14:22', segmentIndex: 0, assigneeName: '坐席二' },
      { dir: 'in', text: '好的，我考虑一下，谢谢。', time: '14:44', segmentIndex: 0 },
      { dir: 'out', text: '不客气，有需要随时联系我们。', time: '14:45', segmentIndex: 0, assigneeName: '坐席二' },
    ],
    'ticket-0850': [
      { dir: 'in', text: 'WebSocket 一直连接失败，错误 1006', time: '16:08', segmentIndex: 0 },
      { dir: 'out', text: '请确认防火墙是否放行 6003 端口，以及 appKey 与签名是否正确。', time: '16:12', segmentIndex: 0, assigneeName: '坐席二' },
      { dir: 'in', text: '端口已开放，还是不行', time: '16:18', segmentIndex: 0 },
      { dir: 'out', text: '请把完整连接 URL 和报错日志发我，我帮您排查。', time: '16:20', segmentIndex: 0, assigneeName: '坐席二' },
      { dir: 'in', text: '好了，是证书问题，已解决', time: '16:29', segmentIndex: 0 },
      { dir: 'out', text: '太好了，有问题随时联系。', time: '16:30', segmentIndex: 0, assigneeName: '坐席二' },
    ],
    'ticket-0998': [
      { dir: 'in', text: '我要申请退款，订单 ORD-8821', time: '09:10', segmentIndex: 0 },
      { dir: 'out', text: '您好，我是坐席三，已收到您的退款申请，正在核实订单状态。', time: '09:11', segmentIndex: 0, assigneeName: '坐席三' },
      { dir: 'in', text: '大概多久能到账？', time: '09:15', segmentIndex: 0 },
      { dir: 'out', text: '退款细节需要售后专员确认，我为您转接坐席一，请稍候。', time: '09:21', segmentIndex: 0, assigneeName: '坐席三' },
      { dir: 'in', text: '好的', time: '09:22', segmentIndex: 1 },
      { dir: 'out', text: '您好，我是坐席一，已看到您的退款单，财务审核已通过。', time: '09:23', segmentIndex: 1, assigneeName: '坐席一' },
      { dir: 'in', text: '那什么时候到账？', time: '09:25', segmentIndex: 1 },
      { dir: 'out', text: '预计 3-7 个工作日原路退回，请注意查收。', time: '09:28', segmentIndex: 1, assigneeName: '坐席一' },
      { dir: 'in', text: '明白了，谢谢', time: '09:34', segmentIndex: 1 },
      { dir: 'out', text: '不客气，祝您生活愉快！', time: '09:35', segmentIndex: 1, assigneeName: '坐席一' },
    ],
    'ticket-0970': [
      { dir: 'in', text: '怎么开具增值税发票？', time: '11:00', segmentIndex: 0 },
      { dir: 'out', text: '请在 APP → 我的订单 → 申请发票，填写抬头与税号即可。', time: '11:03', segmentIndex: 0, assigneeName: '坐席三' },
      { dir: 'in', text: '电子发票可以吗？', time: '11:08', segmentIndex: 0 },
      { dir: 'out', text: '可以，默认开具电子普票，1-3 个工作日发送至邮箱。', time: '11:10', segmentIndex: 0, assigneeName: '坐席三' },
    ],
  };

  function getCustomerDisplayName(userId) {
    const sess = activeSessions.find((s) => s.customerId === userId);
    if (sess) return sess.customerName;
    return customerNames[userId] || userId;
  }

  function getTicketMessages(ticketId) {
    const sess = activeSessions.find((s) => s.id === ticketId);
    if (sess && sess.messages && sess.messages.length) {
      return sess.messages.map((m, i) => ({
        ...m,
        segmentIndex: m.segmentIndex != null ? m.segmentIndex : 0,
        id: m.id || 'msg-live-' + i,
      }));
    }
    return (ticketMessages[ticketId] || []).map((m, i) => ({ ...m, id: m.id || 'msg-' + ticketId + '-' + i }));
  }

  function buildTimelineWithMessages(ticketId) {
    const logs = ticketLogs[ticketId] || [];
    const allMsgs = getTicketMessages(ticketId);
    let conversationSegment = -1;
    return logs.map((log, logIndex) => {
      let messages = [];
      if (log.recordType === 1 || log.recordType === 2) {
        conversationSegment += 1;
        messages = allMsgs.filter((m) => (m.segmentIndex ?? 0) === conversationSegment);
      }
      return {
        ...log,
        logIndex,
        recordTypeLabel: recordTypeLabel(log.recordType),
        satisfactionLabel: satisfactionLabel(log.satisfaction),
        messages,
        messageCount: messages.length,
      };
    });
  }

  function buildTicketListItem(t) {
    return {
      ...t,
      customerName: getCustomerDisplayName(t.userId),
      statusLabel: ticketStatusLabel(t.status),
      skillLabel: normalizeSkill(t.skill),
      satisfactionLabel: satisfactionLabel(t.satisfaction),
      flowCount: (ticketLogs[t.id] || []).length,
    };
  }

  function buildConsultationTicketDetail(ticketId) {
    const ticket = consultationTickets.find((t) => t.id === ticketId);
    if (!ticket) return null;
    const timeline = buildTimelineWithMessages(ticketId);
    const allMessages = getTicketMessages(ticketId);
    const relatedWorkOrders = workOrders.filter((w) => w.consultationTicketId === ticketId);
    return {
      ticket: {
        ...ticket,
        customerName: getCustomerDisplayName(ticket.userId),
        statusLabel: ticketStatusLabel(ticket.status),
        skillLabel: normalizeSkill(ticket.skill),
        satisfactionLabel: satisfactionLabel(ticket.satisfaction),
      },
      timeline,
      messageCount: allMessages.length,
      workOrders: relatedWorkOrders,
      sessionId: activeSessions.find((s) => s.id === ticketId)?.sessionId || null,
    };
  }

  function listConsultationTickets(filters) {
    let list = consultationTickets.slice();
    if (filters.status) {
      const st = +filters.status;
      list = list.filter((t) => t.status === st);
    }
    if (filters.assigneeId) {
      list = list.filter((t) => t.assigneeId === filters.assigneeId);
    }
    if (filters.skill) {
      list = list.filter((t) => t.skill === filters.skill);
    }
    if (filters.keyword) {
      const kw = filters.keyword.toLowerCase();
      list = list.filter((t) => {
        const name = getCustomerDisplayName(t.userId).toLowerCase();
        return (
          t.id.toLowerCase().includes(kw) ||
          name.includes(kw) ||
          t.userId.toLowerCase().includes(kw) ||
          (t.preview || '').toLowerCase().includes(kw) ||
          (t.assigneeName || '').toLowerCase().includes(kw)
        );
      });
    }
    list.sort((a, b) => (a.startTime < b.startTime ? 1 : -1));
    return { items: list.map(buildTicketListItem), total: list.length };
  }

  function ticketStatusLabel(status) {
    return TICKET_STATUS[status] || '未知';
  }

  function recordTypeLabel(type) {
    return RECORD_TYPE[type] || '记录';
  }

  function satisfactionLabel(score) {
    if (score == null) return null;
    const map = { 1: '非常不满意', 2: '不满意', 3: '一般', 4: '满意', 5: '非常满意' };
    return map[score] || score + ' 分';
  }

  function buildCustomerContext(ticketId, customerId) {
    const current = consultationTickets.find((t) => t.id === ticketId);
    const timeline = (ticketLogs[ticketId] || []).map((log) => ({
      ...log,
      recordTypeLabel: recordTypeLabel(log.recordType),
      satisfactionLabel: satisfactionLabel(log.satisfaction),
    }));
    const history = consultationTickets
      .filter((t) => t.userId === customerId && t.id !== ticketId)
      .sort((a, b) => (a.startTime < b.startTime ? 1 : -1))
      .map((t) => ({
        ...t,
        statusLabel: ticketStatusLabel(t.status),
        skillLabel: normalizeSkill(t.skill),
        satisfactionLabel: satisfactionLabel(t.satisfaction),
        timeline: (ticketLogs[t.id] || []).map((log) => ({
          ...log,
          recordTypeLabel: recordTypeLabel(log.recordType),
        })),
      }));
    return {
      current: current
        ? {
            ...current,
            statusLabel: ticketStatusLabel(current.status),
            skillLabel: normalizeSkill(current.skill),
          }
        : null,
      timeline,
      history,
      historyCount: history.length,
    };
  }

  function delay(result) {
    return new Promise((resolve) => setTimeout(() => resolve(result), MOCK_DELAY_MS));
  }

  function pickAgent(requiredSkill) {
    const online = agents.filter((a) => agentStatus.get(a.agentId) === 'online');
    const matched = requiredSkill
      ? online.filter((a) => a.skills.includes(requiredSkill))
      : online;
    const pool = matched.length ? matched : online;
    if (!pool.length) return null;
    return pool[routeCallCount % pool.length];
  }

  function normalizeSkill(code) {
    const map = { GENERAL: '通用', REFUND: '售后', TECH_SUPPORT: '技术支持' };
    return map[code] || code || '通用';
  }

  function formatTicketTime(iso) {
    if (!iso) return '—';
    if (typeof iso === 'string' && iso.includes('T')) {
      return iso.replace('T', ' ').slice(0, 16);
    }
    return String(iso);
  }

  /** 后端 ConsultationSummaryDto → 工作台 session 结构 */
  function mapConsultationToSession(dto, bucket) {
    const id = dto.ticketId || dto.id;
    return {
      id,
      ticketId: id,
      customerId: dto.customerId,
      customerName: dto.customerName || dto.customerId,
      preview: dto.preview || '',
      skill: dto.skillCode || dto.skill,
      channel: dto.channel || 'WEB',
      startTime: formatTicketTime(dto.startTime),
      sessionId: dto.sessionId,
      serviceIdentity: dto.serviceIdentity,
      serviceState: dto.serviceState,
      hostingMode: dto.hostingMode,
      pendingResume: dto.pendingResume,
      unreadCount: dto.unreadCount != null ? dto.unreadCount : 0,
      readOffset: dto.readOffset != null ? dto.readOffset : 0,
      bucket,
      messages: dto.messages || [],
    };
  }

  function mapTicketMessageItemToUi(item, session, role) {
    const text = item.content || '';
    let dir = 'in';
    if (role === 'agent') {
      dir = item.from === session.serviceIdentity ? 'out' : 'in';
    } else {
      dir = item.from === session.customerId ? 'out' : 'in';
    }
    return {
      dir,
      text,
      time: item.createTime ? new Date(item.createTime).toLocaleTimeString() : '',
      lang: detectLang(text),
      packetId: item.packetId,
    };
  }

  async function hydrateSessionMessages(session, readerId, deviceType, role) {
    if (MOCK_ENABLED || !session || !session.ticketId) return session;
    try {
      const page = await getTicketMessages(session.ticketId, readerId, null, 50);
      session.messages = (page.messages || []).map((m) =>
        mapTicketMessageItemToUi(m, session, role || 'agent')
      );
      if (page.messages && page.messages.length) {
        session.preview = session.messages[session.messages.length - 1].text.slice(0, 80);
      }
    } catch (_) {
      /* 热缓存未就绪时保留空列表 */
    }
    return session;
  }

  async function normalizeAgentSessions(raw) {
    if (!raw || raw.stats) return raw;
    const active = (raw.active || []).map((d) => mapConsultationToSession(d, 'active'));
    const hosted = (raw.hosted || []).map((d) => mapConsultationToSession(d, 'hosted'));
    const pendingResume = (raw.pendingResume || []).map((d) => mapConsultationToSession(d, 'pendingResume'));
    return {
      active,
      hosted,
      pendingResume,
      queue: raw.queue || [],
      stats: {
        activeCount: active.length,
        hostedCount: hosted.length,
        pendingCount: pendingResume.length,
        queueCount: (raw.queue || []).length,
        todayServed: raw.todayServed || 0,
      },
    };
  }

  function serviceStateLabel(state) {
    const map = { 1: '进行中', 2: '托管中', 3: '待恢复' };
    return map[state] || '—';
  }

  async function fetchJson(path, options = {}) {
    if (!MOCK_ENABLED) {
      const res = await fetch(API_BASE + path, {
        headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
        ...options,
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      return res.json();
    }
    return mockHandler(path, options);
  }

  async function mockHandler(path, options) {
    const method = (options.method || 'GET').toUpperCase();
    const url = new URL(path.startsWith('http') ? path : 'http://mock' + path);
    const params = url.searchParams;
    let body = {};
    if (options.body) {
      try {
        body = JSON.parse(options.body);
      } catch (_) {
        body = {};
      }
    }

    // POST /route
    if (method === 'POST' && url.pathname === '/route') {
      routeCallCount += 1;
      const customerId = body.customerId;
      const skill = body.requiredSkill || 'GENERAL';
      const existing = customerState.get(customerId);
      if (existing?.agentId) {
        return delay({ success: true, agentId: existing.agentId, message: null, queued: false, queuePosition: null });
      }
      if (existing?.inQueue) {
        const pos = existing.queuePosition || 1;
        return delay({
          success: false,
          agentId: null,
          message: '当前无可用坐席，已排队',
          queued: true,
          queuePosition: pos,
        });
      }

      const agent = pickAgent(skill);
      if (agent && routeCallCount % 3 !== 0) {
        customerState.set(customerId, { agentId: agent.agentId, skill, message: body.message });
        return delay({ success: true, agentId: agent.agentId, message: null, queued: false, queuePosition: null });
      }

      const pos = queueList.length + 1;
      customerState.set(customerId, {
        inQueue: true,
        queuePosition: pos,
        enqueueAt: Date.now(),
        skill,
        message: body.message,
      });
      queueList.push({
        customerId,
        customerName: body.customerName || '新访客',
        preview: body.message || '进线咨询',
        skill,
        waitSeconds: 0,
      });
      return delay({
        success: false,
        agentId: null,
        message: '当前无可用坐席，已排队',
        queued: true,
        queuePosition: pos,
      });
    }

    // GET /queue/status
    if (method === 'GET' && url.pathname === '/queue/status') {
      const customerId = params.get('customerId');
      const st = customerState.get(customerId);
      if (!st?.inQueue) {
        if (st?.agentId) {
          return delay({
            inQueue: false,
            queuePosition: null,
            waitedSeconds: 0,
            timedOut: false,
            message: '已分配坐席 ' + st.agentId,
          });
        }
        return delay({
          inQueue: false,
          queuePosition: null,
          waitedSeconds: 0,
          timedOut: false,
          message: '当前不在队列中',
        });
      }

      const waitedSeconds = Math.floor((Date.now() - (st.enqueueAt || Date.now())) / 1000);
      let pos = st.queuePosition || 1;
      if (waitedSeconds > 6 && pos > 1) {
        pos -= 1;
        st.queuePosition = pos;
        customerState.set(customerId, st);
      }
      if (waitedSeconds > 12 && pos <= 1) {
        const agent = pickAgent(st.skill);
        if (agent) {
          customerState.set(customerId, { agentId: agent.agentId, skill: st.skill, message: st.message });
          queueList = queueList.filter((q) => q.customerId !== customerId);
          return delay({
            inQueue: false,
            queuePosition: null,
            waitedSeconds,
            timedOut: false,
            message: '已分配坐席 ' + agent.agentId,
          });
        }
      }
      return delay({
        inQueue: true,
        queuePosition: pos,
        waitedSeconds,
        timedOut: waitedSeconds > 300,
        message: pos > 1 ? '当前排队第 ' + pos + ' 位' : '即将为您接入',
      });
    }

    // POST /queue/leave
    if (method === 'POST' && url.pathname === '/queue/leave') {
      const customerId = params.get('customerId');
      customerState.delete(customerId);
      queueList = queueList.filter((q) => q.customerId !== customerId);
      return delay(null);
    }

    // POST /session/release
    if (method === 'POST' && url.pathname === '/session/release') {
      const customerId = params.get('customerId');
      activeSessions = activeSessions.filter((s) => s.customerId !== customerId);
      customerState.delete(customerId);
      todayServedCount += 1;
      return delay(null);
    }

    // POST /agent/:id/:action
    const agentMatch = url.pathname.match(/^\/agent\/([^/]+)\/(online|offline|away|dnd)$/);
    if (method === 'POST' && agentMatch) {
      const aid = agentMatch[1];
      const action = agentMatch[2];
      agentStatus.set(aid, action);
      if (action === 'offline') writeSignedInSession(null);
      else {
        const prev = readSignedInSession();
        writeSignedInSession({
          signedIn: true,
          agentId: aid,
          signedInAt: prev && prev.agentId === aid ? prev.signedInAt : Date.now(),
          status: action,
        });
      }
      return delay(null);
    }

    // GET /agent/:id/dashboard
    const dashMatch = url.pathname.match(/^\/agent\/([^/]+)\/dashboard$/);
    if (method === 'GET' && dashMatch) {
      return delay(buildAgentDashboard(dashMatch[1]));
    }

    // GET /agent/im/unread (mock)
    if (method === 'GET' && url.pathname === '/agent/im/unread') {
      const agentId = params.get('agentId');
      const list = activeSessions.map((s) => ({
        ticketId: s.ticketId || s.id,
        readerId: agentId,
        deviceType: 0,
        unreadCount: (s.messages || []).filter((m) => m.dir === 'in').length,
        unreadCountCapped: false,
        readOffset: 0,
        lastPacketId: null,
      }));
      return delay(list);
    }

    // GET /ticket/:id/im/state (mock)
    const imStateMatch = url.pathname.match(/^\/ticket\/([^/]+)\/im\/state$/);
    if (method === 'GET' && imStateMatch) {
      const tid = imStateMatch[1];
      const readerId = params.get('readerId');
      const sess = activeSessions.find((s) => String(s.ticketId || s.id) === tid);
      const unread = sess ? (sess.messages || []).filter((m) => m.dir === 'in').length : 0;
      return delay({
        ticketId: tid,
        readerId,
        deviceType: Number(params.get('deviceType') || 0),
        unreadCount: unread,
        unreadCountCapped: false,
        readOffset: 0,
        lastPacketId: null,
      });
    }

    // GET /ticket/:id/messages (mock)
    const msgMatch = url.pathname.match(/^\/ticket\/([^/]+)\/messages$/);
    if (method === 'GET' && msgMatch) {
      const tid = msgMatch[1];
      const sess = activeSessions.find((s) => String(s.ticketId || s.id) === tid);
      const items = (sess?.messages || []).map((m, i) => ({
        packetId: i + 1,
        messageId: 'mock-' + tid + '-' + i,
        from: m.dir === 'out' ? sess.serviceIdentity || sess.customerId : sess.customerId,
        fromType: m.dir === 'out' ? 5 : 6,
        to: m.dir === 'out' ? sess.customerId : sess.serviceIdentity,
        contentType: -128,
        content: m.text,
        createTime: Date.now() - (sess.messages.length - i) * 60000,
        correlationId: tid,
      }));
      return delay({
        ticketId: tid,
        messages: items,
        nextBeforePacketId: items.length ? items[0].packetId : null,
        hasMore: false,
      });
    }

    // GET /agent/sessions (mock 扩展)
    if (method === 'GET' && url.pathname === '/agent/sessions') {
      return delay({
        active: activeSessions,
        hosted: [],
        pendingResume: [],
        queue: queueList,
        stats: {
          activeCount: activeSessions.length,
          hostedCount: 0,
          pendingCount: 0,
          queueCount: queueList.length,
          todayServed: todayServedCount,
        },
      });
    }

    // POST /consultation/:id/suspend | resume (mock)
    const suspendMatch = url.pathname.match(/^\/consultation\/([^/]+)\/suspend$/);
    if (method === 'POST' && suspendMatch) {
      const sid = suspendMatch[1];
      const s = activeSessions.find((x) => x.id === sid || x.ticketId === sid);
      if (s) {
        activeSessions = activeSessions.filter((x) => x !== s);
        s.bucket = 'hosted';
        s.serviceState = 2;
        if (!hostedSessions) hostedSessions = [];
        hostedSessions.unshift(s);
      }
      return delay({ ok: true });
    }
    const resumeMatch = url.pathname.match(/^\/consultation\/([^/]+)\/resume$/);
    if (method === 'POST' && resumeMatch) {
      const sid = resumeMatch[1];
      const s = (hostedSessions || []).find((x) => x.id === sid || x.ticketId === sid);
      if (s) {
        hostedSessions = hostedSessions.filter((x) => x !== s);
        s.bucket = 'active';
        s.serviceState = 1;
        activeSessions.unshift(s);
      }
      return delay({ ok: true });
    }

    // GET /consultation/agent/:id/capacity (mock)
    const capMatch = url.pathname.match(/^\/consultation\/agent\/([^/]+)\/capacity$/);
    if (method === 'GET' && capMatch) {
      const aid = capMatch[1];
      return delay({
        agentId: aid,
        activeSessions: activeSessions.filter((s) => s.agentId === aid || !s.agentId).length,
        hostedSessions: (hostedSessions || []).length,
        maxActive: 5,
        maxHosted: 2,
        pendingResumeCount: 0,
      });
    }

    // GET /admin/idle-policies (mock)
    if (method === 'GET' && url.pathname === '/admin/idle-policies') {
      return delay([
        {
          appKey: DEFAULT_APP_KEY,
          scopeType: 0,
          scopeId: '*',
          enabled: true,
          visitorIdleWarnSec: 120,
          visitorIdleHostSec: 180,
          visitorIdleCloseSec: 300,
          visitorIdleCloseEnabled: true,
          agentReplyWarnSec: 60,
          agentReplyHostSec: 120,
          maxHostedRatio: 0.5,
          maxHostedAbsolute: 3,
          resumeOnVisitorMessage: true,
        },
      ]);
    }
    if (method === 'PUT' && url.pathname === '/admin/idle-policies') {
      return delay({ ok: true });
    }

    // POST /agent/sessions/:id/message (mock 扩展)
    const msgMatch = url.pathname.match(/^\/agent\/sessions\/([^/]+)\/message$/);
    if (method === 'POST' && msgMatch) {
      const session = activeSessions.find((s) => s.id === msgMatch[1]);
      if (session && body.text) {
        const msg = {
          dir: 'out',
          text: body.text,
          time: body.time || '刚刚',
          lang: body.lang || detectLang(body.text),
        };
        if (body.originalText) {
          msg.originalText = body.originalText;
          msg.originalLang = body.originalLang || detectLang(body.originalText);
        }
        session.messages.push(msg);
        session.preview = body.text;
      }
      return delay({ ok: true });
    }

    // POST /translate
    if (method === 'POST' && url.pathname === '/translate') {
      const result = mockTranslateText(body.text, body.sourceLang || detectLang(body.text), body.targetLang || 'zh');
      return delay({ success: true, ...result });
    }

    // POST /visitor/message (mock IM 收发)
    if (method === 'POST' && url.pathname === '/visitor/message') {
      return delay({ ok: true, autoReply: '收到，我帮您查一下，请稍候。' });
    }

    // GET /consultation/:id/work-orders
    const woListMatch = url.pathname.match(/^\/consultation\/([^/]+)\/work-orders$/);
    if (method === 'GET' && woListMatch) {
      const ticketId = woListMatch[1];
      const customerId = params.get('customerId');
      let list = workOrders.filter((w) => w.consultationTicketId === ticketId);
      if (customerId) {
        const byCustomer = workOrders.filter((w) => w.customerId === customerId && w.consultationTicketId !== ticketId);
        list = list.concat(byCustomer);
      }
      return delay({ items: list, total: list.length });
    }

    // POST /work-orders
    if (method === 'POST' && url.pathname === '/work-orders') {
      workOrderSeq += 1;
      const wo = {
        id: 'WO-2024-' + workOrderSeq,
        consultationTicketId: body.consultationTicketId,
        customerId: body.customerId,
        title: body.title || '未命名工单',
        type: body.type || 'GENERAL',
        priority: body.priority || 'medium',
        status: 'processing',
        description: body.description || '',
        createdAt: '刚刚',
      };
      workOrders.unshift(wo);
      return delay({ success: true, workOrder: wo });
    }

    // GET /materials/search
    if (method === 'GET' && url.pathname === '/materials/search') {
      const keyword = (params.get('keyword') || '').trim().toLowerCase();
      const customerId = params.get('customerId') || '';
      const match = (text) => !keyword || String(text).toLowerCase().includes(keyword);
      const knowledge = kbArticles.filter((a) => match(a.title) || match(a.snippet));
      const orders = (customerOrders[customerId] || []).filter((o) => match(o.title) || match(o.status) || match(o.id));
      const docs = productDocs.filter((d) => match(d.title) || match(d.snippet));
      if (!keyword) {
        return delay({ knowledge: kbArticles.slice(0, 3), orders: customerOrders[customerId] || [], docs: productDocs.slice(0, 2) });
      }
      return delay({ knowledge, orders, docs });
    }

    // GET /consultation/tickets (咨询单列表)
    if (method === 'GET' && url.pathname === '/consultation/tickets') {
      return delay(
        listConsultationTickets({
          status: params.get('status') || '',
          assigneeId: params.get('assigneeId') || '',
          skill: params.get('skill') || '',
          keyword: params.get('keyword') || '',
        })
      );
    }

    // GET /consultation/:ticketId/detail (咨询单详情含流转聊天记录)
    const detailMatch = url.pathname.match(/^\/consultation\/([^/]+)\/detail$/);
    if (method === 'GET' && detailMatch) {
      const detail = buildConsultationTicketDetail(detailMatch[1]);
      if (!detail) return delay({ success: false, message: '咨询单不存在' });
      return delay({ success: true, ...detail });
    }

    // GET /consultation/:ticketId/customer-context
    const ctxMatch = url.pathname.match(/^\/consultation\/([^/]+)\/customer-context$/);
    if (method === 'GET' && ctxMatch) {
      const ticketId = ctxMatch[1];
      const customerId = params.get('customerId') || '';
      return delay(buildCustomerContext(ticketId, customerId));
    }

    // POST /agent/queue/pickup
    if (method === 'POST' && url.pathname === '/agent/queue/pickup') {
      const sessionScopeId = params.get('sessionScopeId') || body.sessionScopeId;
      const agentId = params.get('agentId') || body.agentId || 'agent-001';
      const idx = queueList.findIndex(
        (q) => q.sessionScopeId === sessionScopeId || q.customerId === sessionScopeId
      );
      if (idx < 0) {
        return delay({ success: false, message: '客户不在队列中' });
      }
      const q = queueList[idx];
      queueList.splice(idx, 1);
      customerState.set(customerId, { agentId, skill: q.skill, message: q.preview });
      const now = new Date();
      const startTime =
        String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0');
      const session = {
        id: 'ticket-' + Date.now(),
        customerId: q.customerId,
        customerName: q.customerName,
        customerLocale: q.customerLocale || detectLang(q.preview) || 'zh',
        preview: q.preview,
        skill: q.skill,
        channel: 'WEB',
        startTime,
        sessionId: 'sessionId(' + q.customerId + ',' + agentId + ')',
        messages: [{ dir: 'in', text: q.preview, time: startTime }],
      };
      activeSessions.unshift(session);
      return delay({ success: true, session });
    }

    // —— 管理端配置 Mock ——
    if (method === 'GET' && url.pathname === '/admin/config/general') {
      return delay({ ...generalConfig });
    }
    if (method === 'PUT' && url.pathname === '/admin/config/general') {
      generalConfig = { ...generalConfig, ...body };
      saveAdminConfig();
      return delay({ success: true, config: generalConfig });
    }

    if (method === 'GET' && url.pathname === '/admin/routing-rules') {
      return delay({ ...routingRules });
    }
    if (method === 'PUT' && url.pathname === '/admin/routing-rules') {
      routingRules = { ...routingRules, ...body };
      saveAdminConfig();
      return delay({ success: true, rules: routingRules });
    }

    if (method === 'GET' && url.pathname === '/admin/skills') {
      return delay({ items: skillGroups, total: skillGroups.length });
    }
    const skillMatch = url.pathname.match(/^\/admin\/skills\/([^/]+)$/);
    if (skillMatch) {
      const sid = skillMatch[1];
      const idx = skillGroups.findIndex((s) => s.id === sid);
      if (method === 'PUT' && idx >= 0) {
        skillGroups[idx] = { ...skillGroups[idx], ...body, id: sid };
        saveAdminConfig();
        return delay({ success: true, skill: skillGroups[idx] });
      }
      if (method === 'DELETE' && idx >= 0) {
        skillGroups.splice(idx, 1);
        saveAdminConfig();
        return delay({ success: true });
      }
    }
    if (method === 'POST' && url.pathname === '/admin/skills') {
      const sk = {
        id: 'sk-' + Date.now(),
        code: body.code || 'CUSTOM',
        name: body.name || '新技能组',
        description: body.description || '',
        enabled: body.enabled !== false,
        agentIds: body.agentIds || [],
      };
      skillGroups.push(sk);
      saveAdminConfig();
      return delay({ success: true, skill: sk });
    }

    if (method === 'GET' && url.pathname === '/admin/schedules') {
      return delay({ items: schedules, total: schedules.length });
    }
    const schMatch = url.pathname.match(/^\/admin\/schedules\/([^/]+)$/);
    if (schMatch) {
      const sid = schMatch[1];
      const idx = schedules.findIndex((s) => s.id === sid);
      if (method === 'PUT' && idx >= 0) {
        schedules[idx] = { ...schedules[idx], ...body, id: sid };
        saveAdminConfig();
        return delay({ success: true, schedule: schedules[idx] });
      }
      if (method === 'DELETE' && idx >= 0) {
        schedules.splice(idx, 1);
        saveAdminConfig();
        return delay({ success: true });
      }
    }
    if (method === 'POST' && url.pathname === '/admin/schedules') {
      const sch = {
        id: 'sch-' + Date.now(),
        name: body.name || '新排班',
        timezone: body.timezone || 'Asia/Shanghai',
        days: body.days || [1, 2, 3, 4, 5],
        startTime: body.startTime || '09:00',
        endTime: body.endTime || '18:00',
        agentIds: body.agentIds || [],
        enabled: body.enabled !== false,
      };
      schedules.push(sch);
      saveAdminConfig();
      return delay({ success: true, schedule: sch });
    }

    if (method === 'GET' && url.pathname === '/admin/agents') {
      const list = agents.map((a) => ({
        ...a,
        skillsLabel: (a.skills || []).map(normalizeSkill),
        status: agentStatus.get(a.agentId) || 'offline',
      }));
      return delay({ items: list, total: list.length });
    }
    const adminAgentMatch = url.pathname.match(/^\/admin\/agents\/([^/]+)$/);
    if (method === 'PUT' && adminAgentMatch) {
      const aid = adminAgentMatch[1];
      const idx = agents.findIndex((a) => a.agentId === aid);
      if (idx < 0) return delay({ success: false, message: '坐席不存在' });
      agents[idx] = { ...agents[idx], ...body, agentId: aid };
      if (body.skills) {
        skillGroups.forEach((sg) => {
          const has = sg.agentIds.includes(aid);
          const should = body.skills.includes(sg.code);
          if (should && !has) sg.agentIds.push(aid);
          if (!should && has) sg.agentIds = sg.agentIds.filter((id) => id !== aid);
        });
      }
      saveAdminConfig();
      return delay({ success: true, agent: agents[idx] });
    }

    // —— 坐席个人设置 Mock ——
    const profileMatch = url.pathname.match(/^\/agent\/([^/]+)\/profile$/);
    if (profileMatch) {
      const aid = profileMatch[1];
      if (method === 'GET') return delay(getAgentProfile(aid));
      if (method === 'PUT') return delay({ success: true, profile: saveAgentProfile(aid, body) });
    }

    const qrMatch = url.pathname.match(/^\/agent\/([^/]+)\/quick-replies$/);
    if (qrMatch) {
      const aid = qrMatch[1];
      if (method === 'GET') {
        const list = readQuickReplies(aid).sort((a, b) => (a.sort || 0) - (b.sort || 0));
        return delay({ items: list, total: list.length });
      }
      if (method === 'POST') {
        const list = readQuickReplies(aid);
        const item = {
          id: 'qr-' + Date.now(),
          category: body.category || 'other',
          shortcut: body.shortcut || '',
          title: body.title || '新常用语',
          content: body.content || '',
          sort: body.sort != null ? body.sort : list.length + 1,
        };
        list.push(item);
        writeQuickReplies(aid, list);
        return delay({ success: true, item });
      }
    }
    const qrItemMatch = url.pathname.match(/^\/agent\/([^/]+)\/quick-replies\/([^/]+)$/);
    if (qrItemMatch) {
      const aid = qrItemMatch[1];
      const qid = qrItemMatch[2];
      let list = readQuickReplies(aid);
      const idx = list.findIndex((q) => q.id === qid);
      if (method === 'PUT' && idx >= 0) {
        list[idx] = { ...list[idx], ...body, id: qid };
        writeQuickReplies(aid, list);
        return delay({ success: true, item: list[idx] });
      }
      if (method === 'DELETE' && idx >= 0) {
        list = list.filter((q) => q.id !== qid);
        writeQuickReplies(aid, list);
        return delay({ success: true });
      }
    }

    throw new Error('Mock API not found: ' + method + ' ' + url.pathname);
  }

  const CsMockApi = {
    MOCK_ENABLED,
    API_BASE,
    DEFAULT_APP_KEY,
    DEFAULT_DEVICE_TYPE: 0,
    agents,
    LANG_LABELS,
    normalizeSkill,
    normalizeLang,
    detectLang,
    mapTicketMessageItemToUi,
    hydrateSessionMessages,

    translate(text, sourceLang, targetLang) {
      return fetchJson('/translate', {
        method: 'POST',
        body: JSON.stringify({ text, sourceLang, targetLang }),
      });
    },

    langLabel(code) {
      return LANG_LABELS[normalizeLang(code)] || code;
    },

    serviceStateLabel,

    route(body) {
      return this.routeGuest(body);
    },

    routeGuest(body) {
      const payload = {
        customerId: body.customerId,
        merchantId: body.merchantId || DEFAULT_MERCHANT_ID,
        entryCode: body.entryCode || DEFAULT_ENTRY_CODE,
        message: body.message,
        channel: body.channel || 'web',
        customerName: body.customerName,
        preferredLanguage: body.preferredLanguage,
        appKey: body.appKey || DEFAULT_APP_KEY,
      };
      if (body.requiredSkill) payload.requiredSkill = body.requiredSkill;
      return fetchJson('/route/guest', { method: 'POST', body: JSON.stringify(payload) });
    },

    routeMember(body) {
      const payload = {
        customerId: body.customerId,
        merchantId: body.merchantId || DEFAULT_MERCHANT_ID,
        entryCode: body.entryCode || DEFAULT_ENTRY_CODE,
        message: body.message,
        channel: body.channel || 'web',
        customerName: body.customerName,
        appKey: body.appKey || DEFAULT_APP_KEY,
      };
      if (body.requiredSkill) payload.requiredSkill = body.requiredSkill;
      return fetchJson('/route/member', { method: 'POST', body: JSON.stringify(payload) });
    },

    queueStatus(sessionScopeId, appKey = DEFAULT_APP_KEY) {
      return fetchJson(
        '/queue/status?sessionScopeId=' +
          encodeURIComponent(sessionScopeId) +
          '&appKey=' +
          encodeURIComponent(appKey)
      );
    },

    leaveQueue(sessionScopeId, appKey = DEFAULT_APP_KEY) {
      return fetchJson(
        '/queue/leave?sessionScopeId=' +
          encodeURIComponent(sessionScopeId) +
          '&appKey=' +
          encodeURIComponent(appKey),
        { method: 'POST' }
      );
    },

    releaseSession(customerId, appKey = DEFAULT_APP_KEY) {
      return this.closeConsultation(customerId, appKey);
    },

    closeConsultation(ticketId, appKey = DEFAULT_APP_KEY, closeType, remark) {
      let q = '/consultation/' + encodeURIComponent(ticketId) + '/close?appKey=' + encodeURIComponent(appKey);
      if (closeType != null) q += '&closeType=' + encodeURIComponent(closeType);
      if (remark) q += '&remark=' + encodeURIComponent(remark);
      return fetchJson(q, { method: 'POST' });
    },

    suspendConsultation(ticketId, reason, appKey = DEFAULT_APP_KEY) {
      let q =
        '/consultation/' +
        encodeURIComponent(ticketId) +
        '/suspend?appKey=' +
        encodeURIComponent(appKey);
      if (reason) q += '&reason=' + encodeURIComponent(reason);
      return fetchJson(q, { method: 'POST' });
    },

    resumeConsultation(ticketId, appKey = DEFAULT_APP_KEY) {
      return fetchJson(
        '/consultation/' +
          encodeURIComponent(ticketId) +
          '/resume?appKey=' +
          encodeURIComponent(appKey),
        { method: 'POST' }
      );
    },

    getAgentCapacity(agentId, appKey = DEFAULT_APP_KEY) {
      return fetchJson(
        '/consultation/agent/' +
          encodeURIComponent(agentId) +
          '/capacity?appKey=' +
          encodeURIComponent(appKey)
      );
    },

    listIdlePolicies(appKey = DEFAULT_APP_KEY) {
      return fetchJson('/admin/idle-policies?appKey=' + encodeURIComponent(appKey));
    },

    saveIdlePolicy(dto, appKey = DEFAULT_APP_KEY) {
      return fetchJson('/admin/idle-policies?appKey=' + encodeURIComponent(appKey), {
        method: 'PUT',
        body: JSON.stringify(dto),
      });
    },

    deleteIdlePolicy(scopeType, scopeId, appKey = DEFAULT_APP_KEY) {
      return fetchJson(
        '/admin/idle-policies?appKey=' +
          encodeURIComponent(appKey) +
          '&scopeType=' +
          encodeURIComponent(scopeType) +
          '&scopeId=' +
          encodeURIComponent(scopeId),
        { method: 'DELETE' }
      );
    },

    async getAgentSessions(agentId, deviceType = 0, includeUnread = false) {
      let q =
        '/agent/sessions?agentId=' +
        encodeURIComponent(agentId) +
        '&deviceType=' +
        encodeURIComponent(deviceType) +
        '&includeUnread=' +
        (includeUnread ? 'true' : 'false');
      const raw = await fetchJson(q);
      return normalizeAgentSessions(raw);
    },

    getTicketImState(ticketId, readerId, deviceType = 0, appKey = DEFAULT_APP_KEY) {
      return fetchJson(
        '/ticket/' +
          encodeURIComponent(ticketId) +
          '/im/state?readerId=' +
          encodeURIComponent(readerId) +
          '&deviceType=' +
          encodeURIComponent(deviceType) +
          '&appKey=' +
          encodeURIComponent(appKey)
      );
    },

    getTicketMessages(ticketId, readerId, beforePacketId, limit = 20, appKey = DEFAULT_APP_KEY) {
      let q =
        '/ticket/' +
        encodeURIComponent(ticketId) +
        '/messages?readerId=' +
        encodeURIComponent(readerId) +
        '&limit=' +
        encodeURIComponent(limit) +
        '&appKey=' +
        encodeURIComponent(appKey);
      if (beforePacketId != null && beforePacketId > 0) {
        q += '&beforePacketId=' + encodeURIComponent(beforePacketId);
      }
      return fetchJson(q);
    },

    getAgentImUnread(agentId, deviceType = 0, appKey = DEFAULT_APP_KEY) {
      return fetchJson(
        '/agent/im/unread?agentId=' +
          encodeURIComponent(agentId) +
          '&deviceType=' +
          encodeURIComponent(deviceType) +
          '&appKey=' +
          encodeURIComponent(appKey)
      );
    },

    sendAgentMessage(sessionId, payload) {
      const body = typeof payload === 'string' ? { text: payload } : payload;
      return fetchJson('/agent/sessions/' + encodeURIComponent(sessionId) + '/message', {
        method: 'POST',
        body: JSON.stringify(body),
      });
    },

    sendVisitorMessage(text) {
      return fetchJson('/visitor/message', { method: 'POST', body: JSON.stringify({ text }) });
    },

    setAgentStatus(agentId, status, appKey = DEFAULT_APP_KEY) {
      return fetchJson(
        '/agent/' + encodeURIComponent(agentId) + '/' + status + '?appKey=' + appKey,
        { method: 'POST' }
      );
    },

    pickupFromQueue(sessionScopeId, agentId) {
      return fetchJson(
        '/agent/queue/pickup?agentId=' +
          encodeURIComponent(agentId) +
          '&sessionScopeId=' +
          encodeURIComponent(sessionScopeId),
        { method: 'POST' }
      );
    },

    listWorkOrders(consultationTicketId, customerId) {
      let q = '/consultation/' + encodeURIComponent(consultationTicketId) + '/work-orders';
      if (customerId) q += '?customerId=' + encodeURIComponent(customerId);
      return fetchJson(q);
    },

    createWorkOrder(body) {
      return fetchJson('/work-orders', { method: 'POST', body: JSON.stringify(body) });
    },

    searchMaterials(keyword, customerId, consultationTicketId) {
      const q = new URLSearchParams();
      if (keyword) q.set('keyword', keyword);
      if (customerId) q.set('customerId', customerId);
      if (consultationTicketId) q.set('consultationTicketId', consultationTicketId);
      return fetchJson('/materials/search?' + q.toString());
    },

    workOrderTypeLabel(type) {
      const map = { GENERAL: '通用', REFUND: '售后退款', TECH_SUPPORT: '技术支持', BILLING: '发票账务' };
      return map[type] || type;
    },

    workOrderStatusLabel(status) {
      const map = { processing: '处理中', done: '已完成', pending: '待处理' };
      return map[status] || status;
    },

    ticketStatusLabel,
    recordTypeLabel,
    satisfactionLabel,

    getCustomerContext(ticketId, customerId) {
      const q = new URLSearchParams();
      if (customerId) q.set('customerId', customerId);
      const qs = q.toString();
      return fetchJson('/consultation/' + encodeURIComponent(ticketId) + '/customer-context' + (qs ? '?' + qs : ''));
    },

    listConsultationTickets(params) {
      const q = new URLSearchParams();
      if (params) {
        if (params.status) q.set('status', params.status);
        if (params.assigneeId) q.set('assigneeId', params.assigneeId);
        if (params.skill) q.set('skill', params.skill);
        if (params.keyword) q.set('keyword', params.keyword);
      }
      const qs = q.toString();
      return fetchJson('/consultation/tickets' + (qs ? '?' + qs : ''));
    },

    getConsultationTicketDetail(ticketId) {
      return fetchJson('/consultation/' + encodeURIComponent(ticketId) + '/detail');
    },

    getAgentInfo(agentId) {
      return agents.find((a) => a.agentId === agentId) || { agentId, agentName: agentId, jobNo: '—' };
    },

    getAgentDashboard(agentId) {
      return fetchJson('/agent/' + encodeURIComponent(agentId) + '/dashboard');
    },

    getSignedInSession() {
      return readSignedInSession();
    },

    isSignedIn(agentId) {
      const s = readSignedInSession();
      if (!s || !s.signedIn) return false;
      if (agentId && s.agentId !== agentId) return false;
      const st = agentStatus.get(s.agentId);
      return st && st !== 'offline';
    },

    async signIn(agentId) {
      await fetchJson('/agent/' + encodeURIComponent(agentId) + '/online?appKey=' + encodeURIComponent(DEFAULT_APP_KEY), { method: 'POST' });
      writeSignedInSession({ signedIn: true, agentId, signedInAt: Date.now(), status: 'online' });
      agentStatus.set(agentId, 'online');
      return { success: true, agentId };
    },

    async signOut(agentId) {
      await fetchJson('/agent/' + encodeURIComponent(agentId) + '/offline?appKey=' + encodeURIComponent(DEFAULT_APP_KEY), { method: 'POST' });
      writeSignedInSession(null);
      agentStatus.set(agentId, 'offline');
      return { success: true };
    },

    formatDuration,

    /** 模拟 IM 登录参数（静态页展示用） */
    buildImLoginMock(scope, identity) {
      return {
        messageType: -2,
        scope,
        identity,
        appKey: DEFAULT_APP_KEY,
        note: scope === 6 ? 'cs_visitor' : scope === 5 ? 'cs_agent' : 'normal',
      };
    },

    /** 模拟客服消息 packet */
    buildCsMessageMock(from, to, content) {
      return {
        messageType: -17,
        message: { from, to, contentType: 1, content, createTime: Date.now() },
      };
    },

    QUICK_REPLY_CATEGORY_LABELS,

    getGeneralConfig() {
      return fetchJson('/admin/config/general');
    },

    saveGeneralConfig(config) {
      return fetchJson('/admin/config/general', { method: 'PUT', body: JSON.stringify(config) });
    },

    getRoutingRules() {
      return fetchJson('/admin/routing-rules');
    },

    saveRoutingRules(rules) {
      return fetchJson('/admin/routing-rules', { method: 'PUT', body: JSON.stringify(rules) });
    },

    listSkillGroups() {
      return fetchJson('/admin/skills');
    },

    saveSkillGroup(id, body) {
      return fetchJson('/admin/skills/' + encodeURIComponent(id), { method: 'PUT', body: JSON.stringify(body) });
    },

    createSkillGroup(body) {
      return fetchJson('/admin/skills', { method: 'POST', body: JSON.stringify(body) });
    },

    deleteSkillGroup(id) {
      return fetchJson('/admin/skills/' + encodeURIComponent(id), { method: 'DELETE' });
    },

    listSchedules() {
      return fetchJson('/admin/schedules');
    },

    saveSchedule(id, body) {
      return fetchJson('/admin/schedules/' + encodeURIComponent(id), { method: 'PUT', body: JSON.stringify(body) });
    },

    createSchedule(body) {
      return fetchJson('/admin/schedules', { method: 'POST', body: JSON.stringify(body) });
    },

    deleteSchedule(id) {
      return fetchJson('/admin/schedules/' + encodeURIComponent(id), { method: 'DELETE' });
    },

    listAdminAgents() {
      return fetchJson('/admin/agents');
    },

    saveAdminAgent(agentId, body) {
      return fetchJson('/admin/agents/' + encodeURIComponent(agentId), { method: 'PUT', body: JSON.stringify(body) });
    },

    getAgentProfile(agentId) {
      return fetchJson('/agent/' + encodeURIComponent(agentId) + '/profile');
    },

    saveAgentProfile(agentId, body) {
      return fetchJson('/agent/' + encodeURIComponent(agentId) + '/profile', {
        method: 'PUT',
        body: JSON.stringify(body),
      });
    },

    listQuickReplies(agentId) {
      return fetchJson('/agent/' + encodeURIComponent(agentId) + '/quick-replies');
    },

    createQuickReply(agentId, body) {
      return fetchJson('/agent/' + encodeURIComponent(agentId) + '/quick-replies', {
        method: 'POST',
        body: JSON.stringify(body),
      });
    },

    saveQuickReply(agentId, id, body) {
      return fetchJson('/agent/' + encodeURIComponent(agentId) + '/quick-replies/' + encodeURIComponent(id), {
        method: 'PUT',
        body: JSON.stringify(body),
      });
    },

    deleteQuickReply(agentId, id) {
      return fetchJson('/agent/' + encodeURIComponent(agentId) + '/quick-replies/' + encodeURIComponent(id), {
        method: 'DELETE',
      });
    },
  };

  global.CsMockApi = CsMockApi;
})(typeof window !== 'undefined' ? window : globalThis);
