import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {
  AppState,
  Alert,
  LayoutAnimation,
  PermissionsAndroid,
  Platform,
  StatusBar,
  UIManager,
  View,
} from 'react-native';
import {SafeAreaProvider, SafeAreaView} from 'react-native-safe-area-context';

import {
  DialerCall,
  DialerRecentCall,
  GatewayAttachment,
  GatewayEventRecord,
  GatewayStatus,
  SmsConversation,
  SmsGateway,
  SmsGatewayNativeEvent,
  SmsMessage,
  coercePort,
  formatConversationTitle,
  getGatewayChecklist,
} from './src/SmsGateway';
import {AppHeader} from './src/components/AppHeader';
import {BottomTabBar} from './src/components/BottomTabBar';
import {CallsScreen} from './src/screens/calls/CallsScreen';
import {DialerScreen} from './src/screens/calls/DialerScreen';
import {GatewayScreen} from './src/screens/gateway/GatewayScreen';
import {ConversationScreen} from './src/screens/messages/ConversationScreen';
import {MessagesHome} from './src/screens/messages/MessagesHome';
import {styles} from './src/styles/appStyles';

const DEFAULT_HOST = '0.0.0.0';
const DEFAULT_PORT = '8787';

type ScreenState =
  | {name: 'messages'}
  | {name: 'calls'}
  | {name: 'dialer'}
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
  const [recentCalls, setRecentCalls] = useState<DialerRecentCall[]>([]);
  const [recentCallsLoading, setRecentCallsLoading] = useState(false);
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
  const [searchVisible, setSearchVisible] = useState(false);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [dialerNumber, setDialerNumber] = useState('');
  const [composerBody, setComposerBody] = useState('');
  const [composerAttachment, setComposerAttachment] =
    useState<GatewayAttachment | null>(null);
  const [composerAddress, setComposerAddress] = useState('');
  const [sendingMessage, setSendingMessage] = useState(false);
  const [refreshingMessages, setRefreshingMessages] = useState(false);
  const activeConversationRef = useRef<ScreenState>({name: 'messages'});
  const messagingReadyRef = useRef(false);
  const dialerRecentCallsEnabledRef = useRef(false);

  const syncPendingUiRequest = useCallback(async () => {
    const request = await SmsGateway.consumePendingUiRequest();
    if (!request?.screen) {
      return;
    }

    if (request.screen === 'calls') {
      setScreen({name: 'calls'});
      return;
    }

    if (request.screen === 'dialer') {
      setScreen({name: 'dialer'});
    }
  }, []);

  useEffect(() => {
    if (
      Platform.OS === 'android' &&
      UIManager.setLayoutAnimationEnabledExperimental
    ) {
      UIManager.setLayoutAnimationEnabledExperimental(true);
    }
  }, []);

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

  const loadRecentCalls = useCallback(async () => {
    try {
      const nextRecentCalls = await SmsGateway.listRecentCalls(25);
      setRecentCalls(nextRecentCalls);
    } catch (error) {
      Alert.alert('Recent calls unavailable', String(error));
    } finally {
      setRecentCallsLoading(false);
    }
  }, []);

  const loadConversations = useCallback(async () => {
    if (!messagingReadyRef.current) {
      setConversations([]);
      setConversationsLoading(false);
      return;
    }

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

      if (!messagingReadyRef.current) {
        setMessages([]);
        setMessagesLoading(false);
        setRefreshingMessages(false);
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

  const messagingReady = Boolean(
    status?.smsRoleGranted && status?.gatewayPermissionsGranted,
  );

  useEffect(() => {
    messagingReadyRef.current = messagingReady;
  }, [messagingReady]);

  const canReadRecentCalls = Boolean(
    status &&
      !status.dialerMissingPermissions.includes('android.permission.READ_CALL_LOG'),
  );
  const onCallSurface = screen.name === 'calls' || screen.name === 'dialer';

  useEffect(() => {
    dialerRecentCallsEnabledRef.current = canReadRecentCalls;
  }, [canReadRecentCalls]);

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

          if (
            (event.type.startsWith('sms.') || event.type.startsWith('mms.')) &&
            messagingReadyRef.current
          ) {
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

          if (event.type === 'call.added' || event.type === 'dialer.ui.requested') {
            syncPendingUiRequest().catch(() => undefined);
          }

          if (event.type.startsWith('call.') || event.type === 'dialer.state') {
            loadGatewayStatus().catch(() => undefined);
            const currentScreen = activeConversationRef.current;
            if (
              dialerRecentCallsEnabledRef.current &&
              (currentScreen.name === 'calls' || currentScreen.name === 'dialer')
            ) {
              setRecentCallsLoading(true);
              loadRecentCalls().catch(() => undefined);
            }
          }
        }
      },
    );

    loadGatewayStatus()
      .then(() => syncPendingUiRequest())
      .catch(() => undefined);
    return () => subscription.remove();
  }, [loadConversations, loadGatewayStatus, loadMessages, loadRecentCalls, syncPendingUiRequest]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', nextState => {
      if (nextState !== 'active') {
        return;
      }

      loadGatewayStatus()
        .then(() => syncPendingUiRequest())
        .catch(() => undefined);
    });

    return () => subscription.remove();
  }, [loadGatewayStatus, syncPendingUiRequest]);

  useEffect(() => {
    if (!messagingReady) {
      setConversations([]);
      setConversationsLoading(false);
      setMessages([]);
      setMessagesLoading(false);
      setRefreshingMessages(false);
      return;
    }

    setConversationsLoading(true);
    loadConversations().catch(() => undefined);
  }, [loadConversations, messagingReady]);

  useEffect(() => {
    if (!canReadRecentCalls) {
      setRecentCalls([]);
      setRecentCallsLoading(false);
      return;
    }

    if (!onCallSurface) {
      setRecentCallsLoading(false);
      return;
    }

    setRecentCallsLoading(true);
    loadRecentCalls().catch(() => undefined);
  }, [canReadRecentCalls, loadRecentCalls, onCallSurface]);

  useEffect(() => {
    if (screen.name !== 'conversation') {
      setComposerBody('');
      setComposerAttachment(null);
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

  useEffect(() => {
    if (screen.name !== 'messages') {
      setSearchVisible(false);
      setSearchQuery('');
    }
  }, [screen.name]);

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
    return conversations.filter(conversation => {
      if (unreadOnly && conversation.unreadCount === 0) {
        return false;
      }
      if (!query) {
        return true;
      }

      const haystack = [
        conversation.displayName,
        conversation.address,
        conversation.snippet,
      ]
        .join(' ')
        .toLowerCase();
      return haystack.includes(query);
    });
  }, [conversations, searchQuery, unreadOnly]);

  const unreadConversationCount = useMemo(
    () => conversations.filter(conversation => conversation.unreadCount > 0).length,
    [conversations],
  );

  const activeConversation = useMemo(() => {
    if (screen.name !== 'conversation') {
      return null;
    }

    return (
      conversations.find(conversation => {
        if (screen.threadId) {
          return conversation.threadId === screen.threadId;
        }

        return (
          conversation.address === (screen.address ?? screen.draftAddress ?? '')
        );
      }) ?? null
    );
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
      PermissionsAndroid.PERMISSIONS.RECEIVE_MMS,
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

  const requestDialerPermissions = async (): Promise<boolean> => {
    if (Platform.OS !== 'android' || Platform.Version < 23) {
      return true;
    }

    const permissions = [PermissionsAndroid.PERMISSIONS.CALL_PHONE];
    if (Platform.Version >= 26) {
      permissions.push(PermissionsAndroid.PERMISSIONS.ANSWER_PHONE_CALLS);
    }
    permissions.push(PermissionsAndroid.PERMISSIONS.READ_CALL_LOG);

    const result = await PermissionsAndroid.requestMultiple(permissions);
    const controlPermissions = permissions.filter(
      permission => permission !== PermissionsAndroid.PERMISSIONS.READ_CALL_LOG,
    );
    const controlGranted = controlPermissions.every(
      permission => result[permission] === PermissionsAndroid.RESULTS.GRANTED,
    );

    if (!controlGranted) {
      Alert.alert(
        'Call permissions required',
        'The app needs phone-call permissions before it can place or control calls.',
      );
    } else if (
      result[PermissionsAndroid.PERMISSIONS.READ_CALL_LOG] !==
      PermissionsAndroid.RESULTS.GRANTED
    ) {
      Alert.alert(
        'Call log optional',
        'Call control is ready, but recent calls will stay hidden until call-log access is granted.',
      );
    }

    await loadGatewayStatus();
    if (
      result[PermissionsAndroid.PERMISSIONS.READ_CALL_LOG] ===
      PermissionsAndroid.RESULTS.GRANTED
    ) {
      setRecentCallsLoading(true);
      await loadRecentCalls();
    }

    return controlGranted;
  };

  const requestDialerRole = async () => {
    try {
      await SmsGateway.requestDialerRole();
      await loadGatewayStatus();
    } catch (error) {
      Alert.alert('Default dialer role', String(error));
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

  const ensureDialerReady = async (): Promise<boolean> => {
    const roleGranted = status?.dialerRoleGranted
      ? true
      : await SmsGateway.requestDialerRole();
    if (!roleGranted) {
      Alert.alert(
        'Default dialer role required',
        'This app needs to be the default dialer before it can place or control PSTN calls.',
      );
      return false;
    }

    return requestDialerPermissions();
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
      Alert.alert('New API key', `${result.apiKey}\n\nThis value is only shown once.`);
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

  const markConversationRead = useCallback(
    async (conversation: Pick<SmsConversation, 'threadId' | 'address'>) => {
      setConversations(current =>
        current.map(item =>
          item.threadId === conversation.threadId
            ? {...item, read: true, unreadCount: 0}
            : item,
        ),
      );

      try {
        await SmsGateway.markConversationRead({
          threadId: conversation.threadId,
          address: conversation.address,
        });
      } catch (error) {
        await loadConversations();
        Alert.alert('Mark read failed', String(error));
      }
    },
    [loadConversations],
  );

  const markConversationUnread = useCallback(
    async (conversation: Pick<SmsConversation, 'threadId' | 'address'>) => {
      setConversations(current =>
        current.map(item =>
          item.threadId === conversation.threadId
            ? {...item, read: false, unreadCount: Math.max(item.unreadCount, 1)}
            : item,
        ),
      );

      try {
        await SmsGateway.markConversationUnread({
          threadId: conversation.threadId,
          address: conversation.address,
        });
      } catch (error) {
        await loadConversations();
        Alert.alert('Mark unread failed', String(error));
      }
    },
    [loadConversations],
  );

  const toggleConversationReadState = useCallback(
    async (conversation: Pick<SmsConversation, 'threadId' | 'address'>) => {
      const currentConversation = conversations.find(
        item => item.threadId === conversation.threadId,
      );
      const isRead = Boolean(
        currentConversation?.read && (currentConversation.unreadCount ?? 0) === 0,
      );

      if (isRead) {
        await markConversationUnread(conversation);
        return;
      }

      await markConversationRead(conversation);
    },
    [conversations, markConversationRead, markConversationUnread],
  );

  const deleteConversation = useCallback(
    async (conversation: Pick<SmsConversation, 'threadId' | 'address'>) => {
      LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
      setConversations(current =>
        current.filter(item => item.threadId !== conversation.threadId),
      );

      if (screen.name === 'conversation' && screen.threadId === conversation.threadId) {
        setScreen({name: 'messages'});
        setMessages([]);
      }

      try {
        await SmsGateway.deleteConversation({
          threadId: conversation.threadId,
          address: conversation.address,
        });
      } catch (error) {
        await loadConversations();
        Alert.alert('Delete failed', String(error));
      }
    },
    [loadConversations, screen],
  );

  const openConversation = async (conversation: SmsConversation) => {
    if (!conversation.read || conversation.unreadCount > 0) {
      await markConversationRead(conversation);
    }

    setScreen({
      name: 'conversation',
      threadId: conversation.threadId,
      address: conversation.address,
    });
  };

  const openComposer = () => {
    setScreen({name: 'conversation', draftAddress: ''});
  };

  const pickAttachment = async () => {
    try {
      const attachment = await SmsGateway.pickMmsAttachment();
      setComposerAttachment(attachment);
    } catch (error) {
      const message = String(error);
      if (message.includes('ATTACHMENT_CANCELLED')) {
        return;
      }
      Alert.alert('Attachment failed', message);
    }
  };

  const sendMessage = async () => {
    const destination = composerAddress.trim();
    const body = composerBody.trim();
    if (!destination || (!body && !composerAttachment)) {
      Alert.alert(
        'Incomplete message',
        'Enter a phone number and either a message body or an attachment.',
      );
      return;
    }

    try {
      setSendingMessage(true);
      const ready = await ensureMessagingReady();
      if (!ready) {
        return;
      }

      if (composerAttachment) {
        await SmsGateway.sendMmsMessage({
          destination,
          body: body || undefined,
          attachment: composerAttachment,
        });
      } else {
        await SmsGateway.sendSmsMessage({destination, body});
      }
      setComposerBody('');
      setComposerAttachment(null);
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

  const placeCall = async () => {
    const number = dialerNumber.trim();
    if (!number) {
      Alert.alert('Number required', 'Enter a phone number before placing a call.');
      return;
    }

    try {
      const ready = await ensureDialerReady();
      if (!ready) {
        return;
      }

      await SmsGateway.placeCall({number});
      setScreen({name: 'calls'});
    } catch (error) {
      Alert.alert('Call failed', String(error));
    }
  };

  const answerCall = async (call: DialerCall) => {
    try {
      await SmsGateway.answerCall({callId: call.callId});
    } catch (error) {
      Alert.alert('Answer failed', String(error));
    }
  };

  const rejectCall = async (call: DialerCall) => {
    try {
      await SmsGateway.rejectCall({callId: call.callId});
    } catch (error) {
      Alert.alert('Reject failed', String(error));
    }
  };

  const endCall = async (call: DialerCall) => {
    try {
      await SmsGateway.endCall({callId: call.callId});
    } catch (error) {
      Alert.alert('End call failed', String(error));
    }
  };

  const toggleMuted = async (call: DialerCall) => {
    try {
      await SmsGateway.setMuted({callId: call.callId, muted: !call.isMuted});
    } catch (error) {
      Alert.alert('Mute failed', String(error));
    }
  };

  const showInCallScreen = async (showDialpad = false) => {
    try {
      await SmsGateway.showInCallScreen({showDialpad});
      setScreen({name: 'calls'});
    } catch (error) {
      Alert.alert('In-call UI failed', String(error));
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
        <StatusBar barStyle="light-content" backgroundColor="#111111" />
        {screen.name === 'conversation' ? (
          <ConversationScreen
            title={activeConversationTitle}
            subtitle={
              activeConversation ? activeConversation.address : composerAddress || 'Message thread'
            }
            address={composerAddress}
            body={composerBody}
            attachment={composerAttachment}
            loading={messagesLoading}
            refreshing={refreshingMessages}
            sending={sendingMessage}
            messages={messages}
            onBack={() => setScreen({name: 'messages'})}
            onChangeAddress={setComposerAddress}
            onChangeBody={setComposerBody}
            onPickAttachment={() => {
              pickAttachment().catch(() => undefined);
            }}
            onClearAttachment={() => setComposerAttachment(null)}
            onSend={sendMessage}
            editableAddress={!screen.threadId}
          />
        ) : (
          <View style={styles.root}>
            <AppHeader
              title={
                screen.name === 'gateway'
                  ? 'Gateway'
                  : screen.name === 'dialer'
                    ? 'Dialer'
                  : screen.name === 'calls'
                    ? 'Calls'
                    : 'SMS Socket'
              }
              searchVisible={screen.name === 'messages' ? searchVisible : false}
              searchQuery={searchQuery}
              onToggleSearch={
                screen.name === 'messages'
                  ? () => {
                      if (searchVisible) {
                        setSearchVisible(false);
                        setSearchQuery('');
                        return;
                      }
                      setSearchVisible(true);
                    }
                  : undefined
              }
              onChangeSearch={screen.name === 'messages' ? setSearchQuery : undefined}
            />
            {screen.name === 'messages' ? (
              <View style={styles.flex}>
                <MessagesHome
                  conversations={filteredConversations}
                  loading={conversationsLoading}
                  ready={messagingReady}
                  unreadOnly={unreadOnly}
                  unreadConversationCount={unreadConversationCount}
                  onToggleUnreadOnly={setUnreadOnly}
                  onOpenConversation={openConversation}
                  onToggleConversationReadState={toggleConversationReadState}
                  onDeleteConversation={deleteConversation}
                  onCompose={openComposer}
                  onRequestRole={requestSmsRole}
                  onRequestPermissions={() => {
                    requestSmsPermissions().catch(error => {
                      Alert.alert('SMS permissions', String(error));
                    });
                  }}
                />
              </View>
            ) : screen.name === 'calls' ? (
              <CallsScreen
                status={status}
                recentCalls={recentCalls}
                recentCallsLoading={recentCallsLoading}
                onRequestRole={() => {
                  requestDialerRole().catch(() => undefined);
                }}
                onRequestPermissions={() => {
                  requestDialerPermissions().catch(error => {
                    Alert.alert('Call permissions', String(error));
                  });
                }}
                onAnswerCall={call => {
                  answerCall(call).catch(() => undefined);
                }}
                onRejectCall={call => {
                  rejectCall(call).catch(() => undefined);
                }}
                onEndCall={call => {
                  endCall(call).catch(() => undefined);
                }}
                onToggleMute={call => {
                  toggleMuted(call).catch(() => undefined);
                }}
                onShowInCallScreen={showDialpad => {
                  showInCallScreen(showDialpad).catch(() => undefined);
                }}
                onUseRecentNumber={setDialerNumber}
              />
            ) : screen.name === 'dialer' ? (
              <DialerScreen
                status={status}
                number={dialerNumber}
                recentCalls={recentCalls}
                recentCallsLoading={recentCallsLoading}
                onChangeNumber={setDialerNumber}
                onPressDigit={digit => setDialerNumber(current => `${current}${digit}`)}
                onBackspace={() =>
                  setDialerNumber(current => current.slice(0, Math.max(0, current.length - 1)))
                }
                onPlaceCall={() => {
                  placeCall().catch(() => undefined);
                }}
                onRequestRole={() => {
                  requestDialerRole().catch(() => undefined);
                }}
                onRequestPermissions={() => {
                  requestDialerPermissions().catch(error => {
                    Alert.alert('Call permissions', String(error));
                  });
                }}
                onUseRecentNumber={setDialerNumber}
              />
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
                onRequestDialerRole={requestDialerRole}
                onRequestPermissions={() => {
                  requestSmsPermissions().catch(error => {
                    Alert.alert('SMS permissions', String(error));
                  });
                }}
                onRequestDialerPermissions={() => {
                  requestDialerPermissions().catch(error => {
                    Alert.alert('Call permissions', String(error));
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
                defaultHost={DEFAULT_HOST}
                defaultPort={DEFAULT_PORT}
              />
            )}

            <BottomTabBar
              active={
                screen.name === 'gateway'
                  ? 'gateway'
                  : screen.name === 'dialer'
                    ? 'dialer'
                  : screen.name === 'calls'
                    ? 'calls'
                    : 'messages'
              }
              onChange={tab =>
                setScreen(
                  tab === 'gateway'
                    ? {name: 'gateway'}
                    : tab === 'dialer'
                      ? {name: 'dialer'}
                    : tab === 'calls'
                      ? {name: 'calls'}
                      : {name: 'messages'},
                )
              }
            />
          </View>
        )}
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

export default App;
