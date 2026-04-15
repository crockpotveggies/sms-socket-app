import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  Alert,
  KeyboardAvoidingView,
  PermissionsAndroid,
  Platform,
  Pressable,
  ScrollView,
  StatusBar,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native';
import {SafeAreaProvider, SafeAreaView} from 'react-native-safe-area-context';

import {
  GatewayEventRecord,
  GatewayStatus,
  SmsConversation,
  SmsGateway,
  SmsGatewayNativeEvent,
  SmsMessage,
  coercePort,
  formatConversationTimestamp,
  formatConversationTitle,
  formatMessageTimestamp,
  formatServerAddresses,
  getGatewayChecklist,
  isOutgoingMessage,
} from './src/SmsGateway';

const DEFAULT_HOST = '0.0.0.0';
const DEFAULT_PORT = '8787';

type ScreenState =
  | {name: 'messages'}
  | {name: 'gateway'}
  | {
      name: 'conversation';
      threadId?: string;
      address?: string;
      draftAddress?: string;
    };

function App(): React.JSX.Element {
  const [status, setStatus] = useState<GatewayStatus | null>(null);
  const [events, setEvents] = useState<GatewayEventRecord[]>([]);
  const [gatewayLoading, setGatewayLoading] = useState(true);
  const [conversations, setConversations] = useState<SmsConversation[]>([]);
  const [conversationsLoading, setConversationsLoading] = useState(true);
  const [messages, setMessages] = useState<SmsMessage[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [screen, setScreen] = useState<ScreenState>({name: 'messages'});
  const [gatewayTransition, setGatewayTransition] = useState<
    'idle' | 'starting' | 'stopping'
  >('idle');
  const [host, setHost] = useState(DEFAULT_HOST);
  const [port, setPort] = useState(DEFAULT_PORT);
  const [apiKeyInput, setApiKeyInput] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [composerBody, setComposerBody] = useState('');
  const [composerAddress, setComposerAddress] = useState('');
  const [sendingMessage, setSendingMessage] = useState(false);
  const [refreshingMessages, setRefreshingMessages] = useState(false);
  const activeConversationRef = useRef<ScreenState>({name: 'messages'});

  const loadGatewayStatus = useCallback(async () => {
    try {
      const gatewayStatus = await SmsGateway.getGatewayStatus();
      setStatus(gatewayStatus);
      setHost(gatewayStatus.host);
      setPort(String(gatewayStatus.port));
      setEvents(gatewayStatus.recentEvents);
    } catch (error) {
      Alert.alert('Gateway status unavailable', String(error));
    } finally {
      setGatewayLoading(false);
    }
  }, []);

  const loadConversations = useCallback(async () => {
    try {
      const nextConversations = await SmsGateway.listConversations(100);
      setConversations(nextConversations);
    } catch (error) {
      Alert.alert('Messages unavailable', String(error));
    } finally {
      setConversationsLoading(false);
    }
  }, []);

  const loadMessages = useCallback(
    async (request: {threadId?: string; address?: string}, silent = false) => {
      if (!request.threadId && !request.address) {
        setMessages([]);
        return;
      }

      if (silent) {
        setRefreshingMessages(true);
      } else {
        setMessagesLoading(true);
      }

      try {
        const nextMessages = await SmsGateway.getConversationMessages({
          ...request,
          limit: 250,
        });
        setMessages(nextMessages);
      } catch (error) {
        Alert.alert('Conversation unavailable', String(error));
      } finally {
        setMessagesLoading(false);
        setRefreshingMessages(false);
      }
    },
    [],
  );

  useEffect(() => {
    activeConversationRef.current = screen;
  }, [screen]);

  useEffect(() => {
    const subscription = SmsGateway.addListener(
      (nativeEvent: SmsGatewayNativeEvent) => {
        if (nativeEvent.name === 'SmsGatewayState') {
          const nextStatus = nativeEvent.payload as GatewayStatus;
          setStatus(nextStatus);
          setEvents(nextStatus.recentEvents);
        }

        if (nativeEvent.name === 'SmsGatewayEvent') {
          const event = nativeEvent.payload as GatewayEventRecord;
          setEvents(current => [event, ...current].slice(0, 50));

          if (event.type.startsWith('sms.')) {
            loadConversations().catch(() => undefined);
            const currentScreen = activeConversationRef.current;
            if (currentScreen.name === 'conversation') {
              loadMessages(
                {
                  threadId: currentScreen.threadId,
                  address: currentScreen.address ?? currentScreen.draftAddress,
                },
                true,
              ).catch(() => undefined);
            }
          }
        }
      },
    );

    loadGatewayStatus().catch(() => undefined);
    loadConversations().catch(() => undefined);

    return () => subscription.remove();
  }, [loadConversations, loadGatewayStatus, loadMessages]);

  useEffect(() => {
    if (screen.name !== 'conversation') {
      setComposerBody('');
      setComposerAddress('');
      setMessages([]);
      return;
    }

    setComposerAddress(screen.address ?? screen.draftAddress ?? '');
    loadMessages({
      threadId: screen.threadId,
      address: screen.address ?? screen.draftAddress,
    }).catch(() => undefined);
  }, [loadMessages, screen]);

  const checklist = useMemo(() => getGatewayChecklist(status), [status]);
  const enabled = Boolean(status?.enabled);
  const gatewayPhase = useMemo(() => {
    if (gatewayTransition === 'starting') {
      return 'starting';
    }
    if (gatewayTransition === 'stopping') {
      return 'stopping';
    }
    if (status?.enabled || status?.running) {
      return 'running';
    }
    return 'stopped';
  }, [gatewayTransition, status?.enabled, status?.running]);
  const filteredConversations = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) {
      return conversations;
    }

    return conversations.filter(conversation => {
      const haystack = [
        conversation.displayName,
        conversation.address,
        conversation.snippet,
      ]
        .join(' ')
        .toLowerCase();
      return haystack.includes(query);
    });
  }, [conversations, searchQuery]);
  const activeConversation = useMemo(() => {
    if (screen.name !== 'conversation') {
      return null;
    }

    return conversations.find(conversation => {
      if (screen.threadId) {
        return conversation.threadId === screen.threadId;
      }

      return (
        conversation.address === (screen.address ?? screen.draftAddress ?? '')
      );
    }) ?? null;
  }, [conversations, screen]);
  const activeConversationTitle =
    screen.name === 'conversation'
      ? formatConversationTitle({
          displayName: activeConversation?.displayName,
          address: screen.address ?? screen.draftAddress,
          threadId: screen.threadId,
        })
      : '';

  const requestNotificationPermission = async () => {
    if (Platform.OS !== 'android' || Platform.Version < 33) {
      return;
    }

    const result = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
    );

    if (result !== PermissionsAndroid.RESULTS.GRANTED) {
      Alert.alert(
        'Notification permission',
        'Foreground service notifications are required.',
      );
    }

    await loadGatewayStatus();
  };

  const requestSmsPermissions = async (): Promise<boolean> => {
    if (Platform.OS !== 'android' || Platform.Version < 23) {
      return true;
    }

    const result = await PermissionsAndroid.requestMultiple([
      PermissionsAndroid.PERMISSIONS.READ_CONTACTS,
      PermissionsAndroid.PERMISSIONS.READ_SMS,
      PermissionsAndroid.PERMISSIONS.RECEIVE_SMS,
      PermissionsAndroid.PERMISSIONS.SEND_SMS,
      PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE,
    ]);

    const granted = Object.values(result).every(
      value => value === PermissionsAndroid.RESULTS.GRANTED,
    );

    if (!granted) {
      Alert.alert(
        'SMS permissions required',
        'The app needs SMS and phone permissions before it can show conversations and send messages.',
      );
    }

    await loadGatewayStatus();
    return granted;
  };

  const requestSmsRole = async () => {
    try {
      await SmsGateway.requestSmsRole();
      await loadGatewayStatus();
      await loadConversations();
    } catch (error) {
      Alert.alert('Default SMS role', String(error));
    }
  };

  const ensureMessagingReady = async (): Promise<boolean> => {
    const roleGranted = status?.smsRoleGranted
      ? true
      : await SmsGateway.requestSmsRole();
    if (!roleGranted) {
      Alert.alert(
        'Default SMS role required',
        'This app needs to be your default SMS handler to show and send messages.',
      );
      return false;
    }

    return requestSmsPermissions();
  };

  const startGateway = async () => {
    try {
      const ready = await ensureMessagingReady();
      if (!ready) {
        throw new Error('Required SMS access was not granted.');
      }

      await requestNotificationPermission();
      const nextPort = coercePort(port);
      const result = await SmsGateway.startGateway({
        host: host.trim() || DEFAULT_HOST,
        port: nextPort,
        apiKey: apiKeyInput.trim() || undefined,
      });

      setStatus(result.status);
      setEvents(result.status.recentEvents);
      setApiKeyInput('');
      setGatewayTransition('idle');

      if (result.generatedApiKey) {
        Alert.alert(
          'API key generated',
          `Store this now: ${result.generatedApiKey}\n\nIt is only shown once.`,
        );
      }
    } catch (error) {
      setGatewayTransition('idle');
      Alert.alert('Start failed', String(error));
    }
  };

  const stopGateway = async () => {
    try {
      const nextStatus = await SmsGateway.stopGateway();
      setStatus(nextStatus);
      setEvents(nextStatus.recentEvents);
      setGatewayTransition('idle');
    } catch (error) {
      setGatewayTransition('idle');
      Alert.alert('Stop failed', String(error));
    }
  };

  const rotateApiKey = async () => {
    try {
      const result = await SmsGateway.generateApiKey();
      setStatus(result.status);
      Alert.alert(
        'New API key',
        `${result.apiKey}\n\nThis value is only shown once.`,
      );
    } catch (error) {
      Alert.alert('API key rotation failed', String(error));
    }
  };

  const openBatterySettings = async () => {
    try {
      await SmsGateway.openBatteryOptimizationSettings();
    } catch (error) {
      Alert.alert('Battery settings', String(error));
    }
  };

  const openConversation = (conversation: SmsConversation) => {
    setScreen({
      name: 'conversation',
      threadId: conversation.threadId,
      address: conversation.address,
    });
  };

  const openComposer = () => {
    setScreen({name: 'conversation', draftAddress: ''});
  };

  const sendMessage = async () => {
    const destination = composerAddress.trim();
    const body = composerBody.trim();
    if (!destination || !body) {
      Alert.alert('Incomplete message', 'Enter a phone number and message.');
      return;
    }

    try {
      setSendingMessage(true);
      const ready = await ensureMessagingReady();
      if (!ready) {
        return;
      }

      await SmsGateway.sendSmsMessage({
        destination,
        body,
      });

      setComposerBody('');
      setScreen({
        name: 'conversation',
        address: destination,
        draftAddress: destination,
      });
      await loadConversations();
      await loadMessages({address: destination}, true);
    } catch (error) {
      Alert.alert('Message failed', String(error));
    } finally {
      setSendingMessage(false);
    }
  };

  const onToggleEnabled = (value: boolean) => {
    if (value) {
      setGatewayTransition('starting');
      startGateway().catch(() => undefined);
      return;
    }

    setGatewayTransition('stopping');
    stopGateway().catch(() => undefined);
  };

  return (
    <SafeAreaProvider>
      <SafeAreaView style={styles.safeArea}>
        <StatusBar barStyle="light-content" backgroundColor="#07101d" />
        {screen.name === 'conversation' ? (
          <ConversationScreen
            title={activeConversationTitle}
            address={composerAddress}
            body={composerBody}
            loading={messagesLoading}
            refreshing={refreshingMessages}
            sending={sendingMessage}
            messages={messages}
            onBack={() => setScreen({name: 'messages'})}
            onChangeAddress={setComposerAddress}
            onChangeBody={setComposerBody}
            onSend={sendMessage}
            editableAddress={!screen.threadId}
            subtitle={
              activeConversation
                ? activeConversation.address
                : composerAddress || 'SMS thread'
            }
          />
        ) : (
          <View style={styles.root}>
            <AppHeader />
            {screen.name === 'messages' ? (
              <View style={styles.flex}>
                <MessagesHome
                  conversations={filteredConversations}
                  loading={conversationsLoading}
                  ready={Boolean(
                    status?.smsRoleGranted && status?.gatewayPermissionsGranted,
                  )}
                  searchQuery={searchQuery}
                  onChangeSearch={setSearchQuery}
                  onOpenConversation={openConversation}
                  onCompose={openComposer}
                  onRequestRole={requestSmsRole}
                  onRequestPermissions={() => {
                    requestSmsPermissions().catch(error => {
                      Alert.alert('SMS permissions', String(error));
                    });
                  }}
                />
              </View>
            ) : (
              <GatewayScreen
                status={status}
                events={events}
                checklist={checklist}
                loading={gatewayLoading}
                gatewayPhase={gatewayPhase}
                host={host}
                port={port}
                apiKeyInput={apiKeyInput}
                enabled={enabled}
                onToggleEnabled={onToggleEnabled}
                onRequestRole={requestSmsRole}
                onRequestPermissions={() => {
                  requestSmsPermissions().catch(error => {
                    Alert.alert('SMS permissions', String(error));
                  });
                }}
                onRequestNotifications={() => {
                  requestNotificationPermission().catch(error => {
                    Alert.alert('Notification permission', String(error));
                  });
                }}
                onOpenBatterySettings={openBatterySettings}
                onRotateApiKey={rotateApiKey}
                onChangeHost={setHost}
                onChangePort={setPort}
                onChangeApiKey={setApiKeyInput}
                onStartGateway={startGateway}
                onStopGateway={stopGateway}
              />
            )}

            <BottomTabBar
              active={screen.name === 'gateway' ? 'gateway' : 'messages'}
              onChange={tab =>
                setScreen({name: tab === 'gateway' ? 'gateway' : 'messages'})
              }
            />
          </View>
        )}
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

function AppHeader() {
  return (
    <View style={styles.header}>
      <View style={styles.appBar}>
        <Text style={styles.appBarTitle}>SMS Socket</Text>
      </View>
    </View>
  );
}

function MessagesHome({
  conversations,
  loading,
  ready,
  searchQuery,
  onChangeSearch,
  onOpenConversation,
  onCompose,
  onRequestRole,
  onRequestPermissions,
}: {
  conversations: SmsConversation[];
  loading: boolean;
  ready: boolean;
  searchQuery: string;
  onChangeSearch: (value: string) => void;
  onOpenConversation: (conversation: SmsConversation) => void;
  onCompose: () => void;
  onRequestRole: () => void;
  onRequestPermissions: () => void;
}) {
  return (
    <View style={styles.flex}>
      <ScrollView contentContainerStyle={styles.container}>
        {!ready ? (
          <View style={styles.heroCard}>
            <Text style={styles.heroTitle}>Finish SMS setup to unlock messages</Text>
            <Text style={styles.heroText}>
              The app needs the default SMS role and SMS permissions before it
              can show chats and behave like a normal messaging app.
            </Text>
            <View style={styles.actionRow}>
              <ActionButton label="Set default SMS" onPress={onRequestRole} />
              <ActionButton
                label="Grant permissions"
                onPress={onRequestPermissions}
                tone="secondary"
              />
            </View>
          </View>
        ) : null}

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Chats</Text>
          <Text style={styles.sectionMeta}>
            {loading ? 'Syncing...' : `${conversations.length} conversation(s)`}
          </Text>
        </View>

        <View style={styles.searchShell}>
          <SearchIcon />
          <TextInput
            value={searchQuery}
            onChangeText={onChangeSearch}
            placeholder="Search chats or numbers"
            placeholderTextColor="#6f8194"
            style={styles.searchInput}
          />
        </View>

        {conversations.length === 0 ? (
          <View style={styles.emptyCard}>
            <Text style={styles.emptyTitle}>No conversations yet</Text>
            <Text style={styles.emptyText}>
              Start a new SMS with the plus button and it will show up here.
            </Text>
          </View>
        ) : (
          conversations.map(conversation => (
            <Pressable
              key={`${conversation.threadId}-${conversation.id}`}
              onPress={() => onOpenConversation(conversation)}
              style={styles.chatCard}>
              <View style={styles.chatAvatar}>
                <Text style={styles.chatAvatarText}>
                  {conversation.initials || (conversation.address || '?').slice(-2)}
                </Text>
              </View>
              <View style={styles.chatMeta}>
                <View style={styles.chatRow}>
                  <Text style={styles.chatTitle}>
                    {formatConversationTitle(conversation)}
                  </Text>
                  <View style={styles.chatAside}>
                    <Text style={styles.chatTime}>
                      {formatConversationTimestamp(conversation.timestamp)}
                    </Text>
                    {conversation.unreadCount > 0 ? (
                      <View style={styles.unreadBadge}>
                        <Text style={styles.unreadBadgeText}>
                          {conversation.unreadCount}
                        </Text>
                      </View>
                    ) : null}
                  </View>
                </View>
                {conversation.displayName ? (
                  <Text style={styles.chatAddress}>{conversation.address}</Text>
                ) : null}
                <Text
                  style={[
                    styles.chatSnippet,
                    !conversation.read ? styles.chatSnippetUnread : null,
                  ]}>
                  {conversation.snippet || 'No preview available'}
                </Text>
              </View>
            </Pressable>
          ))
        )}
      </ScrollView>

      <Pressable
        accessibilityLabel="Start new message"
        accessibilityRole="button"
        onPress={onCompose}
        style={styles.fab}>
        <Text style={styles.fabText}>+</Text>
      </Pressable>
    </View>
  );
}

function ConversationScreen({
  title,
  subtitle,
  address,
  body,
  loading,
  refreshing,
  sending,
  messages,
  onBack,
  onChangeAddress,
  onChangeBody,
  onSend,
  editableAddress,
}: {
  title: string;
  subtitle: string;
  address: string;
  body: string;
  loading: boolean;
  refreshing: boolean;
  sending: boolean;
  messages: SmsMessage[];
  onBack: () => void;
  onChangeAddress: (value: string) => void;
  onChangeBody: (value: string) => void;
  onSend: () => void;
  editableAddress: boolean;
}) {
  return (
    <KeyboardAvoidingView
      style={styles.flex}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <View style={styles.conversationHeader}>
        <Pressable onPress={onBack} style={styles.backButton}>
          <Text style={styles.backButtonText}>Back</Text>
        </Pressable>
        <View style={styles.conversationHeaderText}>
          <Text style={styles.conversationTitle}>{title}</Text>
          <Text style={styles.conversationSubtitle}>
            {refreshing ? 'Refreshing conversation...' : subtitle}
          </Text>
        </View>
      </View>

      {editableAddress ? (
        <View style={styles.recipientBar}>
          <Text style={styles.recipientLabel}>To</Text>
          <TextInput
            value={address}
            onChangeText={onChangeAddress}
            keyboardType="phone-pad"
            placeholder="Phone number"
            placeholderTextColor="#6f8194"
            style={styles.recipientInput}
          />
        </View>
      ) : null}

      <ScrollView contentContainerStyle={styles.messageList}>
        {loading ? (
          <Text style={styles.detailText}>Loading conversation...</Text>
        ) : messages.length === 0 ? (
          <View style={styles.emptyCard}>
            <Text style={styles.emptyTitle}>No messages yet</Text>
            <Text style={styles.emptyText}>
              Send the first SMS below to start the thread.
            </Text>
          </View>
        ) : (
          messages.map(message => (
            <View
              key={message.id}
              style={[
                styles.messageBubble,
                isOutgoingMessage(message.messageType)
                  ? styles.messageBubbleOutgoing
                  : styles.messageBubbleIncoming,
              ]}>
              <Text
                style={[
                  styles.messageText,
                  isOutgoingMessage(message.messageType)
                    ? styles.messageTextOutgoing
                    : styles.messageTextIncoming,
                ]}>
                {message.body}
              </Text>
              <Text
                style={[
                  styles.messageTimestamp,
                  isOutgoingMessage(message.messageType)
                    ? styles.messageTimestampOutgoing
                    : styles.messageTimestampIncoming,
                ]}>
                {formatMessageTimestamp(message.timestamp)}
              </Text>
            </View>
          ))
        )}
      </ScrollView>

      <View style={styles.composer}>
        <TextInput
          value={body}
          onChangeText={onChangeBody}
          placeholder="Write an SMS"
          placeholderTextColor="#6f8194"
          multiline
          style={styles.composerInput}
        />
        <Pressable
          accessibilityRole="button"
          onPress={onSend}
          style={[styles.sendButton, sending ? styles.sendButtonDisabled : null]}>
          <Text style={styles.sendButtonText}>{sending ? '...' : 'Send'}</Text>
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

function GatewayScreen({
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
          API key:{' '}
          {status?.apiKeyConfigured ? status.apiKeyPreview : 'Not configured'}
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
          <ActionButton
            label="SMS permissions"
            onPress={onRequestPermissions}
          />
          <ActionButton
            label="Notif permission"
            onPress={onRequestNotifications}
          />
        </View>
        <View style={styles.actionRow}>
          <ActionButton
            label="Battery settings"
            onPress={onOpenBatterySettings}
          />
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
          placeholder={DEFAULT_HOST}
          editable={!configLocked}
        />
        <LabeledInput
          label="Listen port"
          value={port}
          onChangeText={onChangePort}
          placeholder={DEFAULT_PORT}
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
              <Text style={styles.eventPayload}>
                {JSON.stringify(event.payload)}
              </Text>
            </View>
          ))
        )}
      </View>
    </ScrollView>
  );
}

