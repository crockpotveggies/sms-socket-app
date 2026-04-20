import {EmitterSubscription, NativeEventEmitter, NativeModules} from 'react-native';

export type GatewayEventRecord = {
  id: string;
  type: string;
  timestamp: number;
  payload: Record<string, unknown>;
};

export type GatewayAttachment = {
  id: string;
  fileName: string;
  mimeType: string;
  sizeBytes: number;
  contentRedacted?: boolean;
  base64?: string;
  previewBase64?: string;
};

export type MessageDeliveryState =
  | 'received'
  | 'pending'
  | 'sent'
  | 'delivered'
  | 'failed'
  | 'rejected';

export type GatewayStatus = {
  enabled: boolean;
  running: boolean;
  host: string;
  port: number;
  connectionCount: number;
  smsRoleGranted: boolean;
  dialerRoleGranted: boolean;
  notificationPermissionGranted: boolean;
  batteryOptimizationsIgnored: boolean;
  gatewayPermissionsGranted: boolean;
  missingPermissions: string[];
  apiKeyConfigured: boolean;
  apiKeyPreview: string;
  addresses: string[];
  recentEvents: GatewayEventRecord[];
  inCallServiceHealthy: boolean;
  activeCalls: DialerCall[];
  dialerMissingPermissions: string[];
};

export type DialerCall = {
  callId: string;
  number: string;
  displayName: string;
  direction: 'incoming' | 'outgoing' | 'unknown';
  state:
    | 'new'
    | 'dialing'
    | 'ringing'
    | 'holding'
    | 'active'
    | 'disconnecting'
    | 'disconnected'
    | 'select_phone_account'
    | 'connecting'
    | 'unknown';
  isMuted: boolean;
  route: string;
  isConference: boolean;
  canAnswer: boolean;
  canReject: boolean;
  canDisconnect: boolean;
};

export type DialerStatus = Pick<
  GatewayStatus,
  'dialerRoleGranted' | 'inCallServiceHealthy' | 'activeCalls' | 'dialerMissingPermissions'
>;

export type PendingUiRequest = {
  screen?: 'calls' | 'dialer';
  showDialpad?: boolean;
  showWhenLocked?: boolean;
  timestamp?: number;
};

export type DialerRecentCall = {
  id: string;
  number: string;
  displayName: string;
  direction: string;
  timestamp: number;
  durationSeconds: number;
};

export type SmsConversation = {
  id: string;
  kind: 'sms' | 'mms';
  threadId: string;
  address: string;
  participants: string[];
  displayName: string;
  initials: string;
  snippet: string;
  timestamp: number;
  messageType: number;
  read: boolean;
  unreadCount: number;
  status?: number | null;
  deliveryState?: MessageDeliveryState | null;
  carrierAccepted?: boolean | null;
  failureReason?: string | null;
  subject?: string | null;
  hasMedia: boolean;
};

export type SmsMessage = {
  id: string;
  kind: 'sms' | 'mms';
  threadId: string;
  address: string;
  participants: string[];
  displayName: string;
  initials: string;
  body: string;
  timestamp: number;
  messageType: number;
  read: boolean;
  status: number | null;
  deliveryState?: MessageDeliveryState | null;
  carrierAccepted?: boolean | null;
  failureReason?: string | null;
  subject?: string | null;
  hasMedia: boolean;
  attachments: GatewayAttachment[];
};

export type SmsGatewayNativeEvent =
  | {name: 'SmsGatewayState'; payload: GatewayStatus}
  | {name: 'SmsGatewayEvent'; payload: GatewayEventRecord};

type StartGatewayArgs = {
  host: string;
  port: number;
  apiKey?: string;
};

type StartGatewayResult = {
  generatedApiKey?: string;
  status: GatewayStatus;
};

type GenerateApiKeyResult = {
  apiKey: string;
  status: GatewayStatus;
};

type SendSmsArgs = {
  destination: string;
  body: string;
  subscriptionId?: number;
};

type SendMmsArgs = {
  destination: string;
  body?: string;
  subject?: string;
  subscriptionId?: number;
  attachment: GatewayAttachment;
};

type PlaceCallArgs = {
  number: string;
  speakerphone?: boolean;
};

type DialerCallRequest = {
  callId: string;
};

type SetMutedRequest = {
  callId: string;
  muted: boolean;
};

type SendDtmfRequest = {
  callId: string;
  digits: string;
};

type ShowInCallScreenRequest = {
  showDialpad?: boolean;
};

