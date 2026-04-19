/**
 * @format
 */

import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import App from '../App';

jest.mock('../src/SmsGateway', () => {
  const actual = jest.requireActual('../src/SmsGateway');

  return {
    ...actual,
    SmsGateway: {
      addListener: () => ({remove: jest.fn()}),
      deleteConversation: jest.fn().mockResolvedValue(true),
      generateApiKey: jest.fn(),
      getConversationMessages: jest.fn().mockResolvedValue([]),
      getGatewayStatus: jest.fn().mockResolvedValue({
        enabled: false,
        running: false,
        host: '0.0.0.0',
        port: 8787,
        connectionCount: 0,
        smsRoleGranted: false,
        notificationPermissionGranted: true,
        gatewayPermissionsGranted: false,
        missingPermissions: ['android.permission.READ_SMS'],
        batteryOptimizationsIgnored: false,
        apiKeyConfigured: false,
        apiKeyPreview: '',
        addresses: [],
        recentEvents: [],
      }),
      listConversations: jest.fn().mockResolvedValue([]),
      listSubscriptions: jest.fn(),
      markConversationRead: jest.fn().mockResolvedValue(true),
      pickMmsAttachment: jest.fn(),
      openBatteryOptimizationSettings: jest.fn(),
      requestSmsRole: jest.fn(),
      sendMmsMessage: jest.fn(),
      sendSmsMessage: jest.fn(),
      startGateway: jest.fn(),
      stopGateway: jest.fn(),
    },
  };
});

test('renders correctly', async () => {
  await ReactTestRenderer.act(async () => {
    ReactTestRenderer.create(<App />);
    await Promise.resolve();
  });
});
