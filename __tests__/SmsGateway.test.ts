import {
  GatewayStatus,
  coercePort,
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
  notificationPermissionGranted: true,
  gatewayPermissionsGranted: true,
  missingPermissions: [],
  batteryOptimizationsIgnored: false,
  apiKeyConfigured: true,
  apiKeyPreview: '****ABCD',
  addresses: ['192.168.1.25'],
  recentEvents: [],
};

describe('SmsGateway helpers', () => {
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
      {label: 'Notifications allowed', ready: true},
      {label: 'SMS permissions granted', ready: true},
      {label: 'Battery optimization ignored', ready: false},
      {label: 'API key configured', ready: true},
    ]);
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
});
