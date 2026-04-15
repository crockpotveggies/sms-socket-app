import React from 'react';
import {View} from 'react-native';

import {styles} from '../styles/appStyles';

export function MessagesTabIcon({active}: {active: boolean}) {
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

export function GatewayTabIcon({active}: {active: boolean}) {
  return (
    <View style={styles.iconStack}>
      <View style={[styles.iconStackDot, active ? styles.iconActiveFill : null]} />
      <View style={[styles.iconStackLine, active ? styles.iconActiveFill : null]} />
      <View style={[styles.iconStackLine, active ? styles.iconActiveFill : null]} />
      <View
        style={[styles.iconStackLineShort, active ? styles.iconActiveFill : null]}
      />
    </View>
  );
}

export function SearchIcon({compact = false}: {compact?: boolean}) {
  return (
    <View style={compact ? styles.searchIconWrapCompact : styles.searchIconWrap}>
      <View style={compact ? styles.searchLensCompact : styles.searchLens} />
      <View style={compact ? styles.searchHandleCompact : styles.searchHandle} />
    </View>
  );
}

export function CloseIcon() {
  return (
    <View style={styles.closeIconWrap}>
      <View style={[styles.closeIconLine, styles.closeIconLineForward]} />
      <View style={[styles.closeIconLine, styles.closeIconLineBackward]} />
    </View>
  );
}
