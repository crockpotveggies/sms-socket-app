import React from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  View,
} from 'react-native';

import {
  SmsMessage,
  formatMessageTimestamp,
  isOutgoingMessage,
} from '../../SmsGateway';
import {styles} from '../../styles/appStyles';

export function ConversationScreen({
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