function BottomTabBar({
  active,
  onChange,
}: {
  active: 'messages' | 'gateway';
  onChange: (tab: 'messages' | 'gateway') => void;
}) {
  return (
    <View style={styles.bottomBar}>
      <BottomTabButton
        label="Messages"
        active={active === 'messages'}
        icon={<MessagesTabIcon active={active === 'messages'} />}
        onPress={() => onChange('messages')}
      />
      <BottomTabButton
        label="Gateway"
        active={active === 'gateway'}
        icon={<GatewayTabIcon active={active === 'gateway'} />}
        onPress={() => onChange('gateway')}
      />
    </View>
  );
}

function BottomTabButton({
  label,
  active,
  icon,
  onPress,
}: {
  label: string;
  active: boolean;
  icon: React.ReactNode;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={styles.bottomTab}>
      {icon}
      <Text style={active ? styles.bottomTabTextActive : styles.bottomTabText}>
        {label}
      </Text>
    </Pressable>
  );
}

function MessagesTabIcon({active}: {active: boolean}) {
  return (
    <View style={styles.iconBubble}>
      <View
        style={[
          styles.iconBubbleBody,
          active ? styles.iconBubbleBodyActive : null,
        ]}
      />
      <View
        style={[
          styles.iconBubbleTail,
          active ? styles.iconBubbleTailActive : null,
        ]}
      />
    </View>
  );
}

