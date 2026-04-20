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
        dialerRoleGranted: false,
        notificationPermissionGranted: true,
        gatewayPermissionsGranted: false,
        missingPermissions: ['android.permission.READ_SMS'],
        batteryOptimizationsIgnored: false,
        apiKeyConfigured: false,
        apiKeyPreview: '',
        addresses: [],
        recentEvents: [],
        inCallServiceHealthy: false,
        activeCalls: [],
        dialerMissingPermissions: [
          'android.permission.CALL_PHONE',
          'android.permission.READ_CALL_LOG',
        ],
      }),
      getDialerStatus: jest.fn(),
      listConversations: jest.fn().mockResolvedValue([]),
      listRecentCalls: jest.fn().mockResolvedValue([]),
      listSubscriptions: jest.fn(),
      markConversationRead: jest.fn().mockResolvedValue(true),
      answerCall: jest.fn().mockResolvedValue(true),
      consumePendingUiRequest: jest.fn().mockResolvedValue({}),
      endCall: jest.fn().mockResolvedValue(true),
      placeCall: jest.fn().mockResolvedValue({requested: true}),
      pickMmsAttachment: jest.fn(),
      rejectCall: jest.fn().mockResolvedValue(true),
      openBatteryOptimizationSettings: jest.fn(),
      requestDialerRole: jest.fn().mockResolvedValue(false),
      requestSmsRole: jest.fn(),
      sendMmsMessage: jest.fn(),
      sendSmsMessage: jest.fn(),
      setMuted: jest.fn().mockResolvedValue(true),
      showInCallScreen: jest.fn().mockResolvedValue(true),
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
