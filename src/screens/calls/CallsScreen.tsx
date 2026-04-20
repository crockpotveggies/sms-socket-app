import React from 'react';
import {Pressable, ScrollView, Text, View} from 'react-native';

import {
  DialerCall,
  DialerRecentCall,
  GatewayStatus,
  formatDialerCallState,
  formatDialerRoute,
} from '../../SmsGateway';
import {ActionButton} from '../../components/ActionButton';
import {styles} from '../../styles/appStyles';

export function CallsScreen({
  status,
  recentCalls,
  recentCallsLoading,
  onRequestRole,
  onRequestPermissions,
  onAnswerCall,
  onRejectCall,
  onEndCall,
  onToggleMute,
  onShowInCallScreen,
  onUseRecentNumber,
}: {
  status: GatewayStatus | null;
  recentCalls: DialerRecentCall[];
  recentCallsLoading: boolean;
  onRequestRole: () => void;
  onRequestPermissions: () => void;
  onAnswerCall: (call: DialerCall) => void;
  onRejectCall: (call: DialerCall) => void;
  onEndCall: (call: DialerCall) => void;
  onToggleMute: (call: DialerCall) => void;
  onShowInCallScreen: (showDialpad?: boolean) => void;
  onUseRecentNumber: (number: string) => void;
}) {
  const activeCalls = status?.activeCalls ?? [];
  const hasRecentCallAccess =
    Boolean(status) &&
    !status!.dialerMissingPermissions.includes('android.permission.READ_CALL_LOG');
  const dialerControlReady =
    Boolean(status?.dialerRoleGranted) &&
    !status?.dialerMissingPermissions.some(
      permission => permission !== 'android.permission.READ_CALL_LOG',
    );

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Calls status</Text>
        <Text style={styles.detailText}>
          Role: {status?.dialerRoleGranted ? 'Default dialer' : 'Not default'} {'\n'}
          In-call service: {status?.inCallServiceHealthy ? 'Healthy' : 'Waiting / unavailable'}
        </Text>
        {status?.dialerMissingPermissions.length ? (
          <Text style={styles.detailText}>
            Dialer permissions: {status.dialerMissingPermissions.join(', ')}
          </Text>
        ) : null}
        <View style={styles.actionRow}>
          {status?.dialerRoleGranted === false ? (
            <ActionButton label="Request dialer role" onPress={onRequestRole} />
          ) : null}
          {Boolean(status?.dialerMissingPermissions.length) ? (
            <ActionButton label="Call permissions" onPress={onRequestPermissions} />
          ) : null}
          <ActionButton
            label="Open in-call UI"
            onPress={() => onShowInCallScreen(false)}
            tone="secondary"
            disabled={activeCalls.length === 0}
          />
        </View>
      </View>

      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Current calls</Text>
        {activeCalls.length === 0 ? (
          <Text style={styles.detailText}>No active Telecom calls right now.</Text>
        ) : (
          activeCalls.map(call => (
            <View key={call.callId} style={styles.callCard}>
              <View style={styles.callCardHeader}>
                <View style={styles.flex}>
                  <Text style={styles.callCardTitle}>
                    {call.displayName || call.number || 'Unknown caller'}
                  </Text>
                  <Text style={styles.detailText}>
                    {call.number || 'Number unavailable'} {'\n'}
                    {formatDialerCallState(call)} • {formatDialerRoute(call.route)}
                    {call.isConference ? ' • Conference' : ''}
                  </Text>
                </View>
                <Text style={styles.callMutePill}>
                  {call.isMuted ? 'Muted' : 'Live mic'}
                </Text>
              </View>
              <View style={styles.actionRow}>
                {call.canAnswer ? (
                  <ActionButton label="Answer" onPress={() => onAnswerCall(call)} />
                ) : null}
                {call.canReject ? (
                  <ActionButton
                    label="Reject"
                    onPress={() => onRejectCall(call)}
                    tone="secondary"
                  />
                ) : null}
                {call.canDisconnect ? (
                  <ActionButton
                    label="End"
                    onPress={() => onEndCall(call)}
                    tone="secondary"
                  />
                ) : null}
                <ActionButton
                  label={call.isMuted ? 'Unmute' : 'Mute'}
                  onPress={() => onToggleMute(call)}
                  tone="secondary"
                />
              </View>
            </View>
          ))
        )}
      </View>

      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Recent calls</Text>
        {!hasRecentCallAccess ? (
          <Text style={styles.detailText}>
            Grant call-log access to show recent calls. Until then, this screen sticks to
            live calls only, like a polite bouncer.
          </Text>
        ) : recentCallsLoading ? (
          <Text style={styles.detailText}>Loading recent calls...</Text>
        ) : recentCalls.length === 0 ? (
          <Text style={styles.detailText}>No recent calls available.</Text>
        ) : (
          recentCalls.map(call => (
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
                  {call.direction.replace(/_/g, ' ')} •{' '}
                  {new Date(call.timestamp).toLocaleString()}
                </Text>
              </View>
              <Text style={styles.recentCallUse}>Use</Text>
            </Pressable>
          ))
        )}
      </View>

      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Dialer handoff</Text>
        <Text style={styles.detailText}>
          Open the dedicated Dialer tab for keypad entry and outbound calling.
        </Text>
        <View style={styles.actionRow}>
          <ActionButton
            label="Show dialpad"
            onPress={() => onShowInCallScreen(true)}
            tone="secondary"
            disabled={activeCalls.length === 0}
          />
        </View>
      </View>
    </ScrollView>
  );
}