function GatewayTabIcon({active}: {active: boolean}) {
  return (
    <View style={styles.iconStack}>
      <View style={[styles.iconStackDot, active ? styles.iconActiveFill : null]} />
      <View style={[styles.iconStackLine, active ? styles.iconActiveFill : null]} />
      <View style={[styles.iconStackLine, active ? styles.iconActiveFill : null]} />
      <View style={[styles.iconStackLineShort, active ? styles.iconActiveFill : null]} />
    </View>
  );
}

function SearchIcon() {
  return (
    <View style={styles.searchIconWrap}>
      <View style={styles.searchLens} />
      <View style={styles.searchHandle} />
    </View>
  );
}

function ActionButton({
  label,
  onPress,
  tone = 'primary',
  disabled = false,
}: {
  label: string;
  onPress: () => void;
  tone?: 'primary' | 'secondary';
  disabled?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      disabled={disabled}
      onPress={onPress}
      style={[
        styles.button,
        tone === 'secondary' ? styles.buttonSecondary : styles.buttonPrimary,
        disabled ? styles.buttonDisabled : null,
      ]}>
      <Text
        style={
          tone === 'secondary'
            ? [styles.buttonTextSecondary, disabled ? styles.buttonTextDisabled : null]
            : [styles.buttonTextPrimary, disabled ? styles.buttonTextDisabled : null]
        }>
        {label}
      </Text>
    </Pressable>
  );
}

