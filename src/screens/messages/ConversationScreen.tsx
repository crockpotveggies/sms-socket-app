import React from 'react';
import {
  Image,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  View,
} from 'react-native';

import {
  GatewayAttachment,
  SmsMessage,
  formatMessageDeliveryState,
  formatMessageFailureDetail,
  formatMessageTimestamp,
  isOutgoingMessage,
} from '../../SmsGateway';
import {styles} from '../../styles/appStyles';

export function ConversationScreen({
  title,
  subtitle,
  address,
  body,
  attachment,
  loading,
  refreshing,
  sending,
  messages,
  onBack,
  onChangeAddress,
  onChangeBody,
  onPickAttachment,
  onClearAttachment,
  onSend,
  editableAddress,
}: {
  title: string;
  subtitle: string;
  address: string;
  body: string;
  attachment: GatewayAttachment | null;
  loading: boolean;
  refreshing: boolean;
  sending: boolean;
  messages: SmsMessage[];
  onBack: () => void;
  onChangeAddress: (value: string) => void;
  onChangeBody: (value: string) => void;
  onPickAttachment: () => void;
  onClearAttachment: () => void;
  onSend: () => void;
  editableAddress: boolean;
}) {
  const renderAttachmentPreview = (
    nextAttachment: GatewayAttachment,
    outgoing: boolean,
  ) => {
    const imagePayload = nextAttachment.previewBase64 ?? nextAttachment.base64;
    const isImage = nextAttachment.mimeType.startsWith('image/') && imagePayload;

    return (
      <View
        style={[
          styles.attachmentCard,
          outgoing ? styles.attachmentCardOutgoing : styles.attachmentCardIncoming,
        ]}>
        {isImage ? (
          <Image
            source={{
              uri: `data:${nextAttachment.mimeType};base64,${imagePayload}`,
            }}
            style={styles.attachmentPreviewImage}
          />
        ) : null}
        <Text
          style={[
            styles.attachmentTitle,
            outgoing ? styles.attachmentTitleOutgoing : styles.attachmentTitleIncoming,
          ]}>
          {nextAttachment.fileName}
        </Text>
        <Text
          style={[
            styles.attachmentMeta,
            outgoing ? styles.attachmentMetaOutgoing : styles.attachmentMetaIncoming,
          ]}>
          {nextAttachment.mimeType} -{' '}
          {Math.max(1, Math.round(nextAttachment.sizeBytes / 1024))}
          KB
        </Text>
      </View>
    );
  };

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
              Send the first message below to start the thread.
            </Text>
          </View>
        ) : (
          messages.map(message => {
            const outgoing = isOutgoingMessage(message.messageType);
            const deliveryLabel = formatMessageDeliveryState(message);
            const failureDetail = formatMessageFailureDetail(message);
            const isFailure =
              message.deliveryState === 'failed' || message.deliveryState === 'rejected';

            return (
              <View
                key={message.id}
                style={[
                  styles.messageBubble,
                  outgoing
                    ? styles.messageBubbleOutgoing
                    : styles.messageBubbleIncoming,
                ]}>
                {message.attachments.map(item => (
                  <View key={item.id}>
                    {renderAttachmentPreview(item, outgoing)}
                  </View>
                ))}
                <Text
                  style={[
                    styles.messageText,
                    outgoing
                      ? styles.messageTextOutgoing
                      : styles.messageTextIncoming,
                  ]}>
                  {message.body}
                </Text>
                {deliveryLabel ? (
                  <View
                    style={[
                      styles.messageStatusPill,
                      isFailure
                        ? styles.messageStatusPillFailure
                        : outgoing
                          ? styles.messageStatusPillOutgoing
                          : styles.messageStatusPillIncoming,
                    ]}>
                    <Text
                      style={[
                        styles.messageStatusText,
                        isFailure
                          ? styles.messageStatusTextFailure
                          : outgoing
                            ? styles.messageStatusTextOutgoing
                            : styles.messageStatusTextIncoming,
                      ]}>
                      {deliveryLabel}
                    </Text>
                  </View>
                ) : null}
                {failureDetail ? (
                  <Text
                    style={[
                      styles.messageFailureDetail,
                      outgoing
                        ? styles.messageFailureDetailOutgoing
                        : styles.messageFailureDetailIncoming,
                    ]}>
                    {failureDetail}
                  </Text>
                ) : null}
                <Text
                  style={[
                    styles.messageTimestamp,
                    outgoing
                      ? styles.messageTimestampOutgoing
                      : styles.messageTimestampIncoming,
                  ]}>
                  {formatMessageTimestamp(message.timestamp)}
                </Text>
              </View>
            );
          })
        )}
      </ScrollView>

      <View style={styles.composer}>
        <View style={styles.composerStack}>
          {attachment ? (
            <View style={styles.composerAttachmentWrap}>
              {renderAttachmentPreview(attachment, false)}
              {attachment.mimeType === 'application/pdf' ? (
                <Text style={styles.attachmentWarningText}>
                  PDF MMS support varies by carrier and may fail even when images work.
                </Text>
              ) : null}
              <Pressable
                onPress={onClearAttachment}
                style={styles.attachmentRemoveButton}>
                <Text style={styles.attachmentRemoveButtonText}>Remove</Text>
              </Pressable>
            </View>
          ) : null}
          <View style={styles.composerRow}>
            <Pressable
              accessibilityRole="button"
              onPress={onPickAttachment}
              style={styles.attachButton}>
              <Text style={styles.attachButtonText}>Attach</Text>
            </Pressable>
            <TextInput
              value={body}
              onChangeText={onChangeBody}
              placeholder={attachment ? 'Add a caption' : 'Write an SMS or MMS'}
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
        </View>
      </View>
    </KeyboardAvoidingView>
  );
}
