import React, {useCallback, useMemo, useRef} from 'react';
import {Animated, PanResponder, Pressable, Text, View} from 'react-native';

import {
  SmsConversation,
  formatConversationTimestamp,
  formatConversationTitle,
} from '../../SmsGateway';
import {SWIPE_ACTION_WIDTH, styles} from '../../styles/appStyles';

export function SwipeableConversationRow({
  conversation,
  onOpen,
  onToggleReadState,
  onDelete,
}: {
  conversation: SmsConversation;
  onOpen: (conversation: SmsConversation) => void | Promise<void>;
  onToggleReadState: (
    conversation: Pick<SmsConversation, 'threadId' | 'address'>,
  ) => void | Promise<void>;
  onDelete: (
    conversation: Pick<SmsConversation, 'threadId' | 'address'>,
  ) => void | Promise<void>;
}) {
  const translateX = useRef(new Animated.Value(0)).current;
  const offsetRef = useRef(0);

  const animateTo = useCallback(
    (value: number) => {
      offsetRef.current = value;
      Animated.spring(translateX, {
        toValue: value,
        useNativeDriver: true,
        bounciness: 0,
        speed: 22,
      }).start();
    },
    [translateX],
  );

  const panResponder = useMemo(
    () =>
      PanResponder.create({
        onMoveShouldSetPanResponder: (_, gestureState) =>
          Math.abs(gestureState.dx) > 12 &&
          Math.abs(gestureState.dx) > Math.abs(gestureState.dy),
        onPanResponderMove: (_, gestureState) => {
          const next = Math.max(
            -SWIPE_ACTION_WIDTH,
            Math.min(SWIPE_ACTION_WIDTH, offsetRef.current + gestureState.dx),
          );
          translateX.setValue(next);
        },
        onPanResponderRelease: (_, gestureState) => {
          const traveled = offsetRef.current + gestureState.dx;
          if (traveled >= SWIPE_ACTION_WIDTH * 0.45) {
            animateTo(SWIPE_ACTION_WIDTH);
            return;
          }
          if (traveled <= -SWIPE_ACTION_WIDTH * 0.45) {
            animateTo(-SWIPE_ACTION_WIDTH);
            return;
          }
          animateTo(0);
        },
        onPanResponderTerminate: () => animateTo(0),
      }),
    [animateTo, translateX],
  );

  const handleOpen = () => {
    if (offsetRef.current !== 0) {
      animateTo(0);
      return;
    }

    onOpen(conversation);
  };

  const handleToggleReadState = () => {
    animateTo(0);
    onToggleReadState(conversation);
  };

  const handleDelete = () => {
    animateTo(0);
    onDelete(conversation);
  };

  return (
    <View style={styles.swipeRowWrap}>
      <View style={styles.swipeActions}>
        <View style={styles.swipeLeftActionWrap}>
          <Pressable
            accessibilityRole="button"
            onPress={handleToggleReadState}
            style={[styles.swipeActionButton, styles.swipeActionRead]}>
            <Text style={styles.swipeActionText}>
              {conversation.read && conversation.unreadCount === 0
                ? 'Mark unread'
                : 'Mark read'}
            </Text>
          </Pressable>
        </View>
        <View style={styles.swipeRightActionWrap}>
          <Pressable
            accessibilityRole="button"
            onPress={handleDelete}
            style={[styles.swipeActionButton, styles.swipeActionDelete]}>
            <Text style={styles.swipeActionText}>Delete</Text>
          </Pressable>
        </View>
      </View>

      <Animated.View
        {...panResponder.panHandlers}
        style={[styles.chatCardShell, {transform: [{translateX}]}]}>
        <Pressable onPress={handleOpen} style={styles.chatCard}>
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
      </Animated.View>
    </View>
  );
}