type NativeSmsGatewayModule = {
  requestSmsRole(): Promise<boolean>;
  requestDialerRole(): Promise<boolean>;
  getGatewayStatus(): Promise<GatewayStatus>;
  getDialerStatus(): Promise<DialerStatus>;
  consumePendingUiRequest(): Promise<PendingUiRequest>;
  startGateway(config: StartGatewayArgs): Promise<StartGatewayResult>;
  stopGateway(): Promise<GatewayStatus>;
  generateApiKey(): Promise<GenerateApiKeyResult>;
  listSubscriptions(): Promise<Array<Record<string, unknown>>>;
  listRecentCalls(limit: number): Promise<DialerRecentCall[]>;
  listConversations(limit: number): Promise<SmsConversation[]>;
  getConversationMessages(request: {
    threadId?: string;
    address?: string;
    limit?: number;
  }): Promise<SmsMessage[]>;
  markConversationUnread(request: {
    threadId?: string;
    address?: string;
  }): Promise<boolean>;
  markConversationRead(request: {
    threadId?: string;
    address?: string;
  }): Promise<boolean>;
  deleteConversation(request: {
    threadId?: string;
    address?: string;
  }): Promise<boolean>;
  sendSmsMessage(request: SendSmsArgs): Promise<Record<string, unknown>>;
  sendMmsMessage(request: SendMmsArgs): Promise<Record<string, unknown>>;
  placeCall(request: PlaceCallArgs): Promise<Record<string, unknown>>;
  answerCall(request: DialerCallRequest): Promise<boolean>;
  rejectCall(request: DialerCallRequest): Promise<boolean>;
  endCall(request: DialerCallRequest): Promise<boolean>;
  setMuted(request: SetMutedRequest): Promise<boolean>;
  sendDtmf(request: SendDtmfRequest): Promise<Record<string, unknown>>;
  showInCallScreen(request: ShowInCallScreenRequest): Promise<boolean>;
  pickMmsAttachment(): Promise<GatewayAttachment>;
  openBatteryOptimizationSettings(): Promise<boolean>;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
};

const {SmsGatewayModule} = NativeModules as {
  SmsGatewayModule: NativeSmsGatewayModule;
};

const emitter = SmsGatewayModule ? new NativeEventEmitter(SmsGatewayModule) : null;

export const SmsGateway = {
  requestSmsRole: () => SmsGatewayModule.requestSmsRole(),
  requestDialerRole: () => SmsGatewayModule.requestDialerRole(),
  getGatewayStatus: () => SmsGatewayModule.getGatewayStatus(),
  getDialerStatus: () => SmsGatewayModule.getDialerStatus(),
  consumePendingUiRequest: () => SmsGatewayModule.consumePendingUiRequest(),
  startGateway: (config: StartGatewayArgs) => SmsGatewayModule.startGateway(config),
  stopGateway: () => SmsGatewayModule.stopGateway(),
  generateApiKey: () => SmsGatewayModule.generateApiKey(),
  listSubscriptions: () => SmsGatewayModule.listSubscriptions(),
  listRecentCalls: (limit = 25) => SmsGatewayModule.listRecentCalls(limit),
  listConversations: (limit = 100) => SmsGatewayModule.listConversations(limit),
  getConversationMessages: (request: {
    threadId?: string;
    address?: string;
    limit?: number;
  }) => SmsGatewayModule.getConversationMessages(request),
  markConversationUnread: (request: {threadId?: string; address?: string}) =>
    SmsGatewayModule.markConversationUnread(request),
  markConversationRead: (request: {threadId?: string; address?: string}) =>
    SmsGatewayModule.markConversationRead(request),
  deleteConversation: (request: {threadId?: string; address?: string}) =>
    SmsGatewayModule.deleteConversation(request),
  sendSmsMessage: (request: SendSmsArgs) => SmsGatewayModule.sendSmsMessage(request),
  sendMmsMessage: (request: SendMmsArgs) => SmsGatewayModule.sendMmsMessage(request),
  placeCall: (request: PlaceCallArgs) => SmsGatewayModule.placeCall(request),
  answerCall: (request: DialerCallRequest) => SmsGatewayModule.answerCall(request),
  rejectCall: (request: DialerCallRequest) => SmsGatewayModule.rejectCall(request),
  endCall: (request: DialerCallRequest) => SmsGatewayModule.endCall(request),
  setMuted: (request: SetMutedRequest) => SmsGatewayModule.setMuted(request),
  sendDtmf: (request: SendDtmfRequest) => SmsGatewayModule.sendDtmf(request),
  showInCallScreen: (request: ShowInCallScreenRequest = {}) =>
    SmsGatewayModule.showInCallScreen(request),
  pickMmsAttachment: () => SmsGatewayModule.pickMmsAttachment(),
  openBatteryOptimizationSettings: () =>
    SmsGatewayModule.openBatteryOptimizationSettings(),
  addListener: (listener: (event: SmsGatewayNativeEvent) => void): EmitterSubscription => {
    if (!emitter) {
      return {remove: () => undefined} as EmitterSubscription;
    }

    const stateSubscription = emitter.addListener('SmsGatewayState', payload => {
      listener({name: 'SmsGatewayState', payload});
    });
    const eventSubscription = emitter.addListener('SmsGatewayEvent', payload => {
      listener({name: 'SmsGatewayEvent', payload});
    });

    return {
      remove: () => {
        stateSubscription.remove();
        eventSubscription.remove();
      },
    } as EmitterSubscription;
  },
};

