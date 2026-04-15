import {
  GatewayStatus,
  coercePort,
  formatServerAddresses,
  getGatewayChecklist,
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
});
