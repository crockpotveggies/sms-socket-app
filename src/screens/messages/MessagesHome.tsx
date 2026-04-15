import React from 'react';
import {Pressable, ScrollView, Switch, Text, View} from 'react-native';

import {SmsConversation} from '../../SmsGateway';
import {ActionButton} from '../../components/ActionButton';
import {styles} from '../../styles/appStyles';
import {SwipeableConversationRow} from './SwipeableConversationRow';

export function MessagesHome({
  conversations,
  loading,
  ready,
  unreadOnly,
  unreadConversationCount,
  onToggleUnreadOnly,
  onOpenConversation,
  onToggleConversationReadState,
  onDeleteConversation,
  onCompose,
  onRequestRole,
  onRequestPermissions,
}: {
  conversations: SmsConversation[];
  loading: boolean;
  ready: boolean;
  unreadOnly: boolean;
  unreadConversationCount: number;
  onToggleUnreadOnly: (value: boolean) => void;
  onOpenConversation: (conversation: SmsConversation) => void | Promise<void>;
  onToggleConversationReadState: (
    conversation: Pick<SmsConversation, 'threadId' | 'address'>,
  ) => void | Promise<void>;
  onDeleteConversation: (
    conversation: Pick<SmsConversation, 'threadId' | 'address'>,
  ) => void | Promise<void>;
  onCompose: () => void;
  onRequestRole: () => void;
  onRequestPermissions: () => void;
}) {
  return (
    <View style={styles.flex}>
      <ScrollView contentContainerStyle={styles.messagesContainer}>
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
          <Text style={styles.sectionMetaBanner}>
            {loading
              ? 'Syncing messages...'
              : `${unreadConversationCount} unread conversation(s)`}
          </Text>
          <View style={styles.unreadToggleRow}>
            <Text style={styles.unreadToggleLabel}>Unread only</Text>
            <Switch
              value={unreadOnly}
              onValueChange={onToggleUnreadOnly}
              trackColor={{false: '#353535', true: '#5a5a5a'}}
              thumbColor={unreadOnly ? '#f1f1f1' : '#8a8a8a'}
            />
          </View>
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
            <SwipeableConversationRow
              key={`${conversation.threadId}-${conversation.id}`}
              conversation={conversation}
              onOpen={onOpenConversation}
              onToggleReadState={onToggleConversationReadState}
              onDelete={onDeleteConversation}
            />
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
