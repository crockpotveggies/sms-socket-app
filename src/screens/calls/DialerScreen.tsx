import React from 'react';
import {Pressable, ScrollView, Text, View} from 'react-native';

import {DialerRecentCall, GatewayStatus} from '../../SmsGateway';
import {ActionButton} from '../../components/ActionButton';
import {LabeledInput} from '../../components/LabeledInput';
import {styles} from '../../styles/appStyles';

const DIAL_PAD_ROWS = [
  ['1', '2', '3'],
  ['4', '5', '6'],
  ['7', '8', '9'],
  ['*', '0', '#'],
];

export function DialerScreen({
  status,
  number,
  recentCalls,
  recentCallsLoading,
  onChangeNumber,
  onPressDigit,
  onBackspace,
  onPlaceCall,
  onRequestRole,
  onRequestPermissions,
  onUseRecentNumber,
}: {
  status: GatewayStatus | null;
  number: string;
  recentCalls: DialerRecentCall[];
  recentCallsLoading: boolean;
  onChangeNumber: (value: string) => void;
  onPressDigit: (digit: string) => void;
  onBackspace: () => void;
  onPlaceCall: () => void;
  onRequestRole: () => void;
  onRequestPermissions: () => void;
  onUseRecentNumber: (number: string) => void;
}) {
  const dialerControlReady =
    Boolean(status?.dialerRoleGranted) &&
    !status?.dialerMissingPermissions.some(
      permission => permission !== 'android.permission.READ_CALL_LOG',
    );
  const hasRecentCallAccess =
    Boolean(status) &&
    !status!.dialerMissingPermissions.includes('android.permission.READ_CALL_LOG');

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Dialer</Text>
        <Text style={styles.detailText}>
          Keypad entry for PSTN outbound calls using Android Telecom.
        </Text>
        {status?.dialerRoleGranted === false || !dialerControlReady ? (
          <View style={styles.actionRow}>
            {status?.dialerRoleGranted === false ? (
              <ActionButton label="Request dialer role" onPress={onRequestRole} />
            ) : null}
            {Boolean(status?.dialerMissingPermissions.length) ? (
              <ActionButton label="Call permissions" onPress={onRequestPermissions} />
            ) : null}
          </View>
        ) : null}
      </View>

      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Phone keypad</Text>
        <LabeledInput
          label="Number"
          value={number}
          onChangeText={onChangeNumber}
          placeholder="Enter a phone number"
          keyboardType="phone-pad"
        />
        <View style={styles.dialPad}>
          {DIAL_PAD_ROWS.map(row => (
            <View key={row.join('')} style={styles.dialPadRow}>
              {row.map(digit => (
                <Pressable
                  key={digit}
                  accessibilityRole="button"
                  onPress={() => onPressDigit(digit)}
                  style={styles.dialPadKey}>
                  <Text style={styles.dialPadKeyText}>{digit}</Text>
                </Pressable>
              ))}
            </View>
          ))}
        </View>
        <View style={styles.actionRow}>
          <ActionButton
            label="Backspace"
            onPress={onBackspace}
            tone="secondary"
            disabled={number.length === 0}
          />
          <ActionButton
            label="Place call"
            onPress={onPlaceCall}
            disabled={!dialerControlReady || number.trim().length === 0}
          />
        </View>
      </View>

      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Quick dial</Text>
        {!hasRecentCallAccess ? (
          <Text style={styles.detailText}>
            Grant call-log access to populate recent-number shortcuts here.
          </Text>
        ) : recentCallsLoading ? (
          <Text style={styles.detailText}>Loading recent calls...</Text>
        ) : recentCalls.length === 0 ? (
          <Text style={styles.detailText}>No recent numbers available yet.</Text>
        ) : (
          recentCalls.slice(0, 8).map(call => (
            <Pressable
              key={call.id}
              accessibilityRole="button"
              onPress={() => onUseRecentNumber(call.number)}
              style={styles.recentCallRow}>
              <View style={styles.flex}>
                <Text style={styles.callCardTitle}>
                  {call.displayName || call.number || 'Unknown caller'}
                </Text>
                <Text style={styles.detailText}>
                  {call.number || 'Number unavailable'} {'\n'}
                  {new Date(call.timestamp).toLocaleString()}
                </Text>
              </View>
              <Text style={styles.recentCallUse}>Fill</Text>
            </Pressable>
          ))
        )}
      </View>
    </ScrollView>
  );
}