function LabeledInput({
  label,
  ...props
}: {
  label: string;
  value: string;
  onChangeText: (value: string) => void;
  placeholder: string;
  keyboardType?: 'default' | 'numeric';
  secureTextEntry?: boolean;
  editable?: boolean;
}) {
  return (
    <View style={styles.inputGroup}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        placeholderTextColor="#6f8194"
        style={[styles.input, props.editable === false ? styles.inputDisabled : null]}
        autoCapitalize="none"
        autoCorrect={false}
        {...props}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#07101d',
  },
  root: {
    flex: 1,
    backgroundColor: '#07101d',
  },
  flex: {
    flex: 1,
  },
  container: {
    padding: 20,
    gap: 16,
    paddingBottom: 120,
  },
  header: {
    paddingTop: 6,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#162638',
    backgroundColor: '#07101d',
  },
  appBar: {
    minHeight: 56,
    paddingHorizontal: 20,
    justifyContent: 'center',
  },
  appBarTitle: {
    color: '#f4f7fb',
    fontSize: 20,
    fontWeight: '700',
  },
  heroCard: {
    backgroundColor: '#0f1b2d',
    borderRadius: 24,
    padding: 18,
    borderWidth: 1,
    borderColor: '#27425d',
    gap: 10,
  },
  heroTitle: {
    color: '#f4f7fb',
    fontSize: 20,
    fontWeight: '800',
  },
  heroText: {
    color: '#b8c7d8',
    fontSize: 14,
    lineHeight: 21,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 8,
  },
  sectionTitle: {
    color: '#f5f7fb',
    fontSize: 20,
    fontWeight: '700',
  },
  sectionMeta: {
    color: '#87a0b8',
    fontSize: 12,
    fontWeight: '700',
  },
  searchShell: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: '#203249',
    backgroundColor: '#0d1726',
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  searchInput: {
    flex: 1,
    color: '#f4f7fb',
    fontSize: 14,
    paddingVertical: 0,
  },
  chatCard: {
    flexDirection: 'row',
    gap: 14,
    backgroundColor: '#0e1827',
    borderRadius: 22,
    padding: 14,
    borderWidth: 1,
    borderColor: '#203249',
    alignItems: 'center',
  },
  chatAvatar: {
    width: 54,
    height: 54,
    borderRadius: 27,
    backgroundColor: '#163252',
    alignItems: 'center',
    justifyContent: 'center',
  },
  chatAvatarText: {
    color: '#dff5ff',
    fontWeight: '800',
  },
  chatMeta: {
    flex: 1,
    gap: 6,
  },
  chatRow: {
    flexDirection: 'row',
    gap: 12,
    alignItems: 'flex-start',
  },
  chatTitle: {
    color: '#f4f7fb',
    fontSize: 16,
    fontWeight: '700',
    flex: 1,
  },
  chatAside: {
    alignItems: 'flex-end',
    gap: 6,
  },
  chatTime: {
    color: '#82a2bf',
    fontSize: 12,
    fontWeight: '700',
  },
  chatAddress: {
    color: '#6f8aa4',
    fontSize: 12,
    marginTop: -2,
  },
  chatSnippet: {
    color: '#9db2c7',
    fontSize: 13,
    lineHeight: 18,
  },
  chatSnippetUnread: {
    color: '#e9f6ff',
    fontWeight: '700',
  },
  unreadBadge: {
    minWidth: 22,
    paddingHorizontal: 6,
    paddingVertical: 3,
    borderRadius: 11,
    backgroundColor: '#79d2ff',
    alignItems: 'center',
  },
  unreadBadgeText: {
    color: '#08111d',
    fontSize: 11,
    fontWeight: '800',
  },
  emptyCard: {
    backgroundColor: '#0e1827',
    borderRadius: 22,
    padding: 22,
    borderWidth: 1,
    borderColor: '#203249',
    gap: 8,
  },
  emptyTitle: {
    color: '#f4f7fb',
    fontSize: 18,
    fontWeight: '800',
  },
  emptyText: {
    color: '#a6b7c8',
    fontSize: 14,
    lineHeight: 20,
  },
  fab: {
    position: 'absolute',
    right: 24,
    bottom: 94,
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: '#79d2ff',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.3,
    shadowRadius: 10,
    shadowOffset: {width: 0, height: 8},
    elevation: 8,
  },
  fabText: {
    color: '#08111d',
    fontSize: 34,
    lineHeight: 34,
    fontWeight: '400',
    marginTop: -2,
  },
  conversationHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 20,
    paddingTop: 14,
    paddingBottom: 16,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#22344d',
  },
  backButton: {
    backgroundColor: '#0e1827',
    borderWidth: 1,
    borderColor: '#27425d',
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  backButtonText: {
    color: '#dff5ff',
    fontWeight: '700',
  },
  conversationHeaderText: {
    flex: 1,
    gap: 2,
  },
  conversationTitle: {
    color: '#f4f7fb',
    fontSize: 20,
    fontWeight: '800',
  },
  conversationSubtitle: {
    color: '#91a7bc',
    fontSize: 13,
  },
  recipientBar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#22344d',
  },
  recipientLabel: {
    color: '#79d2ff',
    fontWeight: '800',
    width: 28,
  },
  recipientInput: {
    flex: 1,
    color: '#f4f7fb',
    fontSize: 16,
    borderRadius: 14,
    backgroundColor: '#0d1726',
    borderWidth: 1,
    borderColor: '#22344d',
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  messageList: {
    padding: 20,
    gap: 10,
    paddingBottom: 32,
  },
  messageBubble: {
    maxWidth: '82%',
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderRadius: 20,
    gap: 8,
  },
  messageBubbleIncoming: {
    alignSelf: 'flex-start',
    backgroundColor: '#132135',
  },
  messageBubbleOutgoing: {
    alignSelf: 'flex-end',
    backgroundColor: '#79d2ff',
  },
  messageText: {
    fontSize: 15,
    lineHeight: 22,
  },
  messageTextIncoming: {
    color: '#edf6ff',
  },
  messageTextOutgoing: {
    color: '#08111d',
  },
  messageTimestamp: {
    fontSize: 11,
  },
  messageTimestampIncoming: {
    color: '#8fa4b9',
  },
  messageTimestampOutgoing: {
    color: '#18425d',
  },
  composer: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 12,
    padding: 16,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#22344d',
    backgroundColor: '#07101d',
  },
  composerInput: {
    flex: 1,
    minHeight: 52,
    maxHeight: 120,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: '#22344d',
    backgroundColor: '#0d1726',
    color: '#f4f7fb',
    paddingHorizontal: 16,
    paddingVertical: 14,
    textAlignVertical: 'top',
  },
  sendButton: {
    backgroundColor: '#79d2ff',
    borderRadius: 18,
    paddingHorizontal: 18,
    paddingVertical: 15,
  },
  sendButtonDisabled: {
    opacity: 0.7,
  },
  sendButtonText: {
    color: '#08111d',
    fontWeight: '800',
  },
  bottomBar: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 10,
    paddingBottom: 18,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#22344d',
    backgroundColor: '#07101d',
  },
  bottomTab: {
    flex: 1,
    alignItems: 'center',
    gap: 6,
  },
  bottomTabText: {
    color: '#7f97ac',
    fontSize: 12,
    fontWeight: '700',
  },
  bottomTabTextActive: {
    color: '#79d2ff',
    fontSize: 12,
    fontWeight: '800',
  },
  iconBubble: {
    width: 24,
    height: 20,
    alignItems: 'flex-start',
    justifyContent: 'flex-start',
  },
  iconBubbleBody: {
    width: 20,
    height: 15,
    borderRadius: 7,
    borderWidth: 1.6,
    borderColor: '#7f97ac',
  },
  iconBubbleTail: {
    width: 7,
    height: 7,
    marginTop: -2,
    marginLeft: 3,
    borderLeftWidth: 1.6,
    borderBottomWidth: 1.6,
    borderColor: '#7f97ac',
    transform: [{skewX: '-18deg'}],
  },
  iconActiveFill: {
    backgroundColor: '#79d2ff',
  },
  iconBubbleBodyActive: {
    borderColor: '#79d2ff',
  },
  iconBubbleTailActive: {
    borderColor: '#79d2ff',
  },
  iconStack: {
    width: 24,
    gap: 3,
    alignItems: 'flex-start',
  },
  iconStackDot: {
    width: 4,
    height: 4,
    borderRadius: 2,
    backgroundColor: '#7f97ac',
  },
  iconStackLine: {
    width: 20,
    height: 2,
    borderRadius: 1,
    backgroundColor: '#7f97ac',
  },
  iconStackLineShort: {
    width: 14,
    height: 2,
    borderRadius: 1,
    backgroundColor: '#7f97ac',
  },
  searchIconWrap: {
    width: 18,
    height: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  searchLens: {
    width: 11,
    height: 11,
    borderRadius: 6,
    borderWidth: 1.6,
    borderColor: '#7f97ac',
  },
  searchHandle: {
    width: 7,
    height: 1.6,
    borderRadius: 1,
    backgroundColor: '#7f97ac',
    transform: [{rotate: '45deg'}, {translateX: 5}, {translateY: -1}],
  },
  card: {
    backgroundColor: '#111c2e',
    borderRadius: 20,
    padding: 16,
    borderWidth: 1,
    borderColor: '#22344d',
    gap: 12,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  label: {
    color: '#dfe8f0',
    fontSize: 14,
    fontWeight: '600',
  },
  statusText: {
    color: '#7ae582',
    fontSize: 14,
    fontWeight: '700',
  },
  statusTextStopped: {
    color: '#f6bd60',
  },
  statusTextPending: {
    color: '#79d2ff',
  },
  detailText: {
    color: '#9fb0c2',
    fontSize: 13,
    lineHeight: 20,
  },
  checklistItem: {
    flexDirection: 'row',
    gap: 10,
    alignItems: 'center',
  },
  checklistLabel: {
    color: '#dfe8f0',
    fontSize: 14,
  },
  ready: {
    color: '#7ae582',
    fontSize: 11,
    fontWeight: '800',
    width: 56,
  },
  notReady: {
    color: '#f6bd60',
    fontSize: 11,
    fontWeight: '800',
    width: 56,
  },
  actionRow: {
    flexDirection: 'row',
    gap: 12,
    flexWrap: 'wrap',
  },
  button: {
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  buttonPrimary: {
    backgroundColor: '#7fc8f8',
  },
  buttonSecondary: {
    backgroundColor: '#182739',
    borderWidth: 1,
    borderColor: '#35506f',
  },
  buttonTextPrimary: {
    color: '#08111d',
    fontWeight: '800',
  },
  buttonTextSecondary: {
    color: '#dfe8f0',
    fontWeight: '800',
  },
  buttonDisabled: {
    opacity: 0.5,
  },
  buttonTextDisabled: {
    opacity: 0.75,
  },
  inputGroup: {
    gap: 6,
  },
  input: {
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#35506f',
    backgroundColor: '#0d1726',
    paddingHorizontal: 14,
    paddingVertical: 12,
    color: '#f5f7fb',
  },
  inputDisabled: {
    opacity: 0.55,
  },
  eventRow: {
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#22344d',
    paddingTop: 12,
    gap: 4,
  },
  eventType: {
    color: '#7fc8f8',
    fontWeight: '700',
  },
  eventTime: {
    color: '#9fb0c2',
    fontSize: 12,
  },
  eventPayload: {
    color: '#dfe8f0',
    fontSize: 12,
  },
});

export default App;
