import {
  SmsGateway,
  GatewayStatus,
  coercePort,
  formatDialerCallState,
  formatDialerRoute,
  formatMessageDeliveryState,
  formatMessageFailureDetail,
  formatConversationTitle,
  formatServerAddresses,
  getGatewayChecklist,
  isOutgoingMessage,
} from '../src/SmsGateway';

const baseStatus: GatewayStatus = {
  enabled: true,
  running: true,
  host: '0.0.0.0',
  port: 8787,
  connectionCount: 1,
  smsRoleGranted: true,
  dialerRoleGranted: true,
  notificationPermissionGranted: true,
  gatewayPermissionsGranted: true,
  missingPermissions: [],
  batteryOptimizationsIgnored: false,
  apiKeyConfigured: true,
  apiKeyPreview: '****ABCD',
  addresses: ['192.168.1.25'],
  recentEvents: [],
  inCallServiceHealthy: true,
  activeCalls: [],
  dialerMissingPermissions: [],
};

describe('SmsGateway helpers', () => {
  it('exposes additive dialer bridge methods alongside existing SMS methods', () => {
    expect(typeof SmsGateway.requestSmsRole).toBe('function');
    expect(typeof SmsGateway.sendSmsMessage).toBe('function');
    expect(typeof SmsGateway.requestDialerRole).toBe('function');
    expect(typeof SmsGateway.getDialerStatus).toBe('function');
    expect(typeof SmsGateway.placeCall).toBe('function');
    expect(typeof SmsGateway.answerCall).toBe('function');
    expect(typeof SmsGateway.rejectCall).toBe('function');
    expect(typeof SmsGateway.endCall).toBe('function');
    expect(typeof SmsGateway.setMuted).toBe('function');
    expect(typeof SmsGateway.showInCallScreen).toBe('function');
  });

  it('formats gateway addresses', () => {
    expect(formatServerAddresses(baseStatus)).toBe('192.168.1.25:8787');
  });

  it('validates port ranges', () => {
    expect(coercePort('8787')).toBe(8787);
    expect(() => coercePort('80')).toThrow(
      'Port must be an integer between 1024 and 65535.',
    );
  });

  it('builds the setup checklist', () => {
    expect(getGatewayChecklist(baseStatus)).toEqual([
      {label: 'Default SMS role', ready: true},
      {label: 'Default dialer role', ready: true},
      {label: 'Notifications allowed', ready: true},
      {label: 'SMS permissions granted', ready: true},
      {label: 'Dialer permissions granted', ready: true},
      {label: 'In-call service healthy', ready: true},
      {label: 'Battery optimization ignored', ready: false},
      {label: 'API key configured', ready: true},
    ]);
  });

  it('marks dialer checklist items pending when role or health is missing', () => {
    expect(
      getGatewayChecklist({
        ...baseStatus,
        dialerRoleGranted: false,
        inCallServiceHealthy: false,
        dialerMissingPermissions: ['android.permission.CALL_PHONE'],
      }),
    ).toEqual(
      expect.arrayContaining([
        {label: 'Default dialer role', ready: false},
        {label: 'Dialer permissions granted', ready: false},
        {label: 'In-call service healthy', ready: false},
      ]),
    );
  });

  it('prefers display name when formatting conversation titles', () => {
    expect(
      formatConversationTitle({
        displayName: 'Sam Carter',
        address: '+15551234567',
        threadId: '7',
      }),
    ).toBe('Sam Carter');
  });

  it('falls back to thread id when no address or display name exist', () => {
    expect(formatConversationTitle({threadId: '42'})).toBe('Thread 42');
  });

  it('treats non-inbox message types as outgoing for sms and mms shapes', () => {
    expect(isOutgoingMessage(1)).toBe(false);
    expect(isOutgoingMessage(2)).toBe(true);
    expect(isOutgoingMessage(4)).toBe(true);
  });

  it('formats outgoing message delivery states for the UI', () => {
    expect(
      formatMessageDeliveryState({
        messageType: 4,
        deliveryState: 'pending',
        carrierAccepted: null,
        failureReason: null,
      }),
    ).toBe('Sending...');
    expect(
      formatMessageDeliveryState({
        messageType: 2,
        deliveryState: 'rejected',
        carrierAccepted: false,
        failureReason: 'Carrier rejected the attachment.',
      }),
    ).toBe('Carrier rejected this MMS');
  });

  it('only exposes failure detail for failed or rejected outgoing messages', () => {
    expect(
      formatMessageFailureDetail({
        messageType: 2,
        deliveryState: 'failed',
        failureReason: 'Mobile data is disabled.',
      }),
    ).toBe('Mobile data is disabled.');
    expect(
      formatMessageFailureDetail({
        messageType: 2,
        deliveryState: 'sent',
        failureReason: 'Should stay hidden.',
      }),
    ).toBeNull();
  });

  it('formats dialer state and route labels for the calls UI', () => {
    expect(
      formatDialerCallState({
        state: 'ringing',
        direction: 'incoming',
      }),
    ).toBe('Incoming call');
    expect(formatDialerRoute('wired_headset')).toBe('Wired Headset');
  });
});