export const coercePort = (value: string): number => {
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1024 || parsed > 65535) {
    throw new Error('Port must be an integer between 1024 and 65535.');
  }

  return parsed;
};

export const formatServerAddresses = (status: GatewayStatus | null): string => {
  if (!status) {
    return 'Discovering...';
  }

  const addresses = status.addresses.length > 0 ? status.addresses : [status.host];
  return addresses.map(address => `${address}:${status.port}`).join(', ');
};

export const getGatewayChecklist = (status: GatewayStatus | null) => [
  {label: 'Default SMS role', ready: Boolean(status?.smsRoleGranted)},
  {label: 'Default dialer role', ready: Boolean(status?.dialerRoleGranted)},
  {
    label: 'Notifications allowed',
    ready: Boolean(status?.notificationPermissionGranted),
  },
  {
    label: 'SMS permissions granted',
    ready: Boolean(status?.gatewayPermissionsGranted),
  },
  {
    label: 'Dialer permissions granted',
    ready: Boolean(status && (status.dialerMissingPermissions ?? []).length === 0),
  },
  {
    label: 'In-call service healthy',
    ready: Boolean(status?.inCallServiceHealthy),
  },
  {
    label: 'Battery optimization ignored',
    ready: Boolean(status?.batteryOptimizationsIgnored),
  },
  {label: 'API key configured', ready: Boolean(status?.apiKeyConfigured)},
];

export const formatConversationTitle = (conversation: {
  displayName?: string;
  address?: string;
  threadId?: string;
}): string => {
  if (conversation.displayName && conversation.displayName.trim().length > 0) {
    return conversation.displayName;
  }
  if (conversation.address && conversation.address.trim().length > 0) {
    return conversation.address;
  }

  return conversation.threadId ? `Thread ${conversation.threadId}` : 'New message';
};

export const formatMessageTimestamp = (timestamp: number): string =>
  new Date(timestamp).toLocaleString();

export const formatConversationTimestamp = (timestamp: number): string =>
  new Date(timestamp).toLocaleTimeString([], {
    hour: 'numeric',
    minute: '2-digit',
  });

export const isOutgoingMessage = (messageType: number): boolean =>
  messageType !== 1;

export const formatMessageDeliveryState = (
  message: Pick<
    SmsMessage,
    'messageType' | 'deliveryState' | 'carrierAccepted' | 'failureReason'
  >,
): string | null => {
  if (!isOutgoingMessage(message.messageType) || !message.deliveryState) {
    return null;
  }

  switch (message.deliveryState) {
    case 'pending':
      return 'Sending...';
    case 'sent':
      return 'Sent to carrier';
    case 'delivered':
      return 'Delivered';
    case 'rejected':
      return 'Carrier rejected this MMS';
    case 'failed':
      return message.carrierAccepted === false
        ? 'Failed before carrier handoff'
        : 'Send failed';
    default:
      return null;
  }
};

export const formatMessageFailureDetail = (
  message: Pick<
    SmsMessage,
    'messageType' | 'deliveryState' | 'failureReason'
  >,
): string | null => {
  if (!isOutgoingMessage(message.messageType)) {
    return null;
  }
  if (message.deliveryState !== 'failed' && message.deliveryState !== 'rejected') {
    return null;
  }

  const detail = message.failureReason?.trim();
  return detail && detail.length > 0 ? detail : null;
};

export const formatDialerCallState = (call: Pick<DialerCall, 'state' | 'direction'>) => {
  switch (call.state) {
    case 'ringing':
      return call.direction === 'incoming' ? 'Incoming call' : 'Ringing';
    case 'dialing':
      return 'Dialing';
    case 'active':
      return 'Live';
    case 'holding':
      return 'On hold';
    case 'disconnecting':
      return 'Ending';
    case 'disconnected':
      return 'Ended';
    case 'connecting':
      return 'Connecting';
    default:
      return call.state.replace(/_/g, ' ');
  }
};

export const formatDialerRoute = (route: string) =>
  route.replace(/_/g, ' ').replace(/\b\w/g, character => character.toUpperCase());
