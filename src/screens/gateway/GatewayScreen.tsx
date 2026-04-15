import React from 'react';
import {ScrollView, Switch, Text, View} from 'react-native';

import {GatewayEventRecord, GatewayStatus, formatServerAddresses} from '../../SmsGateway';
import {ActionButton} from '../../components/ActionButton';
import {LabeledInput} from '../../components/LabeledInput';
import {styles} from '../../styles/appStyles';

export function GatewayScreen({
  status,
  events,
  checklist,
  loading,
  gatewayPhase,
  host,
  port,
  apiKeyInput,
  enabled,
  onToggleEnabled,
  onRequestRole,
  onRequestPermissions,
  onRequestNotifications,
  onOpenBatterySettings,
  onRotateApiKey,
  onChangeHost,
  onChangePort,
  onChangeApiKey,
  onStartGateway,
  onStopGateway,
  defaultHost,
  defaultPort,
}: {
  status: GatewayStatus | null;
  events: GatewayEventRecord[];
  checklist: Array<{label: string; ready: boolean}>;
  loading: boolean;
  gatewayPhase: 'stopped' | 'starting' | 'running' | 'stopping';
  host: string;
  port: string;
  apiKeyInput: string;
  enabled: boolean;
  onToggleEnabled: (value: boolean) => void;
  onRequestRole: () => void;
  onRequestPermissions: () => void;
  onRequestNotifications: () => void;
  onOpenBatterySettings: () => void;
  onRotateApiKey: () => void;
  onChangeHost: (value: string) => void;
  onChangePort: (value: string) => void;
  onChangeApiKey: (value: string) => void;
  onStartGateway: () => void;
  onStopGateway: () => void;
  defaultHost: string;
  defaultPort: string;
}) {
  const configLocked = gatewayPhase === 'starting' || gatewayPhase === 'running';
  const transitionBusy = gatewayPhase === 'starting' || gatewayPhase === 'stopping';
  const gatewayStatusText =
    gatewayPhase === 'starting'
      ? 'Gateway starting up...'
      : gatewayPhase === 'stopping'
        ? 'Gateway stopping...'
        : gatewayPhase === 'running'
          ? 'Foreground service configured'
          : loading
            ? 'Loading...'
            : 'Gateway stopped';

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Gateway status</Text>
        <View style={styles.row}>
          <Text style={styles.label}>Enabled</Text>
          <Switch
            value={enabled}
            onValueChange={onToggleEnabled}
            disabled={transitionBusy}
          />
        </View>
        <Text
          style={[
            styles.statusText,
            gatewayPhase === 'stopped' ? styles.statusTextStopped : null,
            transitionBusy ? styles.statusTextPending : null,
          ]}>
          {gatewayStatusText}
        </Text>
        <Text style={styles.detailText}>
          Connections: {status?.connectionCount ?? 0} {'\n'}
          Bound addresses: {formatServerAddresses(status)}
        </Text>
        <Text style={styles.detailText}>
          API key: {status?.apiKeyConfigured ? status.apiKeyPreview : 'Not configured'}
        </Text>
        {status && !status.gatewayPermissionsGranted ? (
          <Text style={styles.detailText}>
            Missing permissions: {status.missingPermissions.join(', ')}
          </Text>
        ) : null}
      </View>

      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Setup checklist</Text>
        {checklist.map(item => (
          <View key={item.label} style={styles.checklistItem}>
            <Text style={item.ready ? styles.ready : styles.notReady}>
              {item.ready ? 'READY' : 'PENDING'}
            </Text>
            <Text style={styles.checklistLabel}>{item.label}</Text>
          </View>
        ))}
        <View style={styles.actionRow}>
          <ActionButton label="Request SMS role" onPress={onRequestRole} />
          <ActionButton label="SMS permissions" onPress={onRequestPermissions} />
          <ActionButton label="Notif permission" onPress={onRequestNotifications} />
        </View>
        <View style={styles.actionRow}>
          <ActionButton label="Battery settings" onPress={onOpenBatterySettings} />
          <ActionButton label="Rotate API key" onPress={onRotateApiKey} />
        </View>
      </View>

      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Gateway config</Text>
        {configLocked ? (
          <Text style={styles.detailText}>
            Gateway settings are locked while the service is active. Stop the
            gateway to change host, port, or API key override.
          </Text>
        ) : null}
        <LabeledInput
          label="Listen host"
          value={host}
          onChangeText={onChangeHost}
          placeholder={defaultHost}
          editable={!configLocked}
        />
        <LabeledInput
          label="Listen port"
          value={port}
          onChangeText={onChangePort}
          placeholder={defaultPort}
          keyboardType="numeric"
          editable={!configLocked}
        />
        <LabeledInput
          label="Override API key"
          value={apiKeyInput}
          onChangeText={onChangeApiKey}
          placeholder="Leave blank to keep or generate"
          secureTextEntry
          editable={!configLocked}
        />
        <View style={styles.actionRow}>
          <ActionButton
            label={gatewayPhase === 'starting' ? 'Starting...' : 'Start gateway'}
            onPress={onStartGateway}
            disabled={configLocked || gatewayPhase === 'stopping'}
          />
          <ActionButton
            label={gatewayPhase === 'stopping' ? 'Stopping...' : 'Stop gateway'}
            onPress={onStopGateway}
            tone="secondary"
            disabled={gatewayPhase === 'stopped' || gatewayPhase === 'starting'}
          />
        </View>
      </View>

      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Recent events</Text>
        {events.length === 0 ? (
          <Text style={styles.detailText}>No gateway events yet.</Text>
        ) : (
          events.map(event => (
            <View key={event.id} style={styles.eventRow}>
              <Text style={styles.eventType}>{event.type}</Text>
              <Text style={styles.eventTime}>
                {new Date(event.timestamp).toLocaleString()}
              </Text>
              <Text style={styles.eventPayload}>{JSON.stringify(event.payload)}</Text>
            </View>
          ))
        )}
      </View>
    </ScrollView>
  );
}
