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
  base64?: string;
  previewBase64?: string;
};

export type GatewayStatus = {
  enabled: boolean;
  running: boolean;
  host: string;
  port: number;
  connectionCount: number;
  smsRoleGranted: boolean;
  notificationPermissionGranted: boolean;
  batteryOptimizationsIgnored: boolean;
  gatewayPermissionsGranted: boolean;
  missingPermissions: string[];
  apiKeyConfigured: boolean;
  apiKeyPreview: string;
  addresses: string[];
  recentEvents: GatewayEventRecord[];
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

type NativeSmsGatewayModule = {
  requestSmsRole(): Promise<boolean>;
  getGatewayStatus(): Promise<GatewayStatus>;
  startGateway(config: StartGatewayArgs): Promise<StartGatewayResult>;
  stopGateway(): Promise<GatewayStatus>;
  generateApiKey(): Promise<GenerateApiKeyResult>;
  listSubscriptions(): Promise<Array<Record<string, unknown>>>;
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
  getGatewayStatus: () => SmsGatewayModule.getGatewayStatus(),
  startGateway: (config: StartGatewayArgs) => SmsGatewayModule.startGateway(config),
  stopGateway: () => SmsGatewayModule.stopGateway(),
  generateApiKey: () => SmsGatewayModule.generateApiKey(),
  listSubscriptions: () => SmsGatewayModule.listSubscriptions(),
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
  {
    label: 'Notifications allowed',
    ready: Boolean(status?.notificationPermissionGranted),
  },
  {
    label: 'SMS permissions granted',
    ready: Boolean(status?.gatewayPermissionsGranted),
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
